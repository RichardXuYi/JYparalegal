package com.jyfc.backend.module.evidence.repository;

import com.jyfc.backend.module.evidence.entity.EvidenceItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EvidenceItemRepository extends JpaRepository<EvidenceItemEntity, Long> {

    /** 租户隔离列表（D12 应用层强制）：只回本租户行。 */
    List<EvidenceItemEntity> findAllByTenantIdOrderByIdDesc(Long tenantId);

    /** 租户隔离 + 业务对象挂接查询（biz_type+biz_id）。 */
    List<EvidenceItemEntity> findAllByTenantIdAndBizTypeAndBizIdOrderByIdDesc(Long tenantId, String bizType, Long bizId);

    /** 租户隔离读（D12 应用层强制）：只回本租户行。 */
    Optional<EvidenceItemEntity> findByIdAndTenantId(Long id, Long tenantId);
}
