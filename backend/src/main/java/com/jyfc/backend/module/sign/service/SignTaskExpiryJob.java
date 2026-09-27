package com.jyfc.backend.module.sign.service;

import com.jyfc.backend.module.sign.entity.SignTaskEntity;
import com.jyfc.backend.module.sign.repository.SignTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 签署任务过期收敛（SIGNING + 截止时间已过 → EXPIRED）。
 *
 * <p>补上 EXPIRED 的入边：此前该状态只作为 EXTEND 的源、无任何入边，导致
 * {@code /extend} 永远抛 ILLEGAL_TRANSITION（审阅 Q22）。过期收敛后：
 * 发起方可延期（EXPIRED → SIGNING）；不延期则任务停在 EXPIRED 等待人工处理。
 *
 * <p>多实例部署下靠 sign_task 的 @Version 乐观锁去重，失败方仅记日志。
 */
@Component
public class SignTaskExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(SignTaskExpiryJob.class);

    private final SignTaskRepository taskRepository;
    private final SignTaskStateMachine stateMachine;

    public SignTaskExpiryJob(SignTaskRepository taskRepository, SignTaskStateMachine stateMachine) {
        this.taskRepository = taskRepository;
        this.stateMachine = stateMachine;
    }

    @Scheduled(fixedDelayString = "${jy.sign.expiry-scan-ms:600000}", initialDelayString = "${jy.sign.expiry-scan-initial-ms:60000}")
    public void expireOverdueTasks() {
        List<SignTaskEntity> overdue =
                taskRepository.findAllByStatusAndExpireAtBefore("SIGNING", LocalDateTime.now());
        if (overdue.isEmpty()) return;
        int expired = 0;
        for (SignTaskEntity t : overdue) {
            try {
                stateMachine.transfer(t.getId(), "EXPIRE", "SYSTEM", null, "deadline passed");
                expired++;
            } catch (Exception e) {
                // 并发实例已抢先转移（乐观锁）或状态已变：跳过即可
                log.warn("任务过期转移跳过 taskId={}: {}", t.getId(), e.getMessage());
            }
        }
        log.info("过期收敛完成：扫描 {} 条，转移 {} 条", overdue.size(), expired);
    }
}
