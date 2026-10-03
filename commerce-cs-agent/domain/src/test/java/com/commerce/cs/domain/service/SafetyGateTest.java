package com.commerce.cs.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.commerce.cs.domain.entity.MedDrug;
import com.commerce.cs.domain.entity.MedRedFlag;
import java.util.List;
import org.junit.jupiter.api.Test;

class SafetyGateTest {
    @Test
    void chestPainIsRedFlagAndNegationIsNot() {
        MedRedFlag chest = flag("胸口疼", "请立即急诊");
        MedRedFlag breath = flag("呼吸困难", "请立即急诊");
        assertEquals("胸口疼", SafetyGate.scan("突然胸口疼，出冷汗", List.of(chest, breath)).phrase());
        assertNull(SafetyGate.scan("没有呼吸困难，只是鼻子堵", List.of(breath)));
        MedRedFlag sweat = flag("出冷汗", "请立即急诊");
        assertNull(SafetyGate.scan("是嗓子疼有两天了 胸口疼和出冷汗不存在了 没有发烧", List.of(chest, sweat, breath)));
        assertEquals("胸口疼", SafetyGate.scan("胸口疼，但是出冷汗不存在了", List.of(chest, sweat)).phrase());
    }

    @Test
    void unknownDrugIsDropped() {
        MedDrug known = drug("对乙酰氨基酚");
        List<MedDrug> kept = SafetyGate.allow(List.of("对乙酰氨基酚", "奥司他韦"), List.of(known));
        assertEquals(1, kept.size());
        assertEquals("对乙酰氨基酚", kept.get(0).getName());
        assertTrue(SafetyGate.allow(List.of("奥司他韦"), List.of(known)).isEmpty());
    }

    private static MedRedFlag flag(String phrase, String message) {
        MedRedFlag flag = new MedRedFlag();
        flag.setPhrase(phrase);
        flag.setMessage(message);
        return flag;
    }

    private static MedDrug drug(String name) {
        MedDrug drug = new MedDrug();
        drug.setName(name);
        drug.setDiseaseCode("URI");
        drug.setUsageText("按说明书");
        drug.setCaution("肝病慎用");
        drug.setSourceName("说明书要点");
        drug.setSourceUrl("https://www.nmpa.gov.cn/");
        return drug;
    }
}
