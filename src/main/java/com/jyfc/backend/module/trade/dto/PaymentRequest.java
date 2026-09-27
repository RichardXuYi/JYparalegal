package com.jyfc.backend.module.trade.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 发起支付请求（BE-008）。
 *
 * <p>以带约束注解的 DTO 替换裸 {@code Map}，由 {@code @Valid} 在进入控制器前完成结构校验。</p>
 */
@Data
public class PaymentRequest {

    @NotNull(message = "orderId 不能为空")
    private Long orderId;

    @NotBlank(message = "paymentMethod 不能为空")
    private String paymentMethod;
}
