package com.fishmatrix.common.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fishmatrix.common.error.ApiException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class IdentitySignerTest {

    private static final String SECRET = "test-identity-secret";
    private final IdentitySigner signer = new IdentitySigner(SECRET, 30);

    @Test
    void verifiesSignedIdentity() {
        long exp = Instant.now().getEpochSecond() + 60;
        List<String> roles = List.of("USER");
        List<String> permissions = List.of("reader.public.upload", "music.private.manage");
        String canonical = signer.canonical(7, "小明", "小 明|昵称", "USER", roles, permissions, exp);
        MockHttpServletRequest request = request(canonical, signer.sign(canonical), exp);

        CurrentUser user = signer.verify(request);

        assertEquals(7, user.id());
        assertEquals("u7", user.userId());
        assertEquals("小明", user.username());
        assertEquals("小 明|昵称", user.nickname());
        assertTrue(user.hasPermission("reader.public.upload"));
        assertEquals(2, user.permissions().size());
    }

    @Test
    void rejectsTamperedNickname() {
        long exp = Instant.now().getEpochSecond() + 60;
        String canonical = signer.canonical(7, "小明", "原昵称", "USER", List.of("USER"),
                List.of(), exp);
        MockHttpServletRequest request = request(canonical, signer.sign(canonical), exp);
        request.removeHeader(IdentityHeaders.NICKNAME);
        request.addHeader(IdentityHeaders.NICKNAME, b64("篡改昵称"));

        ApiException exception = assertThrows(ApiException.class, () -> signer.verify(request));

        assertEquals("INVALID_IDENTITY", exception.code());
    }

    @Test
    void rejectsExpiredIdentity() {
        long exp = Instant.now().getEpochSecond() - 120;
        String canonical = signer.canonical(7, "小明", "小明", "USER", List.of("USER"),
                List.of(), exp);
        MockHttpServletRequest request = request(canonical, signer.sign(canonical), exp);

        ApiException exception = assertThrows(ApiException.class, () -> signer.verify(request));

        assertEquals("IDENTITY_EXPIRED", exception.code());
    }

    @Test
    void matchesSharedCrossLanguageSignatureVector() {
        // 与 tf-gateway auth_test.go 共用同一向量，防止两端规范串漂移。
        String canonical = signer.canonical(7, "小明", "小 明|昵称", "USER", List.of("USER"),
                List.of("reader.public.upload", "music.private.manage"), 2000000000L);

        assertEquals("7|5bCP5piO|5bCPIOaYjnzmmLXnp7A|VVNFUg|USER|"
                + "reader.public.upload,music.private.manage|2000000000", canonical);
        assertEquals("bQuQX5sq2E1D8T4lGCmktRCtRvJgtcicp6i4iRfUHPY", signer.sign(canonical));
    }

    @Test
    void requiresSecretToVerify() {
        IdentitySigner unconfigured = new IdentitySigner("", 30);
        long exp = Instant.now().getEpochSecond() + 60;
        String canonical = unconfigured.canonical(1, "a", "a", "USER", List.of("USER"),
                List.of(), exp);
        MockHttpServletRequest request = request(canonical, "signature", exp);

        ApiException exception = assertThrows(ApiException.class, () -> unconfigured.verify(request));

        assertEquals("AUTH_CONFIG_MISSING", exception.code());
    }

    private static MockHttpServletRequest request(String canonical, String signature, long exp) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        String[] parts = canonical.split("\\|", -1);
        request.addHeader(IdentityHeaders.USER_ID, parts[0]);
        request.addHeader(IdentityHeaders.USERNAME, parts[1]);
        request.addHeader(IdentityHeaders.NICKNAME, parts[2]);
        request.addHeader(IdentityHeaders.ROLE, parts[3]);
        request.addHeader(IdentityHeaders.ROLES, parts[4]);
        request.addHeader(IdentityHeaders.PERMISSIONS, parts[5]);
        request.addHeader(IdentityHeaders.EXPIRES_AT, Long.toString(exp));
        request.addHeader(IdentityHeaders.SIGNATURE, signature);
        return request;
    }

    private static String b64(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
