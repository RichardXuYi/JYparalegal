package com.jyfc.backend.module.account.repository;

import com.jyfc.backend.module.account.entity.TenantQuotaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 租户配额仓库：uk_quota_tenant 唯一约束下按租户取行。 */
public interface TenantQuotaRepository extends JpaRepository<TenantQuotaEntity, Long> {
    Optional<TenantQuotaEntity> findByTenantId(Long tenantId);
}
