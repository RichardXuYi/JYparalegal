package com.jyfc.backend.module.auth.repository;

import com.jyfc.backend.module.auth.entity.PositionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PositionRepository extends JpaRepository<PositionEntity, Long> {
    List<PositionEntity> findByCompanyIdAndStatus(Long companyId, Integer status);
    List<PositionEntity> findByDepartmentIdAndStatus(Long departmentId, Integer status);
}