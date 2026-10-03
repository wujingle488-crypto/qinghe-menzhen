package com.commerce.cs.domain.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reciprocal Rank Fusion。同一条资料在多路排序里越靠前，融合分越高。
 * score(d) = Σ 1 / (k + rank)，rank 从 1 开始，k 默认 60。
 */
public final class RrfFusion {
    public static final int DEFAULT_K = 60;

    public record Scored(String key, double score) {
    }

    private RrfFusion() {
    }

    public static List<Scored> fuse(List<List<String>> rankings) {
        return fuse(rankings, DEFAULT_K);
    }

    public static List<Scored> fuse(List<List<String>> rankings, int k) {
        int constant = k < 1 ? DEFAULT_K : k;
        Map<String, Double> scores = new LinkedHashMap<>();
        if (rankings == null) {
            return List.of();
        }
        for (List<String> ranking : rankings) {
            if (ranking == null) {
                continue;
            }
            int rank = 1;
            Set<String> seen = new HashSet<>();
            for (String key : ranking) {
                if (key == null || key.isBlank() || !seen.add(key)) {
                    continue;
                }
                scores.merge(key, 1.0 / (constant + rank), Double::sum);
                rank++;
            }
        }
        List<Map.Entry<String, Double>> ordered = new ArrayList<>(scores.entrySet());
        ordered.sort(Comparator.<Map.Entry<String, Double>>comparingDouble(Map.Entry::getValue).reversed()
                .thenComparing(Map.Entry::getKey));
        return ordered.stream().map(entry -> new Scored(entry.getKey(), entry.getValue())).toList();
    }
}
