package com.jyfc.backend.core.exception;

/**
 * 套餐未购买异常：当前租户 plan 为 FREE（或未解锁对应功能）时抛出。
 * 由 GlobalExceptionHandler 统一映射为 HTTP 402 + code 402，前端据此弹「请购买」。
 */
public class RequiresPurchaseException extends RuntimeException {

    public static final String DEFAULT_MESSAGE = "您没有此功能权限，请购买后使用";

    public RequiresPurchaseException() {
        super(DEFAULT_MESSAGE);
    }

    public RequiresPurchaseException(String message) {
        super(message);
    }
}
