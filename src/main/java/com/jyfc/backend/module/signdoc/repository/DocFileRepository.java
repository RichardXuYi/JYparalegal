package com.jyfc.backend.module.signdoc.repository;

import com.jyfc.backend.module.signdoc.entity.DocFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/** 物理文件仓库（V132 doc_file）：上传落盘切片仅用 save/findById。 */
public interface DocFileRepository extends JpaRepository<DocFileEntity, Long> {
}
