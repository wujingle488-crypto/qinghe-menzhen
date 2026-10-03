package com.commerce.cs.server.web;

import com.commerce.cs.domain.entity.ChatMessage;
import com.commerce.cs.domain.entity.TraceStep;
import com.commerce.cs.domain.repo.ChatMessageRepository;
import com.commerce.cs.domain.repo.TraceRepository;
import com.commerce.cs.domain.service.SymptomAttachmentText;
import com.commerce.cs.server.medical.ConsultHistoryService;
import com.commerce.cs.server.medical.ConsultOrchestrator;
import com.commerce.cs.server.media.SymptomMediaService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class ConsultController {
    private final SessionService sessionService;
    private final ConsultOrchestrator consult;
    private final ChatMessageRepository messages;
    private final TraceRepository traces;
    private final ConsultHistoryService history;
    private final SymptomMediaService media;

    public ConsultController(SessionService sessionService, ConsultOrchestrator consult,
                             ChatMessageRepository messages, TraceRepository traces,
                             ConsultHistoryService history, SymptomMediaService media) {
        this.sessionService = sessionService;
        this.consult = consult;
        this.messages = messages;
        this.traces = traces;
        this.history = history;
        this.media = media;
    }

    @GetMapping("/api/consult/sessions")
    public List<Map<String, Object>> list() {
        return history.list(CurrentUser.id());
    }

    @PostMapping("/api/consult/sessions")
    public Map<String, Object> open() {
        return sessionService.view(sessionService.openConsult(CurrentUser.id()));
    }

    @DeleteMapping("/api/consult/sessions/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        own(id);
        history.delete(id);
        return Map.of("deleted", id);
    }

    @DeleteMapping("/api/consult/sessions")
    public Map<String, Object> deleteMany(@RequestBody Map<String, Object> body) {
        List<Long> ids = idsOf(body.get("ids"));
        for (Long id : ids) {
            own(id);
        }
        history.deleteMany(ids);
        return Map.of("deleted", ids);
    }

    @PatchMapping("/api/consult/sessions/{id}")
    public Map<String, Object> rename(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        own(id);
        Object raw = body.get("title");
        return history.rename(id, raw == null ? "" : String.valueOf(raw));
    }

    @GetMapping("/api/consult/sessions/{id}/messages")
    public List<ChatMessage> messages(@PathVariable Long id) {
        own(id);
        return messages.findBySessionIdOrderByIdAsc(id);
    }

    @GetMapping("/api/consult/sessions/{id}/traces")
    public List<TraceStep> traces(@PathVariable Long id) {
        own(id);
        return traces.findBySessionIdOrderBySeqAsc(id);
    }

    @GetMapping("/api/consult/sessions/{id}/task")
    public Map<String, Object> task(@PathVariable Long id) {
        own(id);
        return consult.taskView(id);
    }

    @PostMapping("/api/consult/sessions/{id}/interrupt")
    public Map<String, Object> interrupt(@PathVariable Long id) {
        own(id);
        consult.interrupt(id);
        return consult.taskView(id);
    }

    @PostMapping("/api/consult/sessions/{id}/cancel")
    public Map<String, Object> cancel(@PathVariable Long id) {
        own(id);
        consult.cancel(id);
        return consult.taskView(id);
    }

    @PostMapping(value = "/api/consult/sessions/{id}/resume", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter resume(@PathVariable Long id) {
        own(id);
        return stream(id, () -> consult.resume(id));
    }

    @PostMapping(value = "/api/consult/sessions/{id}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter message(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        own(id);
        Object text = body.get("text");
        boolean webSearch = Boolean.TRUE.equals(body.get("webSearch"))
                || "true".equalsIgnoreCase(String.valueOf(body.getOrDefault("webSearch", "false")));
        Object rawProfile = body.get("useProfile");
        Boolean useProfile = rawProfile == null ? null
                : Boolean.TRUE.equals(rawProfile) || "true".equalsIgnoreCase(String.valueOf(rawProfile));
        String composed = SymptomAttachmentText.compose(text == null ? "" : String.valueOf(text), observations(body.get("imageIds")));
        if (composed.isBlank()) {
            throw new IllegalArgumentException("请先描述症状或上传图片");
        }
        return stream(id, () -> consult.handle(id, composed, webSearch, useProfile));
    }

    @GetMapping("/api/consult/sessions/{id}/context")
    public Map<String, Object> context(@PathVariable Long id) {
        own(id);
        return consult.contextView(id);
    }

    @PutMapping("/api/consult/sessions/{id}/context")
    public Map<String, Object> linkContext(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        own(id);
        Object rawId = body.get("profileId");
        Object rawLinked = body.get("profileLinked");
        boolean unlink = Boolean.FALSE.equals(rawLinked) || "false".equalsIgnoreCase(String.valueOf(rawLinked));
        Long profileId = rawId == null || String.valueOf(rawId).isBlank() || "null".equals(String.valueOf(rawId))
                ? null : Long.valueOf(String.valueOf(rawId));
        return consult.linkProfile(id, unlink ? null : profileId);
    }

    private void own(Long id) {
        sessionService.requireOwned(CurrentUser.id(), id);
    }

    private List<Long> idsOf(Object rawIds) {
        List<Long> ids = new ArrayList<>();
        if (!(rawIds instanceof List<?> values)) {
            return ids;
        }
        for (Object value : values) {
            if (value == null || String.valueOf(value).isBlank()) {
                continue;
            }
            ids.add(Long.valueOf(String.valueOf(value)));
        }
        return ids;
    }

    private List<String> observations(Object rawIds) {
        List<String> notes = new ArrayList<>();
        if (!(rawIds instanceof List<?> ids)) {
            return notes;
        }
        int count = 0;
        for (Object id : ids) {
            if (id == null || String.valueOf(id).isBlank()) {
                continue;
            }
            count++;
            if (count > 3) {
                break;
            }
            notes.add(media.observe(String.valueOf(id)));
        }
        return notes;
    }

    private SseEmitter stream(Long id, TurnCall call) {
        SseEmitter emitter = new SseEmitter(120_000L);
        try {
            ConsultOrchestrator.TurnResult result = call.run();
            for (ConsultOrchestrator.Step step : result.steps()) {
                emitter.send(SseEmitter.event().name(step.event()).data(step.text()));
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("reply", result.reply());
            payload.put("waitingHuman", result.waitingHuman());
            payload.put("card", result.card());
            payload.put("taskId", result.taskId());
            payload.put("taskStatus", result.taskStatus());
            emitter.send(SseEmitter.event().name("result").data(payload));
            emitter.complete();
        } catch (Exception ex) {
            try {
                consult.interrupt(id);
            } catch (Exception ignored) {
                // 中断标记失败时仍把原始错误回给前端
            }
            emitter.completeWithError(ex);
        }
        return emitter;
    }

    @FunctionalInterface
    private interface TurnCall {
        ConsultOrchestrator.TurnResult run();
    }
}
