/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ghatana.agent.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.agent.AgentConfig;
import com.ghatana.agent.AgentDescriptor;
import com.ghatana.agent.TypedAgent;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Durable, multi-tenant PostgreSQL implementation of {@link AgentRegistry}.
 *
 * <h2>Design</h2>
 * <ul>
 *   <li><b>Persistence</b>: Agent descriptors and configurations are stored in
 *       {@code agent_registrations} and {@code agent_capabilities} tables
 *       (created by {@code V001__create_agent_registry.sql}).</li>
 *   <li><b>In-memory hot cache</b>: Actual {@link TypedAgent} instances are kept
 *       in a {@link ConcurrentHashMap} because agent objects are compute objects
 *       (not serialisable across JVM boundaries). The DB stores the metadata
 *       needed for capability discovery and cross-node awareness.</li>
 *   <li><b>Tenant isolation</b>: A {@code tenantId} is required at construction
 *       time and is enforced on every read/write operation.</li>
 *   <li><b>Capability index</b>: Each capability declared by an agent is stored
 *       as a separate row in {@code agent_capabilities}, enabling O(1) look-up by
 *       capability without full table scans.</li>
 * </ul>
 *
 * <h2>Thread safety</h2>
 * All JDBC operations run off the ActiveJ eventloop via
 * {@link Promise#ofBlocking(Executor, io.activej.common.function.FunctionEx)}.
 * The in-memory cache is backed by {@link ConcurrentHashMap} and is safe for
 * concurrent access.
 *
 * @doc.type class
 * @doc.purpose Durable PostgreSQL-backed multi-tenant agent registry
 * @doc.layer registry
 * @doc.pattern Repository, Cache-Aside
 *
 * @author Ghatana AI Platform
 * @since 2.0.0
 */
public final class JdbcAgentRegistry implements AgentRegistry {

    private static final Logger log = LoggerFactory.getLogger(JdbcAgentRegistry.class);

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    // ─────────────────────────────────────────────────────────────────────────
    // SQL statements
    // ─────────────────────────────────────────────────────────────────────────

    private static final String SQL_EXISTS_AGENT =
            "SELECT COUNT(*) FROM agent_registrations WHERE agent_id = ? AND tenant_id = ?";

    private static final String SQL_INSERT_AGENT = """
            INSERT INTO agent_registrations
                (agent_id, tenant_id, agent_type, descriptor_json, config_json,
                 status, registered_at, updated_at, heartbeat_at, node_id)
            VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?, ?)
            """;

    private static final String SQL_UPDATE_AGENT = """
            UPDATE agent_registrations SET
                agent_type      = ?,
                descriptor_json = ?,
                config_json     = ?,
                status          = 'ACTIVE',
                updated_at      = ?,
                heartbeat_at    = ?,
                node_id         = ?
            WHERE agent_id = ? AND tenant_id = ?
            """;

    private static final String SQL_DELETE_CAPABILITIES = """
            DELETE FROM agent_capabilities WHERE agent_id = ? AND tenant_id = ?
            """;

    private static final String SQL_INSERT_CAPABILITY_SAFE =
            "INSERT INTO agent_capabilities (agent_id, tenant_id, capability) VALUES (?, ?, ?)";

    private static final String SQL_EXISTS_CAPABILITY =
            "SELECT COUNT(*) FROM agent_capabilities WHERE agent_id = ? AND tenant_id = ? AND capability = ?";

    private static final String SQL_DEREGISTER = """
            UPDATE agent_registrations
               SET status = 'DEREGISTERED', updated_at = ?
             WHERE agent_id = ? AND tenant_id = ?
            """;

    private static final String SQL_LIST_IDS = """
            SELECT agent_id FROM agent_registrations
             WHERE tenant_id = ? AND status = 'ACTIVE'
            """;

    private static final String SQL_FIND_BY_CAPABILITY = """
            SELECT DISTINCT ac.agent_id
              FROM agent_capabilities ac
              JOIN agent_registrations ar
                ON ar.agent_id = ac.agent_id AND ar.tenant_id = ac.tenant_id
             WHERE ac.tenant_id = ? AND ac.capability = ? AND ar.status = 'ACTIVE'
            """;

    private static final String SQL_STATS = """
            SELECT
                count(*) FILTER (WHERE status = 'ACTIVE')       AS active_count,
                count(*) FILTER (WHERE status = 'DEGRADED')     AS degraded_count,
                count(*) FILTER (WHERE status = 'DEREGISTERED') AS deregistered_count,
                count(DISTINCT agt.agent_type)                  AS unique_types,
                count(DISTINCT cap.capability)                  AS unique_capabilities
            FROM agent_registrations agt
            LEFT JOIN agent_capabilities cap
                   ON cap.agent_id = agt.agent_id AND cap.tenant_id = agt.tenant_id
            WHERE agt.tenant_id = ?
            """;

    // ─────────────────────────────────────────────────────────────────────────
    // Fields
    // ─────────────────────────────────────────────────────────────────────────

    private final String tenantId;
    private final DataSource dataSource;
    private final ExecutorService executor;

    /** In-memory hot cache: agentId → TypedAgent instance. */
    private final ConcurrentHashMap<String, TypedAgent<?, ?>> localCache =
            new ConcurrentHashMap<>();

    /** Node identifier for registry diagnostics (hostname or pod name). */
    private final String nodeId;

    // ─────────────────────────────────────────────────────────────────────────
    // Construction
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates a registry for the given tenant backed by the provided DataSource.
     *
     * @param tenantId   The tenant scope (all operations are isolated to this tenant)
     * @param dataSource JDBC DataSource (e.g. HikariCP pool)
     */
    public JdbcAgentRegistry(@NotNull String tenantId, @NotNull DataSource dataSource) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.nodeId = resolveNodeId();
        log.info("JdbcAgentRegistry initialised for tenant '{}' on node '{}'", tenantId, nodeId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AgentRegistry — registration
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>The agent descriptor and config are persisted to PostgreSQL (durable),
     * and the live agent instance is cached locally for fast {@link #resolve} calls.
     * Capability rows are atomically replaced to reflect any capability changes
     * since the last registration.
     */
    @Override
    @NotNull
    public Promise<Void> register(@NotNull TypedAgent<?, ?> agent,
                                  @NotNull AgentConfig config) {
        Objects.requireNonNull(agent, "agent");
        Objects.requireNonNull(config, "config");

        AgentDescriptor descriptor = agent.descriptor();
        String agentId = descriptor.getAgentId();

        return Promise.ofBlocking(executor, () -> {
            log.debug("Registering agent '{}' for tenant '{}'", agentId, tenantId);

            String descriptorJson = toJson(descriptor);
            String configJson = toJson(config);
            String agentType = descriptor.getType() != null ? descriptor.getType().name() : "UNKNOWN";
            Instant now = Instant.now();

            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    // 1. Check if registration exists (portable: works in both H2 and PostgreSQL)
                    boolean exists;
                    try (PreparedStatement ps = conn.prepareStatement(SQL_EXISTS_AGENT)) {
                        ps.setString(1, agentId);
                        ps.setString(2, tenantId);
                        try (ResultSet rs = ps.executeQuery()) {
                            rs.next();
                            exists = rs.getLong(1) > 0;
                        }
                    }

                    if (exists) {
                        try (PreparedStatement ps = conn.prepareStatement(SQL_UPDATE_AGENT)) {
                            ps.setString(1, agentType);
                            ps.setString(2, descriptorJson);
                            ps.setString(3, configJson);
                            ps.setTimestamp(4, Timestamp.from(now));
                            ps.setTimestamp(5, Timestamp.from(now));
                            ps.setString(6, nodeId);
                            ps.setString(7, agentId);
                            ps.setString(8, tenantId);
                            ps.executeUpdate();
                        }
                    } else {
                        try (PreparedStatement ps = conn.prepareStatement(SQL_INSERT_AGENT)) {
                            ps.setString(1, agentId);
                            ps.setString(2, tenantId);
                            ps.setString(3, agentType);
                            ps.setString(4, descriptorJson);
                            ps.setString(5, configJson);
                            ps.setTimestamp(6, Timestamp.from(now));
                            ps.setTimestamp(7, Timestamp.from(now));
                            ps.setTimestamp(8, Timestamp.from(now));
                            ps.setString(9, nodeId);
                            ps.executeUpdate();
                        }
                    }

                    // 2. Re-index capabilities (delete-then-insert for clean slate)
                    try (PreparedStatement ps = conn.prepareStatement(SQL_DELETE_CAPABILITIES)) {
                        ps.setString(1, agentId);
                        ps.setString(2, tenantId);
                        ps.executeUpdate();
                    }

                    Set<String> capSet = descriptor.getCapabilities();
                    List<String> capabilities = capSet != null ? new ArrayList<>(capSet) : List.of();
                    for (String cap : capabilities) {
                        try (PreparedStatement ps = conn.prepareStatement(SQL_INSERT_CAPABILITY_SAFE)) {
                            ps.setString(1, agentId);
                            ps.setString(2, tenantId);
                            ps.setString(3, cap);
                            ps.executeUpdate();
                        }
                    }

                    conn.commit();
                } catch (Exception ex) {
                    conn.rollback();
                    throw ex;
                } finally {
                    conn.setAutoCommit(true);
                }
            }

            // 3. Update in-memory cache after successful DB write
            localCache.put(agentId, agent);
            log.info("Registered agent '{}' ({} capabilities) for tenant '{}'",
                    agentId,
                    capabilities(descriptor),
                    tenantId);
            return null;
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AgentRegistry — deregistration
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Marks the agent as {@code DEREGISTERED} in PostgreSQL (soft-delete) and
     * explicitly removes its capability index rows so stats queries reflect the
     * correct active capability set. The entry is removed from the local cache immediately.
     */
    @Override
    @NotNull
    public Promise<Void> deregister(@NotNull String agentId) {
        Objects.requireNonNull(agentId, "agentId");

        localCache.remove(agentId);

        return Promise.ofBlocking(executor, () -> {
            log.debug("Deregistering agent '{}' for tenant '{}'", agentId, tenantId);
            try (Connection conn = dataSource.getConnection()) {
                // Remove capability index first (before status update)
                try (PreparedStatement ps = conn.prepareStatement(SQL_DELETE_CAPABILITIES)) {
                    ps.setString(1, agentId);
                    ps.setString(2, tenantId);
                    ps.executeUpdate();
                }
                // Soft-delete: mark as DEREGISTERED
                try (PreparedStatement ps = conn.prepareStatement(SQL_DEREGISTER)) {
                    ps.setTimestamp(1, Timestamp.from(Instant.now()));
                    ps.setString(2, agentId);
                    ps.setString(3, tenantId);
                    int rows = ps.executeUpdate();
                    if (rows > 0) {
                        log.info("Deregistered agent '{}' for tenant '{}'", agentId, tenantId);
                    } else {
                        log.debug("Agent '{}' was not registered or already deregistered", agentId);
                    }
                }
            }
            return null;
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AgentRegistry — resolution
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Returns from the in-memory cache (O(1)). Agents must be registered
     * via {@link #register} in the current JVM before they can be resolved.
     */
    @Override
    @NotNull
    @SuppressWarnings("unchecked")
    public <I, O> Promise<Optional<TypedAgent<I, O>>> resolve(@NotNull String agentId) {
        Objects.requireNonNull(agentId, "agentId");
        TypedAgent<I, O> agent = (TypedAgent<I, O>) localCache.get(agentId);
        return Promise.of(Optional.ofNullable(agent));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AgentRegistry — discovery
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Queries the {@code agent_registrations} table for this tenant.
     * Returns only agents with {@code status = 'ACTIVE'}.
     */
    @Override
    @NotNull
    public Promise<Set<String>> listAgentIds() {
        return Promise.ofBlocking(executor, () -> {
            Set<String> ids = new LinkedHashSet<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SQL_LIST_IDS)) {
                ps.setString(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        ids.add(rs.getString("agent_id"));
                    }
                }
            }
            return Collections.unmodifiableSet(ids);
        });
    }

    /**
     * {@inheritDoc}
     *
     * <p>Uses the {@code agent_capabilities} index for O(1) look-up.
     * Only returns agent IDs that are currently {@code ACTIVE}.
     */
    @Override
    @NotNull
    public Promise<List<String>> findByCapability(@NotNull String capability) {
        Objects.requireNonNull(capability, "capability");
        return Promise.ofBlocking(executor, () -> {
            List<String> result = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_CAPABILITY)) {
                ps.setString(1, tenantId);
                ps.setString(2, capability);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(rs.getString("agent_id"));
                    }
                }
            }
            return Collections.unmodifiableList(result);
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AgentRegistry — statistics
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Executes a single aggregate query against the DB to get counts
     * without loading agent data into memory.
     */
    @Override
    @NotNull
    public Promise<Map<String, Object>> getStats() {
        return Promise.ofBlocking(executor, () -> {
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("registryType", "JdbcAgentRegistry");
            stats.put("tenantId", tenantId);
            stats.put("localCacheSize", localCache.size());

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SQL_STATS)) {
                ps.setString(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        stats.put("activeAgents", rs.getLong("active_count"));
                        stats.put("degradedAgents", rs.getLong("degraded_count"));
                        stats.put("deregisteredAgents", rs.getLong("deregistered_count"));
                        stats.put("uniqueAgentTypes", rs.getLong("unique_types"));
                        stats.put("uniqueCapabilities", rs.getLong("unique_capabilities"));
                    }
                }
            }
            return Collections.unmodifiableMap(stats);
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test helpers (package-private)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the number of entries in the local in-memory cache.
     * Intended for use in tests to verify cache state.
     */
    int localCacheSize() {
        return localCache.size();
    }

    /**
     * Clears the local in-memory cache. Intended for test teardown only.
     */
    void clearLocalCache() {
        localCache.clear();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize to JSON: " + obj, ex);
        }
    }

    private static int capabilities(AgentDescriptor descriptor) {
        Set<String> caps = descriptor.getCapabilities();
        return caps == null ? 0 : caps.size();
    }

    private static String resolveNodeId() {
        String hostname = System.getenv("HOSTNAME");
        if (hostname != null && !hostname.isBlank()) return hostname;
        String podName = System.getenv("POD_NAME");
        if (podName != null && !podName.isBlank()) return podName;
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown-" + UUID.randomUUID().toString().substring(0, 8);
        }
    }
}
