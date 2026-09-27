package com.jyfc.backend.module.marketing.repository;

import com.jyfc.backend.module.marketing.entity.Activity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

import org.springframework.data.jpa.repository.Query;

public interface ActivityRepository extends JpaRepository<Activity, Long> {
    Page<Activity> findByStatus(Integer status, Pageable pageable);
    List<Activity> findByStatus(Integer status);

    @Query("SELECT DISTINCT a FROM Activity a LEFT JOIN FETCH a.products p LEFT JOIN FETCH a.discounts d LEFT JOIN FETCH a.coupons c WHERE a.status = 1")
    List<Activity> findAllActiveWithDetails();
}
