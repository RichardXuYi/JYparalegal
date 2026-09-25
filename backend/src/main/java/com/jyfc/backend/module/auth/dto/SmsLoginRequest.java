package com.jyfc.backend.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 短信验证码登录请求（BE-008）。
 *
 * <p>以带约束注解的 DTO 替换裸 {@code Map}，由 {@code @Valid} 在进入控制器前完成结构校验；
 * 验证码固定 6 位数字，结构非法请求在到达业务逻辑前即被拒绝。</p>
 */
@Data
public class SmsLoginRequest {

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = "^\\d{6}$", message = "验证码格式不正确")
    private String code;
}
