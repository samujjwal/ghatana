package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.launcher.DataCloudLauncherSettings;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * HMAC-SHA256 token generation and validation for destructive action confirmation.
 *
 * <p>Used by purge, redaction, bulk-delete, and PII-export handlers to ensure
 * destructive operations require an explicit two-step confirmation (dry-run
 * preview followed by token-validated execution).
 *
 * <p>Token format: {@code Base64Url( epochMs + "." + HMAC-SHA256(secret, scope+tenant+resource+epochMs) )}
 *
 * @doc.type class
 * @doc.purpose Time-limited confirmation token for destructive actions
 * @doc.layer product
 * @doc.pattern Utility
 */
public final class DestructiveActionToken {

    private DestructiveActionToken() { /* utility */ }

    /** HMAC-SHA256 algorithm identifier. */
    static final String HMAC_ALGORITHM = "HmacSHA256";

    /** Validity window for confirmation tokens (5 minutes). */
    static final long TOKEN_VALIDITY_MS = 5L * 60 * 1000;

    /** Environment variable name for the purge/export token secret. */
    static final String TOKEN_SECRET_ENV = "DATACLOUD_PURGE_TOKEN_SECRET";

    /** Profile environment variable name. */
    static final String PROFILE_ENV = "DATACLOUD_PROFILE";

    /**
     * Ephemeral per-process fallback secret used only in local/embedded-style profiles.
     */
    static final byte[] EPHEMERAL_SECRET =
        UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);

    // ─────────────────────────────────────────────────────────────────────────
    // Generic token builders / validators
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds a scoped HMAC confirmation token.
     *
     * <p>Payload: {@code scope + ":" + tenantId + ":" + collection + ":" + issuedAtMs}
     */
    static String buildToken(String scope, String tenantId, String collection, long issuedAtMs) {
        return buildToken(scope, tenantId, collection, issuedAtMs, runtimeEnvironment());
    }

    static String buildToken(String scope, String tenantId, String collection, long issuedAtMs, Map<String, String> env) {
        String payload = scope + ":" + tenantId + ":" + collection + ":" + issuedAtMs;
        String hmac = hmacSha256Hex(resolveSecret(env), payload);
        String raw = issuedAtMs + "." + hmac;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Builds a scoped HMAC confirmation token including an entity id.
     *
     * <p>Payload: {@code scope + ":" + tenantId + ":" + collection + ":" + entityId + ":" + issuedAtMs}
     */
    static String buildToken(String scope, String tenantId, String collection, String entityId, long issuedAtMs) {
        return buildToken(scope, tenantId, collection, entityId, issuedAtMs, runtimeEnvironment());
    }

    static String buildToken(String scope, String tenantId, String collection, String entityId, long issuedAtMs, Map<String, String> env) {
        String payload = scope + ":" + tenantId + ":" + collection + ":" + entityId + ":" + issuedAtMs;
        String hmac = hmacSha256Hex(resolveSecret(env), payload);
        String raw = issuedAtMs + "." + hmac;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Validates a scoped confirmation token.
     */
    static TokenValidationResult validateToken(String token, String scope, String tenantId, String collection) {
        return validateToken(token, scope, tenantId, collection, null, runtimeEnvironment());
    }

    static TokenValidationResult validateToken(String token, String scope, String tenantId, String collection, Map<String, String> env) {
        return validateToken(token, scope, tenantId, collection, null, env);
    }

    static TokenValidationResult validateToken(String token, String scope, String tenantId, String collection, String entityId) {
        return validateToken(token, scope, tenantId, collection, entityId, runtimeEnvironment());
    }

    static TokenValidationResult validateToken(String token, String scope, String tenantId, String collection, String entityId, Map<String, String> env) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(token);
            String raw = new String(decoded, StandardCharsets.UTF_8);
            int dotIdx = raw.indexOf('.');
            if (dotIdx < 1) {
                return TokenValidationResult.failure("malformed token");
            }
            long issuedAtMs = Long.parseLong(raw.substring(0, dotIdx));
            String providedHmac = raw.substring(dotIdx + 1);

            long ageMs = System.currentTimeMillis() - issuedAtMs;
            if (ageMs > TOKEN_VALIDITY_MS) {
                return TokenValidationResult.failure("token expired (age=" + (ageMs / 1000) + "s, max=300s)");
            }
            if (ageMs < 0) {
                return TokenValidationResult.failure("token issued in the future");
            }

            String payload = entityId == null
                ? scope + ":" + tenantId + ":" + collection + ":" + issuedAtMs
                : scope + ":" + tenantId + ":" + collection + ":" + entityId + ":" + issuedAtMs;
            String expectedHmac = hmacSha256Hex(resolveSecret(env), payload);
            if (!constantTimeEquals(expectedHmac, providedHmac)) {
                return TokenValidationResult.failure("token signature mismatch");
            }
            return TokenValidationResult.success();
        } catch (IllegalArgumentException e) {
            return TokenValidationResult.failure("token decode error: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Secret resolution & environment
    // ─────────────────────────────────────────────────────────────────────────

    static Map<String, String> runtimeEnvironment() {
        Map<String, String> env = new HashMap<>(System.getenv());
        putSystemPropertyOverride(env, PROFILE_ENV);
        putSystemPropertyOverride(env, TOKEN_SECRET_ENV);
        return Map.copyOf(env);
    }

    static byte[] resolveSecret(Map<String, String> env) {
        return resolveConfiguredSecret(env).orElse(EPHEMERAL_SECRET);
    }

    static Optional<byte[]> resolveConfiguredSecret(Map<String, String> env) {
        String configured = env.get(TOKEN_SECRET_ENV);
        if (configured == null || configured.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(configured.getBytes(StandardCharsets.UTF_8));
    }

    static boolean allowsEphemeralSecret(Map<String, String> env) {
        return DataCloudLauncherSettings.isEmbeddedProfile(
            DataCloudLauncherSettings.resolveProfile(new String[0], env));
    }

    static String resolveProfileName(Map<String, String> env) {
        String rawProfile = env.get(PROFILE_ENV);
        return (rawProfile == null || rawProfile.isBlank()) ? "local" : rawProfile;
    }

    static TokenSecretRequirement validateTokenSecretConfiguration(Map<String, String> env) {
        if (resolveConfiguredSecret(env).isPresent()) {
            return TokenSecretRequirement.available(resolveProfileName(env));
        }
        if (allowsEphemeralSecret(env)) {
            return TokenSecretRequirement.available(resolveProfileName(env));
        }
        return TokenSecretRequirement.unavailable(
            resolveProfileName(env),
            TOKEN_SECRET_ENV + " must be configured for destructive operations outside local or sovereign profiles"
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Crypto primitives
    // ─────────────────────────────────────────────────────────────────────────

    static String hmacSha256Hex(byte[] secret, String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            byte[] result = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(result);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** Constant-time string comparison to prevent timing attacks. */
    static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }

    private static void putSystemPropertyOverride(Map<String, String> env, String key) {
        String value = System.getProperty(key);
        if (value != null) {
            env.put(key, value);
        }
    }

    /** Result of a destructive-action token validation. */
    public record TokenValidationResult(boolean valid, String reason) {
        boolean isValid() { return valid; }
        static TokenValidationResult success()              { return new TokenValidationResult(true, null); }
        static TokenValidationResult failure(String reason) { return new TokenValidationResult(false, reason); }
    }

    /** Availability status of the token-secret configuration. */
    public record TokenSecretRequirement(boolean available, String profile, String message) {
        boolean isAvailable() { return available; }
        static TokenSecretRequirement available(String profile) {
            return new TokenSecretRequirement(true, profile, null);
        }
        static TokenSecretRequirement unavailable(String profile, String message) {
            return new TokenSecretRequirement(false, profile, message);
        }
    }
}
