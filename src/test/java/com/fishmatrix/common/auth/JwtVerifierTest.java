package com.fishmatrix.common.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fishmatrix.common.autoconfigure.FishAuthProperties;
import com.fishmatrix.common.error.ApiException;
import java.security.KeyPair;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class JwtVerifierTest {

    private static final String KID = "k1";

    private KeyPair pair;
    private FishAuthProperties properties;
    private JwtVerifier verifier;

    @BeforeEach
    void setUp() {
        pair = JwtTestSupport.ed25519();
        properties = new FishAuthProperties();
        properties.getJwt().setKeyId(KID);
        properties.getJwt().setIssuer("fishmatrix");
        properties.getJwt().setPublicKey(JwtTestSupport.publicKey(pair));
        verifier = new JwtVerifier(properties);
    }

    @Test
    void verifiesValidToken() {
        CurrentUser user = verifier.verify(JwtTestSupport.token(pair.getPrivate(), KID, claims()));

        assertEquals(7, user.id());
        assertEquals("tester", user.username());
        assertEquals("测试员", user.nickname());
        assertEquals("USER", user.role());
        assertEquals(List.of("USER"), user.roles());
        assertTrue(user.hasPermission("reader.public.upload"));
        assertFalse(user.isAdmin());
    }

    @Test
    void acceptsRolesAndPermissionsAsSpaceSeparatedString() {
        Map<String, Object> claims = claims();
        claims.put("roles", "USER ADMIN");
        claims.put("perms", "reader.public.upload music.private.manage");

        CurrentUser user = verifier.verify(JwtTestSupport.token(pair.getPrivate(), KID, claims));

        assertEquals(List.of("USER", "ADMIN"), user.roles());
        assertTrue(user.isAdmin());
        assertEquals(2, user.permissions().size());
    }

    @Test
    void rejectsExpiredToken() {
        Map<String, Object> claims = claims();
        claims.put("exp", Instant.now().getEpochSecond() - 600);

        ApiException exception = assertThrows(ApiException.class, () -> verifier.verify(
                JwtTestSupport.token(pair.getPrivate(), KID, claims)));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.status());
        assertEquals("TOKEN_EXPIRED", exception.code());
    }

    @Test
    void rejectsTamperedPayload() {
        String token = JwtTestSupport.token(pair.getPrivate(), KID, claims());
        String[] parts = token.split("\\.");
        String forgedPayload = JwtTestSupport.b64(
                "{\"sub\":1,\"role\":\"ADMIN\",\"exp\":9999999999}".getBytes(
                        java.nio.charset.StandardCharsets.UTF_8));
        String forged = parts[0] + "." + forgedPayload + "." + parts[2];

        ApiException exception = assertThrows(ApiException.class, () -> verifier.verify(forged));
        assertEquals("INVALID_TOKEN", exception.code());
    }

    @Test
    void rejectsUnsignedToken() {
        ApiException exception = assertThrows(ApiException.class,
                () -> verifier.verify(JwtTestSupport.unsignedToken(claims())));

        assertEquals("INVALID_TOKEN", exception.code());
    }

    @Test
    void rejectsUnknownKeyId() {
        String token = JwtTestSupport.token(pair.getPrivate(), "unknown", claims());

        ApiException exception = assertThrows(ApiException.class, () -> verifier.verify(token));
        assertEquals("INVALID_TOKEN", exception.code());
    }

    @Test
    void rejectsWrongIssuer() {
        Map<String, Object> claims = claims();
        claims.put("iss", "someone-else");

        ApiException exception = assertThrows(ApiException.class, () -> verifier.verify(
                JwtTestSupport.token(pair.getPrivate(), KID, claims)));

        assertEquals("INVALID_TOKEN", exception.code());
    }

    @Test
    void rejectsMissingExpiry() {
        Map<String, Object> claims = claims();
        claims.remove("exp");

        ApiException exception = assertThrows(ApiException.class, () -> verifier.verify(
                JwtTestSupport.token(pair.getPrivate(), KID, claims)));

        assertEquals("INVALID_TOKEN", exception.code());
    }

    @Test
    void rejectsMalformedToken() {
        assertThrows(ApiException.class, () -> verifier.verify("not-a-jwt"));
        assertThrows(ApiException.class, () -> verifier.verify("a.b"));
    }

    @Test
    void supportsKeyRotationWithOldAndNewKeys() {
        KeyPair next = JwtTestSupport.ed25519();
        properties.getJwt().setKeys(Map.of("k2", JwtTestSupport.publicKey(next)));
        JwtVerifier rotating = new JwtVerifier(properties);

        assertEquals(7, rotating.verify(
                JwtTestSupport.token(pair.getPrivate(), KID, claims())).id());
        assertEquals(7, rotating.verify(
                JwtTestSupport.token(next.getPrivate(), "k2", claims())).id());
        assertEquals(List.of(KID, "k2"), rotating.keyIds());
    }

    @Test
    void reportsMissingConfiguration() {
        FishAuthProperties empty = new FishAuthProperties();
        JwtVerifier unconfigured = new JwtVerifier(empty);

        assertFalse(unconfigured.isConfigured());
        ApiException exception = assertThrows(ApiException.class,
                () -> unconfigured.verify(JwtTestSupport.token(pair.getPrivate(), KID, claims())));
        assertEquals("AUTH_CONFIG_MISSING", exception.code());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.status());
    }

    @Test
    void detectsJwtShape() {
        assertTrue(JwtVerifier.looksLikeJwt("a.b.c"));
        assertFalse(JwtVerifier.looksLikeJwt("opaque-token"));
        assertFalse(JwtVerifier.looksLikeJwt("a.b.c.d"));
        assertFalse(JwtVerifier.looksLikeJwt(null));
    }

    private static Map<String, Object> claims() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("iss", "fishmatrix");
        claims.put("sub", 7);
        claims.put("usr", "tester");
        claims.put("nick", "测试员");
        claims.put("role", "USER");
        claims.put("roles", List.of("USER"));
        claims.put("perms", List.of("reader.public.upload"));
        claims.put("iat", Instant.now().getEpochSecond());
        claims.put("exp", Instant.now().getEpochSecond() + 600);
        return claims;
    }
}
