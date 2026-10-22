package com.fishmatrix.common.trace;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 全链路统一的传输头约定：
 * <ul>
 *   <li>{@code traceparent}：W3C Trace Context，Java 侧由 Micrometer 自动提取/传播；</li>
 *   <li>{@code X-Request-ID}：与 trace-id 相同的 32 位十六进制串，供人肉检索；</li>
 *   <li>{@code X-Trace-ID}：响应头，便于前端在错误提示中展示。</li>
 * </ul>
 */
public final class TraceHeaders {

    public static final String TRACEPARENT = "traceparent";
    public static final String TRACESTATE = "tracestate";
    public static final String REQUEST_ID = "X-Request-ID";
    public static final String TRACE_ID = "X-Trace-ID";

    private static final Pattern REQUEST_ID_PATTERN = Pattern.compile("^[0-9a-fA-F]{16,64}$");
    private static final Pattern TRACEPARENT_PATTERN =
            Pattern.compile("^00-([0-9a-f]{32})-([0-9a-f]{16})-([0-9a-f]{2})$");
    private static final String ZERO_TRACE_ID = "0".repeat(32);

    private TraceHeaders() {
    }

    /** 只接受十六进制，避免日志注入和 Grafana/Loki 查询被污染。 */
    public static boolean isValidRequestId(String value) {
        return value != null && REQUEST_ID_PATTERN.matcher(value).matches();
    }

    public static String normalizeRequestId(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    public static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** 从 traceparent 中提取 trace-id；格式不合法时返回 null。 */
    public static String parseTraceId(String traceparent) {
        if (traceparent == null) {
            return null;
        }
        Matcher matcher = TRACEPARENT_PATTERN.matcher(traceparent.trim().toLowerCase(Locale.ROOT));
        if (!matcher.matches()) {
            return null;
        }
        String traceId = matcher.group(1);
        return ZERO_TRACE_ID.equals(traceId) ? null : traceId;
    }

    public static String formatTraceparent(String traceId, String spanId, boolean sampled) {
        String normalizedTraceId = traceId == null || traceId.isBlank() ? newTraceId() : traceId;
        String normalizedSpanId = spanId == null || spanId.isBlank() ? "0".repeat(16) : spanId;
        return "00-" + normalizedTraceId + "-" + normalizedSpanId + "-" + (sampled ? "01" : "00");
    }
}
