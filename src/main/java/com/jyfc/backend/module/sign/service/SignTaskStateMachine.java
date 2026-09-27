package com.jyfc.backend.module.sign.service;

import com.jyfc.backend.core.exception.BusinessException;
import com.jyfc.backend.module.audit.entity.AuditLogEntity;
import com.jyfc.backend.module.audit.repository.AuditLogRepository;
import com.jyfc.backend.module.sign.entity.SignTaskEntity;
import com.jyfc.backend.module.sign.entity.TaskEventEntity;
import com.jyfc.backend.module.sign.repository.SignTaskRepository;
import com.jyfc.backend.module.sign.repository.TaskEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 签署状态机唯一转移入口（继承旧 TD §3.4；转移表唯一权威 = 旧版 prd/10 §5.2）。
 * 每次转移【同事务】写 task_event + audit_log，并依赖 @Version 乐观锁（D5）。
 * 非法转移抛 BusinessException（映射 409 语义 ILLEGAL_TRANSITION）。
 * 创建提交（docs/11）：SUBMIT 仍是 DRAFT→CREATED（配额事件口径不变），
 * 紧接着 OPEN_FILL / OPEN_FINALIZE / START 进入填写、定稿或签署。
 * FILLING→定稿或签署、FINALIZING→签署 由完成填写 / 确认定稿驱动。
 * VOIDING / VOIDED 仍不在本切片。
 */
@Service
public class SignTaskStateMachine {

    private static final Map<String, Map<String, String>> EDGES;

    static {
        Map<String, Map<String, String>> edges = new HashMap<>();
        edges.put("SUBMIT", Map.of("DRAFT", "CREATED"));
        edges.put("OPEN_FILL", Map.of("CREATED", "FILLING"));
        edges.put("OPEN_FINALIZE", Map.of("CREATED", "FINALIZING"));
        edges.put("START", Map.of("CREATED", "SIGNING"));
        edges.put("FINISH_FILL_TO_FINAL", Map.of("FILLING", "FINALIZING"));
        edges.put("FINISH_FILL_TO_SIGN", Map.of("FILLING", "SIGNING"));
        edges.put("CONFIRM_FINAL", Map.of("FINALIZING", "SIGNING"));
        edges.put("COMPLETE", Map.of("SIGNING", "COMPLETED"));
        edges.put("TERMINATE", Map.of("SIGNING", "TERMINATED"));
        edges.put("REJECT", Map.of("SIGNING", "REJECTED"));
        edges.put("AUTO_TERMINATE", Map.of("REJECTED", "TERMINATED"));
        edges.put("EXPIRE", Map.of("SIGNING", "EXPIRED"));
        edges.put("EXTEND", Map.of("EXPIRED", "SIGNING"));
        edges.put("REVOKE", Map.of(
                "CREATED", "REVOKED",
                "FILLING", "REVOKED",
                "FINALIZING", "REVOKED",
                "SIGNING", "REVOKED"));
        edges.put("START_VOID", Map.of("COMPLETED", "VOIDING"));
        edges.put("FINISH_VOID", Map.of("VOIDING", "VOIDED"));
        edges.put("ABORT_VOID", Map.of("VOIDING", "COMPLETED"));
        EDGES = Map.copyOf(edges);
    }

    private final SignTaskRepository taskRepository;
    private final TaskEventRepository taskEventRepository;
    private final AuditLogRepository auditLogRepository;

    public SignTaskStateMachine(SignTaskRepository taskRepository,
                                TaskEventRepository taskEventRepository,
                                AuditLogRepository auditLogRepository) {
        this.taskRepository = taskRepository;
        this.taskEventRepository = taskEventRepository;
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * 内部转移原语：**不做授权**，调用方必须先完成授权（对外入口见
     * SignFlowService.authorizeTransition / transition / sign / reject / acceptInvite / revoke / extend）。
     * 按 id 加载以支持跨企业参与方推进（参与方授权依据 party/invite 链接，而非租户）。
     */
    @Transactional
    public SignTaskEntity transfer(Long taskId, String trigger, String actorType, Long actorId, String reason) {
        SignTaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException("任务不存在: " + taskId));
        assertTransferable(task.getStatus(), trigger);

        String from = task.getStatus();
        String to = EDGES.get(trigger).get(from);
        task.setStatus(to);
        taskRepository.save(task); // @Version 乐观锁；冲突抛 OptimisticLockingFailure → 409

        TaskEventEventWriter.write(taskEventRepository, task, from, to, trigger, actorType, actorId, reason);
        AuditWriter.write(auditLogRepository, task, actorType, actorId, "SIGN_TASK_TRANSITION:" + trigger);
        return task;
    }

    /** 只校验不转移：供调用方在消耗外部资源（如 CP 送签配额）前预检，避免非法转移白烧配额。 */
    public void assertTransferable(String currentStatus, String trigger) {
        Map<String, String> edge = EDGES.get(trigger);
        if (edge == null || !edge.containsKey(currentStatus)) {
            throw new BusinessException("ILLEGAL_TRANSITION: " + currentStatus + " -[" + trigger + "]->");
        }
    }

    /** 内部：写 task_event（同事务）。 */
    private static final class TaskEventEventWriter {
        static void write(TaskEventRepository repo, SignTaskEntity task, String from, String to,
                          String trigger, String actorType, Long actorId, String reason) {
            TaskEventEntity e = new TaskEventEntity();
            // 租户取自任务本身：系统级转移（定时过期）无请求上下文，不能依赖 listener 注入
            e.setTenantId(task.getTenantId());
            e.setTaskId(task.getId());
            e.setFromStatus(from);
            e.setToStatus(to);
            e.setTriggerType(triggerTypeOf(trigger));
            e.setActorType(actorType);
            e.setActorId(actorId);
            e.setReason(reason);
            repo.save(e);
        }

        private static String triggerTypeOf(String trigger) {
            return Set.of("EXPIRE", "AUTO_COMPLETE").contains(trigger) ? "SYSTEM" : "USER";
        }
    }

    /** 内部：写 audit_log（同事务）。 */
    private static final class AuditWriter {
        static void write(AuditLogRepository repo, SignTaskEntity task, String actorType, Long actorId, String action) {
            AuditLogEntity a = new AuditLogEntity();
            a.setTenantId(task.getTenantId());
            a.setCompanyId(task.getCompanyId());
            a.setActorType(actorType);
            a.setActorId(actorId);
            a.setAction(action);
            a.setEntityType("sign_task");
            a.setEntityId(task.getId());
            a.setResult("SUCCESS");
            repo.save(a);
        }
    }
}
