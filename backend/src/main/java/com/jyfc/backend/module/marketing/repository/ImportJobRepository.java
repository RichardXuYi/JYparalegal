package com.jyfc.backend.module.marketing.repository;

import com.jyfc.backend.module.marketing.entity.ImportJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportJobRepository extends JpaRepository<ImportJob, Long> {
    Page<ImportJob> findByModuleOrderByCreatedAtDesc(String module, Pageable pageable);
}

