package com.nutritionagent.common.web;

import com.nutritionagent.common.api.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 为每个请求分配 traceId，写入 MDC（供日志输出）与响应头（供前端报障时提供）。
 *
 * <p>若请求方已带 {@code X-Trace-Id}，则沿用该值，便于跨系统串联；
 * 但会做长度与字符白名单校验，防止把不可信内容注入日志。
 */
@Component
@Order(1)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_HEADER = "X-Trace-Id";
    private static final int MAX_LENGTH = 64;
    private static final java.util.regex.Pattern SAFE = java.util.regex.Pattern.compile("^[A-Za-z0-9_-]+$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(TRACE_HEADER);
        try {
            if (isSafe(incoming)) {
                TraceContext.set(incoming);
            } else {
                TraceContext.begin();
            }
            response.setHeader(TRACE_HEADER, TraceContext.currentTraceId());
            chain.doFilter(request, response);
        } finally {
            // 必须清理，否则线程复用时会串号
            TraceContext.clear();
        }
    }

    private boolean isSafe(String value) {
        return value != null && !value.isBlank() && value.length() <= MAX_LENGTH && SAFE.matcher(value).matches();
    }
}
