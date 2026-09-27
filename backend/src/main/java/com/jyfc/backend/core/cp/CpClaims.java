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

    /**
     * 令牌用途（与 CP 的 {@code CpAuthController.CLAIM_TOKEN_USE} 对齐）。
     * <p>DP 只接受 {@code user} 身份票。{@code service} 票代表 CP 的服务账号，
     * 用途是 DP→CP 方向的 chokepoint 调用；若 DP 反过来拿它当用户身份，持有
     * CP 服务账号凭证的一方就等于拿到了 DP 全部 {@code authenticated()} 端点、
     * 且租户被钉在票上的 tenant_id。因此必须拒。</p>
     */
    public static final String CLAIM_TOKEN_USE = "token_use";
    public static final String TOKEN_USE_SERVICE = "service";
}