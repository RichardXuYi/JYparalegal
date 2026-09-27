package com.jyfc.backend.module.trade.repository;

import com.jyfc.backend.module.trade.entity.OrderEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<OrderEntity, Long> {
    Optional<OrderEntity> findByOrderNo(String orderNo);
    Page<OrderEntity> findByUserId(Long userId, Pageable pageable);
    Page<OrderEntity> findByStatus(Integer status, Pageable pageable);
    Page<OrderEntity> findByUserIdAndStatus(Long userId, Integer status, Pageable pageable);
    
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    long countByStatus(Integer status);
    long countByUserId(Long userId);
}
