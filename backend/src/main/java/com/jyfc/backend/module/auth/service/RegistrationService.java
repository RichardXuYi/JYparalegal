package com.jyfc.backend.module.auth.service;

import com.jyfc.backend.module.auth.entity.CompanyEntity;
import com.jyfc.backend.module.auth.entity.UserEntity;
import com.jyfc.backend.module.tenant.service.TenantProvisioningService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 桌面端轻量注册：手机号加密码，企业再补公司名。注册后仍是免费套餐。 */
@Service
public class RegistrationService {

    public record Result(Long userId, String username, String userType, Long companyId) {}

    private final UserService users;
    private final CompanyService companies;
    private final TenantProvisioningService tenants;

    public RegistrationService(UserService users, CompanyService companies, TenantProvisioningService tenants) {
        this.users = users;
        this.companies = companies;
        this.tenants = tenants;
    }

    @Transactional
    public Result registerSimple(String phone, String password, boolean enterprise,
                                 String companyName, String unifiedCreditCode,
                                 String legalPerson, String contactEmail) {
        String email = contactEmail == null || contactEmail.isBlank() ? phone + "@example.com" : contactEmail.trim();
        UserEntity user = users.createUser(phone, email, password, "USER");
        user.setPhone(phone);
        user.setEmailVerified(true);
        user.setUserType(enterprise ? "ENTERPRISE" : "PERSONAL");
        users.updateUser(user);

        Long companyId = null;
        if (enterprise) {
            CompanyEntity company = new CompanyEntity();
            company.setName(companyName.trim());
            company.setUnifiedCreditCode(blankToNull(unifiedCreditCode));
            company.setLegalPerson(blankToNull(legalPerson));
            company.setContactEmail(blankToNull(contactEmail));
            company.setContactPhone(phone);
            CompanyEntity saved = companies.createCompany(company, user.getId());
            companyId = saved.getId();
            tenants.bindUserToCompany(user, companyId);
        }
        return new Result(user.getId(), user.getUsername(), user.getUserType(), companyId);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
