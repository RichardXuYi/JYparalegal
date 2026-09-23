package com.jyfc.backend.module.marketing.repository;

import com.jyfc.backend.module.marketing.entity.Coupon;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponRepository extends JpaRepository<Coupon, Long> {
    Page<Coupon> findByStatus(Integer status, Pageable pageable);
    boolean existsByCode(String code);
}
