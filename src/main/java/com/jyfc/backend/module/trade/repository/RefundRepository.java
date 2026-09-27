package com.jyfc.backend.module.trade.repository;

import com.jyfc.backend.module.trade.entity.Refund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RefundRepository extends JpaRepository<Refund, Long> {

    Optional<Refund> findByRefundNo(String refundNo);

    List<Refund> findByOrderIdOrderByCreatedAtDesc(Long orderId);

    List<Refund> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Refund> findByStatusOrderByCreatedAtDesc(Integer status);

    long countByOrderIdAndStatusIn(Long orderId, List<Integer> statuses);
}