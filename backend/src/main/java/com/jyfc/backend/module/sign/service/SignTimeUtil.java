package com.jyfc.backend.module.sign.service;

import com.jyfc.backend.core.exception.BusinessException;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/**
 * 签署域统一的安全时间解析：本地时间契约 YYYY-MM-DDTHH:mm[:ss]。
 * 16 位（缺秒）自动补 ":00"；非法格式抛 BusinessException（经 GlobalExceptionHandler → 400），
 * 而非裸 LocalDateTime.parse 的 DateTimeParseException（→ 不透明 500）。
 */
final class SignTimeUtil {

    private SignTimeUtil() {
    }

    /** 解析截止时间；null / 空 / "null" 视为未提供，返回 null。非法格式 → 400。 */
    static LocalDateTime parseDeadline(String raw) {
        return parse(raw, "截止日期");
    }

    /** 通用安全解析；label 用于错误消息。null / 空 / "null" → 返回 null。 */
    static LocalDateTime parse(String raw, String label) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty() || "null".equals(s)) return null;
        try {
            return LocalDateTime.parse(s.length() == 16 ? s + ":00" : s);
        } catch (DateTimeParseException e) {
            throw new BusinessException(label + "格式不正确");
        }
    }
}
