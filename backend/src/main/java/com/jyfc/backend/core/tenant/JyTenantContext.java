package com.jyfc.backend.core.tenant;

/**
 * 当前请求的租户上下文（ThreadLocal）。
 * 由 {@link JyTenantResolver} 在解析 Hibernate 租户标识时写入/读取。
 * D12：tenant 来自 users.tenant_id（JWT/SecurityContext 解析），不信任请求头裸传。
 */
public final class JyTenantContext {

    /** 哨兵根租户：未认证 / 平台级 actor（admin）/ 未绑定 tenant 的用户。法律域写操作必须拒绝根租户。 */
    public static final Long ROOT_TENANT_ID = 0L;

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private JyTenantContext() {
    }

    public static void set(Long tenantId) {
        CURRENT.set(tenantId);
    }

    public static Long get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
