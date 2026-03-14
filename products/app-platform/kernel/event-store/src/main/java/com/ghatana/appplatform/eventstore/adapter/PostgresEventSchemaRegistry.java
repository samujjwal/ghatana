package com.ghatana.appplatform.eventstore.adapter;

import com.ghatana.appplatform.eventstore.domain.CompatibilityType;
import com.ghatana.appplatform.eventstore.domain.EventSchemaVersion;
import com.ghatana.appplatform.eventstore.domain.SchemaStatus;
import com.ghatana.appplatform.eventstore.port.EventSchemaRegistry;
import io.activej.promise.Promise;
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
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * JDBC adapter for {@link EventSchemaRegistry} backed by the {@code event_schema_registry} table.
 *
 * <p>All blocking JDBC calls are wrapped with {@code Promise.ofBlocking(executor, …)} so
 * they never block the ActiveJ eventloop thread.
 *
 * <p>The {@link #activateSchema(String, int)} method runs inside a single database transaction:
 * it sets the previous ACTIVE row to DEPRECATED and the target row to ACTIVE atomically.
 *
 * @doc.type class
 * @doc.purpose PostgreSQL JDBC adapter for the event schema version registry
 * @doc.layer product
 * @doc.pattern Adapter, Repository
 */
public final class PostgresEventSchemaRegistry implements EventSchemaRegistry {

    private static final Logger log = LoggerFactory.getLogger(PostgresEventSchemaRegistry.class);

    private static final String SQL_UPSERT =
        "INSERT INTO event_schema_registry "
        + "(event_type, version, json_schema, status, compat_type, description, created_at) "
        + "VALUES (?, ?, ?::jsonb, 'DRAFT'::event_schema_status, ?::event_schema_compat, ?, ?) "
        + "ON CONFLICT (event_type, version) DO UPDATE "
        + "  SET json_schema   = EXCLUDED.json_schema, "
        + "      compat_type   = EXCLUDED.compat_type, "
        + "      description   = EXCLUDED.description "
        + "  WHERE event_schema_registry.status = 'DRAFT'::event_schema_status";

    private static final String SQL_DEPRECATE_ACTIVE =
        "UPDATE event_schema_registry "
        + "SET status = 'DEPRECATED'::event_schema_status "
        + "WHERE event_type = ? AND status = 'ACTIVE'::event_schema_status";

    private static final String SQL_ACTIVATE =
        "UPDATE event_schema_registry "
        + "SET status = 'ACTIVE'::event_schema_status, activated_at = ? "
        + "WHERE event_type = ? AND version = ?";

    private static final String SQL_GET_ACTIVE =
        "SELECT event_type, version, json_schema, status, compat_type, description, "
        + "       created_at, activated_at "
        + "FROM event_schema_registry "
        + "WHERE event_type = ? AND status = 'ACTIVE'::event_schema_status "
        + "LIMIT 1";

    private static final String SQL_GET_VERSION =
        "SELECT event_type, version, json_schema, status, compat_type, description, "
        + "       created_at, activated_at "
        + "FROM event_schema_registry "
        + "WHERE event_type = ? AND version = ?";

    private static final String SQL_LIST_VERSIONS =
        "SELECT event_type, version, json_schema, status, compat_type, description, "
        + "       created_at, activated_at "
        + "FROM event_schema_registry "
        + "WHERE event_type = ? "
        + "ORDER BY version ASC";

    private final DataSource dataSource;
    private final Executor blockingExecutor;

    public PostgresEventSchemaRegistry(DataSource dataSource, Executor blockingExecutor) {
        this.dataSource       = dataSource;
        this.blockingExecutor = blockingExecutor;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Upserts the schema with status=DRAFT. If a non-DRAFT version with the same
     * {@code (eventType, version)} already exists the insert is a no-op (the
     * {@code ON CONFLICT WHERE status='DRAFT'} predicate prevents overwriting active schemas).
     */
    @Override
    public Promise<Void> registerSchema(EventSchemaVersion schema) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SQL_UPSERT)) {

                ps.setString(1, schema.eventType());
                ps.setInt(2, schema.version());
                ps.setString(3, schema.jsonSchema());
                ps.setString(4, schema.compatType().name());
                ps.setString(5, schema.description());
                ps.setTimestamp(6, Timestamp.from(
                    schema.createdAt() != null ? schema.createdAt() : Instant.now()));

                ps.executeUpdate();
                log.debug("Registered schema eventType={} version={}", schema.eventType(), schema.version());
            }
            return null;
        });
    }

    /**
     * {@inheritDoc}
     *
     * <p>Runs in a single transaction: deprecate current ACTIVE → activate target version.
     */
    @Override
    public Promise<Void> activateSchema(String eventType, int version) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    // Deprecate current active version (if any)
                    try (PreparedStatement ps = conn.prepareStatement(SQL_DEPRECATE_ACTIVE)) {
                        ps.setString(1, eventType);
                        ps.executeUpdate();
                    }
                    // Promote target to ACTIVE
                    try (PreparedStatement ps = conn.prepareStatement(SQL_ACTIVATE)) {
                        ps.setTimestamp(1, Timestamp.from(Instant.now()));
                        ps.setString(2, eventType);
                        ps.setInt(3, version);
                        int rows = ps.executeUpdate();
                        if (rows == 0) {
                            throw new IllegalStateException(
                                "Schema not found for activation: eventType=" + eventType + " version=" + version);
                        }
                    }
                    conn.commit();
                    log.info("Activated schema eventType={} version={}", eventType, version);
                } catch (Exception e) {
                    conn.rollback();
                    throw e;
                }
            }
            return null;
        });
    }

    /** {@inheritDoc} */
    @Override
    public Promise<Optional<EventSchemaVersion>> getActiveSchema(String eventType) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SQL_GET_ACTIVE)) {

                ps.setString(1, eventType);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(fromResultSet(rs));
                    }
                    return Optional.empty();
                }
            }
        });
    }

    /** {@inheritDoc} */
    @Override
    public Promise<Optional<EventSchemaVersion>> getSchema(String eventType, int version) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SQL_GET_VERSION)) {

                ps.setString(1, eventType);
                ps.setInt(2, version);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(fromResultSet(rs));
                    }
                    return Optional.empty();
                }
            }
        });
    }

    /** {@inheritDoc} */
    @Override
    public Promise<List<EventSchemaVersion>> listVersions(String eventType) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SQL_LIST_VERSIONS)) {

                ps.setString(1, eventType);
                List<EventSchemaVersion> results = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        results.add(fromResultSet(rs));
                    }
                }
                return results;
            }
        });
    }

    private EventSchemaVersion fromResultSet(ResultSet rs) throws SQLException {
        Timestamp createdAt   = rs.getTimestamp("created_at");
        Timestamp activatedAt = rs.getTimestamp("activated_at");

        return EventSchemaVersion.builder()
            .eventType(rs.getString("event_type"))
            .version(rs.getInt("version"))
            .jsonSchema(rs.getString("json_schema"))
            .status(SchemaStatus.valueOf(rs.getString("status")))
            .compatType(CompatibilityType.valueOf(rs.getString("compat_type")))
            .description(rs.getString("description"))
            .createdAt(createdAt != null ? createdAt.toInstant() : null)
            .activatedAt(activatedAt != null ? activatedAt.toInstant() : null)
            .build();
    }
}
