/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.http.controllers;

import com.ghatana.aep.AepEngine;
import com.ghatana.aep.eventcloud.store.EventCloudAgentStore;
import com.ghatana.aep.observability.AepSloMetrics;
import com.ghatana.aep.server.http.HttpHelper;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.spi.EntityStore;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/**
 * Controller for agent management endpoints.
 * Handles agent lifecycle, execution, and memory queries.
 *
 * <p>Agent registry operations (list, get, deregister) are backed by
 * {@link EventCloudAgentStore} — the canonical Data-Cloud-backed store for AEP agents.
 * Memory operations (episodes, facts, policies) delegate to {@link DataCloudClient}
 * for the {@code dc_memory} collection.
 *
 * @doc.type class
 * @doc.purpose Agent registry and memory management via EventCloudAgentStore
 * @doc.layer product
 * @doc.pattern Service
 * @doc.gaa.lifecycle perceive
 */
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);
    private static final String REGISTRATION_MODE_DIRECT = "direct";
    private static final String REGISTRATION_MODE_MANIFEST_ONLY = "manifest-only";
    private static final String REGISTRY_STORAGE_DATACLOUD = "datacloud";
    private static final String REGISTRY_STORAGE_UNCONFIGURED = "unconfigured";
    private static final String MEMORY_PERSISTENCE_DATACLOUD = "datacloud";
    private static final String MEMORY_PERSISTENCE_UNAVAILABLE = "unavailable";
    private static final Set<String> TERMINAL_AGENT_STATUSES = Set.of("REJECTED", "RETIRED", "DELETED", "BLOCKED");

    // P1-9: Security scan patterns for detecting suspicious agent definitions
    private static final Pattern SUSPICIOUS_CODE_PATTERN = Pattern.compile(
        "(eval|exec|system|Runtime\\.getRuntime|ProcessBuilder|Class\\.forName"
            + "|Thread\\.sleep|AccessController|Method\\.invoke"
            + "|Runtime\\.exec|ScriptEngine|Compilable|javax\\.script"
            + "|sun\\.misc\\.Unsafe|java\\.lang\\.reflect|JVM|attach|instrument"
            + "|setAccessible|getDeclaredMethod|getDeclaredField"
            + "|ObjectInputStream|readObject|deserializ|pickle\\.loads"
            + "|__import__|importlib|subprocess|os\\.system|os\\.popen"
            + "|powershell|cmd\\.exe|bash|/bin/sh)",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern SUSPICIOUS_URL_PATTERN = Pattern.compile(
        "(http://(?!localhost|127\\.0\\.0\\.1|\\[::1\\])[^\\s/$.?#][^\\s]*"
            + "|https://(?!localhost|127\\.0\\.0\\.1|\\[::1\\])[^\\s/$.?#][^\\s]*"
            + "|ftp://[^\\s/$.?#][^\\s]*"
            + "|file://[^\\s]*"
            + "|data:[^,]*base64"
            + "|blob:)",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern SUSPICIOUS_PATH_PATTERN = Pattern.compile(
        "(\\.\\.[\\\\/]"
            + "|/etc/passwd|/etc/shadow|/etc/sudoers|/etc/hosts|/proc/"
            + "|/sys/|/dev/|/root/|/home/\\w+/\\.ssh"
            + "|C:\\\\Windows|C:\\\\System32|C:\\\\Users\\\\.*AppData"
            + "|\\\\\\\\.\\\\pipe\\\\|%APPDATA%|%SYSTEMROOT%"
            + "|/var/run/docker\\.sock|\\.pem$|\\.key$|\\.pfx$|\\.p12$)",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern SUSPICIOUS_INJECTION_PATTERN = Pattern.compile(
        "(\\$\\{[^}]+\\}"          // Expression language injection e.g. ${7*7}
            + "|#\\{[^}]+\\}"      // EL injection #{user.password}
            + "|\\{\\{[^}]+\\}\\}" // Template injection {{config}}
            + "|<script[^>]*>"     // XSS / HTML injection
            + "|javascript:"       // Inline JS
            + "|vbscript:"
            + "|on\\w+\\s*=\\s*[\"']?\\s*(alert|eval|exec)" // event handler injection
            + "|\\bSELECT\\b.*\\bFROM\\b|\\bINSERT\\b.*\\bINTO\\b"  // SQL injection fragments
            + "|\\bDROP\\s+TABLE\\b|\\bTRUNCATE\\s+TABLE\\b"
            + "|;\\s*(sleep|waitfor)\\s*\\()" // SQL time-based blind injection
            + "|UNION\\s+(ALL\\s+)?SELECT", // UNION-based SQL injection
        Pattern.CASE_INSENSITIVE);

    private final AepEngine engine;
    /** Agent registry store backed by Data-Cloud EntityStore. Null when Data-Cloud is absent. */
    @Nullable
    private final EventCloudAgentStore agentStore;
    /** Data-Cloud client retained for memory operations (episodes, facts, policies). */
    @Nullable
    private final DataCloudClient agentDataCloud;
    /** Optional SLO metrics recorder for agent execution outcomes. */
    @Nullable
    private final AepSloMetrics sloMetrics;

    /**
     * Creates an agent controller backed by the canonical {@link EventCloudAgentStore}.
     *
     * @param engine      AEP engine for event processing
     * @param agentDataCloud optional Data-Cloud client; when non-null a store is created
     *                       via {@code agentDataCloud.entityStore()} and memory endpoints
     *                       are also enabled
     */
    public AgentController(AepEngine engine, @Nullable DataCloudClient agentDataCloud) {
        this(engine, agentDataCloud, null);
    }

    /**
     * Creates an agent controller with optional SLO metrics recording.
     *
     * @param engine         AEP engine for event processing
     * @param agentDataCloud optional Data-Cloud client and store provider
     * @param sloMetrics     optional SLO metrics recorder for execution outcomes
     */
    public AgentController(
            AepEngine engine,
            @Nullable DataCloudClient agentDataCloud,
            @Nullable AepSloMetrics sloMetrics) {
        this.engine = engine;
        this.agentDataCloud = agentDataCloud;
        this.sloMetrics = sloMetrics;
        EntityStore entityStore = agentDataCloud != null ? agentDataCloud.entityStore() : null;
        this.agentStore = entityStore != null ? new EventCloudAgentStore(entityStore) : null;
    }

    // P1-9: Security scan for agent registration
    private List<String> performSecurityScan(Map<String, Object> agentData) {
        List<String> securityIssues = new ArrayList<>();
        String serialized = agentData.toString();

        // Check for suspicious code patterns
        if (SUSPICIOUS_CODE_PATTERN.matcher(serialized).find()) {
            securityIssues.add("Suspicious code execution patterns detected");
        }

        // Check for suspicious URLs
        if (SUSPICIOUS_URL_PATTERN.matcher(serialized).find()) {
            securityIssues.add("External URLs detected in agent definition");
        }

        // Check for suspicious file paths
        if (SUSPICIOUS_PATH_PATTERN.matcher(serialized).find()) {
            securityIssues.add("Suspicious file path patterns detected");
        }

        // Check for injection patterns (EL, template, SQL, XSS)
        if (SUSPICIOUS_INJECTION_PATTERN.matcher(serialized).find()) {
            securityIssues.add("Injection attack patterns detected");
        }

        return securityIssues;
    }

    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleRegisterAgent(HttpRequest request) {
        if (agentStore == null) {
            return Promise.of(HttpHelper.errorResponse(503,
                "Agent registry not available — DataCloudClient not configured"));
        }

        String requestTenantId = HttpHelper.resolveTenantId(request);

        return request.loadBody().then(buf -> {
            try {
                String bodyStr = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> reqBody = bodyStr.isBlank()
                    ? Map.of()
                    : HttpHelper.mapper().readValue(bodyStr, Map.class);
                String tenantId = resolveBoundTenantId(requestTenantId, reqBody.get("tenantId"));

                // P1-9: Perform security scan before registration
                List<String> securityIssues = performSecurityScan(reqBody);
                if (!securityIssues.isEmpty()) {
                    log.warn("[agents] security scan failed for tenantId={}: {}",
                        tenantId, String.join(", ", securityIssues));
                    return Promise.of(HttpHelper.errorResponse(403,
                        "Security scan failed: " + String.join(", ", securityIssues)));
                }

                String agentId = reqBody.containsKey("id")
                    ? (String) reqBody.get("id")
                    : "agent-" + java.util.UUID.randomUUID();

                // Create agent entity
                Map<String, Object> agentData = new java.util.HashMap<>(reqBody);
                agentData.put("id", agentId);
                agentData.put("tenantId", tenantId);
                agentData.put("status", "ACTIVE");
                agentData.putIfAbsent("version", "1.0.0");
                agentData.putIfAbsent("capabilities", List.of());
                agentData.putIfAbsent("memoryCount", 0);
                agentData.putIfAbsent("registrationMode", REGISTRATION_MODE_DIRECT);
                agentData.putIfAbsent("executable", true);
                agentData.put("createdAt", Instant.now().toString());

                return agentStore.save(tenantId, agentId, agentData)
                    .map(ignored -> HttpHelper.jsonResponse(Map.of(
                        "id", agentId,
                        "tenantId", tenantId,
                        "status", "ACTIVE",
                        "timestamp", Instant.now().toString()
                    )))
                    .then(Promise::of, e -> {
                        log.error("[agents] registration failed for agentId={}: {}",
                            agentId, e.getMessage(), e);
                        return Promise.of(HttpHelper.errorResponse(500,
                            "Failed to register agent: " + e.getMessage()));
                    });
            } catch (SecurityException e) {
                return Promise.of(HttpHelper.errorResponse(403,
                    "Forbidden: " + e.getMessage()));
            } catch (Exception e) {
                return Promise.of(HttpHelper.errorResponse(400,
                    "Invalid request: " + e.getMessage()));
            }
        }, e -> Promise.of(HttpHelper.errorResponse(400, "Failed to read request body")));
    }

    public Promise<HttpResponse> handleListAgents(HttpRequest request) {
        String tenantId = HttpHelper.resolveTenantId(request);
        if (agentStore == null) {
            return Promise.of(HttpHelper.jsonResponse(Map.of(
                "tenantId", tenantId,
                "agents", List.of(),
                "count", 0,
                "configured", false,
                "message", "Agent registry not available — DataCloudClient not configured",
                "timestamp", Instant.now().toString()
            )));
        }
        String limitParam = request.getQueryParameter("limit");
        int limit = limitParam != null ? Math.min(Integer.parseInt(limitParam), 1000) : 1000;
        return agentStore.listAgents(tenantId, limit)
            .map(entities -> {
                List<Map<String, Object>> summaries = entities.stream()
                    .map(e -> summarizeAgentEntity(tenantId, e))
                    .toList();
                return HttpHelper.jsonResponse(Map.of(
                    "tenantId", tenantId,
                    "agents", summaries,
                    "count", summaries.size(),
                    "timestamp", Instant.now().toString()
                ));
            })
            .then(Promise::of, e -> {
                log.error("[agents] list failed for tenant={}: {}", tenantId, e.getMessage(), e);
                return Promise.of(HttpHelper.errorResponse(500,
                    "Failed to list agents: " + e.getMessage()));
            });
    }

    public Promise<HttpResponse> handleGetAgent(HttpRequest request) {
        String agentId = request.getPathParameter("agentId");
        if (agentId == null || agentId.isBlank()) {
            return Promise.of(HttpHelper.errorResponse(400,
                "agentId path parameter is required"));
        }
        if (agentStore == null) {
            return Promise.of(HttpHelper.errorResponse(503,
                "Agent registry not available — DataCloudClient not configured"));
        }
        String tenantId = HttpHelper.resolveTenantId(request);
        return agentStore.findById(tenantId, agentId)
            .map(opt -> opt
                .map(e -> HttpHelper.jsonResponse(detailAgentEntity(tenantId, e)))
                .orElse(HttpHelper.errorResponse(404, "Agent not found: " + agentId)))
            .then(Promise::of, e -> {
                log.error("[agents] get failed for agentId={}: {}", agentId, e.getMessage(), e);
                return Promise.of(HttpHelper.errorResponse(500,
                    "Failed to get agent: " + e.getMessage()));
            });
    }

    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleExecuteAgent(HttpRequest request) {
        // WS4: Enforce agent:execute permission before allowing agent execution
        com.ghatana.aep.security.AepAuthFilter.JwtPayload jwtPayload = 
            request.getAttachment(com.ghatana.aep.security.AepAuthFilter.JWT_PAYLOAD_ATTACHMENT);
        if (jwtPayload == null || !jwtPayload.hasPermission("agent:execute")) {
            log.warn("[agents] execute rejected for agentId={} - missing agent:execute permission", 
                request.getPathParameter("agentId"));
            return Promise.of(HttpHelper.errorResponse(403,
                "Permission required: agent:execute. Access denied."));
        }

        String agentId = request.getPathParameter("agentId");
        Instant startedAt = Instant.now();
        if (agentId == null || agentId.isBlank()) {
            return Promise.of(HttpHelper.errorResponse(400,
                "agentId path parameter is required"));
        }
        if (agentStore == null) {
            return Promise.of(HttpHelper.errorResponse(503,
                "Agent execution requires DataCloud-backed registry"));
        }
        String requestTenantId = HttpHelper.resolveTenantId(request);
        return request.loadBody().then(buf -> {
            try {
                String bodyStr = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> reqBody = bodyStr.isBlank()
                    ? Map.of()
                    : HttpHelper.mapper().readValue(bodyStr, Map.class);
                String tenantId = resolveBoundTenantId(requestTenantId, reqBody.get("tenantId"));
                Object rawInput = reqBody.get("input");
                if (rawInput != null && !(rawInput instanceof Map<?, ?>)) {
                    recordAgentExecutionFailure(
                        tenantId,
                        agentId,
                        "INVALID_INPUT",
                        "permanent",
                        false,
                        startedAt);
                    return Promise.of(HttpHelper.errorResponse(
                        400,
                        "Invalid request: input must be an object",
                        Map.of(
                            "errorCode", "INVALID_INPUT",
                            "category", "permanent",
                            "retryable", false,
                            "suggestion", "Send input as a JSON object under the 'input' field"
                        )
                    ));
                }
                Map<String, Object> input = rawInput instanceof Map<?, ?>
                    ? (Map<String, Object>) rawInput
                    : Map.of();

                return agentStore.findById(tenantId, agentId).then(agentOpt -> {
                    if (agentOpt.isEmpty()) {
                        recordAgentExecutionFailure(
                            tenantId,
                            agentId,
                            "AGENT_NOT_REGISTERED",
                            "permanent",
                            false,
                            startedAt);
                        return Promise.of(HttpHelper.errorResponse(
                            404,
                            "Agent not found: " + agentId,
                            Map.of(
                                "errorCode", "AGENT_NOT_REGISTERED",
                                "category", "permanent",
                                "retryable", false
                            )
                        ));
                    }

                    Map<String, Object> agent = agentOpt.get().data();
                    if (!isExecutable(agent)) {
                        recordAgentExecutionFailure(
                            tenantId,
                            agentId,
                            "AGENT_NOT_EXECUTABLE",
                            "permanent",
                            false,
                            startedAt);
                        return Promise.of(HttpHelper.errorResponse(
                            409,
                            "Agent is not executable: " + agentId,
                            Map.of(
                                "errorCode", "AGENT_NOT_EXECUTABLE",
                                "category", "permanent",
                                "retryable", false
                            )
                        ));
                    }

                    if (!isApprovedForExecution(agent)) {
                        recordAgentExecutionFailure(
                            tenantId,
                            agentId,
                            "AGENT_NOT_APPROVED",
                            "permanent",
                            false,
                            startedAt);
                        return Promise.of(HttpHelper.errorResponse(
                            403,
                            "Agent is not approved for execution: " + agentId,
                            Map.of(
                                "errorCode", "AGENT_NOT_APPROVED",
                                "category", "permanent",
                                "retryable", false
                            )
                        ));
                    }

                    Map<String, Object> payload = new java.util.HashMap<>(input);
                    payload.put("agentId", agentId);
                    payload.put("tenantId", tenantId);

                    AepEngine.Event event = new AepEngine.Event(
                        "agent.invocation",
                        Map.copyOf(payload),
                        Map.of("agentId", agentId),
                        Instant.now()
                    );
                    return engine.process(tenantId, event)
                        .map(result -> {
                            recordAgentExecutionSuccess(tenantId, agentId, startedAt);
                            return HttpHelper.jsonResponse(Map.of(
                                "agentId", agentId,
                                "tenantId", tenantId,
                                "eventId", result.eventId(),
                                "success", result.success(),
                                "detections", result.detections().size(),
                                "timestamp", Instant.now().toString()
                            ));
                        })
                        .then(Promise::of, e -> {
                            log.error("[agents] execute failed for agentId={}: {}",
                                agentId, e.getMessage(), e);
                            return Promise.of(toAgentExecutionErrorResponse(e, agentId, tenantId, startedAt));
                        });
                });
            } catch (SecurityException e) {
                recordAgentExecutionFailure(
                    requestTenantId,
                    agentId,
                    "TENANT_OVERRIDE_FORBIDDEN",
                    "permanent",
                    false,
                    startedAt);
                return Promise.of(HttpHelper.errorResponse(403,
                    "Forbidden: " + e.getMessage()));
            } catch (Exception e) {
                recordAgentExecutionFailure(
                    "unknown",
                    agentId,
                    "INVALID_REQUEST",
                    "permanent",
                    false,
                    startedAt);
                return Promise.of(HttpHelper.errorResponse(400,
                    "Invalid request: " + e.getMessage()));
            }
        }, e -> Promise.of(HttpHelper.errorResponse(400, "Failed to read request body")));
    }

    private HttpResponse toAgentExecutionErrorResponse(
            Throwable error,
            String agentId,
            String tenantId,
            Instant startedAt) {
        Throwable rootCause = rootCause(error);
        String message = safeErrorMessage(rootCause);
        String normalized = message.toLowerCase(Locale.ROOT);

        int statusCode = 500;
        String errorCode = "AGENT_EXECUTION_FAILED";
        String category = "transient";
        boolean retryable = true;
        String suggestion = "Retry the request; if the issue persists, inspect AEP runtime logs for this tenant and agent";

        if (rootCause instanceof SecurityException || normalized.contains("forbidden") || normalized.contains("unauthorized")) {
            statusCode = 403;
            errorCode = "AGENT_EXECUTION_FORBIDDEN";
            category = "permanent";
            retryable = false;
            suggestion = "Verify principal permissions and tenant access before retrying";
        } else if (rootCause instanceof IllegalArgumentException || normalized.contains("invalid") || normalized.contains("validation")) {
            statusCode = 400;
            errorCode = "AGENT_EXECUTION_INVALID_REQUEST";
            category = "permanent";
            retryable = false;
            suggestion = "Fix request payload and metadata, then retry";
        } else if (rootCause instanceof TimeoutException || normalized.contains("timeout")) {
            statusCode = 504;
            errorCode = "AGENT_EXECUTION_TIMEOUT";
            category = "transient";
            retryable = true;
            suggestion = "Retry with smaller input scope or higher upstream timeout budget";
        } else if (normalized.contains("unavailable") || normalized.contains("connection refused")
            || normalized.contains("temporarily") || normalized.contains("downstream")) {
            statusCode = 503;
            errorCode = "AGENT_EXECUTION_DEPENDENCY_UNAVAILABLE";
            category = "transient";
            retryable = true;
            suggestion = "Check dependent services and connectivity, then retry";
        } else if (normalized.contains("not found")) {
            statusCode = 404;
            errorCode = "AGENT_EXECUTION_TARGET_NOT_FOUND";
            category = "permanent";
            retryable = false;
            suggestion = "Ensure the target agent exists for the tenant before executing";
        }

        recordAgentExecutionFailure(tenantId, agentId, errorCode, category, retryable, startedAt);

        return HttpHelper.errorResponse(
            statusCode,
            "Agent execution failed: " + message,
            Map.of(
                "errorCode", errorCode,
                "category", category,
                "retryable", retryable,
                "tenantId", tenantId,
                "agentId", agentId,
                "suggestion", suggestion
            )
        );
    }

    private void recordAgentExecutionSuccess(String tenantId, String agentId, Instant startedAt) {
        if (sloMetrics == null) {
            return;
        }
        long durationMs = Math.max(0L, Duration.between(startedAt, Instant.now()).toMillis());
        try {
            sloMetrics.recordAgentExecutionSuccess(tenantId, agentId, durationMs);
        } catch (Exception metricsError) {
            log.debug("[agents] failed to record success metrics for agentId={}: {}",
                agentId, metricsError.getMessage());
        }
    }

    private void recordAgentExecutionFailure(
            String tenantId,
            String agentId,
            String errorCode,
            String category,
            boolean retryable,
            Instant startedAt) {
        if (sloMetrics == null) {
            return;
        }
        long durationMs = Math.max(0L, Duration.between(startedAt, Instant.now()).toMillis());
        try {
            sloMetrics.recordAgentExecutionFailure(
                tenantId,
                agentId,
                errorCode,
                category,
                retryable,
                durationMs);
        } catch (Exception metricsError) {
            log.debug("[agents] failed to record failure metrics for agentId={}: {}",
                agentId, metricsError.getMessage());
        }
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor.getCause() != null && cursor.getCause() != cursor) {
            cursor = cursor.getCause();
        }
        return cursor;
    }

    private static String safeErrorMessage(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return message;
    }

    private static String resolveBoundTenantId(String requestTenantId, @Nullable Object bodyTenantRaw) {
        String resolvedRequestTenant = requestTenantId == null ? "" : requestTenantId.trim();
        String bodyTenant = String.valueOf(bodyTenantRaw).trim();
        if ("default".equalsIgnoreCase(resolvedRequestTenant) && !bodyTenant.isBlank()
            && !"null".equalsIgnoreCase(bodyTenant)) {
            return bodyTenant;
        }
        if (resolvedRequestTenant.isBlank()) {
            if (!bodyTenant.isBlank() && !"null".equalsIgnoreCase(bodyTenant)) {
                return bodyTenant;
            }
            throw new IllegalArgumentException("Tenant context is required");
        }
        if (bodyTenantRaw == null || bodyTenant.isBlank() || "null".equalsIgnoreCase(bodyTenant)) {
            return resolvedRequestTenant;
        }
        if (!resolvedRequestTenant.equals(bodyTenant)) {
            throw new SecurityException("tenantId in request body cannot override authenticated tenant context");
        }
        return resolvedRequestTenant;
    }

    private static boolean isApprovedForExecution(Map<String, Object> agentData) {
        Object approved = agentData.get("approved");
        if (approved instanceof Boolean approvedFlag && !approvedFlag) {
            return false;
        }
        Object status = agentData.get("status");
        if (!(status instanceof String statusValue)) {
            return true;
        }
        String normalized = statusValue.trim().toUpperCase(Locale.ROOT);
        return !List.of("PENDING", "REJECTED", "BLOCKED", "DRAFT").contains(normalized);
    }

    public Promise<HttpResponse> handleGetAgentMemory(HttpRequest request) {
        String agentId = request.getPathParameter("agentId");
        if (agentId == null || agentId.isBlank()) {
            return Promise.of(HttpHelper.errorResponse(400,
                "agentId path parameter is required"));
        }
        if (agentDataCloud == null) {
            return Promise.of(HttpHelper.errorResponse(503,
                "Agent memory not available — DataCloudClient not configured"));
        }
        String tenantId = HttpHelper.resolveTenantId(request);
        DataCloudClient.Query query = DataCloudClient.Query.builder()
            .filter(DataCloudClient.Filter.eq("agentId", agentId))
            .limit(10_000)
            .build();
        return agentDataCloud.query(tenantId, "dc_memory", query)
            .map(items -> {
                long episodic = items.stream()
                    .filter(e -> "EPISODIC".equals(e.data().get("type"))).count();
                long semantic = items.stream()
                    .filter(e -> "SEMANTIC".equals(e.data().get("type"))).count();
                long procedural = items.stream()
                    .filter(e -> "PROCEDURAL".equals(e.data().get("type"))).count();
                long preference = items.stream()
                    .filter(e -> "PREFERENCE".equals(e.data().get("type"))).count();
                long other = items.size() - episodic - semantic - procedural - preference;
                return HttpHelper.jsonResponse(Map.of(
                    "agentId", agentId,
                    "tenantId", tenantId,
                    "total", items.size(),
                    "byType", Map.of(
                        "episodic", episodic,
                        "semantic", semantic,
                        "procedural", procedural,
                        "preference", preference,
                        "other", other
                    ),
                    "timestamp", Instant.now().toString()
                ));
            })
            .then(Promise::of, e -> {
                log.error("[agents] memory query failed for agentId={}: {}",
                    agentId, e.getMessage(), e);
                return Promise.of(HttpHelper.errorResponse(500,
                    "Failed to query agent memory: " + e.getMessage()));
            });
    }

    public Promise<HttpResponse> handleGetAgentEpisodes(HttpRequest request) {
        String agentId = request.getPathParameter("agentId");
        if (agentId == null || agentId.isBlank()) {
            return Promise.of(HttpHelper.errorResponse(400,
                "agentId path parameter is required"));
        }
        if (agentDataCloud == null) {
            return Promise.of(HttpHelper.errorResponse(503,
                "Episode store not available — DataCloudClient not configured"));
        }
        String tenantId = HttpHelper.resolveTenantId(request);
        String limitParam = request.getQueryParameter("limit");
        int limit = limitParam != null ? Math.min(Integer.parseInt(limitParam), 500) : 50;
        DataCloudClient.Query query = DataCloudClient.Query.builder()
            .filters(List.of(
                DataCloudClient.Filter.eq("agentId", agentId),
                DataCloudClient.Filter.eq("type", "EPISODIC")
            ))
            .limit(limit)
            .build();
        return agentDataCloud.query(tenantId, "dc_memory", query)
            .map(items -> {
                var episodes = items.stream()
                    .map(e -> {
                        Map<String, Object> ep = new java.util.HashMap<>(e.data());
                        ep.put("id", e.id());
                        return ep;
                    })
                    .toList();
                return HttpHelper.jsonResponse(Map.of(
                    "agentId", agentId,
                    "tenantId", tenantId,
                    "episodes", episodes,
                    "count", episodes.size(),
                    "timestamp", Instant.now().toString()
                ));
            })
            .then(Promise::of, e -> {
                log.error("[agents] episodes query failed for agentId={}: {}",
                    agentId, e.getMessage(), e);
                return Promise.of(HttpHelper.errorResponse(500,
                    "Failed to query agent episodes: " + e.getMessage()));
            });
    }

    public Promise<HttpResponse> handleGetAgentFacts(HttpRequest request) {
        String agentId = request.getPathParameter("agentId");
        if (agentId == null || agentId.isBlank()) {
            return Promise.of(HttpHelper.errorResponse(400,
                "agentId path parameter is required"));
        }
        if (agentDataCloud == null) {
            return Promise.of(HttpHelper.errorResponse(503,
                "Fact store not available — DataCloudClient not configured"));
        }
        String tenantId = HttpHelper.resolveTenantId(request);
        String limitParam = request.getQueryParameter("limit");
        int limit = limitParam != null ? Math.min(Integer.parseInt(limitParam), 500) : 100;
        DataCloudClient.Query query = DataCloudClient.Query.builder()
            .filters(List.of(
                DataCloudClient.Filter.eq("agentId", agentId),
                DataCloudClient.Filter.eq("type", "SEMANTIC")
            ))
            .limit(limit)
            .build();
        return agentDataCloud.query(tenantId, "dc_memory", query)
            .map(items -> {
                var facts = items.stream()
                    .map(e -> {
                        Map<String, Object> fact = new java.util.HashMap<>(e.data());
                        fact.put("id", e.id());
                        return fact;
                    })
                    .toList();
                return HttpHelper.jsonResponse(Map.of(
                    "agentId", agentId,
                    "tenantId", tenantId,
                    "facts", facts,
                    "count", facts.size(),
                    "timestamp", Instant.now().toString()
                ));
            })
            .then(Promise::of, e -> {
                log.error("[agents] facts query failed for agentId={}: {}",
                    agentId, e.getMessage(), e);
                return Promise.of(HttpHelper.errorResponse(500,
                    "Failed to query agent facts: " + e.getMessage()));
            });
    }

    public Promise<HttpResponse> handleGetAgentPolicies(HttpRequest request) {
        String agentId = request.getPathParameter("agentId");
        if (agentId == null || agentId.isBlank()) {
            return Promise.of(HttpHelper.errorResponse(400,
                "agentId path parameter is required"));
        }
        if (agentDataCloud == null) {
            return Promise.of(HttpHelper.errorResponse(503,
                "Policy store not available — DataCloudClient not configured"));
        }
        String tenantId = HttpHelper.resolveTenantId(request);
        String limitParam = request.getQueryParameter("limit");
        int limit = limitParam != null ? Math.min(Integer.parseInt(limitParam), 200) : 50;
        DataCloudClient.Query query = DataCloudClient.Query.builder()
            .filters(List.of(
                DataCloudClient.Filter.eq("agentId", agentId),
                DataCloudClient.Filter.eq("type", "PROCEDURAL")
            ))
            .limit(limit)
            .build();
        return agentDataCloud.query(tenantId, "dc_memory", query)
            .map(items -> {
                var policies = items.stream()
                    .map(e -> {
                        Map<String, Object> policy = new java.util.HashMap<>(e.data());
                        policy.put("id", e.id());
                        return policy;
                    })
                    .toList();
                return HttpHelper.jsonResponse(Map.of(
                    "agentId", agentId,
                    "tenantId", tenantId,
                    "policies", policies,
                    "count", policies.size(),
                    "timestamp", Instant.now().toString()
                ));
            })
            .then(Promise::of, e -> {
                log.error("[agents] policies query failed for agentId={}: {}",
                    agentId, e.getMessage(), e);
                return Promise.of(HttpHelper.errorResponse(500,
                    "Failed to query agent policies: " + e.getMessage()));
            });
    }

    public Promise<HttpResponse> handleDeregisterAgent(HttpRequest request) {
        String agentId = request.getPathParameter("agentId");
        if (agentId == null || agentId.isBlank()) {
            return Promise.of(HttpHelper.errorResponse(400,
                "agentId path parameter is required"));
        }
        if (agentStore == null) {
            return Promise.of(HttpHelper.errorResponse(503,
                "Agent registry not available — DataCloudClient not configured"));
        }
        String tenantId = HttpHelper.resolveTenantId(request);
        return agentStore.findById(tenantId, agentId)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.of(HttpHelper.errorResponse(404,
                        "Agent not found: " + agentId));
                }
                return agentStore.delete(tenantId, agentId)
                    .map(ignored -> HttpHelper.jsonResponse(Map.of(
                        "deleted", true,
                        "agentId", agentId,
                        "tenantId", tenantId,
                        "timestamp", Instant.now().toString()
                    )));
            })
            .then(Promise::of, e -> {
                log.error("[agents] deregister failed for agentId={}: {}",
                    agentId, e.getMessage(), e);
                return Promise.of(HttpHelper.errorResponse(500,
                    "Failed to deregister agent: " + e.getMessage()));
            });
    }

    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleLifecycleTransition(HttpRequest request) {
        String agentId = request.getPathParameter("agentId");
        if (agentId == null || agentId.isBlank()) {
            return Promise.of(HttpHelper.errorResponse(400,
                "agentId path parameter is required"));
        }
        if (agentStore == null) {
            return Promise.of(HttpHelper.errorResponse(503,
                "Agent registry not available — DataCloudClient not configured"));
        }

        String tenantId = HttpHelper.resolveTenantId(request);
        String action = request.getPathParameter("action");
        if (action == null || action.isBlank()) {
            action = request.getQueryParameter("action");
        }
        if (action == null || action.isBlank()) {
            return Promise.of(HttpHelper.errorResponse(400, "Lifecycle action is required"));
        }
        String normalizedAction = action.trim().toLowerCase(Locale.ROOT);

        String targetStatus = mapLifecycleActionToStatus(normalizedAction);
        if (targetStatus == null && !isAgentSimulationAction(normalizedAction)) {
            return Promise.of(HttpHelper.errorResponse(400,
                "Unsupported agent lifecycle action: " + action));
        }

        return agentStore.findById(tenantId, agentId)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.of(HttpHelper.errorResponse(404,
                        "Agent not found: " + agentId));
                }

                Map<String, Object> current = opt.get().data();
                String currentStatus = normalizeStatus(current.get("status"));
                if (!isTransitionAllowed(currentStatus, normalizedAction, targetStatus)) {
                    return Promise.of(HttpHelper.errorResponse(409,
                        "Invalid lifecycle transition for agent status: " + currentStatus,
                        Map.of(
                            "agentId", agentId,
                            "currentStatus", currentStatus,
                            "action", normalizedAction
                        )));
                }

                Promise<AepEngine.ProcessingResult> provenancePromise = emitAgentLifecycleEvent(
                    tenantId,
                    agentId,
                    normalizedAction,
                    currentStatus,
                    targetStatus
                );

                if (isAgentSimulationAction(normalizedAction)) {
                    return provenancePromise.map(result -> HttpHelper.jsonResponse(Map.of(
                        "agentId", agentId,
                        "tenantId", tenantId,
                        "action", normalizedAction,
                        "simulationRequested", true,
                        "eventId", result.eventId(),
                        "timestamp", Instant.now().toString()
                    )));
                }

                Map<String, Object> updated = new HashMap<>(current);
                updated.put("status", targetStatus);
                updated.put("lastLifecycleAction", normalizedAction);
                updated.put("updatedAt", Instant.now().toString());
                if ("approve".equals(normalizedAction) || "install".equals(normalizedAction)) {
                    updated.put("approved", true);
                }

                return agentStore.save(tenantId, agentId, updated)
                    .then(ignored -> provenancePromise)
                    .map(result -> HttpHelper.jsonResponse(Map.of(
                        "agentId", agentId,
                        "tenantId", tenantId,
                        "action", normalizedAction,
                        "previousStatus", currentStatus,
                        "status", targetStatus,
                        "transitioned", true,
                        "eventId", result.eventId(),
                        "timestamp", Instant.now().toString()
                    )));
            })
            .then(Promise::of, e -> {
                log.error("[agents] lifecycle transition failed for agentId={}: {}",
                    agentId, e.getMessage(), e);
                return Promise.of(HttpHelper.errorResponse(500,
                    "Failed lifecycle transition: " + e.getMessage()));
            });
    }

    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleRecordAgentReview(HttpRequest request) {
        String agentId = request.getPathParameter("agentId");
        if (agentId == null || agentId.isBlank()) {
            return Promise.of(HttpHelper.errorResponse(400,
                "agentId path parameter is required"));
        }
        if (agentStore == null) {
            return Promise.of(HttpHelper.errorResponse(503,
                "Agent registry not available — DataCloudClient not configured"));
        }

        String tenantId = HttpHelper.resolveTenantId(request);
        return request.loadBody().then(buf -> {
            try {
                String bodyStr = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> payload = bodyStr.isBlank()
                    ? Map.of()
                    : HttpHelper.mapper().readValue(bodyStr, Map.class);

                String outcome = asNonBlankString(payload.get("outcome"));
                if (outcome == null) {
                    return Promise.of(HttpHelper.errorResponse(400,
                        "Review outcome is required"));
                }

                Map<String, Object> reviewPayload = new HashMap<>();
                reviewPayload.put("outcome", outcome);
                String reviewer = asNonBlankString(payload.get("reviewer"));
                if (reviewer != null) {
                    reviewPayload.put("reviewer", reviewer);
                }
                String rationale = asNonBlankString(payload.get("rationale"));
                if (rationale != null) {
                    reviewPayload.put("rationale", rationale);
                }
                Object score = payload.get("score");
                if (score instanceof Number number) {
                    reviewPayload.put("score", number.doubleValue());
                }

                return emitAgentReviewEvent(tenantId, agentId, reviewPayload)
                    .map(result -> HttpHelper.jsonResponse(Map.of(
                        "agentId", agentId,
                        "tenantId", tenantId,
                        "recorded", true,
                        "eventId", result.eventId(),
                        "timestamp", Instant.now().toString()
                    )));
            } catch (Exception e) {
                return Promise.of(HttpHelper.errorResponse(400,
                    "Invalid review payload: " + e.getMessage()));
            }
        }, e -> Promise.of(HttpHelper.errorResponse(400, "Failed to read request body")));
    }

    private Map<String, Object> summarizeAgentEntity(String tenantId, EntityStore.Entity entity) {
        Map<String, Object> data = entity.data();
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("id", data.getOrDefault("id", entity.id().value()));
        response.put("name", data.getOrDefault("name", entity.id().value()));
        response.put("tenantId", data.getOrDefault("tenantId", tenantId));
        response.put("version", data.getOrDefault("version", "1.0.0"));
        response.put("type", data.getOrDefault("type", "unknown"));
        response.put("status", data.getOrDefault("status", "ACTIVE"));
        response.put("capabilities", normalizeCapabilities(data.get("capabilities")));
        response.put("memoryCount", normalizeInteger(data.get("memoryCount")));
        response.put("registeredAt", normalizeTimestamp(data, "registeredAt", "createdAt"));
        response.put("lastSeen", normalizeOptionalTimestamp(data, "lastSeen", "updatedAt"));
        response.put("description", data.getOrDefault("description", ""));
        response.put("registrationMode", normalizeRegistrationMode(data.get("registrationMode")));
        response.put("executable", isExecutable(data));
        response.put("registryStorage", agentStore != null ? REGISTRY_STORAGE_DATACLOUD : REGISTRY_STORAGE_UNCONFIGURED);
        response.put("memoryPersistence", agentDataCloud != null ? MEMORY_PERSISTENCE_DATACLOUD : MEMORY_PERSISTENCE_UNAVAILABLE);
        return response;
    }

    private Map<String, Object> detailAgentEntity(String tenantId, EntityStore.Entity entity) {
        Map<String, Object> response = new java.util.LinkedHashMap<>(summarizeAgentEntity(tenantId, entity));
        response.put("config", entity.data().getOrDefault("config", Map.of()));
        response.put("timestamp", Instant.now().toString());
        return response;
    }

    private static List<String> normalizeCapabilities(@Nullable Object capabilitiesValue) {
        if (capabilitiesValue instanceof List<?> capabilities) {
            return capabilities.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private static int normalizeInteger(@Nullable Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    private static String normalizeTimestamp(Map<String, Object> data, String preferredKey, String fallbackKey) {
        Object value = data.get(preferredKey);
        if (value == null || String.valueOf(value).isBlank()) {
            value = data.get(fallbackKey);
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return Instant.EPOCH.toString();
        }
        return String.valueOf(value);
    }

    @Nullable
    private static String normalizeOptionalTimestamp(Map<String, Object> data, String preferredKey, String fallbackKey) {
        Object value = data.get(preferredKey);
        if (value == null || String.valueOf(value).isBlank()) {
            value = data.get(fallbackKey);
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return String.valueOf(value);
    }

    private static String normalizeRegistrationMode(@Nullable Object value) {
        String registrationMode = value != null ? String.valueOf(value) : REGISTRATION_MODE_DIRECT;
        if (registrationMode.isBlank()) {
            return REGISTRATION_MODE_DIRECT;
        }
        return registrationMode;
    }

    private static boolean isExecutable(Map<String, Object> data) {
        Object executable = data.get("executable");
        if (executable instanceof Boolean executableFlag) {
            return executableFlag;
        }
        return !REGISTRATION_MODE_MANIFEST_ONLY.equals(normalizeRegistrationMode(data.get("registrationMode")));
    }

    private Promise<AepEngine.ProcessingResult> emitAgentLifecycleEvent(
            String tenantId,
            String agentId,
            String action,
            String previousStatus,
            @Nullable String targetStatus) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("agentId", agentId);
        payload.put("action", action);
        payload.put("previousStatus", previousStatus);
        if (targetStatus != null) {
            payload.put("targetStatus", targetStatus);
        }
        payload.put("requestedAt", Instant.now().toString());

        AepEngine.Event event = new AepEngine.Event(
            "agent.lifecycle.transition",
            Map.copyOf(payload),
            Map.of("agentId", agentId),
            Instant.now()
        );
        return engine.process(tenantId, event);
    }

    private Promise<AepEngine.ProcessingResult> emitAgentReviewEvent(
            String tenantId,
            String agentId,
            Map<String, Object> reviewPayload) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("agentId", agentId);
        payload.put("review", Map.copyOf(reviewPayload));
        payload.put("recordedAt", Instant.now().toString());

        AepEngine.Event event = new AepEngine.Event(
            "agent.review.recorded",
            Map.copyOf(payload),
            Map.of("agentId", agentId),
            Instant.now()
        );
        return engine.process(tenantId, event);
    }

    @Nullable
    private static String mapLifecycleActionToStatus(String action) {
        return switch (action) {
            case "scan" -> "SCANNED";
            case "approve" -> "APPROVED";
            case "install" -> "INSTALLED";
            case "configure" -> "CONFIGURED";
            case "execute" -> "ACTIVE";
            case "review" -> "REVIEWED";
            case "learn" -> "LEARNING";
            case "retire" -> "RETIRED";
            case "block" -> "BLOCKED";
            case "reject" -> "REJECTED";
            default -> null;
        };
    }

    private static boolean isAgentSimulationAction(String action) {
        return "simulate".equals(action) || "replay".equals(action);
    }

    private static boolean isTransitionAllowed(String currentStatus, String action, @Nullable String targetStatus) {
        if (isAgentSimulationAction(action)) {
            return true;
        }
        if (TERMINAL_AGENT_STATUSES.contains(currentStatus) && !"retire".equals(action)) {
            return false;
        }
        if ("approve".equals(action) && !Set.of("SCANNED", "PENDING", "DRAFT", "ACTIVE").contains(currentStatus)) {
            return false;
        }
        if ("install".equals(action) && !Set.of("APPROVED", "SCANNED", "ACTIVE").contains(currentStatus)) {
            return false;
        }
        if ("configure".equals(action) && !Set.of("INSTALLED", "APPROVED", "ACTIVE", "CONFIGURED").contains(currentStatus)) {
            return false;
        }
        if ("execute".equals(action) && !Set.of("CONFIGURED", "INSTALLED", "APPROVED", "ACTIVE", "REVIEWED", "LEARNING").contains(currentStatus)) {
            return false;
        }
        if (targetStatus == null) {
            return false;
        }
        return true;
    }

    private static String normalizeStatus(@Nullable Object status) {
        if (status == null) {
            return "ACTIVE";
        }
        String normalized = String.valueOf(status).trim().toUpperCase(Locale.ROOT);
        return normalized.isEmpty() ? "ACTIVE" : normalized;
    }

    @Nullable
    private static String asNonBlankString(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
