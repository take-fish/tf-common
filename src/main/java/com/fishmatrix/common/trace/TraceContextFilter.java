package com.fishmatrix.common.trace;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.fishmatrix.common.autoconfigure.FishCommonProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 统一链路上下文：
 * <ol>
 *   <li>沿用 Micrometer 已提取的 traceId（来自网关的 traceparent）；</li>
 *   <li>把 traceId 写入 MDC 的 trace_id，日志编码器只输出这一组固定字段；</li>
 *   <li>校验并回写 X-Request-ID / X-Trace-ID 响应头；</li>
 *   <li>为 API 与 WebSocket 握手输出一条结构化访问日志。</li>
 * </ol>
 */
public class TraceContextFilter extends OncePerRequestFilter {

    /** 必须晚于 Spring Boot 的 ServerHttpObservationFilter（HIGHEST_PRECEDENCE + 1）。 */
    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 2;

    private static final Logger accessLog = LoggerFactory.getLogger("com.fishmatrix.http.AccessLog");
    private static final int MAX_PATH_LENGTH = 512;

    private final FishCommonProperties properties;

    public TraceContextFilter(FishCommonProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Map<String, String> previousMdc = MDC.getCopyOfContextMap();
        String traceId = TraceIds.currentTraceId();
        if (traceId == null) {
            traceId = TraceHeaders.parseTraceId(request.getHeader(TraceHeaders.TRACEPARENT));
        }
        if (traceId == null) {
            traceId = TraceHeaders.newTraceId();
        }
        String requestIdHeader = TraceHeaders.normalizeRequestId(request.getHeader(TraceHeaders.REQUEST_ID));
        String requestId = TraceHeaders.isValidRequestId(requestIdHeader) ? requestIdHeader : traceId;
        String spanId = TraceIds.currentSpanId();

        MDC.put(TraceIds.MDC_TRACE_ID, traceId);
        if (spanId != null) {
            MDC.put(TraceIds.MDC_SPAN_ID, spanId);
        }
        MDC.put(TraceIds.MDC_REQUEST_ID, requestId);

        setHeaderIfAbsent(response, TraceHeaders.TRACE_ID, traceId);
        setHeaderIfAbsent(response, TraceHeaders.REQUEST_ID, requestId);
        response.setHeader("Access-Control-Expose-Headers", TraceHeaders.REQUEST_ID + ", " + TraceHeaders.TRACE_ID);

        long startedAt = System.nanoTime();
        String path = safePath(request);
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (properties.getLog().isAccessLogEnabled() && shouldLog(path)) {
                accessLog.info(
                        "HTTP 请求完成",
                        kv("event", "http.access"),
                        kv("http_method", request.getMethod()),
                        kv("http_path", path),
                        kv("http_status", response.getStatus()),
                        kv("duration_ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)));
            }
            restoreMdc(previousMdc);
        }
    }

    private static void setHeaderIfAbsent(HttpServletResponse response, String name, String value) {
        if (!response.containsHeader(name)) {
            response.setHeader(name, value);
        }
    }

    private static String safePath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return "/";
        }
        // 只保留路径，绝不记录查询串，避免 WebSocket ticket 等敏感参数进入日志。
        return uri.length() > MAX_PATH_LENGTH ? uri.substring(0, MAX_PATH_LENGTH) : uri;
    }

    private static boolean shouldLog(String path) {
        if (path.startsWith("/actuator/") || path.equals("/health") || path.equals("/healthz")
                || path.equals("/readyz") || path.startsWith("/assets/") || path.equals("/favicon.ico")) {
            return false;
        }
        return path.startsWith("/api/") || path.startsWith("/ws/");
    }

    private static void restoreMdc(Map<String, String> previousMdc) {
        if (previousMdc == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(previousMdc);
        }
    }
}
