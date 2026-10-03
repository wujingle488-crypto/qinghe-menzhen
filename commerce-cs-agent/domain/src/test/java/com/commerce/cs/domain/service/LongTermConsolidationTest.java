package com.commerce.cs.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LongTermConsolidationTest {
    @Test
    void decayUsesCreatedAtAndDoesNotCompound() {
        LongTermConsolidation.Fact fact = fact("RHINITIS", "过敏性鼻炎", 1, LocalDateTime.now().minusDays(100));
        List<LongTermConsolidation.Fact> once = LongTermConsolidation.consolidate(List.of(fact), LocalDateTime.now());
        double afterOnce = once.get(0).importance;
        assertTrue(afterOnce < 0.7);
        assertTrue(afterOnce > 0.55);
        List<LongTermConsolidation.Fact> twice = LongTermConsolidation.consolidate(once, LocalDateTime.now());
        assertEquals(afterOnce, twice.get(0).importance, 0.0001);
    }

    @Test
    void identicalMemoriesDedupAndSameDiseaseMerges() {
        LocalDateTime now = LocalDateTime.now();
        LongTermConsolidation.Fact older = fact("RHINITIS", "用户长期有过敏性鼻炎", 0.7, now.minusDays(2));
        LongTermConsolidation.Fact newer = fact("RHINITIS", "用户长期有过敏性鼻炎，清水样鼻涕", 0.9, now.minusDays(1));
        older.sourceSessionIds.add(11L);
        newer.sourceSessionIds.add(22L);
        List<LongTermConsolidation.Fact> kept = LongTermConsolidation.consolidate(new ArrayList<>(List.of(older, newer)), now);
        assertEquals(1, kept.size());
        assertTrue(kept.get(0).content.contains("清水样鼻涕"));
        assertEquals(0.9, kept.get(0).baseImportance, 0.0001);
        assertTrue(kept.get(0).sourceSessionIds.contains(11L));
        assertTrue(kept.get(0).sourceSessionIds.contains(22L));
    }

    @Test
    void oldUnimportantMemoryExpiresAndOldImportantMemoryStays() {
        LocalDateTime now = LocalDateTime.now();
        LongTermConsolidation.Fact weak = fact("URI", "一次普通感冒", 0.2, now.minusDays(40));
        LongTermConsolidation.Fact strong = fact("RHINITIS", "过敏性鼻炎", 0.9, now.minusDays(40));
        List<LongTermConsolidation.Fact> kept = LongTermConsolidation.consolidate(new ArrayList<>(List.of(weak, strong)), now);
        assertEquals(1, kept.size());
        assertEquals("RHINITIS", kept.get(0).diseaseCode);
    }

    @Test
    void rhinitisWarnsWhenThisVisitLooksLikeACold() {
        assertTrue(ChronicProfile.caution("URI", List.of("RHINITIS")).contains("过敏性鼻炎"));
        assertEquals("", ChronicProfile.caution("URTICARIA", List.of("RHINITIS")));
        assertEquals("RHINITIS", ChronicProfile.code("我有过敏性鼻炎很多年", ""));
    }

    private static LongTermConsolidation.Fact fact(String code, String content, double importance, LocalDateTime createdAt) {
        LongTermConsolidation.Fact fact = new LongTermConsolidation.Fact();
        fact.diseaseCode = code;
        fact.content = content;
        fact.baseImportance = importance;
        fact.importance = importance;
        fact.createdAt = createdAt;
        fact.lastAccessedAt = createdAt;
        return fact;
    }
}
