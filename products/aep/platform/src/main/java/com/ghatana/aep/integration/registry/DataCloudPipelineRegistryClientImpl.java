/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.integration.registry;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.orchestrator.client.PipelineRegistryClient;
import com.ghatana.orchestrator.models.OrchestratorPipelineEntity;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * HTTP-based {@link PipelineRegistryClient} that reads and writes pipeline definitions
 * from/to Data-Cloud's {@code /api/v1/pipelines} endpoints.
 *
 * <p>All HTTP calls are executed on a virtual-thread executor wrapped in
 * {@link Promise#ofBlocking} so the ActiveJ event loop is never blocked.
 *
 * <h2>Endpoints Used</h2>
 * <ul>
 *   <li>{@code GET {DC_BASE_URL}/api/v1/pipelines}              — list all pipelines</li>
 *   <li>{@code GET {DC_BASE_URL}/api/v1/pipelines?tenantId={t}} — list pipelines for tenant</li>
 *   <li>{@code GET {DC_BASE_URL}/api/v1/pipelines/{id}}         — get pipeline by ID</li>
 *   <li>{@code GET {DC_BASE_URL}/health}                        — health probe</li>
 * </ul>
 *
 * <h2>Configuration</h2>
 * <p>The Data-Cloud base URL defaults to {@code http://localhost:8085} and is
 * overridden via the {@code AEP_DC_BASE_URL} environment variable.
 *
 * @doc.type class
 * @doc.purpose HTTP-based PipelineRegistryClient backed by Data-Cloud pipeline storage
 * @doc.layer product
 * @doc.pattern Adapter, Client
 * @see com.ghatana.aep.config.EnvConfig#aepDcBaseUrl()
 * @since 1.0.0
 */
public final class DataCloudPipelineRegistryClientImpl implements PipelineRegistryClient {

    private static final Logger log = LoggerFactory.getLogger(DataCloudPipelineRegistryClientImpl.class);

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule());

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);
    private static final String TENANT_HEADER = "X-Tenant-ID";
    private static final String DEFAULT_TENANT = "system";

    private final HttpClient httpClient;
    private final String dcBaseUrl;
    private final Executor executor;

    /**
     * Creates a pipeline registry client bound to the given Data-Cloud base URL.
     *
     * @param dcBaseUrl Data-Cloud service base URL (e.g., {@code http://localhost:8085})
     */
    public DataCloudPipelineRegistryClientImpl(String dcBaseUrl) {
        this.dcBaseUrl = Objects.requireNonNull(dcBaseUrl, "dcBaseUrl required")
            .strip().replaceAll("/$", ""); // normalise – remove trailing slash
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(HTTP_TIMEOUT)
            .executor(this.executor)
            .build();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Calls {@code GET /api/v1/pipelines} and maps the {@code pipelines} array in
     * the response to a list of {@link OrchestratorPipelineEntity} objects.
     * Returns an empty list on any error so callers remain operational.
     */
    @Override
    public Promise<List<OrchestratorPipelineEntity>> listAllPipelines() {
        return Promise.ofBlocking(executor, () -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(dcBaseUrl + "/api/v1/pipelines"))
                    .GET()
                    .timeout(HTTP_TIMEOUT)
                    .header(TENANT_HEADER, DEFAULT_TENANT)
                    .header("Accept", "application/json")
                    .build();

                HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    Map<String, Object> body = MAPPER.readValue(
                        response.body(), new TypeReference<>() {});
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> rawList =
                        (List<Map<String, Object>>) body.getOrDefault("pipelines", List.of());
                    return rawList.stream()
                        .map(this::toPipelineEntity)
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .toList();
                }

                log.warn("DataCloud listAllPipelines returned HTTP {}", response.statusCode());
                return Collections.emptyList();
            } catch (Exception e) {
                log.error("Failed to list pipelines from DataCloud at {}: {}", dcBaseUrl, e.getMessage());
                return Collections.emptyList();
            }
        });
    }

    /**
     * {@inheritDoc}
     *
     * <p>Calls {@code GET /api/v1/pipelines/{pipelineId}}.
     * Returns {@link Optional#empty()} on 404 or any error.
     */
    @Override
    public Promise<Optional<OrchestratorPipelineEntity>> getPipeline(String pipelineId) {
        Objects.requireNonNull(pipelineId, "pipelineId required");
        return Promise.ofBlocking(executor, () -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(dcBaseUrl + "/api/v1/pipelines/" + pipelineId))
                    .GET()
                    .timeout(HTTP_TIMEOUT)
                    .header(TENANT_HEADER, DEFAULT_TENANT)
                    .header("Accept", "application/json")
                    .build();

                HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    Map<String, Object> raw = MAPPER.readValue(
                        response.body(), new TypeReference<>() {});
                    return toPipelineEntity(raw);
                }
                if (response.statusCode() == 404) {
                    return Optional.empty();
                }

                log.warn("DataCloud getPipeline({}) returned HTTP {}", pipelineId, response.statusCode());
                return Optional.empty();
            } catch (Exception e) {
                log.error("Failed to get pipeline {} from DataCloud at {}: {}",
                    pipelineId, dcBaseUrl, e.getMessage());
                return Optional.empty();
            }
        });
    }

    /**
     * {@inheritDoc}
     *
     * <p>Calls {@code GET /api/v1/pipelines?tenantId={tenantId}} and maps the response.
     * Returns an empty list on any error so callers remain operational.
     */
    @Override
    public Promise<List<OrchestratorPipelineEntity>> listPipelinesForTenant(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId required");
        return Promise.ofBlocking(executor, () -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(dcBaseUrl + "/api/v1/pipelines?tenantId=" + tenantId))
                    .GET()
                    .timeout(HTTP_TIMEOUT)
                    .header(TENANT_HEADER, tenantId)
                    .header("Accept", "application/json")
                    .build();

                HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    Map<String, Object> body = MAPPER.readValue(
                        response.body(), new TypeReference<>() {});
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> rawList =
                        (List<Map<String, Object>>) body.getOrDefault("pipelines", List.of());
                    return rawList.stream()
                        .map(this::toPipelineEntity)
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .toList();
                }

                log.warn("DataCloud listPipelinesForTenant({}) returned HTTP {}",
                    tenantId, response.statusCode());
                return Collections.emptyList();
            } catch (Exception e) {
                log.error("Failed to list pipelines for tenant {} from DataCloud: {}",
                    tenantId, e.getMessage());
                return Collections.emptyList();
            }
        });
    }

    /**
     * {@inheritDoc}
     *
     * <p>Performs a lightweight {@code GET /health} probe against Data-Cloud and
     * returns {@code true} only when the response status is 200.
     */
    @Override
    public Promise<Boolean> isHealthy() {
        return Promise.ofBlocking(executor, () -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(dcBaseUrl + "/health"))
                    .GET()
                    .timeout(Duration.ofSeconds(3))
                    .build();

                HttpResponse<Void> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.discarding());
                return response.statusCode() == 200;
            } catch (Exception e) {
                log.debug("DataCloud pipeline registry health check failed: {}", e.getMessage());
                return false;
            }
        });
    }

    // ==================== Private Helpers ====================

    /**
     * Maps a raw pipeline JSON object (from DC response) to an {@link OrchestratorPipelineEntity}.
     *
     * @param raw the deserialized JSON map
     * @return populated entity, or empty if the map is null or missing an {@code id}
     */
    private Optional<OrchestratorPipelineEntity> toPipelineEntity(Map<String, Object> raw) {
        if (raw == null) {
            return Optional.empty();
        }
        try {
            String id = str(raw, "id");
            if (id == null || id.isBlank()) {
                return Optional.empty();
            }
            OrchestratorPipelineEntity entity = new OrchestratorPipelineEntity();
            entity.id          = id;
            entity.name        = str(raw, "name", id);
            entity.description = str(raw, "description", "");
            entity.config      = str(raw, "config", "{}");
            entity.version     = str(raw, "version", "1.0.0");
            entity.createdBy   = str(raw, "createdBy", "system");
            entity.status      = str(raw, "status", "ACTIVE");
            entity.tenantId    = str(raw, "tenantId", "default");

            String createdAtStr = str(raw, "createdAt");
            String updatedAtStr = str(raw, "updatedAt");
            entity.createdAt = createdAtStr != null ? Instant.parse(createdAtStr) : Instant.now();
            entity.updatedAt = updatedAtStr != null ? Instant.parse(updatedAtStr) : entity.createdAt;

            return Optional.of(entity);
        } catch (Exception e) {
            log.warn("Failed to map pipeline JSON to OrchestratorPipelineEntity: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v instanceof String s ? s : null;
    }

    private static String str(Map<String, Object> map, String key, String defaultValue) {
        Object v = map.get(key);
        return (v instanceof String s && !s.isBlank()) ? s : defaultValue;
    }
}
