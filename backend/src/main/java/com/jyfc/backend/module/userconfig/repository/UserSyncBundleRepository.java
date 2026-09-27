package com.jyfc.backend.module.userconfig.repository;

import com.jyfc.backend.module.userconfig.entity.UserSyncBundleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserSyncBundleRepository extends JpaRepository<UserSyncBundleEntity, Long> {

    List<UserSyncBundleEntity> findByUserId(Long userId);

    List<UserSyncBundleEntity> findByUserIdAndKind(Long userId, String kind);

    Optional<UserSyncBundleEntity> findByUserIdAndKindAndSlug(Long userId, String kind, String slug);

    void deleteByUserIdAndKindAndSlug(Long userId, String kind, String slug);
}
