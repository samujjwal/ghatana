/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Backend Persistence
 */
package com.ghatana.yappc.api.repository.jdbc;

import com.ghatana.yappc.api.domain.LearnedPolicy;
import com.ghatana.yappc.api.repository.LearnedPolicyRepository;
import io.activej.inject.annotation.Inject;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * JDBC-backed implementation of {@link LearnedPolicyRepository}.
 *
 * <p>All blocking JDBC calls are wrapped in {@code Promise.ofBlocking} to keep
 * the ActiveJ eventloop non-blocking (plan rule: NEVER block the eventloop).
 *
 * <h2>Required DDL</h2>
 * <pre>{@code
 * CREATE SCHEMA IF NOT EXISTS yappc;
 * CREATE TABLE yappc.learned_policies (
 *   id          TEXT             PRIMARY KEY,
 *   agent_id    TEXT             NOT NULL,
 *   name        TEXT,
 *   description TEXT,
 *   procedure   TEXT             NOT NULL,
 *   confidence  DOUBLE PRECISION NOT NULL DEFAULT 0.0,
 *   source      TEXT,
 *   version     INTEGER          NOT NULL DEFAULT 1,
 *   tenant_id   TEXT             NOT NULL,
 *   created_at  TIMESTAMPTZ      NOT NULL DEFAULT now(),
 *   updated_at  TIMESTAMPTZ      NOT NULL DEFAULT now()
 * );
 * CREATE INDEX ON yappc.learned_policies (tenant_id, agent_id);
 * CREATE INDEX ON yappc.learned_policies (tenant_id, confidence);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose JDBC persistence for learned agent policies (procedural memory)
 * @doc.layer infrastructure
 * @doc.pattern Repository
 */
public class JdbcLearnedPolicyRepository implements LearnedPolicyRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcLearnedPolicyRepository.class);

    private static final String TABLE = "yappc.learned_policies";

    private final DataSource dataSource;
    private final Executor   executor;

    @Inject
    public JdbcLearnedPolicyRepository(DataSource dataSource) {
        this.dataSource = dataSource;
        this.executor   = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "jdbc-policy-store");
            t.setDaemon(true);
            return t;
        });
    }

    /** Test-friendly constructor with external Executor. */
    public JdbcLearnedPolicyRepository(DataSource dataSource, Executor executor) {
        this.dataSource = dataSource;
        this.executor   = executor;
    }

    // ─── Save (upsert) ────────────────────────────────────────────────────────

    @Override
    @NotNull
    public Promise<LearnedPolicy> save(@NotNull LearnedPolicy policy) {
        return Promise.ofBlocking(executor, () -> {
            Instant now = Instant.now();
            if (policy.getCreatedAt() == null) policy.setCreatedAt(now);
            policy.setUpdatedAt(now);

            String sql = "INSERT INTO " + TABLE
                    + " (id, agent_id, name, description, procedure, confidence,"
                    + "  source, version, tenant_id, created_at, updated_at)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                    + " ON CONFLICT (id) DO UPDATE SET"
                    + "   name        = EXCLUDED.name,"
                    + "   description = EXCLUDED.description,"
                    + "   procedure   = EXCLUDED.procedure,"
                    + "   confidence  = EXCLUDED.confidence,"
                    + "   source      = EXCLUDED.source,"
                    + "   version     = EXCLUDED.version,"
                    + "   updated_at  = EXCLUDED.updated_at";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1,  policy.getId());
                ps.setString(2,  policy.getAgentId());
                ps.setString(3,  policy.getName());
                ps.setString(4,  policy.getDescription());
                ps.setString(5,  policy.getProcedure());
                ps.setDouble(6,  policy.getConfidence());
                ps.setString(7,  policy.getSource());
                ps.setInt(8,     policy.getVersion());
                ps.setString(9,  policy.getTenantId());
                ps.setTimestamp(10, Timestamp.from(policy.getCreatedAt()));
                ps.setTimestamp(11, Timestamp.from(policy.getUpdatedAt()));
                ps.executeUpdate();
            }

            log.debug("JdbcLearnedPolicyRepository: saved policy id='{}' agentId='{}' confidence={}",
                    policy.getId(), policy.getAgentId(), policy.getConfidence());
            return policy;
        });
    }

    // ─── Find by ID ───────────────────────────────────────────────────────────

    @Override
    @NotNull
    public Promise<Optional<LearnedPolicy>> findById(@NotNull String id) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM " + TABLE + " WHERE id = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return Optional.of(mapRow(rs));
                    return Optional.empty();
                }
            }
        });
    }

    // ─── Find by agent ────────────────────────────────────────────────────────

    @Override
    @NotNull
    public Promise<List<LearnedPolicy>> findByAgent(
            @NotNull String tenantId, @NotNull String agentId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM " + TABLE
                    + " WHERE tenant_id = ? AND agent_id = ?"
                    + " ORDER BY created_at DESC";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tenantId);
                ps.setString(2, agentId);
                return collectRows(ps);
            }
        });
    }

    // ─── Find above confidence ────────────────────────────────────────────────

    @Override
    @NotNull
    public Promise<List<LearnedPolicy>> findAboveConfidence(
            @NotNull String tenantId, double minConfidence) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM " + TABLE
                    + " WHERE tenant_id = ? AND confidence >= ?"
                    + " ORDER BY confidence DESC";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tenantId);
                ps.setDouble(2, minConfidence);
                return collectRows(ps);
            }
        });
    }

    // ─── Delete ───────────────────────────────────────────────────────────────

    @Override
    @NotNull
    public Promise<Void> delete(@NotNull String id) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "DELETE FROM " + TABLE + " WHERE id = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, id);
                ps.executeUpdate();
            }
            return null;
        });
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static List<LearnedPolicy> collectRows(PreparedStatement ps) throws SQLException {
        List<LearnedPolicy> result = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) result.add(mapRow(rs));
        }
        return result;
    }

    private static LearnedPolicy mapRow(ResultSet rs) throws SQLException {
        LearnedPolicy p = new LearnedPolicy();
        p.setId(rs.getString("id"));
        p.setAgentId(rs.getString("agent_id"));
        p.setName(rs.getString("name"));
        p.setDescription(rs.getString("description"));
        p.setProcedure(rs.getString("procedure"));
        p.setConfidence(rs.getDouble("confidence"));
        p.setSource(rs.getString("source"));
        p.setVersion(rs.getInt("version"));
        p.setTenantId(rs.getString("tenant_id"));
        Timestamp ca = rs.getTimestamp("created_at");
        if (ca != null) p.setCreatedAt(ca.toInstant());
        Timestamp ua = rs.getTimestamp("updated_at");
        if (ua != null) p.setUpdatedAt(ua.toInstant());
        return p;
    }
}
