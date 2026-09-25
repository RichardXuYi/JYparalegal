package com.jyfc.backend.module.auth.repository;

import com.jyfc.backend.module.auth.entity.UserRefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRefreshTokenRepository extends JpaRepository<UserRefreshTokenEntity, Long> {

    Optional<UserRefreshTokenEntity> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE UserRefreshTokenEntity t SET t.revoked = true WHERE t.userId = :userId AND t.revoked = false")
    int revokeAllByUserId(@Param("userId") Long userId);

    /** 查询某用户所有未吊销的 refresh token（即活跃设备列表）。 */
    List<UserRefreshTokenEntity> findByUserIdAndRevokedFalse(Long userId);

    /** 按记录 ID 吊销指定用户的 refresh token。 */
    @Modifying
    @Query("UPDATE UserRefreshTokenEntity t SET t.revoked = true WHERE t.id = :id AND t.userId = :userId")
    int revokeByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
}
