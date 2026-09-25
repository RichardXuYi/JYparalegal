package com.jyfc.backend.module.news.controller;

import com.jyfc.backend.core.security.UserContextUtil;
import com.jyfc.backend.module.news.entity.NewsComment;
import com.jyfc.backend.module.news.service.NewsCommentService;
import com.jyfc.backend.shared.dto.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 新闻评论（用户端）
 */
@RestController
@RequestMapping("/api/app/news/{newsId}/comments")
public class NewsCommentController {

    private final NewsCommentService commentService;
    private final UserContextUtil userContextUtil;

    public NewsCommentController(NewsCommentService commentService, UserContextUtil userContextUtil) {
        this.commentService = commentService;
        this.userContextUtil = userContextUtil;
    }

    /**
     * 发表评论
     */
    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ApiResponse<NewsComment> add(@PathVariable("newsId") Long newsId,
                                        @RequestBody Map<String, Object> body) {
        Long userId = userContextUtil.getCurrentUserId();
        Object contentObj = body != null ? body.get("content") : null;
        if (contentObj == null) {
            return ApiResponse.error(400, "评论内容不能为空");
        }
        String content = String.valueOf(contentObj);
        Long parentId = null;
        if (body != null && body.get("parentId") != null) {
            try {
                parentId = Long.valueOf(String.valueOf(body.get("parentId")));
            } catch (NumberFormatException ignored) {
            }
        }
        NewsComment comment = commentService.add(newsId, userId, content, parentId);
        return ApiResponse.success("评论成功", comment);
    }

    /**
     * 评论列表
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ApiResponse<Map<String, Object>> list(@PathVariable("newsId") Long newsId) {
        List<NewsComment> comments = commentService.listByNews(newsId);
        long total = commentService.countByNews(newsId);
        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("items", comments);
        return ApiResponse.success(result);
    }

    /**
     * 删除评论（只能删自己的；管理员可删任意）
     */
    @DeleteMapping("/{commentId}")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ApiResponse<Void> delete(@PathVariable("newsId") Long newsId,
                                    @PathVariable("commentId") Long commentId) {
        Long userId = userContextUtil.getCurrentUserId();
        boolean isAdmin = userContextUtil.isAdmin();
        commentService.deleteMine(commentId, userId, isAdmin);
        return ApiResponse.success("已删除", null);
    }
}