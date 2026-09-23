package com.jyfc.backend.module.account.service;

import com.jyfc.backend.core.exception.BusinessException;
import com.jyfc.backend.module.account.entity.TenantQuotaEntity;
import com.jyfc.backend.module.account.repository.TenantQuotaRepository;
import com.jyfc.backend.module.sign.repository.TaskEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 签署配额（DP 本地口径；{@code jy.cp.mode=local} 时的强制点）。
 *
 * <p>口径唯一定义：{@code quota = tenant_quota.sign_quota}；{@code used = 本自然月到达 CREATED 的任务数}
 * （按 {@code task_event.to_status='CREATED'} 计数，与状态机 SUBMIT 同源）。
 * **界面展示与送签拦截都走本服务**，避免"显示一个数、拦截另一个数"（审阅 Q5.3）。
 *
 * <p>诚实标注：本地配额是 honor-system，拦误用不拦改代码的机器主人（docs/08 §1 威胁②）。
 */
@Service
public class SignQuotaService {

    private final TenantQuotaRepository quotaRepository;
    private final TaskEventRepository taskEventRepository;

    public SignQuotaService(TenantQuotaRepository quotaRepository, TaskEventRepository taskEventRepository) {
        this.quotaRepository = quotaRepository;
        this.taskEventRepository = taskEventRepository;
    }

    /** 配额行（缺失时按默认 upsert：PRO / 11000）。 */
    @Transactional
    public TenantQuotaEntity quotaOf(Long tenantId) {
        return quotaRepository.findByTenantId(tenantId).orElseGet(() -> {
            TenantQuotaEntity n = new TenantQuotaEntity();
            n.setTenantId(tenantId);
            n.setPlan("PRO");
            n.setSignQuota(11000);
            n.setAiQuotaTokens(0L);
            n.setAiUsedTokens(0L);
            return quotaRepository.save(n);
        });
    }

    /** 本自然月已送签份数。 */
    public long usedThisMonth(Long tenantId) {
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        return taskEventRepository.countByTenantIdAndToStatusAndCreatedAtGreaterThanEqual(
                tenantId, "CREATED", monthStart);
    }

    /** 送签前的本地强制：用尽即拒（不再有"默认无限签"）。 */
    public void assertCanSign(Long tenantId) {
        int quota = quotaOf(tenantId).getSignQuota();
        long used = usedThisMonth(tenantId);
        if (used >= quota) {
            throw new BusinessException("本月签署配额已用尽（" + used + "/" + quota
                    + "）：请升级套餐或等待下月重置");
        }
    }

    /** 总览口径（与 {@link #assertCanSign} 同源）。 */
    @Transactional
    public Map<String, Object> snapshot(Long tenantId) {
        TenantQuotaEntity q = quotaOf(tenantId);
        long used = usedThisMonth(tenantId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("plan", q.getPlan());
        out.put("signQuota", q.getSignQuota());
        out.put("signUsed", used);
        out.put("signRemaining", q.getSignQuota() - used);
        out.put("signPeriod", YearMonth.now().toString());
        out.put("aiQuotaTokens", q.getAiQuotaTokens());
        out.put("aiUsedTokens", q.getAiUsedTokens());
        out.put("billingRule", "PER_DOC");
        return out;
    }
}
