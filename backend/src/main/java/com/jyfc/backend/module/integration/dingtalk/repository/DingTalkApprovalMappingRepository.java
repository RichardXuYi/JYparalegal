package com.jyfc.backend.module.integration.dingtalk.repository;

import com.jyfc.backend.module.integration.dingtalk.entity.DingTalkApprovalMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DingTalkApprovalMappingRepository extends JpaRepository<DingTalkApprovalMapping, Long> {
    Optional<DingTalkApprovalMapping> findByDingtalkApprovalId(String dingtalkApprovalId);
    Optional<DingTalkApprovalMapping> findByContractId(Long contractId);
}
