package com.jyfc.backend.module.sitecontent.repository;

import com.jyfc.backend.module.sitecontent.entity.SiteContent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SiteContentRepository extends JpaRepository<SiteContent, Long> {
    List<SiteContent> findBySectionAndStatusOrderBySortOrderAsc(String section, Integer status);
    List<SiteContent> findBySectionOrderBySortOrderAsc(String section);
    List<SiteContent> findAllByOrderBySectionAscSortOrderAsc();
    Optional<SiteContent> findBySectionAndContentKey(String section, String contentKey);
}
