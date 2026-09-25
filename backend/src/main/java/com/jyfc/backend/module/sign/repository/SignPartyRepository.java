package com.jyfc.backend.module.sign.repository;

import com.jyfc.backend.module.sign.entity.SignPartyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SignPartyRepository extends JpaRepository<SignPartyEntity, Long> {
    List<SignPartyEntity> findByTaskIdOrderByIdAsc(Long taskId);

    Optional<SignPartyEntity> findByIdAndTenantId(Long id, Long tenantId);

    /** 待我处理：我是参与方且待签署。 */
    List<SignPartyEntity> findByUserIdAndPartyStatusIn(Long userId, java.util.Collection<String> statuses);

    List<SignPartyEntity> findByUserId(Long userId);

    void deleteByTaskId(Long taskId);
}
