package com.jyfc.backend.module.tenant.repository;

import com.jyfc.backend.module.tenant.entity.TenantEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantRepository extends JpaRepository<TenantEntity, Long> {
    Optional<TenantEntity> findFirstByOwnerUserIdAndTenantTypeOrderByIdAsc(Long ownerUserId, String tenantType);

    Optional<TenantEntity> findFirstByOwnerUserIdAndTenantTypeAndNameOrderByIdAsc(
            Long ownerUserId, String tenantType, String name);
}
