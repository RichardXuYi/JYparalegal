package com.jyfc.backend.module.sign.repository;

import com.jyfc.backend.module.sign.entity.CertificateRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 出证记录仓库（uk_cert_task：一任务一证）。 */
public interface CertificateRecordRepository extends JpaRepository<CertificateRecordEntity, Long> {
    Optional<CertificateRecordEntity> findByTaskId(Long taskId);
}
