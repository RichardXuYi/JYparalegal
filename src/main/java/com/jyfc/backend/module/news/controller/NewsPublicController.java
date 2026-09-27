package com.jyfc.backend.module.news.controller;

import com.jyfc.backend.shared.dto.ApiResponse;
import com.jyfc.backend.module.news.entity.News;
import com.jyfc.backend.module.news.repository.NewsRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/public/news")
public class NewsPublicController {
    private final NewsRepository newsRepository;
    public NewsPublicController(NewsRepository newsRepository) { this.newsRepository = newsRepository; }

    // 公开列表排序：置顶优先 -> 排序值降序 -> 发布时间/创建时间降序
    private static final Sort PUBLIC_SORT = Sort.by(
            Sort.Order.desc("isTop"),
            Sort.Order.desc("sortOrder"),
            Sort.Order.desc("publishTime"),
            Sort.Order.desc("createdAt"));

    @GetMapping
    public ApiResponse<List<News>> list() {
        var pageable = PageRequest.of(0, Integer.MAX_VALUE, PUBLIC_SORT);
        List<News> newsList = newsRepository.findByStatus(1, pageable).getContent();
        return ApiResponse.success("News retrieved successfully", newsList);
    }

    @GetMapping("/{id}")
    public ApiResponse<News> detail(@PathVariable long id) {
        return newsRepository.findById(id)
                .filter(n -> n.getStatus() != null && n.getStatus() == 1)
                .map(ApiResponse::success)
                .orElse(ApiResponse.error(404, "News not found"));
    }
}
