package com.jyfc.backend.module.integration.wecom.repository;

import com.jyfc.backend.module.integration.wecom.entity.WeComDepartmentMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WeComDepartmentMappingRepository extends JpaRepository<WeComDepartmentMapping, Long> {

    Optional<WeComDepartmentMapping> findByWecomDeptId(Long wecomDeptId);

    List<WeComDepartmentMapping> findByParentId(Long parentId);

    void deleteByWecomDeptId(Long wecomDeptId);
}
