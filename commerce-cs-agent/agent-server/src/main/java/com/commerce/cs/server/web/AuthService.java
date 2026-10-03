package com.commerce.cs.server.web;

import com.commerce.cs.domain.entity.AuthToken;
import com.commerce.cs.domain.entity.ClinicUser;
import com.commerce.cs.domain.repo.AuthTokenRepository;
import com.commerce.cs.domain.repo.ClinicUserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private static final int TOKEN_DAYS = 7;

    private final ClinicUserRepository users;
    private final AuthTokenRepository tokens;
    private final PasswordEncoder passwords;
    private final SecureRandom random = new SecureRandom();

    public AuthService(ClinicUserRepository users, AuthTokenRepository tokens, PasswordEncoder passwords) {
        this.users = users;
        this.tokens = tokens;
        this.passwords = passwords;
    }

    @Transactional
    public Map<String, Object> register(String rawUsername, String password, String confirm) {
        String username = normalize(rawUsername);
        if (username.length() < 2 || username.length() > 20) {
            throw new IllegalArgumentException("用户名需要 2 到 20 个字");
        }
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("密码至少 6 位");
        }
        if (!password.equals(confirm)) {
            throw new IllegalArgumentException("两次输入的密码不一致");
        }
        if (users.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("这个用户名已经有人用了");
        }
        ClinicUser user = new ClinicUser();
        user.setUsername(username);
        user.setPasswordHash(passwords.encode(password));
        user.setCreatedAt(LocalDateTime.now());
        users.save(user);
        return issue(user);
    }

    @Transactional
    public Map<String, Object> login(String rawUsername, String password) {
        String username = normalize(rawUsername);
        ClinicUser user = users.findByUsername(username).orElse(null);
        if (user == null || password == null || !passwords.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("用户名或密码不正确");
        }
        return issue(user);
    }

    @Transactional
    public void logout(String authorization) {
        String raw = rawToken(authorization);
        if (raw != null) {
            tokens.deleteByTokenHash(hash(raw));
        }
    }

    @Transactional
    public Long userIdOf(String authorization) {
        String raw = rawToken(authorization);
        if (raw == null) {
            return null;
        }
        AuthToken row = tokens.findByTokenHash(hash(raw)).orElse(null);
        if (row == null) {
            return null;
        }
        if (row.getExpiresAt().isBefore(LocalDateTime.now())) {
            tokens.delete(row);
            return null;
        }
        return row.getUserId();
    }

    public Map<String, Object> me(long userId) {
        ClinicUser user = users.findById(userId).orElseThrow(() -> new AuthException("请先登录"));
        return profile(user);
    }

    private Map<String, Object> issue(ClinicUser user) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = HexFormat.of().formatHex(bytes);
        AuthToken row = new AuthToken();
        row.setTokenHash(hash(raw));
        row.setUserId(user.getId());
        row.setExpiresAt(LocalDateTime.now().plusDays(TOKEN_DAYS));
        tokens.save(row);
        Map<String, Object> view = profile(user);
        view.put("token", raw);
        return view;
    }

    private static Map<String, Object> profile(ClinicUser user) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", user.getId());
        view.put("username", user.getUsername());
        return view;
    }

    private static String normalize(String rawUsername) {
        return rawUsername == null ? "" : rawUsername.trim();
    }

    private static String rawToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        String raw = authorization.substring("Bearer ".length()).trim();
        return raw.isEmpty() ? null : raw;
    }

    private static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }
}
