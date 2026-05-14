package com.ghatana.yappc.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.audit.AuditLogger;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.bytebuf.ByteBuf;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @doc.type class
 * @doc.purpose Verify preview session API security enforcement and audit parity
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PreviewSessionApiController")
class PreviewSessionApiControllerTest extends EventloopTestBase {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("create session emits scoped audit event")
    void createSessionEmitsScopedAuditEvent() throws Exception {
        RecordingAuditLogger auditLogger = new RecordingAuditLogger();
        PreviewSessionApiController controller = new PreviewSessionApiController(objectMapper, "test-secret", auditLogger);

        HttpRequest request = jsonPost(
                "http://localhost/api/v1/preview/session/create",
                Map.of("projectId", "proj-1", "artifactId", "artifact-1", "duration", 60)
        );
        request.attach(Principal.class, new Principal("user-1", List.of("builder"), "tenant-1"));

        HttpResponse response = runPromise(() -> controller.createSession(request));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(auditLogger.events()).hasSize(1);
        assertThat(auditLogger.events().get(0)).containsEntry("type", "preview.session.create")
                .containsEntry("outcome", "succeeded")
                .containsEntry("actor", "user-1")
                .containsEntry("tenantId", "tenant-1")
                .containsEntry("projectId", "proj-1")
                .containsEntry("artifactId", "artifact-1");
        Map<?, ?> metadata = (Map<?, ?>) auditLogger.events().get(0).get("metadata");
        assertThat(metadata.get("durationSeconds")).isEqualTo(60);
    }

    @Test
    @DisplayName("validate session emits outcome audit event")
    void validateSessionEmitsOutcomeAuditEvent() throws Exception {
        RecordingAuditLogger auditLogger = new RecordingAuditLogger();
        PreviewSessionApiController controller = new PreviewSessionApiController(objectMapper, "test-secret", auditLogger);

        HttpRequest createRequest = jsonPost(
                "http://localhost/api/v1/preview/session/create",
                Map.of("projectId", "proj-1", "artifactId", "artifact-1")
        );
        createRequest.attach(Principal.class, new Principal("user-1", List.of("builder"), "tenant-1"));
        HttpResponse createResponse = runPromise(() -> controller.createSession(createRequest));
        String sessionToken = stringValue(readJson(createResponse).get("sessionToken"));

        HttpRequest validateRequest = jsonPost(
                "http://localhost/api/v1/preview/session/validate",
                Map.of("sessionToken", sessionToken)
        );
        validateRequest.attach(Principal.class, new Principal("user-1", List.of("builder"), "tenant-1"));

        HttpResponse validateResponse = runPromise(() -> controller.validateSession(validateRequest));

        assertThat(validateResponse.getCode()).isEqualTo(200);
        assertThat(auditLogger.events()).hasSize(2);
        assertThat(auditLogger.events().get(1)).containsEntry("type", "preview.session.validate")
                .containsEntry("outcome", "succeeded")
                .containsEntry("actor", "user-1")
                .containsEntry("tenantId", "tenant-1");
        Map<?, ?> metadata = (Map<?, ?>) auditLogger.events().get(1).get("metadata");
        assertThat(metadata.get("valid")).isEqualTo(true);
        assertThat(metadata.get("reason")).isEqualTo("valid");
    }

    // ── Section 16: Security enforcement tests ────────────────────────────────

    @Nested
    @DisplayName("Production startup guard")
    class ProductionStartupGuard {

        @Test
        @DisplayName("production startup fails without preview signing secret")
        void productionStartupFailsWithoutSigningSecret() {
            RecordingAuditLogger auditLogger = new RecordingAuditLogger();
            PreviewSecurityPolicy policy = PreviewSecurityPolicy.productionDefaults();

            assertThatThrownBy(() -> PreviewSessionApiController.createProductionSafe(
                    new ObjectMapper(), null, auditLogger,
                    null, policy, true))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("YAPPC_PREVIEW_SESSION_SECRET");
        }

        @Test
        @DisplayName("production startup fails without authorization service")
        void productionStartupFailsWithoutAuthorizationService() {
            RecordingAuditLogger auditLogger = new RecordingAuditLogger();
            PreviewSecurityPolicy policy = PreviewSecurityPolicy.productionDefaults();

            assertThatThrownBy(() -> PreviewSessionApiController.createProductionSafe(
                    new ObjectMapper(), "a-strong-secret", auditLogger,
                    null, policy, true))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("YappcAuthorizationService");
        }

        @Test
        @DisplayName("non-production mode allows missing secret")
        void nonProductionAllowsMissingSecret() throws Exception {
            RecordingAuditLogger auditLogger = new RecordingAuditLogger();
            PreviewSecurityPolicy policy = PreviewSecurityPolicy.developmentDefaults();

            // Should NOT throw in non-production mode
            PreviewSessionApiController controller = PreviewSessionApiController.createProductionSafe(
                    new ObjectMapper(), null, auditLogger, null, policy, false);
            assertThat(controller).isNotNull();
        }
    }

    @Nested
    @DisplayName("Trust level enforcement")
    class TrustLevelEnforcement {

        @Test
        @DisplayName("untrusted artifact cannot create preview session")
        void untrustedArtifactCannotCreatePreviewSession() throws Exception {
            RecordingAuditLogger auditLogger = new RecordingAuditLogger();
            PreviewSessionApiController controller = new PreviewSessionApiController(objectMapper, "test-secret", auditLogger);

            HttpRequest request = jsonPost(
                    "http://localhost/api/v1/preview/session/create",
                    Map.of("projectId", "proj-1", "artifactId", "artifact-1",
                           "trustLevel", "UNTRUSTED")
            );
            request.attach(Principal.class, new Principal("user-1", List.of("builder"), "tenant-1"));

            HttpResponse response = runPromise(() -> controller.createSession(request));

            assertThat(response.getCode()).isEqualTo(403);
            assertThat(response.getBody().asString(StandardCharsets.UTF_8)).contains("Untrusted");

            // Audit event must record the block decision
            assertThat(auditLogger.events()).hasSize(1);
            assertThat(auditLogger.events().get(0)).containsEntry("outcome", "blocked");
            Map<?, ?> metadata = (Map<?, ?>) auditLogger.events().get(0).get("metadata");
            assertThat(metadata.get("reason")).isEqualTo("untrusted_artifact_blocked");
        }

        @Test
        @DisplayName("semi-trusted artifact without acknowledgement is blocked")
        void semiTrustedWithoutAcknowledgementIsBlocked() throws Exception {
            RecordingAuditLogger auditLogger = new RecordingAuditLogger();
            PreviewSessionApiController controller = new PreviewSessionApiController(objectMapper, "test-secret", auditLogger);

            HttpRequest request = jsonPost(
                    "http://localhost/api/v1/preview/session/create",
                    Map.of("projectId", "proj-1", "artifactId", "artifact-1",
                           "trustLevel", "SEMI_TRUSTED")
            );
            request.attach(Principal.class, new Principal("user-1", List.of("builder"), "tenant-1"));

            HttpResponse response = runPromise(() -> controller.createSession(request));

            assertThat(response.getCode()).isEqualTo(403);
            assertThat(response.getBody().asString(StandardCharsets.UTF_8)).contains("acknowledgement");

            // Audit event must record the block decision
            assertThat(auditLogger.events()).hasSize(1);
            assertThat(auditLogger.events().get(0)).containsEntry("outcome", "blocked");
            Map<?, ?> metadata = (Map<?, ?>) auditLogger.events().get(0).get("metadata");
            assertThat(metadata.get("reason")).isEqualTo("semi_trusted_requires_acknowledgement");
        }

        @Test
        @DisplayName("semi-trusted artifact with acknowledgement creates session")
        void semiTrustedWithAcknowledgementCreatesSession() throws Exception {
            RecordingAuditLogger auditLogger = new RecordingAuditLogger();
            PreviewSessionApiController controller = new PreviewSessionApiController(objectMapper, "test-secret", auditLogger);

            HttpRequest request = jsonPost(
                    "http://localhost/api/v1/preview/session/create",
                    Map.of("projectId", "proj-1", "artifactId", "artifact-1",
                           "trustLevel", "SEMI_TRUSTED", "acknowledged", true)
            );
            request.attach(Principal.class, new Principal("user-1", List.of("builder"), "tenant-1"));

            HttpResponse response = runPromise(() -> controller.createSession(request));

            assertThat(response.getCode()).isEqualTo(200);
            assertThat(auditLogger.events()).hasSize(1);
            assertThat(auditLogger.events().get(0)).containsEntry("outcome", "succeeded");
        }
    }

    @Nested
    @DisplayName("Token validation")
    class TokenValidation {

        @Test
        @DisplayName("expired session token is rejected")
        void expiredSessionTokenIsRejected() throws Exception {
            RecordingAuditLogger auditLogger = new RecordingAuditLogger();
            PreviewSessionApiController controller = new PreviewSessionApiController(objectMapper, "test-secret", auditLogger);

            // Create a session with the minimum duration (0 seconds effective = already expired)
            // We create with duration=1 but the token will still be valid immediately;
            // so instead we use a pre-constructed token with past expiry
            String expiredToken = buildExpiredToken(objectMapper, "test-secret", "tenant-1", "proj-1");

            HttpRequest validateRequest = jsonPost(
                    "http://localhost/api/v1/preview/session/validate",
                    Map.of("sessionToken", expiredToken)
            );
            validateRequest.attach(Principal.class, new Principal("user-1", List.of("builder"), "tenant-1"));

            HttpResponse response = runPromise(() -> controller.validateSession(validateRequest));

            assertThat(response.getCode()).isEqualTo(200);
            Map<String, Object> body = readJson(response);
            assertThat(body.get("valid")).isEqualTo(false);
            assertThat(body.get("reason").toString()).containsIgnoringCase("expired");
        }

        @Test
        @DisplayName("token from different tenant is rejected")
        void tokenFromDifferentTenantIsRejected() throws Exception {
            RecordingAuditLogger auditLogger = new RecordingAuditLogger();
            PreviewSessionApiController controller = new PreviewSessionApiController(objectMapper, "test-secret", auditLogger);

            // Create session for tenant-1
            HttpRequest createRequest = jsonPost(
                    "http://localhost/api/v1/preview/session/create",
                    Map.of("projectId", "proj-1", "artifactId", "artifact-1")
            );
            createRequest.attach(Principal.class, new Principal("user-1", List.of("builder"), "tenant-1"));
            HttpResponse createResponse = runPromise(() -> controller.createSession(createRequest));
            String sessionToken = stringValue(readJson(createResponse).get("sessionToken"));

            // Validate with expected tenant-2 (mismatch)
            HttpRequest validateRequest = jsonPost(
                    "http://localhost/api/v1/preview/session/validate",
                    Map.of("sessionToken", sessionToken,
                           "expectedTenantId", "tenant-2")
            );
            validateRequest.attach(Principal.class, new Principal("user-2", List.of("builder"), "tenant-2"));

            HttpResponse response = runPromise(() -> controller.validateSession(validateRequest));

            assertThat(response.getCode()).isEqualTo(200);
            Map<String, Object> body = readJson(response);
            assertThat(body.get("valid")).isEqualTo(false);
            assertThat(body.get("reason").toString()).containsIgnoringCase("tenant");
        }

        @Test
        @DisplayName("token from different project is rejected")
        void tokenFromDifferentProjectIsRejected() throws Exception {
            RecordingAuditLogger auditLogger = new RecordingAuditLogger();
            PreviewSessionApiController controller = new PreviewSessionApiController(objectMapper, "test-secret", auditLogger);

            // Create session for proj-1
            HttpRequest createRequest = jsonPost(
                    "http://localhost/api/v1/preview/session/create",
                    Map.of("projectId", "proj-1", "artifactId", "artifact-1")
            );
            createRequest.attach(Principal.class, new Principal("user-1", List.of("builder"), "tenant-1"));
            HttpResponse createResponse = runPromise(() -> controller.createSession(createRequest));
            String sessionToken = stringValue(readJson(createResponse).get("sessionToken"));

            // Validate with expected proj-2 (mismatch)
            HttpRequest validateRequest = jsonPost(
                    "http://localhost/api/v1/preview/session/validate",
                    Map.of("sessionToken", sessionToken,
                           "expectedProjectId", "proj-2")
            );
            validateRequest.attach(Principal.class, new Principal("user-1", List.of("builder"), "tenant-1"));

            HttpResponse response = runPromise(() -> controller.validateSession(validateRequest));

            assertThat(response.getCode()).isEqualTo(200);
            Map<String, Object> body = readJson(response);
            assertThat(body.get("valid")).isEqualTo(false);
            assertThat(body.get("reason").toString()).containsIgnoringCase("project");
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private HttpRequest jsonPost(String url, Map<String, Object> body) throws Exception {
        return HttpRequest.post(url)
                .withBody(ByteBuf.wrapForReading(objectMapper.writeValueAsBytes(body)))
                .build();
    }

    private Map<String, Object> readJson(HttpResponse response) throws Exception {
        return objectMapper.readValue(
                response.getBody().asString(StandardCharsets.UTF_8),
                new TypeReference<>() {
                }
        );
    }

    private String stringValue(Object value) {
        return value instanceof String string ? string : "";
    }

    /**
     * Builds a session token whose expiresAt is 1 hour in the past (already expired).
     */
    private static String buildExpiredToken(ObjectMapper mapper, String secret, String tenantId, String projectId) throws Exception {
        java.time.Instant past = java.time.Instant.now().minusSeconds(7200);
        java.util.LinkedHashMap<String, Object> session = new java.util.LinkedHashMap<>();
        session.put("sessionId", "preview_expired");
        session.put("projectId", projectId);
        session.put("artifactId", "artifact-expired");
        session.put("userId", "user-1");
        session.put("tenantId", tenantId);
        session.put("createdAt", past.minusSeconds(3600).toString());
        session.put("expiresAt", past.toString());
        session.put("scope", Map.of());
        session.put("trustLevel", "TRUSTED_LOCAL");
        session.put("dataClassification", "INTERNAL");
        session.put("cspEnabled", false);
        session.put("sandboxEnabled", false);

        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
        String sig = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(mapper.writeValueAsBytes(session)));
        session.put("signature", sig);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(mapper.writeValueAsBytes(session));
    }

    private static final class RecordingAuditLogger implements AuditLogger {
        private final List<Map<String, Object>> events = new ArrayList<>();

        @Override
        public Promise<Void> log(Map<String, Object> event) {
            events.add(event);
            return Promise.complete();
        }

        List<Map<String, Object>> events() {
            return events;
        }
    }
}
