package com.fishmatrix.common.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fishmatrix.common.autoconfigure.FishAuthProperties;
import com.fishmatrix.common.error.ApiException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

class AuthInterceptorTest {

    private static final String SECRET = "interceptor-secret";
    private static final CurrentUser USER = new CurrentUser(7, "tester", "测试员", "USER",
            List.of("USER"), Set.of("reader.public.upload"));

    private FishAuthProperties properties;
    private IdentitySigner signer;
    private JwtVerifier jwtVerifier;
    private AuthClient authClient;
    private AuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        properties = new FishAuthProperties();
        properties.setIdentitySecret(SECRET);
        signer = new IdentitySigner(SECRET, 30);
        jwtVerifier = new JwtVerifier(properties);
        authClient = mock(AuthClient.class);
        interceptor = new AuthInterceptor(signer, jwtVerifier, authClient, properties);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void acceptsSignedIdentityAndClearsContext() throws Exception {
        MockHttpServletRequest request = signedRequest("reader.public.upload");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), handler("upload")));
        assertEquals(7, UserContext.require().id());

        interceptor.afterCompletion(request, new MockHttpServletResponse(),
                handler("upload"), null);
        assertNull(UserContext.get());
        verifyNoInteractions(authClient);
    }

    @Test
    void rejectsIdentityWithoutRequiredPermission() throws Exception {
        MockHttpServletRequest request = signedRequest("music.private.manage");

        ApiException exception = assertThrows(ApiException.class, () -> interceptor.preHandle(
                request, new MockHttpServletResponse(), handler("upload")));

        assertEquals(HttpStatus.FORBIDDEN, exception.status());
        assertEquals("PERMISSION_DENIED", exception.code());
        UserContext.clear();
    }

    @Test
    void allowsHandlerWithoutPermissionAnnotation() throws Exception {
        MockHttpServletRequest request = signedRequest("music.private.manage");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), handler("open")));
        assertNotNull(UserContext.get());
    }

    @Test
    void bearerFallbackIntrospectsUserCenter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test/items");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer opaque-token");
        when(authClient.introspect("opaque-token")).thenReturn(USER);

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), handler("upload")));
        assertEquals(USER, UserContext.get());
    }

    @Test
    void verifiesJwtLocallyWithoutCallingUserCenter() throws Exception {
        java.security.KeyPair pair = JwtTestSupport.ed25519();
        properties.getJwt().setKeyId("k1");
        properties.getJwt().setPublicKey(JwtTestSupport.publicKey(pair));
        properties.setAllowBearerFallback(false);
        AuthInterceptor jwtInterceptor = new AuthInterceptor(signer, new JwtVerifier(properties),
                authClient, properties);

        java.util.Map<String, Object> claims = new java.util.HashMap<>();
        claims.put("iss", "fishmatrix");
        claims.put("sub", 7);
        claims.put("usr", "tester");
        claims.put("nick", "测试员");
        claims.put("role", "USER");
        claims.put("roles", java.util.List.of("USER"));
        claims.put("perms", java.util.List.of("reader.public.upload"));
        claims.put("exp", Instant.now().getEpochSecond() + 600);
        String token = JwtTestSupport.token(pair.getPrivate(), "k1", claims);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test/items");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);

        assertTrue(jwtInterceptor.preHandle(request, new MockHttpServletResponse(),
                handler("upload")));
        assertEquals(7, UserContext.require().id());
        verifyNoInteractions(authClient);
    }

    @Test
    void rejectsJwtWithBadSignatureEvenWhenFallbackDisabled() throws Exception {
        java.security.KeyPair pair = JwtTestSupport.ed25519();
        properties.getJwt().setKeyId("k1");
        properties.getJwt().setPublicKey(JwtTestSupport.publicKey(pair));
        properties.setAllowBearerFallback(false);
        AuthInterceptor jwtInterceptor = new AuthInterceptor(signer, new JwtVerifier(properties),
                authClient, properties);

        java.util.Map<String, Object> claims = new java.util.HashMap<>();
        claims.put("sub", 7);
        claims.put("exp", Instant.now().getEpochSecond() + 600);
        String forged = JwtTestSupport.token(JwtTestSupport.ed25519().getPrivate(), "k1", claims);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test/items");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + forged);

        ApiException exception = assertThrows(ApiException.class,
                () -> jwtInterceptor.preHandle(request, new MockHttpServletResponse(),
                        handler("upload")));
        assertEquals("INVALID_TOKEN", exception.code());
        verifyNoInteractions(authClient);
    }

    @Test
    void rejectsMissingCredentialsWhenFallbackDisabled() throws Exception {
        properties.setAllowBearerFallback(false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test/items");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer opaque-token");

        ApiException exception = assertThrows(ApiException.class, () -> interceptor.preHandle(
                request, new MockHttpServletResponse(), handler("upload")));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.status());
        verifyNoInteractions(authClient);
    }

    @Test
    void rejectsMissingBearerToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test/items");

        ApiException exception = assertThrows(ApiException.class, () -> interceptor.preHandle(
                request, new MockHttpServletResponse(), handler("upload")));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.status());
    }

    @Test
    void bypassesPublicPaths() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET",
                "/api/v1/reader/health");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), handler("upload")));
        assertNull(UserContext.get());
    }

    private MockHttpServletRequest signedRequest(String... permissions) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test/items");
        long exp = Instant.now().getEpochSecond() + 60;
        List<String> roleList = List.of("USER");
        List<String> permissionList = List.of(permissions);
        String canonical = signer.canonical(7, "tester", "测试员", "USER", roleList,
                permissionList, exp);
        request.addHeader(IdentityHeaders.USER_ID, "7");
        request.addHeader(IdentityHeaders.USERNAME, encode("tester"));
        request.addHeader(IdentityHeaders.NICKNAME, encode("测试员"));
        request.addHeader(IdentityHeaders.ROLE, encode("USER"));
        request.addHeader(IdentityHeaders.ROLES, String.join(",", roleList));
        request.addHeader(IdentityHeaders.PERMISSIONS, String.join(",", permissionList));
        request.addHeader(IdentityHeaders.EXPIRES_AT, Long.toString(exp));
        request.addHeader(IdentityHeaders.SIGNATURE, signer.sign(canonical));
        return request;
    }

    private static String encode(String value) {
        return java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static HandlerMethod handler(String name) throws NoSuchMethodException {
        TestEndpoint endpoint = new TestEndpoint();
        Method method = TestEndpoint.class.getDeclaredMethod(name);
        return new HandlerMethod(endpoint, method);
    }

    static class TestEndpoint {

        @RequirePermission("reader.public.upload")
        public void upload() {
        }

        public void open() {
        }
    }
}
