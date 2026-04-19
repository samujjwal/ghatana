package com.ghatana.datacloud.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.spi.BatchResult;
import com.ghatana.datacloud.spi.EntityStore;
import com.ghatana.datacloud.spi.TenantContext;
import io.activej.promise.Promise;
import org.h2.jdbcx.JdbcDataSource;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * @doc.type class
 * @doc.purpose File-backed H2 entity store for sovereign Data Cloud deployments
 * @doc.layer product
 * @doc.pattern Repository
 */
public final class H2SovereignEntityStore implements EntityStore, AutoCloseable {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private static final String SELECT_SQL = """
        SELECT collection_name, data_json, created_at, updated_at, version, deleted
          FROM dc_entities
         WHERE tenant_id = ? AND entity_id = ?
        """;

    private static final String INSERT_SQL = """
        INSERT INTO dc_entities (
            tenant_id, entity_id, collection_name, data_json, created_at, updated_at, version, deleted
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """;

    private static final String UPDATE_SQL = """
        UPDATE dc_entities
           SET collection_name = ?, data_json = ?, updated_at = ?, version = ?, deleted = ?
         WHERE tenant_id = ? AND entity_id = ?
        """;

    private static final String QUERY_SQL = """
        SELECT entity_id, collection_name, data_json, created_at, updated_at, version
          FROM dc_entities
         WHERE tenant_id = ? AND collection_name = ? AND deleted = FALSE
         ORDER BY updated_at DESC, entity_id ASC
         LIMIT ? OFFSET ?
        """;

    private static final String COUNT_SQL = """
        SELECT COUNT(*)
          FROM dc_entities
         WHERE tenant_id = ? AND collection_name = ? AND deleted = FALSE
        """;

    private static final String DELETE_SQL = """
        UPDATE dc_entities
           SET deleted = TRUE, updated_at = ?, version = version + 1
         WHERE tenant_id = ? AND entity_id = ? AND deleted = FALSE
        """;
        private static final int DELETE_BATCH_CHUNK_SIZE = 250;

    private static final String TOMBSTONE_COUNTS_SQL = """
        SELECT tenant_id, COUNT(*)
          FROM dc_entities
         WHERE deleted = TRUE
         GROUP BY tenant_id
        """;

    private static final String COMPACT_SQL = """
        DELETE FROM dc_entities
         WHERE tenant_id = ? AND deleted = TRUE
        """;

    private final Path databasePath;
    private final DataSource dataSource;
    private final Executor executor;

    public H2SovereignEntityStore(Path sovereignDirectory) {
        this(sovereignDirectory, Executors.newVirtualThreadPerTaskExecutor());
    }

    public H2SovereignEntityStore(Path sovereignDirectory, Executor executor) {
        this.databasePath = Objects.requireNonNull(sovereignDirectory, "sovereignDirectory").resolve("entities");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.dataSource = createDataSource(databasePath);
        initializeSchema();
    }

    @Override
    public Promise<Entity> save(TenantContext tenant, Entity entity) {
        return Promise.ofBlocking(executor, () -> saveSync(tenant, entity));
    }

    @Override
    public Promise<BatchResult<String>> saveBatch(TenantContext tenant, List<Entity> entities) {
        return Promise.ofBlocking(executor, () -> {
            for (Entity entity : entities) {
                saveSync(tenant, entity);
            }
            return BatchResult.success(entities.size());
        });
    }

    @Override
    public Promise<Optional<Entity>> findById(TenantContext tenant, EntityId id) {
        return Promise.ofBlocking(executor, () -> findByIdSync(tenant.tenantId(), id.value()));
    }

    @Override
    public Promise<List<Entity>> findByIds(TenantContext tenant, List<EntityId> ids) {
        return Promise.ofBlocking(executor, () -> {
            List<Entity> entities = new ArrayList<>();
            for (EntityId id : ids) {
                findByIdSync(tenant.tenantId(), id.value()).ifPresent(entities::add);
            }
            return entities;
        });
    }

    @Override
    public Promise<QueryResult> query(TenantContext tenant, QuerySpec query) {
        return Promise.ofBlocking(executor, () -> {
            long totalCount;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement countStatement = connection.prepareStatement(COUNT_SQL)) {
                countStatement.setString(1, tenant.tenantId());
                countStatement.setString(2, query.collection());
                try (ResultSet resultSet = countStatement.executeQuery()) {
                    resultSet.next();
                    totalCount = resultSet.getLong(1);
                }
            }

            List<Entity> entities = new ArrayList<>();
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement queryStatement = connection.prepareStatement(QUERY_SQL)) {
                queryStatement.setString(1, tenant.tenantId());
                queryStatement.setString(2, query.collection());
                queryStatement.setInt(3, query.limit());
                queryStatement.setInt(4, query.offset());
                try (ResultSet resultSet = queryStatement.executeQuery()) {
                    while (resultSet.next()) {
                        entities.add(mapEntity(resultSet.getString("entity_id"), resultSet));
                    }
                }
            }
            return QueryResult.of(entities, totalCount);
        });
    }

    @Override
    public Promise<Void> delete(TenantContext tenant, EntityId id) {
        return Promise.ofBlocking(executor, () -> {
            deleteSync(tenant.tenantId(), id.value());
            return null;
        });
    }

    @Override
    public Promise<BatchResult<String>> deleteBatch(TenantContext tenant, List<EntityId> ids) {
        return Promise.ofBlocking(executor, () -> {
            List<com.ghatana.datacloud.spi.BatchError<String>> errors = new ArrayList<>();
            int successCount = 0;
            try (Connection connection = dataSource.getConnection()) {
                boolean originalAutoCommit = connection.getAutoCommit();
                connection.setAutoCommit(false);
                try {
                    for (int offset = 0; offset < ids.size(); offset += DELETE_BATCH_CHUNK_SIZE) {
                        List<EntityId> chunk = ids.subList(offset, Math.min(offset + DELETE_BATCH_CHUNK_SIZE, ids.size()));
                        successCount += executeDeleteChunk(connection, tenant.tenantId(), chunk);
                    }
                    connection.commit();
                } catch (Exception exception) {
                    connection.rollback();
                    throw exception;
                } finally {
                    connection.setAutoCommit(originalAutoCommit);
                }
            }
            return new BatchResult<>(ids.size(), successCount, errors.size(), errors);
        });
    }

    private int executeDeleteChunk(Connection connection, String tenantId, List<EntityId> ids) throws Exception {
        StringBuilder sql = new StringBuilder("""
            UPDATE dc_entities
               SET deleted = TRUE, updated_at = ?, version = version + 1
             WHERE tenant_id = ? AND deleted = FALSE AND entity_id IN (
            """);
        appendPlaceholders(sql, ids.size());
        sql.append(')');

        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            statement.setTimestamp(1, Timestamp.from(Instant.now()));
            statement.setString(2, tenantId);
            for (int index = 0; index < ids.size(); index++) {
                statement.setString(index + 3, ids.get(index).value());
            }
            statement.executeUpdate();
        }
        return ids.size();
    }

    @Override
    public Promise<Long> count(TenantContext tenant, QuerySpec query) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(COUNT_SQL)) {
                statement.setString(1, tenant.tenantId());
                statement.setString(2, query.collection());
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    return resultSet.getLong(1);
                }
            }
        });
    }

    @Override
    public Promise<Boolean> exists(TenantContext tenant, EntityId id) {
        return findById(tenant, id).map(Optional::isPresent);
    }

    public Promise<Map<String, Long>> countTombstones() {
        return Promise.ofBlocking(executor, () -> {
            Map<String, Long> counts = new LinkedHashMap<>();
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(TOMBSTONE_COUNTS_SQL);
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    counts.put(resultSet.getString(1), resultSet.getLong(2));
                }
            }
            return counts;
        });
    }

    public Promise<Integer> compactTenant(String tenantId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(COMPACT_SQL)) {
                statement.setString(1, tenantId);
                int deletedRows = statement.executeUpdate();
                try (Statement checkpoint = connection.createStatement()) {
                    checkpoint.execute("CHECKPOINT");
                }
                return deletedRows;
            }
        });
    }

    public Path databasePath() {
        return databasePath;
    }

    @Override
    public void close() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement shutdown = connection.createStatement()) {
            shutdown.execute("SHUTDOWN");
        }
    }

    private Entity saveSync(TenantContext tenant, Entity entity) throws Exception {
        Instant now = Instant.now();
        try (Connection connection = dataSource.getConnection()) {
            Optional<EntityMetadata> existingMetadata = readMetadata(connection, tenant.tenantId(), entity.id().value());
            EntityMetadata metadata = existingMetadata
                .map(existing -> new EntityMetadata(
                    existing.createdAt(),
                    now,
                    Optional.empty(),
                    Optional.empty(),
                    existing.version() + 1))
                .orElseGet(() -> new EntityMetadata(now, now, Optional.empty(), Optional.empty(), 1));

            if (existingMetadata.isPresent()) {
                try (PreparedStatement statement = connection.prepareStatement(UPDATE_SQL)) {
                    statement.setString(1, entity.collection());
                    statement.setString(2, OBJECT_MAPPER.writeValueAsString(entity.data()));
                    statement.setTimestamp(3, Timestamp.from(metadata.updatedAt()));
                    statement.setLong(4, metadata.version());
                    statement.setBoolean(5, false);
                    statement.setString(6, tenant.tenantId());
                    statement.setString(7, entity.id().value());
                    statement.executeUpdate();
                }
            } else {
                try (PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
                    statement.setString(1, tenant.tenantId());
                    statement.setString(2, entity.id().value());
                    statement.setString(3, entity.collection());
                    statement.setString(4, OBJECT_MAPPER.writeValueAsString(entity.data()));
                    statement.setTimestamp(5, Timestamp.from(metadata.createdAt()));
                    statement.setTimestamp(6, Timestamp.from(metadata.updatedAt()));
                    statement.setLong(7, metadata.version());
                    statement.setBoolean(8, false);
                    statement.executeUpdate();
                }
            }
            return new Entity(entity.id(), entity.collection(), entity.data(), metadata);
        }
    }

    private Optional<Entity> findByIdSync(String tenantId, String entityId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_SQL)) {
            statement.setString(1, tenantId);
            statement.setString(2, entityId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next() || resultSet.getBoolean("deleted")) {
                    return Optional.empty();
                }
                return Optional.of(mapEntity(entityId, resultSet));
            }
        }
    }

    private void deleteSync(String tenantId, String entityId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(DELETE_SQL)) {
            statement.setTimestamp(1, Timestamp.from(Instant.now()));
            statement.setString(2, tenantId);
            statement.setString(3, entityId);
            statement.executeUpdate();
        }
    }

    private Optional<EntityMetadata> readMetadata(Connection connection, String tenantId, String entityId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_SQL)) {
            statement.setString(1, tenantId);
            statement.setString(2, entityId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(new EntityMetadata(
                    resultSet.getTimestamp("created_at").toInstant(),
                    resultSet.getTimestamp("updated_at").toInstant(),
                    Optional.empty(),
                    Optional.empty(),
                    resultSet.getLong("version")
                ));
            }
        }
    }

    private Entity mapEntity(String entityId, ResultSet resultSet) throws Exception {
        return new Entity(
            EntityId.of(entityId),
            resultSet.getString("collection_name"),
            OBJECT_MAPPER.readValue(resultSet.getString("data_json"), MAP_TYPE),
            new EntityMetadata(
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant(),
                Optional.empty(),
                Optional.empty(),
                resultSet.getLong("version")
            )
        );
    }

    private DataSource createDataSource(Path path) {
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to prepare sovereign entity store directory", exception);
        }
        JdbcDataSource jdbcDataSource = new JdbcDataSource();
        jdbcDataSource.setURL("jdbc:h2:file:" + path.toAbsolutePath() + ";DB_CLOSE_ON_EXIT=FALSE");
        jdbcDataSource.setUser("sa");
        jdbcDataSource.setPassword("");
        return jdbcDataSource;
    }

    private void initializeSchema() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS dc_entities (
                    tenant_id VARCHAR(255) NOT NULL,
                    entity_id VARCHAR(255) NOT NULL,
                    collection_name VARCHAR(255) NOT NULL,
                    data_json CLOB NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL,
                    version BIGINT NOT NULL,
                    deleted BOOLEAN NOT NULL DEFAULT FALSE,
                    PRIMARY KEY (tenant_id, entity_id)
                )
                """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_dc_entities_collection ON dc_entities(tenant_id, collection_name, deleted)");
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to initialize sovereign entity store schema", exception);
        }
    }

    private static void appendPlaceholders(StringBuilder sql, int count) {
        for (int index = 0; index < count; index++) {
            if (index > 0) {
                sql.append(',');
            }
            sql.append('?');
        }
    }
}
