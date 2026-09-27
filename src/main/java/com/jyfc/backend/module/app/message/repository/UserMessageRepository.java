package com.jyfc.backend.module.app.message.repository;

import com.jyfc.backend.module.app.message.entity.UserMessageEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserMessageRepository extends JpaRepository<UserMessageEntity, Long> {

    Page<UserMessageEntity> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<UserMessageEntity> findByUserIdAndTypeOrderByCreatedAtDesc(Long userId, String type, Pageable pageable);

    long countByUserIdAndIsReadFalse(Long userId);

    @Modifying
    @Query("UPDATE UserMessageEntity m SET m.isRead = true WHERE m.id = :id AND m.userId = :userId")
    int markRead(@Param("id") Long id, @Param("userId") Long userId);
}
