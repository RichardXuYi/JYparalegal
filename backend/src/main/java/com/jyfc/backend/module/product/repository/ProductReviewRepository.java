package com.jyfc.backend.module.product.repository;

import com.jyfc.backend.module.product.entity.ProductReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductReviewRepository extends JpaRepository<ProductReview, Long> {
    
    // 根据产品ID查找评论
    List<ProductReview> findByProductId(Long productId);
    
    // 根据用户ID查找评论
    List<ProductReview> findByUserId(Long userId);

    // 根据产品ID分页查找评论
    Page<ProductReview> findByProductId(Long productId, Pageable pageable);

    // 统计产品的评论数量
    long countByProductId(Long productId);

    // 可选：搜索评论
    @Query("SELECT r FROM ProductReview r WHERE " +
           "(:productId IS NULL OR r.productId = :productId) AND " +
           "(:rating IS NULL OR r.rating = :rating) AND " +
           "(:hasReply IS NULL OR (:hasReply = true AND r.reply IS NOT NULL) OR (:hasReply = false AND r.reply IS NULL))")
    Page<ProductReview> findAllWithFilters(
            @Param("productId") Long productId, 
            @Param("rating") Integer rating, 
            @Param("hasReply") Boolean hasReply, 
            Pageable pageable
    );
}
