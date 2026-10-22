package com.fishmatrix.common.trace;

import org.slf4j.MDC;

/**
 * 读取当前线程的链路标识。Micrometer 写入的 MDC 键为 traceId/spanId，
 * 本库同时维护 snake_case 的 trace_id/span_id/request_id，作为日志字段的统一名称。
 */
public final class TraceIds {

    public static final String MDC_TRACE_ID = "trace_id";
    public static final String MDC_SPAN_ID = "span_id";
    public static final String MDC_REQUEST_ID = "request_id";

    private TraceIds() {
    }

    public static String currentTraceId() {
        return firstNonBlank(MDC.get(MDC_TRACE_ID), MDC.get("traceId"));
    }

    public static String currentSpanId() {
        return firstNonBlank(MDC.get(MDC_SPAN_ID), MDC.get("spanId"));
    }

    public static String currentRequestId() {
        return firstNonBlank(MDC.get(MDC_REQUEST_ID), currentTraceId());
    }

    /**
     * 保证当前线程一定有 trace_id。仅在过滤器之外的极端场景使用，
     * 正常情况下 TraceContextFilter 已经写入。
     */
    public static String ensureTraceId() {
        String traceId = currentTraceId();
        if (traceId == null) {
            traceId = TraceHeaders.newTraceId();
            MDC.put(MDC_TRACE_ID, traceId);
            MDC.put(MDC_REQUEST_ID, traceId);
        }
        return traceId;
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second == null || second.isBlank() ? null : second;
    }
}
