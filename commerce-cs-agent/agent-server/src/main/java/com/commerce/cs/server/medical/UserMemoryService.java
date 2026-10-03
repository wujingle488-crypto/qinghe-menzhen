package com.commerce.cs.server.medical;

import com.commerce.cs.domain.entity.UserMemory;
import com.commerce.cs.domain.repo.UserMemoryRepository;
import com.commerce.cs.domain.service.ChronicProfile;
import com.commerce.cs.domain.service.LongTermConsolidation;
import com.commerce.cs.domain.service.LongTermConsolidation.Fact;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

/** 用户长期病档案。向量挂在现有嵌入服务上，写不进去时仍按病种保留，不伪造向量。 */
@Service
public class UserMemoryService {
    private static final Logger log = LoggerFactory.getLogger(UserMemoryService.class);

    private final UserMemoryRepository memories;
    private final RagProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient http;

    public UserMemoryService(UserMemoryRepository memories, RagProperties properties, ObjectMapper objectMapper) {
        this.memories = memories;
        this.properties = properties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(800));
        factory.setReadTimeout(Duration.ofMillis(2500));
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    public record View(List<String> codes, String context, String profileLine) {
        public String caution(String currentCode) {
            return ChronicProfile.caution(currentCode, codes);
        }

        static View empty() {
            return new View(List.of(), "", "");
        }
    }

    @Transactional
    public View recall(Long userId) {
        if (userId == null) {
            return View.empty();
        }
        List<Fact> kept = refresh(userId);
        List<String> codes = kept.stream().map(item -> item.diseaseCode).filter(code -> code != null && !code.isBlank()).distinct().toList();
        String context = kept.isEmpty() ? "" : "用户长期情况，只能作对照，不能据此加药："
                + kept.stream().map(item -> item.content).reduce((left, right) -> left + "；" + right).orElse("");
        String profile = codes.isEmpty() ? "" : "长期记忆：" + codes.stream().map(ChronicProfile::label)
                .filter(label -> !label.isBlank()).reduce((left, right) -> left + "、" + right).orElse("") + "。";
        return new View(codes, context, profile);
    }

    @Transactional
    public void rememberFromConsult(Long userId, String text, String concludedCode) {
        rememberFromConsult(userId, text, concludedCode, null);
    }

    @Transactional
    public void rememberFromConsult(Long userId, String text, String concludedCode, Long sessionId) {
        String code = ChronicProfile.code(text, concludedCode);
        if (userId == null || code.isBlank()) {
            return;
        }
        List<Fact> facts = new ArrayList<>(refresh(userId));
        Fact incoming = new Fact();
        incoming.diseaseCode = code;
        String label = ChronicProfile.label(code);
        String said = text == null ? "" : text.trim();
        incoming.content = clip("用户长期情况：" + label + "。" + said, 400);
        boolean stated = ChronicProfile.code(said, "").equals(code);
        incoming.baseImportance = stated ? 0.9 : 0.75;
        incoming.importance = incoming.baseImportance;
        incoming.createdAt = LocalDateTime.now();
        incoming.lastAccessedAt = incoming.createdAt;
        incoming.vector = embed(label + " " + said);
        if (sessionId != null) {
            incoming.sourceSessionIds.add(sessionId);
        }
        facts.add(incoming);
        replace(userId, LongTermConsolidation.consolidate(facts, LocalDateTime.now()));
    }

    /** 删掉某次会话带进来的长期记忆。别的会话也写过的同一条会留下。没有来源号的旧记录不动。 */
    @Transactional
    public void forgetSession(Long userId, Long sessionId) {
        if (userId == null || sessionId == null) {
            return;
        }
        for (UserMemory row : memories.findByUserId(userId)) {
            java.util.LinkedHashSet<Long> sources = parseSources(row.getSourceSessionIds());
            if (sources.isEmpty() || !sources.remove(sessionId)) {
                continue;
            }
            if (sources.isEmpty()) {
                memories.delete(row);
            } else {
                row.setSourceSessionIds(formatSources(sources));
                memories.save(row);
            }
        }
    }

    private List<Fact> refresh(Long userId) {
        List<UserMemory> rows = memories.findByUserId(userId);
        List<Fact> facts = new ArrayList<>();
        for (UserMemory row : rows) {
            facts.add(toFact(row));
        }
        List<Fact> kept = LongTermConsolidation.consolidate(facts, LocalDateTime.now());
        if (changed(rows, kept)) {
            replace(userId, kept);
        }
        return kept;
    }

    private boolean changed(List<UserMemory> rows, List<Fact> kept) {
        if (rows.size() != kept.size()) {
            return true;
        }
        for (Fact fact : kept) {
            if (fact.id == null) {
                return true;
            }
            UserMemory row = null;
            for (UserMemory item : rows) {
                if (fact.id.equals(item.getId())) {
                    row = item;
                    break;
                }
            }
            if (row == null
                    || Math.abs(fact.importance - row.getImportance()) >= 0.01
                    || !fact.content.equals(row.getContent())) {
                return true;
            }
        }
        return false;
    }

    private void replace(Long userId, List<Fact> kept) {
        List<UserMemory> rows = memories.findByUserId(userId);
        for (UserMemory row : rows) {
            boolean stillThere = kept.stream().anyMatch(fact -> row.getId().equals(fact.id));
            if (!stillThere) {
                memories.delete(row);
            }
        }
        for (Fact fact : kept) {
            UserMemory row = fact.id == null ? new UserMemory() : memories.findById(fact.id).orElseGet(UserMemory::new);
            row.setUserId(userId);
            row.setDiseaseCode(fact.diseaseCode);
            row.setContent(clip(fact.content, 500));
            row.setBaseImportance(fact.baseImportance);
            row.setImportance(fact.importance);
            row.setCreatedAt(fact.createdAt == null ? LocalDateTime.now() : fact.createdAt);
            row.setLastAccessedAt(fact.lastAccessedAt == null ? LocalDateTime.now() : fact.lastAccessedAt);
            row.setEmbedding(writeVector(fact.vector));
            row.setSourceSessionIds(formatSources(fact.sourceSessionIds));
            memories.save(row);
        }
    }

    private Fact toFact(UserMemory row) {
        Fact fact = new Fact();
        fact.id = row.getId();
        fact.diseaseCode = row.getDiseaseCode();
        fact.content = row.getContent();
        fact.baseImportance = row.getBaseImportance();
        fact.importance = row.getImportance();
        fact.createdAt = row.getCreatedAt();
        fact.lastAccessedAt = row.getLastAccessedAt();
        fact.vector = readVector(row.getEmbedding());
        fact.sourceSessionIds = parseSources(row.getSourceSessionIds());
        return fact;
    }

    private double[] embed(String text) {
        if (text == null || text.isBlank() || properties.getEmbedUrl() == null || properties.getEmbedUrl().isBlank()) {
            return null;
        }
        try {
            String raw = http.post()
                    .uri(trimSlash(properties.getEmbedUrl()) + "/embed")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(java.util.Map.of("text", text))
                    .retrieve()
                    .body(String.class);
            if (raw == null || raw.isBlank()) {
                return null;
            }
            JsonNode vector = objectMapper.readTree(raw).path("vector");
            if (!vector.isArray() || vector.isEmpty()) {
                return null;
            }
            double[] values = new double[vector.size()];
            for (int i = 0; i < vector.size(); i++) {
                values[i] = vector.get(i).asDouble();
            }
            return values;
        } catch (Exception ex) {
            log.warn("长期记忆未写入向量: {}", ex.toString());
            return null;
        }
    }

    private String writeVector(double[] vector) {
        if (vector == null || vector.length == 0) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(vector);
        } catch (Exception ex) {
            return null;
        }
    }

    private double[] readVector(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(raw);
            if (!node.isArray() || node.isEmpty()) {
                return null;
            }
            double[] values = new double[node.size()];
            for (int i = 0; i < node.size(); i++) {
                values[i] = node.get(i).asDouble();
            }
            return values;
        } catch (Exception ex) {
            return null;
        }
    }

    private static java.util.LinkedHashSet<Long> parseSources(String raw) {
        java.util.LinkedHashSet<Long> ids = new java.util.LinkedHashSet<>();
        if (raw == null || raw.isBlank()) {
            return ids;
        }
        for (String part : raw.split(",")) {
            String token = part.trim();
            if (token.isEmpty()) {
                continue;
            }
            try {
                ids.add(Long.parseLong(token));
            } catch (NumberFormatException ignored) {
                // 跳过坏数据
            }
        }
        return ids;
    }

    private static String formatSources(java.util.Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return "";
        }
        return ids.stream().map(String::valueOf).reduce((left, right) -> left + "," + right).orElse("");
    }

    private static String clip(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }

    private static String trimSlash(String url) {
        if (url == null) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
