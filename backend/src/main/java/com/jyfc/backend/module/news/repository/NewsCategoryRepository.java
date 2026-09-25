package com.jyfc.backend.module.news.repository;

import com.jyfc.backend.module.news.entity.NewsCategory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NewsCategoryRepository extends JpaRepository<NewsCategory, Long> {
}
