package com.commerce.cs.domain.service;

import com.commerce.cs.domain.entity.MedDisease;
import com.commerce.cs.domain.entity.MedSymptom;
import java.util.List;
import java.util.regex.Pattern;

/** 问诊采集：部位、时长、发烧齐了就收口；最多追问三次，用户也可以提前要分析。 */
public final class ConsultIntake {
    public static final int MAX_ASKS = 3;

    private static final Pattern DURATION = Pattern.compile(
            "(?<!今)(?:[0-9]+|[一二两三四五六七八九十半几数多])天|小时|分钟|刚才|刚刚|好久|持续|半天|昨晚|昨夜|昨天|前天");
    private static final List<String> FEVER = List.of(
            "不发烧", "没发烧", "没有发烧", "不发热", "没发热", "没有发热",
            "低烧", "高烧", "低热", "高热", "发烧", "发热", "体温", "没烧");
    private static final List<String> STOP = List.of(
            "先给分析", "直接分析", "不用问了", "别问了", "给我分析", "直接给分析");
    private static final List<String> GREETING = List.of(
            "你好", "您好", "在吗", "嗨", "哈喽", "hello", "hi", "早上好", "下午好", "晚上好", "晚安",
            "谢谢", "多谢", "再见", "拜拜");
    private static final List<String> CHITCHAT = List.of(
            "天气", "聊天", "无聊", "讲个笑话", "你是谁", "你叫什么", "你会什么", "在干嘛");
    private static final List<String> CONSULT_CUES = List.of(
            "不舒服", "难受", "疼", "痛", "痒", "咳", "烧", "热", "鼻涕", "喷嚏", "恶心", "呕吐",
            "拉肚子", "腹泻", "便秘", "疹", "疱", "风团", "乏力", "酸痛", "喉咙", "嗓子", "肚子",
            "皮肤", "胸口", "胸闷", "头晕", "体检", "报告", "用药", "吃什么药", "能不能吃", "挂什么科",
            "医院", "过敏", "鼻炎", "感冒", "发烧", "发热");

    private static final List<String> BODY_PARTS = List.of(
            "喉咙", "嗓子", "咽", "鼻", "头", "眼", "耳", "牙", "口腔", "嘴", "脖子", "颈", "胸", "肚子", "胃",
            "腹", "背", "腰", "手", "脚", "腿", "膝", "关节", "皮肤", "身上", "脸");

    private ConsultIntake() {
    }

    public record Slots(boolean site, boolean duration, boolean fever) {
        public boolean complete() {
            return site && duration && fever;
        }

        /** 凑不满两项时，只给参考方向，不推荐药品。 */
        public boolean thin() {
            int filled = (site ? 1 : 0) + (duration ? 1 : 0) + (fever ? 1 : 0);
            return filled < 2;
        }

        public String missingText() {
            StringBuilder text = new StringBuilder();
            if (!site) {
                text.append("不舒服的部位");
            }
            if (!duration) {
                if (!text.isEmpty()) {
                    text.append("、");
                }
                text.append("持续了多久");
            }
            if (!fever) {
                if (!text.isEmpty()) {
                    text.append("、");
                }
                text.append("有没有发烧");
            }
            return text.toString();
        }
    }

    public static boolean stopRequested(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.replace(" ", "").replace("\n", "");
        return STOP.stream().anyMatch(normalized::contains);
    }

    /**
     * 普通寒暄/闲聊：还没有进入症状采集时，先正常聊天并引导，不硬追问槽位。
     * 一旦历史里已有部位/时长/发烧信号，就按问诊处理。
     */
    public static boolean isCasualChat(String current, Slots historySlots) {
        if (current == null || current.isBlank()) {
            return false;
        }
        if (stopRequested(current)) {
            return false;
        }
        if (historySlots != null && (historySlots.site() || historySlots.duration() || historySlots.fever())) {
            return false;
        }
        String text = current.replace(" ", "").replace("\n", "").toLowerCase();
        if (CONSULT_CUES.stream().anyMatch(text::contains)) {
            return false;
        }
        if (DURATION.matcher(text).find() || FEVER.stream().anyMatch(text::contains)) {
            return false;
        }
        if (text.contains("我是谁") || text.contains("我叫什么")) {
            return false;
        }
        if (GREETING.stream().anyMatch(text::contains) || CHITCHAT.stream().anyMatch(text::contains)) {
            return true;
        }
        return text.length() <= 12;
    }

    public static String casualGuideReply(String current) {
        String hello = current != null && (current.contains("谢谢") || current.contains("多谢"))
                ? "不客气～"
                : "你好呀！我是青禾，你的门诊助手。";
        return hello + "\n\n"
                + "有什么我可以帮你的吗？身体不舒服、用药能不能吃、想了解常见病注意事项，都可以跟我说。\n\n"
                + "也可以直接告诉我，例如：\n"
                + "- 最近哪里不舒服，想了解一下怎么回事\n"
                + "- 想查查某个药能不能吃、怎么吃\n"
                + "- 喉咙痛、低烧、起风团这类症状持续了多久\n\n"
                + "你尽管说。胸痛、大出血、叫不醒请直接去急诊。\n\n"
                + "*教学参考，不是确诊，也不是处方。*";
    }

    public static Slots read(String history, List<MedDisease> diseases, List<MedSymptom> symptoms) {
        String text = history == null ? "" : history.replace(" ", "");
        boolean site = !KeywordRanker.rank(text, diseases, symptoms).isEmpty()
                || BODY_PARTS.stream().anyMatch(text::contains);
        boolean duration = DURATION.matcher(text).find();
        boolean fever = FEVER.stream().anyMatch(text::contains);
        return new Slots(site, duration, fever);
    }

    public static String question(Slots slots, int asked, String candidateCode) {
        if (slots == null || slots.complete() || asked >= MAX_ASKS) {
            return null;
        }
        if (!slots.site()) {
            return "主要是哪里不舒服？比如喉咙、鼻子、肚子或皮肤。";
        }
        if (!slots.duration()) {
            return durationQuestion(candidateCode);
        }
        if (!slots.fever()) {
            return feverQuestion(candidateCode);
        }
        return null;
    }

    private static String durationQuestion(String code) {
        return switch (code == null ? "" : code) {
            case "FLU" -> "高热和全身酸痛大概持续了多久？";
            case "RHINITIS" -> "打喷嚏或清水样鼻涕大概持续了多久？";
            case "COUGH" -> "咳嗽大概持续了多久？";
            default -> "这样大概持续了多久？";
        };
    }

    private static String feverQuestion(String code) {
        return switch (code == null ? "" : code) {
            case "RHINITIS" -> "有没有发烧？清水样鼻涕如果同时发烧，更要和感冒分开看。";
            case "FLU" -> "体温大概多少度？有没有全身酸痛？";
            case "FOOD", "GASTRO" -> "有没有发烧？同餐的人有没有一起不舒服？";
            default -> "有没有发烧？大概多少度？";
        };
    }
}
