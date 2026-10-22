package com.fishmatrix.common.trace;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class TracesTest {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @AfterEach
    void shutdown() throws Exception {
        executor.shutdownNow();
        executor.awaitTermination(2, TimeUnit.SECONDS);
        MDC.clear();
    }

    @Test
    void wrapsTaskWithTraceAndMdc() throws Exception {
        MDC.put(TraceIds.MDC_TRACE_ID, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        MDC.put(TraceIds.MDC_REQUEST_ID, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");

        Future<String> future = executor.submit(Traces.wrap(
                () -> Thread.currentThread().getName() + ":" + MDC.get(TraceIds.MDC_TRACE_ID)));

        assertThat(future.get(2, TimeUnit.SECONDS)).endsWith(":aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        assertThat(MDC.get(TraceIds.MDC_TRACE_ID)).isEqualTo("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    }
}
