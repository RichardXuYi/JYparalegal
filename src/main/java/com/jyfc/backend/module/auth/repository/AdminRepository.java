package com.jyfc.backend.module.auth.repository;
import com.jyfc.backend.module.auth.entity.AdminEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
@Repository
public interface AdminRepository extends JpaRepository<AdminEntity, Long> {    Optional<AdminEntity> findByUsername(String username);
    Optional<AdminEntity> findByEmail(String email);
}