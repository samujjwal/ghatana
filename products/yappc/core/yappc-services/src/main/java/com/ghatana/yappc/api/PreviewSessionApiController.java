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
 * can issue preview sessions for specific artifacts.
 *
 * @doc.type class
 * @doc.purpose Issues and validates signed preview sessions for the standalone builder preview runtime
 * @doc.layer api
 * @doc.pattern Controller
 */
public final class PreviewSessionApiController {

    private static final int DEFAULT_SESSION_DURATION_SECONDS = 3600;
    private static final int MAX_SESSION_DURATION_SECONDS = 86400;

    private final ObjectMapper objectMapper;
    private final String signingSecret;
    private final AuditLogger auditLogger;
    private final YappcAuthorizationService authorizationService;

    public PreviewSessionApiController(@NotNull ObjectMapper objectMapper, String signingSecret) {
        this(objectMapper, signingSecret, AuditLogger.noop(), null);
    }

    public PreviewSessionApiController(@NotNull ObjectMapper objectMapper, String signingSecret, @NotNull AuditLogger auditLogger) {
        this(objectMapper, signingSecret, auditLogger, null);
    }

    public PreviewSessionApiController(
            @NotNull ObjectMapper objectMapper,
            String signingSecret,
            @NotNull AuditLogger auditLogger,
            YappcAuthorizationService authorizationService
    ) {
        this.objectMapper = objectMapper;
        this.signingSecret = signingSecret;
        this.auditLogger = auditLogger;
        this.authorizationService = authorizationService;
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
                CreatePreviewSessionRequest payload = objectMapper.readValue(body.getArray(), CreatePreviewSessionRequest.class);
                if (payload.projectId() == null || payload.projectId().isBlank()) {
                    return Promise.of(HttpResponse.ofCode(400).withJson("{\"error\":\"projectId is required\"}").build());
                }
                if (payload.artifactId() == null || payload.artifactId().isBlank()) {
                    return Promise.of(HttpResponse.ofCode(400).withJson("{\"error\":\"artifactId is required\"}").build());
                }

                // Enforce backend authorization if authorization service is available
                if (authorizationService != null) {
                    String tenantId = principal.getTenantId();
                    String workspaceId = firstNonBlank(
                        request.getHeader(HttpHeaders.of("X-Workspace-Id")),
                        request.getHeader(HttpHeaders.of("X-Workspace-ID"))
                    );
                    if (workspaceId == null || workspaceId.isBlank()) {
                        return Promise.of(HttpResponse.ofCode(400)
                                .withJson("{\"error\":\"workspaceId is required\"}")
                                .build());
                    }
                    try {
                        authorizationService.authorizeArtifactAccess(
                            principal,
                            tenantId,
                            workspaceId,
                            payload.projectId(),
                            payload.artifactId(),
                            Permission.PROJECT_READ
                        );
                    } catch (Exception e) {
                        return Promise.of(HttpResponse.ofCode(403)
                                .withJson("{\"error\":\"Forbidden: insufficient permissions\"}")
                                .build());
                    }
                }

                int requestedDuration = payload.duration() != null ? payload.duration() : DEFAULT_SESSION_DURATION_SECONDS;
                int actualDuration = Math.min(Math.max(1, requestedDuration), MAX_SESSION_DURATION_SECONDS);
                Instant createdAt = Instant.now();
                Instant expiresAt = createdAt.plusSeconds(actualDuration);

                Map<String, Object> scope = normalizeScope(payload.projectId(), payload.artifactId(), payload.scope(), actualDuration);
                Map<String, Object> unsignedSession = new LinkedHashMap<>();
                unsignedSession.put("sessionId", "preview_" + UUID.randomUUID());
                unsignedSession.put("projectId", payload.projectId());
                unsignedSession.put("artifactId", payload.artifactId());
                unsignedSession.put("userId", principal.getName());
                unsignedSession.put("createdAt", createdAt.toString());
                unsignedSession.put("expiresAt", expiresAt.toString());
                unsignedSession.put("scope", scope);

                String signature = signPayload(unsignedSession);
                Map<String, Object> signedSession = new LinkedHashMap<>(unsignedSession);
                signedSession.put("signature", signature);
                String sessionToken = encodeSessionToken(signedSession);

                Map<String, Object> response = Map.of(
                        "sessionId", unsignedSession.get("sessionId"),
                        "sessionToken", sessionToken,
                        "expiresAt", expiresAt.toString()
                );
                HttpResponse httpResponse = HttpResponse.ok200()
                        .withHeader(io.activej.http.HttpHeaders.CONTENT_TYPE, "application/json")
                        .withJson(objectMapper.writeValueAsString(response))
                        .build();
                return auditPreviewEvent("preview.session.create", "succeeded", principal, payload.projectId(), payload.artifactId(), Map.of(
                        "sessionId", unsignedSession.get("sessionId"),
                        "expiresAt", expiresAt.toString(),
                        "durationSeconds", actualDuration
                )).map(ignored -> httpResponse);
            } catch (Exception e) {
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

                ValidationResult validation = validateToken(payload.sessionToken());
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
     rivate static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    p   return objectMapper.readValue(decoded, new TypeReference<>() {
        });
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
            String projectId,
            String artifactId,
            Integer duration,
            Map<String, Object> scope
    ) {
    }

    public record ValidatePreviewSessionRequest(String sessionToken) {
    }

    private record ValidationResult(boolean valid, String reason) {
    }
}
