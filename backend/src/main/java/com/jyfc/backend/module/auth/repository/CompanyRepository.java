package com.jyfc.backend.module.auth.repository;

import com.jyfc.backend.module.auth.entity.CompanyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CompanyRepository extends JpaRepository<CompanyEntity, Long> {
    Optional<CompanyEntity> findByOwnerUserId(Long ownerUserId);
    List<CompanyEntity> findByOwnerUserIdAndStatus(Long ownerUserId, Integer status);
    Optional<CompanyEntity> findByUnifiedCreditCode(String unifiedCreditCode);
}