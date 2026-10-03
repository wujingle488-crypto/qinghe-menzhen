package com.commerce.cs.domain.service;

import com.commerce.cs.domain.entity.MedDisease;
import com.commerce.cs.domain.entity.MedSymptom;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 底账上的关键词打分。ES 不可用时，检索门面用它顶上。 */
public final class KeywordRanker {
    private KeywordRanker() {
    }

    public record Ranked(String diseaseCode, int score) {
    }

    public static List<Ranked> rank(String text, List<MedDisease> diseases, List<MedSymptom> symptoms) {
        String query = text == null ? "" : text.replace(" ", "");
        Map<String, Integer> scores = new HashMap<>();
        if (diseases != null) {
            for (MedDisease disease : diseases) {
                int score = disease.getName() != null && query.contains(disease.getName()) ? 3 : 0;
                scores.put(disease.getCode(), score);
            }
        }
        if (symptoms != null) {
            for (MedSymptom symptom : symptoms) {
                if (symptom.getDiseaseCode() == null || symptom.getAliases() == null) {
                    continue;
                }
                for (String alias : symptom.getAliases().split("[,，]")) {
                    String token = alias.trim();
                    if (token.length() >= 2 && query.contains(token)) {
                        scores.merge(symptom.getDiseaseCode(), 2, Integer::sum);
                    }
                }
            }
        }
        return scores.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .sorted(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed())
                .map(entry -> new Ranked(entry.getKey(), entry.getValue()))
                .toList();
    }
}
