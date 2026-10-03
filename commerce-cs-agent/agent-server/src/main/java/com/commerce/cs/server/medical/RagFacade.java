package com.commerce.cs.server.medical;

import com.commerce.cs.domain.entity.MedDisease;
import com.commerce.cs.domain.service.KeywordRanker;
import com.commerce.cs.domain.service.RrfFusion;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * 大模型只有这一个检索入口。向量、关键词、图谱在里面并行召回，再用 RRF 融合。
 */
@Service
public class RagFacade {
    private static final Logger log = LoggerFactory.getLogger(RagFacade.class);

    private final KnowledgeLedger ledger;
    private final RagProperties properties;
    private final Neo4jGraphClient graph;
    private final ObjectMapper objectMapper;
    private final RestClient http;

    public RagFacade(KnowledgeLedger ledger, RagProperties properties, Neo4jGraphClient graph, ObjectMapper objectMapper) {
        this.ledger = ledger;
        this.properties = properties;
        this.graph = graph;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(800));
        factory.setReadTimeout(Duration.ofMillis(3000));
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    public record Hit(String channel, String title, String text, String sourceName, String sourceUrl, String diseaseCode) {
    }

    public record Bundle(List<Hit> hits, boolean keywordDegraded, boolean vectorDegraded, boolean graphDegraded, String note) {
    }

    private record Channel(String name, List<Hit> hits, boolean degraded, String note) {
    }

    public Bundle retrieve(String query) {
        List<Channel> channels = runParallel(query);
        String note = channels.stream().map(Channel::note).collect(Collectors.joining("；"))
                + "；融合排序使用 RRF，k=" + Math.max(properties.getRrfK(), 1);
        return new Bundle(fuse(channels),
                degraded(channels, "关键词"),
                degraded(channels, "向量"),
                degraded(channels, "图谱"),
                note);
    }

    private List<Channel> runParallel(String query) {
        List<Callable<Channel>> tasks = List.of(
                () -> keywordChannel(query),
                () -> vectorChannel(query),
                () -> graphChannel(query));
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Channel>> futures = executor.invokeAll(tasks, 4, TimeUnit.SECONDS);
            List<String> names = List.of("关键词", "向量", "图谱");
            List<Channel> channels = new ArrayList<>();
            for (int i = 0; i < futures.size(); i++) {
                channels.add(read(futures.get(i), names.get(i)));
            }
            return channels;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return List.of(
                    new Channel("关键词", List.of(), true, "关键词检索中断"),
                    new Channel("向量", List.of(), true, "向量检索中断"),
                    new Channel("图谱", List.of(), true, "图谱检索中断"));
        }
    }

    private Channel read(Future<Channel> future, String name) {
        try {
            return future.get();
        } catch (Exception ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new Channel(name, List.of(), true, name + "检索超时");
        }
    }

    private Channel keywordChannel(String query) {
        String lexical = lexical(query);
        if (properties.isEsEnabled()) {
            try {
                return new Channel("关键词", searchEs(lexical), false, "关键词检索走 ES");
            } catch (RuntimeException ex) {
                return new Channel("关键词", fromLedger(lexical), true, "ES 不可用，关键词检索已降级到知识底账");
            }
        }
        return new Channel("关键词", fromLedger(lexical), true, "关键词检索走知识底账");
    }

    private Channel vectorChannel(String query) {
        if (!properties.isChromaEnabled()) {
            return new Channel("向量", List.of(), true, "语义检索未启用");
        }
        try {
            return new Channel("向量", searchChroma(query), false, "语义检索走 Chroma：子块匹配，召回父块");
        } catch (RuntimeException ex) {
            log.warn("向量检索失败: {}", ex.toString());
            return new Channel("向量", List.of(), true, "向量服务不可用，语义通道已降级，不伪造向量");
        }
    }

    private Channel graphChannel(String query) {
        if (!properties.isNeo4jEnabled()) {
            return new Channel("图谱", List.of(), true, "图谱检索未启用");
        }
        try {
            return new Channel("图谱", graph.search(lexical(query)), false, "图谱检索走 Neo4j");
        } catch (RuntimeException ex) {
            log.warn("图谱检索失败: {}", ex.toString());
            return new Channel("图谱", List.of(), true, "Neo4j 不可用，图谱通道已降级");
        }
    }

    /** 口语只补到关键词和图谱。向量仍用原句，避免把改写写进嵌入。 */
    private static String lexical(String query) {
        if (query == null || query.isBlank()) {
            return query == null ? "" : query;
        }
        String normalized = query.replace("嗓子疼", "咽痛").replace("流鼻涕", "流涕").replace("低热", "低烧");
        if (normalized.equals(query)) {
            return query;
        }
        return query + "\n" + normalized;
    }

    private List<Hit> fuse(List<Channel> channels) {
        Map<String, List<Hit>> grouped = new java.util.LinkedHashMap<>();
        List<List<String>> rankings = new ArrayList<>();
        for (Channel channel : channels) {
            List<String> keys = new ArrayList<>();
            for (Hit hit : channel.hits()) {
                String key = fusionKey(hit);
                keys.add(key);
                grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(hit);
            }
            if (!keys.isEmpty()) {
                rankings.add(keys);
            }
        }
        List<Hit> fused = new ArrayList<>();
        for (RrfFusion.Scored scored : RrfFusion.fuse(rankings, properties.getRrfK())) {
            List<Hit> group = grouped.get(scored.key());
            if (group == null || group.isEmpty()) {
                continue;
            }
            fused.add(merge(group));
            if (fused.size() >= 5) {
                break;
            }
        }
        return fused;
    }

    private static Hit merge(List<Hit> group) {
        Hit body = group.stream().filter(hit -> "向量".equals(hit.channel())).findFirst().orElse(group.get(0));
        String channels = group.stream()
                .map(Hit::channel)
                .distinct()
                .sorted(Comparator.comparingInt(RagFacade::channelOrder))
                .collect(Collectors.joining("+"));
        return new Hit(channels, body.title(), body.text(), body.sourceName(), body.sourceUrl(),
                firstCode(group, body.diseaseCode()));
    }

    private static String firstCode(List<Hit> group, String fallback) {
        for (Hit hit : group) {
            if (hit.diseaseCode() != null && !hit.diseaseCode().isBlank()) {
                return hit.diseaseCode();
            }
        }
        return fallback == null ? "" : fallback;
    }

    private static String fusionKey(Hit hit) {
        if (hit.diseaseCode() != null && !hit.diseaseCode().isBlank()) {
            return "disease:" + hit.diseaseCode();
        }
        return "title:" + (hit.title() == null ? "" : hit.title());
    }

    private static int channelOrder(String channel) {
        return switch (channel) {
            case "向量" -> 0;
            case "关键词" -> 1;
            case "图谱" -> 2;
            default -> 9;
        };
    }

    private static boolean degraded(List<Channel> channels, String name) {
        return channels.stream().filter(channel -> name.equals(channel.name())).anyMatch(Channel::degraded);
    }

    private List<Hit> fromLedger(String query) {
        List<Hit> hits = new ArrayList<>();
        for (KeywordRanker.Ranked item : KeywordRanker.rank(query, ledger.diseases(), ledger.symptoms()).stream().limit(5).toList()) {
            MedDisease disease = ledger.disease(item.diseaseCode());
            if (disease == null) {
                continue;
            }
            String extra = ledger.articles().stream()
                    .filter(article -> item.diseaseCode().equals(article.getDiseaseCode()))
                    .map(article -> article.getTitle() + "：" + article.getBody())
                    .collect(Collectors.joining("\n"));
            String text = disease.getSummary() + (extra.isBlank() ? "" : "\n" + extra);
            hits.add(new Hit("关键词", disease.getName(), text, disease.getSourceName(),
                    disease.getSourceUrl(), disease.getCode()));
        }
        return hits;
    }

    private List<Hit> searchEs(String query) {
        String url = trimSlash(properties.getEsUrl()) + "/med_kb/_search";
        Map<String, Object> body = Map.of(
                "size", 3,
                "query", Map.of("multi_match", Map.of(
                        "query", query == null ? "" : query,
                        "fields", List.of("title", "body", "keywords"))));
        @SuppressWarnings("unchecked")
        Map<String, Object> response = readJson(http.post().uri(url).body(body));
        if (response == null || !(response.get("hits") instanceof Map<?, ?> hitsNode)) {
            return List.of();
        }
        Object raw = hitsNode.get("hits");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Hit> hits = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> hit)) {
                continue;
            }
            Object source = hit.get("_source");
            if (!(source instanceof Map<?, ?> doc)) {
                continue;
            }
            hits.add(new Hit("关键词", str(doc.get("title")), str(doc.get("body")),
                    str(doc.get("sourceName")), str(doc.get("sourceUrl")), str(doc.get("diseaseCode"))));
        }
        return hits;
    }

    /** 用子块向量匹配，同一父块只召回一次，正文用父块。 */
    private List<Hit> searchChroma(String query) {
        String text = query == null ? "" : query.trim();
        if (text.isEmpty()) {
            return List.of();
        }
        Map<String, Object> embedded = readJson(http.post()
                .uri(trimSlash(properties.getEmbedUrl()) + "/embed")
                .body(Map.of("text", text)));
        if (embedded == null || !(embedded.get("vector") instanceof List<?> vector) || vector.isEmpty()) {
            throw new IllegalStateException("empty embedding");
        }
        String collectionId = collectionId();
        String url = trimSlash(properties.getChromaUrl())
                + "/api/v2/tenants/default_tenant/databases/default_database/collections/"
                + collectionId + "/query";
        Map<String, Object> body = Map.of(
                "query_embeddings", List.of(vector),
                "n_results", 8,
                "include", List.of("metadatas", "documents", "distances"));
        Map<String, Object> response = readJson(http.post().uri(url).body(body));
        if (response == null || !(response.get("metadatas") instanceof List<?> groups) || groups.isEmpty()) {
            return List.of();
        }
        Object first = groups.get(0);
        if (!(first instanceof List<?> metadatas)) {
            return List.of();
        }
        List<?> documents = response.get("documents") instanceof List<?> docs && !docs.isEmpty() && docs.get(0) instanceof List<?>
                ? (List<?>) docs.get(0) : List.of();
        List<Hit> hits = new ArrayList<>();
        java.util.Set<String> seenParents = new java.util.LinkedHashSet<>();
        for (int i = 0; i < metadatas.size(); i++) {
            if (!(metadatas.get(i) instanceof Map<?, ?> meta)) {
                continue;
            }
            String parentId = str(meta.get("parent_id"));
            if (!parentId.isEmpty() && !seenParents.add(parentId)) {
                continue;
            }
            String parent = str(meta.get("parent_text"));
            if (parent.isBlank() && i < documents.size()) {
                parent = str(documents.get(i));
            }
            hits.add(new Hit("向量", str(meta.get("title")), parent, str(meta.get("source_name")),
                    str(meta.get("source_url")), str(meta.get("disease_code"))));
            if (hits.size() >= 8) {
                break;
            }
        }
        return hits;
    }

    private String collectionId() {
        String name = properties.getChromaCollection() == null || properties.getChromaCollection().isBlank()
                ? "med_parent_child" : properties.getChromaCollection();
        String url = trimSlash(properties.getChromaUrl())
                + "/api/v2/tenants/default_tenant/databases/default_database/collections/" + name;
        Map<String, Object> collection = readJson(http.get().uri(url));
        if (collection == null || collection.get("id") == null) {
            throw new IllegalStateException("chroma collection missing");
        }
        return collection.get("id").toString();
    }

    private Map<String, Object> readJson(RestClient.RequestHeadersSpec<?> spec) {
        try {
            String raw = spec.exchange((request, response) -> {
                byte[] bytes = response.getBody().readAllBytes();
                if (response.getStatusCode().isError()) {
                    String text = new String(bytes, StandardCharsets.UTF_8);
                    if (text.length() > 180) {
                        text = text.substring(0, 180);
                    }
                    throw new IllegalStateException("HTTP " + response.getStatusCode().value()
                            + " " + request.getURI() + " " + text);
                }
                return new String(bytes, StandardCharsets.UTF_8);
            });
            if (raw == null || raw.isBlank()) {
                return Map.of();
            }
            return objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {
            });
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("检索响应无法解析", ex);
        }
    }

    private static String str(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String trimSlash(String url) {
        if (url == null || url.isBlank()) {
            return "http://127.0.0.1:9200";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
