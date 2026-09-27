package com.jyfc.backend.module.news.repository;

import com.jyfc.backend.module.news.entity.NewsComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NewsCommentRepository extends JpaRepository<NewsComment, Long> {

    List<NewsComment> findByNewsIdAndStatusOrderByCreatedAtDesc(Long newsId, Integer status);

    long countByNewsIdAndStatus(Long newsId, Integer status);

    long countByUserId(Long userId);

    List<NewsComment> findByUserIdOrderByCreatedAtDesc(Long userId);

    long countByParentId(Long parentId);
}