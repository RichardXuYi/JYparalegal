package com.jyfc.backend.module.tenant.service;

import com.jyfc.backend.module.auth.entity.CompanyEntity;
import com.jyfc.backend.module.auth.entity.UserEntity;
import com.jyfc.backend.module.auth.repository.CompanyRepository;
import com.jyfc.backend.module.auth.repository.UserRepository;
import com.jyfc.backend.module.tenant.entity.TenantEntity;
import com.jyfc.backend.module.tenant.repository.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 给用户和企业补租户。已有租户时不重复创建。 */
@Service
public class TenantProvisioningService {

    private final TenantRepository tenants;
    private final UserRepository users;
    private final CompanyRepository companies;

    public TenantProvisioningService(TenantRepository tenants, UserRepository users, CompanyRepository companies) {
        this.tenants = tenants;
        this.users = users;
        this.companies = companies;
    }

    @Transactional
    public void provisionPersonalUser(UserEntity user) {
        if (user == null || user.getId() == null) return;
        if (user.getTenantId() != null && user.getTenantId() != 0L) return;
        TenantEntity tenant = new TenantEntity();
        tenant.setName(user.getUsername() == null ? "个人" : user.getUsername());
        tenant.setTenantType("PERSONAL");
        tenant.setStatus("ACTIVE");
        tenant.setOwnerUserId(user.getId());
        tenant = tenants.save(tenant);
        user.setTenantId(tenant.getId());
        users.save(user);
    }

    @Transactional
    public void provisionCompany(CompanyEntity company) {
        if (company == null || company.getId() == null) return;
        if (company.getTenantId() == null || company.getTenantId() == 0L) {
            TenantEntity tenant = new TenantEntity();
            tenant.setName(company.getName() == null ? "企业" : company.getName());
            tenant.setTenantType("ENTERPRISE");
            tenant.setStatus("ACTIVE");
            tenant.setOwnerUserId(company.getOwnerUserId());
            tenant = tenants.save(tenant);
            company.setTenantId(tenant.getId());
            companies.save(company);
        }
        if (company.getOwnerUserId() != null) {
            users.findById(company.getOwnerUserId()).ifPresent(owner -> bindUserToCompany(owner, company.getId()));
        }
    }

    @Transactional
    public void bindUserToCompany(UserEntity user, Long companyId) {
        if (user == null || companyId == null) return;
        CompanyEntity company = companies.findById(companyId).orElse(null);
        if (company == null) return;
        if (company.getTenantId() == null || company.getTenantId() == 0L) {
            provisionCompany(company);
            company = companies.findById(companyId).orElse(company);
        }
        if (company.getTenantId() == null) return;
        user.setTenantId(company.getTenantId());
        user.setCompanyId(companyId);
        user.setUserType("ENTERPRISE");
        users.save(user);
    }
}
