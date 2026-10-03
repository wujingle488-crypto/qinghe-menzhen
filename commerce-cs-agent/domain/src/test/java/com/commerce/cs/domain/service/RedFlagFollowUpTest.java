package com.commerce.cs.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RedFlagFollowUpTest {
    @Test
    void newComplaintDoesNotConfirmAndReminderStays() {
        SessionMemory memory = new SessionMemory();
        memory.rememberFlag("胸口疼", "突然胸痛或胸口疼，需要马上就医。");
        assertNull(RedFlagFollowUp.confirms(memory, "身上起了一片风团，很痒"));
        assertNull(RedFlagFollowUp.confirms(memory, "风团还在"));
        RedFlagFollowUp.resolve(memory, "身上起了一片风团，很痒");
        assertEquals(1, memory.openFlags.size());
        assertTrue(RedFlagFollowUp.reminder(memory).contains("胸口疼"));
        assertTrue(RedFlagFollowUp.reminder(memory).contains("120"));
    }

    @Test
    void shortConfirmReopensAndResolvedClears() {
        SessionMemory memory = new SessionMemory();
        memory.rememberFlag("胸口疼", "请立即急诊");
        assertEquals("胸口疼", RedFlagFollowUp.confirms(memory, "还在").phrase());
        assertEquals("胸口疼", RedFlagFollowUp.confirms(memory, "胸口还是疼").phrase());

        RedFlagFollowUp.resolve(memory, "已经好了");
        assertTrue(memory.openFlags.isEmpty());
        assertNull(RedFlagFollowUp.confirms(memory, "还在"));
    }

    @Test
    void negatedPhraseClearsOnlyThatFlag() {
        SessionMemory memory = new SessionMemory();
        memory.rememberFlag("胸口疼", "请立即急诊");
        memory.rememberFlag("呼吸困难", "请立即急诊");
        RedFlagFollowUp.resolve(memory, "没有胸口疼了，身上是风团");
        assertEquals(1, memory.openFlags.size());
        assertEquals("呼吸困难", memory.openFlags.get(0).phrase);
    }

    @Test
    void sayingBothAreGoneClearsThemAndDoesNotReblock() {
        SessionMemory memory = new SessionMemory();
        memory.rememberFlag("胸口疼", "请立即急诊");
        memory.rememberFlag("出冷汗", "请立即急诊");
        String text = "是嗓子疼有两天了 胸口疼和出冷汗不存在了 没有发烧";
        RedFlagFollowUp.resolve(memory, text);
        assertTrue(memory.openFlags.isEmpty());
        com.commerce.cs.domain.entity.MedRedFlag chest = new com.commerce.cs.domain.entity.MedRedFlag();
        chest.setPhrase("胸口疼");
        chest.setMessage("请立即急诊");
        com.commerce.cs.domain.entity.MedRedFlag sweat = new com.commerce.cs.domain.entity.MedRedFlag();
        sweat.setPhrase("出冷汗");
        sweat.setMessage("请立即急诊");
        assertNull(RedFlagFollowUp.scanCurrent(text, java.util.List.of(chest, sweat)));
    }

    @Test
    void oldHistoryRestoresTheUnclosedFlag() {
        SessionMemory memory = new SessionMemory();
        com.commerce.cs.domain.entity.MedRedFlag chest = new com.commerce.cs.domain.entity.MedRedFlag();
        chest.setPhrase("胸口疼");
        chest.setMessage("请立即急诊");
        RedFlagFollowUp.restore(memory, "突然胸口疼，出冷汗\n身上起了一片风团，很痒", "身上起了一片风团，很痒", java.util.List.of(chest));
        assertEquals(1, memory.openFlags.size());
        assertTrue(RedFlagFollowUp.reminder(memory).contains("胸口疼"));
    }
}
