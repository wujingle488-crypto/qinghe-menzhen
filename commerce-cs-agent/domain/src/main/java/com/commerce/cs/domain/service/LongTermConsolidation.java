package com.commerce.cs.domain.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 长期记忆整理：按创建时间做幂等衰减，高相似去重，中等相似合并，又旧又不重要才删除。
 * 衰减写回的是展示用重要度，底数不变，所以重复跑不会越衰减越狠。
 */
public final class LongTermConsolidation {
    public static final double DECAY_RATE = 0.995;
    public static final double MIN_DECAY_DELTA = 0.01;
    public static final double DEDUP_THRESHOLD = 0.95;
    public static final double MERGE_THRESHOLD = 0.80;
    public static final int TTL_DAYS = 30;
    public static final double MIN_IMPORTANCE = 0.3;

    private LongTermConsolidation() {
    }

    public static final class Fact {
        public Long id;
        public String diseaseCode = "";
        public String content = "";
        public double baseImportance = 0.5;
        public double importance = 0.5;
        public LocalDateTime createdAt = LocalDateTime.now();
        public LocalDateTime lastAccessedAt = LocalDateTime.now();
        public double[] vector;
        /** 这条长期记忆来自哪些问诊会话。删会话时只去掉对应来源。 */
        public java.util.Set<Long> sourceSessionIds = new java.util.LinkedHashSet<>();

        public Fact() {
        }
    }

    public static List<Fact> consolidate(List<Fact> source, LocalDateTime now) {
        List<Fact> items = new ArrayList<>();
        if (source != null) {
            items.addAll(source);
        }
        LocalDateTime clock = now == null ? LocalDateTime.now() : now;
        decay(items, clock);
        boolean[] removed = new boolean[items.size()];
        for (int i = 0; i < items.size(); i++) {
            if (removed[i]) {
                continue;
            }
            for (int j = i + 1; j < items.size(); j++) {
                if (removed[j]) {
                    continue;
                }
                double sim = similarity(items.get(i), items.get(j));
                if (sim >= DEDUP_THRESHOLD) {
                    if (items.get(j).importance >= items.get(i).importance) {
                        absorb(items.get(j), items.get(i));
                        removed[i] = true;
                        break;
                    }
                    absorb(items.get(i), items.get(j));
                    removed[j] = true;
                } else if (sim >= MERGE_THRESHOLD) {
                    items.set(i, merge(items.get(i), items.get(j), clock));
                    removed[j] = true;
                }
            }
        }
        List<Fact> kept = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            if (removed[i] || expired(items.get(i), clock)) {
                continue;
            }
            kept.add(items.get(i));
        }
        return kept;
    }

    public static double effective(double base, long days) {
        return base * Math.pow(DECAY_RATE, Math.max(days, 0));
    }

    private static void decay(List<Fact> items, LocalDateTime now) {
        for (Fact item : items) {
            if (item.createdAt == null) {
                item.createdAt = now;
            }
            long days = Math.max(0, Duration.between(item.createdAt, now).toHours() / 24);
            double updated = effective(item.baseImportance, days);
            if (Math.abs(item.importance - updated) >= MIN_DECAY_DELTA) {
                item.importance = updated;
            }
        }
    }

    private static boolean expired(Fact item, LocalDateTime now) {
        long days = item.createdAt == null ? 0 : Math.max(0, Duration.between(item.createdAt, now).toHours() / 24);
        return days > TTL_DAYS && item.importance < MIN_IMPORTANCE;
    }

    static double similarity(Fact left, Fact right) {
        if (left == null || right == null) {
            return 0;
        }
        String a = left.content == null ? "" : left.content.trim();
        String b = right.content == null ? "" : right.content.trim();
        if (!a.isEmpty() && a.equals(b)) {
            return 1;
        }
        double vectorScore = cosine(left.vector, right.vector);
        if (sameDisease(left, right)) {
            return Math.max(vectorScore, 0.90);
        }
        if (!a.isEmpty() && !b.isEmpty() && (a.contains(b) || b.contains(a))) {
            return Math.max(vectorScore, 0.90);
        }
        return vectorScore;
    }

    private static Fact merge(Fact base, Fact other, LocalDateTime now) {
        Fact kept = base.baseImportance >= other.baseImportance ? base : other;
        Fact dropped = kept == base ? other : base;
        Fact merged = new Fact();
        merged.id = kept.id;
        merged.diseaseCode = kept.diseaseCode == null || kept.diseaseCode.isBlank() ? dropped.diseaseCode : kept.diseaseCode;
        merged.content = combine(kept.content, dropped.content);
        merged.baseImportance = Math.max(base.baseImportance, other.baseImportance);
        merged.importance = Math.max(base.importance, other.importance);
        merged.createdAt = earlier(base.createdAt, other.createdAt);
        merged.lastAccessedAt = now;
        merged.vector = average(base.vector, other.vector, base.importance, other.importance);
        absorb(merged, base);
        absorb(merged, other);
        return merged;
    }

    static void absorb(Fact kept, Fact dropped) {
        if (kept == null || dropped == null || kept == dropped) {
            return;
        }
        if (kept.sourceSessionIds == null) {
            kept.sourceSessionIds = new java.util.LinkedHashSet<>();
        }
        if (dropped.sourceSessionIds != null) {
            kept.sourceSessionIds.addAll(dropped.sourceSessionIds);
        }
    }

    private static String combine(String left, String right) {
        String a = left == null ? "" : left.trim();
        String b = right == null ? "" : right.trim();
        if (a.isEmpty()) {
            return clip(b);
        }
        if (b.isEmpty() || a.contains(b)) {
            return clip(a);
        }
        if (b.contains(a)) {
            return clip(b);
        }
        return clip(a + "；" + b);
    }

    private static String clip(String text) {
        return text.length() <= 400 ? text : text.substring(0, 400);
    }

    private static LocalDateTime earlier(LocalDateTime left, LocalDateTime right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isBefore(right) ? left : right;
    }

    private static boolean sameDisease(Fact left, Fact right) {
        return left.diseaseCode != null && !left.diseaseCode.isBlank() && left.diseaseCode.equals(right.diseaseCode);
    }

    static double cosine(double[] left, double[] right) {
        if (left == null || right == null || left.length == 0 || left.length != right.length) {
            return 0;
        }
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return 0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private static double[] average(double[] left, double[] right, double leftWeight, double rightWeight) {
        if (left == null || left.length == 0) {
            return right;
        }
        if (right == null || right.length != left.length) {
            return left;
        }
        double sum = leftWeight + rightWeight;
        if (sum <= 0) {
            sum = 2;
            leftWeight = 1;
            rightWeight = 1;
        }
        double[] merged = new double[left.length];
        for (int i = 0; i < left.length; i++) {
            merged[i] = (left[i] * leftWeight + right[i] * rightWeight) / sum;
        }
        return merged;
    }
}
