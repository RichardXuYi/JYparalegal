package com.jyfc.backend.module.dashboard.repository;

import com.jyfc.backend.module.dashboard.entity.FavoriteEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FavoriteRepository extends JpaRepository<FavoriteEntity, Long> {
    Page<FavoriteEntity> findByUserId(Long userId, Pageable pageable);
    Optional<FavoriteEntity> findByUserIdAndTypeAndTargetId(Long userId, String type, Long targetId);
    void deleteByUserIdAndTypeAndTargetId(Long userId, String type, Long targetId);
    long countByUserId(Long userId);
    long countByTypeAndTargetId(String type, Long targetId);
}
