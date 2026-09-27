package com.jyfc.backend.module.news.repository;

import com.jyfc.backend.module.news.entity.News;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NewsRepository extends JpaRepository<News, Long> {
    Page<News> findByStatus(Integer status, Pageable pageable);
    Page<News> findByCategoryId(Long categoryId, Pageable pageable);
    Page<News> findByTitleContaining(String title, Pageable pageable);
    Page<News> findByTitleContainingAndStatus(String title, Integer status, Pageable pageable);
    boolean existsByTitle(String title);
    long countByStatus(Integer status);
}
