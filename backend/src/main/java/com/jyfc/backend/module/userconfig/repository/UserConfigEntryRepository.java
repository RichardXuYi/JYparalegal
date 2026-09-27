package com.jyfc.backend.module.userconfig.repository;

import com.jyfc.backend.module.userconfig.entity.UserConfigEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserConfigEntryRepository extends JpaRepository<UserConfigEntryEntity, Long> {

    List<UserConfigEntryEntity> findByUserId(Long userId);

    List<UserConfigEntryEntity> findByUserIdAndNamespace(Long userId, String namespace);

    Optional<UserConfigEntryEntity> findByUserIdAndNamespaceAndConfigKey(Long userId, String namespace, String configKey);

    void deleteByUserIdAndNamespaceAndConfigKey(Long userId, String namespace, String configKey);
}
