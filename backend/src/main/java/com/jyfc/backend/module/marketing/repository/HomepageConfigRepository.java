package com.jyfc.backend.module.marketing.repository;

import com.jyfc.backend.module.marketing.entity.HomepageConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HomepageConfigRepository extends JpaRepository<HomepageConfig, Long> {
    List<HomepageConfig> findByStatusOrderBySortOrderAsc(Integer status);
}
