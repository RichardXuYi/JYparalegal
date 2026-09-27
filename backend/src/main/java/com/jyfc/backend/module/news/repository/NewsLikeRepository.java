package com.jyfc.backend.module.news.repository;

import com.jyfc.backend.module.news.entity.NewsLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NewsLikeRepository extends JpaRepository<NewsLike, Long> {
    Optional<NewsLike> findByUserIdAndNewsId(Long userId, Long newsId);
    void deleteByUserIdAndNewsId(Long userId, Long newsId);
    long countByUserId(Long userId);
    long countByNewsId(Long newsId);
}

