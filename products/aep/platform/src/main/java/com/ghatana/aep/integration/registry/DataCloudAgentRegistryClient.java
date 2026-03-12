/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.integration.registry;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.aep.domain.models.agent.AgentInfo;
import com.ghatana.orchestrator.client.AgentRegistryClient;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * HTTP-based {@link AgentRegistryClient} that reads agent definitions from Data-Cloud.
 *
 * <p>Uses the Data-Cloud HTTP API ({@code /api/v1/agents}) to discover and resolve
 * agents registered in the platform agent registry. All HTTP calls are executed on a
 * virtual-thread executor wrapped in {@link Promise#ofBlocking} to keep the ActiveJ
 * event loop unblocked.
 *
 * <h2>Endpoints Used</h2>
 * <ul>
 *   <li>{@code GET {DC_BASE_URL}/api/v1/agents} — list all active agents</li>
 *   <li>{@code GET {DC_BASE_URL}/api/v1/agents/{id}} — get a specific agent by ID</li>
 * </ul>
 *
 * <h2>Configuration</h2>
 * <p>The Data-Cloud base URL defaults to {@code http://localhost:8085} and is
 * overridden via the {@code AEP_DC_BASE_URL} environment variable.
 *
 * @doc.type class
 * @doc.purpose HTTP-based AgentRegistryClient backed by Data-Cloud agent storage
 * @doc.layer product
 * @doc.pattern Adapter, Client
 * @see com.ghatana.aep.config.EnvConfig#aepDcBaseUrl()
 * @since 1.0.0
 */
public final class DataCloudAgentRegistryClient implements AgentRegistryClient {

    private static final Logger log = LoggerFactory.getLogger(DataCloudAgentRegistryClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule());

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);
    private static final String TENANT_HEADER = "X-Tenant-ID";
    private static final String DEFAULT_TENANT = "system";

    private final HttpClient httpClient;
    private final String dcBaseUrl;
    private final Executor executor;

    /**
     * Creates an agent registry client bound to the given Data-Cloud base URL.
     *
     * @param dcBaseUrl Data-Cloud service base URL (e.g., {@code http://localhost:8085})
     */
    public DataCloudAgentRegistryClient(String dcBaseUrl) {
        this.dcBaseUrl = Objects.requireNonNull(dcBaseUrl, "dcBaseUrl required")
            .strip().replaceAll("/$", ""); // remove trailing slash
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(HTTP_TIMEOUT)
            .executor(this.executor)
            .build();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Fetches the full agent list from Data-Cloud ({@code GET /api/v1/agents}).
     */
    @Override
    public Promise<List<AgentInfo>> listAllAgents() {
        return Promise.ofBlocking(executor, () -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(dcBaseUrl + "/api/v1/agents"))
                    .GET()
                    .timeout(HTTP_TIMEOUT)
                    .header(TENANT_HEADER, DEFAULT_TENANT)
                    .header("Accept", "application/json")
                    .build();

                HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    List<Map<String, Object>> rawList = MAPPER.readValue(
                        response.body(), new TypeReference<>() {});
                    return rawList.stream()
                        .map(this::toAgentInfo)
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .toList();
                }

                log.warn("DataCloud listAllAgents returned HTTP {}", response.statusCode());
                return Collections.emptyList();
            } catch (Exception e) {
                log.error("Failed to list agents from DataCloud at {}: {}", dcBaseUrl, e.getMessage());
                return Collections.emptyList();
            }
        });
    }

    /**
     * {@inheritDoc}
     *
     * <p>Fetches a specific agent from Data-Cloud ({@code GET /api/v1/agents/{id}}).
     */
    @Override
    public Promise<Optional<AgentInfo>> getAgent(String agentId) {
        Objects.requireNonNull(agentId, "agentId required");
        return Promise.ofBlocking(executor, () -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(dcBaseUrl + "/api/v1/agents/" + agentId))
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
                    return toAgentInfo(raw);
                }
                if (response.statusCode() == 404) {
                    return Optional.empty();
                }

                log.warn("DataCloud getAgent({}) returned HTTP {}", agentId, response.statusCode());
                return Optional.empty();
            } catch (Exception e) {
                log.error("Failed to get agent {} from DataCloud at {}: {}", agentId, dcBaseUrl, e.getMessage());
                return Optional.empty();
            }
        });
    }

    /**
     * {@inheritDoc}
     *
     * <p>Requests all agents and filters by type client-side (Data-Cloud does not yet
     * support server-side type filtering on the {@code dc_agents} collection).
     */
    @Override
    public Promise<List<AgentInfo>> listAgentsByType(String agentType) {
        Objects.requireNonNull(agentType, "agentType required");
        return listAllAgents().map(agents ->
            agents.stream()
                .filter(a -> agentType.equalsIgnoreCase(a.getType()))
                .toList()
        );
    }

    /**
     * {@inheritDoc}
     *
     * <p>Performs a lightweight health probe against Data-Cloud
     * ({@code GET /health}) and returns true if the response is 200.
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
                log.debug("DataCloud health check failed: {}", e.getMessage());
                return false;
            }
        });
    }

    // ==================== Private Helpers ====================

    @SuppressWarnings("unchecked")
    private Optional<AgentInfo> toAgentInfo(Map<String, Object> raw) {
        if (raw == null) {
            return Optional.empty();
        }
        try {
            String id = str(raw, "id");
            String name = str(raw, "name");
            if (id == null || id.isBlank()) {
                return Optional.empty();
            }
            AgentInfo info = new AgentInfo(
                id,
                name != null ? name : id,
                str(raw, "type", "GENERIC"),
                str(raw, "version", "1.0.0"),
                str(raw, "status", "ACTIVE"),
                str(raw, "description", ""),
                str(raw, "endpoint", "")
            );
            Object caps = raw.get("capabilities");
            if (caps instanceof Map<?, ?> capsMap) {
                capsMap.forEach((k, v) -> {
                    if (k instanceof String key && v instanceof Map<?, ?> valMap) {
                        Map<String, Object> capAttrs = (Map<String, Object>) valMap;
                        info.addCapability(key, capAttrs);
                    }
                });
            }
            return Optional.of(info);
        } catch (Exception e) {
            log.warn("Failed to map agent JSON to AgentInfo: {}", e.getMessage());
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
