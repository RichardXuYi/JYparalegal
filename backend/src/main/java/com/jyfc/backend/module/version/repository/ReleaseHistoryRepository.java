package com.jyfc.backend.module.version.repository;

import com.jyfc.backend.module.version.entity.ReleaseHistoryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReleaseHistoryRepository extends JpaRepository<ReleaseHistoryEntity, Long> {

    Page<ReleaseHistoryEntity> findByClientTypeOrderByPublishedAtDesc(String clientType, Pageable pageable);
}
