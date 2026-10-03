package com.commerce.cs.domain.service;

import com.commerce.cs.domain.entity.MedRedFlag;
import java.util.List;

/**
 * 红旗只拦截当前这句话。旧的紧急情况留在会话记忆里，等这次别的主诉收口后再追问是否还在。
 * 用户明确说已经缓解或已就医，就清掉，不再重复拦截。
 */
public final class RedFlagFollowUp {
    private RedFlagFollowUp() {
    }

    public static void resolve(SessionMemory memory, String text) {
        if (memory == null || memory.openFlags == null || memory.openFlags.isEmpty() || text == null || text.isBlank()) {
            return;
        }
        String normalized = text.replace(" ", "");
        if (plainlyResolved(normalized)) {
            memory.clearFlags();
            return;
        }
        memory.openFlags.removeIf(flag -> negated(normalized, flag.phrase));
    }

    /**
     * 旧会话只在观察里记过红旗、没有 openFlags 时，按用户原话回放一遍。
     * 后面如果已经说过缓解，回放会清掉，不会再追问。
     */
    public static void restore(SessionMemory memory, String history, String current, List<MedRedFlag> flags) {
        if (memory == null || (memory.openFlags != null && !memory.openFlags.isEmpty()) || history == null || history.isBlank()) {
            return;
        }
        if (memory.openFlags == null) {
            memory.openFlags = new java.util.ArrayList<>();
        }
        for (String line : history.split("\n")) {
            String text = line.trim();
            if (text.isEmpty() || text.equals(current == null ? "" : current.trim())) {
                continue;
            }
            resolve(memory, text);
            SafetyGate.Hit hit = SafetyGate.scan(text, flags);
            if (hit != null) {
                memory.rememberFlag(hit.phrase(), hit.message());
            }
        }
    }

    /** 短句确认旧红旗还在，且没有带上新的主诉。 */
    public static SafetyGate.Hit confirms(SessionMemory memory, String text) {
        if (memory == null || memory.openFlags == null || memory.openFlags.isEmpty() || text == null || text.isBlank()) {
            return null;
        }
        String normalized = text.replace(" ", "").replaceAll("[。！？!?,，、]", "");
        if (normalized.length() > 16 || !normalized.matches(".*(还在|还是|仍然|一直|没好|没有好转|更严重|还疼|还痛).*")) {
            return null;
        }
        String rest = normalized;
        for (String word : List.of("还在", "还是", "仍然", "一直", "没好", "没有好转", "更严重", "还疼", "还痛", "的", "了", "啊", "呀", "呢", "吧", "很", "都")) {
            rest = rest.replace(word, "");
        }
        for (SessionMemory.OpenFlag flag : memory.openFlags) {
            if (flag.phrase != null) {
                rest = rest.replace(flag.phrase, "");
                rest = rest.replace(flag.phrase.replace("疼", "").replace("痛", ""), "");
            }
        }
        if (!rest.isEmpty()) {
            return null;
        }
        SessionMemory.OpenFlag first = memory.openFlags.get(0);
        return new SafetyGate.Hit(first.phrase, first.message);
    }

    public static String reminder(SessionMemory memory) {
        if (memory == null || memory.flagReminded || memory.openFlags == null || memory.openFlags.isEmpty()) {
            return "";
        }
        StringBuilder text = new StringBuilder("\n\n> 另外确认一下：");
        int i = 0;
        for (SessionMemory.OpenFlag flag : memory.openFlags) {
            if (i++ > 0) {
                text.append("、");
            }
            text.append("「").append(flag.phrase).append("」");
        }
        text.append("现在还在吗？如果还在，仍需要马上就医或拨打 **120**。");
        return text.toString();
    }

    public static SafetyGate.Hit scanCurrent(String text, List<MedRedFlag> flags) {
        return SafetyGate.scan(text, flags);
    }

    private static boolean negated(String text, String phrase) {
        if (phrase == null || phrase.isBlank() || !text.contains(phrase)) {
            return false;
        }
        return !SafetyGate.mentioned(text, phrase);
    }

    private static boolean plainlyResolved(String text) {
        String compact = text.replaceAll("[。！？!?,，、]", "");
        if (compact.length() > 16) {
            return false;
        }
        return compact.matches(".*(已经好了|好一些了|好了|不疼了|不痛了|缓解了|没事了|去过急诊|看过急诊|已经就医|去医院了|看过医生).*");
    }
}
