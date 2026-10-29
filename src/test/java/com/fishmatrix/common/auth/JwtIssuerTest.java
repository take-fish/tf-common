package com.fishmatrix.common.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fishmatrix.common.autoconfigure.FishAuthProperties;
import com.fishmatrix.common.error.ApiException;
import java.security.KeyPair;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class JwtIssuerTest {

    private static final String KID = "k1";

    private KeyPair pair;
    private FishAuthProperties properties;
    private JwtIssuer issuer;
    private JwtVerifier verifier;

    @BeforeEach
    void setUp() {
        pair = JwtTestSupport.ed25519();
        properties = new FishAuthProperties();
        properties.getJwt().setKeyId(KID);
        properties.getJwt().setIssuer("fishmatrix");
        properties.getJwt().setPublicKey(JwtTestSupport.publicKey(pair));
        properties.getJwt().setPrivateKey(JwtTestSupport.privateKey(pair));
        issuer = new JwtIssuer(properties);
        verifier = new JwtVerifier(properties);
    }

    @Test
    void issuesTokenThatVerifierAccepts() {
        CurrentUser user = JwtIssuer.currentUser(7, "tester", "测试员", "USER",
                List.of("USER"), List.of("reader.public.upload"));

        String token = issuer.issue(user);
        CurrentUser parsed = verifier.verify(token);

        assertEquals(7, parsed.id());
        assertEquals("tester", parsed.username());
        assertEquals("测试员", parsed.nickname());
        assertEquals(List.of("USER"), parsed.roles());
        assertTrue(parsed.hasPermission("reader.public.upload"));
        assertEquals(3, token.split("\\.").length);
    }

    @Test
    void carriesKeyIdInHeader() {
        String token = issuer.issue(JwtIssuer.currentUser(1, "a", "b", "USER", List.of("USER"),
                List.of()));
        String header = new String(Base64.getUrlDecoder().decode(token.split("\\.")[0]));

        assertTrue(header.contains("\"kid\":\"" + KID + "\""));
        assertTrue(header.contains("\"alg\":\"EdDSA\""));
    }

    @Test
    void honoursCustomTtl() {
        String token = issuer.issue(JwtIssuer.currentUser(1, "a", "b", "USER", List.of("USER"),
                List.of()), Duration.ofSeconds(90));
        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]));

        long exp = Long.parseLong(payload.replaceAll(".*\"exp\":(\\d+).*", "$1"));
        long iat = Long.parseLong(payload.replaceAll(".*\"iat\":(\\d+).*", "$1"));
        assertEquals(90, exp - iat);
    }

    @Test
    void rejectsSigningWithoutPrivateKey() {
        FishAuthProperties withoutKey = new FishAuthProperties();
        JwtIssuer unconfigured = new JwtIssuer(withoutKey);

        assertFalse(unconfigured.isConfigured());
        ApiException exception = assertThrows(ApiException.class,
                () -> unconfigured.issue(JwtIssuer.currentUser(1, "a", "b", "USER",
                        List.of("USER"), List.of())));

        assertEquals("AUTH_CONFIG_MISSING", exception.code());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.status());
    }

    @Test
    void tokensIssuedWithRotatedKeyAreAcceptedByRotatingVerifier() {
        KeyPair next = JwtTestSupport.ed25519();
        FishAuthProperties rotating = new FishAuthProperties();
        rotating.getJwt().setKeyId("k2");
        rotating.getJwt().setPublicKey(JwtTestSupport.publicKey(pair));
        rotating.getJwt().setKeys(java.util.Map.of("k2", JwtTestSupport.publicKey(next)));
        rotating.getJwt().setPrivateKey(JwtTestSupport.privateKey(next));
        rotating.getJwt().setIssuer("fishmatrix");

        String token = new JwtIssuer(rotating).issue(JwtIssuer.currentUser(9, "u", "n", "USER",
                List.of("USER"), List.of()));

        assertEquals(9, new JwtVerifier(rotating).verify(token).id());
    }
}
