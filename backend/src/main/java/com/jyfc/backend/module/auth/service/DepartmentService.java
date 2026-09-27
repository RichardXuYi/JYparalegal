package com.jyfc.backend.module.auth.service;

import com.jyfc.backend.module.auth.entity.DepartmentEntity;
import com.jyfc.backend.module.auth.repository.CompanyRepository;
import com.jyfc.backend.module.auth.repository.DepartmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class DepartmentService {

    private static final Logger log = LoggerFactory.getLogger(DepartmentService.class);

    private final DepartmentRepository departmentRepository;
    private final CompanyRepository companyRepository;

    public DepartmentService(DepartmentRepository departmentRepository, CompanyRepository companyRepository) {
        this.departmentRepository = departmentRepository;
        this.companyRepository = companyRepository;
    }

    /**
     * 创建部门
     */
    @Transactional
    public DepartmentEntity createDepartment(DepartmentEntity department, Long companyId) {
        if (department == null) throw new IllegalArgumentException("Department must not be null");
        if (companyId == null) throw new IllegalArgumentException("companyId must not be null");
        log.debug("Creating department: {} for company: {}", department.getName(), companyId);

        // 验证企业存在
        if (!companyRepository.existsById(companyId)) {
            throw new IllegalArgumentException("企业不存在");
        }

        // 检查企业归属
        department.setCompanyId(companyId);
        department.setStatus(1);
        return departmentRepository.save(department);
    }

    /**
     * 获取企业部门列表
     */
    public List<DepartmentEntity> findByCompanyId(Long companyId) {
        if (companyId == null) return List.of();
        return departmentRepository.findByCompanyIdAndStatus(companyId, 1);
    }

    /**
     * 获取子部门
     */
    public List<DepartmentEntity> findChildren(Long companyId, Long parentId) {
        if (companyId == null || parentId == null) return List.of();
        return departmentRepository.findByCompanyIdAndParentId(companyId, parentId);
    }

    /**
     * 根据ID获取部门
     */
    public Optional<DepartmentEntity> findById(Long id) {
        if (id == null) return Optional.empty();
        return departmentRepository.findById(id);
    }

    /**
     * 更新部门
     */
    @Transactional
    public DepartmentEntity updateDepartment(DepartmentEntity department) {
        if (department == null) throw new IllegalArgumentException("Department must not be null");
        log.debug("Updating department: {}", department.getId());
        return departmentRepository.save(department);
    }

    /**
     * 删除部门（软删除）
     */
    @Transactional
    public void deleteDepartment(Long id) {
        if (id == null) return;
        log.debug("Deleting department: {}", id);
        departmentRepository.findById(id).ifPresent(dept -> {
            dept.setStatus(0);
            departmentRepository.save(dept);
        });
    }
}