package com.jyfc.backend.module.trade.repository;

import com.jyfc.backend.module.trade.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByOrderId(Long orderId);
    void deleteByOrderId(Long orderId);
}
