package com.jyfc.backend.module.integration.feishu.exception;

/**
 * 飞书 API 调用异常。
 * 当飞书接口返回 errcode != 0 时抛出。
 */
public class FeishuApiException extends RuntimeException {

    private final int errCode;

    public FeishuApiException(int errCode, String message) {
        super("Feishu API error [" + errCode + "]: " + message);
        this.errCode = errCode;
    }

    public int getErrCode() {
        return errCode;
    }
}
