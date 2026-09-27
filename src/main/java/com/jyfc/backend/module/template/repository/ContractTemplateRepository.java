package com.jyfc.backend.module.template.repository;

import com.jyfc.backend.module.template.entity.ContractTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContractTemplateRepository extends JpaRepository<ContractTemplateEntity, Long> {

    /** 租户隔离列表（D12 应用层强制）：只回本租户行。 */
    List<ContractTemplateEntity> findAllByTenantIdOrderByIdDesc(Long tenantId);

    /** 租户隔离读（D12 应用层强制）：只回本租户行。 */
    Optional<ContractTemplateEntity> findByIdAndTenantId(Long id, Long tenantId);
}
