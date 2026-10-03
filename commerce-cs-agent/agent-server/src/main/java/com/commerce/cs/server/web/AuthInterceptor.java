package com.commerce.cs.server.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {
    static final String USER_ID = "authUserId";

    private static final Set<String> OPEN = Set.of("/api/auth/login", "/api/auth/register", "/api/health");

    private final AuthService auth;

    public AuthInterceptor(AuthService auth) {
        this.auth = auth;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String path = request.getRequestURI();
        if ("OPTIONS".equals(request.getMethod()) || OPEN.contains(path) || !path.startsWith("/api/")) {
            return true;
        }
        Long userId = auth.userIdOf(request.getHeader("Authorization"));
        if (userId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"请先登录\"}");
            return false;
        }
        request.setAttribute(USER_ID, userId);
        return true;
    }
}
