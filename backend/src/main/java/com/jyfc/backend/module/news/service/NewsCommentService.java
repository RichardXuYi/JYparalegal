package com.jyfc.backend.module.news.service;

import com.jyfc.backend.module.news.entity.NewsComment;
import com.jyfc.backend.module.news.repository.NewsCommentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.util.List;

@Service
public class NewsCommentService {

    private final NewsCommentRepository commentRepository;

    public NewsCommentService(NewsCommentRepository commentRepository) {
        this.commentRepository = commentRepository;
    }

    /**
     * 发表评论
     */
    @Transactional
    public NewsComment add(Long newsId, Long userId, String content, Long parentId) {
        if (newsId == null) throw new IllegalArgumentException("newsId must not be null");
        if (userId == null) throw new IllegalArgumentException("userId must not be null");
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("评论内容不能为空");
        }
        if (content.length() > 2000) {
            throw new IllegalArgumentException("评论内容不能超过 2000 字");
        }

        NewsComment c = new NewsComment();
        c.setNewsId(newsId);
        c.setUserId(userId);
        c.setContent(HtmlUtils.htmlEscape(content.trim()));
        c.setParentId(parentId);
        c.setStatus(NewsComment.STATUS_VISIBLE);
        return commentRepository.save(c);
    }

    /**
     * 删除自己的评论
     */
    @Transactional
    public void deleteMine(Long commentId, Long userId, boolean isAdmin) {
        if (commentId == null) throw new IllegalArgumentException("commentId must not be null");
        if (userId == null) throw new IllegalArgumentException("userId must not be null");
        NewsComment c = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalStateException("评论不存在"));
        if (!isAdmin && !c.getUserId().equals(userId)) {
            throw new IllegalStateException("无权删除他人评论");
        }
        // 有回复的评论，改为隐藏而不是物理删除
        long replyCount = commentRepository.countByParentId(commentId);
        if (replyCount > 0) {
            c.setStatus(NewsComment.STATUS_HIDDEN);
            c.setContent("[该评论已删除]");
            commentRepository.save(c);
        } else {
            commentRepository.delete(c);
        }
    }

    public List<NewsComment> listByNews(Long newsId) {
        if (newsId == null) return List.of();
        return commentRepository.findByNewsIdAndStatusOrderByCreatedAtDesc(newsId, NewsComment.STATUS_VISIBLE);
    }

    public long countByNews(Long newsId) {
        if (newsId == null) return 0L;
        return commentRepository.countByNewsIdAndStatus(newsId, NewsComment.STATUS_VISIBLE);
    }

    public List<NewsComment> listByUser(Long userId) {
        if (userId == null) return List.of();
        return commentRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }
}