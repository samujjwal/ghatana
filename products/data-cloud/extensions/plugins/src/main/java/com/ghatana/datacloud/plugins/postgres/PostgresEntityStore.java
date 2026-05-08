package com.ghatana.datacloud.plugins.postgres;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.datacloud.spi.BatchError;
import com.ghatana.datacloud.spi.BatchResult;
import com.ghatana.datacloud.spi.EntityStore;
import com.ghatana.datacloud.spi.TenantContext;
import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @doc.type class
 * @doc.purpose PostgreSQL-backed EntityStore SPI implementation with tenant-scoped queries.
 * @doc.layer product
 * @doc.pattern Adapter, Plugin
 */
public class PostgresEntityStore implements EntityStore, AutoCloseable {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final String ENTITY_SELECT_COLUMNS =
        "id, collection_name, data, created_at, created_by, updated_at, updated_by, version";
    private static final int DELETE_BATCH_CHUNK_SIZE = 500;

    private final PostgresEntityStoreConfig config;
    private final ExecutorService blockingExecutor;
    private volatile HikariDataSource dataSource;

    public PostgresEntityStore() {
        this(PostgresEntityStoreConfig.fromEnvironmentIfPresent().orElse(null), Executors.newVirtualThreadPerTaskExecutor());
    }

    public PostgresEntityStore(PostgresEntityStoreConfig config) {
        this(config, Executors.newVirtualThreadPerTaskExecutor());
    }

    PostgresEntityStore(PostgresEntityStoreConfig config, ExecutorService blockingExecutor) {
        this.config = config;
        this.blockingExecutor = Objects.requireNonNull(blockingExecutor, "blockingExecutor required");
    }

    @Override
    public Promise<Entity> save(TenantContext tenant, Entity entity) {
        Objects.requireNonNull(tenant, "tenant required");
        Objects.requireNonNull(entity, "entity required");

        return Promise.ofBlocking(blockingExecutor, () -> {
            try (Connection connection = openTenantConnection(tenant)) {
                if (entityExists(connection, tenant.tenantId(), entity.id().value())) {
                    return updateEntity(connection, tenant, entity);
                }
                return insertEntity(connection, tenant, entity);
            }
        });
    }

    @Override
    public Promise<BatchResult<String>> saveBatch(TenantContext tenant, List<Entity> entities) {
        Objects.requireNonNull(tenant, "tenant required");
        Objects.requireNonNull(entities, "entities required");

        return Promise.ofBlocking(blockingExecutor, () -> {
            List<BatchError<String>> errors = new ArrayList<>();
            int successCount = 0;
            for (int index = 0; index < entities.size(); index++) {
                Entity entity = entities.get(index);
                try {
                    try (Connection connection = openTenantConnection(tenant)) {
                        if (entityExists(connection, tenant.tenantId(), entity.id().value())) {
                            updateEntity(connection, tenant, entity);
                        } else {
                            insertEntity(connection, tenant, entity);
                        }
                    }
                    successCount++;
                } catch (Exception exception) {
                    errors.add(new BatchError<>(index, entity.id().value(), "SAVE_FAILED", exception.getMessage()));
                }
            }
            return new BatchResult<>(entities.size(), successCount, errors.size(), errors);
        });
    }

    @Override
    public Promise<Optional<Entity>> findById(TenantContext tenant, EntityId id) {
        Objects.requireNonNull(tenant, "tenant required");
        Objects.requireNonNull(id, "id required");

        return Promise.ofBlocking(blockingExecutor, () -> {
            String sql = "SELECT " + ENTITY_SELECT_COLUMNS + " FROM entities WHERE tenant_id = ? AND id = ?::uuid AND active = TRUE";
            try (Connection connection = openTenantConnection(tenant);
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, tenant.tenantId());
                statement.setObject(2, UUID.fromString(id.value()));
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(readEntity(resultSet));
                }
            }
        });
    }

    @Override
    public Promise<Optional<Entity>> findByRef(TenantContext tenant, EntityRef ref) {
        Objects.requireNonNull(tenant, "tenant required");
        Objects.requireNonNull(ref, "ref required");

        return Promise.ofBlocking(blockingExecutor, () -> {
            String sql = "SELECT " + ENTITY_SELECT_COLUMNS
                + " FROM entities WHERE tenant_id = ? AND collection_name = ? AND id = ?::uuid AND active = TRUE";
            try (Connection connection = openTenantConnection(tenant);
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, tenant.tenantId());
                statement.setString(2, ref.collection());
                statement.setObject(3, UUID.fromString(ref.entityId().value()));
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(readEntity(resultSet));
                }
            }
        });
    }

    @Override
    public Promise<List<Entity>> findByIds(TenantContext tenant, List<EntityId> ids) {
        Objects.requireNonNull(tenant, "tenant required");
        Objects.requireNonNull(ids, "ids required");

        return Promise.ofBlocking(blockingExecutor, () -> {
            if (ids.isEmpty()) {
                return List.of();
            }

            StringBuilder sql = new StringBuilder("SELECT ").append(ENTITY_SELECT_COLUMNS)
                .append(" FROM entities WHERE tenant_id = ? AND active = TRUE AND id IN (");
            appendPlaceholders(sql, ids.size());
            sql.append(')');

            try (Connection connection = openTenantConnection(tenant);
                 PreparedStatement statement = connection.prepareStatement(sql.toString())) {
                statement.setString(1, tenant.tenantId());
                for (int index = 0; index < ids.size(); index++) {
                    statement.setObject(index + 2, UUID.fromString(ids.get(index).value()));
                }
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<Entity> entities = new ArrayList<>();
                    while (resultSet.next()) {
                        entities.add(readEntity(resultSet));
                    }
                    return List.copyOf(entities);
                }
            }
        });
    }

    @Override
    public Promise<QueryResult> query(TenantContext tenant, QuerySpec query) {
        Objects.requireNonNull(tenant, "tenant required");
        Objects.requireNonNull(query, "query required");

        return Promise.ofBlocking(blockingExecutor, () -> {
            SqlWithParameters whereClause = buildWhereClause(query);
            StringBuilder sql = new StringBuilder("SELECT ").append(ENTITY_SELECT_COLUMNS)
                .append(" FROM entities WHERE tenant_id = ? AND collection_name = ? AND active = TRUE")
                .append(whereClause.sql())
                .append(buildOrderByClause(query.sorts()))
                .append(" LIMIT ? OFFSET ?");

            try (Connection connection = openTenantConnection(tenant);
                 PreparedStatement statement = connection.prepareStatement(sql.toString())) {
                int nextIndex = bindBaseQueryParameters(statement, tenant, query.collection(), whereClause.parameters());
                statement.setInt(nextIndex++, query.limit());
                statement.setInt(nextIndex, query.offset());

                List<Entity> entities = new ArrayList<>();
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        entities.add(readEntity(resultSet));
                    }
                }

                long totalCount = executeCount(connection, tenant, query, whereClause);
                boolean hasMore = query.offset() + entities.size() < totalCount;
                return new QueryResult(List.copyOf(entities), totalCount, hasMore);
            }
        });
    }

    @Override
    public Promise<Void> delete(TenantContext tenant, EntityId id) {
        Objects.requireNonNull(tenant, "tenant required");
        Objects.requireNonNull(id, "id required");

        return Promise.ofBlocking(blockingExecutor, () -> {
            String sql = "UPDATE entities SET active = FALSE, updated_at = ?, version = version + 1 WHERE tenant_id = ? AND id = ?::uuid AND active = TRUE";
            try (Connection connection = openTenantConnection(tenant);
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setTimestamp(1, Timestamp.from(Instant.now()));
                statement.setString(2, tenant.tenantId());
                statement.setObject(3, UUID.fromString(id.value()));
                statement.executeUpdate();
                return null;
            }
        });
    }

    @Override
    public Promise<Void> deleteByRef(TenantContext tenant, EntityRef ref) {
        Objects.requireNonNull(tenant, "tenant required");
        Objects.requireNonNull(ref, "ref required");

        return Promise.ofBlocking(blockingExecutor, () -> {
            String sql = "UPDATE entities SET active = FALSE, updated_at = ?, version = version + 1 "
                + "WHERE tenant_id = ? AND collection_name = ? AND id = ?::uuid AND active = TRUE";
            try (Connection connection = openTenantConnection(tenant);
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setTimestamp(1, Timestamp.from(Instant.now()));
                statement.setString(2, tenant.tenantId());
                statement.setString(3, ref.collection());
                statement.setObject(4, UUID.fromString(ref.entityId().value()));
                statement.executeUpdate();
                return null;
            }
        });
    }

    @Override
    public Promise<BatchResult<String>> deleteBatch(TenantContext tenant, List<EntityId> ids) {
        Objects.requireNonNull(tenant, "tenant required");
        Objects.requireNonNull(ids, "ids required");

        return Promise.ofBlocking(blockingExecutor, () -> {
            List<BatchError<String>> errors = new ArrayList<>();
            int successCount = 0;
            try (Connection connection = openTenantConnection(tenant);
                 PreparedStatement statement = connection.prepareStatement(
                     "UPDATE entities SET active = FALSE, updated_at = ?, version = version + 1 WHERE tenant_id = ? AND id = ?::uuid AND active = TRUE"
                 )) {
                boolean originalAutoCommit = connection.getAutoCommit();
                connection.setAutoCommit(false);
                try {
                    List<Integer> pendingIndices = new ArrayList<>();
                    List<EntityId> pendingIds = new ArrayList<>();
                    Timestamp now = Timestamp.from(Instant.now());

                    for (int index = 0; index < ids.size(); index++) {
                        EntityId id = ids.get(index);
                        try {
                            statement.setTimestamp(1, now);
                            statement.setString(2, tenant.tenantId());
                            statement.setObject(3, UUID.fromString(id.value()));
                            statement.addBatch();
                            pendingIndices.add(index);
                            pendingIds.add(id);
                        } catch (Exception exception) {
                            errors.add(new BatchError<>(index, id.value(), "DELETE_FAILED", exception.getMessage()));
                        }

                        if (pendingIds.size() == DELETE_BATCH_CHUNK_SIZE) {
                            successCount += executeDeleteChunk(statement, pendingIndices, pendingIds, errors);
                            pendingIndices.clear();
                            pendingIds.clear();
                        }
                    }

                    if (!pendingIds.isEmpty()) {
                        successCount += executeDeleteChunk(statement, pendingIndices, pendingIds, errors);
                    }

                    connection.commit();
                } catch (SQLException exception) {
                    connection.rollback();
                    throw exception;
                } finally {
                    connection.setAutoCommit(originalAutoCommit);
                }
            }
            return new BatchResult<>(ids.size(), successCount, errors.size(), errors);
        });
    }

    private int executeDeleteChunk(
        PreparedStatement statement,
        List<Integer> pendingIndices,
        List<EntityId> pendingIds,
        List<BatchError<String>> errors
    ) throws SQLException {
        int[] results = statement.executeBatch();
        statement.clearBatch();

        int successCount = 0;
        for (int resultIndex = 0; resultIndex < results.length; resultIndex++) {
            if (results[resultIndex] == Statement.EXECUTE_FAILED) {
                errors.add(new BatchError<>(
                    pendingIndices.get(resultIndex),
                    pendingIds.get(resultIndex).value(),
                    "DELETE_FAILED",
                    "Batch delete execution failed"
                ));
                continue;
            }
            successCount++;
        }
        return successCount;
    }

    @Override
    public Promise<Long> count(TenantContext tenant, QuerySpec query) {
        Objects.requireNonNull(tenant, "tenant required");
        Objects.requireNonNull(query, "query required");

        return Promise.ofBlocking(blockingExecutor, () -> {
            SqlWithParameters whereClause = buildWhereClause(query);
            try (Connection connection = openTenantConnection(tenant)) {
                return executeCount(connection, tenant, query, whereClause);
            }
        });
    }

    @Override
    public Promise<Boolean> exists(TenantContext tenant, EntityId id) {
        Objects.requireNonNull(tenant, "tenant required");
        Objects.requireNonNull(id, "id required");

        return Promise.ofBlocking(blockingExecutor, () -> {
            String sql = "SELECT 1 FROM entities WHERE tenant_id = ? AND id = ?::uuid AND active = TRUE";
            try (Connection connection = openTenantConnection(tenant);
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, tenant.tenantId());
                statement.setObject(2, UUID.fromString(id.value()));
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next();
                }
            }
        });
    }

    @Override
    public Promise<Boolean> existsByRef(TenantContext tenant, EntityRef ref) {
        Objects.requireNonNull(tenant, "tenant required");
        Objects.requireNonNull(ref, "ref required");

        return Promise.ofBlocking(blockingExecutor, () -> {
            String sql = "SELECT 1 FROM entities WHERE tenant_id = ? AND collection_name = ? AND id = ?::uuid AND active = TRUE";
            try (Connection connection = openTenantConnection(tenant);
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, tenant.tenantId());
                statement.setString(2, ref.collection());
                statement.setObject(3, UUID.fromString(ref.entityId().value()));
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next();
                }
            }
        });
    }

    @Override
    public Promise<List<String>> listCollections(TenantContext tenant) {
        Objects.requireNonNull(tenant, "tenant required");

        return Promise.ofBlocking(blockingExecutor, () -> {
            String sql = "SELECT DISTINCT collection_name FROM entities WHERE tenant_id = ? AND active = TRUE ORDER BY collection_name ASC";
            List<String> collections = new ArrayList<>();
            try (Connection connection = openTenantConnection(tenant);
                 PreparedStatement statement = connection.prepareStatement(sql)) {
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

    @Override
    public void close() {
        HikariDataSource current = dataSource;
        if (current != null) {
            current.close();
        }
        blockingExecutor.close();
    }

    private Entity insertEntity(Connection connection, TenantContext tenant, Entity entity) throws Exception {
        EntityMetadata metadata = normalizeMetadata(entity.metadata(), false);
        String sql = "INSERT INTO entities (id, tenant_id, collection_name, record_type, data, metadata, created_at, created_by, version, active, updated_at, updated_by) "
            + "VALUES (?::uuid, ?, ?, 'ENTITY', ?::jsonb, ?::jsonb, ?, ?, ?, TRUE, ?, ?)";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, UUID.fromString(entity.id().value()));
            statement.setString(2, tenant.tenantId());
            statement.setString(3, entity.collection());
            statement.setString(4, writeJson(entity.data()));
            statement.setString(5, writeJson(metadataDocument(metadata)));
            statement.setTimestamp(6, Timestamp.from(metadata.createdAt()));
            statement.setString(7, metadata.createdBy().orElse(null));
            statement.setLong(8, metadata.version());
            statement.setTimestamp(9, Timestamp.from(metadata.updatedAt()));
            statement.setString(10, metadata.updatedBy().orElse(null));
            statement.executeUpdate();
            return new Entity(entity.id(), entity.collection(), entity.data(), metadata);
        }
    }

    private Entity updateEntity(Connection connection, TenantContext tenant, Entity entity) throws Exception {
        EntityMetadata metadata = normalizeMetadata(entity.metadata(), true);
        String sql = "UPDATE entities SET collection_name = ?, data = ?::jsonb, metadata = ?::jsonb, updated_at = ?, updated_by = ?, version = ?, active = TRUE "
            + "WHERE tenant_id = ? AND id = ?::uuid";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, entity.collection());
            statement.setString(2, writeJson(entity.data()));
            statement.setString(3, writeJson(metadataDocument(metadata)));
            statement.setTimestamp(4, Timestamp.from(metadata.updatedAt()));
            statement.setString(5, metadata.updatedBy().orElse(null));
            statement.setLong(6, metadata.version());
            statement.setString(7, tenant.tenantId());
            statement.setObject(8, UUID.fromString(entity.id().value()));
            statement.executeUpdate();
            return new Entity(entity.id(), entity.collection(), entity.data(), metadata);
        }
    }

    private long executeCount(Connection connection, TenantContext tenant, QuerySpec query, SqlWithParameters whereClause) throws SQLException {
        String sql = "SELECT COUNT(*) FROM entities WHERE tenant_id = ? AND collection_name = ? AND active = TRUE" + whereClause.sql();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindBaseQueryParameters(statement, tenant, query.collection(), whereClause.parameters());
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }

    private int bindBaseQueryParameters(
        PreparedStatement statement,
        TenantContext tenant,
        String collection,
        List<SqlParameter> parameters
    ) throws SQLException {
        int nextIndex = 1;
        statement.setString(nextIndex++, tenant.tenantId());
        statement.setString(nextIndex++, collection);
        for (SqlParameter parameter : parameters) {
            nextIndex = parameter.bind(statement, nextIndex);
        }
        return nextIndex;
    }

    private SqlWithParameters buildWhereClause(QuerySpec query) {
        StringBuilder sql = new StringBuilder();
        List<SqlParameter> parameters = new ArrayList<>();

        for (Filter filter : query.filters()) {
            sql.append(" AND ");
            if (isColumnField(filter.field())) {
                appendColumnFilter(sql, parameters, filter);
            } else {
                appendJsonFilter(sql, parameters, filter);
            }
        }

        return new SqlWithParameters(sql.toString(), List.copyOf(parameters));
    }

    private String buildOrderByClause(List<Sort> sorts) {
        if (sorts.isEmpty()) {
            return " ORDER BY created_at DESC";
        }

        List<String> clauses = new ArrayList<>();
        for (Sort sort : sorts) {
            String direction = sort.direction() == Direction.DESC ? "DESC" : "ASC";
            if (isColumnField(sort.field())) {
                clauses.add(columnName(sort.field()) + " " + direction);
            } else {
                clauses.add("data ->> '" + escapeSqlLiteral(sort.field()) + "' " + direction);
            }
        }
        return " ORDER BY " + String.join(", ", clauses);
    }

    private void appendColumnFilter(StringBuilder sql, List<SqlParameter> parameters, Filter filter) {
        String column = columnName(filter.field());
        switch (filter.operator()) {
            case EQ -> {
                sql.append(column).append(" = ?");
                parameters.add(SqlParameter.of(normalizeValue(filter.value())));
            }
            case NE -> {
                sql.append(column).append(" <> ?");
                parameters.add(SqlParameter.of(normalizeValue(filter.value())));
            }
            case GT, GTE, LT, LTE -> {
                sql.append(column).append(' ').append(operatorToken(filter.operator())).append(" ?");
                parameters.add(SqlParameter.of(normalizeValue(filter.value())));
            }
            case LIKE -> {
                sql.append(column).append(" LIKE ?");
                parameters.add(SqlParameter.of(String.valueOf(filter.value())));
            }
            case IN, NOT_IN -> appendInFilter(sql, parameters, column, filter.operator(), filter.value());
            case IS_NULL -> sql.append(column).append(" IS NULL");
            case IS_NOT_NULL -> sql.append(column).append(" IS NOT NULL");
        }
    }

    private void appendJsonFilter(StringBuilder sql, List<SqlParameter> parameters, Filter filter) {
        String fieldExpression = "data ->> ?";
        switch (filter.operator()) {
            case EQ -> {
                sql.append(fieldExpression).append(" = ?");
                parameters.add(SqlParameter.of(filter.field()));
                parameters.add(SqlParameter.of(String.valueOf(filter.value())));
            }
            case NE -> {
                sql.append(fieldExpression).append(" <> ?");
                parameters.add(SqlParameter.of(filter.field()));
                parameters.add(SqlParameter.of(String.valueOf(filter.value())));
            }
            case GT, GTE, LT, LTE -> {
                if (filter.value() instanceof Number number) {
                    sql.append("CAST(NULLIF(").append(fieldExpression).append(", '') AS DOUBLE PRECISION) ")
                        .append(operatorToken(filter.operator())).append(" ?");
                    parameters.add(SqlParameter.of(filter.field()));
                    parameters.add(SqlParameter.of(number.doubleValue()));
                } else {
                    sql.append(fieldExpression).append(' ').append(operatorToken(filter.operator())).append(" ?");
                    parameters.add(SqlParameter.of(filter.field()));
                    parameters.add(SqlParameter.of(String.valueOf(filter.value())));
                }
            }
            case LIKE -> {
                sql.append(fieldExpression).append(" LIKE ?");
                parameters.add(SqlParameter.of(filter.field()));
                parameters.add(SqlParameter.of(String.valueOf(filter.value())));
            }
            case IN, NOT_IN -> appendJsonInFilter(sql, parameters, filter.field(), filter.operator(), filter.value());
            case IS_NULL -> {
                sql.append(fieldExpression).append(" IS NULL");
                parameters.add(SqlParameter.of(filter.field()));
            }
            case IS_NOT_NULL -> {
                sql.append(fieldExpression).append(" IS NOT NULL");
                parameters.add(SqlParameter.of(filter.field()));
            }
        }
    }

    private void appendInFilter(StringBuilder sql, List<SqlParameter> parameters, String column, Operator operator, Object value) {
        List<?> values = castList(value);
        sql.append(column).append(operator == Operator.NOT_IN ? " NOT IN (" : " IN (");
        appendPlaceholders(sql, values.size());
        sql.append(')');
        for (Object item : values) {
            parameters.add(SqlParameter.of(normalizeValue(item)));
        }
    }

    private void appendJsonInFilter(StringBuilder sql, List<SqlParameter> parameters, String field, Operator operator, Object value) {
        List<?> values = castList(value);
        sql.append("data ->> ?").append(operator == Operator.NOT_IN ? " NOT IN (" : " IN (");
        parameters.add(SqlParameter.of(field));
        appendPlaceholders(sql, values.size());
        sql.append(')');
        for (Object item : values) {
            parameters.add(SqlParameter.of(String.valueOf(item)));
        }
    }

    private Entity readEntity(ResultSet resultSet) throws Exception {
        Instant createdAt = Optional.ofNullable(resultSet.getTimestamp("created_at"))
            .map(Timestamp::toInstant)
            .orElse(Instant.now());
        Instant updatedAt = Optional.ofNullable(resultSet.getTimestamp("updated_at"))
            .map(Timestamp::toInstant)
            .orElse(createdAt);
        EntityMetadata metadata = new EntityMetadata(
            createdAt,
            updatedAt,
            Optional.ofNullable(resultSet.getString("created_by")),
            Optional.ofNullable(resultSet.getString("updated_by")),
            resultSet.getLong("version")
        );
        return new Entity(
            EntityId.of(resultSet.getObject("id", UUID.class).toString()),
            resultSet.getString("collection_name"),
            readJson(resultSet.getString("data")),
            metadata
        );
    }

    private Connection openTenantConnection(TenantContext tenant) throws SQLException {
        Connection connection = requireDataSource().getConnection();
        try (PreparedStatement statement = connection.prepareStatement("SELECT set_config('app.current_tenant_id', ?, false)")) {
            statement.setString(1, tenant.tenantId());
            statement.execute();
        } catch (SQLException exception) {
            connection.close();
            throw exception;
        }
        return connection;
    }

    private boolean entityExists(Connection connection, String tenantId, String entityId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT 1 FROM entities WHERE tenant_id = ? AND id = ?::uuid"
        )) {
            statement.setString(1, tenantId);
            statement.setObject(2, UUID.fromString(entityId));
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private HikariDataSource requireDataSource() {
        HikariDataSource current = dataSource;
        if (current != null) {
            return current;
        }

        synchronized (this) {
            if (dataSource == null) {
                if (config == null || !config.isConfigured()) {
                    throw new IllegalStateException(
                        "PostgresEntityStore discovered on the classpath, but no database configuration was provided. "
                            + "Set DATACLOUD_DB_URL, DATACLOUD_DB_USER, and DATACLOUD_DB_PASSWORD."
                    );
                }
                dataSource = config.createDataSource();
            }
            return dataSource;
        }
    }

    private EntityMetadata normalizeMetadata(EntityMetadata metadata, boolean update) {
        EntityMetadata base = metadata == null ? EntityMetadata.empty() : metadata;
        Instant createdAt = base.createdAt() != null ? base.createdAt() : Instant.now();
        Instant updatedAt = Instant.now();
        long version = Math.max(1L, base.version());
        if (update) {
            version += 1;
        }
        return new EntityMetadata(createdAt, updatedAt, base.createdBy(), base.updatedBy(), version);
    }

    private Map<String, Object> metadataDocument(EntityMetadata metadata) {
        Map<String, Object> document = new HashMap<>();
        document.put("createdAt", metadata.createdAt().toString());
        document.put("updatedAt", metadata.updatedAt().toString());
        metadata.createdBy().ifPresent(value -> document.put("createdBy", value));
        metadata.updatedBy().ifPresent(value -> document.put("updatedBy", value));
        document.put("version", metadata.version());
        return document;
    }

    private Map<String, Object> readJson(String value) throws Exception {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        return OBJECT_MAPPER.readValue(value, MAP_TYPE);
    }

    private String writeJson(Map<String, Object> value) throws Exception {
        return OBJECT_MAPPER.writeValueAsString(value == null ? Map.of() : value);
    }

    private Object normalizeValue(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        return value;
    }

    private boolean isColumnField(String field) {
        return switch (field) {
            case "id", "createdAt", "updatedAt", "createdBy", "updatedBy", "version", "collection", "active" -> true;
            default -> false;
        };
    }

    private String columnName(String field) {
        return switch (field) {
            case "id" -> "id";
            case "createdAt" -> "created_at";
            case "updatedAt" -> "updated_at";
            case "createdBy" -> "created_by";
            case "updatedBy" -> "updated_by";
            case "version" -> "version";
            case "collection" -> "collection_name";
            case "active" -> "active";
            default -> throw new IllegalArgumentException("Unsupported column field: " + field);
        };
    }

    private String operatorToken(Operator operator) {
        return switch (operator) {
            case EQ -> "=";
            case NE -> "<>";
            case GT -> ">";
            case GTE -> ">=";
            case LT -> "<";
            case LTE -> "<=";
            default -> throw new IllegalArgumentException("Unsupported operator for scalar comparison: " + operator);
        };
    }

    private List<?> castList(Object value) {
        if (value instanceof List<?> list && !list.isEmpty()) {
            return list;
        }
        throw new IllegalArgumentException("IN filters require a non-empty List value");
    }

    private void appendPlaceholders(StringBuilder sql, int count) {
        for (int index = 0; index < count; index++) {
            if (index > 0) {
                sql.append(", ");
            }
            sql.append('?');
        }
    }

    private String escapeSqlLiteral(String value) {
        return value.replace("'", "''");
    }

    private record SqlWithParameters(String sql, List<SqlParameter> parameters) {
    }

    private record SqlParameter(Object value) {
        static SqlParameter of(Object value) {
            return new SqlParameter(value);
        }

        int bind(PreparedStatement statement, int index) throws SQLException {
            if (value == null) {
                statement.setObject(index, null);
            } else if (value instanceof UUID uuid) {
                statement.setObject(index, uuid);
            } else if (value instanceof Number number) {
                statement.setObject(index, number);
            } else if (value instanceof Boolean bool) {
                statement.setBoolean(index, bool);
            } else {
                statement.setString(index, String.valueOf(value));
            }
            return index + 1;
        }
    }
}