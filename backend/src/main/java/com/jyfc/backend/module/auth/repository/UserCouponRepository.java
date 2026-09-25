package com.jyfc.backend.module.auth.repository;

import com.jyfc.backend.module.auth.entity.UserCoupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserCouponRepository extends JpaRepository<UserCoupon, Long> {
    List<UserCoupon> findByUserId(Long userId);
    List<UserCoupon> findByUserIdAndStatus(Long userId, Integer status);
}
