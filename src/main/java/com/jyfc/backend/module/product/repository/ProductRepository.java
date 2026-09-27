package com.jyfc.backend.module.product.repository;

import com.jyfc.backend.module.product.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
    Page<Product> findByStatus(Integer status, Pageable pageable);
    Page<Product> findByCategoryId(Long categoryId, Pageable pageable);
    Page<Product> findByNameContaining(String name, Pageable pageable);
    Page<Product> findByProductType(String productType, Pageable pageable);
    Page<Product> findByStatusAndProductType(Integer status, String productType, Pageable pageable);

    boolean existsByName(String name);
    long countByStatus(Integer status);
    long countByCategoryId(Long categoryId);
}
