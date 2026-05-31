package com.ghatana.datacloud.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.memory.media.MediaArtifactEventEmitter;
import com.ghatana.datacloud.memory.media.MediaArtifactRecord;
import com.ghatana.datacloud.memory.media.MediaArtifactRepository;
import com.ghatana.datacloud.operations.OperationKind;
import com.ghatana.datacloud.operations.OperationRecord;
import com.ghatana.datacloud.operations.OperationRecorder;
import com.ghatana.datacloud.operations.OperationStatus;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.http.server.response.ResponseBuilder;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * REST controller for Data Cloud media artifact metadata.
 *
 * <p>Emits events for all mutating operations to enable cross-plane integration
 * with Data Cloud Operations and agent catalog tool registration.
 *
 * <p>Tenant identity is resolved from the authenticated {@link Principal}
 * attached by the security filter. The controller does not read spoofable
 * tenant headers or query parameters.
 *
 * @doc.type class
 * @doc.purpose Tenant-scoped API for media artifact registration and retrieval
 * @doc.layer product
 * @doc.pattern Controller
 */
public final class MediaArtifactController {

    private static final Logger log = LoggerFactory.getLogger(MediaArtifactController.class);

    private static final String COLLECTION_PATH = "/api/v1/media/artifacts";
    private static final String TRANSCRIPTION_SUFFIX = "/transcribe";
    private static final String VISION_SUFFIX = "/analyze";

    private final MediaArtifactRepository repository;
    private final ObjectMapper objectMapper;
    private final MediaArtifactEventEmitter eventEmitter;
    private final OperationRecorder operationRecorder;

    public MediaArtifactController(MediaArtifactRepository repository, ObjectMapper objectMapper) {
        this(repository, objectMapper, null);
    }

    public MediaArtifactController(MediaArtifactRepository repository, ObjectMapper objectMapper, MediaArtifactEventEmitter eventEmitter) {
        this(repository, objectMapper, eventEmitter, null);
    }

    public MediaArtifactController(
            MediaArtifactRepository repository,
            ObjectMapper objectMapper,
            MediaArtifactEventEmitter eventEmitter,
            OperationRecorder operationRecorder) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
        this.eventEmitter = eventEmitter;
        this.operationRecorder = operationRecorder;

        if (eventEmitter != null) {
            log.info("[media-artifact] Controller initialized with event emitter");
        } else {
            log.warn("[media-artifact] Controller initialized without event emitter - events will not be emitted");
        }
    }

    public Promise<HttpResponse> handle(HttpRequest request) {
        Principal principal = request.getAttachment(Principal.class);
        if (principal == null || principal.getTenantId() == null || principal.getTenantId().isBlank()) {
            log.warn("[media-artifact] Authenticated principal with tenant scope is required");
            return Promise.of(ResponseBuilder.unauthorized()
                .json(Map.of("error", "Authenticated tenant principal is required"))
                .build());
        }

        String tenantId = principal.getTenantId();
        String path = request.getPath();
        HttpMethod method = request.getMethod();

        if (method == HttpMethod.POST && COLLECTION_PATH.equals(path)) {
            if (!hasAnyRole(principal, "editor", "admin", "processor")) {
                return Promise.of(ResponseBuilder.forbidden().json(Map.of("error", "media artifact create permission is required")).build());
            }
            return createArtifact(request, tenantId);
        }
        if (method == HttpMethod.GET && COLLECTION_PATH.equals(path)) {
            if (!hasAnyRole(principal, "viewer", "editor", "admin", "processor")) {
                return Promise.of(ResponseBuilder.forbidden().json(Map.of("error", "media artifact read permission is required")).build());
            }
            return listArtifacts(request, tenantId);
        }
        if (path.startsWith(COLLECTION_PATH + "/")) {
            String remainingPath = path.substring((COLLECTION_PATH + "/").length());

            // Handle nested routes: /transcribe and /analyze
            if (remainingPath.endsWith(TRANSCRIPTION_SUFFIX)) {
                String artifactId = remainingPath.substring(0, remainingPath.length() - TRANSCRIPTION_SUFFIX.length());
                if (artifactId.isBlank()) {
                    return Promise.of(ResponseBuilder.badRequest()
                        .json(Map.of("error", "artifactId path parameter is required"))
                        .build());
                }
                if (method == HttpMethod.POST) {
                    if (!hasAnyRole(principal, "editor", "admin", "processor")) {
                        return Promise.of(ResponseBuilder.forbidden().json(Map.of("error", "media artifact process permission is required")).build());
                    }
                    return triggerTranscription(artifactId, tenantId, principal, request);
                }
            }
            if (remainingPath.endsWith(VISION_SUFFIX)) {
                String artifactId = remainingPath.substring(0, remainingPath.length() - VISION_SUFFIX.length());
                if (artifactId.isBlank()) {
                    return Promise.of(ResponseBuilder.badRequest()
                        .json(Map.of("error", "artifactId path parameter is required"))
                        .build());
                }
                if (method == HttpMethod.POST) {
                    if (!hasAnyRole(principal, "editor", "admin", "processor")) {
                        return Promise.of(ResponseBuilder.forbidden().json(Map.of("error", "media artifact process permission is required")).build());
                    }
                    return triggerVisionAnalysis(artifactId, tenantId, principal, request);
                }
            }

            // Handle simple artifact ID routes (no nested path)
            if (!remainingPath.contains("/")) {
                String artifactId = remainingPath;
                if (artifactId.isBlank()) {
                    return Promise.of(ResponseBuilder.badRequest()
                        .json(Map.of("error", "artifactId path parameter is required"))
                        .build());
                }
                if (method == HttpMethod.GET) {
                    if (!hasAnyRole(principal, "viewer", "editor", "admin", "processor")) {
                        return Promise.of(ResponseBuilder.forbidden().json(Map.of("error", "media artifact read permission is required")).build());
                    }
                    return getArtifact(artifactId, tenantId);
                }
                if (method == HttpMethod.DELETE) {
                    if (!hasAnyRole(principal, "admin")) {
                        return Promise.of(ResponseBuilder.forbidden().json(Map.of("error", "media artifact delete permission is required")).build());
                    }
                    return deleteArtifact(artifactId, tenantId);
                }
            }
        }

        return Promise.of(ResponseBuilder.notFound()
            .json(Map.of("error", "Endpoint not found"))
            .build());
    }

    private Promise<HttpResponse> createArtifact(HttpRequest request, String tenantId) {
        return request.loadBody().then(body -> {
            try {
                CreateMediaArtifactRequest payload = objectMapper.readValue(
                    body != null ? body.asArray() : new byte[0],
                    CreateMediaArtifactRequest.class);

                String agentId = normalize(payload.agentId());
                String mediaType = normalize(payload.mediaType());
                String storageUri = normalize(payload.storageUri());
                String consentStatus = normalize(payload.consentStatus());

                if (agentId == null || mediaType == null || storageUri == null) {
                    return Promise.of(ResponseBuilder.badRequest()
                        .json(Map.of("error", "agentId, mediaType, and storageUri are required"))
                        .build());
                }

                if (requiresExplicitConsent(mediaType) && consentStatus == null) {
                    return Promise.of(ResponseBuilder.badRequest()
                        .json(Map.of("error", "consentStatus is required for audio/video media artifacts"))
                        .build());
                }

                Map<String, String> metadata = new HashMap<>();
                if (payload.metadata() != null) {
                    metadata.putAll(payload.metadata());
                }
                if (consentStatus != null) {
                    metadata.put("consentStatus", consentStatus);
                }
                String retentionPolicy = normalize(payload.retentionPolicy());
                if (retentionPolicy != null) {
                    metadata.put("retentionPolicy", retentionPolicy);
                }
                String retentionUntil = normalize(payload.retentionUntil());
                if (retentionUntil != null) {
                    metadata.put("retentionUntil", retentionUntil);
                }

                MediaArtifactRecord record = MediaArtifactRecord.create(
                    tenantId,
                    agentId,
                    mediaType,
                    storageUri,
                    payload.sizeBytes() == null ? 0L : payload.sizeBytes(),
                    normalize(payload.checksum()),
                    payload.durationMs() == null ? 0L : payload.durationMs(),
                    normalize(payload.originToolId()),
                    normalize(payload.correlationId()),
                    Map.copyOf(metadata));

                return repository.save(record)
                    .then(saved -> {
                        if (eventEmitter != null) {
                            return eventEmitter.emitCreated(saved)
                                .map(offset -> ResponseBuilder.created().json(toResponse(saved)).build());
                        }
                        return Promise.of(ResponseBuilder.created().json(toResponse(saved)).build());
                    });
            } catch (Exception e) {
                log.warn("Invalid create media artifact request", e);
                return Promise.of(ResponseBuilder.badRequest()
                    .json(Map.of("error", "Invalid request payload"))
                    .build());
            }
        });
    }

    private Promise<HttpResponse> listArtifacts(HttpRequest request, String tenantId) {
        int limit = parseLimit(request.getQueryParameter("limit"));
        String agentId = normalize(request.getQueryParameter("agentId"));
        String mediaType = normalize(request.getQueryParameter("mediaType"));

        if (agentId == null && mediaType == null) {
            return Promise.of(ResponseBuilder.badRequest()
                .json(Map.of("error", "Either agentId or mediaType query parameter is required"))
                .build());
        }

        Promise<List<MediaArtifactRecord>> recordsPromise;
        if (agentId != null) {
            recordsPromise = repository.findByAgent(agentId, tenantId, limit);
        } else {
            recordsPromise = repository.findByMediaType(mediaType, tenantId, limit);
        }

        return recordsPromise.map(records -> ResponseBuilder.ok()
            .json(Map.of("items", records.stream().map(this::toResponse).toList(), "count", records.size()))
            .build());
    }

    private Promise<HttpResponse> getArtifact(String artifactId, String tenantId) {
        return repository.findById(artifactId, tenantId)
            .map(optional -> optional
                .map(record -> ResponseBuilder.ok().json(toResponse(record)).build())
                .orElseGet(() -> ResponseBuilder.notFound()
                    .json(Map.of("error", "Media artifact not found"))
                    .build()));
    }

    private Promise<HttpResponse> deleteArtifact(String artifactId, String tenantId) {
        return repository.findById(artifactId, tenantId)
            .then(optional -> {
                if (optional.isEmpty()) {
                    return Promise.of(ResponseBuilder.notFound()
                        .json(Map.of("error", "Media artifact not found"))
                        .build());
                }

                MediaArtifactRecord record = optional.get();
                return repository.delete(artifactId, tenantId)
                    .then(deleted -> {
                        if (deleted && eventEmitter != null) {
                            return eventEmitter.emitDeleted(artifactId, tenantId, record.agentId())
                                .map(offset -> ResponseBuilder.noContent().build());
                        }
                        return Promise.of(deleted
                            ? ResponseBuilder.noContent().build()
                            : ResponseBuilder.notFound().json(Map.of("error", "Media artifact not found")).build());
                    });
            });
    }

    private Promise<HttpResponse> triggerTranscription(String artifactId, String tenantId, Principal principal, HttpRequest request) {
        return repository.findById(artifactId, tenantId)
            .then(optional -> {
                if (optional.isEmpty()) {
                    return Promise.of(ResponseBuilder.notFound()
                        .json(Map.of("error", "Media artifact not found"))
                        .build());
                }

                MediaArtifactRecord record = optional.get();
                if (!record.mediaType().startsWith("audio/")) {
                    return Promise.of(ResponseBuilder.badRequest()
                        .json(Map.of("error", "Transcription is only supported for audio artifacts"))
                        .build());
                }

                if (eventEmitter == null) {
                    OperationRecord operation = recordMediaOperation(
                        tenantId,
                        artifactId,
                        record.agentId(),
                        OperationKind.MEDIA_PROCESSING,
                        OperationStatus.BLOCKED,
                        "Media transcription",
                        "Transcription runtime is not configured",
                        principal,
                        request,
                        Map.of("operation", "transcription"));
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("error", "Media transcription runtime is not configured");
                    response.put("artifactId", artifactId);
                    response.put("status", "blocked");
                    if (operation != null) {
                        response.put("operationId", operation.operationId());
                    }
                    return Promise.of(ResponseBuilder.serviceUnavailable().json(response).build());
                }

                return request.loadBody().then(body -> {
                    try {
                        String languageCode = "en-US";
                        if (body != null && body.asArray().length > 0) {
                            Map<String, String> payload = objectMapper.readValue(
                                body.asArray(),
                                objectMapper.getTypeFactory().constructMapType(Map.class, String.class, String.class));
                            if (payload.containsKey("languageCode")) {
                                languageCode = payload.get("languageCode");
                            }
                        }

                        String jobId = "transcription-" + artifactId + "-" + System.currentTimeMillis();
                        String requestedLanguageCode = languageCode;
                        OperationRecord operation = recordMediaOperation(
                            tenantId,
                            artifactId,
                            record.agentId(),
                            OperationKind.MEDIA_PROCESSING,
                            OperationStatus.ACCEPTED,
                            "Media transcription",
                            "Transcription requested",
                            principal,
                            request,
                            Map.of("languageCode", requestedLanguageCode, "jobId", jobId));

                        return eventEmitter.emitTranscriptionRequested(artifactId, tenantId, record.agentId(), requestedLanguageCode)
                            .map(offset -> ResponseBuilder.accepted()
                                .json(Map.of(
                                    "jobId", jobId,
                                    "operationId", operation == null ? "" : operation.operationId(),
                                    "artifactId", artifactId,
                                    "status", "pending",
                                    "message", "Transcription job accepted",
                                    "languageCode", requestedLanguageCode
                                ))
                                .build());
                    } catch (Exception e) {
                        log.warn("Invalid transcription request", e);
                        return Promise.of(ResponseBuilder.badRequest()
                            .json(Map.of("error", "Invalid request payload"))
                            .build());
                    }
                });
            });
    }

    private Promise<HttpResponse> triggerVisionAnalysis(String artifactId, String tenantId, Principal principal, HttpRequest request) {
        return repository.findById(artifactId, tenantId)
            .then(optional -> {
                if (optional.isEmpty()) {
                    return Promise.of(ResponseBuilder.notFound()
                        .json(Map.of("error", "Media artifact not found"))
                        .build());
                }

                MediaArtifactRecord record = optional.get();
                if (!record.mediaType().startsWith("image/") && !record.mediaType().startsWith("video/")) {
                    return Promise.of(ResponseBuilder.badRequest()
                        .json(Map.of("error", "Vision analysis is only supported for image or video artifacts"))
                        .build());
                }

                if (eventEmitter == null) {
                    OperationRecord operation = recordMediaOperation(
                        tenantId,
                        artifactId,
                        record.agentId(),
                        OperationKind.MEDIA_PROCESSING,
                        OperationStatus.BLOCKED,
                        "Media vision analysis",
                        "Vision analysis runtime is not configured",
                        principal,
                        request,
                        Map.of("operation", "vision-analysis"));
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("error", "Media vision analysis runtime is not configured");
                    response.put("artifactId", artifactId);
                    response.put("status", "blocked");
                    if (operation != null) {
                        response.put("operationId", operation.operationId());
                    }
                    return Promise.of(ResponseBuilder.serviceUnavailable().json(response).build());
                }

                return request.loadBody().then(body -> {
                    try {
                        String analysisType = "OBJECT_DETECTION";
                        if (body != null && body.asArray().length > 0) {
                            Map<String, String> payload = objectMapper.readValue(
                                body.asArray(),
                                objectMapper.getTypeFactory().constructMapType(Map.class, String.class, String.class));
                            if (payload.containsKey("analysisType")) {
                                analysisType = payload.get("analysisType");
                            }
                        }

                        String jobId = "vision-" + artifactId + "-" + System.currentTimeMillis();
                        String requestedAnalysisType = analysisType;
                        OperationRecord operation = recordMediaOperation(
                            tenantId,
                            artifactId,
                            record.agentId(),
                            OperationKind.MEDIA_PROCESSING,
                            OperationStatus.ACCEPTED,
                            "Media vision analysis",
                            "Vision analysis requested",
                            principal,
                            request,
                            Map.of("analysisType", requestedAnalysisType, "jobId", jobId));

                        return eventEmitter.emitProcessingRequested(artifactId, tenantId, record.agentId(), "vision-analysis", requestedAnalysisType)
                            .map(offset -> ResponseBuilder.accepted()
                                .json(Map.of(
                                    "jobId", jobId,
                                    "operationId", operation == null ? "" : operation.operationId(),
                                    "artifactId", artifactId,
                                    "status", "pending",
                                    "message", "Vision analysis job accepted",
                                    "analysisType", requestedAnalysisType
                                ))
                                .build());
                    } catch (Exception e) {
                        log.warn("Invalid vision analysis request", e);
                        return Promise.of(ResponseBuilder.badRequest()
                            .json(Map.of("error", "Invalid request payload"))
                            .build());
                    }
                });
            });
    }

    private int parseLimit(String limitValue) {
        if (limitValue == null || limitValue.isBlank()) {
            return 50;
        }
        try {
            int parsed = Integer.parseInt(limitValue.trim());
            return Math.max(1, Math.min(parsed, 200));
        } catch (NumberFormatException ignored) {
            return 50;
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean requiresExplicitConsent(String mediaType) {
        return mediaType.startsWith("audio/") || mediaType.startsWith("video/");
    }

    private static boolean hasAnyRole(Principal principal, String... roles) {
        for (String role : roles) {
            if (principal.hasRole(role)) {
                return true;
            }
        }
        return false;
    }

    private OperationRecord recordMediaOperation(
            String tenantId,
            String artifactId,
            String agentId,
            OperationKind kind,
            OperationStatus status,
            String action,
            String summary,
            Principal principal,
            HttpRequest request,
            Map<String, Object> metadata) {
        if (operationRecorder == null) {
            return null;
        }
        String correlationId = request.getHeader(io.activej.http.HttpHeaders.of("X-Correlation-ID"));
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = request.getHeader(io.activej.http.HttpHeaders.of("X-Request-ID"));
        }
        return operationRecorder.record(OperationRecord.create(
            tenantId,
            kind,
            status,
            "media-artifact",
            artifactId,
            action,
            summary,
            principal.getName(),
            correlationId,
            false,
            metadata == null ? Map.of() : metadata));
    }

    private Map<String, Object> toResponse(MediaArtifactRecord record) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("artifactId", record.artifactId());
        response.put("tenantId", record.tenantId());
        response.put("agentId", record.agentId());
        response.put("mediaType", record.mediaType());
        response.put("storageUri", record.storageUri());
        response.put("sizeBytes", record.sizeBytes());
        response.put("checksum", record.checksum() == null ? "" : record.checksum());
        response.put("durationMs", record.durationMs());
        response.put("originToolId", record.originToolId() == null ? "" : record.originToolId());
        response.put("correlationId", record.correlationId() == null ? "" : record.correlationId());
        response.put("metadata", sanitizeMetadata(record.metadata()));
        response.put("createdAt", record.createdAt().toString());
        return Map.copyOf(response);
    }

    private Map<String, Object> sanitizeMetadata(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            String key = entry.getKey().toLowerCase();
            // Redact sensitive keys
            if (key.contains("sensitive") || key.contains("secret") || key.contains("password") || key.contains("token")) {
                sanitized.put(entry.getKey(), "[REDACTED]");
            } else {
                sanitized.put(entry.getKey(), entry.getValue());
            }
        }
        return Map.copyOf(sanitized);
    }

    private record CreateMediaArtifactRequest(
        String agentId,
        String mediaType,
        String storageUri,
        Long sizeBytes,
        String checksum,
        Long durationMs,
        String originToolId,
        String correlationId,
        Map<String, String> metadata,
        String consentStatus,
        String retentionPolicy,
        String retentionUntil
    ) {
    }
}
