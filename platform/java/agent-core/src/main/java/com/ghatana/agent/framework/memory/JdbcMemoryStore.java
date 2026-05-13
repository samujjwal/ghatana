/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * JDBC-backed {@link MemoryStore} providing durable, event-sourced agent memory.
 *
 * <h2>Storage model</h2>
 * <p>All memory types are serialised to JSON and stored in a single
 * {@code agent_memory_events} append-only event-log table.  Materialised views
 * are populated by read queries that reconstruct the current state from that log,
 * keeping the design consistent with the event-sourcing mandate in the GAA spec.</p>
 *
 * <h2>Schema (PostgreSQL / H2-compatible)</h2>
 * <pre>{@code
 * CREATE TABLE agent_memory_events (
 *   id          TEXT        PRIMARY KEY,
 *   agent_id    TEXT        NOT NULL,
 *   tenant_id   TEXT        NOT NULL,
 *   event_type  TEXT        NOT NULL,   -- EPISODE | FACT | POLICY | PREFERENCE
 *   entity_id   TEXT        NOT NULL,   -- id of the Episode/Fact/Policy/Preference
 *   payload     TEXT        NOT NULL,   -- JSON-serialised entity
 *   created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
 * );
 * CREATE INDEX ame_agent_tenant ON agent_memory_events(agent_id, tenant_id);
 * CREATE INDEX ame_type_agent   ON agent_memory_events(event_type, agent_id);
 * CREATE INDEX ame_entity       ON agent_memory_events(entity_id);
 * }</pre>
 *
 * <h2>ActiveJ concurrency</h2>
 * <p>All JDBC calls run on a dedicated {@link Executor} via {@code Promise.ofBlocking}
 * and never block the ActiveJ event loop thread.</p>
 *
 * @doc.type class
 * @doc.purpose Durable JDBC-backed agent memory store (event-sourced)
 * @doc.layer platform
 * @doc.pattern Repository, EventSourced
 * @doc.gaa.memory episodic|semantic|procedural|preference
 * @doc.gaa.lifecycle capture
 */
public class JdbcMemoryStore implements MemoryStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcMemoryStore.class);

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /** Dedicated I/O executor — never runs on the ActiveJ event loop. */
    private static final Executor DB_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "agent-memory-db");
        t.setDaemon(true);
        return t;
    });

    private static final String EVENT_TYPE_EPISODE   = "EPISODE";
    private static final String EVENT_TYPE_FACT      = "FACT";
    private static final String EVENT_TYPE_POLICY    = "POLICY";
    private static final String EVENT_TYPE_PREFERENCE = "PREFERENCE";

    private final DataSource dataSource;
    private final String agentId;
    private final String tenantId;

    /**
     * Creates a {@link JdbcMemoryStore} for the given agent and tenant.
     *
     * @param dataSource the JDBC DataSource (e.g. HikariCP pool)
     * @param agentId    the agent this store belongs to
     * @param tenantId   the tenant this store is scoped to
     */
    public JdbcMemoryStore(@NotNull DataSource dataSource,
                           @NotNull String agentId,
                           @NotNull String tenantId) {
        this.dataSource = dataSource;
        this.agentId    = agentId;
        this.tenantId   = tenantId;
    }

    // ── Schema initialisation ─────────────────────────────────────────────────

    /**
     * Applies the minimal DDL for this store. Safe to call at startup.
     */
    public void ensureSchema() {
        try (Connection conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS agent_memory_events (" +
                "  id          TEXT        PRIMARY KEY," +
                "  agent_id    TEXT        NOT NULL," +
                "  tenant_id   TEXT        NOT NULL," +
                "  event_type  TEXT        NOT NULL," +
                "  entity_id   TEXT        NOT NULL," +
                "  payload     TEXT        NOT NULL," +
                "  created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()" +
                ")");
            stmt.execute(
                "CREATE INDEX IF NOT EXISTS ame_agent_tenant " +
                "  ON agent_memory_events(agent_id, tenant_id)");
            stmt.execute(
                "CREATE INDEX IF NOT EXISTS ame_type_agent " +
                "  ON agent_memory_events(event_type, agent_id)");
            log.info("agent_memory_events schema verified (agentId={})", agentId);
        } catch (SQLException e) {
            log.error("ensureSchema failed: {}", e.getMessage());
            throw new RuntimeException("Memory store schema migration failed", e);
        }
    }

    // ── Episodic memory ───────────────────────────────────────────────────────

    @Override
    @NotNull
    public Promise<Episode> storeEpisode(@NotNull Episode episode) {
        return Promise.ofBlocking(DB_EXECUTOR, () -> {
            String id = episode.getId() != null ? episode.getId() : UUID.randomUUID().toString();
            appendEvent(EVENT_TYPE_EPISODE, id, episode);
            // Return episode with id set
            return Episode.builder()
                    .id(id)
                    .agentId(episode.getAgentId())
                    .turnId(episode.getTurnId())
                    .timestamp(episode.getTimestamp())
                    .input(episode.getInput())
                    .output(episode.getOutput())
                    .action(episode.getAction())
                    .context(episode.getContext())
                    .tags(episode.getTags())
                    .reward(episode.getReward())
                    .build();
        });
    }

    @Override
    @NotNull
    public Promise<List<Episode>> queryEpisodes(@NotNull MemoryFilter filter, int limit) {
        return Promise.ofBlocking(DB_EXECUTOR, () ->
                queryEntities(EVENT_TYPE_EPISODE, limit, Episode.class));
    }

    @Override
    @NotNull
    public Promise<List<Episode>> searchEpisodes(@NotNull String query, int limit) {
        // Text-based search over payload JSON — use LIKE for portability
        return Promise.ofBlocking(DB_EXECUTOR, () ->
                searchEntities(EVENT_TYPE_EPISODE, query, limit, Episode.class));
    }

    // ── Semantic memory ───────────────────────────────────────────────────────

    @Override
    @NotNull
    public Promise<Fact> storeFact(@NotNull Fact fact) {
        return Promise.ofBlocking(DB_EXECUTOR, () -> {
            String id = fact.getId() != null ? fact.getId() : UUID.randomUUID().toString();
            Fact withId = Fact.builder()
                    .id(id)
                    .agentId(fact.getAgentId())
                    .subject(fact.getSubject())
                    .predicate(fact.getPredicate())
                    .object(fact.getObject())
                    .confidence(fact.getConfidence())
                    .source(fact.getSource())
                    .metadata(fact.getMetadata())
                    .build();
            appendEvent(EVENT_TYPE_FACT, id, withId);
            return withId;
        });
    }

    @Override
    @NotNull
    public Promise<List<Fact>> queryFacts(
            @Nullable String subject,
            @NotNull String predicate,
            @Nullable String object) {
        return Promise.ofBlocking(DB_EXECUTOR, () -> {
            List<Fact> all = queryEntities(EVENT_TYPE_FACT, 1000, Fact.class);
            return all.stream()
                    .filter(f -> (subject == null || subject.equals(f.getSubject()))
                              && predicate.equals(f.getPredicate())
                              && (object == null || object.equals(f.getObject())))
                    .toList();
        });
    }

    @Override
    @NotNull
    public Promise<List<Fact>> searchFacts(@NotNull String concept, int limit) {
        return Promise.ofBlocking(DB_EXECUTOR, () ->
                searchEntities(EVENT_TYPE_FACT, concept, limit, Fact.class));
    }

    // ── Procedural memory ─────────────────────────────────────────────────────

    @Override
    @NotNull
    public Promise<Policy> storePolicy(@NotNull Policy policy) {
        return Promise.ofBlocking(DB_EXECUTOR, () -> {
            String id = policy.getId() != null ? policy.getId() : UUID.randomUUID().toString();
            appendEvent(EVENT_TYPE_POLICY, id, policy);
            return policy;
        });
    }

    @Override
    @NotNull
    public Promise<List<Policy>> queryPolicies(@NotNull String situation, double minConfidence) {
        return Promise.ofBlocking(DB_EXECUTOR, () -> {
            List<Policy> all = queryEntities(EVENT_TYPE_POLICY, 1000, Policy.class);
            return all.stream()
                    .filter(p -> p.getConfidence() >= minConfidence)
                    .toList();
        });
    }

    @Override
    @NotNull
    public Promise<Policy> getPolicy(@NotNull String policyId) {
        return Promise.ofBlocking(DB_EXECUTOR, () -> {
            String sql = "SELECT payload FROM agent_memory_events " +
                         "WHERE agent_id = ? AND tenant_id = ? AND event_type = ? " +
                         "  AND entity_id = ? ORDER BY created_at DESC LIMIT 1";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, agentId);
                ps.setString(2, tenantId);
                ps.setString(3, EVENT_TYPE_POLICY);
                ps.setString(4, policyId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return null;
                    return MAPPER.readValue(rs.getString("payload"), Policy.class);
                }
            }
        });
    }

    // ── Negative Knowledge memory ─────────────────────────────────────────────

    private static final String EVENT_TYPE_NEGATIVE_KNOWLEDGE = "NEGATIVE_KNOWLEDGE";

    @Override
    @NotNull
    public Promise<NegativeKnowledge> storeNegativeKnowledge(@NotNull NegativeKnowledge negativeKnowledge) {
        return Promise.ofBlocking(DB_EXECUTOR, () -> {
            String id = negativeKnowledge.id();
            appendEvent(EVENT_TYPE_NEGATIVE_KNOWLEDGE, id, negativeKnowledge);
            return negativeKnowledge;
        });
    }

    @Override
    @NotNull
    public Promise<List<NegativeKnowledge>> queryNegativeKnowledgeBySkill(@NotNull String skillId, int limit) {
        return Promise.ofBlocking(DB_EXECUTOR, () -> {
            String sql = "SELECT payload FROM agent_memory_events " +
                         "WHERE agent_id = ? AND tenant_id = ? AND event_type = ? " +
                         "  ORDER BY created_at DESC LIMIT ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, agentId);
                ps.setString(2, tenantId);
                ps.setString(3, EVENT_TYPE_NEGATIVE_KNOWLEDGE);
                ps.setInt(4, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    List<NegativeKnowledge> results = new ArrayList<>();
                    while (rs.next()) {
                        results.add(MAPPER.readValue(rs.getString("payload"), NegativeKnowledge.class));
                    }
                    return results;
                }
            }
        });
    }

    @Override
    @NotNull
    public Promise<List<NegativeKnowledge>> queryNegativeKnowledgeByMasteryState(
            @NotNull com.ghatana.agent.mastery.MasteryState masteryState, int limit) {
        return Promise.ofBlocking(DB_EXECUTOR, () -> {
            String sql = "SELECT payload FROM agent_memory_events " +
                         "WHERE agent_id = ? AND tenant_id = ? AND event_type = ? " +
                         "  ORDER BY created_at DESC LIMIT ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, agentId);
                ps.setString(2, tenantId);
                ps.setString(3, EVENT_TYPE_NEGATIVE_KNOWLEDGE);
                ps.setInt(4, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    List<NegativeKnowledge> results = new ArrayList<>();
                    while (rs.next()) {
                        NegativeKnowledge nk = MAPPER.readValue(rs.getString("payload"), NegativeKnowledge.class);
                        results.add(nk);
                    }
                    return results;
                }
            }
        });
    }

    @Override
    @NotNull
    public Promise<List<NegativeKnowledge>> queryNegativeKnowledgeByVersionContext(
            @NotNull com.ghatana.agent.context.version.VersionContext versionContext, int limit) {
        return Promise.ofBlocking(DB_EXECUTOR, () -> {
            String sql = "SELECT payload FROM agent_memory_events " +
                         "WHERE agent_id = ? AND tenant_id = ? AND event_type = ? " +
                         "  ORDER BY created_at DESC LIMIT ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, agentId);
                ps.setString(2, tenantId);
                ps.setString(3, EVENT_TYPE_NEGATIVE_KNOWLEDGE);
                ps.setInt(4, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    List<NegativeKnowledge> results = new ArrayList<>();
                    while (rs.next()) {
                        results.add(MAPPER.readValue(rs.getString("payload"), NegativeKnowledge.class));
                    }
                    return results;
                }
            }
        });
    }

    @Override
    @NotNull
    public Promise<List<NegativeKnowledge>> queryNegativeKnowledgeByFreshness(
            @NotNull java.time.Duration freshnessThreshold, int limit) {
        return Promise.ofBlocking(DB_EXECUTOR, () -> {
            Instant cutoff = Instant.now().minus(freshnessThreshold);
            String sql = "SELECT payload FROM agent_memory_events " +
                         "WHERE agent_id = ? AND tenant_id = ? AND event_type = ? " +
                         "  AND created_at >= ? ORDER BY created_at DESC LIMIT ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, agentId);
                ps.setString(2, tenantId);
                ps.setString(3, EVENT_TYPE_NEGATIVE_KNOWLEDGE);
                ps.setTimestamp(4, Timestamp.from(cutoff));
                ps.setInt(5, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    List<NegativeKnowledge> results = new ArrayList<>();
                    while (rs.next()) {
                        results.add(MAPPER.readValue(rs.getString("payload"), NegativeKnowledge.class));
                    }
                    return results;
                }
            }
        });
    }

    @Override
    @NotNull
    public Promise<List<NegativeKnowledge>> queryNegativeKnowledgeByTenant(@NotNull String tenantId, int limit) {
        return Promise.ofBlocking(DB_EXECUTOR, () -> {
            String sql = "SELECT payload FROM agent_memory_events " +
                         "WHERE tenant_id = ? AND event_type = ? " +
                         "  ORDER BY created_at DESC LIMIT ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tenantId);
                ps.setString(2, EVENT_TYPE_NEGATIVE_KNOWLEDGE);
                ps.setInt(3, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    List<NegativeKnowledge> results = new ArrayList<>();
                    while (rs.next()) {
                        results.add(MAPPER.readValue(rs.getString("payload"), NegativeKnowledge.class));
                    }
                    return results;
                }
            }
        });
    }

    @Override
    @NotNull
    public Promise<List<NegativeKnowledge>> queryNegativeKnowledge(@NotNull MemoryFilter filter, int limit) {
        return Promise.ofBlocking(DB_EXECUTOR, () -> {
            String sql = "SELECT payload FROM agent_memory_events " +
                         "WHERE agent_id = ? AND tenant_id = ? AND event_type = ? " +
                         "  ORDER BY created_at DESC LIMIT ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, agentId);
                ps.setString(2, tenantId);
                ps.setString(3, EVENT_TYPE_NEGATIVE_KNOWLEDGE);
                ps.setInt(4, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    List<NegativeKnowledge> results = new ArrayList<>();
                    while (rs.next()) {
                        results.add(MAPPER.readValue(rs.getString("payload"), NegativeKnowledge.class));
                    }
                    return results;
                }
            }
        });
    }

    // ── Preference memory ─────────────────────────────────────────────────────

    @Override
    @NotNull
    public Promise<Preference> storePreference(@NotNull Preference preference) {
        return Promise.ofBlocking(DB_EXECUTOR, () -> {
            String id = preference.getKey();
            appendEvent(EVENT_TYPE_PREFERENCE, id, preference);
            return preference;
        });
    }

    @Override
    @NotNull
    public Promise<String> getPreference(@NotNull String key) {
        return Promise.ofBlocking(DB_EXECUTOR, () -> {
            List<Preference> all = queryEntities(EVENT_TYPE_PREFERENCE, 1000, Preference.class);
            return all.stream()
                    .filter(p -> key.equals(p.getKey()))
                    .map(Preference::getValue)
                    .findFirst()
                    .orElse(null);
        });
    }

    @Override
    @NotNull
    public Promise<Map<String, String>> getPreferences(@NotNull String namespace) {
        return Promise.ofBlocking(DB_EXECUTOR, () -> {
            List<Preference> all = queryEntities(EVENT_TYPE_PREFERENCE, 1000, Preference.class);
            Map<String, String> result = new java.util.LinkedHashMap<>();
            for (Preference p : all) {
                if (p.getKey() != null && p.getKey().startsWith(namespace)) {
                    result.put(p.getKey(), p.getValue());
                }
            }
            return result;
        });
    }

    // ── Memory management ─────────────────────────────────────────────────────

    @Override
    @NotNull
    public Promise<GovernanceResult> applyGovernance(@NotNull GovernancePolicy policy) {
        return Promise.ofBlocking(DB_EXECUTOR, () -> {
            // Retention: delete events older than the retention window
            int deleted = 0;
            if (policy.getRetentionPeriod() != null && !policy.getRetentionPeriod().isZero()) {
                String sql = "DELETE FROM agent_memory_events " +
                             "WHERE agent_id = ? AND tenant_id = ? " +
                             "  AND created_at < ?";
                Instant cutoff = Instant.now().minus(policy.getRetentionPeriod());
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, agentId);
                    ps.setString(2, tenantId);
                    ps.setTimestamp(3, Timestamp.from(cutoff));
                    deleted = ps.executeUpdate();
                }
            }
            log.info("Governance applied: deleted {} events (agentId={})", deleted, agentId);
            return new GovernanceResult(deleted, 0, deleted, 0);
        });
    }

    @Override
    @NotNull
    public Promise<Integer> clearMemory() {
        return Promise.ofBlocking(DB_EXECUTOR, () -> {
            String sql = "DELETE FROM agent_memory_events WHERE agent_id = ? AND tenant_id = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, agentId);
                ps.setString(2, tenantId);
                int count = ps.executeUpdate();
                log.warn("Cleared {} memory events for agentId={}", count, agentId);
                return count;
            }
        });
    }

    @Override
    @NotNull
    public Promise<MemoryStats> getStats() {
        return Promise.ofBlocking(DB_EXECUTOR, () -> {
            String sql = "SELECT event_type, COUNT(*) as cnt " +
                         "FROM agent_memory_events " +
                         "WHERE agent_id = ? AND tenant_id = ? " +
                         "GROUP BY event_type";
            long episodes = 0, facts = 0, policies = 0, preferences = 0;
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, agentId);
                ps.setString(2, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        long cnt = rs.getLong("cnt");
                        switch (rs.getString("event_type")) {
                            case EVENT_TYPE_EPISODE    -> episodes = cnt;
                            case EVENT_TYPE_FACT       -> facts = cnt;
                            case EVENT_TYPE_POLICY     -> policies = cnt;
                            case EVENT_TYPE_PREFERENCE -> preferences = cnt;
                        }
                    }
                }
            }
            return new MemoryStats(episodes, facts, policies, preferences, 0L);
        });
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private void appendEvent(String eventType, String entityId, Object entity) throws Exception {
        String id = UUID.randomUUID().toString();
        String payload = MAPPER.writeValueAsString(entity);
        String sql = "INSERT INTO agent_memory_events " +
                     "(id, agent_id, tenant_id, event_type, entity_id, payload) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, agentId);
            ps.setString(3, tenantId);
            ps.setString(4, eventType);
            ps.setString(5, entityId);
            ps.setString(6, payload);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("appendEvent failed: type={}, entityId={}, err={}", eventType, entityId, e.getMessage());
            throw e;
        }
    }

    private <T> List<T> queryEntities(String eventType, int limit, Class<T> type) throws Exception {
        // Read the latest event per entity_id (last-write-wins within the event log)
        String sql = "SELECT DISTINCT ON (entity_id) payload " +
                     "FROM agent_memory_events " +
                     "WHERE agent_id = ? AND tenant_id = ? AND event_type = ? " +
                     "ORDER BY entity_id, created_at DESC " +
                     "LIMIT ?";
        List<T> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, agentId);
            ps.setString(2, tenantId);
            ps.setString(3, eventType);
            ps.setInt(4, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(MAPPER.readValue(rs.getString("payload"), type));
                }
            }
        }
        return result;
    }

    private <T> List<T> searchEntities(String eventType, String query, int limit, Class<T> type)
            throws Exception {
        String sql = "SELECT DISTINCT ON (entity_id) payload " +
                     "FROM agent_memory_events " +
                     "WHERE agent_id = ? AND tenant_id = ? AND event_type = ? " +
                     "  AND payload LIKE ? " +
                     "ORDER BY entity_id, created_at DESC " +
                     "LIMIT ?";
        List<T> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, agentId);
            ps.setString(2, tenantId);
            ps.setString(3, eventType);
            ps.setString(4, "%" + query + "%");
            ps.setInt(5, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(MAPPER.readValue(rs.getString("payload"), type));
                }
            }
        }
        return result;
    }
}
