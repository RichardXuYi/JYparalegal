package com.jyfc.backend.module.product.repository;

import com.jyfc.backend.module.product.entity.ProductAttribute;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProductAttributeRepository extends JpaRepository<ProductAttribute, Long> {
    List<ProductAttribute> findByProductId(Long productId);
    void deleteByProductId(Long productId);
}
