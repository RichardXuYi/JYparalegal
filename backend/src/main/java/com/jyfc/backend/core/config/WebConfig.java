package com.jyfc.backend.core.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.lang.NonNull;
import java.nio.file.Paths;
import java.nio.file.Path;

import com.jyfc.backend.core.security.EntitlementInterceptor;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    @Value("${file.upload.path:./uploads}")
    private String uploadPath;
    
    @Autowired
    private SecurityInterceptor securityInterceptor;

    @Autowired
    private EntitlementInterceptor entitlementInterceptor;
    
    /**
     * 配置静态资源访问路径
     */
    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        // 安全处理可能为 null 的 uploadPath
        String path = uploadPath != null ? uploadPath : "./uploads";
        Path normalizedPath = Paths.get(path).toAbsolutePath().normalize();
        registry.addResourceHandler("/uploads/**").addResourceLocations("file:" + normalizedPath.toString() + "/");
    }
    
    /**
     * 配置拦截器
     */
    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(securityInterceptor).addPathPatterns("/api/**").excludePathPatterns("/api/auth/login", "/api/auth/register", "/api/auth/send-sms-code");
        // 阶段2 套餐门禁：法律域接口按租户 plan 拦截，FREE → 402。豁免 esign 回调（公网 webhook）。
        // /internal/tools 是智能体侧入口，把建签署任务/审查/草稿/抽取/知识库检索又暴露了一遍；
        // 只挂 /api/** 会让免费用户绕开套餐门禁。/api/knowledge 同理（与 /internal/tools/kb 同一能力）。
        // 注：/api/user/skills 是用户级技能同步存储，不属付费法务能力，不纳入门禁。
        registry.addInterceptor(entitlementInterceptor)
                .addPathPatterns(
                        "/api/sign/**",
                        "/api/evidence/**", "/api/templates/**", "/api/review/**",
                        "/api/knowledge/**", "/internal/tools/**")
                .excludePathPatterns("/api/sign/callbacks/**");
    }
}
