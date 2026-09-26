package com.jyfc.backend.module.integration.wecom.repository;

import com.jyfc.backend.module.integration.wecom.entity.WeComApprovalMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WeComApprovalMappingRepository extends JpaRepository<WeComApprovalMapping, Long> {

    Optional<WeComApprovalMapping> findByWecomSpNo(String wecomSpNo);

    List<WeComApprovalMapping> findByContractId(Long contractId);

    Optional<WeComApprovalMapping> findByContractIdAndWecomSpNo(Long contractId, String wecomSpNo);
}
