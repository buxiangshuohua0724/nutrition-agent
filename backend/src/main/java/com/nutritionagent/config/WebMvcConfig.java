package com.nutritionagent.config;

import com.nutritionagent.common.security.RoleInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 层装配：注册角色拦截器。
 *
 * <p>刻意**不做**全局路径前缀改写：契约里的路径（{@code /api/v1/...}）由
 * {@code application.yml} 的 {@code spring.mvc.servlet.path} 统一提供，
 * 这样 Controller 上的映射与 {@code docs/api-contract.md} 的路径逐字一致，
 * 便于机械比对。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final RoleInterceptor roleInterceptor;

    public WebMvcConfig(RoleInterceptor roleInterceptor) {
        this.roleInterceptor = roleInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 拦截全部请求：是否校验由 @RequireRole 是否存在决定，避免维护一份易漂移的路径清单
        registry.addInterceptor(roleInterceptor).addPathPatterns("/**");
    }
}
