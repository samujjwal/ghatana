package com.ghatana.datacloud.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.launcher.http.handlers.HttpHandlerSupport;
import com.ghatana.datacloud.memory.media.FrameIndex;
import com.ghatana.datacloud.memory.media.MediaArtifactRecord;
import com.ghatana.datacloud.memory.media.MediaArtifactService;
import com.ghatana.datacloud.memory.media.MediaProcessingJob;
import com.ghatana.datacloud.memory.media.Transcript;
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
import java.util.Optional;

/**
 * REST controller for Data Cloud media artifact metadata.
 *
 * <p>WS3: Refactored to delegate business logic to {@link MediaArtifactService}.
 * This controller now only handles HTTP concerns (request parsing, response building)
 * and uses {@link HttpHandlerSupport} for canonical permission checking.
 *
 * <p>Tenant identity is resolved from the authenticated {@link Principal}
 * attached by the security filter. The controller does not read spoofable
 * tenant headers or query parameters.
 *
 * <p>Event emission and operation recording are mandatory - the service requires
 * both dependencies and all mutating operations emit canonical events.
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

    private final MediaArtifactService service;
    private final ObjectMapper objectMapper;
    private final HttpHandlerSupport httpSupport;

    /**
     * Creates a new MediaArtifactController with mandatory service and HTTP support.
     *
     * @param service the media artifact service (mandatory, includes event emission and operation recording)
     * @param objectMapper the JSON object mapper
     * @param httpSupport the HTTP handler support for permission checking
     */
    public MediaArtifactController(
            MediaArtifactService service,
            ObjectMapper objectMapper,
            HttpHandlerSupport httpSupport) {
        this.service = service;
        this.objectMapper = objectMapper;
        this.httpSupport = httpSupport;
        log.info("[media-artifact] Controller initialized with mandatory service and HTTP support");
    }

    public Promise<HttpResponse> handle(HttpRequest request) {
        // Use HttpHandlerSupport for canonical permission checking
        var permissionResult = httpSupport.resolveRequestContextWithError(request);
        if (!permissionResult.isSuccess()) {
            return Promise.of(httpSupport.errorResponse(
                permissionResult.errorCode(),
                permissionResult.errorMessage()
            ));
        }

        Principal principal = request.getAttachment(Principal.class);
        String tenantId = principal.getTenantId();
        String path = request.getPath();
        HttpMethod method = request.getMethod();

        // Collection-level endpoints
        if (method == HttpMethod.POST && COLLECTION_PATH.equals(path)) {
            var createResult = httpSupport.requirePermission(request, "media:artifact:create");
            if (!createResult.isSuccess()) {
                return Promise.of(httpSupport.errorResponse(
                    createResult.errorCode(),
                    createResult.errorMessage()
                ));
            }
            return createArtifact(request, tenantId, principal);
        }
        if (method == HttpMethod.GET && COLLECTION_PATH.equals(path)) {
            var readResult = httpSupport.requirePermission(request, "media:artifact:read");
            if (!readResult.isSuccess()) {
                return Promise.of(httpSupport.errorResponse(
                    readResult.errorCode(),
                    readResult.errorMessage()
                ));
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

            // Sub-resource endpoints
            if (subPath.equals(JOBS_SUFFIX)) {
                if (method == HttpMethod.GET) {
                    var readResult = httpSupport.requirePermission(request, "media:artifact:read");
                    if (!readResult.isSuccess()) {
                        return Promise.of(httpSupport.errorResponse(
                            readResult.errorCode(),
                            readResult.errorMessage()
                        ));
                    }
                    return getJobs(artifactId, tenantId, principal, request);
                }
            }
            if (subPath.equals(TRANSCRIPT_SUFFIX)) {
                if (method == HttpMethod.GET) {
                    var readResult = httpSupport.requirePermission(request, "media:artifact:read");
                    if (!readResult.isSuccess()) {
                        return Promise.of(httpSupport.errorResponse(
                            readResult.errorCode(),
                            readResult.errorMessage()
                        ));
                    }
                    return getTranscript(artifactId, tenantId, principal, request);
                }
            }
            if (subPath.equals(FRAME_INDEX_SUFFIX)) {
                if (method == HttpMethod.GET) {
                    var readResult = httpSupport.requirePermission(request, "media:artifact:read");
                    if (!readResult.isSuccess()) {
                        return Promise.of(httpSupport.errorResponse(
                            readResult.errorCode(),
                            readResult.errorMessage()
                        ));
                    }
                    return getFrameIndex(artifactId, tenantId, principal, request);
                }
            }
            if (subPath.equals(RETRY_SUFFIX)) {
                if (method == HttpMethod.POST) {
                    var processResult = httpSupport.requirePermission(request, "media:artifact:process");
                    if (!processResult.isSuccess()) {
                        return Promise.of(httpSupport.errorResponse(
                            processResult.errorCode(),
                            processResult.errorMessage()
                        ));
                    }
                    return retryProcessing(artifactId, tenantId, principal, request);
                }
            }
            if (subPath.equals(TRANSCRIPTION_SUFFIX)) {
                if (method == HttpMethod.POST) {
                    var processResult = httpSupport.requirePermission(request, "media:artifact:process");
                    if (!processResult.isSuccess()) {
                        return Promise.of(httpSupport.errorResponse(
                            processResult.errorCode(),
                            processResult.errorMessage()
                        ));
                    }
                    return triggerTranscription(artifactId, tenantId, principal, request);
                }
            }
            if (subPath.equals(VISION_SUFFIX)) {
                if (method == HttpMethod.POST) {
                    var processResult = httpSupport.requirePermission(request, "media:artifact:process");
                    if (!processResult.isSuccess()) {
                        return Promise.of(httpSupport.errorResponse(
                            processResult.errorCode(),
                            processResult.errorMessage()
                        ));
                    }
                    return triggerVisionAnalysis(artifactId, tenantId, principal, request);
                }
            }

            // Artifact resource endpoints
            if (subPath.isEmpty()) {
                if (method == HttpMethod.GET) {
                    var readResult = httpSupport.requirePermission(request, "media:artifact:read");
                    if (!readResult.isSuccess()) {
                        return Promise.of(httpSupport.errorResponse(
                            readResult.errorCode(),
                            readResult.errorMessage()
                        ));
                    }
                    return getArtifact(artifactId, tenantId);
                }
                if (method == HttpMethod.DELETE) {
                    var deleteResult = httpSupport.requirePermission(request, "media:artifact:delete");
                    if (!deleteResult.isSuccess()) {
                        return Promise.of(httpSupport.errorResponse(
                            deleteResult.errorCode(),
                            deleteResult.errorMessage()
                        ));
                    }
                    return deleteArtifact(artifactId, tenantId, principal, request);
                }
                // Consent update endpoint
                if (method == HttpMethod.PATCH) {
                    var updateResult = httpSupport.requirePermission(request, "media:artifact:update");
                    if (!updateResult.isSuccess()) {
                        return Promise.of(httpSupport.errorResponse(
                            updateResult.errorCode(),
                            updateResult.errorMessage()
                        ));
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

                // Determine initial lifecycle state based on consent
                String processingState = MediaArtifactRecord.LIFECYCLE_REGISTERED;
                if (requiresExplicitConsent(mediaType)) {
                    processingState = consentStatus != null && consentStatus.equals(MediaArtifactRecord.CONSENT_GRANTED)
                        ? MediaArtifactRecord.LIFECYCLE_QUEUED
                        : MediaArtifactRecord.LIFECYCLE_CONSENT_PENDING;
                }

                // Create record with new fields
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

                // Delegate to service (handles event emission and operation recording)
                return service.createArtifact(record, principal, request)
                    .map(saved -> ResponseBuilder.created().json(toResponse(saved)).build());
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

        // Delegate to service
        return service.listArtifacts(agentId, mediaType, tenantId, limit)
            .map(records -> ResponseBuilder.ok()
                .json(Map.of("items", records.stream().map(this::toResponse).toList(), "count", records.size()))
                .build());
    }

    private Promise<HttpResponse> getArtifact(String artifactId, String tenantId) {
        // Delegate to service
        return service.getArtifact(artifactId, tenantId)
            .map(optional -> optional
                .map(record -> ResponseBuilder.ok().json(toResponse(record)).build())
                .orElseGet(() -> ResponseBuilder.notFound()
                    .json(Map.of("error", "Media artifact not found"))
                    .build()));
    }

    private Promise<HttpResponse> deleteArtifact(String artifactId, String tenantId, Principal principal, HttpRequest request) {
        // Delegate to service (handles retention policy, event emission, operation recording)
        return service.deleteArtifact(artifactId, tenantId, principal, request)
            .then(deleted -> {
                if (deleted) {
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("artifactId", artifactId);
                    response.put("status", "deleted");
                    response.put("deletedAt", java.time.Instant.now().toString());
                    return Promise.of(ResponseBuilder.ok().json(response).build());
                }
                return Promise.of(ResponseBuilder.notFound()
                    .json(Map.of("error", "Media artifact not found"))
                    .build());
            });
    }

    /**
     * Update consent status for a media artifact.
     */
    private Promise<HttpResponse> updateConsent(String artifactId, String tenantId, Principal principal, HttpRequest request) {
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

                // Delegate to service (handles event emission and operation recording)
                return service.updateConsentStatus(artifactId, tenantId, newConsentStatus, principal, request)
                    .then(updated -> {
                        if (updated) {
                            Map<String, Object> response = new LinkedHashMap<>();
                            response.put("artifactId", artifactId);
                            response.put("consentStatus", newConsentStatus);
                            response.put("updatedAt", java.time.Instant.now().toString());
                            return Promise.of(ResponseBuilder.ok().json(response).build());
                        }
                        return Promise.of(ResponseBuilder.notFound()
                            .json(Map.of("error", "Media artifact not found"))
                            .build());
                    });
            } catch (Exception e) {
                log.warn("Invalid consent update request", e);
                return Promise.of(ResponseBuilder.badRequest()
                    .json(Map.of("error", "Invalid request payload"))
                    .build());
            }
        });
    }

    private boolean isValidConsentStatus(String status) {
        return MediaArtifactRecord.CONSENT_GRANTED.equals(status)
            || MediaArtifactRecord.CONSENT_PENDING.equals(status)
            || MediaArtifactRecord.CONSENT_DENIED.equals(status)
            || MediaArtifactRecord.CONSENT_NOT_REQUIRED.equals(status);
    }

    /**
     * Get processing jobs for a media artifact.
     */
    private Promise<HttpResponse> getJobs(String artifactId, String tenantId, Principal principal, HttpRequest request) {
        // Delegate to service
        return service.getJobs(artifactId, tenantId)
            .map(jobs -> {
                Map<String, Object> response = new LinkedHashMap<>();
                response.put("artifactId", artifactId);
                response.put("jobs", jobs.stream().map(this::toJobResponse).toList());
                response.put("count", jobs.size());
                return ResponseBuilder.ok().json(response).build();
            });
    }

    /**
     * Get transcript for a media artifact.
     */
    private Promise<HttpResponse> getTranscript(String artifactId, String tenantId, Principal principal, HttpRequest request) {
        // Delegate to service
        return service.getTranscript(artifactId, tenantId)
            .map(optionalTranscript -> optionalTranscript
                .map(this::toTranscriptResponse)
                .map(transcript -> ResponseBuilder.ok().json(transcript).build())
                .orElseGet(() -> ResponseBuilder.notFound()
                    .json(Map.of("error", "Transcript not found"))
                    .build()));
    }

    /**
     * Get frame index for a media artifact.
     */
    private Promise<HttpResponse> getFrameIndex(String artifactId, String tenantId, Principal principal, HttpRequest request) {
        // Delegate to service
        return service.getFrameIndex(artifactId, tenantId)
            .map(optionalFrameIndex -> optionalFrameIndex
                .map(this::toFrameIndexResponse)
                .map(frameIndex -> ResponseBuilder.ok().json(frameIndex).build())
                .orElseGet(() -> ResponseBuilder.notFound()
                    .json(Map.of("error", "Frame index not found"))
                    .build()));
    }

    /**
     * Retry processing for a failed media artifact.
     */
    private Promise<HttpResponse> retryProcessing(String artifactId, String tenantId, Principal principal, HttpRequest request) {
        // Delegate to service (handles consent enforcement, event emission, operation recording)
        return service.retryProcessing(artifactId, tenantId, principal, request)
            .then(success -> {
                if (success) {
                    String jobId = "retry-" + artifactId + "-" + System.currentTimeMillis();
                    return Promise.of(ResponseBuilder.accepted()
                        .json(Map.of(
                            "jobId", jobId,
                            "artifactId", artifactId,
                            "status", "queued",
                            "message", "Processing retry accepted"
                        ))
                        .build());
                }
                return Promise.of(ResponseBuilder.badRequest()
                    .json(Map.of("error", "Failed to retry processing - check artifact state and consent"))
                    .build());
            });
    }

    private Promise<HttpResponse> triggerTranscription(String artifactId, String tenantId, Principal principal, HttpRequest request) {
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

                // Delegate to service (handles consent enforcement, event emission, operation recording)
                return service.triggerTranscription(artifactId, tenantId, languageCode, principal, request)
                    .then(success -> {
                        if (success) {
                            String jobId = "transcription-" + artifactId + "-" + System.currentTimeMillis();
                            return Promise.of(ResponseBuilder.accepted()
                                .json(Map.of(
                                    "jobId", jobId,
                                    "artifactId", artifactId,
                                    "status", MediaArtifactRecord.LIFECYCLE_PROCESSING,
                                    "message", "Transcription job accepted",
                                    "languageCode", languageCode
                                ))
                                .build());
                        }
                        return Promise.of(ResponseBuilder.forbidden()
                            .json(Map.of("error", "Transcription blocked - check consent and retention policy"))
                            .build());
                    });
            } catch (Exception e) {
                log.warn("Invalid transcription request", e);
                return Promise.of(ResponseBuilder.badRequest()
                    .json(Map.of("error", "Invalid request payload"))
                    .build());
            }
        });
    }

    private Promise<HttpResponse> triggerVisionAnalysis(String artifactId, String tenantId, Principal principal, HttpRequest request) {
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

                // Delegate to service (handles consent enforcement, event emission, operation recording)
                return service.triggerVisionAnalysis(artifactId, tenantId, analysisType, principal, request)
                    .then(success -> {
                        if (success) {
                            String jobId = "vision-" + artifactId + "-" + System.currentTimeMillis();
                            return Promise.of(ResponseBuilder.accepted()
                                .json(Map.of(
                                    "jobId", jobId,
                                    "artifactId", artifactId,
                                    "status", MediaArtifactRecord.LIFECYCLE_PROCESSING,
                                    "message", "Vision analysis job accepted",
                                    "analysisType", analysisType
                                ))
                                .build());
                        }
                        return Promise.of(ResponseBuilder.forbidden()
                            .json(Map.of("error", "Vision analysis blocked - check consent and retention policy"))
                            .build());
                    });
            } catch (Exception e) {
                log.warn("Invalid vision analysis request", e);
                return Promise.of(ResponseBuilder.badRequest()
                    .json(Map.of("error", "Invalid request payload"))
                    .build());
            }
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
