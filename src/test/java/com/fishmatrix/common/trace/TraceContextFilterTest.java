package com.fishmatrix.common.trace;

import static org.assertj.core.api.Assertions.assertThat;

import com.fishmatrix.common.autoconfigure.FishCommonProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceContextFilterTest {

    private final TraceContextFilter filter = new TraceContextFilter(new FishCommonProperties());

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void reusesMicrometerTraceIdAndReturnsHeaders() throws Exception {
        String traceId = "4bf92f3577b34da6a3ce929d0e0e4736";
        MDC.put("traceId", traceId);
        MDC.put("spanId", "00f067aa0ba902b7");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/user/meta");
        request.addHeader(TraceHeaders.REQUEST_ID, traceId);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            assertThat(MDC.get(TraceIds.MDC_TRACE_ID)).isEqualTo(traceId);
            assertThat(MDC.get(TraceIds.MDC_SPAN_ID)).isEqualTo("00f067aa0ba902b7");
            assertThat(MDC.get(TraceIds.MDC_REQUEST_ID)).isEqualTo(traceId);
        });

        assertThat(response.getHeader(TraceHeaders.TRACE_ID)).isEqualTo(traceId);
        assertThat(response.getHeader(TraceHeaders.REQUEST_ID)).isEqualTo(traceId);
        assertThat(MDC.get(TraceIds.MDC_TRACE_ID)).isNull();
    }

    @Test
    void ignoresUnsafeRequestIdHeader() throws Exception {
        String traceId = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        MDC.put("traceId", traceId);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/user/meta");
        request.addHeader(TraceHeaders.REQUEST_ID, "bad id</script>");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        assertThat(response.getHeader(TraceHeaders.REQUEST_ID)).isEqualTo(traceId);
    }

    @Test
    void generatesTraceWhenNothingIsProvided() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/user/meta");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        String traceId = response.getHeader(TraceHeaders.TRACE_ID);
        assertThat(traceId).matches("[0-9a-f]{32}");
        assertThat(response.getHeader(TraceHeaders.REQUEST_ID)).isEqualTo(traceId);
    }
}
