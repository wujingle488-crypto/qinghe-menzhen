package com.commerce.cs.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SymptomAttachmentTextTest {
    @Test
    void imageNoteFollowsTheUsersWords() {
        String merged = SymptomAttachmentText.compose("身上起风团", List.of("前臂有一片红色风团。"));
        assertTrue(merged.startsWith("身上起风团"));
        assertTrue(merged.contains("前臂有一片红色风团"));
        assertTrue(merged.contains("不是诊断"));
    }

    @Test
    void blankImageDoesNotChangeTheText() {
        assertEquals("喉咙痛", SymptomAttachmentText.compose("喉咙痛", List.of("  ", "")));
    }
}
