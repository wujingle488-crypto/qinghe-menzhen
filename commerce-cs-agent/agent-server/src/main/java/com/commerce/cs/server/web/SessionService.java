package com.commerce.cs.server.web;

import com.commerce.cs.domain.SessionStatus;
import com.commerce.cs.domain.entity.ChatSession;
import com.commerce.cs.domain.repo.ChatSessionRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionService {
    private final ChatSessionRepository sessions;

    public SessionService(ChatSessionRepository sessions) {
        this.sessions = sessions;
    }

    @Transactional
    public ChatSession openConsult(Long userId) {
        ChatSession session = new ChatSession();
        session.setBuyerId(userId);
        session.setShopName("门诊智能助手");
        session.setStatus(SessionStatus.OPEN);
        session.setUpdatedAt(LocalDateTime.now());
        session.setLastMessage("开始问诊");
        return sessions.save(session);
    }

    public ChatSession requireOwned(Long userId, Long sessionId) {
        ChatSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));
        if (userId == null || !userId.equals(session.getBuyerId())) {
            throw new ForbiddenException("无权访问该会话");
        }
        return session;
    }

    public Map<String, Object> view(ChatSession session) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", session.getId());
        data.put("status", session.getStatus().name());
        data.put("buyerId", session.getBuyerId());
        data.put("shopName", session.getShopName());
        data.put("lastMessage", session.getLastMessage());
        data.put("updatedAt", session.getUpdatedAt() == null ? null : session.getUpdatedAt().toString());
        return data;
    }
}
