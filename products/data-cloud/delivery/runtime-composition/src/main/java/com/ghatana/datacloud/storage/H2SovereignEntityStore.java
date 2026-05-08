package com.ghatana.datacloud.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.spi.BatchError;
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

    // DC-P0-001: All single-entity SQL now includes collection_name for collection-scoped identity.
    private static final String SELECT_BY_REF_SQL = """
        SELECT data_json, created_at, updated_at, version, deleted
          FROM dc_entities
         WHERE tenant_id = ? AND collection_name = ? AND entity_id = ?
        """;

    private static final String INSERT_SQL = """
        INSERT INTO dc_entities (
            tenant_id, entity_id, collection_name, data_json, created_at, updated_at, version, deleted
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """;

    private static final String UPDATE_BY_REF_SQL = """
        UPDATE dc_entities
           SET data_json = ?, updated_at = ?, version = ?, deleted = ?
         WHERE tenant_id = ? AND collection_name = ? AND entity_id = ?
        """;

    // DC-P0-001: Delete is collection-scoped.
    private static final String DELETE_BY_REF_SQL = """
        UPDATE dc_entities
           SET deleted = TRUE, updated_at = ?, version = version + 1
         WHERE tenant_id = ? AND collection_name = ? AND entity_id = ? AND deleted = FALSE
        """;

    // DC-P0-002: QUERY_SQL and COUNT_SQL are now built dynamically in buildQuerySql/buildCountSql.
    // These base templates are extended with filter/sort clauses at query time.

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

    // ==================== Deprecated single-ID methods (DC-P0-001) ====================

    @Override
    @Deprecated
    public Promise<Optional<Entity>> findById(TenantContext tenant, EntityId id) {
        // Without collection context, collection-scoped lookup is impossible. Return empty.
        return Promise.of(Optional.empty());
    }

    @Override
    @Deprecated
    public Promise<List<Entity>> findByIds(TenantContext tenant, List<EntityId> ids) {
        // Without collection context, collection-scoped lookup is impossible. Return empty.
        return Promise.of(List.of());
    }

    // ==================== Collection-scoped methods (DC-P0-001) ====================

    @Override
    public Promise<Optional<Entity>> findByRef(TenantContext tenant, EntityRef ref) {
        return Promise.ofBlocking(executor, () -> findByRefSync(tenant.tenantId(), ref.collection(), ref.entityId().value()));
    }

    @Override
    public Promise<List<Entity>> findByRefs(TenantContext tenant, List<EntityRef> refs) {
        return Promise.ofBlocking(executor, () -> {
            List<Entity> entities = new ArrayList<>();
            for (EntityRef ref : refs) {
                findByRefSync(tenant.tenantId(), ref.collection(), ref.entityId().value()).ifPresent(entities::add);
            }
            return entities;
        });
    }

    // ==================== Query with filters/sorts (DC-P0-002) ====================

    @Override
    public Promise<QueryResult> query(TenantContext tenant, QuerySpec query) {
        return Promise.ofBlocking(executor, () -> {
            DynamicQuery dq = buildDynamicQuery(tenant.tenantId(), query);

            long totalCount;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement countStatement = connection.prepareStatement(dq.countSql())) {
                bindQueryParametersFull(countStatement, tenant.tenantId(), dq, query, false);
                try (ResultSet resultSet = countStatement.executeQuery()) {
                    resultSet.next();
                    totalCount = resultSet.getLong(1);
                }
            }

            List<Entity> entities = new ArrayList<>();
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement queryStatement = connection.prepareStatement(dq.selectSql())) {
                bindQueryParametersFull(queryStatement, tenant.tenantId(), dq, query, true);
                try (ResultSet resultSet = queryStatement.executeQuery()) {
                    while (resultSet.next()) {
                        entities.add(mapEntity(resultSet.getString("entity_id"), resultSet));
                    }
                }
            }
            return QueryResult.of(entities, totalCount);
        });
    }

    // ==================== Delete (DC-P0-001, DC-BE-001) ====================

    @Override
    @Deprecated
    public Promise<Void> delete(TenantContext tenant, EntityId id) {
        // Without collection context, cannot safely delete. No-op.
        return Promise.of(null);
    }

    @Override
    public Promise<Void> deleteByRef(TenantContext tenant, EntityRef ref) {
        return Promise.ofBlocking(executor, () -> {
            deleteByRefSync(tenant.tenantId(), ref.collection(), ref.entityId().value());
            return null;
        });
    }

    /** @deprecated Collection context required. Delegates to deleteByRefs if EntityRef available. */
    @Override
    @Deprecated
    public Promise<BatchResult<String>> deleteBatch(TenantContext tenant, List<EntityId> ids) {
        // Without collection context this is a no-op to avoid silent cross-collection deletes.
        return Promise.of(new BatchResult<String>(ids.size(), 0, ids.size(),
            java.util.stream.IntStream.range(0, ids.size())
                .mapToObj(i -> new BatchError<String>(i, ids.get(i).value(), "COLLECTION_REQUIRED", "collection context required"))
                .toList()));
    }

    @Override
    public Promise<BatchResult<String>> deleteByRefs(TenantContext tenant, List<EntityRef> refs) {
        return Promise.ofBlocking(executor, () -> {
            int successCount = 0;
            List<BatchError<String>> errors = new ArrayList<>();
            try (Connection connection = dataSource.getConnection()) {
                boolean originalAutoCommit = connection.getAutoCommit();
                connection.setAutoCommit(false);
                try {
                    for (int offset = 0; offset < refs.size(); offset += DELETE_BATCH_CHUNK_SIZE) {
                        List<EntityRef> chunk = refs.subList(offset, Math.min(offset + DELETE_BATCH_CHUNK_SIZE, refs.size()));
                        // DC-BE-001: use actual affected row count, not chunk.size().
                        successCount += executeDeleteRefChunk(connection, tenant.tenantId(), chunk);
                    }
                    connection.commit();
                } catch (Exception exception) {
                    connection.rollback();
                    throw exception;
                } finally {
                    connection.setAutoCommit(originalAutoCommit);
                }
            }
            int failed = refs.size() - successCount;
            return new BatchResult<>(refs.size(), successCount, failed, errors);
        });
    }

    /**
     * DC-BE-001: returns the actual number of rows affected (not chunk.size()).
     */
    private int executeDeleteRefChunk(Connection connection, String tenantId, List<EntityRef> refs) throws Exception {
        // Group by collection name so we can generate one SQL per collection within the chunk.
        Map<String, List<String>> byCollection = new LinkedHashMap<>();
        for (EntityRef ref : refs) {
            byCollection.computeIfAbsent(ref.collection(), k -> new ArrayList<>()).add(ref.entityId().value());
        }
        int totalAffected = 0;
        Timestamp now = Timestamp.from(Instant.now());
        for (Map.Entry<String, List<String>> entry : byCollection.entrySet()) {
            String collection = entry.getKey();
            List<String> entityIds = entry.getValue();
            StringBuilder sql = new StringBuilder("""
                UPDATE dc_entities
                   SET deleted = TRUE, updated_at = ?, version = version + 1
                 WHERE tenant_id = ? AND collection_name = ? AND deleted = FALSE AND entity_id IN (
                """);
            appendPlaceholders(sql, entityIds.size());
            sql.append(')');
            try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
                statement.setTimestamp(1, now);
                statement.setString(2, tenantId);
                statement.setString(3, collection);
                for (int i = 0; i < entityIds.size(); i++) {
                    statement.setString(i + 4, entityIds.get(i));
                }
                // DC-BE-001: capture actual rows affected
                totalAffected += statement.executeUpdate();
            }
        }
        return totalAffected;
    }

    @Override
    public Promise<Long> count(TenantContext tenant, QuerySpec query) {
        return Promise.ofBlocking(executor, () -> {
            DynamicQuery dq = buildDynamicQuery(tenant.tenantId(), query);
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(dq.countSql())) {
                bindQueryParametersFull(statement, tenant.tenantId(), dq, query, false);
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    return resultSet.getLong(1);
                }
            }
        });
    }

    @Override
    @Deprecated
    public Promise<Boolean> exists(TenantContext tenant, EntityId id) {
        // Without collection context, returns false (DC-P0-001).
        return Promise.of(false);
    }

    @Override
    public Promise<Boolean> existsByRef(TenantContext tenant, EntityRef ref) {
        return findByRef(tenant, ref).map(Optional::isPresent);
    }

    private static final String LIST_COLLECTIONS_SQL = """
        SELECT DISTINCT collection_name
          FROM dc_entities
         WHERE tenant_id = ? AND deleted = FALSE
         ORDER BY collection_name ASC
        """;

    @Override
    public Promise<List<String>> listCollections(TenantContext tenant) {
        return Promise.ofBlocking(executor, () -> {
            List<String> collections = new ArrayList<>();
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(LIST_COLLECTIONS_SQL)) {
                statement.setString(1, tenant.tenantId());
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        collections.add(resultSet.getString("collection_name"));
                    }
                }
            }
            return collections;
        });
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
            // DC-P0-001: readMetadata uses collection-scoped SELECT
            Optional<EntityMetadata> existingMetadata = readMetadataByRef(
                connection, tenant.tenantId(), entity.collection(), entity.id().value());
            EntityMetadata metadata = existingMetadata
                .map(existing -> new EntityMetadata(
                    existing.createdAt(),
                    now,
                    Optional.empty(),
                    Optional.empty(),
                    existing.version() + 1))
                .orElseGet(() -> new EntityMetadata(now, now, Optional.empty(), Optional.empty(), 1));

            if (existingMetadata.isPresent()) {
                // DC-P0-001: UPDATE_BY_REF_SQL includes collection_name in WHERE
                try (PreparedStatement statement = connection.prepareStatement(UPDATE_BY_REF_SQL)) {
                    statement.setString(1, OBJECT_MAPPER.writeValueAsString(entity.data()));
                    statement.setTimestamp(2, Timestamp.from(metadata.updatedAt()));
                    statement.setLong(3, metadata.version());
                    statement.setBoolean(4, false);
                    statement.setString(5, tenant.tenantId());
                    statement.setString(6, entity.collection());
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

    /** DC-P0-001: collection-scoped findById. */
    private Optional<Entity> findByRefSync(String tenantId, String collection, String entityId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_REF_SQL)) {
            statement.setString(1, tenantId);
            statement.setString(2, collection);
            statement.setString(3, entityId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next() || resultSet.getBoolean("deleted")) {
                    return Optional.empty();
                }
                return Optional.of(mapEntityWithCollection(entityId, collection, resultSet));
            }
        }
    }

    /** DC-P0-001: collection-scoped delete. */
    private void deleteByRefSync(String tenantId, String collection, String entityId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(DELETE_BY_REF_SQL)) {
            statement.setTimestamp(1, Timestamp.from(Instant.now()));
            statement.setString(2, tenantId);
            statement.setString(3, collection);
            statement.setString(4, entityId);
            statement.executeUpdate();
        }
    }

    /** DC-P0-001: readMetadata is now collection-scoped. */
    private Optional<EntityMetadata> readMetadataByRef(
            Connection connection, String tenantId, String collection, String entityId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_BY_REF_SQL)) {
            statement.setString(1, tenantId);
            statement.setString(2, collection);
            statement.setString(3, entityId);
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

    /**
     * Variant of mapEntity used by findByRefSync where collection_name is passed explicitly
     * (the SELECT_BY_REF_SQL does not select collection_name since it's already in the WHERE clause).
     */
    private Entity mapEntityWithCollection(String entityId, String collection, ResultSet resultSet) throws Exception {
        return new Entity(
            EntityId.of(entityId),
            collection,
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

    // ==================== DC-P0-002: Dynamic query builder ====================

    /**
     * Holds the dynamically-built SQL strings and their ordered positional parameters.
     *
     * @param countSql       SQL for COUNT query
     * @param selectSql      SQL for data query
     * @param baseParams     positional parameters shared by both count and select queries
     *                       (everything before LIMIT/OFFSET)
     * @param filterParamValues  values for the dynamic filter clauses in order
     */
    private record DynamicQuery(
        String countSql,
        String selectSql,
        List<Object> filterParamValues
    ) { }

    /**
     * DC-P0-002: Builds a DynamicQuery for the given {@link QuerySpec}, applying
     * {@code filters}, {@code sorts}, {@code search}, and {@code projections}.
     *
     * <p>Data fields are stored as JSON in the {@code data_json} CLOB column.
     * H2's {@code JSON_VALUE(data_json, '$.fieldName')} is used to extract and compare them.
     */
    private DynamicQuery buildDynamicQuery(String tenantId, QuerySpec query) {
        List<Object> filterValues = new ArrayList<>();
        StringBuilder where = new StringBuilder();
        where.append(" WHERE tenant_id = ? AND collection_name = ? AND deleted = FALSE");
        // tenantId and collection are bound at parameter positions 1 and 2 always.

        // Apply full-text search (DC-P0-002)
        if (query.search() != null && !query.search().isBlank()) {
            where.append(" AND LOWER(data_json) LIKE ?");
            filterValues.add("%" + query.search().toLowerCase() + "%");
        }

        // Apply filters (DC-P0-002)
        for (Filter filter : query.filters()) {
            String column = dataColumn(filter.field());
            switch (filter.operator()) {
                case EQ -> {
                    where.append(" AND ").append(column).append(" = ?");
                    filterValues.add(filterValue(filter));
                }
                case NE -> {
                    where.append(" AND ").append(column).append(" <> ?");
                    filterValues.add(filterValue(filter));
                }
                case GT -> {
                    where.append(" AND ").append(column).append(" > ?");
                    filterValues.add(filterValue(filter));
                }
                case GTE -> {
                    where.append(" AND ").append(column).append(" >= ?");
                    filterValues.add(filterValue(filter));
                }
                case LT -> {
                    where.append(" AND ").append(column).append(" < ?");
                    filterValues.add(filterValue(filter));
                }
                case LTE -> {
                    where.append(" AND ").append(column).append(" <= ?");
                    filterValues.add(filterValue(filter));
                }
                case LIKE -> {
                    where.append(" AND ").append(column).append(" LIKE ?");
                    filterValues.add("%" + filterValue(filter) + "%");
                }
                case IN -> {
                    if (filter.value() instanceof List<?> values && !values.isEmpty()) {
                        where.append(" AND ").append(column).append(" IN (");
                        appendPlaceholders(where, values.size());
                        where.append(')');
                        filterValues.addAll(values);
                    }
                }
                case NOT_IN -> {
                    if (filter.value() instanceof List<?> values && !values.isEmpty()) {
                        where.append(" AND ").append(column).append(" NOT IN (");
                        appendPlaceholders(where, values.size());
                        where.append(')');
                        filterValues.addAll(values);
                    }
                }
                case IS_NULL -> where.append(" AND ").append(column).append(" IS NULL");
                case IS_NOT_NULL -> where.append(" AND ").append(column).append(" IS NOT NULL");
            }
        }

        // Apply sorts (DC-P0-002)
        StringBuilder orderBy = new StringBuilder();
        if (!query.sorts().isEmpty()) {
            orderBy.append(" ORDER BY ");
            for (int i = 0; i < query.sorts().size(); i++) {
                if (i > 0) orderBy.append(", ");
                Sort sort = query.sorts().get(i);
                orderBy.append(dataColumn(sort.field()));
                orderBy.append(sort.direction() == Direction.DESC ? " DESC" : " ASC");
            }
            // Ensure deterministic sort tie-breaking
            orderBy.append(", entity_id ASC");
        } else {
            orderBy.append(" ORDER BY updated_at DESC, entity_id ASC");
        }

        String fromClause = "FROM dc_entities";
        String countSql = "SELECT COUNT(*) " + fromClause + where;
        String selectSql = "SELECT entity_id, collection_name, data_json, created_at, updated_at, version "
            + fromClause + where + orderBy + " LIMIT ? OFFSET ?";

        return new DynamicQuery(countSql, selectSql, filterValues);
    }

    /**
     * Bind all parameters for a dynamic query statement.
     * Order: (1) tenantId, (2) collectionName, (3+) filter values, [LIMIT, OFFSET for select]
     */
    private void bindQueryParametersFull(
            PreparedStatement statement, String tenantId, DynamicQuery dq, QuerySpec query, boolean isSelect) throws Exception {
        int idx = 1;
        statement.setString(idx++, tenantId);
        statement.setString(idx++, query.collection());
        for (Object value : dq.filterParamValues()) {
            statement.setObject(idx++, value);
        }
        if (isSelect) {
            statement.setInt(idx++, query.limit());
            statement.setInt(idx, query.offset());
        }
    }

    /** Returns a SQL column expression for the given field name. */
    private static String dataColumn(String field) {
        return switch (field) {
            case "id" -> "entity_id";
            case "collection" -> "collection_name";
            case "version" -> "version";
            case "createdAt", "created_at" -> "created_at";
            case "updatedAt", "updated_at" -> "updated_at";
            // DC-P0-002: data fields are extracted from the JSON column via H2 JSON_VALUE
            default -> "JSON_VALUE(data_json, '$." + field.replace("'", "") + "')";
        };
    }

    private static Object filterValue(Filter filter) {
        return filter.value();
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
            // DC-P0-001: PRIMARY KEY now includes collection_name to prevent cross-collection collision.
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
                    PRIMARY KEY (tenant_id, collection_name, entity_id)
                )
                """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_dc_entities_tenant_collection ON dc_entities(tenant_id, collection_name, deleted)");
            // DC-PERF-001: cover ORDER BY updated_at DESC, entity_id ASC queries
            statement.execute("CREATE INDEX IF NOT EXISTS idx_dc_entities_tenant_updated ON dc_entities(tenant_id, collection_name, updated_at DESC)");
            // DC-PERF-001: cover listCollections GROUP BY tenant_id, collection_name queries
            statement.execute("CREATE INDEX IF NOT EXISTS idx_dc_entities_tenant_collections ON dc_entities(tenant_id, collection_name) WHERE deleted = FALSE");
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
