/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.repository.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.agent.framework.memory.Episode;
import com.ghatana.agent.framework.memory.EventLogMemoryStore;
import com.ghatana.agent.framework.memory.Fact;
import com.ghatana.agent.framework.memory.MemoryFilter;
import com.ghatana.agent.framework.memory.Policy;
import com.ghatana.agent.framework.memory.Preference;
import io.activej.promise.Promise;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import javax.sql.DataSource;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JDBC-backed agent memory store that persists episodic memory across restarts.
 *
 * <p><b>Strategy</b><br>
 * Extends {@link EventLogMemoryStore} so all in-memory materialized views and
 * index structures are inherited. Three operations are overridden to additionally
 * write through to PostgreSQL:
 * <ul>
 *   <li>{@link #storeEpisode} — persists to {@code agent_episodes}</li>
 *   <li>{@link #storeFact} — persists to {@code agent_facts}</li>
 *   <li>{@link #storePolicy} — persists to {@code agent_policies}</li>
 * </ul>
 *
 * <p>On construction, the store rehydrates the in-memory views from the last
 * {@code REHYDRATION_LIMIT} rows of each table so agents remember recent context
 * across restarts.
 *
 * <p><b>Concurrency</b><br>
 * All JDBC calls use {@link io.activej.promise.Promise#ofBlocking} to stay off
 * the ActiveJ event-loop thread. The inherited in-memory structures are
 * thread-safe ({@code ConcurrentHashMap}).
 *
 * @doc.type class
 * @doc.purpose JDBC-backed agent memory store; persists episodes, facts, and policies
 * @doc.layer api
 * @doc.pattern Repository, Decorator, Event Sourcing
 * @doc.gaa.memory episodic|semantic|procedural
 */
public class JdbcMemoryStore extends EventLogMemoryStore {

  private static final Logger logger = LoggerFactory.getLogger(JdbcMemoryStore.class);
  private static final int REHYDRATION_LIMIT = 500;
  private static final Executor JDBC_EXECUTOR =
      Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "memory-jdbc");
        t.setDaemon(true);
        return t;
      });

  private final DataSource dataSource;
  private final ObjectMapper objectMapper;

  /**
   * Creates the store, eagerly rehydrating recent memory from PostgreSQL.
   *
   * @param dataSource   JDBC data source pointing to the YAPPC PostgreSQL database
   * @param objectMapper Jackson mapper for JSON serialisation of JSONB columns
   */
  public JdbcMemoryStore(@NotNull DataSource dataSource, @NotNull ObjectMapper objectMapper) {
    super();
    this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    rehydrate();
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Episodic Memory — write-through + rehydration
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * {@inheritDoc}
   *
   * <p>Writes through to {@code agent_episodes} after updating the in-memory store.
   */
  @Override
  @NotNull
  public Promise<Episode> storeEpisode(@NotNull Episode episode) {
    return super.storeEpisode(episode)
        .then(stored -> Promise.ofBlocking(JDBC_EXECUTOR, () -> {
          persistEpisode(stored);
          return stored;
        }));
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Semantic Memory — write-through
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * {@inheritDoc}
   *
   * <p>Writes through to {@code agent_facts} after updating the in-memory store.
   */
  @Override
  @NotNull
  public Promise<Fact> storeFact(@NotNull Fact fact) {
    return super.storeFact(fact)
        .then(stored -> Promise.ofBlocking(JDBC_EXECUTOR, () -> {
          persistFact(stored);
          return stored;
        }));
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Procedural Memory — write-through
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * {@inheritDoc}
   *
   * <p>Writes through to {@code agent_policies} after updating the in-memory store.
   */
  @Override
  @NotNull
  public Promise<Policy> storePolicy(@NotNull Policy policy) {
    return super.storePolicy(policy)
        .then(stored -> Promise.ofBlocking(JDBC_EXECUTOR, () -> {
          persistPolicy(stored);
          return stored;
        }));
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Private: JDBC helpers
  // ═══════════════════════════════════════════════════════════════════════════

  private void persistEpisode(Episode e) {
    String sql = """
        INSERT INTO agent_episodes
          (id, agent_id, tenant_id, turn_id, input_text, output_text, action, context, tags, reward, occurred_at)
        VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?)
        ON CONFLICT (id) DO NOTHING
        """;
    try (Connection conn = dataSource.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, e.getId());
      ps.setString(2, e.getAgentId());
      ps.setString(3, "default"); // tenant isolation — extend when Episode carries tenantId
      ps.setString(4, e.getTurnId());
      ps.setString(5, e.getInput());
      ps.setString(6, e.getOutput());
      ps.setString(7, toJson(e.getAction()));
      ps.setString(8, toJson(e.getContext()));
      ps.setString(9, toJson(e.getTags()));
      ps.setDouble(10, e.getReward() != null ? e.getReward() : 0.0);
      ps.setTimestamp(11, e.getTimestamp() != null
          ? Timestamp.from(e.getTimestamp()) : Timestamp.from(Instant.now()));
      ps.executeUpdate();
    } catch (SQLException ex) {
      logger.error("Failed to persist episode id={}: {}", e.getId(), ex.getMessage());
    }
  }

  private void persistFact(Fact f) {
    String sql = """
        INSERT INTO agent_facts
          (id, agent_id, tenant_id, subject, predicate, object, confidence, source, metadata)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
        ON CONFLICT (id) DO UPDATE SET
          confidence = EXCLUDED.confidence,
          updated_at = NOW()
        """;
    try (Connection conn = dataSource.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, f.getId());
      ps.setString(2, f.getAgentId());
      ps.setString(3, "default");
      ps.setString(4, f.getSubject());
      ps.setString(5, f.getPredicate());
      ps.setString(6, f.getObject());
      ps.setDouble(7, f.getConfidence());
      ps.setString(8, f.getSource());
      ps.setString(9, toJson(f.getMetadata()));
      ps.executeUpdate();
    } catch (SQLException ex) {
      logger.error("Failed to persist fact id={}: {}", f.getId(), ex.getMessage());
    }
  }

  private void persistPolicy(Policy p) {
    String sql = """
        INSERT INTO agent_policies
          (id, agent_id, tenant_id, situation, action, confidence, use_count, metadata)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb)
        ON CONFLICT (id) DO UPDATE SET
          confidence = EXCLUDED.confidence,
          use_count = EXCLUDED.use_count,
          updated_at = NOW()
        """;
    try (Connection conn = dataSource.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, p.getId());
      ps.setString(2, p.getAgentId());
      ps.setString(3, "default");
      ps.setString(4, p.getSituation());
      ps.setString(5, p.getAction());
      ps.setDouble(6, p.getConfidence() != null ? p.getConfidence() : 0.5);
      ps.setInt(7, p.getUseCount() != null ? p.getUseCount() : 0);
      ps.setString(8, toJson(p.getMetadata()));
      ps.executeUpdate();
    } catch (SQLException ex) {
      logger.error("Failed to persist policy id={}: {}", p.getId(), ex.getMessage());
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Private: Rehydration
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Loads recent episodes, facts, and policies from PostgreSQL into the inherited
   * in-memory views so agents have context immediately after a restart.
   */
  private void rehydrate() {
    int episodes = rehydrateEpisodes();
    int facts = rehydrateFacts();
    int policies = rehydratePolicies();
    logger.info("JdbcMemoryStore rehydrated: episodes={} facts={} policies={}",
        episodes, facts, policies);
  }

  private int rehydrateEpisodes() {
    String sql = """
        SELECT id, agent_id, turn_id, input_text, output_text, occurred_at
        FROM agent_episodes
        ORDER BY occurred_at DESC
        LIMIT ?
        """;
    int count = 0;
    try (Connection conn = dataSource.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setInt(1, REHYDRATION_LIMIT);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          Episode ep = Episode.builder()
              .id(rs.getString("id"))
              .agentId(rs.getString("agent_id"))
              .turnId(rs.getString("turn_id"))
              .input(rs.getString("input_text"))
              .output(rs.getString("output_text"))
              .timestamp(rs.getTimestamp("occurred_at").toInstant())
              .build();
          // Restore directly via parent storeEpisode WITHOUT writing back to DB
          super.storeEpisode(ep);
          count++;
        }
      }
    } catch (Exception ex) {
      logger.warn("Episode rehydration failed (first startup?): {}", ex.getMessage());
    }
    return count;
  }

  private int rehydrateFacts() {
    String sql = """
        SELECT id, agent_id, subject, predicate, object, confidence, source
        FROM agent_facts
        ORDER BY updated_at DESC
        LIMIT ?
        """;
    int count = 0;
    try (Connection conn = dataSource.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setInt(1, REHYDRATION_LIMIT);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          Fact fact = Fact.builder()
              .id(rs.getString("id"))
              .agentId(rs.getString("agent_id"))
              .subject(rs.getString("subject"))
              .predicate(rs.getString("predicate"))
              .object(rs.getString("object"))
              .confidence(rs.getDouble("confidence"))
              .source(rs.getString("source"))
              .build();
          super.storeFact(fact);
          count++;
        }
      }
    } catch (Exception ex) {
      logger.warn("Fact rehydration failed: {}", ex.getMessage());
    }
    return count;
  }

  private int rehydratePolicies() {
    String sql = """
        SELECT id, agent_id, situation, action, confidence, use_count
        FROM agent_policies
        WHERE confidence >= 0.5
        ORDER BY confidence DESC
        LIMIT ?
        """;
    int count = 0;
    try (Connection conn = dataSource.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setInt(1, REHYDRATION_LIMIT);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          Policy policy = Policy.builder()
              .id(rs.getString("id"))
              .agentId(rs.getString("agent_id"))
              .situation(rs.getString("situation"))
              .action(rs.getString("action"))
              .confidence(rs.getDouble("confidence"))
              .useCount(rs.getInt("use_count"))
              .build();
          super.storePolicy(policy);
          count++;
        }
      }
    } catch (Exception ex) {
      logger.warn("Policy rehydration failed: {}", ex.getMessage());
    }
    return count;
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Private: Utilities
  // ═══════════════════════════════════════════════════════════════════════════

  private String toJson(Object value) {
    if (value == null) return "{}";
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception e) {
      logger.warn("JSON serialisation failed for memory value: {}", e.getMessage());
      return "{}";
    }
  }
}
