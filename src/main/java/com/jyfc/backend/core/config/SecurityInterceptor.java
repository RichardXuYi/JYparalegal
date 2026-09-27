package com.jyfc.backend.core.config;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 安全拦截器
 * 在请求处理前进行安全检查和信息收集
 */
@Component
public class SecurityInterceptor implements HandlerInterceptor {
    
    private static final Logger log = LoggerFactory.getLogger(SecurityInterceptor.class);
    
    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) throws Exception {
        // 记录请求信息用于审计
        String clientIp = RequestUtils.getClientIpAddress();
        String userAgent = RequestUtils.getUserAgent();
        String method = request.getMethod();
        String uri = request.getRequestURI();
        
        // 设置请求属性，供后续处理使用
        request.setAttribute("clientIp", clientIp);
        request.setAttribute("userAgent", userAgent);
        
        if (log.isDebugEnabled()) {
            log.debug("Request - Method: {}, URI: {}, IP: {}, UserAgent: {}",
                method, uri, clientIp, userAgent);
        }
        
        return true;
    }
    
    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler, @Nullable Exception ex) throws Exception {
        if (ex != null) {
            String clientIp = (String) request.getAttribute("clientIp");
            log.error("Request processing error - IP: {}, Exception: {}", clientIp, ex.getMessage());
        }
    }
}
