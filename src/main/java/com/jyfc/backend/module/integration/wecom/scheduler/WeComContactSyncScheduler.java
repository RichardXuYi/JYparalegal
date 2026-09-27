package com.jyfc.backend.module.integration.wecom.scheduler;

import com.jyfc.backend.module.integration.wecom.config.WeComProperties;
import com.jyfc.backend.module.integration.wecom.service.WeComContactService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 企业微信通讯录同步定时任务
 *
 * 负责定时从企业微信同步组织架构数据。
 * 全量同步：每天凌晨 2 点
 * 增量同步：每小时检查变更
 *
 * 注意：定时任务仅在启用了企业微信集成时生效。
 * 如果配置 integration.wecom.enabled=false，任务会跳过执行。
 */
@Component
public class WeComContactSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(WeComContactSyncScheduler.class);

    private final WeComContactService weComContactService;
    private final WeComProperties weComProperties;

    public WeComContactSyncScheduler(WeComContactService weComContactService,
                                    WeComProperties weComProperties) {
        this.weComContactService = weComContactService;
        this.weComProperties = weComProperties;
    }

    /** 企微集成未启用时所有任务直接跳过，避免抛 IllegalStateException 污染日志 */
    private boolean isDisabled() {
        return !weComProperties.isEnabled();
    }

    /**
     * 每天凌晨 2 点全量同步通讯录
     *
     * 此任务会递归同步企业微信所有部门及其成员。
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void fullSyncContact() {
        if (isDisabled()) {
            log.debug("企业微信集成未启用，跳过全量同步任务");
            return;
        }
        log.info("===== 企业微信通讯录全量同步开始 =====");
        LocalDateTime startTime = LocalDateTime.now();

        try {
            // 同步部门列表
            List<Map<String, Object>> departments = weComContactService.syncDepartmentList();
            log.info("部门同步完成: {} 个部门", departments.size());

            // 遍历所有部门，同步各部门成员
            int totalUsers = 0;
            for (Map<String, Object> dept : departments) {
                Long deptId = Long.valueOf(dept.get("id").toString());
                // 跳过根部门（id=1）
                if (deptId == 1L) continue;

                try {
                    List<Map<String, Object>> users = weComContactService.syncDepartmentUsers(deptId, false);
                    totalUsers += users != null ? users.size() : 0;
                } catch (Exception e) {
                    log.error("同步部门 {} 成员失败: {}", deptId, e.getMessage());
                }
            }

            LocalDateTime endTime = LocalDateTime.now();
            long durationSeconds = java.time.Duration.between(startTime, endTime).getSeconds();
            log.info("===== 企业微信通讯录全量同步完成 (耗时 {} 秒, 共 {} 成员) =====",
                    durationSeconds, totalUsers);

        } catch (Exception e) {
            log.error("企业微信通讯录全量同步失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 每小时增量同步变更
     *
     * 仅同步根部门成员（包含子部门），用于获取变更数据。
     * 实际企业微信需要结合回调通知实现实时变更检测。
     */
    @Scheduled(fixedDelay = 3_600_000) // 1 小时
    public void incrementalSyncContact() {
        if (isDisabled()) {
            log.debug("企业微信集成未启用，跳过增量同步任务");
            return;
        }
        if (!isExecutionTime()) {
            return; // 凌晨 2 点已有全量同步，跳过
        }

        log.info("企业微信通讯录增量同步开始");
        LocalDateTime startTime = LocalDateTime.now();

        try {
            // 增量同步：同步根部门成员（含子部门）
            List<Map<String, Object>> users = weComContactService.syncDepartmentUsers(1L, true);

            LocalDateTime endTime = LocalDateTime.now();
            long durationSeconds = java.time.Duration.between(startTime, endTime).getSeconds();
            log.info("企业微信通讯录增量同步完成 (耗时 {} 秒, 共 {} 成员)",
                    durationSeconds, users != null ? users.size() : 0);

        } catch (Exception e) {
            log.error("企业微信通讯录增量同步失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 判断是否为全量同步的执行时间
     * 如果是凌晨 2:00-2:30，跳过增量同步（避免与全量同步冲突）
     */
    private boolean isExecutionTime() {
        LocalDateTime now = LocalDateTime.now();
        int hour = now.getHour();
        int minute = now.getMinute();
        // 凌晨 2:00 ~ 2:30 跳过增量同步
        return !(hour == 2 && minute < 30);
    }
}
