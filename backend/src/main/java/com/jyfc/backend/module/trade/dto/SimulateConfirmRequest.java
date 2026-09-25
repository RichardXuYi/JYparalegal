package com.jyfc.backend.module.trade.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 模拟支付确认请求（BE-008）。
 *
 * <p>以带约束注解的 DTO 替换裸 {@code Map}，由 {@code @Valid} 在进入控制器前完成结构校验。</p>
 */
@Data
public class SimulateConfirmRequest {

    @NotBlank(message = "transactionNo 不能为空")
    private String transactionNo;
}
