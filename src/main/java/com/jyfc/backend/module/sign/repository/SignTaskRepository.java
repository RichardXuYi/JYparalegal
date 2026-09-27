package com.jyfc.backend.module.sign.repository;

import com.jyfc.backend.module.sign.entity.SignTaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SignTaskRepository extends JpaRepository<SignTaskEntity, Long> {
    Optional<SignTaskEntity> findByTaskNo(String taskNo);

    Optional<SignTaskEntity> findByProviderFlowId(String providerFlowId);

    /** 租户隔离读（D12 应用层强制）：只回本租户行。 */
    Optional<SignTaskEntity> findByIdAndTenantId(Long id, Long tenantId);

    /** 租户隔离列表（inbox 查询式，不建镜像表）。 */
    java.util.List<SignTaskEntity> findAllByTenantIdOrderByIdDesc(Long tenantId);

    /** 过期扫描（系统级，跨租户）：指定状态且截止时间已过的任务。 */
    java.util.List<SignTaskEntity> findAllByStatusAndExpireAtBefore(String status, java.time.LocalDateTime time);
}
