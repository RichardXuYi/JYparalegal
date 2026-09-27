package com.jyfc.backend.module.dashboard.dto;

/**
 * 管理员修改密码请求体
 *
 * <p>前端契约：{@code PUT /api/admin/profile/password}</p>
 * <pre>
 * {
 *   "current": "oldPassword",
 *   "next":    "newPassword"
 * }
 * </pre>
 *
 * <p>字段名严格使用 {@code current} / {@code next}（与前端 Settings.tsx 的 pwdForm 保持一致）。</p>
 */
public class ChangePasswordRequest {

    /** 当前密码（明文） */
    private String current;

    /** 新密码（明文） */
    private String next;

    public ChangePasswordRequest() {
    }

    public String getCurrent() {
        return current;
    }

    public void setCurrent(String current) {
        this.current = current;
    }

    public String getNext() {
        return next;
    }

    public void setNext(String next) {
        this.next = next;
    }
}
