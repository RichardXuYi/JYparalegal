package com.jyfc.backend.module.audit.repository;

import com.jyfc.backend.module.audit.entity.AuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {
}
