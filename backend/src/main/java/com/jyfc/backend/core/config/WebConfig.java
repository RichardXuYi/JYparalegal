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

@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    @Value("${file.upload.path:./uploads}")
    private String uploadPath;
    
    @Autowired
    private SecurityInterceptor securityInterceptor;
    
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
    }
}
