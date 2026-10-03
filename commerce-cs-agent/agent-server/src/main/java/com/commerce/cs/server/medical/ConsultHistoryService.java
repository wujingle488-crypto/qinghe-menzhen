package com.commerce.cs.server.medical;

import com.commerce.cs.domain.entity.ChatMessage;
import com.commerce.cs.domain.entity.ChatSession;
import com.commerce.cs.domain.repo.ChatMessageRepository;
import com.commerce.cs.domain.repo.ChatSessionRepository;
import com.commerce.cs.domain.repo.ConsultMemoryRepository;
import com.commerce.cs.domain.repo.ConsultTaskRepository;
import com.commerce.cs.domain.repo.TraceRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 问诊历史：按会话列出、删除。删除只清这一次对话及其 checkpoint、只从这次写出的长期记忆。 */
@Service
public class ConsultHistoryService {
    private final ChatSessionRepository sessions;
    private final ChatMessageRepository messages;
    private final ConsultMemoryRepository consultMemories;
    private final ConsultTaskRepository tasks;
    private final TraceRepository traces;
    private final UserMemoryService userMemories;

    public ConsultHistoryService(ChatSessionRepository sessions, ChatMessageRepository messages,
                                 ConsultMemoryRepository consultMemories, ConsultTaskRepository tasks,
                                 TraceRepository traces, UserMemoryService userMemories) {
        this.sessions = sessions;
        this.messages = messages;
        this.consultMemories = consultMemories;
        this.tasks = tasks;
        this.traces = traces;
        this.userMemories = userMemories;
    }

    public List<Map<String, Object>> list(Long buyerId) {
        return sessions.findByBuyerIdOrderByUpdatedAtDescIdDesc(buyerId).stream()
                .filter(session -> "门诊智能助手".equals(session.getShopName()))
                .map(this::summary)
                .toList();
    }

    @Transactional
    public void delete(Long sessionId) {
        ChatSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));
        messages.deleteBySessionId(sessionId);
        traces.deleteBySessionId(sessionId);
        tasks.deleteBySessionId(sessionId);
        consultMemories.deleteBySessionId(sessionId);
        userMemories.forgetSession(session.getBuyerId(), sessionId);
        sessions.delete(session);
    }

    @Transactional
    public void deleteMany(List<Long> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            return;
        }
        for (Long sessionId : sessionIds) {
            if (sessionId != null) {
                delete(sessionId);
            }
        }
    }

    @Transactional
    public Map<String, Object> rename(Long sessionId, String rawTitle) {
        ChatSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));
        String title = rawTitle == null ? "" : rawTitle.replace('\n', ' ').trim();
        if (title.isBlank()) {
            throw new IllegalArgumentException("标题不能为空");
        }
        if (title.length() > 40) {
            title = title.substring(0, 40);
        }
        session.setTitle(title);
        sessions.save(session);
        return summary(session);
    }

    private Map<String, Object> summary(ChatSession session) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", session.getId());
        view.put("title", title(session));
        view.put("updatedAt", session.getUpdatedAt() == null ? "" : session.getUpdatedAt().toString());
        view.put("status", session.getStatus() == null ? "" : session.getStatus().name());
        return view;
    }

    private String title(ChatSession session) {
        if (session.getTitle() != null && !session.getTitle().isBlank()) {
            return session.getTitle().trim();
        }
        String fromUser = messages.findBySessionIdOrderByIdAsc(session.getId()).stream()
                .filter(message -> "user".equals(message.getRole()))
                .map(ChatMessage::getContent)
                .filter(text -> text != null && !text.isBlank())
                .findFirst()
                .orElse("");
        if (!fromUser.isBlank()) {
            String oneLine = fromUser.replace('\n', ' ').trim();
            return oneLine.length() <= 28 ? oneLine : oneLine.substring(0, 28) + "…";
        }
        return "新的问诊";
    }
}
