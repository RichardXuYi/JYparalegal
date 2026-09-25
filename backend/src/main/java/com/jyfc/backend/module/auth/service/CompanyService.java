package com.jyfc.backend.module.auth.service;

import com.jyfc.backend.module.auth.entity.CompanyEntity;
import com.jyfc.backend.module.auth.repository.CompanyRepository;
import com.jyfc.backend.module.tenant.service.TenantProvisioningService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class CompanyService {

    private static final Logger log = LoggerFactory.getLogger(CompanyService.class);

    private final CompanyRepository companyRepository;
    private final TenantProvisioningService tenantProvisioningService;

    public CompanyService(CompanyRepository companyRepository, TenantProvisioningService tenantProvisioningService) {
        this.companyRepository = companyRepository;
        this.tenantProvisioningService = tenantProvisioningService;
    }

    /**
     * 创建企业
     */
    @Transactional
    public CompanyEntity createCompany(CompanyEntity company, Long ownerUserId) {
        log.debug("Creating company: {} for user: {}", company.getName(), ownerUserId);

        // 检查统一信用代码是否已存在
        if (company.getUnifiedCreditCode() != null && !company.getUnifiedCreditCode().isBlank()) {
            Optional<CompanyEntity> existing = companyRepository.findByUnifiedCreditCode(company.getUnifiedCreditCode());
            if (existing.isPresent()) {
                throw new IllegalArgumentException("该统一社会信用代码已被注册");
            }
        }

        company.setOwnerUserId(ownerUserId);
        company.setStatus(1); // 正常状态
        CompanyEntity saved = companyRepository.save(company);
        // 运行时租户供给：为新企业建 ENTERPRISE 租户并绑定 owner（幂等）
        tenantProvisioningService.provisionCompany(saved);
        return saved;
    }

    /**
     * 根据ID获取企业
     */
    public Optional<CompanyEntity> findById(Long id) {
        if (id == null) return Optional.empty();
        return companyRepository.findById(id);
    }

    /**
     * 获取用户拥有的企业
     */
    public Optional<CompanyEntity> findByOwnerUserId(Long ownerUserId) {
        if (ownerUserId == null) return Optional.empty();
        return companyRepository.findByOwnerUserId(ownerUserId);
    }

    /**
     * 更新企业信息
     */
    @Transactional
    public CompanyEntity updateCompany(CompanyEntity company) {
        log.debug("Updating company: {}", company.getId());
        return companyRepository.save(company);
    }

    /**
     * 删除企业（软删除）
     */
    @Transactional
    public void deleteCompany(Long id) {
        if (id == null) return;
        log.debug("Deleting company: {}", id);
        companyRepository.findById(id).ifPresent(company -> {
            company.setStatus(0);
            companyRepository.save(company);
        });
    }

    /**
     * 获取用户的企业列表
     */
    public List<CompanyEntity> findByUserId(Long userId) {
        if (userId == null) return List.of();
        return companyRepository.findByOwnerUserIdAndStatus(userId, 1);
    }

    /**
     * 获取所有正常状态企业（仅供管理员使用，业务层需做权限校验）
     */
    public List<CompanyEntity> findAllActive() {
        return companyRepository.findAll().stream()
                .filter(c -> c.getStatus() != null && c.getStatus() == 1)
                .toList();
    }
}