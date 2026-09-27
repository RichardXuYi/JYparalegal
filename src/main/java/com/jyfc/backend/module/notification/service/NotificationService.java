package com.jyfc.backend.module.notification.service;

import com.jyfc.backend.module.notification.dto.NotificationDto;
import com.jyfc.backend.module.notification.entity.NotificationEntity;
import com.jyfc.backend.module.notification.repository.NotificationRepository;
import com.jyfc.backend.shared.dto.PageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 统一通知中心服务（MVP）
 * <p>
 * 不接 AI 聊天消息、不接钉钉/飞书/企微，仅供业务代码创建站内通知。
 */
@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /**
     * 创建一条通知。允许 userId 为空（系统级通知后续可扩展），MVP 仅对非空 userId 入库。
     *
     * @return 已保存实体的 ID；userId 为空时返回 null，调用方不依赖此返回值做后续逻辑。
     */
    @Transactional
    public Long createNotification(Long userId,
                                   NotificationEntity.Type type,
                                   String title,
                                   String content,
                                   String linkUrl) {
        if (userId == null) {
            log.debug("通知接收人为空，跳过: title={}", title);
            return null;
        }
        if (type == null) {
            log.warn("通知类型为空，使用 SYSTEM 占位: title={}", title);
            type = NotificationEntity.Type.SYSTEM;
        }
        NotificationEntity entity = new NotificationEntity(userId, type, title, content, linkUrl);
        NotificationEntity saved = notificationRepository.save(entity);
        log.info("通知已创建: id={}, userId={}, type={}, title={}",
            saved.getId(), userId, type, title);
        return saved.getId();
    }

    /**
     * 分页查询用户通知
     *
     * @param userId     当前用户 ID
     * @param page       页码（0 起）
     * @param size       每页条数
     * @param unreadOnly true 表示只看未读
     */
    @Transactional(readOnly = true)
    public PageResponse<NotificationDto> getUserNotifications(Long userId, int page, int size, boolean unreadOnly) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        Pageable pageable = PageRequest.of(safePage, safeSize);
        Page<NotificationEntity> pageData = unreadOnly
            ? notificationRepository.findByUserIdAndIsReadOrderByCreatedAtDesc(userId, false, pageable)
            : notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        List<NotificationDto> items = pageData.getContent().stream()
            .map(NotificationDto::from)
            .collect(Collectors.toList());
        return new PageResponse<>(items, pageData.getTotalElements(), safePage, safeSize);
    }

    /**
     * 获取未读数量
     */
    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndIsRead(userId, false);
    }

    /**
     * 标记单条已读
     *
     * @return 找到且标记成功返回 true；找不到或不属于该用户返回 false
     */
    @Transactional
    public boolean markAsRead(Long userId, Long notificationId) {
        return notificationRepository.findByIdAndUserId(notificationId, userId)
            .map(n -> {
                if (Boolean.TRUE.equals(n.getIsRead())) {
                    return true;
                }
                n.setIsRead(true);
                notificationRepository.save(n);
                return true;
            })
            .orElse(false);
    }

    /**
     * 全部标记为已读
     *
     * @return 受影响条数
     */
    @Transactional
    public int markAllAsRead(Long userId) {
        return notificationRepository.markAllAsReadForUser(userId);
    }
}
