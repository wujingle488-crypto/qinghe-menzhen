package com.commerce.cs.server.web;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** 从当前请求取出登录用户编号。 */
public final class CurrentUser {
    private CurrentUser() {
    }

    public static long id() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            throw new AuthException("请先登录");
        }
        Object value = attrs.getRequest().getAttribute(AuthInterceptor.USER_ID);
        if (!(value instanceof Long userId)) {
            throw new AuthException("请先登录");
        }
        return userId;
    }
}
