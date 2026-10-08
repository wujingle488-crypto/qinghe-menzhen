package com.commerce.cs.domain.service;

import com.commerce.cs.domain.entity.MedDisease;
import com.commerce.cs.domain.entity.MedSymptom;
import java.util.List;
import java.util.regex.Pattern;

/** 问诊采集：部位、时长、发烧齐了就收口；最多追问三次，用户也可以提前要分析。 */
public final class ConsultIntake {
    public static final int MAX_ASKS = 3;

    /**
     * 病程时长。年、月、周和多位中文数字都要认，例如「五十年」「50年」「两个月」。
     * 「今天」「今年」前面没有数量，不会被当成持续了多久。
     */
    private static final Pattern DURATION = Pattern.compile(
            "(?:[0-9]+(?:\\.[0-9]+)?|[零〇一二两三四五六七八九十百千万半几数多]+)"
                    + "(?:来个|个来|个半|个多|个)?"
                    + "(?:年|个月|月|星期|周|天|日|小时|钟头|分钟)"
                    + "|好久|持续了?|半天|刚才|刚刚|昨晚|昨夜|昨天|前天|去年|前年|上周|上星期|上个月|最近");
    private static final List<String> FEVER = List.of(
            "不发烧", "没发烧", "没有发烧", "不发热", "没发热", "没有发热",
            "低烧", "高烧", "低热", "高热", "发烧", "发热", "体温", "没烧");
    private static final List<String> STOP = List.of(
            "先给分析", "直接分析", "不用问了", "别问了", "给我分析", "直接给分析");

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
