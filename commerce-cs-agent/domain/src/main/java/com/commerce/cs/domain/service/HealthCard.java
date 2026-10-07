package com.commerce.cs.domain.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 电子就诊卡领域模型：字段采集目标、BMI/年龄段计算、完整度。
 * 不做诊断，只管理档案。
 */
public final class HealthCard {
    public static final List<String> REQUIRED = List.of(
            "gender", "age_or_birth", "height_cm", "weight_kg", "allergies", "chronic", "medications");

    private HealthCard() {
    }

    public static Map<String, Object> empty() {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("gender", "");
        card.put("birth_date", "");
        card.put("age", "");
        card.put("height_cm", "");
        card.put("weight_kg", "");
        card.put("allergies", "");
        card.put("chronic", "");
        card.put("medications", "");
        card.put("bp_sys", "");
        card.put("bp_dia", "");
        card.put("heart_rate", "");
        card.put("temperature", "");
        card.put("spo2", "");
        card.put("glucose", "");
        card.put("waist_cm", "");
        card.put("blood_type", "");
        card.put("family_history", "");
        card.put("surgery_history", "");
        card.put("smoking", "");
        card.put("drinking", "");
        card.put("exercise", "");
        card.put("sleep_hours", "");
        card.put("pregnancy", "");
        card.put("menstrual", "");
        return card;
    }

    public static int requiredFilled(Map<String, Object> card) {
        int n = 0;
        if (filled(card.get("gender"))) n++;
        if (filled(card.get("age")) || filled(card.get("birth_date"))) n++;
        if (filled(card.get("height_cm"))) n++;
        if (filled(card.get("weight_kg"))) n++;
        if (filled(card.get("allergies"))) n++;
        if (filled(card.get("chronic"))) n++;
        if (filled(card.get("medications"))) n++;
        return n;
    }

    public static int completeness(Map<String, Object> card) {
        return (int) Math.round(requiredFilled(card) * 100.0 / REQUIRED.size());
    }

    public static Double bmi(Map<String, Object> card) {
        Double h = asDouble(card.get("height_cm"));
        Double w = asDouble(card.get("weight_kg"));
        if (h == null || w == null || h <= 0) {
            return null;
        }
        double meters = h / 100.0;
        return Math.round(w / (meters * meters) * 10.0) / 10.0;
    }

    public static String bmiClass(Double bmi) {
        if (bmi == null) {
            return "未知";
        }
        if (bmi < 18.5) return "偏瘦";
        if (bmi < 24) return "正常";
        if (bmi < 28) return "超重";
        return "肥胖";
    }

    public static Integer ageYears(Map<String, Object> card) {
        Integer age = asInt(card.get("age"));
        if (age != null) {
            return age;
        }
        String birth = str(card.get("birth_date"));
        if (birth.length() >= 4) {
            try {
                int year = Integer.parseInt(birth.substring(0, 4));
                int now = java.time.LocalDate.now().getYear();
                if (year > 1900 && year <= now) {
                    return now - year;
                }
            } catch (NumberFormatException ignored) {
                // ignore
            }
        }
        return null;
    }

    public static String ageBand(Integer age) {
        if (age == null) return "未知";
        if (age <= 3) return "婴幼儿";
        if (age <= 13) return "儿童";
        if (age <= 17) return "青少年";
        if (age <= 64) return "成人";
        return "老年";
    }

    public static List<String> missingRequired(Map<String, Object> card) {
        List<String> missing = new ArrayList<>();
        if (!filled(card.get("gender"))) missing.add("gender");
        if (!filled(card.get("age")) && !filled(card.get("birth_date"))) missing.add("age_or_birth");
        if (!filled(card.get("height_cm"))) missing.add("height_cm");
        if (!filled(card.get("weight_kg"))) missing.add("weight_kg");
        if (!filled(card.get("allergies"))) missing.add("allergies");
        if (!filled(card.get("chronic"))) missing.add("chronic");
        if (!filled(card.get("medications"))) missing.add("medications");
        return missing;
    }

    public static String label(String field) {
        return switch (field) {
            case "gender" -> "性别";
            case "age_or_birth", "age", "birth_date" -> "出生日期或年龄";
            case "height_cm" -> "身高（cm）";
            case "weight_kg" -> "体重（kg）";
            case "allergies" -> "过敏史";
            case "chronic" -> "慢性病史";
            case "medications" -> "当前长期用药";
            case "bp_sys", "bp_dia", "blood_pressure" -> "血压";
            case "heart_rate" -> "心率";
            case "temperature" -> "体温";
            case "spo2" -> "血氧";
            case "glucose" -> "血糖";
            case "waist_cm" -> "腰围";
            case "blood_type" -> "血型";
            case "family_history" -> "家族遗传病史";
            case "surgery_history" -> "手术史/住院史";
            case "smoking" -> "吸烟";
            case "drinking" -> "饮酒";
            case "exercise" -> "运动频率";
            case "sleep_hours" -> "睡眠时长";
            case "pregnancy" -> "妊娠/哺乳/备孕";
            case "menstrual" -> "月经是否规律";
            default -> field;
        };
    }

    public static String askHint(String field) {
        return switch (field) {
            case "gender" -> "您的性别是？（男/女/其他）";
            case "age_or_birth", "age", "birth_date" -> "您的年龄是多少岁？或出生年份也可以。";
            case "height_cm" -> "您的身高大约多少厘米？仅用于评估，本地保存。";
            case "weight_kg" -> "您的体重大约多少公斤？仅用于更准确评估，本地保存。";
            case "allergies" -> "有没有药物或食物过敏？没有请说「无」。";
            case "chronic" -> "有没有慢性病，比如高血压、糖尿病、哮喘？没有请说「无」。";
            case "medications" -> "目前有没有长期在吃的药（药名、剂量、频次）？没有请说「无」。";
            case "pregnancy" -> "目前是否妊娠、哺乳或备孕？";
            default -> "请补充" + label(field) + "。";
        };
    }

    public static String summary(Map<String, Object> card) {
        Integer age = ageYears(card);
        Double bmi = bmi(card);
        String gender = display(card.get("gender"));
        String ageText = age == null ? "未知" : age + "岁（" + ageBand(age) + "）";
        String height = display(card.get("height_cm"));
        String weight = display(card.get("weight_kg"));
        String bmiText = bmi == null ? "未知" : bmi + "（" + bmiClass(bmi) + "）";
        String bp = bpText(card);
        String special = specialText(card);
        String allergies = riskText(card.get("allergies"), "无已知过敏");
        String chronic = riskText(card.get("chronic"), "无");
        String meds = riskText(card.get("medications"), "无");
        return "【档案摘要】\n"
                + "基础：" + gender + "，" + ageText + "，身高" + height + "cm，体重" + weight + "kg，BMI " + bmiText + "\n"
                + "体征：血压 " + bp + "，心率 " + display(card.get("heart_rate"))
                + "，体温 " + display(card.get("temperature")) + "℃，血氧 " + display(card.get("spo2"))
                + "%，血糖 " + display(card.get("glucose")) + "\n"
                + "风险：过敏[" + allergies + "]｜慢病[" + chronic + "]｜用药[" + meds + "]｜特殊状态[" + special + "]\n"
                + "生活：吸烟" + display(card.get("smoking")) + "，饮酒" + display(card.get("drinking"))
                + "，运动" + display(card.get("exercise")) + "，睡眠" + display(card.get("sleep_hours")) + "\n"
                + "家族史：" + display(card.get("family_history"));
    }

    /**
     * 给用户看的问诊辅助摘要。
     * 基本情况展示年龄段、性别、身高、体重、BMI（体型对症状评估有参考价值）；
     * 既往/用药/过敏仍只说有没有登记，不展示病名与药名。
     */
    public static List<Map<String, String>> consultDigest(Map<String, Object> card) {
        List<Map<String, String>> items = new ArrayList<>();
        Integer age = ageYears(card);
        String gender = str(card.get("gender"));
        List<String> basicParts = new ArrayList<>();
        if (age != null) {
            basicParts.add("成人".equals(ageBand(age)) ? "成年人" : ageBand(age));
        }
        if (filled(gender)) {
            basicParts.add(gender);
        }
        if (filled(card.get("height_cm"))) {
            basicParts.add("身高" + str(card.get("height_cm")) + "cm");
        }
        if (filled(card.get("weight_kg"))) {
            basicParts.add("体重" + str(card.get("weight_kg")) + "kg");
        }
        Double bmi = bmi(card);
        if (bmi != null) {
            basicParts.add("BMI " + bmi + "（" + bmiClass(bmi) + "）");
        }
        items.add(item("基本情况", basicParts.isEmpty() ? "未登记" : String.join(" · ", basicParts)));
        items.add(item("既往情况", presence(card.get("chronic"), "有已登记的慢性病史", "无已登记慢性病")));
        items.add(item("用药信息", presence(card.get("medications"), "存在已登记药物", "无已登记长期用药")));
        items.add(item("过敏信息", presence(card.get("allergies"), "有已登记过敏史", "无已登记过敏")));
        if (gender.contains("女") && filled(card.get("pregnancy")) && !isNone(card.get("pregnancy"))) {
            items.add(item("特殊状态", "有已登记的妊娠/哺乳/备孕情况"));
        }
        return items;
    }

    /** 已登记内容涉及哪些类别，用于「已包含：基本情况 · 既往记录 · 用药信息」。 */
    public static List<String> consultIncluded(Map<String, Object> card) {
        List<String> included = new ArrayList<>();
        if (ageYears(card) != null
                || filled(card.get("gender"))
                || filled(card.get("height_cm"))
                || filled(card.get("weight_kg"))) {
            included.add("基本情况");
        }
        if (filled(card.get("chronic")) || filled(card.get("surgery_history"))) included.add("既往记录");
        if (filled(card.get("medications"))) included.add("用药信息");
        if (filled(card.get("allergies"))) included.add("过敏信息");
        return included;
    }

    /**
     * 写进模型的就诊卡。用户问自己是谁或自己的资料时，按这里回答。
     * 一项都没登记、也没有姓名时返回空串。
     */
    public static String consultContext(Map<String, Object> card) {
        return consultContext(card, null);
    }

    public static String consultContext(Map<String, Object> card, String displayName) {
        StringBuilder facts = new StringBuilder();
        if (displayName != null && !displayName.isBlank()) {
            facts.append("姓名：").append(displayName.trim()).append("\n");
        }
        Integer age = ageYears(card);
        String gender = str(card.get("gender"));
        if (age != null || filled(gender)) {
            facts.append("基本情况：")
                    .append(filled(gender) ? gender : "")
                    .append(age != null && filled(gender) ? "，" : "")
                    .append(age == null ? "" : age + "岁")
                    .append("\n");
        }
        appendFact(facts, "身高cm", card.get("height_cm"), "");
        appendFact(facts, "体重kg", card.get("weight_kg"), "");
        Double bmi = bmi(card);
        if (bmi != null) {
            facts.append("BMI：").append(bmi).append("（").append(bmiClass(bmi)).append("）\n");
        }
        appendFact(facts, "慢性病史", card.get("chronic"), "无");
        appendFact(facts, "手术/住院史", card.get("surgery_history"), "无");
        appendFact(facts, "长期用药", card.get("medications"), "无");
        appendFact(facts, "过敏史", card.get("allergies"), "无已登记过敏");
        if (gender.contains("女")) {
            appendFact(facts, "妊娠/哺乳/备孕", card.get("pregnancy"), "无");
        }
        if (facts.isEmpty()) {
            return "";
        }
        return "[就诊卡]\n" + facts
                + "使用规则：这是用户本次主动关联的就诊卡，上面的字段就是已登记资料。"
                + "用户问「我」的资料，或用本卡姓名（第三人称）问身高、体重、BMI、年龄、过敏、用药、慢病等，都必须按上面直接回答，不要说材料里没有，也不要说不知道。"
                + "卡片上没有的字段才可以说未登记。用户没问这些时，不要主动把整张卡念一遍。"
                + "和这次症状描述冲突时先问清楚；不能只凭既往病史下结论；过敏药不得出现在建议里。";
    }

    private static void appendFact(StringBuilder facts, String label, Object value, String noneLabel) {
        if (!filled(value)) {
            return;
        }
        if (noneLabel == null || noneLabel.isBlank()) {
            if (!filled(value) || isNone(value)) {
                return;
            }
        }
        String text = isNone(value) ? noneLabel : str(value);
        if (text.isBlank()) {
            return;
        }
        facts.append(label).append("：").append(text.length() > 60 ? text.substring(0, 60) : text).append("\n");
    }

    private static String presence(Object value, String present, String none) {
        if (!filled(value)) {
            return "未登记";
        }
        return isNone(value) ? none : present;
    }

    private static boolean isNone(Object value) {
        String text = str(value);
        return "无".equals(text) || "没有".equals(text) || "无已知过敏".equals(text) || "否".equals(text);
    }

    private static Map<String, String> item(String label, String value) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("label", label);
        row.put("value", value);
        return row;
    }

    public static void put(Map<String, Object> card, String field, Object value) {
        if (field == null || value == null) {
            return;
        }
        String key = switch (field) {
            case "age_or_birth" -> "age";
            case "blood_pressure" -> "bp_sys";
            default -> field;
        };
        card.put(key, value);
    }

    public static boolean filled(Object value) {
        if (value == null) {
            return false;
        }
        String text = String.valueOf(value).trim();
        return !text.isEmpty() && !"未知".equals(text) && !"用户未提供".equals(text);
    }

    private static String specialText(Map<String, Object> card) {
        String gender = str(card.get("gender"));
        if (gender.contains("女")) {
            return filled(card.get("pregnancy")) ? str(card.get("pregnancy")) : "⚠️未知";
        }
        return display(card.get("pregnancy"));
    }

    private static String bpText(Map<String, Object> card) {
        if (filled(card.get("bp_sys")) || filled(card.get("bp_dia"))) {
            return display(card.get("bp_sys")) + "/" + display(card.get("bp_dia")) + " mmHg";
        }
        return "未知";
    }

    private static String riskText(Object value, String emptyLabel) {
        if (!filled(value)) {
            return "⚠️未知";
        }
        String text = str(value);
        if ("无".equals(text) || "没有".equals(text) || "无已知过敏".equals(text)) {
            return emptyLabel;
        }
        return text;
    }

    private static String display(Object value) {
        return filled(value) ? str(value) : "未知";
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static Double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        String text = str(value).replace("cm", "").replace("kg", "").trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Integer asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = str(value).replace("岁", "").trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
