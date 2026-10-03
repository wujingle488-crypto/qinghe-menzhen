package com.commerce.cs.server.web;

import com.commerce.cs.server.media.MediaFileStore;
import com.commerce.cs.server.media.SymptomMediaService;
import java.util.Map;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class MediaController {
    private final SymptomMediaService media;

    public MediaController(SymptomMediaService media) {
        this.media = media;
    }

    @PostMapping(value = "/api/media/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> uploadImage(@RequestParam("file") MultipartFile file) throws Exception {
        MediaFileStore.Stored stored = media.saveImage(file.getContentType(), file.getBytes());
        return Map.of(
                "id", stored.id(),
                "url", "/api/media/images/" + stored.id(),
                "name", file.getOriginalFilename() == null ? "image" : file.getOriginalFilename());
    }

    @GetMapping("/api/media/images/{id}")
    public ResponseEntity<FileSystemResource> image(@PathVariable String id) {
        MediaFileStore.Stored stored = media.read(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, stored.mime())
                .body(new FileSystemResource(stored.path()));
    }

    @PostMapping(value = "/api/media/speech", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> speech(@RequestParam("file") MultipartFile file) throws Exception {
        String text = media.transcribe(file.getBytes());
        return Map.of("text", text);
    }
}
