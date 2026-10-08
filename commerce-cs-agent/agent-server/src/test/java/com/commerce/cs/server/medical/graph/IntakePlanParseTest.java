package com.commerce.cs.server.medical.graph;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntakePlanParseTest {

    @Test
    void askKeepsTheModelQuestion() {
        ConsultSupport.IntakePlan plan = ConsultSupport.parseIntake("ASK\n这个 70° 是体温计上的读数吗？");
        assertTrue(plan.ask());
        assertEquals("这个 70° 是体温计上的读数吗？", plan.question());
    }

    @Test
    void blankOrAnswerDoesNotBecomeATemplateQuestion() {
        assertFalse(ConsultSupport.parseIntake(null).ask());
        assertFalse(ConsultSupport.parseIntake("").ask());
        assertFalse(ConsultSupport.parseIntake("ANSWER").ask());
        assertFalse(ConsultSupport.parseIntake("ASK").ask());
        assertFalse(ConsultSupport.parseIntake("拿不准，先答").ask());
    }
}
