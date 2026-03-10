/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.repository.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.api.domain.AgentRegistryEntry;
import com.ghatana.yappc.api.domain.AgentRegistryEntry.AgentStatus;
import com.ghatana.yappc.api.domain.AgentRegistryEntry.HealthStatus;
import com.ghatana.yappc.api.repository.AgentRegistryRepository;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.activej.inject.annotation.Inject;
import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * JDBC implementation of the persistent agent registry.
 *
 * <p>Uses PostgreSQL {@code yappc.agent_registry} as the source of truth.
 * Blocking JDBC calls are offloaded to a dedicated executor to avoid
 * blocking the ActiveJ event loop.
 *
 * @doc.type class
 * @doc.purpose JDBC-backed agent registry
 * @doc.layer infrastructure
 * @doc.pattern Repository
 */
public class JdbcAgentRegistryRepository implements AgentRegistryRepository {

    private static final Logger logger = LoggerFactory.getLogger(JdbcAgentRegistryRepository.class);
    private static final Executor JDBC_EXECUTOR = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "jdbc-repo");
        t.setDaemon(true);
        return t;
    });


    private static final String UPSERT =
            "INSERT INTO yappc.agent_registry " +
            "  (id, name, version, agent_type, status, capabilities, config, metadata, " +
            "   health_status, last_heartbeat, tenant_id) " +
            "VALUES (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?, ?) " +
            "ON CONFLICT (id, tenant_id) DO UPDATE SET " +
            "  name = EXCLUDED.name, version = EXCLUDED.version, " +
            "  agent_type = EXCLUDED.agent_type, status = EXCLUDED.status, " +
            "  capabilities = EXCLUDED.capabilities, config = EXCLUDED.config, " +
            "  metadata = EXCLUDED.metadata, health_status = EXCLUDED.health_status, " +
            "  last_heartbeat = EXCLUDED.last_heartbeat, updated_at = NOW()";

    private static final String SELECT_BY_ID =
            "SELECT * FROM yappc.agent_registry WHERE tenant_id = ? AND id = ?";

    private static final String SELECT_ACTIVE =
            "SELECT * FROM yappc.agent_registry WHERE tenant_id = ? AND status = 'ACTIVE'";

    private static final String SELECT_BY_TYPE =
            "SELECT * FROM yappc.agent_registry WHERE tenant_id = ? AND agent_type = ?";

    private static final String SELECT_BY_CAPABILITY =
            "SELECT * FROM yappc.agent_registry " +
            "WHERE tenant_id = ? AND capabilities @> ?::jsonb AND status = 'ACTIVE'";

    private static final String UPDATE_STATUS =
            "UPDATE yappc.agent_registry SET status = ?, updated_at = NOW() " +
            "WHERE tenant_id = ? AND id = ?";

    private static final String UPDATE_HEARTBEAT =
            "UPDATE yappc.agent_registry " +
            "SET last_heartbeat = NOW(), health_status = 'HEALTHY', updated_at = NOW() " +
            "WHERE tenant_id = ? AND id = ?";

    private static final String INSERT_METRIC =
            "INSERT INTO yappc.agent_metrics (agent_id, tenant_id, metric_name, metric_value) " +
            "VALUES (?, ?, ?, ?)";

    private static final String DELETE_AGENT =
            "DELETE FROM yappc.agent_registry WHERE tenant_id = ? AND id = ?";

    private final DataSource dataSource;
    private final ObjectMapper mapper;
    private final Executor executor;

    @Inject
    public JdbcAgentRegistryRepository(DataSource dataSource, ObjectMapper mapper) {
        this.dataSource = dataSource;
        this.mapper     = mapper;
        this.executor   = Executors.newCachedThreadPool();
    }

    @Override
    public Promise<AgentRegistryEntry> save(AgentRegistryEntry entry) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(UPSERT)) {
                ps.setString(1, entry.getId());
                ps.setString(2, entry.getName());
                ps.setString(3, entry.getVersion());
                ps.setString(4, entry.getAgentType());
                ps.setString(5, entry.getStatus().name());
                ps.setString(6, toJson(entry.getCapabilities()));
                ps.setString(7, toJson(entry.getConfig()));
                ps.setString(8, toJson(entry.getMetadata()));
                ps.setString(9, entry.getHealthStatus() != null
                        ? entry.getHealthStatus().name() : HealthStatus.UNKNOWN.name());
                ps.setTimestamp(10, entry.getLastHeartbeat() != null
                        ? Timestamp.from(entry.getLastHeartbeat()) : null);
                ps.setString(11, entry.getTenantId());
                ps.executeUpdate();
            }
            return entry;
        });
    }

    @Override
    public Promise<Optional<AgentRegistryEntry>> findById(String tenantId, String agentId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SELECT_BY_ID)) {
                ps.setString(1, tenantId);
                ps.setString(2, agentId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return Optional.of(mapRow(rs));
                    return Optional.empty();
                }
            }
        });
    }

    @Override
    public Promise<List<AgentRegistryEntry>> findActiveByTenant(String tenantId) {
        return Promise.ofBlocking(executor, () -> queryList(SELECT_ACTIVE, tenantId));
    }

    @Override
    public Promise<List<AgentRegistryEntry>> findByType(String tenantId, String agentType) {
        return Promise.ofBlocking(executor, () -> {
            List<AgentRegistryEntry> results = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SELECT_BY_TYPE)) {
                ps.setString(1, tenantId);
                ps.setString(2, agentType);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) results.add(mapRow(rs));
                }
            }
            return results;
        });
    }

    @Override
    public Promise<List<AgentRegistryEntry>> findByCapability(String tenantId, String capability) {
        return Promise.ofBlocking(executor, () -> {
            List<AgentRegistryEntry> results = new ArrayList<>();
            String capabilityJson = "[\"" + capability + "\"]";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SELECT_BY_CAPABILITY)) {
                ps.setString(1, tenantId);
                ps.setString(2, capabilityJson);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) results.add(mapRow(rs));
                }
            }
            return results;
        });
    }

    @Override
    public Promise<Void> updateStatus(String tenantId, String agentId, AgentStatus status) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(UPDATE_STATUS)) {
                ps.setString(1, status.name());
                ps.setString(2, tenantId);
                ps.setString(3, agentId);
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public Promise<Void> updateHeartbeat(String tenantId, String agentId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(UPDATE_HEARTBEAT)) {
                ps.setString(1, tenantId);
                ps.setString(2, agentId);
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public Promise<Void> recordMetric(String tenantId, String agentId,
                                      String metricName, double value) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(INSERT_METRIC)) {
                ps.setString(1, agentId);
                ps.setString(2, tenantId);
                ps.setString(3, metricName);
                ps.setDouble(4, value);
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public Promise<Boolean> delete(String tenantId, String agentId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(DELETE_AGENT)) {
                ps.setString(1, tenantId);
                ps.setString(2, agentId);
                return ps.executeUpdate() > 0;
            }
        });
    }

    // -------------------------------------------------------------------------

    private List<AgentRegistryEntry> queryList(String sql, String tenantId) throws Exception {
        List<AgentRegistryEntry> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(mapRow(rs));
            }
        }
        return results;
    }

    private AgentRegistryEntry mapRow(ResultSet rs) throws Exception {
        AgentRegistryEntry e = new AgentRegistryEntry();
        e.setId(rs.getString("id"));
        e.setName(rs.getString("name"));
        e.setVersion(rs.getString("version"));
        e.setAgentType(rs.getString("agent_type"));
        e.setStatus(AgentStatus.valueOf(rs.getString("status")));
        e.setCapabilities(fromJson(rs.getString("capabilities"),
                new TypeReference<List<String>>() {}));
        e.setConfig(fromJson(rs.getString("config"),
                new TypeReference<Map<String, Object>>() {}));
        e.setMetadata(fromJson(rs.getString("metadata"),
                new TypeReference<Map<String, Object>>() {}));
        e.setHealthStatus(HealthStatus.valueOf(rs.getString("health_status")));
        Timestamp hb = rs.getTimestamp("last_heartbeat");
        if (hb != null) e.setLastHeartbeat(hb.toInstant());
        e.setTenantId(rs.getString("tenant_id"));
        e.setCreatedAt(rs.getTimestamp("created_at").toInstant());
        e.setUpdatedAt(rs.getTimestamp("updated_at").toInstant());
        return e;
    }

    private String toJson(Object obj) {
        if (obj == null) return "{}";
        try {
            return mapper.writeValueAsString(obj);
        } catch (JsonProcessingException ex) {
            logger.warn("Failed to serialise to JSON: {}", ex.getMessage());
            return "{}";
        }
    }

    private <T> T fromJson(String json, TypeReference<T> type) {
        if (json == null || json.isBlank()) return null;
        try {
            return mapper.readValue(json, type);
        } catch (Exception ex) {
            logger.warn("Failed to deserialise JSON: {}", ex.getMessage());
            return null;
        }
    }
}
