package com.jyfc.backend.module.sign.repository;

import com.jyfc.backend.module.sign.entity.TaskEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface TaskEventRepository extends JpaRepository<TaskEventEntity, Long> {

    /** 月度送签份数口径：本租户在 [from, +∞) 内到达 CREATED 的任务数（SUBMIT 的落点状态）。 */
    long countByTenantIdAndToStatusAndCreatedAtGreaterThanEqual(Long tenantId, String toStatus, LocalDateTime from);
}
