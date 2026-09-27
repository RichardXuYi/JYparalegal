package com.jyfc.backend.module.version.repository;

import com.jyfc.backend.module.version.entity.AppVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AppVersionRepository extends JpaRepository<AppVersionEntity, Long> {

    Optional<AppVersionEntity> findByClientType(AppVersionEntity.ClientType clientType);
}
