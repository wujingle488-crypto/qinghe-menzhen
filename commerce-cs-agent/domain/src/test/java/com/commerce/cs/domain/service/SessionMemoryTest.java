package com.commerce.cs.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SessionMemoryTest {
    @Test
    void askRemembersTheGap() {
        SessionMemory memory = new SessionMemory();
        memory.sync(new ConsultIntake.Slots(true, false, false), "URTICARIA", "荨麻疹样过敏");
        memory.ask("这样大概持续了多久？");
        assertEquals(1, memory.askCount);
        assertTrue(memory.summary().contains("已追问 1 次"));
        assertTrue(memory.summary().contains("还缺持续了多久、有没有发烧"));
        assertTrue(memory.summary().contains("荨麻疹样过敏"));
        assertTrue(memory.summary().contains("还没有给过参考方向"));
    }

    @Test
    void laterTurnKeepsThePreviousDirection() {
        SessionMemory memory = new SessionMemory();
        memory.sync(new ConsultIntake.Slots(true, false, false), "URTICARIA", "荨麻疹样过敏");
        memory.conclude("荨麻疹样过敏", "", "信息未齐，未推荐药品");
        memory.supplement("两天了，不发烧");
        assertTrue(memory.promptContext().contains("上次参考方向是荨麻疹样过敏"));
        assertTrue(memory.promptContext().contains("未推荐药品"));
        assertTrue(memory.promptContext().contains("两天了，不发烧"));
        assertFalse(memory.summary().contains("还没有给过参考方向"));
    }
}
