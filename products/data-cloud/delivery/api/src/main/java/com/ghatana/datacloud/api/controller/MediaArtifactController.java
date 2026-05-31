package com.ghatana.datacloud.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.memory.media.FrameIndex;
import com.ghatana.datacloud.memory.media.MediaArtifactEventEmitter;
import com.ghatana.datacloud.memory.media.MediaArtifactRecord;
import com.ghatana.datacloud.memory.media.MediaArtifactRepository;
import com.ghatana.datacloud.memory.media.MediaProcessingJob;
import com.ghatana.datacloud.memory.media.Transcript;
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

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.ghatana.datacloud.trace.CrossPlaneTrace;

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
    private static final String JOBS_SUFFIX = "/jobs";
    private static final String TRANSCRIPT_SUFFIX = "/transcript";
    private static final String FRAME_INDEX_SUFFIX = "/frame-index";
    private static final String RETRY_SUFFIX = "/retry";

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

        // Pass 6: Collection-level endpoints
        if (method == HttpMethod.POST && COLLECTION_PATH.equals(path)) {
            if (!hasPermission(principal, "media:artifact:create")) {
                return Promise.of(ResponseBuilder.forbidden()
                    .json(Map.of("error", "media:artifact:create permission is required"))
                    .build());
            }
            return createArtifact(request, tenantId, principal);
        }
        if (method == HttpMethod.GET && COLLECTION_PATH.equals(path)) {
            if (!hasPermission(principal, "media:artifact:read")) {
                return Promise.of(ResponseBuilder.forbidden()
                    .json(Map.of("error", "media:artifact:read permission is required"))
                    .build());
            }
            return listArtifacts(request, tenantId);
        }

        // Pass 6: Artifact-level endpoints
        if (path.startsWith(COLLECTION_PATH + "/")) {
            String remainingPath = path.substring((COLLECTION_PATH + "/").length());

            // Extract artifactId and sub-path
            String artifactId;
            String subPath;
            int slashIndex = remainingPath.indexOf('/');
            if (slashIndex > 0) {
                artifactId = remainingPath.substring(0, slashIndex);
                subPath = remainingPath.substring(slashIndex);
            } else {
                artifactId = remainingPath;
                subPath = "";
            }

            if (artifactId.isBlank()) {
                return Promise.of(ResponseBuilder.badRequest()
                    .json(Map.of("error", "artifactId path parameter is required"))
                    .build());
            }

            // Pass 6: Sub-resource endpoints
            if (subPath.equals(JOBS_SUFFIX)) {
                if (method == HttpMethod.GET && hasPermission(principal, "media:artifact:read")) {
                    return getJobs(artifactId, tenantId, principal, request);
                }
            }
            if (subPath.equals(TRANSCRIPT_SUFFIX)) {
                if (method == HttpMethod.GET && hasPermission(principal, "media:artifact:read")) {
                    return getTranscript(artifactId, tenantId, principal, request);
                }
            }
            if (subPath.equals(FRAME_INDEX_SUFFIX)) {
                if (method == HttpMethod.GET && hasPermission(principal, "media:artifact:read")) {
                    return getFrameIndex(artifactId, tenantId, principal, request);
                }
            }
            if (subPath.equals(RETRY_SUFFIX)) {
                if (method == HttpMethod.POST && hasPermission(principal, "media:artifact:process")) {
                    return retryProcessing(artifactId, tenantId, principal, request);
                }
            }
            if (subPath.equals(TRANSCRIPTION_SUFFIX)) {
                if (method == HttpMethod.POST) {
                    if (!hasPermission(principal, "media:artifact:process")) {
                        return Promise.of(ResponseBuilder.forbidden()
                            .json(Map.of("error", "media:artifact:process permission is required"))
                            .build());
                    }
                    return triggerTranscription(artifactId, tenantId, principal, request);
                }
            }
            if (subPath.equals(VISION_SUFFIX)) {
                if (method == HttpMethod.POST) {
                    if (!hasPermission(principal, "media:artifact:process")) {
                        return Promise.of(ResponseBuilder.forbidden()
                            .json(Map.of("error", "media:artifact:process permission is required"))
                            .build());
                    }
                    return triggerVisionAnalysis(artifactId, tenantId, principal, request);
                }
            }

            // Pass 6: Artifact resource endpoints
            if (subPath.isEmpty()) {
                if (method == HttpMethod.GET) {
                    if (!hasPermission(principal, "media:artifact:read")) {
                        return Promise.of(ResponseBuilder.forbidden()
                            .json(Map.of("error", "media:artifact:read permission is required"))
                            .build());
                    }
                    return getArtifact(artifactId, tenantId);
                }
                if (method == HttpMethod.DELETE) {
                    if (!hasPermission(principal, "media:artifact:delete")) {
                        return Promise.of(ResponseBuilder.forbidden()
                            .json(Map.of("error", "media:artifact:delete permission is required"))
                            .build());
                    }
                    return deleteArtifact(artifactId, tenantId, principal, request);
                }
                // Pass 6: Consent update endpoint
                if (method == HttpMethod.PATCH) {
                    if (!hasPermission(principal, "media:artifact:update")) {
                        return Promise.of(ResponseBuilder.forbidden()
                            .json(Map.of("error", "media:artifact:update permission is required"))
                            .build());
                    }
                    return updateConsent(artifactId, tenantId, principal, request);
                }
            }
        }

        return Promise.of(ResponseBuilder.notFound()
            .json(Map.of("error", "Endpoint not found"))
            .build());
    }

    private Promise<HttpResponse> createArtifact(HttpRequest request, String tenantId, Principal principal) {
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

                // Pass 6: Determine initial lifecycle state based on consent
                String processingState = MediaArtifactRecord.LIFECYCLE_REGISTERED;
                if (requiresExplicitConsent(mediaType)) {
                    processingState = consentStatus != null && consentStatus.equals(MediaArtifactRecord.CONSENT_GRANTED)
                        ? MediaArtifactRecord.LIFECYCLE_QUEUED
                        : MediaArtifactRecord.LIFECYCLE_CONSENT_PENDING;
                }

                // Pass 6: Create record with new fields
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
                    "ACTIVE",
                    processingState,
                    "INTERNAL",
                    consentStatus,
                    retentionPolicy,
                    null,
                    null,
                    agentId,
                    "media-artifact-service",
                    Map.of(),
                    Map.copyOf(metadata),
                    principal.getName());

                return repository.save(record)
                    .then(saved -> {
                        // Record operation with canonical permission context
                        OperationRecord operation = recordMediaOperation(
                            tenantId,
                            saved.artifactId(),
                            agentId,
                            OperationKind.MEDIA_PROCESSING,
                            OperationStatus.SUCCEEDED,
                            "Media artifact create",
                            "Media artifact created successfully",
                            principal,
                            request,
                            Map.of("permission", "media:artifact:create", "mediaType", mediaType, "operation", "create"));

                        Map<String, Object> response = toResponse(saved);
                        if (operation != null) {
                            response.put("operationId", operation.operationId());
                            response.put("traceId", operation.traceId());
                            response.put("requestId", operation.requestId());
                        }

                        if (eventEmitter != null) {
                            return eventEmitter.emitCreated(saved)
                                .map(offset -> ResponseBuilder.created().json(response).build());
                        }
                        return Promise.of(ResponseBuilder.created().json(response).build());
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

    private Promise<HttpResponse> deleteArtifact(String artifactId, String tenantId, Principal principal, HttpRequest request) {
        return repository.findById(artifactId, tenantId)
            .then(optional -> {
                if (optional.isEmpty()) {
                    return Promise.of(ResponseBuilder.notFound()
                        .json(Map.of("error", "Media artifact not found"))
                        .build());
                }

                MediaArtifactRecord record = optional.get();

                // Pass 6: Enforce retention policy
                if (!record.isRetentionPolicyValid()) {
                    OperationRecord operation = recordMediaOperation(
                        tenantId, artifactId, record.agentId(),
                        OperationKind.MEDIA_PROCESSING, OperationStatus.BLOCKED,
                        "Media artifact delete", "Retention policy prevents deletion",
                        principal, request, Map.of("retentionPolicy", record.retentionPolicy()));
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("error", "Retention policy prevents deletion of this artifact");
                    response.put("artifactId", artifactId);
                    response.put("status", "blocked");
                    if (operation != null) {
                        response.put("operationId", operation.operationId());
                        response.put("traceId", operation.traceId());
                        response.put("requestId", operation.requestId());
                    }
                    return Promise.of(ResponseBuilder.forbidden().json(response).build());
                }

                // Pass 6: Use soft delete (markDeleted) instead of hard delete
                return repository.markDeleted(artifactId, tenantId, principal.getName())
                    .then(deleted -> {
                        if (deleted) {
                            // Pass 6: Record operation and emit event
                            OperationRecord operation = recordMediaOperation(
                                tenantId, artifactId, record.agentId(),
                                OperationKind.MEDIA_PROCESSING, OperationStatus.SUCCEEDED,
                                "Media artifact delete", "Media artifact marked as deleted",
                                principal, request, Map.of("permission", "media:artifact:delete"));

                            if (eventEmitter != null) {
                                return eventEmitter.emitDeleted(artifactId, tenantId, record.agentId())
                                    .map(offset -> {
                                        Map<String, Object> response = new LinkedHashMap<>();
                                        response.put("artifactId", artifactId);
                                        response.put("status", "deleted");
                                        response.put("deletedAt", Instant.now().toString());
                                        if (operation != null) {
                                            response.put("operationId", operation.operationId());
                                        }
                                        return ResponseBuilder.ok().json(response).build();
                                    });
                            }
                            Map<String, Object> response = new LinkedHashMap<>();
                            response.put("artifactId", artifactId);
                            response.put("status", "deleted");
                            response.put("deletedAt", Instant.now().toString());
                            if (operation != null) {
                                response.put("operationId", operation.operationId());
                                response.put("traceId", operation.traceId());
                                response.put("requestId", operation.requestId());
                            }
                            return Promise.of(ResponseBuilder.ok().json(response).build());
                        }
                        return Promise.of(ResponseBuilder.notFound()
                            .json(Map.of("error", "Media artifact not found"))
                            .build());
                    });
            });
    }

    /**
     * Pass 6: Update consent status for a media artifact.
     */
    private Promise<HttpResponse> updateConsent(String artifactId, String tenantId, Principal principal, HttpRequest request) {
        return repository.findById(artifactId, tenantId)
            .then(optional -> {
                if (optional.isEmpty()) {
                    return Promise.of(ResponseBuilder.notFound()
                        .json(Map.of("error", "Media artifact not found"))
                        .build());
                }

                MediaArtifactRecord record = optional.get();

                return request.loadBody().then(body -> {
                    try {
                        Map<String, String> payload = objectMapper.readValue(
                            body != null ? body.asArray() : new byte[0],
                            objectMapper.getTypeFactory().constructMapType(Map.class, String.class, String.class));

                        String newConsentStatus = payload.get("consentStatus");
                        if (newConsentStatus == null || !isValidConsentStatus(newConsentStatus)) {
                            return Promise.of(ResponseBuilder.badRequest()
                                .json(Map.of("error", "Valid consentStatus is required (GRANTED, PENDING, DENIED, NOT_REQUIRED)"))
                                .build());
                        }

                        // Update consent status
                        return repository.updateConsentStatus(artifactId, tenantId, newConsentStatus, principal.getName())
                            .then(updated -> {
                                if (!updated) {
                                    return Promise.of(ResponseBuilder.notFound()
                                        .json(Map.of("error", "Media artifact not found"))
                                        .build());
                                }

                                // Record operation
                                OperationRecord operation = recordMediaOperation(
                                    tenantId, artifactId, record.agentId(),
                                    OperationKind.MEDIA_PROCESSING, OperationStatus.SUCCEEDED,
                                    "Media artifact consent update", "Consent status updated to " + newConsentStatus,
                                    principal, request, Map.of("consentStatus", newConsentStatus));

                                // Emit event
                                if (eventEmitter != null) {
                                    return eventEmitter.emitUpdated(artifactId, tenantId, record.agentId(), "consent", newConsentStatus)
                                        .map(offset -> {
                                            Map<String, Object> response = new LinkedHashMap<>();
                                            response.put("artifactId", artifactId);
                                            response.put("consentStatus", newConsentStatus);
                                            response.put("updatedAt", Instant.now().toString());
                                            if (operation != null) {
                                                response.put("operationId", operation.operationId());
                                            }
                                            return ResponseBuilder.ok().json(response).build();
                                        });
                                }

                                Map<String, Object> response = new LinkedHashMap<>();
                                response.put("artifactId", artifactId);
                                response.put("consentStatus", newConsentStatus);
                                response.put("updatedAt", Instant.now().toString());
                                if (operation != null) {
                                    response.put("operationId", operation.operationId());
                                    response.put("traceId", operation.traceId());
                                    response.put("requestId", operation.requestId());
                                }
                                return Promise.of(ResponseBuilder.ok().json(response).build());
                            });
                    } catch (Exception e) {
                        log.warn("Invalid consent update request", e);
                        return Promise.of(ResponseBuilder.badRequest()
                            .json(Map.of("error", "Invalid request payload"))
                            .build());
                    }
                });
            });
    }

    private boolean isValidConsentStatus(String status) {
        return MediaArtifactRecord.CONSENT_GRANTED.equals(status)
            || MediaArtifactRecord.CONSENT_PENDING.equals(status)
            || MediaArtifactRecord.CONSENT_DENIED.equals(status)
            || MediaArtifactRecord.CONSENT_NOT_REQUIRED.equals(status);
    }

    /**
     * Pass 6: Get processing jobs for a media artifact.
     */
    private Promise<HttpResponse> getJobs(String artifactId, String tenantId, Principal principal, HttpRequest request) {
        return repository.findById(artifactId, tenantId)
            .then(optional -> {
                if (optional.isEmpty()) {
                    return Promise.of(ResponseBuilder.notFound()
                        .json(Map.of("error", "Media artifact not found"))
                        .build());
                }

                return repository.findJobs(artifactId, tenantId)
                    .map(jobs -> {
                        Map<String, Object> response = new LinkedHashMap<>();
                        response.put("artifactId", artifactId);
                        response.put("jobs", jobs.stream().map(this::toJobResponse).toList());
                        response.put("count", jobs.size());
                        return ResponseBuilder.ok().json(response).build();
                    });
            });
    }

    /**
     * Pass 6: Get transcript for a media artifact.
     */
    private Promise<HttpResponse> getTranscript(String artifactId, String tenantId, Principal principal, HttpRequest request) {
        return repository.findById(artifactId, tenantId)
            .then(optional -> {
                if (optional.isEmpty()) {
                    return Promise.of(ResponseBuilder.notFound()
                        .json(Map.of("error", "Media artifact not found"))
                        .build());
                }

                MediaArtifactRecord record = optional.get();
                if (record.transcriptId() == null) {
                    return Promise.of(ResponseBuilder.notFound()
                        .json(Map.of("error", "Transcript not available for this artifact"))
                        .build());
                }

                return repository.findTranscript(artifactId, tenantId)
                    .map(optionalTranscript -> optionalTranscript
                        .map(this::toTranscriptResponse)
                        .map(transcript -> ResponseBuilder.ok().json(transcript).build())
                        .orElseGet(() -> ResponseBuilder.notFound()
                            .json(Map.of("error", "Transcript not found"))
                            .build()));
            });
    }

    /**
     * Pass 6: Get frame index for a media artifact.
     */
    private Promise<HttpResponse> getFrameIndex(String artifactId, String tenantId, Principal principal, HttpRequest request) {
        return repository.findById(artifactId, tenantId)
            .then(optional -> {
                if (optional.isEmpty()) {
                    return Promise.of(ResponseBuilder.notFound()
                        .json(Map.of("error", "Media artifact not found"))
                        .build());
                }

                MediaArtifactRecord record = optional.get();
                if (record.frameIndexId() == null) {
                    return Promise.of(ResponseBuilder.notFound()
                        .json(Map.of("error", "Frame index not available for this artifact"))
                        .build());
                }

                return repository.findFrameIndex(artifactId, tenantId)
                    .map(optionalFrameIndex -> optionalFrameIndex
                        .map(this::toFrameIndexResponse)
                        .map(frameIndex -> ResponseBuilder.ok().json(frameIndex).build())
                        .orElseGet(() -> ResponseBuilder.notFound()
                            .json(Map.of("error", "Frame index not found"))
                            .build()));
            });
    }

    /**
     * Pass 6: Retry processing for a failed media artifact.
     */
    private Promise<HttpResponse> retryProcessing(String artifactId, String tenantId, Principal principal, HttpRequest request) {
        return repository.findById(artifactId, tenantId)
            .then(optional -> {
                if (optional.isEmpty()) {
                    return Promise.of(ResponseBuilder.notFound()
                        .json(Map.of("error", "Media artifact not found"))
                        .build());
                }

                MediaArtifactRecord record = optional.get();

                // Pass 6: Only retry failed artifacts
                if (!MediaArtifactRecord.LIFECYCLE_FAILED.equals(record.processingState())) {
                    return Promise.of(ResponseBuilder.badRequest()
                        .json(Map.of("error", "Only failed artifacts can be retried. Current state: " + record.processingState()))
                        .build());
                }

                // Pass 6: Enforce consent
                if (!record.hasConsentForProcessing()) {
                    OperationRecord operation = recordMediaOperation(
                        tenantId, artifactId, record.agentId(),
                        OperationKind.MEDIA_PROCESSING, OperationStatus.BLOCKED,
                        "Media processing retry", "Consent required for processing",
                        principal, request, Map.of("consentStatus", record.consentStatus()));
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("error", "Consent required before retrying processing");
                    response.put("artifactId", artifactId);
                    response.put("consentStatus", record.consentStatus());
                    response.put("status", "blocked");
                    if (operation != null) {
                        response.put("operationId", operation.operationId());
                        response.put("traceId", operation.traceId());
                        response.put("requestId", operation.requestId());
                    }
                    return Promise.of(ResponseBuilder.forbidden().json(response).build());
                }

                // Clear error and reset state
                return repository.updateLastError(artifactId, tenantId, null, principal.getName())
                    .then(cleared -> repository.updateProcessingState(artifactId, tenantId, MediaArtifactRecord.LIFECYCLE_QUEUED))
                    .then(updated -> {
                        String jobId = "retry-" + artifactId + "-" + System.currentTimeMillis();
                        OperationRecord operation = recordMediaOperation(
                            tenantId, artifactId, record.agentId(),
                            OperationKind.MEDIA_PROCESSING, OperationStatus.ACCEPTED,
                            "Media processing retry", "Processing retry initiated",
                            principal, request, Map.of("jobId", jobId));

                        if (eventEmitter != null) {
                            return eventEmitter.emitProcessingRequested(artifactId, tenantId, record.agentId(), "retry", "auto")
                                .map(offset -> ResponseBuilder.accepted()
                                    .json(Map.of(
                                        "jobId", jobId,
                                        "operationId", operation == null ? "" : operation.operationId(),
                                        "traceId", operation == null ? "" : operation.traceId(),
                                        "requestId", operation == null ? "" : operation.requestId(),
                                        "artifactId", artifactId,
                                        "status", "queued",
                                        "message", "Processing retry accepted"
                                    ))
                                    .build());
                        }

                        return Promise.of(ResponseBuilder.accepted()
                            .json(Map.of(
                                "jobId", jobId,
                                "operationId", operation == null ? "" : operation.operationId(),
                                "traceId", operation == null ? "" : operation.traceId(),
                                "requestId", operation == null ? "" : operation.requestId(),
                                "artifactId", artifactId,
                                "status", "queued",
                                "message", "Processing retry accepted"
                            ))
                            .build());
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

                // Pass 6: Enforce consent
                if (!record.hasConsentForProcessing()) {
                    OperationRecord operation = recordMediaOperation(
                        tenantId, artifactId, record.agentId(),
                        OperationKind.MEDIA_PROCESSING, OperationStatus.BLOCKED,
                        "Media transcription", "Consent required for transcription",
                        principal, request, Map.of("consentStatus", record.consentStatus()));
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("error", "Consent required before transcription");
                    response.put("artifactId", artifactId);
                    response.put("consentStatus", record.consentStatus());
                    response.put("status", "blocked");
                    if (operation != null) {
                        response.put("operationId", operation.operationId());
                        response.put("traceId", operation.traceId());
                        response.put("requestId", operation.requestId());
                    }
                    return Promise.of(ResponseBuilder.forbidden().json(response).build());
                }

                // Pass 6: Enforce retention policy
                if (!record.isRetentionPolicyValid()) {
                    OperationRecord operation = recordMediaOperation(
                        tenantId, artifactId, record.agentId(),
                        OperationKind.MEDIA_PROCESSING, OperationStatus.BLOCKED,
                        "Media transcription", "Retention policy expired",
                        principal, request, Map.of("retentionPolicy", record.retentionPolicy()));
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("error", "Retention policy expired - transcription blocked");
                    response.put("artifactId", artifactId);
                    response.put("status", "blocked");
                    if (operation != null) {
                        response.put("operationId", operation.operationId());
                        response.put("traceId", operation.traceId());
                        response.put("requestId", operation.requestId());
                    }
                    return Promise.of(ResponseBuilder.forbidden().json(response).build());
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

                        // Pass 6: Update processing state and job ID
                        repository.updateProcessingJobId(artifactId, tenantId, jobId, principal.getName());
                        repository.updateProcessingState(artifactId, tenantId, MediaArtifactRecord.LIFECYCLE_PROCESSING);

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
                                    "traceId", operation == null ? "" : operation.traceId(),
                                    "requestId", operation == null ? "" : operation.requestId(),
                                    "artifactId", artifactId,
                                    "status", MediaArtifactRecord.LIFECYCLE_PROCESSING,
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

                // Pass 6: Enforce consent
                if (!record.hasConsentForProcessing()) {
                    OperationRecord operation = recordMediaOperation(
                        tenantId, artifactId, record.agentId(),
                        OperationKind.MEDIA_PROCESSING, OperationStatus.BLOCKED,
                        "Media vision analysis", "Consent required for vision analysis",
                        principal, request, Map.of("consentStatus", record.consentStatus()));
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("error", "Consent required before vision analysis");
                    response.put("artifactId", artifactId);
                    response.put("consentStatus", record.consentStatus());
                    response.put("status", "blocked");
                    if (operation != null) {
                        response.put("operationId", operation.operationId());
                        response.put("traceId", operation.traceId());
                        response.put("requestId", operation.requestId());
                    }
                    return Promise.of(ResponseBuilder.forbidden().json(response).build());
                }

                // Pass 6: Enforce retention policy
                if (!record.isRetentionPolicyValid()) {
                    OperationRecord operation = recordMediaOperation(
                        tenantId, artifactId, record.agentId(),
                        OperationKind.MEDIA_PROCESSING, OperationStatus.BLOCKED,
                        "Media vision analysis", "Retention policy expired",
                        principal, request, Map.of("retentionPolicy", record.retentionPolicy()));
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("error", "Retention policy expired - vision analysis blocked");
                    response.put("artifactId", artifactId);
                    response.put("status", "blocked");
                    if (operation != null) {
                        response.put("operationId", operation.operationId());
                        response.put("traceId", operation.traceId());
                        response.put("requestId", operation.requestId());
                    }
                    return Promise.of(ResponseBuilder.forbidden().json(response).build());
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

                        // Pass 6: Update processing state and job ID
                        repository.updateProcessingJobId(artifactId, tenantId, jobId, principal.getName());
                        repository.updateProcessingState(artifactId, tenantId, MediaArtifactRecord.LIFECYCLE_PROCESSING);

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
                                    "traceId", operation == null ? "" : operation.traceId(),
                                    "requestId", operation == null ? "" : operation.requestId(),
                                    "artifactId", artifactId,
                                    "status", MediaArtifactRecord.LIFECYCLE_PROCESSING,
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

    /**
     * Checks if the principal has the specified canonical permission.
     * Permissions are derived from the Principal's roles using canonical permission mapping.
     */
    private static boolean hasPermission(Principal principal, String permission) {
        if (principal == null || permission == null) {
            return false;
        }
        // Derive permissions from roles using canonical permission mapping
        return hasPermissionFromRoles(principal, permission);
    }

    /**
     * Derives permissions from Principal roles using canonical permission registry.
     * This aligns with DataCloudPermissionRegistry for consistent permission checking.
     */
    private static boolean hasPermissionFromRoles(Principal principal, String permission) {
        if (principal == null || principal.getRoles() == null || permission == null) {
            return false;
        }

        // Canonical permission mapping matching DataCloudPermissionRegistry
        // ADMIN/PLATFORM_ADMIN have all media permissions
        for (String role : principal.getRoles()) {
            String normalizedRole = role == null ? "" : role.trim().toUpperCase();
            boolean hasPermission = switch (normalizedRole) {
                case "ADMIN", "PLATFORM_ADMIN" -> true; // Admin has all permissions
                case "OPERATOR" -> permission.equals("media:artifact:read")
                    || permission.equals("media:artifact:process");
                case "EDITOR" -> permission.equals("media:artifact:create")
                    || permission.equals("media:artifact:read")
                    || permission.equals("media:artifact:process");
                case "VIEWER" -> permission.equals("media:artifact:read");
                case "PROCESSOR" -> permission.equals("media:artifact:process");
                default -> false;
            };
            if (hasPermission) {
                return true;
            }
        }
        return false;
    }

    /**
     * @deprecated Use hasPermission() for canonical permission checking
     */
    @Deprecated
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
        String traceId = request.getHeader(io.activej.http.HttpHeaders.of("X-Trace-ID"));
        if (traceId == null || traceId.isBlank()) {
            traceId = request.getHeader(io.activej.http.HttpHeaders.of("X-Correlation-ID"));
        }
        if (traceId == null || traceId.isBlank()) {
            traceId = request.getHeader(io.activej.http.HttpHeaders.of("X-Request-ID"));
        }
        String requestId = request.getHeader(io.activej.http.HttpHeaders.of("X-Request-ID"));
        if (requestId == null || requestId.isBlank()) {
            requestId = traceId;
        }
        String correlationId = request.getHeader(io.activej.http.HttpHeaders.of("X-Correlation-ID"));
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = requestId;
        }
        return operationRecorder.record(OperationRecord.create(
            tenantId,
            traceId,
            requestId,
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
        response.put("status", record.status());
        // Pass 6: Include lifecycle fields
        response.put("processingState", record.processingState());
        response.put("consentStatus", record.consentStatus());
        response.put("retentionPolicy", record.retentionPolicy());
        response.put("createdBy", record.createdBy());
        response.put("updatedBy", record.updatedBy());
        response.put("createdAt", record.createdAt().toString());
        response.put("updatedAt", record.updatedAt() != null ? record.updatedAt().toString() : null);
        response.put("deletedAt", record.deletedAt() != null ? record.deletedAt().toString() : null);
        response.put("transcriptId", record.transcriptId());
        response.put("frameIndexId", record.frameIndexId());
        response.put("processingJobId", record.processingJobId());
        response.put("lastError", record.lastError());
        response.put("canBeProcessed", record.canBeProcessed());
        response.put("requiresConsent", record.requiresExplicitConsent());
        response.put("metadata", sanitizeMetadata(record.metadata()));
        return Map.copyOf(response);
    }

    /**
     * Pass 6: Convert MediaProcessingJob to response map.
     */
    private Map<String, Object> toJobResponse(MediaProcessingJob job) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jobId", job.jobId());
        response.put("artifactId", job.artifactId());
        response.put("jobType", job.jobType());
        response.put("status", job.status());
        response.put("parameters", job.parameters());
        response.put("resultId", job.resultId());
        response.put("errorMessage", job.errorMessage());
        response.put("progress", job.progress());
        response.put("createdAt", job.createdAt().toString());
        response.put("startedAt", job.startedAt() != null ? job.startedAt().toString() : null);
        response.put("completedAt", job.completedAt() != null ? job.completedAt().toString() : null);
        response.put("isTerminal", job.isTerminal());
        response.put("isSuccessful", job.isSuccessful());
        return Map.copyOf(response);
    }

    /**
     * Pass 6: Convert Transcript to response map.
     */
    private Map<String, Object> toTranscriptResponse(Transcript transcript) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("transcriptId", transcript.transcriptId());
        response.put("artifactId", transcript.artifactId());
        response.put("languageCode", transcript.languageCode());
        response.put("confidence", transcript.confidence());
        response.put("durationMs", transcript.durationMs());
        response.put("wordCount", transcript.wordCount());
        response.put("speakerCount", transcript.speakerCount());
        response.put("fullText", transcript.fullText());
        response.put("segments", transcript.segments().stream()
            .map(s -> Map.of(
                "segmentId", s.segmentId(),
                "startMs", s.startMs(),
                "endMs", s.endMs(),
                "speakerId", s.speakerId(),
                "text", s.text(),
                "confidence", s.confidence()
            ))
            .toList());
        response.put("createdAt", transcript.createdAt().toString());
        return Map.copyOf(response);
    }

    /**
     * Pass 6: Convert FrameIndex to response map.
     */
    private Map<String, Object> toFrameIndexResponse(FrameIndex frameIndex) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("frameIndexId", frameIndex.frameIndexId());
        response.put("artifactId", frameIndex.artifactId());
        response.put("analysisType", frameIndex.analysisType());
        response.put("confidence", frameIndex.confidence());
        response.put("frameCount", frameIndex.frameCount());
        response.put("durationMs", frameIndex.durationMs());
        response.put("labels", frameIndex.labels().stream()
            .map(l -> Map.of(
                "label", l.label(),
                "occurrenceCount", l.occurrenceCount(),
                "avgConfidence", l.avgConfidence()
            ))
            .toList());
        response.put("events", frameIndex.events().stream()
            .map(e -> Map.of(
                "eventType", e.eventType(),
                "startMs", e.startMs(),
                "endMs", e.endMs(),
                "description", e.description(),
                "confidence", e.confidence()
            ))
            .toList());
        response.put("createdAt", frameIndex.createdAt().toString());
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

    /**
     * Pass 9: Generates a unique trace ID for cross-plane tracing.
     */
    private String generateTraceId() {
        return "trc-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * Pass 9: Generates a span ID for tracing.
     */
    private String generateSpanId() {
        return "spn-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
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
