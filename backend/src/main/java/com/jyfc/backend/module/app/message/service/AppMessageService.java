package com.jyfc.backend.module.app.message.service;

import com.jyfc.backend.module.app.message.entity.UserMessageEntity;
import com.jyfc.backend.module.app.message.repository.UserMessageRepository;
import com.jyfc.backend.module.auth.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppMessageService {

    private final UserMessageRepository messageRepository;
    private final UserRepository userRepository;

    @Autowired
    public AppMessageService(UserMessageRepository messageRepository,
                             UserRepository userRepository) {
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
    }

    public Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return userRepository.findByUsername(auth.getName()).map(u -> u.getId()).orElse(null);
    }

    public Page<UserMessageEntity> listMessages(Long userId, String type, int page, int size) {
        if (userId == null) return Page.empty();
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(100, size)));
        if (type == null || type.isBlank() || "all".equalsIgnoreCase(type)) {
            return messageRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        }
        return messageRepository.findByUserIdAndTypeOrderByCreatedAtDesc(userId, type, pageable);
    }

    public long getUnreadCount(Long userId) {
        if (userId == null) return 0;
        return messageRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Transactional
    public boolean markRead(Long userId, Long messageId) {
        if (userId == null || messageId == null) return false;
        return messageRepository.markRead(messageId, userId) > 0;
    }

    @Transactional
    public boolean delete(Long userId, Long messageId) {
        if (userId == null || messageId == null) return false;
        var opt = messageRepository.findById(messageId);
        if (opt.isEmpty() || !opt.get().getUserId().equals(userId)) return false;
        messageRepository.deleteById(messageId);
        return true;
    }
}
