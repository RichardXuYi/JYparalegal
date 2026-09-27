package com.jyfc.backend.core.tenant;

import jakarta.persistence.PrePersist;
import org.hibernate.CallbackException;

/**
 * 租户写入监听器（D12，应用层强制）：持久化前若实体未带 tenant_id，从 {@link JyTenantContext} 注入。
 * 通过 reflection 调用 setTenantId，避免为每个实体加接口（S0' 最小实现）。
 */
public class TenantEntityListener {

    @PrePersist
    public void prePersist(Object entity) {
        try {
            var m = entity.getClass().getMethod("getTenantId");
            Object current = m.invoke(entity);
            if (current == null) {
                Long tid = JyTenantContext.get();
                entity.getClass().getMethod("setTenantId", Long.class).invoke(entity, tid);
            }
        } catch (NoSuchMethodException e) {
            // 实体无 tenant 列（平台级表），跳过
        } catch (Exception e) {
            throw new CallbackException("租户注入失败: " + entity.getClass().getSimpleName(), e);
        }
    }
}
