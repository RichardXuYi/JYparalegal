package com.jyfc.backend.core.security;

import com.jyfc.backend.core.exception.RequiresPurchaseException;
import com.jyfc.backend.core.tenant.JyTenantContext;
import com.jyfc.backend.module.account.service.SignQuotaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** 免费套餐不能使用签署、模板、证据、比对、知识库和模拟法庭。 */
@Component
public class EntitlementInterceptor implements HandlerInterceptor {

    private final SignQuotaService quotas;

    public EntitlementInterceptor(SignQuotaService quotas) {
        this.quotas = quotas;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        Long tenantId = JyTenantContext.get();
        if (tenantId == null || tenantId == 0L) {
            throw new RequiresPurchaseException("当前套餐未包含此功能，请升级后使用");
        }
        String plan = quotas.quotaOf(tenantId).getPlan();
        if (plan == null || plan.isBlank() || "FREE".equalsIgnoreCase(plan)) {
            throw new RequiresPurchaseException("当前套餐未包含此功能，请升级后使用");
        }
        return true;
    }
}
