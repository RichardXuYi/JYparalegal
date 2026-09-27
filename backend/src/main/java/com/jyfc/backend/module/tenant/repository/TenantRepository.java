package com.jyfc.backend.module.tenant.repository;

import com.jyfc.backend.module.tenant.entity.TenantEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<TenantEntity, Long> {
}
