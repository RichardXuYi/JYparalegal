package com.jyfc.backend.core.tenant;

import com.jyfc.backend.core.cp.CpClaims;
import com.jyfc.backend.module.auth.entity.UserEntity;
import com.jyfc.backend.module.auth.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * 请求级租户上下文（D12，应用层强制）。
 * 在 JWT 过滤之后运行：解析当前用户 tenant_id 写入 {@link JyTenantContext}，请求结束清理。
 * 未认证/管理员/未绑定 tenant → 哨兵根租户 0；法律域写操作由控制器守卫拒绝根租户。
 * 读隔离由仓库的 tenant-scoped 查询保证（见 SignTaskRepository.findByIdAndTenantId 等）。
 *
 * <p><b>D16 追加</b>：若启用 CP 且本次请求由 CP passport 认证，则 tenant 以 **CP 的 tenant_id claim 为权威**
 * （不再回查 DB）——这正是 docs/04 §1「tenant 来自 JWT claim」的落地；CP 关闭时仍走既有 DB 解析。</p>
 */
@Component
@Order(10)
public class TenantContextFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;
    private final ThreadLocal<String> memoUser = new ThreadLocal<>();
    private final ThreadLocal<Long> memoTenant = new ThreadLocal<>();

    public TenantContextFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            JyTenantContext.set(resolve(request));
            chain.doFilter(request, response);
        } finally {
            JyTenantContext.clear();
            memoUser.remove();
            memoTenant.remove();
        }
    }

    private Long resolve(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return JyTenantContext.ROOT_TENANT_ID;
        }
        // D16：CP passport 的 tenant_id claim 为权威
        Object cpTenant = request.getAttribute(CpClaims.REQ_ATTR_TENANT_ID);
        if (cpTenant instanceof Long l && l > 0) {
            return l;
        }
        String username = auth.getName();
        if (username.equals(memoUser.get())) {
            return memoTenant.get();
        }
        Optional<UserEntity> user = userRepository.findByUsername(username);
        Long tenant = user.map(UserEntity::getTenantId).orElse(null);
        Long resolved = tenant != null ? tenant : JyTenantContext.ROOT_TENANT_ID;
        memoUser.set(username);
        memoTenant.set(resolved);
        return resolved;
    }
}
