package com.commerce.cs.domain.service;

import java.util.List;

/** 把图片观察并进用户原话。观察只是文字，后面仍走红旗和药白名单。 */
public final class SymptomAttachmentText {
    private SymptomAttachmentText() {
    }

    public static String compose(String text, List<String> observations) {
        String words = text == null ? "" : text.trim();
        if (observations == null || observations.isEmpty()) {
            return words;
        }
        StringBuilder notes = new StringBuilder();
        for (String observation : observations) {
            if (observation == null || observation.isBlank()) {
                continue;
            }
            notes.append("- ").append(observation.trim()).append("\n");
        }
        if (notes.isEmpty()) {
            return words;
        }
        StringBuilder merged = new StringBuilder();
        if (!words.isBlank()) {
            merged.append(words).append("\n\n");
        }
        merged.append("图片观察（只供参考，不是诊断）：\n").append(notes);
        return merged.toString().trim();
    }
}
