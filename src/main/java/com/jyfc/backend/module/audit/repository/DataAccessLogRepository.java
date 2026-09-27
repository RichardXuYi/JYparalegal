package com.jyfc.backend.module.audit.repository;

import com.jyfc.backend.module.audit.entity.DataAccessLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DataAccessLogRepository extends JpaRepository<DataAccessLogEntity, Long> {
}
