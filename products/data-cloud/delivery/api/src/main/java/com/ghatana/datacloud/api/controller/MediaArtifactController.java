package com.ghatana.datacloud.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.memory.media.FrameIndex;
import com.ghatana.datacloud.memory.media.MediaArtifactRecord;
import com.ghatana.datacloud.memory.media.MediaArtifactService;
import com.ghatana.datacloud.memory.media.MediaProcessingJob;
import com.ghatana.datacloud.memory.media.Transcript;
import com.ghatana.datacloud.security.RoutePolicyEnforcer;
import com.ghatana.datacloud.security.RouteSensitivityMatrix;
import com.ghatana.datacloud.security.SecurityInterceptor;
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
import java.util.Set;
import java.util.regex.Pattern;

/**
 * REST controller for Data Cloud media artifact metadata.
 *
 * <p>WS3-1: Thin controller that delegates all business logic to {@link MediaArtifactService}.
 * This controller only handles HTTP concerns (request parsing, response building).
 *
 * <p>Tenant identity is resolved from the authenticated {@link Principal}
 * attached by the security filter. The controller does not read spoofable
 * tenant headers or query parameters.
 *
 * <p>Event emission and operation recording are mandatory - the service requires
 * both dependencies and all mutating operations emit canonical events.
 *
 * @doc.type class
 * @doc.purpose Thin HTTP controller for media artifact operations
 * @doc.layer product
 * @doc.pattern Controller
 */
public final class MediaArtifactController {

    private static final Logger log = LoggerFactory.getLogger(MediaArtifactController.class);

    private static final String COLLECTION_PATH = "/api/v1/media/artifacts";
    private static final String TRANSCRIPTION_SUFFIX = "/transcribe";
    private static final String VISION_SUFFIX = "/analyze";
    private static final String MULTIMODAL_SUFFIX = "/index-multimodal";
    private static final String JOBS_SUFFIX = "/jobs";
    private static final String TRANSCRIPT_SUFFIX = "/transcript";
    private static final String FRAME_INDEX_SUFFIX = "/frame-index";
    private static final String RETRY_SUFFIX = "/retry";
        private static final Pattern LANGUAGE_CODE_PATTERN = Pattern.compile("^[a-z]{2}(?:-[A-Z]{2})?$");
        private static final Set<String> SUPPORTED_ANALYSIS_TYPES = Set.of(
            "OBJECT_DETECTION",
            "FACE_RECOGNITION",
            "SCENE_CLASSIFICATION"
        );
        private static final Set<String> SUPPORTED_INDEX_TYPES = Set.of(
            "SEMANTIC",
            "VECTOR",
            "AUDIO_VISUAL",
            "SCENE_UNDERSTANDING"
        );

    private final MediaArtifactService service;
    private final ObjectMapper objectMapper;
    private final SecurityInterceptor securityInterceptor;

    /**
     * Creates a new MediaArtifactController with mandatory service.
     *
     * @param service the media artifact service (mandatory, includes event emission and operation recording)
     * @param objectMapper the JSON object mapper
     */
    public MediaArtifactController(
            MediaArtifactService service,
            ObjectMapper objectMapper) {
        this(service, objectMapper, new SecurityInterceptor(new RoutePolicyEnforcer(new RouteSensitivityMatrix())));
    }

    MediaArtifactController(
            MediaArtifactService service,
            ObjectMapper objectMapper,
            SecurityInterceptor securityInterceptor) {
        this.service = service;
        this.objectMapper = objectMapper;
        this.securityInterceptor = securityInterceptor;
        log.info("[media-artifact] Controller initialized with mandatory service");
    }

    public Promise<HttpResponse> handle(HttpRequest request) {
        Principal principal = request.getAttachment(Principal.class);
        if (principal == null) {
            return Promise.of(ResponseBuilder.unauthorized()
                .json(Map.of("error", "Unauthorized: No principal found"))
                .build());
        }

        Promise<HttpResponse> denial = securityInterceptor.intercept(
            request,
            securityInterceptor.extractSecurityContext(request, principal)
        );
        if (denial != null) {
            return denial;
        }

        String tenantId = principal.getTenantId();
        String path = request.getPath();
        HttpMethod method = request.getMethod();

        // Collection-level endpoints
        if (method == HttpMethod.POST && COLLECTION_PATH.equals(path)) {
            return createArtifact(request, tenantId, principal);
        }
        if (method == HttpMethod.GET && COLLECTION_PATH.equals(path)) {
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
                    return getJobs(artifactId, tenantId, principal, request);
                }
            }
            if (subPath.equals(TRANSCRIPT_SUFFIX)) {
                if (method == HttpMethod.GET) {
                    return getTranscript(artifactId, tenantId, principal, request);
                }
            }
            if (subPath.equals(FRAME_INDEX_SUFFIX)) {
                if (method == HttpMethod.GET) {
                    return getFrameIndex(artifactId, tenantId, principal, request);
                }
            }
            if (subPath.equals(RETRY_SUFFIX)) {
                if (method == HttpMethod.POST) {
                    return retryProcessing(artifactId, tenantId, principal, request);
                }
            }
            if (subPath.equals(TRANSCRIPTION_SUFFIX)) {
                if (method == HttpMethod.POST) {
                    return triggerTranscription(artifactId, tenantId, principal, request);
                }
            }
            if (subPath.equals(VISION_SUFFIX)) {
                if (method == HttpMethod.POST) {
                    return triggerVisionAnalysis(artifactId, tenantId, principal, request);
                }
            }
            if (subPath.equals(MULTIMODAL_SUFFIX)) {
                if (method == HttpMethod.POST) {
                    return triggerMultimodalIndexing(artifactId, tenantId, principal, request);
                }
            }

            // Artifact resource endpoints
            if (subPath.isEmpty()) {
                if (method == HttpMethod.GET) {
                    return getArtifact(artifactId, tenantId);
                }
                if (method == HttpMethod.DELETE) {
                    return deleteArtifact(artifactId, tenantId, principal, request);
                }
                // Consent update endpoint
                if (method == HttpMethod.PATCH) {
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

                // Delegate to service (handles validation, lifecycle state, event emission, operation recording)
                return service.createArtifact(
                    tenantId,
                    payload.agentId(),
                    payload.mediaType(),
                    payload.storageUri(),
                    payload.sizeBytes(),
                    payload.checksum(),
                    payload.durationMs(),
                    payload.originToolId(),
                    payload.correlationId(),
                    payload.consentStatus(),
                    payload.retentionPolicy(),
                    payload.retentionUntil(),
                    payload.metadata(),
                    principal,
                    request)
                    .map(saved -> ResponseBuilder.created().json(toResponse(saved)).build());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid create media artifact request: {}", e.getMessage());
                return Promise.of(ResponseBuilder.badRequest()
                    .json(Map.of("error", e.getMessage()))
                    .build());
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
        String agentId = normalizeQueryParam(request.getQueryParameter("agentId"));
        String mediaType = normalizeQueryParam(request.getQueryParameter("mediaType"));

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

    private String normalizeQueryParam(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
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
                String newConsentStatus = parseConsentUpdateRequest(body).consentStatus();
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
        return service.retryProcessing(artifactId, tenantId, principal, request)
            .then(optionalJob -> {
                if (optionalJob.isPresent()) {
                    MediaProcessingJob job = optionalJob.get();
                    return Promise.of(ResponseBuilder.accepted()
                        .json(buildAcceptedJobResponse(job, "Processing retry accepted"))
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
                TranscriptionRequest payload = parseTranscriptionRequest(body);

                return service.triggerTranscription(artifactId, tenantId, payload.languageCode(), principal, request)
                    .then(optionalJob -> {
                        if (optionalJob.isPresent()) {
                            MediaProcessingJob job = optionalJob.get();
                            return Promise.of(ResponseBuilder.accepted()
                                .json(buildAcceptedJobResponse(job, "Transcription job accepted"))
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
                VisionAnalysisRequest payload = parseVisionAnalysisRequest(body);

                return service.triggerVisionAnalysis(artifactId, tenantId, payload.analysisType(), principal, request)
                    .then(optionalJob -> {
                        if (optionalJob.isPresent()) {
                            MediaProcessingJob job = optionalJob.get();
                            return Promise.of(ResponseBuilder.accepted()
                                .json(buildAcceptedJobResponse(job, "Vision analysis job accepted"))
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

    private Promise<HttpResponse> triggerMultimodalIndexing(String artifactId, String tenantId, Principal principal, HttpRequest request) {
        return request.loadBody().then(body -> {
            try {
                MultimodalIndexingRequest payload = parseMultimodalIndexingRequest(body);

                return service.triggerMultimodalIndexing(artifactId, tenantId, payload.indexType(), principal, request)
                    .then(optionalJob -> {
                        if (optionalJob.isPresent()) {
                            MediaProcessingJob job = optionalJob.get();
                            return Promise.of(ResponseBuilder.accepted()
                                .json(buildAcceptedJobResponse(job, "Multimodal indexing job accepted"))
                                .build());
                        }
                        return Promise.of(ResponseBuilder.forbidden()
                            .json(Map.of("error", "Multimodal indexing blocked - check consent and retention policy"))
                            .build());
                    });
            } catch (Exception e) {
                log.warn("Invalid multimodal indexing request", e);
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

    private TranscriptionRequest parseTranscriptionRequest(io.activej.bytebuf.ByteBuf body) throws Exception {
        if (body == null || body.asArray().length == 0) {
            return new TranscriptionRequest("en-US");
        }

        TranscriptionRequest payload = objectMapper.readValue(body.asArray(), TranscriptionRequest.class);
        String languageCode = payload.languageCode() == null || payload.languageCode().isBlank()
                ? "en-US"
                : payload.languageCode();
        if (!LANGUAGE_CODE_PATTERN.matcher(languageCode).matches()) {
            throw new IllegalArgumentException("Invalid languageCode");
        }
        return new TranscriptionRequest(languageCode);
    }

    private VisionAnalysisRequest parseVisionAnalysisRequest(io.activej.bytebuf.ByteBuf body) throws Exception {
        if (body == null || body.asArray().length == 0) {
            return new VisionAnalysisRequest("OBJECT_DETECTION");
        }

        VisionAnalysisRequest payload = objectMapper.readValue(body.asArray(), VisionAnalysisRequest.class);
        String analysisType = payload.analysisType() == null || payload.analysisType().isBlank()
                ? "OBJECT_DETECTION"
                : payload.analysisType();
        if (!SUPPORTED_ANALYSIS_TYPES.contains(analysisType)) {
            throw new IllegalArgumentException("Invalid analysisType");
        }
        return new VisionAnalysisRequest(analysisType);
    }

    private MultimodalIndexingRequest parseMultimodalIndexingRequest(io.activej.bytebuf.ByteBuf body) throws Exception {
        if (body == null || body.asArray().length == 0) {
            return new MultimodalIndexingRequest("SEMANTIC");
        }

        MultimodalIndexingRequest payload = objectMapper.readValue(body.asArray(), MultimodalIndexingRequest.class);
        String indexType = payload.indexType() == null || payload.indexType().isBlank()
                ? "SEMANTIC"
                : payload.indexType();
        if (!SUPPORTED_INDEX_TYPES.contains(indexType)) {
            throw new IllegalArgumentException("Invalid indexType");
        }
        return new MultimodalIndexingRequest(indexType);
    }

    private ConsentUpdateRequest parseConsentUpdateRequest(io.activej.bytebuf.ByteBuf body) throws Exception {
        if (body == null || body.asArray().length == 0) {
            throw new IllegalArgumentException("consentStatus is required");
        }

        ConsentUpdateRequest payload = objectMapper.readValue(body.asArray(), ConsentUpdateRequest.class);
        if (payload.consentStatus() == null || !isValidConsentStatus(payload.consentStatus())) {
            throw new IllegalArgumentException("Invalid consentStatus");
        }
        return payload;
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
        response.put("queuedAt", job.queuedAt().toString());
        response.put("startedAt", job.startedAt() != null ? job.startedAt().toString() : null);
        response.put("completedAt", job.completedAt() != null ? job.completedAt().toString() : null);
        response.put("isTerminal", job.isTerminal());
        response.put("isSuccessful", job.isSuccessful());
        return Map.copyOf(response);
    }

    private Map<String, Object> buildAcceptedJobResponse(MediaProcessingJob job, String message) {
        Map<String, Object> response = new LinkedHashMap<>(toJobResponse(job));
        response.put("message", message);
        return Map.copyOf(response);
    }

    private record TranscriptionRequest(String languageCode) {
    }

    private record VisionAnalysisRequest(String analysisType) {
    }

    private record MultimodalIndexingRequest(String indexType) {
    }

    private record ConsentUpdateRequest(String consentStatus) {
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
