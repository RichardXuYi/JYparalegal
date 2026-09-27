package com.jyfc.backend.module.mall.repository;

import com.jyfc.backend.module.mall.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CartRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findByUserIdOrderByAddedAtDesc(Long userId);

    List<CartItem> findByUserIdAndSelectedOrderByAddedAtDesc(Long userId, Boolean selected);

    Optional<CartItem> findByUserIdAndProductIdAndSkuId(Long userId, Long productId, Long skuId);

    long countByUserId(Long userId);

    long deleteByUserIdAndId(Long userId, Long id);

    long deleteByUserIdAndProductIdAndSkuId(Long userId, Long productId, Long skuId);
}