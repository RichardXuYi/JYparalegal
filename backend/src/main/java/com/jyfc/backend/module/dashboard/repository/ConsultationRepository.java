package com.jyfc.backend.module.dashboard.repository;

import com.jyfc.backend.module.dashboard.entity.ConsultationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConsultationRepository extends JpaRepository<ConsultationEntity, Long> {
    List<ConsultationEntity> findByUserId(Long userId);
    Page<ConsultationEntity> findByUserId(Long userId, Pageable pageable);
    long countByUserId(Long userId);
}
