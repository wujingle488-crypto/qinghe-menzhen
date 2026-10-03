package com.commerce.cs.server.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/api/auth/register")
    public Map<String, Object> register(@RequestBody Map<String, String> body) {
        return auth.register(body.get("username"), body.get("password"), body.get("confirm"));
    }

    @PostMapping("/api/auth/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body) {
        return auth.login(body.get("username"), body.get("password"));
    }

    @PostMapping("/api/auth/logout")
    public Map<String, Object> logout(HttpServletRequest request) {
        auth.logout(request.getHeader("Authorization"));
        return Map.of("ok", true);
    }

    @GetMapping("/api/auth/me")
    public Map<String, Object> me() {
        return auth.me(CurrentUser.id());
    }
}
