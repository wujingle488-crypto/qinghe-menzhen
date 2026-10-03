package com.commerce.cs.server.medical;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.commerce.cs.domain.ConsultTaskStatus;
import com.commerce.cs.domain.entity.ConsultTask;
import com.commerce.cs.server.medical.graph.ConsultSupport;
import com.commerce.cs.server.medical.graph.ConsultTurn;
import com.commerce.cs.server.medical.graph.ConsultWorkflow;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 问诊入口：主路径走 Spring AI Alibaba StateGraph。
 * 红旗门禁、检索、SafetyGate、写记忆为固定节点；问诊为受约束 Agent 节点。
 * 断网/取消后可按 checkpoint + 幂等键恢复，不重复写入用户消息与长期记忆。
 */
@Service
public class ConsultOrchestrator {
    private final CompiledGraph consultGraph;
    private final ConsultSupport support;
    private final ConsultTaskStore taskStore;

    public ConsultOrchestrator(CompiledGraph consultGraph, ConsultSupport support, ConsultTaskStore taskStore) {
        this.consultGraph = consultGraph;
        this.support = support;
        this.taskStore = taskStore;
    }

    public record Step(String event, String text) {
    }

    public record TurnResult(String reply, List<Step> steps, boolean waitingHuman, Map<String, Object> card,
                             Long taskId, String taskStatus) {
        public TurnResult(String reply, List<Step> steps, boolean waitingHuman, Map<String, Object> card) {
            this(reply, steps, waitingHuman, card, null, null);
        }
    }

    public TurnResult handle(Long sessionId, String text) {
        return handle(sessionId, text, false);
    }

    @Transactional
    public TurnResult handle(Long sessionId, String text, boolean webSearch) {
        return handle(sessionId, text, webSearch, null);
    }

    @Transactional
    public TurnResult handle(Long sessionId, String text, boolean webSearch, Boolean useProfile) {
        String content = text == null ? "" : text.trim();
        return run(sessionId, content, false, null, webSearch, useProfile);
    }

    /** 继续未完成任务：用上次用户内容重跑图，不重复落用户消息。 */
    @Transactional
    public TurnResult resume(Long sessionId) {
        ConsultTask task = taskStore.findResumable(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("没有可继续的问诊任务"));
        if (task.getStatus() == ConsultTaskStatus.DONE) {
            throw new IllegalArgumentException("任务已完成");
        }
        String content = task.getLastContent() == null ? "" : task.getLastContent();
        return run(sessionId, content, true, task, false, null);
    }

    @Transactional
    public void interrupt(Long sessionId) {
        taskStore.markInterruptedBySession(sessionId);
        support.trace(sessionId, "任务", "中断", "", "标记为可恢复", null);
    }

    @Transactional
    public void cancel(Long sessionId) {
        taskStore.cancel(sessionId);
        support.trace(sessionId, "任务", "取消", "", "任务已取消", null);
    }

    public Map<String, Object> contextView(Long sessionId) {
        Map<String, Object> view = new LinkedHashMap<>();
        Long profileId = support.linkedProfileId(sessionId);
        view.put("profileLinked", profileId != null);
        view.put("profileId", profileId);
        if (profileId != null) {
            try {
                view.put("title", support.healthProfiles().consultDigest(
                        support.requireSession(sessionId).getBuyerId(), profileId).get("title"));
            } catch (Exception ex) {
                view.put("profileLinked", false);
                view.put("profileId", null);
            }
        }
        return view;
    }

    @Transactional
    public Map<String, Object> linkProfile(Long sessionId, Long profileId) {
        if (profileId != null) {
            support.healthProfiles().consultDigest(support.requireSession(sessionId).getBuyerId(), profileId);
        }
        support.linkProfile(sessionId, profileId);
        support.trace(sessionId, "就诊卡", profileId == null ? "移除" : "关联", "",
                profileId == null ? "本会话恢复普通问诊" : "本会话关联就诊卡 " + profileId, true);
        return contextView(sessionId);
    }

    public Map<String, Object> taskView(Long sessionId) {
        Map<String, Object> view = new LinkedHashMap<>();
        Optional<ConsultTask> active = taskStore.findActive(sessionId);
        if (active.isEmpty()) {
            view.put("resumable", false);
            view.put("status", "NONE");
            return view;
        }
        ConsultTask task = active.get();
        view.put("resumable", task.getStatus() == ConsultTaskStatus.INTERRUPTED
                || task.getStatus() == ConsultTaskStatus.RUNNING);
        view.put("status", task.getStatus().name());
        view.put("taskId", task.getId());
        view.put("coreIntent", task.getCoreIntent());
        view.put("currentNode", task.getCurrentNode());
        view.put("resumeNode", task.getResumeNode());
        view.put("lastContent", task.getLastContent());
        view.put("completedNodes", taskStore.completedNodes(task));
        return view;
    }

    private TurnResult run(Long sessionId, String content, boolean resumeMode, ConsultTask existing, boolean webSearch,
                           Boolean useProfile) {
        ConsultTurn turn = new ConsultTurn();
        turn.sessionId = sessionId;
        turn.session = support.requireSession(sessionId);
        turn.content = content;
        turn.resumeMode = resumeMode;
        turn.webSearch = webSearch;
        turn.useProfile = useProfile;
        ConsultTask task = existing;
        if (task == null) {
            task = taskStore.start(sessionId, turn.session.getBuyerId(), content);
            support.saveMessage(sessionId, "user", content);
        } else {
            taskStore.reopen(task.getId());
            support.trace(sessionId, "任务", "恢复", content,
                    "从节点 " + (task.getResumeNode() == null ? task.getCurrentNode() : task.getResumeNode()) + " 继续",
                    true);
        }
        turn.taskId = task.getId();
        turn.history = support.userHistory(sessionId);
        try {
            OverAllState state = consultGraph.invoke(Map.of(ConsultWorkflow.KEY_TURN, turn))
                    .orElseThrow(() -> new IllegalStateException("问诊图未返回状态"));
            ConsultTurn done = state.value(ConsultWorkflow.KEY_TURN, ConsultTurn.class)
                    .orElseThrow(() -> new IllegalStateException("问诊图缺少 turn 结果"));
            ConsultTask latest = taskStore.require(done.taskId);
            boolean waiting = latest.getStatus() == ConsultTaskStatus.WAITING_USER;
            return new TurnResult(done.reply, normalizeSteps(done.steps), waiting, done.card,
                    latest.getId(), latest.getStatus().name());
        } catch (RuntimeException ex) {
            taskStore.markInterrupted(turn.taskId);
            support.trace(sessionId, "任务", "中断", content,
                    "节点=" + (task.getCurrentNode() == null ? "" : task.getCurrentNode()) + "；" + ex.getClass().getSimpleName(),
                    false);
            throw ex;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Step> normalizeSteps(List<? extends Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<Step> out = new ArrayList<>(raw.size());
        for (Object item : raw) {
            if (item instanceof Step step) {
                out.add(step);
            } else if (item instanceof Map<?, ?> map) {
                Object event = map.get("event");
                Object text = map.get("text");
                out.add(new Step(event == null ? "progress" : String.valueOf(event),
                        text == null ? "" : String.valueOf(text)));
            }
        }
        return out;
    }
}
