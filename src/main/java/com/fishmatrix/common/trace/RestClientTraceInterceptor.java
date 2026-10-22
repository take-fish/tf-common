package com.fishmatrix.common.trace;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import java.io.IOException;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * 服务间调用必须透传同一条链路。使用当前 Span 的上下文生成 W3C traceparent，
 * 同时把 trace-id 写入 X-Request-ID，便于在日志中直接检索。
 */
public class RestClientTraceInterceptor implements ClientHttpRequestInterceptor {

    private final Tracer tracer;

    public RestClientTraceInterceptor(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        String traceId = null;
        String spanId = null;
        boolean sampled = true;

        Span span = tracer == null ? null : tracer.currentSpan();
        if (span != null && span.context() != null) {
            TraceContext context = span.context();
            traceId = context.traceId();
            spanId = context.spanId();
            sampled = context.sampled();
        }
        if (traceId == null) {
            traceId = TraceIds.currentTraceId();
        }
        if (traceId != null) {
            if (spanId != null) {
                request.getHeaders().set(TraceHeaders.TRACEPARENT,
                        TraceHeaders.formatTraceparent(traceId, spanId, sampled));
            }
            request.getHeaders().set(TraceHeaders.REQUEST_ID, traceId);
        }
        return execution.execute(request, body);
    }
}
