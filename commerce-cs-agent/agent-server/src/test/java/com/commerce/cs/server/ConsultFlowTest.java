package com.commerce.cs.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.commerce.cs.domain.entity.ChatMessage;
import com.commerce.cs.domain.entity.ChatSession;
import com.commerce.cs.domain.entity.ConsultTask;
import com.commerce.cs.domain.repo.ChatMessageRepository;
import com.commerce.cs.domain.repo.UserMemoryRepository;
import com.commerce.cs.server.medical.ConsultOrchestrator;
import com.commerce.cs.server.medical.ConsultTaskStore;
import com.commerce.cs.server.web.SessionService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "llm.enabled=false")
class ConsultFlowTest {
    @Autowired
    private ConsultOrchestrator consult;
    @Autowired
    private SessionService sessions;
    @Autowired
    private UserMemoryRepository userMemories;
    @Autowired
    private ConsultTaskStore taskStore;
    @Autowired
    private ChatMessageRepository messages;
    @Autowired
    private com.commerce.cs.server.medical.HealthProfileService profiles;

    @BeforeEach
    void clearDemoMemories() {
        userMemories.deleteByUserId(1L);
        userMemories.deleteByUserId(42L);
    }

    @Test
    void coldGetsWhitelistedDrugAndChestPainGetsNone() {
        ChatSession cold = sessions.openConsult(1L);
        ConsultOrchestrator.TurnResult coldTurn = consult.handle(cold.getId(), "喉咙痛，低烧两天");
        assertTrue(coldTurn.reply().contains("普通感冒"));
        List<Map<String, Object>> drugs = drugsOf(coldTurn);
        assertEquals("对乙酰氨基酚", drugs.get(0).get("name"));
        assertTrue(String.valueOf(coldTurn.card().get("memory")).contains("都已了解"));
        assertTrue(Boolean.TRUE.equals(coldTurn.card().get("degraded")));
        assertEquals(false, coldTurn.card().get("graphDegraded"));
        assertEquals(false, coldTurn.card().get("vectorDegraded"), String.valueOf(coldTurn.card().get("modeNote")));
        assertTrue(String.valueOf(coldTurn.card().get("modeNote")).contains("RRF"));
        String channel = String.valueOf(evidencesOf(coldTurn).get(0).get("channel"));
        assertTrue(channel.contains("向量") && channel.contains("图谱") && channel.contains("关键词"), channel);

        ChatSession emergency = sessions.openConsult(1L);
        ConsultOrchestrator.TurnResult emergencyTurn = consult.handle(emergency.getId(), "突然胸口疼，出冷汗");
        assertTrue(drugsOf(emergencyTurn).isEmpty());
        assertEquals(true, emergencyTurn.card().get("redFlag"));
        assertTrue(emergencyTurn.reply().contains("120"));
    }

    @Test
    void laterSymptomIsAnsweredThenAsksIfEmergencyRemains() {
        ChatSession session = sessions.openConsult(1L);
        ConsultOrchestrator.TurnResult emergency = consult.handle(session.getId(), "突然胸口疼，出冷汗");
        assertEquals(true, emergency.card().get("redFlag"));

        ConsultOrchestrator.TurnResult hives = consult.handle(session.getId(), "身上起了一片风团，很痒");
        assertTrue(hives.card() == null, hives.reply());
        assertTrue(hives.reply().contains("多久"), hives.reply());
        assertFalse(hives.reply().contains("120"), hives.reply());

        consult.handle(session.getId(), "两天了");
        ConsultOrchestrator.TurnResult closed = consult.handle(session.getId(), "不发烧");
        assertTrue(closed.card() != null, closed.reply());
        assertEquals(false, closed.card().get("redFlag"));
        assertTrue(closed.reply().contains("荨麻疹") || closed.reply().contains("氯雷他定"), closed.reply());
        assertFalse(closed.reply().contains("另外确认一下"), closed.reply());

        ConsultOrchestrator.TurnResult still = consult.handle(session.getId(), "还在");
        assertEquals(true, still.card().get("redFlag"));
        assertTrue(still.reply().contains("马上就医") || still.reply().contains("120"), still.reply());
    }

    @Test
    void allergyAsksThenKeepsCaution() {
        ChatSession session = sessions.openConsult(1L);
        ConsultOrchestrator.TurnResult first = consult.handle(session.getId(), "身上起了一片风团，很痒");
        assertTrue(first.card() == null);
        assertTrue(first.reply().contains("多久"));

        ConsultOrchestrator.TurnResult second = consult.handle(session.getId(), "两天了");
        assertTrue(second.card() == null);
        assertTrue(second.reply().contains("发烧"));

        ConsultOrchestrator.TurnResult turn = consult.handle(session.getId(), "不发烧");
        List<Map<String, Object>> drugs = drugsOf(turn);
        assertEquals("氯雷他定", drugs.get(0).get("name"));
        assertTrue(String.valueOf(drugs.get(0).get("caution")).contains("呼吸困难"));
        assertTrue(String.valueOf(turn.card().get("memory")).contains("已追问 2 次"), String.valueOf(turn.card().get("memory")));
        assertTrue(String.valueOf(turn.card().get("memory")).contains("荨麻疹"));
    }

    @Test
    void earlyStopGivesDirectionWithoutDrugs() {
        ChatSession session = sessions.openConsult(1L);
        ConsultOrchestrator.TurnResult first = consult.handle(session.getId(), "身上起了一片风团，很痒");
        assertTrue(first.card() == null);

        ConsultOrchestrator.TurnResult stop = consult.handle(session.getId(), "先给分析");
        assertTrue(stop.reply().contains("荨麻疹"), stop.reply());
        assertTrue(stop.reply().contains("还没说到"));
        assertTrue(drugsOf(stop).isEmpty());
        assertFalse(stop.reply().contains("氯雷他定"));
        assertTrue(String.valueOf(stop.card().get("memory")).contains("已追问 1 次"));
        assertTrue(String.valueOf(stop.card().get("memory")).contains("未推荐药品"));
    }

    @Test
    void profileLinkIsScopedToOneSession() {
        ChatSession linked = sessions.openConsult(1L);
        Long cardId = Long.valueOf(String.valueOf(profiles.create(1L, "测试").get("id")));
        assertEquals(false, consult.contextView(linked.getId()).get("profileLinked"));
        assertEquals(true, consult.linkProfile(linked.getId(), cardId).get("profileLinked"));
        assertEquals(cardId, consult.contextView(linked.getId()).get("profileId"));
        ConsultOrchestrator.TurnResult asked = consult.handle(linked.getId(), "身上起了一片风团，很痒", false, true);
        assertTrue(asked.card() == null, asked.reply());
        assertEquals(cardId, consult.contextView(linked.getId()).get("profileId"));

        ChatSession fresh = sessions.openConsult(1L);
        assertEquals(false, consult.contextView(fresh.getId()).get("profileLinked"));

        consult.handle(linked.getId(), "两天了", false, false);
        assertEquals(false, consult.contextView(linked.getId()).get("profileLinked"));
        long userLines = messages.findBySessionIdOrderByIdAsc(linked.getId()).stream()
                .filter(item -> "user".equals(item.getRole()))
                .count();
        assertEquals(2, userLines);
    }

    @Test
    void threeAsksThenClose() {
        ChatSession session = sessions.openConsult(1L);
        assertTrue(consult.handle(session.getId(), "有点不舒服").card() == null);
        assertTrue(consult.handle(session.getId(), "还是不舒服").card() == null);
        assertTrue(consult.handle(session.getId(), "就这些情况").card() == null);
        ConsultOrchestrator.TurnResult closed = consult.handle(session.getId(), "先给分析");
        assertTrue(closed.card() != null);
        assertTrue(drugsOf(closed).isEmpty());
        assertFalse(closed.reply().contains("对乙酰氨基酚"));
    }

    @Test
    void rhinitisInLongTermMemoryWarnsOnALaterCold() {
        ChatSession rhinitis = sessions.openConsult(42L);
        ConsultOrchestrator.TurnResult first = consult.handle(rhinitis.getId(), "我有过敏性鼻炎，清水样鼻涕，不发烧，三天了");
        assertTrue(first.reply().contains("过敏性鼻炎"), first.reply());
        assertTrue(String.valueOf(first.card().get("memory")).contains("长期记忆：过敏性鼻炎"));

        ChatSession cold = sessions.openConsult(42L);
        ConsultOrchestrator.TurnResult later = consult.handle(cold.getId(), "喉咙痛，低烧两天");
        assertTrue(later.reply().contains("普通感冒"), later.reply());
        assertTrue(later.reply().contains("过敏性鼻炎"), later.reply());
        assertEquals("对乙酰氨基酚", drugsOf(later).get(0).get("name"));
        assertTrue(String.valueOf(later.card().get("chronicCaution")).contains("不要只按感冒处理"));
    }

    @Test
    void rhinitisCanRecommendLoratadineAndHandFootMouthStillHasNoDrug() {
        ChatSession rhinitis = sessions.openConsult(1L);
        ConsultOrchestrator.TurnResult nose = consult.handle(rhinitis.getId(), "过敏性鼻炎，清水样鼻涕，不发烧，三天了");
        assertTrue(nose.reply().contains("过敏性鼻炎"), nose.reply());
        assertEquals("氯雷他定", drugsOf(nose).get(0).get("name"));

        ChatSession hfmd = sessions.openConsult(1L);
        ConsultOrchestrator.TurnResult blisters = consult.handle(hfmd.getId(), "小朋友手上和口腔里有疱疹，一天了，不发烧");
        assertTrue(blisters.reply().contains("手足口"), blisters.reply());
        assertTrue(drugsOf(blisters).isEmpty());

        ChatSession colloquial = sessions.openConsult(1L);
        ConsultOrchestrator.TurnResult cold = consult.handle(colloquial.getId(), "嗓子疼，有点低热，还流鼻涕，两天了");
        assertTrue(cold.reply().contains("普通感冒"), cold.reply());
        assertEquals("对乙酰氨基酚", drugsOf(cold).get(0).get("name"));
    }

    @Test
    void eczemaAndConstipationCanRecommendWhitelistDrugs() {
        ChatSession eczema = sessions.openConsult(1L);
        ConsultOrchestrator.TurnResult itch = consult.handle(eczema.getId(), "手臂湿疹发红很痒，三天了，不发烧");
        assertTrue(itch.reply().contains("湿疹"), itch.reply());
        assertFalse(drugsOf(itch).isEmpty());
        assertTrue(drugsOf(itch).stream().anyMatch(item -> "炉甘石洗剂".equals(item.get("name"))
                || "氢化可的松乳膏".equals(item.get("name"))));

        ChatSession constipation = sessions.openConsult(1L);
        ConsultOrchestrator.TurnResult hard = consult.handle(constipation.getId(), "便秘，大便干硬排便困难，五天了，不发烧");
        assertTrue(hard.reply().contains("便秘"), hard.reply());
        assertEquals("乳果糖", drugsOf(hard).get(0).get("name"));
    }

    @Test
    void unrelatedQuestionDoesNotInventADisease() {
        ChatSession session = sessions.openConsult(1L);
        ConsultOrchestrator.TurnResult turn = consult.handle(session.getId(), "今天天气怎么样");
        assertTrue(turn.card() == null, String.valueOf(turn.card()));
        assertTrue(turn.reply().contains("青禾") || turn.reply().contains("哪里不舒服"), turn.reply());
        assertFalse(turn.reply().contains("对乙酰氨基酚"));
        assertFalse(turn.reply().contains("氯雷他定"));
        assertEquals("WAITING_USER", turn.taskStatus());
    }

    @Test
    void greetingChatsNormallyThenGuidesSymptoms() {
        ChatSession session = sessions.openConsult(1L);
        ConsultOrchestrator.TurnResult hi = consult.handle(session.getId(), "你好");
        assertTrue(hi.card() == null);
        assertTrue(hi.reply().contains("青禾"), hi.reply());
        assertTrue(hi.reply().contains("哪里不舒服") || hi.reply().contains("不舒服"), hi.reply());
        assertEquals("WAITING_USER", hi.taskStatus());
        assertFalse(hi.reply().contains("对乙酰氨基酚"));
    }

    @Test
    void interruptedTaskCanResumeWithoutDuplicatingUserMessage() {
        ChatSession session = sessions.openConsult(1L);
        Long sid = session.getId();
        ChatMessage user = new ChatMessage();
        user.setSessionId(sid);
        user.setRole("user");
        user.setContent("喉咙痛，低烧两天");
        user.setCreatedAt(LocalDateTime.now());
        messages.save(user);

        ConsultTask task = taskStore.start(sid, 1L, "喉咙痛，低烧两天");
        taskStore.checkpoint(task.getId(), "red_flag");
        taskStore.checkpoint(task.getId(), "load_memory");
        consult.interrupt(sid);

        Map<String, Object> view = consult.taskView(sid);
        assertEquals(true, view.get("resumable"));
        assertEquals("INTERRUPTED", view.get("status"));

        ConsultOrchestrator.TurnResult resumed = consult.resume(sid);
        assertTrue(resumed.reply().contains("普通感冒"), resumed.reply());
        assertEquals("对乙酰氨基酚", drugsOf(resumed).get(0).get("name"));
        assertEquals("DONE", resumed.taskStatus());
        long userCount = messages.findBySessionIdOrderByIdAsc(sid).stream()
                .filter(item -> "user".equals(item.getRole()))
                .count();
        assertEquals(1, userCount);
        assertEquals(false, consult.taskView(sid).get("resumable"));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> drugsOf(ConsultOrchestrator.TurnResult turn) {
        return (List<Map<String, Object>>) turn.card().get("drugs");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> evidencesOf(ConsultOrchestrator.TurnResult turn) {
        return (List<Map<String, Object>>) turn.card().get("evidences");
    }
}
