package com.commerce.cs.domain.service;

/** 从用户原话或本次结论里认出长期病。急性病不写进长期档案。 */
public final class ChronicProfile {
    private ChronicProfile() {
    }

    public static String code(String text, String concludedCode) {
        String normalized = text == null ? "" : text;
        if (normalized.contains("鼻炎")) {
            return "RHINITIS";
        }
        if (normalized.contains("偏头痛")) {
            return "MIGRAINE";
        }
        if ((normalized.contains("荨麻疹") || normalized.contains("风团"))
                && (normalized.contains("反复") || normalized.contains("一直") || normalized.contains("多年") || normalized.contains("老毛病"))) {
            return "URTICARIA";
        }
        if ("RHINITIS".equals(concludedCode) || "MIGRAINE".equals(concludedCode)) {
            return concludedCode;
        }
        return "";
    }

    public static String label(String code) {
        return switch (code == null ? "" : code) {
            case "RHINITIS" -> "过敏性鼻炎";
            case "MIGRAINE" -> "偏头痛";
            case "URTICARIA" -> "反复荨麻疹";
            default -> "";
        };
    }

    /** 长期病和这次的方向容易混在一起时，给出对照提醒，不额外给药。 */
    public static String caution(String currentCode, Iterable<String> chronicCodes) {
        boolean rhinitis = has(chronicCodes, "RHINITIS");
        boolean migraine = has(chronicCodes, "MIGRAINE");
        if (rhinitis && ("URI".equals(currentCode) || "FLU".equals(currentCode))) {
            return "你之前说过有过敏性鼻炎。这次如果鼻子痒、清水样鼻涕、不太发烧，要和普通感冒或流感分开看，不要只按感冒处理。";
        }
        if (migraine && "MIGRAINE".equals(currentCode)) {
            return "你之前提到过偏头痛。这次仍只作参考，突然加重、视物不清或颈项发硬要就医。";
        }
        return "";
    }

    private static boolean has(Iterable<String> codes, String expected) {
        if (codes == null) {
            return false;
        }
        for (String code : codes) {
            if (expected.equals(code)) {
                return true;
            }
        }
        return false;
    }
}
