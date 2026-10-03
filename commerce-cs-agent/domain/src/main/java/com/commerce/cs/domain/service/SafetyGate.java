package com.commerce.cs.domain.service;

import com.commerce.cs.domain.entity.MedDrug;
import com.commerce.cs.domain.entity.MedRedFlag;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 红旗与药白名单。这两项不能因为检索或模型失败而关掉。 */
public final class SafetyGate {
    private SafetyGate() {
    }

    public record Hit(String phrase, String message) {
    }

    public static Hit scan(String text, List<MedRedFlag> flags) {
        if (text == null || text.isBlank() || flags == null) {
            return null;
        }
        String normalized = text.replace(" ", "");
        for (MedRedFlag flag : flags) {
            String phrase = flag.getPhrase();
            if (phrase == null || phrase.isBlank()) {
                continue;
            }
            if (mentioned(normalized, phrase.trim())) {
                return new Hit(phrase.trim(), flag.getMessage());
            }
        }
        return null;
    }

    public static List<MedDrug> allow(List<String> names, List<MedDrug> whitelist) {
        Map<String, MedDrug> byName = new LinkedHashMap<>();
        if (whitelist != null) {
            for (MedDrug drug : whitelist) {
                if (drug.getName() != null) {
                    byName.putIfAbsent(drug.getName().trim(), drug);
                }
            }
        }
        List<MedDrug> kept = new ArrayList<>();
        if (names == null) {
            return kept;
        }
        for (String name : names) {
            if (name == null) {
                continue;
            }
            MedDrug drug = byName.get(name.trim());
            if (drug != null && kept.stream().noneMatch(item -> item.getName().equals(drug.getName()))) {
                kept.add(drug);
            }
        }
        return kept;
    }

    static boolean mentioned(String text, String phrase) {
        int from = 0;
        while (from <= text.length() - phrase.length()) {
            int index = text.indexOf(phrase, from);
            if (index < 0) {
                return false;
            }
            if (!negated(text, index) && !deniedLater(text, index + phrase.length())) {
                return true;
            }
            from = index + phrase.length();
        }
        return false;
    }

    private static boolean negated(String text, int index) {
        int start = Math.max(0, index - 3);
        String prefix = text.substring(start, index).toLowerCase(Locale.ROOT);
        return prefix.contains("没") || prefix.contains("无") || prefix.endsWith("不") || prefix.endsWith("未");
    }

    /**
     * 「胸口疼和出冷汗不存在了」否定在症状后面，中间用「和」带上前一个症状。
     * 「突然胸口疼」后面没有这类说法，仍然算正在发生。
     */
    private static boolean deniedLater(String text, int end) {
        int windowEnd = Math.min(text.length(), end + 12);
        String gap = text.substring(end, windowEnd);
        int denial = denialAt(gap);
        if (denial < 0) {
            return false;
        }
        String between = gap.substring(0, denial);
        if (between.contains("但是") || between.contains("不过") || between.contains("可是") || between.contains("然而")
                || between.contains("两天") || between.contains("发烧")) {
            return false;
        }
        return between.length() <= 8;
    }

    private static int denialAt(String gap) {
        int best = -1;
        for (String marker : List.of("不存在", "没有了", "没了", "已经没有", "不在了", "没有出现")) {
            int at = gap.indexOf(marker);
            if (at >= 0 && (best < 0 || at < best)) {
                best = at;
            }
        }
        return best;
    }
}
