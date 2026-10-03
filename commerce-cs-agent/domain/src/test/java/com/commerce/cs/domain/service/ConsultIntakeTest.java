package com.commerce.cs.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.commerce.cs.domain.entity.MedDisease;
import com.commerce.cs.domain.entity.MedSymptom;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConsultIntakeTest {
    private final List<MedDisease> diseases = List.of(disease("URI", "普通感冒样上呼吸道感染"));
    private final List<MedSymptom> symptoms = List.of(symptom("URI", "喉咙痛,咽痛,低烧,流涕"));

    @Test
    void fullColdCanCloseImmediately() {
        ConsultIntake.Slots slots = ConsultIntake.read("喉咙痛，低烧两天", diseases, symptoms);
        assertTrue(slots.complete());
        assertFalse(slots.thin());
        assertNull(ConsultIntake.question(slots, 0, "URI"));
    }

    @Test
    void todayIsNotADurationAndWeatherHasNoSite() {
        ConsultIntake.Slots slots = ConsultIntake.read("今天天气怎么样", diseases, symptoms);
        assertFalse(slots.site());
        assertFalse(slots.duration());
        assertTrue(ConsultIntake.isCasualChat("今天天气怎么样", slots));
        assertTrue(ConsultIntake.casualGuideReply("今天天气怎么样").contains("哪里不舒服"));
        assertTrue(ConsultIntake.question(slots, 0, "").contains("哪里"));
        assertNull(ConsultIntake.question(slots, ConsultIntake.MAX_ASKS, ""));
    }

    @Test
    void greetingIsCasualAndSymptomStartsConsult() {
        ConsultIntake.Slots empty = ConsultIntake.read("", diseases, symptoms);
        assertTrue(ConsultIntake.isCasualChat("你好", empty));
        assertTrue(ConsultIntake.isCasualChat("在吗", empty));
        assertFalse(ConsultIntake.isCasualChat("喉咙痛两天", empty));
        assertFalse(ConsultIntake.isCasualChat("有点不舒服", empty));
        ConsultIntake.Slots afterSite = ConsultIntake.read("喉咙痛", diseases, symptoms);
        assertFalse(ConsultIntake.isCasualChat("嗯", afterSite));
    }

    @Test
    void hivesAskDurationBeforeFever() {
        List<MedSymptom> hiveWords = List.of(symptom("URTICARIA", "风团,很痒"));
        ConsultIntake.Slots slots = ConsultIntake.read("身上起了一片风团，很痒", List.of(), hiveWords);
        assertTrue(slots.site());
        assertFalse(slots.complete());
        assertTrue(slots.thin());
        assertTrue(ConsultIntake.question(slots, 0, "URTICARIA").contains("多久"));
    }

    @Test
    void stopPhrasesAreRecognized() {
        assertTrue(ConsultIntake.stopRequested("先给分析"));
        assertTrue(ConsultIntake.stopRequested("不用问了"));
        assertFalse(ConsultIntake.stopRequested("喉咙痛两天"));
    }

    private static MedDisease disease(String code, String name) {
        MedDisease disease = new MedDisease();
        disease.setCode(code);
        disease.setName(name);
        return disease;
    }

    private static MedSymptom symptom(String code, String aliases) {
        MedSymptom symptom = new MedSymptom();
        symptom.setDiseaseCode(code);
        symptom.setAliases(aliases);
        return symptom;
    }
}
