package com.nutritionagent.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 令牌子过滤器：解析 {@code Authorization: Bearer <token>}，把调用者写入
 * {@link UserContext}，请求结束时无条件清除。
 *
 * <p>三条刻意的行为：
 * <ol>
 *   <li><b>令牌无效不报错</b>。这里只负责"能认出就认出"，是否允许匿名由各接口
 *       自己决定（公开接口匿名可访问，需要登录的接口在 {@code requireLoggedIn()}
 *       处抛 40101）。过滤器里抛异常会让公开接口也被令牌问题连带失败。</li>
 *   <li><b>绝不记录令牌原文</b>（PRD 8.4 限制日志记录）。异常只记类型。</li>
 *   <li><b>finally 里 clear</b>，防止线程池复用把身份带给下一个请求。</li>
 * </ol>
 *
 * <p>{@code @Order(0)} 让它排在 {@code TraceIdFilter}(order=1) 之前：进入时先于
 * traceId 生成器，退出时晚于它清除 MDC，因此异常日志仍带 traceId。
 */
@Component
@Order(0)
public class AuthTokenFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthTokenFilter.class);

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final AuthTokenService authTokenService;

    public AuthTokenFilter(AuthTokenService authTokenService) {
        this.authTokenService = authTokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String raw = extractToken(request);
            if (raw != null) {
                try {
                    UserPrincipal principal = authTokenService.resolve(raw);
                    if (principal != null) {
                        UserContext.set(principal);
                    }
                } catch (RuntimeException ex) {
                    // 数据库不可用等情况：降级为游客，绝不因为令牌查询失败而拒绝公开接口
                    log.warn("解析登录令牌失败，本次请求按未登录处理: {}", ex.getClass().getSimpleName());
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header == null || !header.startsWith(PREFIX)) {
            return null;
        }
        String token = header.substring(PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
