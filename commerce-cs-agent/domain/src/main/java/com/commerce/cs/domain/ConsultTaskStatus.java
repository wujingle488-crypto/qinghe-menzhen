package com.commerce.cs.domain;

/** 问诊任务状态：自然语言在 messages，能不能继续由这里决定。 */
public enum ConsultTaskStatus {
    RUNNING,
    WAITING_USER,
    INTERRUPTED,
    DONE,
    CANCELLED
}
