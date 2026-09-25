package com.jyfc.backend.module.product.repository;

import com.jyfc.backend.module.product.entity.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {
}
