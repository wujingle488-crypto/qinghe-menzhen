package com.commerce.cs.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.commerce.cs.domain.entity.MedDisease;
import com.commerce.cs.domain.entity.MedSymptom;
import java.util.List;
import org.junit.jupiter.api.Test;

class KeywordRankerTest {
    @Test
    void soreThroatMatchesColdNotFlu() {
        MedDisease cold = disease("URI", "普通感冒样上呼吸道感染");
        MedDisease flu = disease("FLU", "流感样症状");
        MedSymptom coldWords = symptom("URI", "喉咙痛,咽痛,低烧,流涕");
        MedSymptom fluWords = symptom("FLU", "高烧,寒战,全身酸痛,流感");
        List<KeywordRanker.Ranked> ranked = KeywordRanker.rank(
                "喉咙痛，低烧两天", List.of(cold, flu), List.of(coldWords, fluWords));
        assertEquals("URI", ranked.get(0).diseaseCode());
    }

    @Test
    void rhinitisIsNotLockedByShortAllergyAlias() {
        MedDisease hives = disease("URTICARIA", "荨麻疹样过敏");
        MedDisease rhinitis = disease("RHINITIS", "过敏性鼻炎");
        MedSymptom hiveWords = symptom("URTICARIA", "风团,荨麻疹,很痒,皮疹");
        MedSymptom noseWords = symptom("RHINITIS", "过敏性鼻炎,清水样鼻涕,鼻子痒,花粉,连续打喷嚏");
        List<KeywordRanker.Ranked> ranked = KeywordRanker.rank(
                "过敏性鼻炎，清水样鼻涕，不发烧", List.of(hives, rhinitis), List.of(hiveWords, noseWords));
        assertFalse(ranked.isEmpty());
        assertEquals("RHINITIS", ranked.get(0).diseaseCode());
    }

    private static MedDisease disease(String code, String name) {
        MedDisease disease = new MedDisease();
        disease.setCode(code);
        disease.setName(name);
        disease.setSummary("摘要");
        disease.setAdvice("建议");
        disease.setSourceName("来源");
        disease.setSourceUrl("https://www.chinacdc.cn/");
        return disease;
    }

    private static MedSymptom symptom(String code, String aliases) {
        MedSymptom symptom = new MedSymptom();
        symptom.setDiseaseCode(code);
        symptom.setAliases(aliases);
        return symptom;
    }
}
