package com.jyfc.cp.config;

import com.jyfc.cp.security.CpSigningAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 注册 CP 的鉴权拦截器。
 * <p>
 * 仅保护 {@code /cp/v1/signing/**}：
 * <ul>
 *   <li>{@code /cp/v1/auth/login} 必须开放——DP 靠它换取服务账号令牌；</li>
 *   <li>{@code /api/esign/callback} 由 e签宝签名验签保护（已改 fail-closed），不走 Bearer。</li>
 * </ul>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final CpSigningAuthInterceptor signingAuthInterceptor;

    public WebConfig(CpSigningAuthInterceptor signingAuthInterceptor) {
        this.signingAuthInterceptor = signingAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(signingAuthInterceptor)
                .addPathPatterns("/cp/v1/signing/**");
    }
}
