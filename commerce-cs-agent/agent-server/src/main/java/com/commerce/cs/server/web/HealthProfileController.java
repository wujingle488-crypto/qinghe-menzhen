package com.commerce.cs.server.web;

import com.commerce.cs.server.medical.HealthProfileService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthProfileController {
    private final HealthProfileService profiles;

    public HealthProfileController(HealthProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping("/api/profile/cards")
    public List<Map<String, Object>> list() {
        return profiles.list(CurrentUser.id());
    }

    @PostMapping("/api/profile/cards")
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String name = body.get("name") == null ? "" : String.valueOf(body.get("name"));
        return profiles.create(CurrentUser.id(), name);
    }

    @GetMapping("/api/profile")
    public Map<String, Object> get(@RequestParam Long id) {
        return profiles.view(CurrentUser.id(), id);
    }

    @GetMapping("/api/profile/cards/{id}/digest")
    public Map<String, Object> digest(@PathVariable Long id) {
        return profiles.consultDigest(CurrentUser.id(), id);
    }

    @PutMapping("/api/profile")
    public Map<String, Object> save(@RequestBody Map<String, Object> body) {
        Long id = body.get("id") == null ? null : Long.valueOf(String.valueOf(body.get("id")));
        String name = body.get("name") == null ? "" : String.valueOf(body.get("name"));
        @SuppressWarnings("unchecked")
        Map<String, Object> patch = body.get("profile") instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : body;
        return profiles.saveFields(CurrentUser.id(), id, name, patch);
    }

    @PostMapping("/api/profile/assistant")
    public Map<String, Object> assistant(@RequestBody Map<String, Object> body) {
        Long id = body.get("id") == null ? null : Long.valueOf(String.valueOf(body.get("id")));
        String mode = body.get("mode") == null ? "" : String.valueOf(body.get("mode"));
        String input = body.get("input") == null ? "" : String.valueOf(body.get("input"));
        return profiles.run(CurrentUser.id(), id, mode, input);
    }
}
