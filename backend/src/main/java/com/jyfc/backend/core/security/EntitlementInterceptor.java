package com.jyfc.backend.core.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jyfc.backend.core.exception.RequiresPurchaseException;
import com.jyfc.backend.core.tenant.JyTenantContext;
import com.jyfc.backend.module.account.service.SignQuotaService;
import com.jyfc.backend.shared.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 套餐门禁（阶段2）：对法律域接口按当前租户的 plan 拦截。
 *
 * <p>FREE 套餐（未购买）访问被 gate 的路径时返回 HTTP 402 + code 402，前端据此弹「请购买」。
 * PRO 及以上通过；<b>无配额行的租户由 {@code quotaOf} 补为 FREE/0</b>（不再默认放行）。
 * 未认证 / 根租户交由安全层与控制器 requireTenant 处理。</p>
 *
 * <p>拦截器只看租户 plan、与请求体形状无关，因此新增付费入口只需在 {@code WebConfig}
 * 加路径（含 /api/sign/**、/internal/tools/**、/api/knowledge/** 等，豁免 esign 回调）。</p>
 */
@Component
public class EntitlementInterceptor implements HandlerInterceptor {

    private final SignQuotaService signQuotaService;
    private final ObjectMapper objectMapper;

    public EntitlementInterceptor(SignQuotaService signQuotaService, ObjectMapper objectMapper) {
        this.signQuotaService = signQuotaService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Long tenant = JyTenantContext.get();
        if (tenant == null || JyTenantContext.ROOT_TENANT_ID.equals(tenant)) {
            return true; // 交由认证层 / 控制器 requireTenant 处理
        }
        String plan = signQuotaService.quotaOf(tenant).getPlan();
        if (!"FREE".equalsIgnoreCase(plan)) {
            return true; // 已购买（PRO 及以上）
        }
        writePurchaseRequired(response);
        return false;
    }

    private void writePurchaseRequired(HttpServletResponse response) throws java.io.IOException {
        response.setStatus(HttpStatus.PAYMENT_REQUIRED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiResponse<Void> body = ApiResponse.error(HttpStatus.PAYMENT_REQUIRED.value(), RequiresPurchaseException.DEFAULT_MESSAGE);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
