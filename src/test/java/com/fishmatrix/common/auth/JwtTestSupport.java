package com.fishmatrix.common.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

/** JWT 相关测试的签名工具：生成 Ed25519 密钥对并签发测试令牌。 */
final class JwtTestSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JwtTestSupport() {
    }

    static KeyPair ed25519() {
        try {
            return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    /** 取 X.509 编码尾部的 32 字节裸公钥，base64url 无填充。 */
    static String publicKey(KeyPair pair) {
        byte[] spki = pair.getPublic().getEncoded();
        byte[] raw = Arrays.copyOfRange(spki, spki.length - 32, spki.length);
        return b64(raw);
    }

    static String token(PrivateKey key, String kid, Map<String, Object> claims) {
        try {
            String header = b64(MAPPER.writeValueAsBytes(Map.of(
                    "alg", "EdDSA", "typ", "JWT", "kid", kid)));
            String payload = b64(MAPPER.writeValueAsBytes(claims));
            Signature signature = Signature.getInstance("Ed25519");
            signature.initSign(key);
            signature.update((header + "." + payload).getBytes(StandardCharsets.US_ASCII));
            return header + "." + payload + "." + b64(signature.sign());
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    /** 构造未签名的令牌（alg=none），用于验证降级攻击会被拒绝。 */
    static String unsignedToken(Map<String, Object> claims) {
        try {
            String header = b64(MAPPER.writeValueAsBytes(Map.of("alg", "none", "typ", "JWT")));
            String payload = b64(MAPPER.writeValueAsBytes(claims));
            return header + "." + payload + ".";
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    static String b64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
