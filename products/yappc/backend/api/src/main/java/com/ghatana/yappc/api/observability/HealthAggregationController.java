/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.observability;

import com.ghatana.yappc.api.common.TenantContextExtractor;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Provides a unified health-aggregation endpoint for the YAPPC API service.
 *
 * <h2>Endpoint</h2>
 * <pre>GET /health/detailed</pre>
 *
 * <h2>Response shape</h2>
 * <pre>{@code
 * {
 *   "status": "UP",
 *   "timestamp": "2025-01-19T...",
 *   "services": {
 *     "api": "UP",
 *     "lifecycle": "UNKNOWN",
 *     "scaffold": "UNKNOWN"
 *   },
 *   "db": {
 *     "reachable": true,
 *     "version": "PostgreSQL 16.1..."
 *   },
 *   "agents": {
 *     "registered": 228
 *   }
 * }
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Unified health aggregation endpoint (Observability 6.6)
 * @doc.layer api
 * @doc.pattern Controller
 */
public class HealthAggregationController {

    private static final Logger log = LoggerFactory.getLogger(HealthAggregationController.class);

    private static final Executor JDBC_EXECUTOR =
            Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "health-jdbc");
                t.setDaemon(true);
                return t;
            });

    private final DataSource dataSource;
    /** Total registered agents in the YAPPC catalog (injected from configuration). */
    private final int registeredAgentCount;

    /**
     * @param dataSource           JDBC data source for the database reachability check
     * @param registeredAgentCount total number of agents declared in the agent catalog
     */
    public HealthAggregationController(DataSource dataSource, int registeredAgentCount) {
        this.dataSource           = Objects.requireNonNull(dataSource, "dataSource");
        this.registeredAgentCount = registeredAgentCount;
    }

    /**
     * Serves the detailed health check.
     *
     * <p>GET /health/detailed
     *
     * <p>This endpoint is intentionally <em>not</em> auth-gated so that load balancers
     * and monitoring agents can reach it without credentials.
     */
    public Promise<HttpResponse> getDetailedHealth(HttpRequest request) {
        return Promise.ofBlocking(JDBC_EXECUTOR, this::buildHealthReport)
                .map(report -> {
                    boolean up = "UP".equals(report.get("status"));
                    String json = toJson(report);
                    return up
                            ? HttpResponse.ok200().withJson(json).build()
                            : HttpResponse.ofCode(503).withJson(json).build();
                })
                .then(resp -> Promise.of(resp), e -> {
                    log.error("Health check failed", e);
                    String body = "{\"status\":\"DOWN\",\"error\":\"" + e.getMessage() + "\"}";
                    return Promise.of(HttpResponse.ofCode(503).withJson(body).build());
                });
    }

    // ─── Internal ─────────────────────────────────────────────────────────────

    /** Assembles the health report synchronously (called on blocking executor). */
    private Map<String, Object> buildHealthReport() {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("timestamp", Instant.now().toString());

        // Database connectivity
        Map<String, Object> db = checkDatabase();
        report.put("db", db);

        // Service statuses (current process = API is obviously UP)
        Map<String, Object> services = new LinkedHashMap<>();
        services.put("api",       "UP");
        services.put("lifecycle", "UNKNOWN"); // cross-service ping not yet implemented
        services.put("scaffold",  "UNKNOWN");
        report.put("services", services);

        // Agent catalog summary
        Map<String, Object> agents = new LinkedHashMap<>();
        agents.put("registered", registeredAgentCount);
        report.put("agents", agents);

        // Overall status: UP only if DB is reachable
        boolean dbReachable = Boolean.TRUE.equals(db.get("reachable"));
        report.put("status", dbReachable ? "UP" : "DEGRADED");

        return report;
    }

    private Map<String, Object> checkDatabase() {
        Map<String, Object> db = new LinkedHashMap<>();
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            db.put("reachable", true);
            db.put("version",   meta.getDatabaseProductVersion());
        } catch (Exception e) {
            log.warn("Database health check failed: {}", e.getMessage());
            db.put("reachable", false);
            db.put("error",     e.getMessage());
        }
        return db;
    }

    /** Minimal JSON serialisation (avoids ObjectMapper dependency for this simple structure). */
    @SuppressWarnings("unchecked")
    private String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(entry.getKey()).append('"').append(':');
            Object val = entry.getValue();
            if (val == null)                 sb.append("null");
            else if (val instanceof Boolean) sb.append(val);
            else if (val instanceof Number)  sb.append(val);
            else if (val instanceof Map)     sb.append(toJson((Map<String, Object>) val));
            else                             sb.append('"').append(val.toString().replace("\"", "\\\"")).append('"');
        }
        sb.append('}');
        return sb.toString();
    }
}
