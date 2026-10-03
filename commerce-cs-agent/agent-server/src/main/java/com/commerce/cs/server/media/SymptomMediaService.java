package com.commerce.cs.server.media;

import com.commerce.cs.server.llm.DeepSeekClient;
import java.nio.file.Files;
import java.util.Base64;
import org.springframework.stereotype.Service;

@Service
public class SymptomMediaService {
    private final MediaFileStore store;
    private final DeepSeekClient llm;
    private final SpeechTranscriber speech;

    public SymptomMediaService(MediaFileStore store, DeepSeekClient llm, SpeechTranscriber speech) {
        this.store = store;
        this.llm = llm;
        this.speech = speech;
    }

    public MediaFileStore.Stored saveImage(String contentType, byte[] bytes) throws Exception {
        return store.saveImage(contentType, bytes);
    }

    public MediaFileStore.Stored read(String id) {
        return store.require(id);
    }

    public String observe(String id) {
        MediaFileStore.Stored stored = store.require(id);
        try {
            byte[] bytes = Files.readAllBytes(stored.path());
            String note = llm.describeImage(stored.mime(), Base64.getEncoder().encodeToString(bytes));
            if (note == null || note.isBlank()) {
                return "这张图片暂时看不清，请用文字补充一下哪里不舒服。";
            }
            return clip(note, 400);
        } catch (Exception ex) {
            return "这张图片暂时看不清，请用文字补充一下哪里不舒服。";
        }
    }

    public String transcribe(byte[] wav) throws Exception {
        MediaFileStore.Stored stored = store.saveSpeech(wav);
        return speech.transcribe(stored.path());
    }

    private static String clip(String text, int max) {
        String trimmed = text.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
