package com.jyfc.backend.module.auth.service;

import com.jyfc.backend.module.auth.entity.DepartmentEntity;
import com.jyfc.backend.module.auth.entity.PositionEntity;
import com.jyfc.backend.module.auth.repository.DepartmentRepository;
import com.jyfc.backend.module.auth.repository.PositionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class PositionService {

    private static final Logger log = LoggerFactory.getLogger(PositionService.class);

    private final PositionRepository positionRepository;
    private final DepartmentRepository departmentRepository;

    public PositionService(PositionRepository positionRepository, DepartmentRepository departmentRepository) {
        this.positionRepository = positionRepository;
        this.departmentRepository = departmentRepository;
    }

    /**
     * 创建职位
     */
    @Transactional
    public PositionEntity createPosition(PositionEntity position, Long companyId, Long departmentId) {
        if (position == null) throw new IllegalArgumentException("Position must not be null");
        if (companyId == null) throw new IllegalArgumentException("companyId must not be null");
        if (departmentId == null) throw new IllegalArgumentException("departmentId must not be null");
        log.debug("Creating position: {} for department: {}", position.getName(), departmentId);

        // 验证部门存在且属于该企业
        Optional<DepartmentEntity> deptOpt = departmentRepository.findById(departmentId);
        if (deptOpt.isEmpty()) {
            throw new IllegalArgumentException("部门不存在");
        }
        if (!deptOpt.get().getCompanyId().equals(companyId)) {
            throw new IllegalArgumentException("部门不属于该企业");
        }

        position.setCompanyId(companyId);
        position.setDepartmentId(departmentId);
        position.setStatus(1);
        return positionRepository.save(position);
    }

    /**
     * 获取企业职位列表
     */
    public List<PositionEntity> findByCompanyId(Long companyId) {
        if (companyId == null) return List.of();
        return positionRepository.findByCompanyIdAndStatus(companyId, 1);
    }

    /**
     * 获取部门职位列表
     */
    public List<PositionEntity> findByDepartmentId(Long departmentId) {
        if (departmentId == null) return List.of();
        return positionRepository.findByDepartmentIdAndStatus(departmentId, 1);
    }

    /**
     * 根据ID获取职位
     */
    public Optional<PositionEntity> findById(Long id) {
        if (id == null) return Optional.empty();
        return positionRepository.findById(id);
    }

    /**
     * 更新职位
     */
    @Transactional
    public PositionEntity updatePosition(PositionEntity position) {
        if (position == null) throw new IllegalArgumentException("Position must not be null");
        log.debug("Updating position: {}", position.getId());
        return positionRepository.save(position);
    }

    /**
     * 删除职位（软删除）
     */
    @Transactional
    public void deletePosition(Long id) {
        if (id == null) return;
        log.debug("Deleting position: {}", id);
        positionRepository.findById(id).ifPresent(pos -> {
            pos.setStatus(0);
            positionRepository.save(pos);
        });
    }
}