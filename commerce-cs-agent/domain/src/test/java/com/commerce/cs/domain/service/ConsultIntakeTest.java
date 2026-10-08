package com.commerce.cs.domain.service;

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
    void yearsMonthsAndWeeksCountAsDuration() {
        assertTrue(ConsultIntake.read("头爆炸\n五十年", diseases, symptoms).duration());
        assertTrue(ConsultIntake.read("头爆炸\n50年", diseases, symptoms).duration());
        assertTrue(ConsultIntake.read("咳嗽三年了", diseases, symptoms).duration());
        assertTrue(ConsultIntake.read("拉肚子两个月", diseases, symptoms).duration());
        assertTrue(ConsultIntake.read("头痛一周", diseases, symptoms).duration());
        assertTrue(ConsultIntake.read("嗓子疼半小时", diseases, symptoms).duration());
        ConsultIntake.Slots answered = ConsultIntake.read("头爆炸\n五十年", diseases, symptoms);
        assertTrue(answered.site());
        assertTrue(ConsultIntake.question(answered, 1, "").contains("烧"));
        assertFalse(ConsultIntake.question(answered, 1, "").contains("多久"));
        assertFalse(ConsultIntake.read("我五十岁", diseases, symptoms).duration());
        assertFalse(ConsultIntake.read("今年", diseases, symptoms).duration());
    }

    @Test
    void todayIsNotADurationAndWeatherHasNoSite() {
        ConsultIntake.Slots slots = ConsultIntake.read("今天天气怎么样", diseases, symptoms);
        assertFalse(slots.site());
        assertFalse(slots.duration());
        assertTrue(ConsultIntake.question(slots, 0, "").contains("哪里"));
        assertNull(ConsultIntake.question(slots, ConsultIntake.MAX_ASKS, ""));
    }

    @Test
    void symptomSlotsStillDriveFollowUp() {
        ConsultIntake.Slots empty = ConsultIntake.read("", diseases, symptoms);
        assertFalse(empty.site());
        ConsultIntake.Slots cold = ConsultIntake.read("喉咙痛两天", diseases, symptoms);
        assertTrue(cold.site());
        assertTrue(cold.duration());
        ConsultIntake.Slots afterSite = ConsultIntake.read("喉咙痛", diseases, symptoms);
        assertTrue(afterSite.site());
        assertTrue(ConsultIntake.question(afterSite, 0, "URI").contains("多久")
                || ConsultIntake.question(afterSite, 0, "URI").contains("烧"));
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
