package com.jyfc.backend.core.cp;

/**
 * CP 集成共享常量：request attribute 名与 claim 名（与 CP 侧 CpTokenService 对齐）。
 */
public final class CpClaims {

    private CpClaims() {
    }

    /** JwtAuthenticationFilter 校验 CP token 后，把 tenant_id claim 放进 request attribute，供 TenantContextFilter 使用。 */
    public static final String REQ_ATTR_TENANT_ID = "jy.cp.tenantId";
    public static final String REQ_ATTR_CP_USER = "jy.cp.username";

    public static final String CLAIM_TENANT_ID = "tenant_id";
    public static final String CLAIM_PLAN = "plan";
    public static final String CLAIM_ENT = "ent";
    public static final String CLAIM_INSTANCE_ID = "instance_id";
    public static final String CLAIM_USERNAME = "username";
}