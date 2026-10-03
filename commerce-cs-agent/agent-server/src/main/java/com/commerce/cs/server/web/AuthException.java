package com.commerce.cs.server.web;

/** 未登录或令牌失效。 */
public class AuthException extends RuntimeException {
    public AuthException(String message) {
        super(message);
    }
}
