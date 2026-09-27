package com.jyfc.backend.module.sitecontent.controller;

import com.jyfc.backend.module.sitecontent.dto.SiteContentDTO;
import com.jyfc.backend.module.sitecontent.entity.SiteContent;
import com.jyfc.backend.module.sitecontent.repository.SiteContentRepository;
import com.jyfc.backend.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/site/contents")
public class AdminSiteContentController {
    private final SiteContentRepository siteContentRepository;

    public AdminSiteContentController(SiteContentRepository siteContentRepository) {
        this.siteContentRepository = siteContentRepository;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    public ApiResponse<List<SiteContent>> list(@RequestParam(required = false) String section) {
        List<SiteContent> list;
        if (section != null && !section.isEmpty()) {
            list = siteContentRepository.findBySectionOrderBySortOrderAsc(section);
        } else {
            list = siteContentRepository.findAllByOrderBySectionAscSortOrderAsc();
        }
        return ApiResponse.success(list);
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    @Transactional
    public ApiResponse<SiteContent> create(@Valid @RequestBody SiteContentDTO dto) {
        // Check if already exists
        if (siteContentRepository.findBySectionAndContentKey(dto.getSection(), dto.getContentKey()).isPresent()) {
            return ApiResponse.error(400, "Content with this section and key already exists");
        }

        SiteContent content = new SiteContent();
        content.setSection(dto.getSection());
        content.setContentKey(dto.getContentKey());
        content.setContentValue(dto.getContentValue());
        content.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        content.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
        return ApiResponse.success(siteContentRepository.save(content));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    @Transactional
    public ApiResponse<SiteContent> update(@PathVariable Long id, @Valid @RequestBody SiteContentDTO dto) {
        return siteContentRepository.findById(id).map(content -> {
            content.setSection(dto.getSection());
            content.setContentKey(dto.getContentKey());
            content.setContentValue(dto.getContentValue());
            if (dto.getSortOrder() != null) content.setSortOrder(dto.getSortOrder());
            if (dto.getStatus() != null) content.setStatus(dto.getStatus());
            return ApiResponse.success("Content updated successfully", siteContentRepository.save(content));
        }).orElse(ApiResponse.error(404, "Content not found"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    @Transactional
    public ApiResponse<Void> delete(@PathVariable Long id) {
        return siteContentRepository.findById(id).map(content -> {
            siteContentRepository.delete(content);
            return ApiResponse.success("Content deleted successfully", (Void) null);
        }).orElse(ApiResponse.error(404, "Content not found"));
    }
}
