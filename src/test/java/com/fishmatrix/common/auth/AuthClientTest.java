package com.fishmatrix.common.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fishmatrix.common.error.ApiException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AuthClientTest {

    private static final String BASE = "http://user-test";
    private static final String TOKEN = "opaque-token";
    private static final String IDENTITY_JSON = """
            {"id":7,"username":"tester","nickname":"测试员","role":"USER",
             "roles":["USER"],"permissions":["reader.public.upload"]}
            """;

    private MockRestServiceServer server;
    private AuthClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AuthClient(builder, BASE, Duration.ofSeconds(30), 16);
    }

    @Test
    void introspectsAndCachesSuccessfulIdentity() {
        server.expect(requestTo(BASE + "/api/v1/user/auth/me"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andRespond(withSuccess(IDENTITY_JSON, MediaType.APPLICATION_JSON));

        CurrentUser first = client.introspect(TOKEN);
        CurrentUser second = client.introspect(TOKEN);
        server.verify();

        assertEquals(7, first.id());
        assertEquals("u7", first.userId());
        assertEquals("测试员", first.nickname());
        assertEquals(first, second);
        assertEquals(first, client.cached(TOKEN).orElseThrow());
    }

    @Test
    void evictForcesFreshIntrospection() {
        server.expect(requestTo(BASE + "/api/v1/user/auth/me"))
                .andRespond(withSuccess(IDENTITY_JSON, MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/api/v1/user/auth/me"))
                .andRespond(withSuccess(IDENTITY_JSON, MediaType.APPLICATION_JSON));

        client.introspect(TOKEN);
        client.evict(TOKEN);
        assertEquals(true, client.cached(TOKEN).isEmpty());
        client.introspect(TOKEN);
        server.verify();
    }

    @Test
    void mapsRejectedTokenToUnauthorized() {
        server.expect(requestTo(BASE + "/api/v1/user/auth/me"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body(""));

        ApiException exception = assertThrows(ApiException.class, () -> client.introspect(TOKEN));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.status());
        assertEquals("UNAUTHORIZED", exception.code());
    }

    @Test
    void treatsServerErrorAsUnavailableAndDoesNotCache() {
        server.expect(requestTo(BASE + "/api/v1/user/auth/me"))
                .andRespond(withServerError());

        ApiException exception = assertThrows(ApiException.class, () -> client.introspect(TOKEN));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.status());
        assertEquals("AUTH_UNAVAILABLE", exception.code());
        assertEquals(true, client.cached(TOKEN).isEmpty());
    }

    @Test
    void rejectsBlankTokenWithoutCallingUserCenter() {
        ApiException exception = assertThrows(ApiException.class, () -> client.introspect("  "));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.status());
        server.verify();
    }
}
