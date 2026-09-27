package com.jyfc.backend.core.exception;

/** 公司档案库未配置或连不上。不能把档案写进平台库。 */
public class ArchiveUnavailableException extends RuntimeException {
    public ArchiveUnavailableException(String message) {
        super(message);
    }
}
