package com.jyfc.backend.module.auth.repository;

import com.jyfc.backend.module.auth.entity.DepartmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DepartmentRepository extends JpaRepository<DepartmentEntity, Long> {
    List<DepartmentEntity> findByCompanyIdAndStatus(Long companyId, Integer status);
    List<DepartmentEntity> findByCompanyIdAndParentId(Long companyId, Long parentId);
}