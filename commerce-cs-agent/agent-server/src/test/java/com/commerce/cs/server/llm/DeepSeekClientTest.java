package com.commerce.cs.server.llm;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class DeepSeekClientTest {
    @Test
    void withClockAppendsShanghaiDate() {
        String now = ZonedDateTime.now(ZoneId.of("Asia/Shanghai"))
                .format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA));
        String prompted = DeepSeekClient.withClock("你是助手。");
        assertTrue(prompted.contains("你是助手。"), prompted);
        assertTrue(prompted.contains("【系统时钟】"), prompted);
        assertTrue(prompted.contains(now), prompted);
        assertTrue(prompted.contains("禁止编造其它日期"), prompted);
    }
}
