package com.jyfc.backend.module.sign.repository;

import com.jyfc.backend.module.sign.entity.SignInviteEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SignInviteRepository extends JpaRepository<SignInviteEntity, Long> {
    /** 待我接收（view=PENDING_RECEIVE，prd10 §4.2）。 */
    List<SignInviteEntity> findByTargetUserIdAndInviteStatus(Long targetUserId, String inviteStatus);

    Optional<SignInviteEntity> findByIdAndTenantId(Long id, Long tenantId);

    List<SignInviteEntity> findByTaskId(Long taskId);

    void deleteByTaskId(Long taskId);
}
