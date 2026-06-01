package com.ghatana.digitalmarketing.persistence.eventstore;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.domain.eventstore.TenantContext;
import com.ghatana.platform.types.identity.Offset;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * PostgreSQL-backed tenant-scoped implementation of {@link EventLogStore}.
 *
 * <p>Maintains monotonic per-tenant offsets with explicit transactional locking in
 * {@code dmos_event_log_offsets}. All blocking I/O runs through {@code Promise.ofBlocking}.
 * Tailing is intentionally unsupported in this adapter.</p>
 *
 * @doc.type class
 * @doc.purpose Durable tenant-scoped event log storage for DMOS campaign event sourcing
 * @doc.layer product
 * @doc.pattern Adapter, Repository
 */
public final class PostgresEventLogStore implements EventLogStore {

    private static final Logger LOG = LoggerFactory.getLogger(PostgresEventLogStore.class);
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {};

    private static final String INIT_OFFSET_SQL =
        "INSERT INTO dmos_event_log_offsets (tenant_id, next_offset) VALUES (?, 1) " +
        "ON CONFLICT (tenant_id) DO NOTHING";

    private static final String SELECT_NEXT_OFFSET_SQL =
        "SELECT next_offset FROM dmos_event_log_offsets WHERE tenant_id = ? FOR UPDATE";

    private static final String UPDATE_NEXT_OFFSET_SQL =
        "UPDATE dmos_event_log_offsets SET next_offset = ? WHERE tenant_id = ?";

    private static final String INSERT_EVENT_SQL =
        "INSERT INTO dmos_event_log (tenant_id, tenant_offset, event_id, event_type, event_version, event_ts, payload, content_type, headers_json, idempotency_key) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)";

    private static final String READ_SQL =
        "SELECT event_id, event_type, event_version, event_ts, payload, content_type, headers_json, idempotency_key " +
        "FROM dmos_event_log WHERE tenant_id = ? AND tenant_offset >= ? ORDER BY tenant_offset ASC LIMIT ?";

    private static final String READ_BY_TIME_SQL =
        "SELECT event_id, event_type, event_version, event_ts, payload, content_type, headers_json, idempotency_key " +
        "FROM dmos_event_log WHERE tenant_id = ? AND event_ts >= ? AND event_ts < ? ORDER BY tenant_offset ASC LIMIT ?";

    private static final String READ_BY_TYPE_SQL =
        "SELECT event_id, event_type, event_version, event_ts, payload, content_type, headers_json, idempotency_key " +
        "FROM dmos_event_log WHERE tenant_id = ? AND event_type = ? AND tenant_offset >= ? ORDER BY tenant_offset ASC LIMIT ?";

    private static final String LATEST_OFFSET_SQL =
        "SELECT MAX(tenant_offset) FROM dmos_event_log WHERE tenant_id = ?";

    private final DataSource dataSource;
    private final Executor executor;
    private final ObjectMapper objectMapper;

    public PostgresEventLogStore(DataSource dataSource, Executor executor) {
        this(dataSource, executor, new ObjectMapper());
    }

    public PostgresEventLogStore(DataSource dataSource, Executor executor, ObjectMapper objectMapper) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public Promise<Offset> append(TenantContext tenant, EventEntry entry) {
        Objects.requireNonNull(tenant, "tenant must not be null");
        Objects.requireNonNull(entry, "entry must not be null");

        return Promise.ofBlocking(executor, () -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    long tenantOffset = reserveOffset(connection, tenant.tenantId());
                    insertEvent(connection, tenant.tenantId(), tenantOffset, entry);
                    connection.commit();
                    return Offset.of(String.valueOf(tenantOffset));
                } catch (Exception e) {
                    connection.rollback();
                    throw e;
                } finally {
                    connection.setAutoCommit(true);
                }
            } catch (Exception e) {
                LOG.error("[DMOS-EVENTSTORE] append failed for tenant={}", tenant.tenantId(), e);
                throw new IllegalStateException("Failed to append event", e);
            }
        });
    }

    @Override
    public Promise<List<Offset>> appendBatch(TenantContext tenant, List<EventEntry> entries) {
        Objects.requireNonNull(tenant, "tenant must not be null");
        Objects.requireNonNull(entries, "entries must not be null");

        return Promise.ofBlocking(executor, () -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    List<Offset> offsets = new ArrayList<>(entries.size());
                    for (EventEntry entry : entries) {
                        long tenantOffset = reserveOffset(connection, tenant.tenantId());
                        insertEvent(connection, tenant.tenantId(), tenantOffset, entry);
                        offsets.add(Offset.of(String.valueOf(tenantOffset)));
                    }
                    connection.commit();
                    return offsets;
                } catch (Exception e) {
                    connection.rollback();
                    throw e;
                } finally {
                    connection.setAutoCommit(true);
                }
            } catch (Exception e) {
                LOG.error("[DMOS-EVENTSTORE] appendBatch failed for tenant={}", tenant.tenantId(), e);
                throw new IllegalStateException("Failed to append event batch", e);
            }
        });
    }

    @Override
    public Promise<List<EventEntry>> read(TenantContext tenant, Offset from, int limit) {
        Objects.requireNonNull(tenant, "tenant must not be null");
        Objects.requireNonNull(from, "from must not be null");

        return Promise.ofBlocking(executor, () -> {
            long startOffset = normalizeReadOffset(from);
            int boundedLimit = Math.max(1, limit);

            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(READ_SQL)) {
                statement.setString(1, tenant.tenantId());
                statement.setLong(2, startOffset);
                statement.setInt(3, boundedLimit);
                try (ResultSet rs = statement.executeQuery()) {
                    return mapEntries(rs);
                }
            } catch (Exception e) {
                LOG.error("[DMOS-EVENTSTORE] read failed for tenant={} from={}", tenant.tenantId(), from.value(), e);
                throw new IllegalStateException("Failed to read events", e);
            }
        });
    }

    @Override
    public Promise<List<EventEntry>> readByTimeRange(TenantContext tenant, Instant startTime, Instant endTime, int limit) {
        Objects.requireNonNull(tenant, "tenant must not be null");
        Objects.requireNonNull(startTime, "startTime must not be null");
        Objects.requireNonNull(endTime, "endTime must not be null");

        return Promise.ofBlocking(executor, () -> {
            int boundedLimit = Math.max(1, limit);
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(READ_BY_TIME_SQL)) {
                statement.setString(1, tenant.tenantId());
                statement.setTimestamp(2, Timestamp.from(startTime));
                statement.setTimestamp(3, Timestamp.from(endTime));
                statement.setInt(4, boundedLimit);
                try (ResultSet rs = statement.executeQuery()) {
                    return mapEntries(rs);
                }
            } catch (Exception e) {
                LOG.error("[DMOS-EVENTSTORE] readByTimeRange failed for tenant={}", tenant.tenantId(), e);
                throw new IllegalStateException("Failed to read events by time range", e);
            }
        });
    }

    @Override
    public Promise<List<EventEntry>> readByType(TenantContext tenant, String eventType, Offset from, int limit) {
        Objects.requireNonNull(tenant, "tenant must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(from, "from must not be null");

        return Promise.ofBlocking(executor, () -> {
            long startOffset = normalizeReadOffset(from);
            int boundedLimit = Math.max(1, limit);

            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(READ_BY_TYPE_SQL)) {
                statement.setString(1, tenant.tenantId());
                statement.setString(2, eventType);
                statement.setLong(3, startOffset);
                statement.setInt(4, boundedLimit);
                try (ResultSet rs = statement.executeQuery()) {
                    return mapEntries(rs);
                }
            } catch (Exception e) {
                LOG.error("[DMOS-EVENTSTORE] readByType failed for tenant={} eventType={}", tenant.tenantId(), eventType, e);
                throw new IllegalStateException("Failed to read events by type", e);
            }
        });
    }

    @Override
    public Promise<Offset> getLatestOffset(TenantContext tenant) {
        Objects.requireNonNull(tenant, "tenant must not be null");

        return Promise.ofBlocking(executor, () -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(LATEST_OFFSET_SQL)) {
                statement.setString(1, tenant.tenantId());
                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) {
                        long value = rs.getLong(1);
                        return Offset.of(String.valueOf(value));
                    }
                    return Offset.of("0");
                }
            } catch (Exception e) {
                LOG.error("[DMOS-EVENTSTORE] getLatestOffset failed for tenant={}", tenant.tenantId(), e);
                throw new IllegalStateException("Failed to resolve latest offset", e);
            }
        });
    }

    @Override
    public Promise<Offset> getEarliestOffset(TenantContext tenant) {
        Objects.requireNonNull(tenant, "tenant must not be null");
        return Promise.of(Offset.of("0"));
    }

    @Override
    public Promise<Subscription> tail(TenantContext tenant, Offset from, Consumer<EventEntry> handler) {
        return Promise.ofException(new IllegalStateException(
            "PostgresEventLogStore does not support tail subscriptions; use polling read()"
        ));
    }

    private long reserveOffset(Connection connection, String tenantId) throws Exception {
        try (PreparedStatement init = connection.prepareStatement(INIT_OFFSET_SQL)) {
            init.setString(1, tenantId);
            init.executeUpdate();
        }

        long nextOffset;
        try (PreparedStatement select = connection.prepareStatement(SELECT_NEXT_OFFSET_SQL)) {
            select.setString(1, tenantId);
            try (ResultSet rs = select.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("No offset row for tenant: " + tenantId);
                }
                nextOffset = rs.getLong(1);
            }
        }

        try (PreparedStatement update = connection.prepareStatement(UPDATE_NEXT_OFFSET_SQL)) {
            update.setLong(1, nextOffset + 1);
            update.setString(2, tenantId);
            update.executeUpdate();
        }
        return nextOffset;
    }

    private void insertEvent(Connection connection, String tenantId, long tenantOffset, EventEntry entry) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_EVENT_SQL)) {
            statement.setString(1, tenantId);
            statement.setLong(2, tenantOffset);
            statement.setObject(3, entry.eventId());
            statement.setString(4, entry.eventType());
            statement.setString(5, entry.eventVersion());
            statement.setTimestamp(6, Timestamp.from(entry.timestamp()));
            statement.setBytes(7, toBytes(entry.payload()));
            statement.setString(8, entry.contentType());
            statement.setString(9, objectMapper.writeValueAsString(entry.headers()));
            if (entry.idempotencyKey().isPresent()) {
                statement.setString(10, entry.idempotencyKey().get());
            } else {
                statement.setNull(10, java.sql.Types.VARCHAR);
            }
            statement.executeUpdate();
        }
    }

    private List<EventEntry> mapEntries(ResultSet rs) throws Exception {
        List<EventEntry> entries = new ArrayList<>();
        while (rs.next()) {
            Map<String, String> headers = objectMapper.readValue(rs.getString("headers_json"), STRING_MAP);
            String idempotency = rs.getString("idempotency_key");

            entries.add(new EventEntry(
                UUID.fromString(rs.getString("event_id")),
                rs.getString("event_type"),
                rs.getString("event_version"),
                rs.getTimestamp("event_ts").toInstant(),
                ByteBuffer.wrap(rs.getBytes("payload")),
                rs.getString("content_type"),
                headers,
                Optional.ofNullable(idempotency),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
            ));
        }
        return entries;
    }

    private static byte[] toBytes(ByteBuffer payload) {
        ByteBuffer duplicate = payload.asReadOnlyBuffer();
        byte[] bytes = new byte[duplicate.remaining()];
        duplicate.get(bytes);
        return bytes;
    }

    private static long normalizeReadOffset(Offset from) {
        long parsed = Long.parseLong(from.value());
        return parsed <= 0 ? 1L : parsed;
    }
}
