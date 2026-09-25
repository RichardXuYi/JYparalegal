package com.jyfc.backend.module.knowledge.repository;

import com.jyfc.backend.module.knowledge.entity.KnowledgeCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface KnowledgeCategoryRepository extends JpaRepository<KnowledgeCategory, Long> {
    Optional<KnowledgeCategory> findByCode(String code);
}

