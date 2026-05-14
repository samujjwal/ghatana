package com.ghatana.yappc.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.audit.AuditLogger;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.security.rbac.Permission;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.HttpHeaders;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Issues and validates signed preview sessions for the standalone builder preview runtime.
 *
 * <p>This controller enforces backend authorization for preview session operations,
 * ensuring that only authenticated principals with appropriate project permissions
 * can issue preview sessions for specific artifacts. Preview sessions include trust
 * classification, CSP/sandbox policy, and data classification for security boundaries.
 *
 * @doc.type class
 * @doc.purpose Issues and validates signed preview sessions for the standalone builder preview runtime
 * @doc.layer api
 * @doc.pattern Controller
 */
public final class PreviewSessionApiController {

    private static final Logger log = LoggerFactory.getLogger(PreviewSessionApiController.class);
    private static final int DEFAULT_SESSION_DURATION_SECONDS = 3600;
    private static final int MAX_SESSION_DURATION_SECONDS = 86400;

    private final ObjectMapper objectMapper;
    private final String signingSecret;
    private final AuditLogger auditLogger;
    private final YappcAuthorizationService authorizationService;
    private final PreviewSecurityPolicy securityPolicy;

    public PreviewSessionApiController(@NotNull ObjectMapper objectMapper, String signingSecret) {
        this(objectMapper, signingSecret, event -> Promise.complete(), null, null);
    }

    public PreviewSessionApiController(@NotNull ObjectMapper objectMapper, String signingSecret, @NotNull AuditLogger auditLogger) {
        this(objectMapper, signingSecret, auditLogger, null, null);
    }

    public PreviewSessionApiController(
            @NotNull ObjectMapper objectMapper,
            String signingSecret,
            @NotNull AuditLogger auditLogger,
            YappcAuthorizationService authorizationService
    ) {
        this(objectMapper, signingSecret, auditLogger, authorizationService, null);
    }

    public PreviewSessionApiController(
            @NotNull ObjectMapper objectMapper,
            String signingSecret,
            @NotNull AuditLogger auditLogger,
            YappcAuthorizationService authorizationService,
            PreviewSecurityPolicy securityPolicy
    ) {
        this.objectMapper = objectMapper;
        this.signingSecret = signingSecret;
        this.auditLogger = auditLogger;
        this.authorizationService = authorizationService;
        this.securityPolicy = securityPolicy != null ? securityPolicy : PreviewSecurityPolicy.developmentDefaults();
    }

    /**
     * Creates a production-safe PreviewSessionApiController with mandatory dependencies.
     *
     * <p>In production mode, this factory fails fast if required security dependencies are missing:
     * <ul>
     *   <li>YAPPC_PREVIEW_SESSION_SECRET must be configured</li>
     *   <li>YappcAuthorizationService must be provided for resource-level authorization</li>
     *   <li>AuditLogger must be a real implementation (not noop)</li>
     *   <li>PreviewSecurityPolicy must be provided with strict CSP/sandbox settings</li>
     * </ul>
     *
     * @param objectMapper Jackson ObjectMapper for JSON serialization
     * @param signingSecret HMAC signing secret for session tokens
     * @param auditLogger Audit logger for security events
     * @param authorizationService Authorization service for resource-level checks
     * @param securityPolicy Security policy with CSP/sandbox/trust classification
     * @param isProduction true if running in production mode
     * @return configured PreviewSessionApiController
     * @throws IllegalStateException if production mode and required dependencies are missing
     */
    public static PreviewSessionApiController createProductionSafe(
            @NotNull ObjectMapper objectMapper,
            String signingSecret,
            @NotNull AuditLogger auditLogger,
            YappcAuthorizationService authorizationService,
            PreviewSecurityPolicy securityPolicy,
            boolean isProduction
    ) {
        if (isProduction) {
            if (signingSecret == null || signingSecret.isBlank()) {
                throw new IllegalStateException(
                    "PRODUCTION STARTUP GUARD FAILED: YAPPC_PREVIEW_SESSION_SECRET is required in production mode"
                );
            }
            if (authorizationService == null) {
                throw new IllegalStateException(
                    "PRODUCTION STARTUP GUARD FAILED: YappcAuthorizationService is required in production mode for resource-level authorization"
                );
            }
            if (auditLogger == null) {
                throw new IllegalStateException(
                    "PRODUCTION STARTUP GUARD FAILED: Real AuditLogger is required in production mode (noop audit not allowed)"
                );
            }
            if (securityPolicy == null) {
                throw new IllegalStateException(
                    "PRODUCTION STARTUP GUARD FAILED: PreviewSecurityPolicy is required in production mode for CSP/sandbox enforcement"
                );
            }
            if (!securityPolicy.cspPolicy().enabled()) {
                throw new IllegalStateException(
                    "PRODUCTION STARTUP GUARD FAILED: CSP must be enabled in production mode"
                );
            }
            if (!securityPolicy.sandboxPolicy().enabled()) {
                throw new IllegalStateException(
                    "PRODUCTION STARTUP GUARD FAILED: Sandbox must be enabled in production mode"
                );
            }
        }
        return new PreviewSessionApiController(objectMapper, signingSecret, auditLogger, authorizationService, securityPolicy);
    }

    public Promise<HttpResponse> createSession(HttpRequest request) {
        if (signingSecret == null || signingSecret.isBlank()) {
            return Promise.of(HttpResponse.ofCode(503)
                    .withJson("{\"error\":\"Preview sessions are not configured on this server\"}")
                    .build());
        }

        Principal principal = request.getAttachment(Principal.class);
        if (principal == null) {
            return Promise.of(HttpResponse.ofCode(401)
                    .withJson("{\"error\":\"Unauthenticated\"}")
                    .build());
        }

        return request.loadBody().then(body -> {
            try {
                String json = body.asString(StandardCharsets.UTF_8);
                CreatePreviewSessionRequest payload = objectMapper.readValue(json, CreatePreviewSessionRequest.class);

                // Validate required fields
                if (payload.projectId() == null || payload.projectId().isBlank()) {
                    return Promise.of(HttpResponse.ofCode(400)
                            .withJson("{\"error\":\"projectId is required\"}")
                            .build());
                }
                if (payload.artifactId() == null || payload.artifactId().isBlank()) {
                    return Promise.of(HttpResponse.ofCode(400)
                            .withJson("{\"error\":\"artifactId is required\"}")
                            .build());
                }

                // Determine effective trust level from payload or policy default
                PreviewSecurityPolicy.TrustLevel effectiveTrustLevel =
                        payload.trustLevel() != null ? payload.trustLevel() : securityPolicy.defaultTrustLevel();

                // UNTRUSTED artifacts are never permitted to create preview sessions
                if (effectiveTrustLevel == PreviewSecurityPolicy.TrustLevel.UNTRUSTED) {
                    return auditPreviewEvent("preview.session.create", "blocked", principal,
                            payload.projectId(), payload.artifactId(),
                            Map.of("reason", "untrusted_artifact_blocked", "trustLevel", effectiveTrustLevel.name()))
                            .map(ignored -> HttpResponse.ofCode(403)
                                    .withJson("{\"error\":\"Untrusted artifacts cannot create preview sessions\"}")
                                    .build());
                }

                // SEMI_TRUSTED artifacts require explicit user acknowledgement before session creation
                if (effectiveTrustLevel == PreviewSecurityPolicy.TrustLevel.SEMI_TRUSTED
                        && !Boolean.TRUE.equals(payload.acknowledged())) {
                    return auditPreviewEvent("preview.session.create", "blocked", principal,
                            payload.projectId(), payload.artifactId(),
                            Map.of("reason", "semi_trusted_requires_acknowledgement", "trustLevel", effectiveTrustLevel.name()))
                            .map(ignored -> HttpResponse.ofCode(403)
                                    .withJson("{\"error\":\"Semi-trusted artifacts require explicit acknowledgement before preview\"}")
                                    .build());
                }

                // Determine duration
                int requestedDuration = payload.duration() != null ? payload.duration() : DEFAULT_SESSION_DURATION_SECONDS;
                int actualDuration = Math.min(requestedDuration, MAX_SESSION_DURATION_SECONDS);

                Instant createdAt = Instant.now();
                Instant expiresAt = createdAt.plusSeconds(actualDuration);

                // Use the already-validated effective trust level
                PreviewSecurityPolicy.TrustLevel trustLevel = effectiveTrustLevel;
                PreviewSecurityPolicy.DataClassification dataClassification = PreviewSecurityPolicy.DataClassification.INTERNAL;

                // Build session token with trust classification
                Map<String, Object> scope = normalizeScope(payload.projectId(), payload.artifactId(), payload.scope(), actualDuration);
                Map<String, Object> unsignedSession = new LinkedHashMap<>();
                unsignedSession.put("sessionId", "preview_" + UUID.randomUUID());
                unsignedSession.put("projectId", payload.projectId());
                unsignedSession.put("artifactId", payload.artifactId());
                unsignedSession.put("userId", principal.getName());
                unsignedSession.put("tenantId", principal.getTenantId());
                unsignedSession.put("createdAt", createdAt.toString());
                unsignedSession.put("expiresAt", expiresAt.toString());
                unsignedSession.put("scope", scope);
                unsignedSession.put("trustLevel", trustLevel.name());
                unsignedSession.put("dataClassification", dataClassification.name());
                unsignedSession.put("cspEnabled", securityPolicy.cspPolicy().enabled());
                unsignedSession.put("sandboxEnabled", securityPolicy.sandboxPolicy().enabled());

                String signature = signPayload(unsignedSession);
                Map<String, Object> signedSession = new LinkedHashMap<>(unsignedSession);
                signedSession.put("signature", signature);
                String sessionToken = encodeSessionToken(signedSession);

                Map<String, Object> response = new LinkedHashMap<>();
                response.put("sessionId", unsignedSession.get("sessionId"));
                response.put("sessionToken", sessionToken);
                response.put("expiresAt", expiresAt.toString());
                response.put("trustLevel", trustLevel.name());
                response.put("dataClassification", dataClassification.name());
                response.put("cspEnabled", securityPolicy.cspPolicy().enabled());
                response.put("sandboxEnabled", securityPolicy.sandboxPolicy().enabled());

                HttpResponse httpResponse = HttpResponse.ok200()
                        .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                        .withJson(objectMapper.writeValueAsString(response))
                        .build();

                return auditPreviewEvent("preview.session.create", "succeeded", principal, payload.projectId(), payload.artifactId(), Map.of(
                        "sessionId", unsignedSession.get("sessionId"),
                        "expiresAt", expiresAt.toString(),
                        "durationSeconds", actualDuration,
                        "trustLevel", trustLevel.name(),
                        "dataClassification", dataClassification.name()
                )).map(ignored -> httpResponse);
            } catch (Exception e) {
                log.error("Error creating preview session", e);
                return Promise.of(HttpResponse.ofCode(400)
                        .withJson("{\"error\":\"Invalid preview session request\"}")
                        .build());
            }
        });
    }

    public Promise<HttpResponse> validateSession(HttpRequest request) {
        if (signingSecret == null || signingSecret.isBlank()) {
            return Promise.of(HttpResponse.ofCode(503)
                    .withJson("{\"valid\":false,\"reason\":\"Preview sessions are not configured on this server\"}")
                    .build());
        }

        return request.loadBody().then(body -> {
            try {
                ValidatePreviewSessionRequest payload = objectMapper.readValue(body.getArray(), ValidatePreviewSessionRequest.class);
                if (payload.sessionToken() == null || payload.sessionToken().isBlank()) {
                    return Promise.of(HttpResponse.ofCode(400)
                            .withJson("{\"valid\":false,\"reason\":\"sessionToken is required\"}")
                            .build());
                }

                ValidationResult validation = validateTokenWithScope(
                        payload.sessionToken(),
                        payload.expectedTenantId(),
                        payload.expectedProjectId()
                );
                Map<String, Object> responseBody = new LinkedHashMap<>();
                responseBody.put("valid", validation.valid());
                responseBody.put("reason", validation.reason());
                HttpResponse httpResponse = HttpResponse.ok200()
                        .withHeader(io.activej.http.HttpHeaders.CONTENT_TYPE, "application/json")
                        .withJson(objectMapper.writeValueAsString(responseBody))
                        .build();
                return auditPreviewEvent(
                        "preview.session.validate",
                        validation.valid() ? "succeeded" : "failed",
                        request.getAttachment(Principal.class),
                        null,
                        null,
                        Map.of(
                                "valid", validation.valid(),
                                "reason", validation.reason() != null ? validation.reason() : "valid"
                        )
                ).map(ignored -> httpResponse);
            } catch (Exception e) {
                return Promise.of(HttpResponse.ofCode(400)
                        .withJson("{\"valid\":false,\"reason\":\"Invalid preview session validation request\"}")
                        .build());
            }
        });
    }

    private ValidationResult validateToken(String sessionToken) {
        try {
            Map<String, Object> signedSession = decodeSessionToken(sessionToken);
            String signature = stringValue(signedSession.get("signature"));
            if (signature == null || signature.isBlank()) {
                return new ValidationResult(false, "Missing signature");
            }

            Map<String, Object> unsignedSession = new LinkedHashMap<>(signedSession);
            unsignedSession.remove("signature");
            String expectedSignature = signPayload(unsignedSession);
            if (!expectedSignature.equals(signature)) {
                return new ValidationResult(false, "Invalid session signature");
            }

            Instant createdAt = Instant.parse(stringValue(unsignedSession.get("createdAt")));
            Instant expiresAt = Instant.parse(stringValue(unsignedSession.get("expiresAt")));
            if (!expiresAt.isAfter(createdAt)) {
                return new ValidationResult(false, "Session expiration must be after creation");
            }
            if (Instant.now().isAfter(expiresAt)) {
                return new ValidationResult(false, "Session expired");
            }

            return new ValidationResult(true, null);
        } catch (Exception e) {
            return new ValidationResult(false, "Invalid preview session token");
        }
    }

    private ValidationResult validateTokenWithScope(String sessionToken, String expectedTenantId, String expectedProjectId) {
        ValidationResult base = validateToken(sessionToken);
        if (!base.valid()) {
            return base;
        }
        try {
            Map<String, Object> decoded = decodeSessionToken(sessionToken);
            if (expectedTenantId != null && !expectedTenantId.equals(decoded.get("tenantId"))) {
                return new ValidationResult(false, "Token tenant does not match requesting context");
            }
            if (expectedProjectId != null && !expectedProjectId.equals(decoded.get("projectId"))) {
                return new ValidationResult(false, "Token project does not match requesting context");
            }
        } catch (Exception e) {
            return new ValidationResult(false, "Could not decode token for scope validation");
        }
        return base;
    }

    private Map<String, Object> normalizeScope(
            String projectId,
            String artifactId,
            Map<String, Object> requestedScope,
            int maxDuration
    ) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        Map<String, Object> scope = requestedScope != null ? requestedScope : Map.of();
        normalized.put("readOnly", booleanValue(scope.get("readOnly"), true));
        normalized.put("allowDownload", booleanValue(scope.get("allowDownload"), false));
        normalized.put("allowClipboard", booleanValue(scope.get("allowClipboard"), false));
        normalized.put("maxDuration", maxDuration);
        normalized.put("allowedProjectIds", List.copyOf(scopeList(scope.get("allowedProjectIds"), projectId)));
        normalized.put("allowedArtifactIds", List.copyOf(scopeList(scope.get("allowedArtifactIds"), artifactId)));
        return normalized;
    }

    private List<String> scopeList(Object value, String fallback) {
        if (value instanceof List<?> list) {
            return list.stream().filter(String.class::isInstance).map(String.class::cast).distinct().sorted().toList();
        }
        return List.of(fallback);
    }

    private boolean booleanValue(Object value, boolean fallback) {
        return value instanceof Boolean bool ? bool : fallback;
    }

    private String signPayload(Map<String, Object> payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signature = mac.doFinal(objectMapper.writeValueAsBytes(payload));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
    }

    private String encodeSessionToken(Map<String, Object> signedSession) throws Exception {
        byte[] json = objectMapper.writeValueAsBytes(signedSession);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json);
    }

    private Map<String, Object> decodeSessionToken(String sessionToken) throws Exception {
        byte[] decoded = Base64.getUrlDecoder().decode(sessionToken);
        return objectMapper.readValue(decoded, new TypeReference<>() {});
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String stringValue(Object value) {
        return value instanceof String string ? string : null;
    }

    private Promise<Void> auditPreviewEvent(
            String eventType,
            String outcome,
            Principal principal,
            String projectId,
            String artifactId,
            Map<String, Object> metadata
    ) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", eventType);
        event.put("outcome", outcome);
        event.put("actor", principal.getName());
        event.put("tenantId", principal.getTenantId());
        event.put("projectId", projectId);
        event.put("artifactId", artifactId);
        event.put("timestamp", Instant.now().toString());
        event.put("metadata", metadata);
        return auditLogger.log(event);
    }

    public record CreatePreviewSessionRequest(
            String tenantId,
            String workspaceId,
            String projectId,
            String artifactId,
            Integer duration,
            Map<String, Object> scope,
            PreviewSecurityPolicy.TrustLevel trustLevel,
            PreviewSecurityPolicy.DataClassification dataClassification,
            Boolean acknowledged
    ) {
    }

    public record ValidatePreviewSessionRequest(
            String sessionToken,
            String expectedTenantId,
            String expectedWorkspaceId,
            String expectedProjectId
    ) {
    }

    private record ValidationResult(boolean valid, String reason) {
    }
}
