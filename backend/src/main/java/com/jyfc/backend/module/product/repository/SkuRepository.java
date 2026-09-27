package com.jyfc.backend.module.product.repository;

import com.jyfc.backend.module.product.entity.Sku;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SkuRepository extends JpaRepository<Sku, Long> {
    List<Sku> findByProductId(Long productId);
    boolean existsBySkuCode(String skuCode);

    /**
     * F1-2: 原子扣减库存（防止超卖）。
     * 仅当当前库存 >= qty 时才扣减；返回受影响行数，0 表示库存不足。
     * 必须在事务中调用；该操作绕开 Hibernate 一级缓存，调用方需自行重新加载实体。
     */
    @Modifying
    @Query("UPDATE Sku s SET s.stock = s.stock - :qty WHERE s.id = :id AND s.stock >= :qty")
    int deductStockAtomic(@Param("id") Long id, @Param("qty") Integer qty);

    /**
     * F1-4: 原子恢复库存（取消/退款时使用）。
     */
    @Modifying
    @Query("UPDATE Sku s SET s.stock = s.stock + :qty WHERE s.id = :id")
    int restoreStockAtomic(@Param("id") Long id, @Param("qty") Integer qty);
}