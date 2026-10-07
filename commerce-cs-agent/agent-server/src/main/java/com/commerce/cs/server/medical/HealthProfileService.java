package com.commerce.cs.server.medical;

import com.commerce.cs.domain.entity.HealthProfile;
import com.commerce.cs.domain.repo.HealthProfileRepository;
import com.commerce.cs.domain.service.HealthCard;
import com.commerce.cs.server.llm.DeepSeekClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 健康档案管理助手：collect / complete / summary / update。
 * 不做诊断；LLM 不可用时用规则模板。
 */
@Service
public class HealthProfileService {
    private final HealthProfileRepository profiles;
    private final DeepSeekClient llm;
    private final ObjectMapper objectMapper;

    public HealthProfileService(HealthProfileRepository profiles, DeepSeekClient llm, ObjectMapper objectMapper) {
        this.profiles = profiles;
        this.llm = llm;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> list(Long userId) {
        return profiles.findByUserIdOrderByUpdatedAtDescIdDesc(userId).stream().map(this::brief).toList();
    }

    @Transactional
    public Map<String, Object> create(Long userId, String name) {
        HealthProfile row = new HealthProfile();
        row.setUserId(userId);
        row.setDisplayName(cleanName(name));
        row.setPayload(writeJson(HealthCard.empty()));
        row.setCompleteness(0);
        row.setUpdatedAt(LocalDateTime.now());
        return brief(profiles.save(row));
    }

    public Map<String, Object> view(Long userId, Long id) {
        return viewOf(require(userId, id));
    }

    @Transactional
    public Map<String, Object> saveFields(Long userId, Long id, String name, Map<String, Object> patch) {
        HealthProfile row = require(userId, id);
        if (name != null && !name.isBlank()) {
            row.setDisplayName(cleanName(name));
        }
        Map<String, Object> card = readPayload(row);
        if (patch != null) {
            for (Map.Entry<String, Object> entry : patch.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null || "name".equals(entry.getKey())) {
                    continue;
                }
                HealthCard.put(card, entry.getKey(), normalizeValue(entry.getValue()));
            }
        }
        persist(row, card);
        return viewOf(row);
    }

    /** 四种模式入口，作用在指定的那张就诊卡上。 */
    public Map<String, Object> run(Long userId, Long id, String mode, String input) {
        HealthProfile row = require(userId, id);
        Map<String, Object> card = readPayload(row);
        String resolved = resolveMode(mode, card, input);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", resolved);
        result.put("id", row.getId());
        switch (resolved) {
            case "complete" -> {
                String reply = complete(card, input);
                persist(row, card);
                result.put("reply", reply);
            }
            case "summary" -> result.put("reply", HealthCard.summary(card));
            case "update" -> result.putAll(updateFromDialogue(row, card, input));
            default -> {
                String reply = collect(card, input);
                persist(row, card);
                result.put("reply", reply);
            }
        }
        Map<String, Object> saved = readPayload(require(userId, id));
        result.put("profile", saved);
        result.put("completeness", HealthCard.completeness(saved));
        result.put("summary", HealthCard.summary(saved));
        return result;
    }

    /** 问诊摘要：只给登记类别和有无，不回传原始字段。空卡也可以被选中。 */
    public Map<String, Object> consultDigest(Long userId, Long id) {
        HealthProfile row = require(userId, id);
        Map<String, Object> card = readPayload(row);
        Map<String, Object> view = brief(row);
        view.put("items", HealthCard.consultDigest(card));
        return view;
    }

    public String consultContext(Long userId, Long profileId) {
        if (profileId == null) {
            return "";
        }
        return profiles.findByIdAndUserId(profileId, userId)
                .map(row -> HealthCard.consultContext(readPayload(row), row.getDisplayName()))
                .orElse("");
    }

    public String displayName(Long userId, Long profileId) {
        if (profileId == null || userId == null) {
            return "";
        }
        return profiles.findByIdAndUserId(profileId, userId)
                .map(row -> row.getDisplayName() == null ? "" : row.getDisplayName().trim())
                .orElse("");
    }

    /** 问诊结束后只回写本次关联的那张卡。 */
    @Transactional
    public Map<String, Object> updateAfterConsult(Long userId, Long profileId, String dialogue) {
        if (profileId == null) {
            return Map.of("updates", List.of(), "reply", "未关联就诊卡，不回写。");
        }
        HealthProfile row = require(userId, profileId);
        return updateFromDialogue(row, readPayload(row), dialogue);
    }

    private String collect(Map<String, Object> card, String input) {
        applyLooseAnswer(card, input);
        List<String> missing = HealthCard.missingRequired(card);
        if (missing.isEmpty()) {
            return "【本次询问】\n必填项已齐，请确认下面的档案摘要。\n\n"
                    + HealthCard.summary(card)
                    + "\n\n【当前档案完整度】100%（必填项已完成 7/7）";
        }
        List<String> ask = missing.stream().limit(3).toList();
        StringBuilder text = new StringBuilder("【本次询问】\n");
        int i = 1;
        for (String field : ask) {
            text.append(i++).append(". ").append(HealthCard.askHint(field)).append("\n");
        }
        text.append("\n【当前档案完整度】").append(HealthCard.completeness(card))
                .append("%（必填项已完成 ").append(HealthCard.requiredFilled(card)).append("/7）\n\n");
        text.append("【已收集档案】\n");
        appendFilled(text, card);
        return text.toString().trim();
    }

    private String complete(Map<String, Object> card, String input) {
        applyLooseAnswer(card, input);
        List<String> priority = new ArrayList<>();
        for (String field : List.of("allergies", "medications", "chronic", "pregnancy", "gender", "age_or_birth")) {
            if (HealthCard.missingRequired(card).contains(field)
                    || ("pregnancy".equals(field) && str(card.get("gender")).contains("女") && !HealthCard.filled(card.get("pregnancy")))
                    || ("age_or_birth".equals(field) && HealthCard.missingRequired(card).contains("age_or_birth"))) {
                if (!priority.contains(field)) {
                    priority.add(field);
                }
            }
        }
        for (String field : HealthCard.missingRequired(card)) {
            if (!priority.contains(field)) {
                priority.add(field);
            }
        }
        if (priority.isEmpty()) {
            return "【需要补充】\n关键字段已齐，可继续问诊。";
        }
        List<String> ask = priority.stream().limit(2).toList();
        StringBuilder text = new StringBuilder("【需要补充】\n");
        for (String field : ask) {
            text.append("1. 字段名：").append(HealthCard.label(field)).append("\n");
            text.append("   追问话术：\"").append(HealthCard.askHint(field)).append("\"\n\n");
        }
        text.append("【补充原因】\n");
        for (String field : ask) {
            text.append("- ").append(HealthCard.label(field)).append(" 将影响：")
                    .append(reason(field)).append("\n");
        }
        return text.toString().trim();
    }

    private Map<String, Object> updateFromDialogue(HealthProfile row, Map<String, Object> card, String dialogue) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> updates = new ArrayList<>();
        List<Map<String, Object>> conflicts = new ArrayList<>();
        if (dialogue != null && !dialogue.isBlank() && llm.available()) {
            String raw = llm.chat("""
                    你是健康档案管理助手，只从问诊对话提取可写入档案的字段。
                    输出严格 JSON：{"updates":[{"field":"...","value":"...","action":"append|replace|remove"}],"conflicts":[]}
                    可写字段：allergies,chronic,medications,height_cm,weight_kg,gender,age,pregnancy,smoking,drinking,bp_sys,bp_dia,heart_rate,temperature。
                    不确定的不要写。不要 PII。不要症状主诉。
                    """, "当前档案：" + writeJson(card) + "\n\n问诊记录：\n" + dialogue);
            parseUpdateJson(raw, card, updates, conflicts);
        } else {
            ruleExtract(dialogue, card, updates);
        }
        for (Map<String, Object> item : updates) {
            String field = str(item.get("field"));
            Object value = item.get("value");
            String action = str(item.get("action"));
            Object old = card.get(field);
            if ("append".equals(action) && HealthCard.filled(old)) {
                String merged = str(old);
                String add = str(value);
                if (!merged.contains(add)) {
                    card.put(field, merged + "；" + add);
                }
            } else if ("remove".equals(action)) {
                card.put(field, "无");
            } else {
                if (HealthCard.filled(old) && value != null && !str(old).equals(str(value))) {
                    conflicts.add(Map.of("field", field, "old", old, "new", value, "note", "用户本次提供"));
                }
                HealthCard.put(card, field, value);
            }
        }
        persist(row, card);
        out.put("updates", updates);
        out.put("conflicts", conflicts);
        out.put("reply", updates.isEmpty() ? "本次问诊无可回写档案项。" : "已按问诊内容更新档案 " + updates.size() + " 项。");
        return out;
    }

    private void ruleExtract(String dialogue, Map<String, Object> card, List<Map<String, Object>> updates) {
        if (dialogue == null || dialogue.isBlank()) {
            return;
        }
        for (String raw : dialogue.split("\n")) {
            String line = raw.trim();
            if (line.length() > 40 || line.contains("教学参考") || line.contains("不是确诊")) {
                continue;
            }
            if (line.matches(".*(过敏史|我对).{0,12}(无|青霉素|磺胺|头孢).*")
                    && !HealthCard.filled(card.get("allergies"))) {
                updates.add(Map.of("field", "allergies", "value", line, "action", "replace"));
            }
        }
    }

    private void applyLooseAnswer(Map<String, Object> card, String input) {
        if (input == null || input.isBlank()) {
            return;
        }
        String text = input.trim();
        if (text.matches("^(男|女|其他)$") && !HealthCard.filled(card.get("gender"))) {
            card.put("gender", text);
        }
        if (text.matches("^\\d{1,3}岁?$") && !HealthCard.filled(card.get("age"))) {
            card.put("age", text.replace("岁", ""));
        }
        if (text.matches("^\\d{2,3}(\\.\\d+)?\\s*cm$") || text.matches("^身高\\s*\\d{2,3}")) {
            card.put("height_cm", text.replaceAll("[^0-9.]", ""));
        }
        if (text.matches("^\\d{2,3}(\\.\\d+)?\\s*kg$") || text.matches("^体重\\s*\\d{2,3}")) {
            card.put("weight_kg", text.replaceAll("[^0-9.]", ""));
        }
        if (text.contains("过敏") || "无".equals(text) || "没有".equals(text)) {
            if (!HealthCard.filled(card.get("allergies")) && (text.contains("过敏") || missingOnly(card, "allergies")
                    || HealthCard.missingRequired(card).contains("allergies"))) {
                card.put("allergies", "无".equals(text) || "没有".equals(text) ? "无" : text);
            }
        }
        // 用户可能一次回复多字段，简单按关键词拆
        for (String part : text.split("[，,；;\\n]")) {
            String p = part.trim();
            if (p.startsWith("性别")) card.put("gender", p.replace("性别", "").replace("：", "").replace(":", "").trim());
            if (p.startsWith("年龄")) card.put("age", p.replaceAll("[^0-9]", ""));
            if (p.startsWith("身高")) card.put("height_cm", p.replaceAll("[^0-9.]", ""));
            if (p.startsWith("体重")) card.put("weight_kg", p.replaceAll("[^0-9.]", ""));
            if (p.startsWith("过敏")) card.put("allergies", p.replaceFirst("^过敏[史：:]*", "").trim());
            if (p.startsWith("慢病") || p.startsWith("慢性病")) card.put("chronic", p.replaceFirst("^(慢病|慢性病)[史：:]*", "").trim());
            if (p.startsWith("用药") || p.startsWith("长期用药")) card.put("medications", p.replaceFirst("^(用药|长期用药)[：:]*", "").trim());
        }
        // 引导追问身高/体重时，允许「165，55，没有」这类纯数字顺序回答
        List<String> missing = HealthCard.missingRequired(card);
        List<String> nums = new ArrayList<>();
        for (String part : text.split("[，,；;\\s\\n]+")) {
            String p = part.trim().replace("cm", "").replace("kg", "").replace("厘米", "").replace("公斤", "");
            if (p.matches("^\\d{2,3}(\\.\\d+)?$")) {
                nums.add(p);
            }
        }
        int ni = 0;
        if (!HealthCard.filled(card.get("height_cm")) && missing.contains("height_cm") && ni < nums.size()) {
            double h = Double.parseDouble(nums.get(ni));
            if (h >= 80 && h <= 250) {
                card.put("height_cm", nums.get(ni));
                ni++;
            }
        }
        if (!HealthCard.filled(card.get("weight_kg")) && missing.contains("weight_kg") && ni < nums.size()) {
            double w = Double.parseDouble(nums.get(ni));
            if (w >= 20 && w <= 300) {
                card.put("weight_kg", nums.get(ni));
            }
        }
    }

    private boolean missingOnly(Map<String, Object> card, String field) {
        return HealthCard.missingRequired(card).size() == 1 && HealthCard.missingRequired(card).contains(field);
    }

    private void persist(HealthProfile row, Map<String, Object> card) {
        row.setPayload(writeJson(card));
        row.setCompleteness(HealthCard.completeness(card));
        row.setUpdatedAt(LocalDateTime.now());
        profiles.save(row);
    }

    private HealthProfile require(Long userId, Long id) {
        if (id == null) {
            throw new IllegalArgumentException("请先选择一张就诊卡");
        }
        return profiles.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("就诊卡不存在"));
    }

    private Map<String, Object> viewOf(HealthProfile row) {
        Map<String, Object> card = readPayload(row);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", row.getId());
        view.put("name", row.getDisplayName() == null ? "" : row.getDisplayName());
        view.put("title", titleOf(row));
        view.put("profile", card);
        view.put("completeness", HealthCard.completeness(card));
        view.put("requiredFilled", HealthCard.requiredFilled(card));
        view.put("requiredTotal", HealthCard.REQUIRED.size());
        view.put("missingRequired", HealthCard.missingRequired(card));
        view.put("bmi", HealthCard.bmi(card));
        view.put("bmiClass", HealthCard.bmiClass(HealthCard.bmi(card)));
        view.put("ageYears", HealthCard.ageYears(card));
        view.put("ageBand", HealthCard.ageBand(HealthCard.ageYears(card)));
        view.put("summary", HealthCard.summary(card));
        return view;
    }

    private Map<String, Object> brief(HealthProfile row) {
        Map<String, Object> card = readPayload(row);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", row.getId());
        view.put("name", row.getDisplayName() == null ? "" : row.getDisplayName());
        view.put("title", titleOf(row));
        view.put("exists", true);
        view.put("available", true);
        view.put("included", HealthCard.consultIncluded(card));
        view.put("completeness", HealthCard.completeness(card));
        view.put("updatedAt", row.getUpdatedAt() == null ? "" : row.getUpdatedAt().toString());
        return view;
    }

    private static String titleOf(HealthProfile row) {
        String name = row.getDisplayName() == null ? "" : row.getDisplayName().trim();
        return name.isBlank() ? "未命名的就诊卡" : name + "的就诊卡";
    }

    private static String cleanName(String name) {
        String text = name == null ? "" : name.trim();
        if (text.isBlank()) {
            throw new IllegalArgumentException("请先填写姓名");
        }
        if (text.length() > 20) {
            throw new IllegalArgumentException("姓名不超过 20 个字");
        }
        return text;
    }

    private Map<String, Object> readPayload(HealthProfile row) {
        try {
            Map<String, Object> card = objectMapper.readValue(row.getPayload(), new TypeReference<>() {
            });
            Map<String, Object> merged = HealthCard.empty();
            merged.putAll(card);
            return merged;
        } catch (Exception ex) {
            return HealthCard.empty();
        }
    }

    private String writeJson(Map<String, Object> card) {
        try {
            return objectMapper.writeValueAsString(card);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private void parseUpdateJson(String raw, Map<String, Object> card, List<Map<String, Object>> updates,
                                 List<Map<String, Object>> conflicts) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        try {
            String json = raw;
            int start = raw.indexOf('{');
            int end = raw.lastIndexOf('}');
            if (start >= 0 && end > start) {
                json = raw.substring(start, end + 1);
            }
            JsonNode root = objectMapper.readTree(json);
            if (root.path("updates").isArray()) {
                for (JsonNode node : root.path("updates")) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("field", node.path("field").asText(""));
                    item.put("value", node.path("value").isNumber() ? node.path("value").numberValue() : node.path("value").asText(""));
                    item.put("action", node.path("action").asText("replace"));
                    if (!str(item.get("field")).isBlank()) {
                        updates.add(item);
                    }
                }
            }
            if (root.path("conflicts").isArray()) {
                for (JsonNode node : root.path("conflicts")) {
                    conflicts.add(objectMapper.convertValue(node, new TypeReference<>() {
                    }));
                }
            }
        } catch (Exception ignored) {
            // 规则降级已在外层处理
        }
    }

    private static String resolveMode(String mode, Map<String, Object> card, String input) {
        if (mode != null && !mode.isBlank()) {
            return mode.trim().toLowerCase();
        }
        if (input != null && input.length() > 80 && (input.contains("用户") || input.contains("助手"))) {
            return "update";
        }
        if (HealthCard.requiredFilled(card) >= 7) {
            return "summary";
        }
        if (HealthCard.requiredFilled(card) > 0) {
            return "complete";
        }
        return "collect";
    }

    private static String reason(String field) {
        return switch (field) {
            case "allergies" -> "用药禁忌排查";
            case "medications" -> "药物相互作用判断";
            case "chronic" -> "主诉与慢病关联";
            case "pregnancy" -> "用药与检查安全";
            case "gender" -> "性别相关风险分层";
            case "age_or_birth", "age" -> "年龄段风险分层";
            default -> "更准确的参考建议";
        };
    }

    private static void appendFilled(StringBuilder text, Map<String, Object> card) {
        for (Map.Entry<String, Object> entry : card.entrySet()) {
            if (HealthCard.filled(entry.getValue())) {
                text.append("- ").append(HealthCard.label(entry.getKey())).append("：")
                        .append(entry.getValue()).append("\n");
            }
        }
    }

    private static Object normalizeValue(Object value) {
        if (value instanceof String s) {
            return s.trim();
        }
        return value;
    }

    private static String snippetAfter(String text, String key) {
        int idx = text.indexOf(key);
        if (idx < 0) {
            return "有过敏史";
        }
        String slice = text.substring(idx, Math.min(text.length(), idx + 24)).replaceAll("[\\n\\r]", " ");
        return slice.isBlank() ? "有过敏史" : slice;
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
