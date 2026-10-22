package com.fishmatrix.common.trace;

import io.micrometer.context.ContextSnapshot;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import java.util.Map;
import java.util.concurrent.Callable;
import org.slf4j.MDC;

/**
 * 手工 span 与跨线程上下文工具。WebSocket 每帧、异步任务使用同一 trace-id，
 * 保证按一个 ID 就能检索到完整过程。
 */
public final class Traces {

    private Traces() {
    }

    public static SpanScope startSpan(Tracer tracer, String name) {
        return startSpan(tracer, null, name);
    }

    public static SpanScope startSpan(Tracer tracer, TraceContext parent, String name) {
        Span.Builder builder = tracer.spanBuilder().name(name);
        if (parent != null) {
            builder.setParent(parent);
        }
        Span span = builder.start();
        Tracer.SpanInScope scope = tracer.withSpan(span);
        Map<String, String> previousMdc = MDC.getCopyOfContextMap();
        MDC.put(TraceIds.MDC_TRACE_ID, span.context().traceId());
        MDC.put(TraceIds.MDC_SPAN_ID, span.context().spanId());
        MDC.put(TraceIds.MDC_REQUEST_ID, span.context().traceId());
        return new SpanScope(span, scope, previousMdc);
    }

    /** 捕获当前链路与 MDC，交给线程池执行。 */
    public static Runnable wrap(Runnable task) {
        ContextSnapshot snapshot = ContextSnapshot.captureAll();
        Map<String, String> capturedMdc = MDC.getCopyOfContextMap();
        return () -> {
            try (ContextSnapshot.Scope contextScope = snapshot.setThreadLocals()) {
                Map<String, String> previousMdc = MDC.getCopyOfContextMap();
                setMdc(capturedMdc);
                try {
                    task.run();
                } finally {
                    setMdc(previousMdc);
                }
            }
        };
    }

    public static <T> Callable<T> wrap(Callable<T> task) {
        ContextSnapshot snapshot = ContextSnapshot.captureAll();
        Map<String, String> capturedMdc = MDC.getCopyOfContextMap();
        return () -> {
            try (ContextSnapshot.Scope contextScope = snapshot.setThreadLocals()) {
                Map<String, String> previousMdc = MDC.getCopyOfContextMap();
                setMdc(capturedMdc);
                try {
                    return task.call();
                } finally {
                    setMdc(previousMdc);
                }
            }
        };
    }

    private static void setMdc(Map<String, String> values) {
        if (values == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(values);
        }
    }

    /** 作用域关闭时恢复 MDC 并结束 span。 */
    public static final class SpanScope implements AutoCloseable {

        private final Span span;
        private final Tracer.SpanInScope scope;
        private final Map<String, String> previousMdc;

        private SpanScope(Span span, Tracer.SpanInScope scope, Map<String, String> previousMdc) {
            this.span = span;
            this.scope = scope;
            this.previousMdc = previousMdc;
        }

        public Span span() {
            return span;
        }

        @Override
        public void close() {
            try {
                if (scope != null) {
                    scope.close();
                }
            } finally {
                setMdc(previousMdc);
                span.end();
            }
        }
    }
}
