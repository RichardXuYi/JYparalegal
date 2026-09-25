package com.jyfc.backend.module.sign.repository;

import com.jyfc.backend.module.sign.entity.SignCcEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SignCcRepository extends JpaRepository<SignCcEntity, Long> {
    List<SignCcEntity> findByUserId(Long userId);
}
