package com.jyfc.backend.core.exception;

/** 套餐未开通。由全局异常处理返回 HTTP 402，前端弹出确认。 */
public class RequiresPurchaseException extends RuntimeException {

    public RequiresPurchaseException(String message) {
        super(message);
    }
}
