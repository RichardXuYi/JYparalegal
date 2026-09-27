package com.jyfc.backend.module.marketing.repository;

import com.jyfc.backend.module.marketing.entity.Discount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiscountRepository extends JpaRepository<Discount, Long> {
    Page<Discount> findByStatus(Integer status, Pageable pageable);
}
