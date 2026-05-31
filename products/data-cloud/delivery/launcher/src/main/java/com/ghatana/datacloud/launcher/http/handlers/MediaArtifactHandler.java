package com.ghatana.datacloud.launcher.http.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.application.MediaArtifactService;
import com.ghatana.datacloud.entity.media.MediaArtifact;
import com.ghatana.datacloud.entity.media.MediaProcessingJob;
import com.ghatana.datacloud.entity.media.Transcript;
import com.ghatana.platform.http.server.response.ResponseBuilder;
import io.activej.http.HttpHeader;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * HTTP handler for media artifact management endpoints.
 *
 * <p><b>Purpose</b><br>
 * Provides REST API endpoints for media artifact lifecycle management including
 * registration, privacy validation, processing jobs, transcription, and search.
 *
 * @doc.type class
 * @doc.purpose HTTP handler for media artifact REST API
 * @doc.layer product
 * @doc.pattern Handler
 */
public final class MediaArtifactHandler {

    private static final Logger log = LoggerFactory.getLogger(MediaArtifactHandler.class);

    private static final HttpHeader HEADER_TENANT_ID = HttpHeaders.of("X-Tenant-ID");
    private static final String COLLECTION_PATH = "/api/v1/media/artifacts";
    private static final String PROCESSING_PATH = "/api/v1/media/artifacts/%s/jobs";
    private static final String TRANSCRIPT_PATH = "/api/v1/media/artifacts/%s/transcripts";
    private static final String SEARCH_PATH = "/api/v1/media/transcripts/search";

    private final MediaArtifactService mediaArtifactService;
    private final ObjectMapper objectMapper;

    public MediaArtifactHandler(MediaArtifactService mediaArtifactService, ObjectMapper objectMapper) {
        this.mediaArtifactService = Objects.requireNonNull(mediaArtifactService, "mediaArtifactService cannot be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
    }

    public Promise<HttpResponse> handle(HttpRequest request) {
        String tenantId = request.getHeader(HEADER_TENANT_ID);
        if (tenantId == null || tenantId.isBlank()) {
            return Promise.of(ResponseBuilder.badRequest()
                    .json(Map.of("error", "X-Tenant-ID header is required"))
                    .build());
        }

        String path = request.getPath();
        HttpMethod method = request.getMethod();

        // Artifact CRUD
        if (method == HttpMethod.POST && COLLECTION_PATH.equals(path)) {
            return registerArtifact(request, tenantId);
        }
        if (method == HttpMethod.GET && COLLECTION_PATH.equals(path)) {
            return listArtifacts(request, tenantId);
        }
        if (path.startsWith(COLLECTION_PATH + "/") && !path.contains("/jobs") && !path.contains("/transcripts")) {
            String artifactId = path.substring((COLLECTION_PATH + "/").length());
            if (artifactId.isBlank() || artifactId.contains("/")) {
                return Promise.of(ResponseBuilder.badRequest()
                        .json(Map.of("error", "artifactId path parameter is required"))
                        .build());
            }
            if (method == HttpMethod.GET) {
                return getArtifact(artifactId, tenantId);
            }
            if (method == HttpMethod.DELETE) {
                return deleteArtifact(artifactId, tenantId, request);
            }
        }

        // Processing jobs
        if (method == HttpMethod.POST && path.matches(COLLECTION_PATH.replace("%s", "[^/]+") + "/jobs")) {
            String artifactId = extractArtifactId(path, COLLECTION_PATH, "/jobs");
            return createProcessingJob(artifactId, tenantId, request);
        }
        if (method == HttpMethod.POST && path.matches(COLLECTION_PATH.replace("%s", "[^/]+") + "/jobs/[^/]+/start")) {
            String artifactId = extractArtifactId(path, COLLECTION_PATH, "/jobs");
            String jobId = extractJobId(path, "/jobs/", "/start");
            return startProcessingJob(jobId, tenantId, request);
        }
        if (method == HttpMethod.POST && path.matches(COLLECTION_PATH.replace("%s", "[^/]+") + "/jobs/[^/]+/complete")) {
            String artifactId = extractArtifactId(path, COLLECTION_PATH, "/jobs");
            String jobId = extractJobId(path, "/jobs/", "/complete");
            return completeProcessingJob(jobId, tenantId, request);
        }

        // Transcripts
        if (method == HttpMethod.GET && path.matches(COLLECTION_PATH.replace("%s", "[^/]+") + "/transcripts")) {
            String artifactId = extractArtifactId(path, COLLECTION_PATH, "/transcripts");
            return getTranscripts(artifactId, tenantId);
        }

        // Search
        if (method == HttpMethod.GET && SEARCH_PATH.equals(path)) {
            return searchTranscripts(request, tenantId);
        }

        return Promise.of(ResponseBuilder.notFound()
                .json(Map.of("error", "Endpoint not found"))
                .build());
    }

    private Promise<HttpResponse> registerArtifact(HttpRequest request, String tenantId) {
        return request.loadBody().then(body -> {
            try {
                Map<String, Object> payload = objectMapper.readValue(
                        body != null ? body.asArray() : new byte[0],
                        objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));

                String agentId = (String) payload.get("agentId");
                String mediaType = (String) payload.get("mediaType");
                String storageUri = (String) payload.get("storageUri");
                Long sizeBytes = payload.get("sizeBytes") != null ? ((Number) payload.get("sizeBytes")).longValue() : null;
                String checksum = (String) payload.get("checksum");
                Long durationMs = payload.get("durationMs") != null ? ((Number) payload.get("durationMs")).longValue() : null;
                @SuppressWarnings("unchecked")
                Map<String, String> metadata = (Map<String, String>) payload.getOrDefault("metadata", Map.of());

                if (agentId == null || mediaType == null || storageUri == null) {
                    return Promise.of(ResponseBuilder.badRequest()
                            .json(Map.of("error", "agentId, mediaType, and storageUri are required"))
                            .build());
                }

                return mediaArtifactService.registerArtifact(tenantId, agentId, mediaType, storageUri, sizeBytes, checksum, durationMs, metadata)
                        .map(artifact -> ResponseBuilder.created().json(toArtifactResponse(artifact)).build());

            } catch (Exception e) {
                log.warn("Invalid register artifact request", e);
                return Promise.of(ResponseBuilder.badRequest()
                        .json(Map.of("error", "Invalid request payload"))
                        .build());
            }
        });
    }

    private Promise<HttpResponse> listArtifacts(HttpRequest request, String tenantId) {
        int limit = parseLimit(request.getQueryParameter("limit"));
        String mediaType = request.getQueryParameter("mediaType");
        String agentId = request.getQueryParameter("agentId");

        return mediaArtifactService.listArtifacts(tenantId, mediaType, agentId, limit)
                .map(artifacts -> ResponseBuilder.ok()
                        .json(Map.of(
                                "items", artifacts.stream().map(this::toArtifactResponse).toList(),
                                "count", artifacts.size()
                        ))
                        .build());
    }

    private Promise<HttpResponse> getArtifact(String artifactId, String tenantId) {
        return mediaArtifactService.getArtifact(artifactId, tenantId)
                .map(artifactOpt -> artifactOpt
                        .map(artifact -> ResponseBuilder.ok().json(toArtifactResponse(artifact)).build())
                        .orElseGet(() -> ResponseBuilder.notFound()
                                .json(Map.of("error", "Media artifact not found"))
                                .build()));
    }

    private Promise<HttpResponse> deleteArtifact(String artifactId, String tenantId, HttpRequest request) {
        return request.loadBody().then(body -> {
            try {
                Map<String, Object> payload = objectMapper.readValue(
                        body != null ? body.asArray() : new byte[0],
                        objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));

                String reason = (String) payload.getOrDefault("reason", "User requested deletion");

                return mediaArtifactService.deleteArtifact(artifactId, tenantId, reason)
                        .map(ignored -> ResponseBuilder.noContent().build());

            } catch (Exception e) {
                log.warn("Invalid delete artifact request", e);
                return Promise.of(ResponseBuilder.badRequest()
                        .json(Map.of("error", "Invalid request payload"))
                        .build());
            }
        });
    }

    private Promise<HttpResponse> createProcessingJob(String artifactId, String tenantId, HttpRequest request) {
        return request.loadBody().then(body -> {
            try {
                Map<String, Object> payload = objectMapper.readValue(
                        body != null ? body.asArray() : new byte[0],
                        objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));

                String jobTypeStr = (String) payload.get("jobType");
                @SuppressWarnings("unchecked")
                Map<String, Object> parameters = (Map<String, Object>) payload.getOrDefault("parameters", Map.of());
                String requestedBy = (String) payload.getOrDefault("requestedBy", "system");

                if (jobTypeStr == null) {
                    return Promise.of(ResponseBuilder.badRequest()
                            .json(Map.of("error", "jobType is required"))
                            .build());
                }

                MediaProcessingJob.JobType jobType = MediaProcessingJob.JobType.valueOf(jobTypeStr.toUpperCase());

                return mediaArtifactService.createProcessingJob(artifactId, tenantId, jobType, parameters, requestedBy)
                        .map(job -> ResponseBuilder.created().json(toJobResponse(job)).build());

            } catch (Exception e) {
                log.warn("Invalid create processing job request", e);
                return Promise.of(ResponseBuilder.badRequest()
                        .json(Map.of("error", "Invalid request payload"))
                        .build());
            }
        });
    }

    private Promise<HttpResponse> startProcessingJob(String jobId, String tenantId, HttpRequest request) {
        return request.loadBody().then(body -> {
            try {
                Map<String, Object> payload = objectMapper.readValue(
                        body != null ? body.asArray() : new byte[0],
                        objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));

                String workerNode = (String) payload.getOrDefault("workerNode", "worker-1");

                return mediaArtifactService.startProcessingJob(jobId, tenantId, workerNode)
                        .map(job -> ResponseBuilder.ok().json(toJobResponse(job)).build());

            } catch (Exception e) {
                log.warn("Invalid start processing job request", e);
                return Promise.of(ResponseBuilder.badRequest()
                        .json(Map.of("error", "Invalid request payload"))
                        .build());
            }
        });
    }

    private Promise<HttpResponse> completeProcessingJob(String jobId, String tenantId, HttpRequest request) {
        return request.loadBody().then(body -> {
            try {
                Map<String, Object> payload = objectMapper.readValue(
                        body != null ? body.asArray() : new byte[0],
                        objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));

                @SuppressWarnings("unchecked")
                Map<String, Object> results = (Map<String, Object>) payload.getOrDefault("results", Map.of());

                return mediaArtifactService.completeProcessingJob(jobId, tenantId, results)
                        .map(job -> ResponseBuilder.ok().json(toJobResponse(job)).build());

            } catch (Exception e) {
                log.warn("Invalid complete processing job request", e);
                return Promise.of(ResponseBuilder.badRequest()
                        .json(Map.of("error", "Invalid request payload"))
                        .build());
            }
        });
    }

    private Promise<HttpResponse> getTranscripts(String artifactId, String tenantId) {
        return mediaArtifactService.getTranscripts(artifactId, tenantId)
                .map(transcripts -> ResponseBuilder.ok()
                        .json(Map.of(
                                "artifactId", artifactId,
                                "transcripts", transcripts.stream().map(this::toTranscriptResponse).toList(),
                                "count", transcripts.size()
                        ))
                        .build());
    }

    private Promise<HttpResponse> searchTranscripts(HttpRequest request, String tenantId) {
        String searchText = request.getQueryParameter("q");
        if (searchText == null || searchText.isBlank()) {
            return Promise.of(ResponseBuilder.badRequest()
                    .json(Map.of("error", "q query parameter is required"))
                    .build());
        }

        return mediaArtifactService.searchTranscripts(tenantId, searchText)
                .map(transcripts -> ResponseBuilder.ok()
                        .json(Map.of(
                                "query", searchText,
                                "results", transcripts.stream().map(this::toTranscriptResponse).toList(),
                                "count", transcripts.size()
                        ))
                        .build());
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

    private String extractArtifactId(String path, String basePath, String suffix) {
        int startIndex = basePath.indexOf("%s");
        String prefix = basePath.substring(0, startIndex);
        int artifactIdStart = path.indexOf(prefix) + prefix.length();
        int artifactIdEnd = path.indexOf(suffix);
        return path.substring(artifactIdStart, artifactIdEnd);
    }

    private String extractJobId(String path, String prefix, String suffix) {
        int jobIdStart = path.indexOf(prefix) + prefix.length();
        int jobIdEnd = path.indexOf(suffix);
        return path.substring(jobIdStart, jobIdEnd);
    }

    private Map<String, Object> toArtifactResponse(MediaArtifact artifact) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("artifactId", artifact.getArtifactId());
        response.put("tenantId", artifact.getTenantId());
        response.put("agentId", artifact.getAgentId());
        response.put("mediaType", artifact.getMediaType());
        response.put("storageUri", artifact.getStorageUri());
        response.put("sizeBytes", artifact.getSizeBytes());
        response.put("checksum", artifact.getChecksum());
        response.put("durationMs", artifact.getDurationMs());
        response.put("classification", artifact.getClassification().name());
        response.put("consentStatus", artifact.getConsentStatus() != null ? artifact.getConsentStatus().name() : "NONE");
        response.put("retentionPolicy", artifact.getRetentionPolicy());
        response.put("expiresAt", artifact.getExpiresAt() != null ? artifact.getExpiresAt().toString() : null);
        response.put("metadata", artifact.getMetadata());
        response.put("createdAt", artifact.getCreatedAt().toString());
        response.put("updatedAt", artifact.getUpdatedAt().toString());
        return Map.copyOf(response);
    }

    private Map<String, Object> toJobResponse(MediaProcessingJob job) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jobId", job.getJobId());
        response.put("tenantId", job.getTenantId());
        response.put("artifactId", job.getMediaArtifactId() != null ? job.getMediaArtifactId().toString() : null);
        response.put("jobType", job.getJobType().name());
        response.put("status", job.getStatus().name());
        response.put("priority", job.getPriority().name());
        response.put("progressPercentage", job.getProgressPercentage());
        response.put("statusMessage", job.getStatusMessage());
        response.put("parameters", job.getParameters());
        response.put("results", job.getResults());
        response.put("errorInfo", job.getErrorInfo());
        response.put("processingTimeMs", job.getProcessingTimeMs());
        response.put("retryCount", job.getRetryCount());
        response.put("requestedBy", job.getRequestedBy());
        response.put("workerNode", job.getWorkerNode());
        response.put("createdAt", job.getCreatedAt().toString());
        response.put("startedAt", job.getStartedAt() != null ? job.getStartedAt().toString() : null);
        response.put("completedAt", job.getCompletedAt() != null ? job.getCompletedAt().toString() : null);
        return Map.copyOf(response);
    }

    private Map<String, Object> toTranscriptResponse(Transcript transcript) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("transcriptId", transcript.getTranscriptId());
        response.put("tenantId", transcript.getTenantId());
        response.put("artifactId", transcript.getMediaArtifactId() != null ? transcript.getMediaArtifactId().toString() : null);
        response.put("languageCode", transcript.getLanguageCode());
        response.put("detectedLanguage", transcript.getDetectedLanguage());
        response.put("confidenceScore", transcript.getConfidenceScore());
        response.put("wordCount", transcript.getWordCount());
        response.put("speakerCount", transcript.getSpeakerCount());
        response.put("fullText", transcript.getFullText());
        response.put("segments", transcript.getSegments());
        response.put("speakers", transcript.getSpeakers());
        response.put("createdAt", transcript.getCreatedAt().toString());
        response.put("updatedAt", transcript.getUpdatedAt().toString());
        return Map.copyOf(response);
    }
}
