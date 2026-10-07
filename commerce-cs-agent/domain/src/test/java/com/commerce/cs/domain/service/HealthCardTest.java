package com.commerce.cs.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HealthCardTest {
    @Test
    void completenessAndBmi() {
        Map<String, Object> card = HealthCard.empty();
        assertEquals(0, HealthCard.completeness(card));
        card.put("gender", "女");
        card.put("age", "28");
        card.put("height_cm", "160");
        card.put("weight_kg", "56");
        card.put("allergies", "无");
        card.put("chronic", "无");
        card.put("medications", "无");
        assertEquals(100, HealthCard.completeness(card));
        assertEquals(21.9, HealthCard.bmi(card));
        assertEquals("正常", HealthCard.bmiClass(HealthCard.bmi(card)));
        assertTrue(HealthCard.summary(card).contains("成人"));
    }

    @Test
    void emptyCardGivesNoConsultContext() {
        Map<String, Object> card = HealthCard.empty();
        assertEquals("", HealthCard.consultContext(card));
        assertTrue(HealthCard.consultIncluded(card).isEmpty());
        assertTrue(HealthCard.consultDigest(card).stream().allMatch(row -> "未登记".equals(row.get("value"))));
    }

    @Test
    void digestShowsBodyMetricsButHidesDiseaseNames() {
        Map<String, Object> card = HealthCard.empty();
        card.put("gender", "男");
        card.put("age", "35");
        card.put("height_cm", "175");
        card.put("weight_kg", "95");
        card.put("chronic", "高血压");
        card.put("medications", "氨氯地平 5mg 每日一次");
        card.put("allergies", "青霉素");

        String digest = HealthCard.consultDigest(card).toString();
        assertFalse(digest.contains("高血压"), digest);
        assertFalse(digest.contains("青霉素"), digest);
        assertTrue(digest.contains("成年人"), digest);
        assertTrue(digest.contains("身高175cm"), digest);
        assertTrue(digest.contains("体重95kg"), digest);
        assertTrue(digest.contains("BMI 31.0"), digest);
        assertTrue(digest.contains("肥胖"), digest);
        assertTrue(digest.contains("存在已登记药物"), digest);
        assertEquals(List.of("基本情况", "既往记录", "用药信息", "过敏信息"), HealthCard.consultIncluded(card));

        String context = HealthCard.consultContext(card, "李四");
        assertTrue(context.startsWith("[就诊卡]"), context);
        assertTrue(context.contains("姓名：李四"), context);
        assertTrue(context.contains("35岁"), context);
        assertTrue(context.contains("身高cm：175"), context);
        assertTrue(context.contains("体重kg：95"), context);
        assertTrue(context.contains("BMI：31.0（肥胖）"), context);
        assertTrue(context.contains("青霉素"), context);
        assertTrue(context.contains("高血压"), context);
        assertTrue(context.contains("必须按上面直接回答"), context);
    }
}
