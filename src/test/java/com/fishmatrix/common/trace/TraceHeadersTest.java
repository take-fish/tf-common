package com.fishmatrix.common.trace;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TraceHeadersTest {

    @Test
    void acceptsOnlyHexRequestIds() {
        assertThat(TraceHeaders.isValidRequestId("4bf92f3577b34da6a3ce929d0e0e4736")).isTrue();
        assertThat(TraceHeaders.isValidRequestId("not-a-trace-id")).isFalse();
        assertThat(TraceHeaders.isValidRequestId("<script>alert(1)</script>")).isFalse();
        assertThat(TraceHeaders.isValidRequestId(null)).isFalse();
    }

    @Test
    void parsesW3cTraceparent() {
        String traceparent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

        assertThat(TraceHeaders.parseTraceId(traceparent))
                .isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(TraceHeaders.parseTraceId("00-" + "0".repeat(32) + "-00f067aa0ba902b7-01")).isNull();
        assertThat(TraceHeaders.parseTraceId("b3-123")).isNull();
    }

    @Test
    void formatsTraceparentWithSamplingFlag() {
        assertThat(TraceHeaders.formatTraceparent(
                        "4bf92f3577b34da6a3ce929d0e0e4736", "00f067aa0ba902b7", true))
                .isEqualTo("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
    }
}
