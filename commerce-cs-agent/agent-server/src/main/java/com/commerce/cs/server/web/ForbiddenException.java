package com.commerce.cs.server.web;

/** 已登录，但不能看别人的会话或就诊卡。 */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
