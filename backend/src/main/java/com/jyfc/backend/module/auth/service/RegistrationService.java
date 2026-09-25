package com.jyfc.backend.module.auth.service;

import com.jyfc.backend.module.account.entity.TenantQuotaEntity;
import com.jyfc.backend.module.account.repository.TenantQuotaRepository;
import com.jyfc.backend.module.auth.entity.CompanyEntity;
import com.jyfc.backend.module.auth.entity.UserEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 轻量注册（桌面端）：仅手机号 + 密码（企业另需公司名/信用代码）。
 * 不做短信/邮箱验证（与 {@code AuthController.register} 的完整流程区分）。
 *
 * <p>编排：建 user（手机号兼作 username）→ 个人租户由 {@code UserService.createUser} 自动供给；
 * 企业则再建 company（{@code CompanyService.createCompany} 自动供给企业租户并把 owner 绑过去）
 * → 落 FREE 套餐配额（未购买默认不可用法律域，由后续门禁阶段强制）。</p>
 */
@Service
public class RegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);
    private static final String FREE_PLAN = "FREE";

    private final UserService userService;
    private final CompanyService companyService;
    private final TenantQuotaRepository tenantQuotaRepository;

    public RegistrationService(UserService userService,
                               CompanyService companyService,
                               TenantQuotaRepository tenantQuotaRepository) {
        this.userService = userService;
        this.companyService = companyService;
        this.tenantQuotaRepository = tenantQuotaRepository;
    }

    public record Result(Long userId, String username, String userType, Long companyId, Long tenantId) {}

    @Transactional
    public Result registerSimple(String phone,
                                 String rawPassword,
                                 boolean enterprise,
                                 String companyName,
                                 String unifiedCreditCode,
                                 String legalPerson,
                                 String contactEmail) {
        // 手机号兼作 username（users.username 非空唯一；phone 唯一 → username=phone 唯一）。
        // email 列可空但 createUser 要求非空，合成占位（不对用户展示）。
        String username = phone;
        String placeholderEmail = phone + "@registered.invalid";

        UserEntity user = userService.createUser(username, placeholderEmail, rawPassword, "USER");
        user.setPhone(phone);

        Long tenantId = user.getTenantId();
        Long companyId = null;

        if (enterprise) {
            user.setUserType("ENTERPRISE");
            CompanyEntity company = new CompanyEntity();
            company.setName(companyName);
            company.setUnifiedCreditCode(unifiedCreditCode);
            company.setLegalPerson(legalPerson);
            company.setContactPhone(phone);
            company.setContactEmail(contactEmail);
            CompanyEntity created = companyService.createCompany(company, user.getId());
            companyId = created.getId();
            tenantId = created.getTenantId() != null ? created.getTenantId() : tenantId;
            user.setCompanyId(companyId);
        } else {
            user.setUserType("PERSONAL");
        }

        UserEntity saved = userService.updateUser(user);
        ensureFreeQuota(tenantId);
        log.info("轻量注册成功: username={}, userType={}, companyId={}, tenantId={}",
                username, saved.getUserType(), companyId, tenantId);
        return new Result(saved.getId(), username, saved.getUserType(), companyId, tenantId);
    }

    /** 新租户默认 FREE 套餐、0 配额（未购买）。已存在配额行则不覆盖。 */
    private void ensureFreeQuota(Long tenantId) {
        if (tenantId == null || tenantId == 0L) return;
        if (tenantQuotaRepository.findByTenantId(tenantId).isPresent()) return;
        TenantQuotaEntity q = new TenantQuotaEntity();
        q.setTenantId(tenantId);
        q.setPlan(FREE_PLAN);
        q.setSignQuota(0);
        q.setAiQuotaTokens(0L);
        q.setAiUsedTokens(0L);
        tenantQuotaRepository.save(q);
    }
}
