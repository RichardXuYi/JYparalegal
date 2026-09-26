package com.jyfc.backend.module.notification.repository;

import com.jyfc.backend.module.notification.entity.NotificationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {

    /** 分页查询某用户的通知（按时间倒序） */
    Page<NotificationEntity> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /** 分页查询某用户的未读通知 */
    Page<NotificationEntity> findByUserIdAndIsReadOrderByCreatedAtDesc(
        Long userId, Boolean isRead, Pageable pageable);

    /** 统计某用户未读数量 */
    long countByUserIdAndIsRead(Long userId, Boolean isRead);

    /**
     * 查找指定用户的某条通知（用于"标已读"接口的归属校验）
     */
    Optional<NotificationEntity> findByIdAndUserId(Long id, Long userId);

    /**
     * 将某用户全部未读通知置为已读
     */
    @Modifying
    @Query("UPDATE NotificationEntity n SET n.isRead = true WHERE n.userId = :userId AND n.isRead = false")
    int markAllAsReadForUser(@Param("userId") Long userId);
}
