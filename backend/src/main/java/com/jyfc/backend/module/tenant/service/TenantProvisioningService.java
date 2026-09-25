package com.jyfc.backend.module.tenant.service;

import com.jyfc.backend.module.auth.entity.CompanyEntity;
import com.jyfc.backend.module.auth.entity.UserEntity;
import com.jyfc.backend.module.auth.repository.CompanyRepository;
import com.jyfc.backend.module.auth.repository.UserRepository;
import com.jyfc.backend.module.tenant.entity.TenantEntity;
import com.jyfc.backend.module.tenant.repository.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 运行时租户供给（修复「V130 只在迁移时回填一次、之后无任何代码建 tenant」的缺口）。
 *
 * <p>不变量：每个 user / company 落库后都必须绑定一个非空、非根(0)的 tenant_id，
 * 否则法律域（签署/庭审/语音/证据/比对/模板/企业账户）的 requireTenant 守卫会一律拒绝。
 * 所有方法幂等，可在 bootstrap 每次启动、注册、建公司、成员分配等路径重复调用。</p>
 */
@Service
public class TenantProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(TenantProvisioningService.class);

    public static final String TYPE_ENTERPRISE = "ENTERPRISE";
    public static final String TYPE_INDIVIDUAL = "INDIVIDUAL";
    private static final String STATUS_ACTIVE = "ACTIVE";

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;

    public TenantProvisioningService(TenantRepository tenantRepository,
                                     UserRepository userRepository,
                                     CompanyRepository companyRepository) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
    }

    private static boolean unbound(Long tenantId) {
        return tenantId == null || tenantId == 0L;
    }

    /** 确保 userId 拥有一个 INDIVIDUAL 租户，返回其 id（幂等）。 */
    @Transactional
    public Long ensureIndividualTenant(Long userId) {
        if (userId == null) return null;
        TenantEntity tenant = tenantRepository
                .findFirstByOwnerUserIdAndTenantTypeOrderByIdAsc(userId, TYPE_INDIVIDUAL)
                .orElseGet(() -> {
                    TenantEntity n = new TenantEntity();
                    n.setName("personal-" + userId);
                    n.setTenantType(TYPE_INDIVIDUAL);
                    n.setStatus(STATUS_ACTIVE);
                    n.setOwnerUserId(userId);
                    TenantEntity saved = tenantRepository.save(n);
                    log.info("创建个人租户: id={}, ownerUserId={}", saved.getId(), userId);
                    return saved;
                });
        return tenant.getId();
    }

    /** 确保某企业拥有一个 ENTERPRISE 租户（按 owner+name 匹配），返回其 id（幂等）。 */
    @Transactional
    public Long ensureEnterpriseTenant(String companyName, Long ownerUserId) {
        if (ownerUserId == null) return null;
        String name = (companyName == null || companyName.isBlank()) ? ("company-" + ownerUserId) : companyName;
        TenantEntity tenant = tenantRepository
                .findFirstByOwnerUserIdAndTenantTypeAndNameOrderByIdAsc(ownerUserId, TYPE_ENTERPRISE, name)
                .orElseGet(() -> {
                    TenantEntity n = new TenantEntity();
                    n.setName(name);
                    n.setTenantType(TYPE_ENTERPRISE);
                    n.setStatus(STATUS_ACTIVE);
                    n.setOwnerUserId(ownerUserId);
                    TenantEntity saved = tenantRepository.save(n);
                    log.info("创建企业租户: id={}, name={}, ownerUserId={}", saved.getId(), name, ownerUserId);
                    return saved;
                });
        return tenant.getId();
    }

    /** 为一个新建的个人用户绑定 INDIVIDUAL 租户（若尚未绑定）。 */
    @Transactional
    public void provisionPersonalUser(UserEntity user) {
        if (user == null || user.getId() == null || !unbound(user.getTenantId())) return;
        Long tid = ensureIndividualTenant(user.getId());
        user.setTenantId(tid);
        userRepository.save(user);
    }

    /**
     * 为一个企业绑定 ENTERPRISE 租户，并把企业 owner 一并挂到该租户。返回 tenant id（幂等）。
     */
    @Transactional
    public Long provisionCompany(CompanyEntity company) {
        if (company == null || company.getId() == null) return null;
        Long tid = company.getTenantId();
        if (unbound(tid)) {
            tid = ensureEnterpriseTenant(company.getName(), company.getOwnerUserId());
            company.setTenantId(tid);
            companyRepository.save(company);
        }
        if (company.getOwnerUserId() != null) {
            final Long tenantId = tid;
            userRepository.findById(company.getOwnerUserId()).ifPresent(owner -> {
                if (!tenantId.equals(owner.getTenantId())) {
                    owner.setTenantId(tenantId);
                    userRepository.save(owner);
                }
            });
        }
        return tid;
    }

    /** 把一个成员用户挂到某企业的 ENTERPRISE 租户（企业尚未建租户时顺带补建）。 */
    @Transactional
    public void bindUserToCompany(UserEntity user, Long companyId) {
        if (user == null || companyId == null) return;
        companyRepository.findById(companyId).ifPresent(company -> {
            Long tid = unbound(company.getTenantId()) ? provisionCompany(company) : company.getTenantId();
            if (tid != null && !tid.equals(user.getTenantId())) {
                user.setTenantId(tid);
                userRepository.save(user);
            }
        });
    }
}
