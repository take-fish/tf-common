package com.fishmatrix.common.trace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

class RestClientTraceInterceptorTest {

    @Test
    void injectsCurrentSpanIntoOutgoingRequest() throws Exception {
        Tracer tracer = mock(Tracer.class);
        Span span = mock(Span.class);
        TraceContext context = mock(TraceContext.class);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(context);
        when(context.traceId()).thenReturn("4bf92f3577b34da6a3ce929d0e0e4736");
        when(context.spanId()).thenReturn("00f067aa0ba902b7");
        when(context.sampled()).thenReturn(true);

        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET, "/api/v1/user/auth/me");
        ClientHttpRequestExecution execution = (req, body) -> new MockClientHttpResponse(new byte[0], 200);

        new RestClientTraceInterceptor(tracer).intercept(request, new byte[0], execution);

        assertThat(request.getHeaders().getFirst(TraceHeaders.TRACEPARENT))
                .isEqualTo("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        assertThat(request.getHeaders().getFirst(TraceHeaders.REQUEST_ID))
                .isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
    }
}
