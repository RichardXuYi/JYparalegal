package com.jyfc.backend.module.marketing.controller;

import com.jyfc.backend.shared.dto.ApiResponse;
import com.jyfc.backend.module.marketing.dto.HomepageConfigDTO;
import com.jyfc.backend.module.marketing.entity.HomepageConfig;
import com.jyfc.backend.module.marketing.repository.HomepageConfigRepository;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/homepage-configs")
public class AdminHomepageConfigController {
    private final HomepageConfigRepository homepageConfigRepository;

    public AdminHomepageConfigController(HomepageConfigRepository homepageConfigRepository) {
        this.homepageConfigRepository = homepageConfigRepository;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    public ApiResponse<List<HomepageConfig>> list(@RequestParam(required = false) Integer status) {
        // If status is not provided, return all (maybe sorted by sortOrder)
        List<HomepageConfig> list = status == null ? homepageConfigRepository.findAll() : homepageConfigRepository.findByStatusOrderBySortOrderAsc(status);
        return ApiResponse.success(list);
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    @Transactional
    public ApiResponse<HomepageConfig> create(@Valid @RequestBody HomepageConfigDTO dto) {
        HomepageConfig c = new HomepageConfig();
        c.setType(dto.getType());
        c.setTitle(dto.getTitle());
        c.setRefId(dto.getRefId());
        c.setConfigJson(dto.getConfigJson());
        c.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        c.setStatus(1); // 默认启用
        return ApiResponse.success(homepageConfigRepository.save(c));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    @Transactional
    public ApiResponse<HomepageConfig> update(@PathVariable Long id, @Valid @RequestBody HomepageConfigDTO dto) {
        return homepageConfigRepository.findById(id).map(c -> {
            c.setType(dto.getType());
            c.setTitle(dto.getTitle());
            c.setRefId(dto.getRefId());
            c.setConfigJson(dto.getConfigJson());
            if (dto.getSortOrder() != null) c.setSortOrder(dto.getSortOrder());
            return ApiResponse.success("Config updated successfully", homepageConfigRepository.save(c));
        }).orElse(ApiResponse.error(404, "Config not found"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    @Transactional
    public ApiResponse<Void> delete(@PathVariable Long id) {
        return homepageConfigRepository.findById(id).map(c -> {
            homepageConfigRepository.delete(c);
            return ApiResponse.success("Config deleted successfully", (Void)null);
        }).orElse(ApiResponse.error(404, "Config not found"));
    }
}
