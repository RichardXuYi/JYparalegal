package com.jyfc.backend.module.account.service;

import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 账号中心服务（配额切片）。总览数据统一取自 {@link SignQuotaService}——
 * 与送签拦截使用同一份额度口径（审阅 Q5.3：界面一个数、拦截另一个数是缺陷）。
 */
@Service
public class AccountService {

    private final SignQuotaService signQuotaService;

    public AccountService(SignQuotaService signQuotaService) {
        this.signQuotaService = signQuotaService;
    }

    public Map<String, Object> getOverview(Long tenantId) {
        return signQuotaService.snapshot(tenantId);
    }
}
