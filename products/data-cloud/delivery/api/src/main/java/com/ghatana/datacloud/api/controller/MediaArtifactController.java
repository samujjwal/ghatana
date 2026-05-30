package com.ghatana.datacloud.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.memory.media.MediaArtifactRecord;
import com.ghatana.datacloud.memory.media.MediaArtifactRepository;
import com.ghatana.platform.http.server.response.ResponseBuilder;
import io.activej.http.HttpHeader;
import io.activej.http.HttpHeaders;
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
 * @doc.type class
 * @doc.purpose Tenant-scoped API for media artifact registration and retrieval
 * @doc.layer product
 * @doc.pattern Controller
 */
public final class MediaArtifactController {

    private static final Logger log = LoggerFactory.getLogger(MediaArtifactController.class);

    private static final HttpHeader HEADER_TENANT_ID = HttpHeaders.of("X-Tenant-ID");
    private static final String COLLECTION_PATH = "/api/v1/media/artifacts";

    private final MediaArtifactRepository repository;
    private final ObjectMapper objectMapper;

    public MediaArtifactController(MediaArtifactRepository repository, ObjectMapper objectMapper) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
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

        if (method == HttpMethod.POST && COLLECTION_PATH.equals(path)) {
            return createArtifact(request, tenantId);
        }
        if (method == HttpMethod.GET && COLLECTION_PATH.equals(path)) {
            return listArtifacts(request, tenantId);
        }
        if (path.startsWith(COLLECTION_PATH + "/")) {
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
                return deleteArtifact(artifactId, tenantId);
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
        return repository.delete(artifactId, tenantId)
            .map(deleted -> deleted
                ? ResponseBuilder.noContent().build()
                : ResponseBuilder.notFound().json(Map.of("error", "Media artifact not found")).build());
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
        response.put("metadata", record.metadata() == null ? Collections.emptyMap() : record.metadata());
        response.put("createdAt", record.createdAt().toString());
        return Map.copyOf(response);
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