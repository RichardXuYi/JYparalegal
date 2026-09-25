package com.jyfc.cp.esign;

/**
 * e签宝 API 调用异常。
 */
public class EsignApiException extends RuntimeException {
    public EsignApiException(String message, Throwable cause) {
        super(message, cause);
    }
    public EsignApiException(String message) {
        super(message);
    }
}
