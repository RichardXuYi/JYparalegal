package com.jyfc.backend.core.exception;

/**
 * 业务异常，用于表示业务逻辑错误（如资源不存在、权限不足等）。
 * 由 GlobalExceptionHandler 统一处理，返回 HTTP 400 及错误消息。
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
