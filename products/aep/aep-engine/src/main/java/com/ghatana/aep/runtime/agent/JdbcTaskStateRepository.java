package com.ghatana.agent.memory.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.agent.memory.model.taskstate.*;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * PostgreSQL implementation of {@link TaskStateRepository}.
 *
 * <p>Uses JSONB columns for complex sub-structures (phases, checkpoints, blockers,
 * invariants, done-criteria, dependencies, environment). All blocking JDBC calls
 * are executed off the ActiveJ eventloop via {@link Promise#ofBlocking}.
 *
 * <p>Schema created by {@code V003__create_task_states.sql}.
 *
 * @doc.type class
 * @doc.purpose PostgreSQL task state persistence with JDBC + JSONB
 * @doc.layer agent-memory
 * @doc.pattern Repository
 * @doc.gaa.memory episodic
 */
public class JdbcTaskStateRepository implements TaskStateRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcTaskStateRepository.class);

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    // ─────────────────────────────────────────────────────────────────────────
    // SQL statements
    // ─────────────────────────────────────────────────────────────────────────

    private static final String SQL_UPSERT = """
            INSERT INTO task_states (
                task_id, agent_id, tenant_id, description, status,
                phases, checkpoints, blockers, invariants, done_criteria,
                dependencies, environment,
                created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb,
                      ?::jsonb, ?::jsonb, ?::jsonb, ?, ?)
            ON CONFLICT (task_id) DO UPDATE SET
                agent_id      = EXCLUDED.agent_id,
                description   = EXCLUDED.description,
                status        = EXCLUDED.status,
                phases        = EXCLUDED.phases,
                checkpoints   = EXCLUDED.checkpoints,
                blockers      = EXCLUDED.blockers,
                invariants    = EXCLUDED.invariants,
                done_criteria = EXCLUDED.done_criteria,
                dependencies  = EXCLUDED.dependencies,
                environment   = EXCLUDED.environment,
                updated_at    = EXCLUDED.updated_at
            """;

    private static final String SQL_FIND_BY_ID = """
            SELECT task_id, agent_id, tenant_id, description, status,
                   phases, checkpoints, blockers, invariants, done_criteria,
                   dependencies, environment, created_at, updated_at,
                   completed_at, archived_at
              FROM task_states
             WHERE task_id = ? AND archived_at IS NULL
            """;

    private static final String SQL_FIND_ACTIVE_BY_AGENT = """
            SELECT task_id, agent_id, tenant_id, description, status,
                   phases, checkpoints, blockers, invariants, done_criteria,
                   dependencies, environment, created_at, updated_at,
                   completed_at, archived_at
              FROM task_states
             WHERE agent_id = ? AND status IN ('IN_PROGRESS', 'PAUSED', 'BLOCKED', 'CREATED', 'PLANNING')
               AND archived_at IS NULL
             ORDER BY created_at DESC
            """;

    private static final String SQL_UPDATE_STATUS = """
            UPDATE task_states
               SET status = ?, updated_at = ?
             WHERE task_id = ?
            """;

    private static final String SQL_ARCHIVE = """
            UPDATE task_states
               SET status = 'ARCHIVED', archived_at = ?, updated_at = ?
             WHERE task_id = ? AND archived_at IS NULL
            """;

    private static final String SQL_ARCHIVE_INACTIVE = """
            UPDATE task_states
               SET status = 'ARCHIVED', archived_at = NOW(), updated_at = NOW()
             WHERE status IN ('IN_PROGRESS', 'PAUSED', 'BLOCKED', 'CREATED', 'PLANNING')
               AND archived_at IS NULL
               AND updated_at < ?
            """;

    // ─────────────────────────────────────────────────────────────────────────
    // Fields
    // ─────────────────────────────────────────────────────────────────────────

    private final DataSource dataSource;
    private final ExecutorService executor;

    // ─────────────────────────────────────────────────────────────────────────
    // Construction
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates a repository backed by the given DataSource.
     *
     * @param dataSource JDBC DataSource (e.g., HikariCP)
     */
    public JdbcTaskStateRepository(@NotNull DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TaskStateRepository implementation
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @NotNull
    public Promise<TaskState> save(@NotNull TaskState task) {
        Objects.requireNonNull(task, "task");
        return Promise.ofBlocking(executor, () -> {
            log.debug("Saving task state: {} (agent={}, status={})",
                    task.getTaskId(), task.getAgentId(), task.getStatus());

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SQL_UPSERT)) {

                int i = 1;
                ps.setString(i++, task.getTaskId());
                ps.setString(i++, task.getAgentId());
                ps.setString(i++, task.getTenantId());
                ps.setString(i++, task.getDescription());
                ps.setString(i++, task.getStatus().name());
                ps.setString(i++, toJson(task.getPhases()));
                ps.setString(i++, toJson(task.getCheckpoints()));
                ps.setString(i++, toJson(task.getBlockers()));
                ps.setString(i++, toJson(task.getInvariants()));
                ps.setString(i++, toJson(task.getDoneCriteria()));
                ps.setString(i++, toJson(task.getDependencies()));
                ps.setString(i++, toJson(task.getEnvironmentSnapshot()));
                ps.setTimestamp(i++, Timestamp.from(task.getCreatedAt()));
                ps.setTimestamp(i, Timestamp.from(Instant.now()));
                ps.executeUpdate();
            }
            return task;
        });
    }

    @Override
    @NotNull
    public Promise<@Nullable TaskState> findById(@NotNull String taskId) {
        Objects.requireNonNull(taskId, "taskId");
        return Promise.ofBlocking(executor, () -> {
            log.debug("Finding task state: {}", taskId);
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_ID)) {
                ps.setString(1, taskId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapRow(rs);
                    }
                    return null;
                }
            }
        });
    }

    @Override
    @NotNull
    public Promise<List<TaskState>> findActiveByAgent(@NotNull String agentId) {
        Objects.requireNonNull(agentId, "agentId");
        return Promise.ofBlocking(executor, () -> {
            log.debug("Finding active tasks for agent: {}", agentId);
            List<TaskState> results = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SQL_FIND_ACTIVE_BY_AGENT)) {
                ps.setString(1, agentId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        results.add(mapRow(rs));
                    }
                }
            }
            return results;
        });
    }

    @Override
    @NotNull
    public Promise<Void> updateStatus(@NotNull String taskId, @NotNull String status) {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(status, "status");
        return Promise.ofBlocking(executor, () -> {
            log.debug("Updating task {} status to {}", taskId, status);
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SQL_UPDATE_STATUS)) {
                ps.setString(1, status);
                ps.setTimestamp(2, Timestamp.from(Instant.now()));
                ps.setString(3, taskId);
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    @NotNull
    public Promise<Void> archive(@NotNull String taskId) {
        Objects.requireNonNull(taskId, "taskId");
        return Promise.ofBlocking(executor, () -> {
            log.debug("Archiving task: {}", taskId);
            Instant now = Instant.now();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SQL_ARCHIVE)) {
                ps.setTimestamp(1, Timestamp.from(now));
                ps.setTimestamp(2, Timestamp.from(now));
                ps.setString(3, taskId);
                int rows = ps.executeUpdate();
                log.debug("Archived {} task row(s) for taskId={}", rows, taskId);
            }
            return null;
        });
    }

    @Override
    @NotNull
    public Promise<Integer> archiveInactiveSince(@NotNull Instant since) {
        Objects.requireNonNull(since, "since");
        return Promise.ofBlocking(executor, () -> {
            log.debug("Archiving tasks inactive since: {}", since);
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SQL_ARCHIVE_INACTIVE)) {
                ps.setTimestamp(1, Timestamp.from(since));
                int count = ps.executeUpdate();
                log.info("Archived {} inactive task state(s) (inactive since {})", count, since);
                return count;
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private TaskState mapRow(ResultSet rs) throws Exception {
        // Deserialise JSONB fields
        List<TaskPhase> phases = fromJson(rs.getString("phases"),
                new TypeReference<List<TaskPhase>>() {}, List.of());
        List<TaskCheckpoint> checkpoints = fromJson(rs.getString("checkpoints"),
                new TypeReference<List<TaskCheckpoint>>() {}, List.of());
        List<TaskBlocker> blockers = fromJson(rs.getString("blockers"),
                new TypeReference<List<TaskBlocker>>() {}, List.of());
        List<TaskInvariant> invariants = fromJson(rs.getString("invariants"),
                new TypeReference<List<TaskInvariant>>() {}, List.of());
        DoneCriteria doneCriteria = fromJson(rs.getString("done_criteria"),
                new TypeReference<DoneCriteria>() {}, null);
        List<TaskDependency> dependencies = fromJson(rs.getString("dependencies"),
                new TypeReference<List<TaskDependency>>() {}, List.of());
        EnvironmentSnapshot envSnapshot = fromJson(rs.getString("environment"),
                new TypeReference<EnvironmentSnapshot>() {}, null);

        String statusStr = rs.getString("status");
        TaskLifecycleStatus status = parseStatus(statusStr);

        return TaskState.builder()
                .taskId(rs.getString("task_id"))
                .agentId(rs.getString("agent_id"))
                .tenantId(rs.getString("tenant_id"))
                .description(rs.getString("description"))
                .status(status)
                .phases(phases)
                .checkpoints(checkpoints)
                .blockers(blockers)
                .invariants(invariants)
                .doneCriteria(doneCriteria)
                .dependencies(dependencies)
                .environmentSnapshot(envSnapshot)
                .createdAt(rs.getTimestamp("created_at").toInstant())
                .updatedAt(rs.getTimestamp("updated_at").toInstant())
                .build();
    }

    private static TaskLifecycleStatus parseStatus(String value) {
        if (value == null) return TaskLifecycleStatus.IN_PROGRESS;
        try {
            return TaskLifecycleStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown TaskLifecycleStatus '{}', defaulting to IN_PROGRESS", value);
            return TaskLifecycleStatus.IN_PROGRESS;
        }
    }

    private String toJson(Object obj) {
        if (obj == null) return "null";
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("Failed to serialise object to JSON: {}", e.getMessage());
            return "null";
        }
    }

    private <T> T fromJson(String json, TypeReference<T> type, T defaultValue) {
        if (json == null || json.equals("null") || json.isBlank()) return defaultValue;
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            log.warn("Failed to deserialise JSON to {}: {}", type.getType(), e.getMessage());
            return defaultValue;
        }
    }
}
