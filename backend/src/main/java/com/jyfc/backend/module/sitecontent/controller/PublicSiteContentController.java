package com.jyfc.backend.module.sitecontent.controller;

import com.jyfc.backend.module.sitecontent.entity.SiteContent;
import com.jyfc.backend.module.sitecontent.repository.SiteContentRepository;
import com.jyfc.backend.shared.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/public/site")
public class PublicSiteContentController {
    private final SiteContentRepository siteContentRepository;

    public PublicSiteContentController(SiteContentRepository siteContentRepository) {
        this.siteContentRepository = siteContentRepository;
    }

    @GetMapping("/{section}")
    public ApiResponse<Map<String, Object>> getSection(@PathVariable String section) {
        List<SiteContent> items = siteContentRepository
                .findBySectionAndStatusOrderBySortOrderAsc(section, 1);

        Map<String, Object> result = new HashMap<>();
        result.put("section", section);
        result.put("items", items);
        return ApiResponse.success(result);
    }

    @GetMapping("/all")
    public ApiResponse<Map<String, List<SiteContent>>> getAll() {
        List<SiteContent> allItems = siteContentRepository
                .findAllByOrderBySectionAscSortOrderAsc();

        // Filter only enabled items and group by section
        Map<String, List<SiteContent>> grouped = allItems.stream()
                .filter(item -> item.getStatus() != null && item.getStatus() == 1)
                .collect(Collectors.groupingBy(SiteContent::getSection));

        return ApiResponse.success(grouped);
    }
}
