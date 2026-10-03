package com.commerce.cs.server.medical;

import com.commerce.cs.domain.entity.ConsultMemory;
import com.commerce.cs.domain.repo.ConsultMemoryRepository;
import com.commerce.cs.domain.service.SessionMemory;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

/** 按会话读写记忆。读失败时当作新会话，不让坏数据挡住问诊。 */
@Service
public class ConsultMemoryStore {
    private final ConsultMemoryRepository memories;
    private final ObjectMapper objectMapper;

    public ConsultMemoryStore(ConsultMemoryRepository memories, ObjectMapper objectMapper) {
        this.memories = memories;
        this.objectMapper = objectMapper;
    }

    public Held open(Long sessionId) {
        return memories.findBySessionId(sessionId)
                .map(row -> new Held(read(row.getPayload()), false))
                .orElseGet(() -> new Held(new SessionMemory(), true));
    }

    public void save(Long sessionId, SessionMemory memory) {
        ConsultMemory row = memories.findBySessionId(sessionId).orElseGet(() -> {
            ConsultMemory created = new ConsultMemory();
            created.setSessionId(sessionId);
            return created;
        });
        try {
            row.setPayload(objectMapper.writeValueAsString(memory));
        } catch (Exception ex) {
            row.setPayload("{}");
        }
        row.setUpdatedAt(LocalDateTime.now());
        memories.save(row);
    }

    private SessionMemory read(String payload) {
        if (payload == null || payload.isBlank()) {
            return new SessionMemory();
        }
        try {
            SessionMemory memory = objectMapper.readValue(payload, SessionMemory.class);
            if (memory.observations == null) {
                memory.observations = new java.util.ArrayList<>();
            }
            if (memory.episodes == null) {
                memory.episodes = new java.util.ArrayList<>();
            }
            if (memory.openFlags == null) {
                memory.openFlags = new java.util.ArrayList<>();
            }
            return memory;
        } catch (Exception ex) {
            return new SessionMemory();
        }
    }

    public record Held(SessionMemory memory, boolean fresh) {
    }
}
