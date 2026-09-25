package com.jyfc.backend.module.account.controller;

import com.jyfc.backend.core.exception.BusinessException;
import com.jyfc.backend.core.tenant.JyTenantContext;
import com.jyfc.backend.module.account.service.AccountService;
import com.jyfc.backend.shared.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 账号中心 REST（配额切片）。GET /api/account/overview 返回套餐与签署/AI 配额总览；
 * 租户应用层强制（D12）：tenant() 守卫，缺失即拒。
 */
@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    private Long tenant() {
        Long t = JyTenantContext.get();
        if (t == null || t == 0L) throw new BusinessException("租户上下文缺失");
        return t;
    }

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        return ApiResponse.success(accountService.getOverview(tenant()));
    }
}
