package com.jyfc.backend.module.news.controller;

import com.jyfc.backend.shared.dto.ApiResponse;
import com.jyfc.backend.module.news.dto.NewsDTO;
import com.jyfc.backend.module.news.entity.News;
import com.jyfc.backend.module.news.repository.NewsRepository;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/news")
public class AdminNewsController {
    private final NewsRepository newsRepository;

    public AdminNewsController(NewsRepository newsRepository) {
        this.newsRepository = newsRepository;
    }

    // 后台列表排序：置顶优先 -> 排序值降序 -> 创建时间降序
    private static final Sort ADMIN_SORT = Sort.by(
            Sort.Order.desc("isTop"),
            Sort.Order.desc("sortOrder"),
            Sort.Order.desc("createdAt"));

    @GetMapping
    @Cacheable(value = "news", key = "#root.methodName + ':' + #page + ':' + #size")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    public ApiResponse<Map<String, Object>> list(
                                     @RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "10") int size,
                                     @RequestParam(required = false) Integer status,
                                     @RequestParam(required = false) String keyword) {
        int pageNum = Math.max(1, page);
        int pageSize = Math.max(1, Math.min(size, 100));

        var pageable = PageRequest.of(pageNum - 1, pageSize, ADMIN_SORT);

        Page<News> p;
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        if (hasKeyword && status != null) {
            p = newsRepository.findByTitleContainingAndStatus(keyword, status, pageable);
        } else if (hasKeyword) {
            p = newsRepository.findByTitleContaining(keyword, pageable);
        } else if (status != null) {
            p = newsRepository.findByStatus(status, pageable);
        } else {
            p = newsRepository.findAll(pageable);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", p.getContent());
        result.put("total", p.getTotalElements());
        result.put("page", pageNum);
        result.put("pageSize", pageSize);
        return ApiResponse.success(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    public ApiResponse<News> detail(@PathVariable long id) {
        return newsRepository.findById(id)
                .map(ApiResponse::success)
                .orElse(ApiResponse.error(404, "News not found"));
    }

    @PostMapping
    @CacheEvict(value = "news", allEntries = true)
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    @Transactional
    public ApiResponse<News> create(@Valid @RequestBody NewsDTO dto) {
        News n = new News();
        applyDto(n, dto);
        int status = dto.getStatus() != null ? dto.getStatus() : 0;
        n.setStatus(status);
        if (status == 1 && n.getPublishTime() == null) {
            n.setPublishTime(LocalDateTime.now());
        }
        return ApiResponse.success(newsRepository.save(n));
    }

    @PutMapping("/{id}")
    @CacheEvict(value = "news", allEntries = true)
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    @Transactional
    public ApiResponse<News> update(@PathVariable long id, @Valid @RequestBody NewsDTO dto) {
        return newsRepository.findById(id).map(n -> {
            applyDto(n, dto);
            if (dto.getStatus() != null) {
                n.setStatus(dto.getStatus());
                if (dto.getStatus() == 1 && n.getPublishTime() == null) {
                    n.setPublishTime(LocalDateTime.now());
                }
            }
            return ApiResponse.success("News updated successfully", newsRepository.save(n));
        }).orElse(ApiResponse.error(404, "News not found"));
    }

    @PatchMapping("/{id}/status")
    @CacheEvict(value = "news", allEntries = true)
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    @Transactional
    public ApiResponse<News> updateStatus(@PathVariable long id, @RequestBody Map<String, Object> body) {
        Object statusObj = body.get("status");
        if (statusObj == null) {
            return ApiResponse.error(400, "Status is required");
        }
        Integer status = statusObj instanceof Integer
                ? (Integer) statusObj
                : Integer.valueOf(String.valueOf(statusObj));
        if (status < 0 || status > 2) {
            return ApiResponse.error(400, "Status must be 0 (draft), 1 (published) or 2 (offline)");
        }
        return newsRepository.findById(id).map(n -> {
            n.setStatus(status);
            if (status == 1 && n.getPublishTime() == null) {
                n.setPublishTime(LocalDateTime.now());
            }
            return ApiResponse.success(newsRepository.save(n));
        }).orElse(ApiResponse.error(404, "News not found"));
    }

    @DeleteMapping("/{id}")
    @CacheEvict(value = "news", allEntries = true)
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    @Transactional
    public ApiResponse<Void> delete(@PathVariable long id) {
        return newsRepository.findById(id).map(n -> {
            newsRepository.delete(n);
            return ApiResponse.success("News deleted successfully", (Void) null);
        }).orElse(ApiResponse.error(404, "News not found"));
    }

    // 把 DTO 的公共字段写入实体（不含 status，status 由调用方按场景处理）
    private void applyDto(News n, NewsDTO dto) {
        n.setTitle(dto.getTitle());
        n.setContent(dto.getContent());
        n.setSummary(dto.getSummary());
        n.setCoverImage(dto.getCoverImage());
        try {
            if (dto.getCategory() != null) n.setCategoryId(Long.parseLong(dto.getCategory()));
        } catch (NumberFormatException ignored) {}
        if (dto.getSortOrder() != null) n.setSortOrder(dto.getSortOrder());
        if (dto.getIsTop() != null) n.setIsTop(dto.getIsTop());
    }
}
