package com.jyfc.backend.module.sign.repository;

import com.jyfc.backend.module.sign.entity.SignProviderEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SignProviderEventRepository extends JpaRepository<SignProviderEventEntity, Long> {
    boolean existsByEventKey(String eventKey);
}
