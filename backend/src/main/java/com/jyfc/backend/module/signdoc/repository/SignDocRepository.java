package com.jyfc.backend.module.signdoc.repository;

import com.jyfc.backend.module.signdoc.entity.SignDocEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** 签署文档仓库：任务维度列表 + 租户隔离读（D12 应用层强制）。 */
public interface SignDocRepository extends JpaRepository<SignDocEntity, Long> {
    List<SignDocEntity> findAllByTaskIdOrderByIdAsc(Long taskId);

    Optional<SignDocEntity> findByIdAndTenantId(Long id, Long tenantId);
}
