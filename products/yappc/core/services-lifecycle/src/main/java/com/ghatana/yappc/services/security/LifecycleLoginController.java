/*
 * Copyright (c) 2026 Ghatana Technologies
 */
package com.ghatana.yappc.services.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.security.port.JwtTokenProvider;
import io.activej.bytebuf.ByteBuf;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Handles {@code POST /api/auth/login} and {@code POST /api/auth/logout} in the lifecycle
 * service, completing the frontend authentication flow alongside {@link JwtAuthController}.
 *
 * <p><b>Credential store</b></p>
 * <p>Users are loaded from the {@code YAPPC_AUTH_USERS} environment variable at startup.
 * The value must be a JSON array of user objects:
 * <pre>{@code
 * [
 *   {
 *     "id": "u1",
 *     "email": "admin@yappc.io",
 *     "passwordHash": "<PBKDF2 base64>",
 *     "salt": "<base64 salt>",
 *     "name": "Admin",
 *     "roles": ["admin", "user"],
 *     "tenantId": "default-tenant"
 *   }
 * ]
 * }</pre>
 *
 * <p>If the env var is absent a single dev-only user is bootstrapped automatically with
 * email {@code dev@yappc.io} and password {@code change-me-in-production}.
 *
 * <p><b>Password hashing</b></p>
 * <p>Passwords are verified using PBKDF2WithHmacSHA256 (65 536 iterations, 256-bit key).
 * Stored hashes must be created with the same parameters via
 * {@link #hashPassword(String, byte[])}.
 *
 * @doc.type class
 * @doc.purpose Login and logout auth endpoints for YAPPC lifecycle consumers
 * @doc.layer product
 * @doc.pattern Controller
 */
public final class LifecycleLoginController {

    private static final Logger logger = LoggerFactory.getLogger(LifecycleLoginController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 65_536;
    private static final int KEY_LENGTH_BITS = 256;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final JwtTokenProvider tokenProvider;
    private final List<UserRecord> users;

    public LifecycleLoginController(JwtTokenProvider tokenProvider, List<UserRecord> users) {
        this.tokenProvider = tokenProvider;
        this.users = Collections.unmodifiableList(users);
    }

    // -------------------------------------------------------------------------
    // Public endpoint handlers
    // -------------------------------------------------------------------------

    /**
     * {@code POST /api/auth/login}
     *
     * <p>Accepts {@code {"email": "...", "password": "..."}} JSON. Returns a JWT and user
     * details on success, or 401 on bad credentials.
     */
    public Promise<HttpResponse> login(HttpRequest request) {
        Map<?, ?> body;
        try {
            ByteBuf rawBody = request.getBody();
            if (rawBody == null || rawBody.readRemaining() == 0) {
                return Promise.of(error(400, "BAD_REQUEST", "Request body is required"));
            }
            body = MAPPER.readValue(rawBody.getString(StandardCharsets.UTF_8), Map.class);
        } catch (Exception e) {
            return Promise.of(error(400, "BAD_REQUEST", "Invalid JSON body"));
        }

        String email = asString(body.get("email"));
        String password = asString(body.get("password"));

        if (email == null || email.isBlank()) {
            return Promise.of(error(400, "BAD_REQUEST", "email is required"));
        }
        if (password == null || password.isEmpty()) {
            return Promise.of(error(400, "BAD_REQUEST", "password is required"));
        }

        Optional<UserRecord> match = users.stream()
                .filter(u -> email.equalsIgnoreCase(u.email))
                .findFirst();

        if (match.isEmpty() || !verifyPassword(password, match.get().passwordHash, match.get().salt)) {
            logger.warn("Failed login attempt for email={}", email);
            return Promise.of(error(401, "UNAUTHORIZED", "Invalid credentials"));
        }

        UserRecord user = match.get();
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("name", user.name);
        extraClaims.put("email", user.email);
        extraClaims.put("tenantId", user.tenantId);

        String token = tokenProvider.createToken(user.id, user.roles, extraClaims);
        logger.info("Successful login for userId={} tenantId={}", user.id, user.tenantId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("token", token);
        Map<String, Object> userPayload = new LinkedHashMap<>();
        userPayload.put("id", user.id);
        userPayload.put("name", user.name);
        userPayload.put("email", user.email);
        userPayload.put("roles", user.roles);
        userPayload.put("tenantId", user.tenantId);
        response.put("user", userPayload);

        return Promise.of(json(200, response));
    }

    /**
     * {@code POST /api/auth/logout}
     *
     * <p>JWTs are stateless; logout is handled client-side by discarding the token.
     * This endpoint exists for protocol completeness (e.g. to trigger server-side audit
     * logging) and always returns 200.
     */
    public Promise<HttpResponse> logout(HttpRequest request) {
        // Extract user id for audit visibility if a valid token was sent
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).strip();
            tokenProvider.getUserIdFromToken(token).ifPresent(uid ->
                    logger.info("Logout recorded for userId={}", uid));
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("message", "Logged out successfully");
        return Promise.of(json(200, payload));
    }

    // -------------------------------------------------------------------------
    // Static factory helpers
    // -------------------------------------------------------------------------

    /**
     * Builds a {@link LifecycleLoginController} by loading users from the
     * {@code YAPPC_AUTH_USERS} environment variable (JSON array).
     *
     * <p>When the env var is absent the controller is seeded with a single
     * dev-only account ({@code dev@yappc.io} / {@code change-me-in-production})
     * so the service is not start-blocked in development environments.
     */
    @SuppressWarnings("unchecked")
    public static LifecycleLoginController fromEnvironment(JwtTokenProvider tokenProvider) {
        String raw = System.getenv("YAPPC_AUTH_USERS");
        List<UserRecord> users;

        if (raw == null || raw.isBlank()) {
            logger.warn("YAPPC_AUTH_USERS env var not set — bootstrapping dev-only user. " +
                    "DO NOT use this in production.");
            users = bootstrapDevUser();
        } else {
            try {
                List<Map<String, Object>> parsed = MAPPER.readValue(raw, List.class);
                users = new ArrayList<>();
                for (Map<String, Object> entry : parsed) {
                    users.add(UserRecord.fromMap(entry));
                }
                logger.info("Loaded {} user(s) from YAPPC_AUTH_USERS", users.size());
            } catch (Exception e) {
                logger.error("Failed to parse YAPPC_AUTH_USERS — falling back to dev user", e);
                users = bootstrapDevUser();
            }
        }

        return new LifecycleLoginController(tokenProvider, users);
    }

    /**
     * Hashes a password using PBKDF2WithHmacSHA256 with the supplied salt.
     * Useful for generating credential entries for {@code YAPPC_AUTH_USERS}.
     */
    public static String hashPassword(String password, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(
                    password.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS);
            SecretKeyFactory skf = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
            byte[] hash = skf.generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("PBKDF2WithHmacSHA256 unavailable", e);
        }
    }

    /** Generates a new cryptographically-random 16-byte salt. */
    public static byte[] generateSalt() {
        byte[] salt = new byte[16];
        SECURE_RANDOM.nextBytes(salt);
        return salt;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private static boolean verifyPassword(String password, String storedHash, String base64Salt) {
        try {
            byte[] salt = Base64.getDecoder().decode(base64Salt);
            String computed = hashPassword(password, salt);
            // Constant-time comparison to prevent timing attacks
            return constantTimeEquals(computed, storedHash);
        } catch (Exception e) {
            logger.warn("Password verification failed", e);
            return false;
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    private static List<UserRecord> bootstrapDevUser() {
        byte[] salt = generateSalt();
        String hash = hashPassword("change-me-in-production", salt);
        UserRecord dev = new UserRecord(
                "dev-user-1",
                "dev@yappc.io",
                hash,
                Base64.getEncoder().encodeToString(salt),
                "YAPPC Dev",
                List.of("admin", "user"),
                "default-tenant");
        return List.of(dev);
    }

    private static String asString(Object value) {
        return value instanceof String ? (String) value : null;
    }

    private static HttpResponse error(int code, String errorCode, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("error", errorCode);
        payload.put("message", message);
        return json(code, payload);
    }

    private static HttpResponse json(int code, Map<String, Object> payload) {
        try {
            return HttpResponse.ofCode(code)
                    .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .withBody(MAPPER.writeValueAsBytes(payload))
                    .build();
        } catch (Exception e) {
            logger.error("Failed to serialize login response", e);
            return HttpResponse.ofCode(500)
                    .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .withJson("{\"error\":\"INTERNAL_SERVER_ERROR\",\"message\":\"Serialization failure\"}")
                    .build();
        }
    }

    // -------------------------------------------------------------------------
    // Value type
    // -------------------------------------------------------------------------

    /**
     * Immutable user record loaded from the credential store.
     */
    public record UserRecord(
            String id,
            String email,
            String passwordHash,
            String salt,
            String name,
            List<String> roles,
            String tenantId) {

        @SuppressWarnings("unchecked")
        static UserRecord fromMap(Map<String, Object> m) {
            Object rolesRaw = m.get("roles");
            List<String> roles = rolesRaw instanceof List<?>
                    ? ((List<?>) rolesRaw).stream()
                            .filter(String.class::isInstance)
                            .map(String.class::cast)
                            .toList()
                    : List.of("user");

            return new UserRecord(
                    requireString(m, "id"),
                    requireString(m, "email"),
                    requireString(m, "passwordHash"),
                    requireString(m, "salt"),
                    (String) m.getOrDefault("name", requireString(m, "email")),
                    roles,
                    (String) m.getOrDefault("tenantId", "default-tenant"));
        }

        private static String requireString(Map<String, Object> m, String key) {
            Object v = m.get(key);
            if (!(v instanceof String s) || s.isBlank()) {
                throw new IllegalArgumentException("User record missing required field: " + key);
            }
            return s;
        }
    }
}
