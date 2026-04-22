package com.ghatana.yappc.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.services.artifact.ArtifactModelVersion;
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
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type class
 * @doc.purpose JDBC persistence for artifact model version history snapshots with merge provenance
 * @doc.layer infrastructure
 * @doc.pattern Repository
 */
public final class ArtifactModelVersionRepository {

    private static final Logger log = LoggerFactory.getLogger(ArtifactModelVersionRepository.class);
    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() { };
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() { };

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    private final Executor executor;

    public ArtifactModelVersionRepository(DataSource dataSource) {
        this(dataSource, new ObjectMapper(), Runnable::run);
    }

    public ArtifactModelVersionRepository(DataSource dataSource, ObjectMapper objectMapper, Executor executor) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    public Promise<ArtifactModelVersion> saveVersion(ArtifactModelVersion version) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                INSERT INTO artifact_model_versions (
                    version_id, product_id, tenant_id, parent_version_id, commit_message,
                    committed_at, committed_by, model_snapshot_json, node_count, edge_count,
                    merge_provenance_json
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, product_id, version_id) DO UPDATE SET
                    commit_message = EXCLUDED.commit_message,
                    model_snapshot_json = EXCLUDED.model_snapshot_json,
                    node_count = EXCLUDED.node_count,
                    edge_count = EXCLUDED.edge_count,
                    merge_provenance_json = EXCLUDED.merge_provenance_json,
                    committed_at = EXCLUDED.committed_at
                """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, version.versionId());
                statement.setString(2, version.productId());
                statement.setString(3, version.tenantId());
                statement.setString(4, version.parentVersionId());
                statement.setString(5, version.commitMessage());
                statement.setTimestamp(6, Timestamp.from(version.committedAt()));
                statement.setString(7, version.committedBy());
                statement.setString(8, writeJson(version.modelSnapshot()));
                statement.setLong(9, version.nodeCount());
                statement.setLong(10, version.edgeCount());
                statement.setString(11, writeJson(version.mergeProvenance()));
                statement.executeUpdate();
            }
            return version;
        }).whenResult(v -> log.info("Saved artifact model version {} for product {}", v.versionId(), v.productId()));
    }

    public Promise<List<ArtifactModelVersion>> findVersionsByProduct(String productId, String tenantId, int limit) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                SELECT version_id, product_id, tenant_id, parent_version_id, commit_message,
                       committed_at, committed_by, model_snapshot_json, node_count, edge_count,
                       merge_provenance_json
                FROM artifact_model_versions
                WHERE tenant_id = ? AND product_id = ?
                ORDER BY committed_at DESC
                LIMIT ?
                """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, tenantId);
                statement.setString(2, productId);
                statement.setInt(3, limit);
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<ArtifactModelVersion> versions = new ArrayList<>();
                    while (resultSet.next()) {
                        versions.add(mapVersion(resultSet));
                    }
                    return versions;
                }
            }
        });
    }

    public Promise<ArtifactModelVersion> findVersionById(String versionId, String productId, String tenantId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                SELECT version_id, product_id, tenant_id, parent_version_id, commit_message,
                       committed_at, committed_by, model_snapshot_json, node_count, edge_count,
                       merge_provenance_json
                FROM artifact_model_versions
                WHERE tenant_id = ? AND product_id = ? AND version_id = ?
                """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, tenantId);
                statement.setString(2, productId);
                statement.setString(3, versionId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return mapVersion(resultSet);
                    }
                    return null;
                }
            }
        });
    }

    public Promise<String> findLatestVersionId(String productId, String tenantId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                SELECT version_id FROM artifact_model_versions
                WHERE tenant_id = ? AND product_id = ?
                ORDER BY committed_at DESC
                LIMIT 1
                """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, tenantId);
                statement.setString(2, productId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return resultSet.getString("version_id");
                    }
                    return null;
                }
            }
        });
    }

    private ArtifactModelVersion mapVersion(ResultSet resultSet) throws SQLException {
        return new ArtifactModelVersion(
            resultSet.getString("version_id"),
            resultSet.getString("product_id"),
            resultSet.getString("tenant_id"),
            resultSet.getString("parent_version_id"),
            resultSet.getString("commit_message"),
            resultSet.getTimestamp("committed_at").toInstant(),
            resultSet.getString("committed_by"),
            readJson(resultSet.getString("model_snapshot_json"), OBJECT_MAP, Map.of()),
            resultSet.getLong("node_count"),
            resultSet.getLong("edge_count"),
            readJson(resultSet.getString("merge_provenance_json"), STRING_MAP, Map.of())
        );
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to serialize artifact model version data", exception);
        }
    }

    private <T> T readJson(String value, TypeReference<T> typeReference, T fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return objectMapper.readValue(value, typeReference);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to deserialize artifact model version data", exception);
        }
    }
}
