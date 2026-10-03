package com.commerce.cs.server.medical;

import com.commerce.cs.domain.ConsultTaskStatus;
import com.commerce.cs.domain.entity.ConsultTask;
import com.commerce.cs.domain.repo.ConsultTaskRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 问诊任务 checkpoint：节点成功后落库；断网/取消后可按幂等键恢复，避免重复写记忆。
 */
@Service
public class ConsultTaskStore {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final ConsultTaskRepository tasks;
    private final ObjectMapper objectMapper;

    public ConsultTaskStore(ConsultTaskRepository tasks, ObjectMapper objectMapper) {
        this.tasks = tasks;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ConsultTask start(Long sessionId, Long buyerId, String content) {
        cancelActive(sessionId);
        ConsultTask task = new ConsultTask();
        task.setSessionId(sessionId);
        task.setBuyerId(buyerId);
        task.setStatus(ConsultTaskStatus.RUNNING);
        task.setCoreIntent(clip(content, 240));
        task.setLastContent(clip(content, 900));
        task.setCurrentNode("start");
        task.setCompletedNodes("[]");
        task.setAppliedKeys("[]");
        task.setResumeNode("red_flag");
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        return tasks.save(task);
    }

    @Transactional
    public void checkpoint(Long taskId, String node) {
        if (taskId == null || node == null || node.isBlank()) {
            return;
        }
        ConsultTask task = tasks.findById(taskId).orElse(null);
        if (task == null || task.getStatus() == ConsultTaskStatus.DONE || task.getStatus() == ConsultTaskStatus.CANCELLED) {
            return;
        }
        Set<String> completed = new LinkedHashSet<>(readList(task.getCompletedNodes()));
        completed.add(node);
        task.setCompletedNodes(writeList(completed));
        task.setCurrentNode(node);
        task.setResumeNode(nextNode(node));
        task.setUpdatedAt(LocalDateTime.now());
        tasks.save(task);
    }

    /** 幂等键：同一 task+动作只生效一次（如写长期记忆、检索 trace）。 */
    @Transactional
    public boolean tryApply(Long taskId, String key) {
        if (taskId == null || key == null || key.isBlank()) {
            return true;
        }
        ConsultTask task = tasks.findById(taskId).orElse(null);
        if (task == null) {
            return true;
        }
        Set<String> keys = new LinkedHashSet<>(readList(task.getAppliedKeys()));
        if (keys.contains(key)) {
            return false;
        }
        keys.add(key);
        task.setAppliedKeys(writeList(keys));
        task.setUpdatedAt(LocalDateTime.now());
        tasks.save(task);
        return true;
    }

    @Transactional
    public void reopen(Long taskId) {
        ConsultTask task = taskId == null ? null : tasks.findById(taskId).orElse(null);
        if (task == null) {
            return;
        }
        task.setStatus(ConsultTaskStatus.RUNNING);
        task.setUpdatedAt(LocalDateTime.now());
        tasks.save(task);
    }

    @Transactional
    public void markWaitingUser(Long taskId) {
        updateStatus(taskId, ConsultTaskStatus.WAITING_USER, null);
    }

    @Transactional
    public void markDone(Long taskId) {
        updateStatus(taskId, ConsultTaskStatus.DONE, null);
    }

    @Transactional
    public void markInterrupted(Long taskId) {
        ConsultTask task = taskId == null ? null : tasks.findById(taskId).orElse(null);
        if (task == null) {
            return;
        }
        if (task.getStatus() == ConsultTaskStatus.DONE || task.getStatus() == ConsultTaskStatus.CANCELLED) {
            return;
        }
        task.setStatus(ConsultTaskStatus.INTERRUPTED);
        task.setUpdatedAt(LocalDateTime.now());
        tasks.save(task);
    }

    @Transactional
    public void markInterruptedBySession(Long sessionId) {
        tasks.findFirstBySessionIdAndStatusOrderByIdDesc(sessionId, ConsultTaskStatus.RUNNING).ifPresent(task -> {
            task.setStatus(ConsultTaskStatus.INTERRUPTED);
            task.setUpdatedAt(LocalDateTime.now());
            tasks.save(task);
        });
    }

    @Transactional
    public void cancel(Long sessionId) {
        cancelActive(sessionId);
    }

    public Optional<ConsultTask> findResumable(Long sessionId) {
        return tasks.findFirstBySessionIdAndStatusInOrderByIdDesc(sessionId,
                List.of(ConsultTaskStatus.INTERRUPTED, ConsultTaskStatus.RUNNING));
    }

    public Optional<ConsultTask> findActive(Long sessionId) {
        return tasks.findFirstBySessionIdAndStatusInOrderByIdDesc(sessionId,
                List.of(ConsultTaskStatus.RUNNING, ConsultTaskStatus.WAITING_USER, ConsultTaskStatus.INTERRUPTED));
    }

    public ConsultTask require(Long taskId) {
        return tasks.findById(taskId).orElseThrow(() -> new IllegalArgumentException("问诊任务不存在"));
    }

    public Set<String> completedNodes(ConsultTask task) {
        return new LinkedHashSet<>(readList(task.getCompletedNodes()));
    }

    private void cancelActive(Long sessionId) {
        for (ConsultTaskStatus status : List.of(
                ConsultTaskStatus.RUNNING, ConsultTaskStatus.WAITING_USER, ConsultTaskStatus.INTERRUPTED)) {
            tasks.findFirstBySessionIdAndStatusOrderByIdDesc(sessionId, status).ifPresent(task -> {
                task.setStatus(ConsultTaskStatus.CANCELLED);
                task.setUpdatedAt(LocalDateTime.now());
                tasks.save(task);
            });
        }
    }

    private void updateStatus(Long taskId, ConsultTaskStatus status, String resumeNode) {
        if (taskId == null) {
            return;
        }
        ConsultTask task = tasks.findById(taskId).orElse(null);
        if (task == null) {
            return;
        }
        task.setStatus(status);
        if (resumeNode != null) {
            task.setResumeNode(resumeNode);
        }
        task.setUpdatedAt(LocalDateTime.now());
        tasks.save(task);
    }

    private static String nextNode(String node) {
        return switch (node) {
            case "red_flag" -> "load_memory";
            case "load_memory" -> "intake_agent";
            case "intake_agent" -> "retrieve";
            case "retrieve" -> "advice";
            case "advice" -> "safety_gate";
            case "safety_gate" -> "write_memory";
            case "write_memory" -> "end";
            default -> node;
        };
    }

    private List<String> readList(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            List<String> list = objectMapper.readValue(json, STRING_LIST);
            return list == null ? new ArrayList<>() : new ArrayList<>(list);
        } catch (Exception ex) {
            return new ArrayList<>();
        }
    }

    private String writeList(Iterable<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private static String clip(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
