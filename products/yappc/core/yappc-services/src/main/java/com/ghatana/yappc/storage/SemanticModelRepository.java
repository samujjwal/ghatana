package com.ghatana.yappc.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.domain.artifact.SemanticModelDto;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * @doc.type class
 * @doc.purpose JDBC persistence for semantic model elements with full scope isolation
 * @doc.layer infrastructure
 * @doc.pattern Repository
 *
 * P3: Provides durable storage for semantic model elements extracted from source code.
 * Supports tenant/workspace/project scope isolation and snapshot-based queries.
 */
public final class SemanticModelRepository {

    private static final Logger log = LoggerFactory.getLogger(SemanticModelRepository.class);
    private static final TypeReference<Map<String, Object>> STRING_MAP = new TypeReference<>() { };

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    private final java.util.concurrent.Executor executor;

    public SemanticModelRepository(DataSource dataSource, ObjectMapper objectMapper, java.util.concurrent.Executor executor) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    /**
     * Save a semantic model element.
     *
     * @param model the semantic model to persist
     * @return promise of the persisted model
     */
    public Promise<SemanticModelDto> saveModel(SemanticModelDto model) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                INSERT INTO semantic_models (
                    id, element_id, element_type, name, qualified_name, file_path,
                    source_location_json, properties_json, dependencies_json, dependents_json,
                    provenance, extracted_at, snapshot_id, tenant_id, workspace_id, project_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    element_type = EXCLUDED.element_type,
                    name = EXCLUDED.name,
                    qualified_name = EXCLUDED.qualified_name,
                    file_path = EXCLUDED.file_path,
                    source_location_json = EXCLUDED.source_location_json,
                    properties_json = EXCLUDED.properties_json,
                    dependencies_json = EXCLUDED.dependencies_json,
                    dependents_json = EXCLUDED.dependents_json,
                    extracted_at = EXCLUDED.extracted_at
                """;

            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, model.id());
                statement.setString(2, model.elementId());
                statement.setString(3, model.elementType());
                statement.setString(4, model.name());
                statement.setString(5, model.qualifiedName());
                statement.setString(6, model.filePath());
                statement.setString(7, writeSourceLocation(model.sourceLocation()));
                statement.setString(8, writeStringList(withGovernanceProperties(model)));
                statement.setString(9, writeStringList(model.dependencies()));
                statement.setString(10, writeStringList(model.dependents()));
                statement.setString(11, model.provenance());
                statement.setTimestamp(12, Timestamp.from(model.extractedAt()));
                statement.setString(13, model.snapshotId());
                statement.setString(14, model.tenantId());
                statement.setString(15, model.workspaceId());
                statement.setString(16, model.projectId());
                statement.executeUpdate();
                
                log.debug("Saved semantic model {} for element {} in snapshot {}", 
                    model.id(), model.elementId(), model.snapshotId());
                return model;
            }
        });
    }

    /**
     * Save multiple semantic model elements in batch.
     *
     * @param models the semantic models to persist
     * @return promise of count of saved models
     */
    public Promise<Integer> saveModels(List<SemanticModelDto> models) {
        return Promise.ofBlocking(executor, () -> {
            if (models == null || models.isEmpty()) {
                return 0;
            }

            String sql = """
                INSERT INTO semantic_models (
                    id, element_id, element_type, name, qualified_name, file_path,
                    source_location_json, properties_json, dependencies_json, dependents_json,
                    provenance, extracted_at, snapshot_id, tenant_id, workspace_id, project_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    element_type = EXCLUDED.element_type,
                    name = EXCLUDED.name,
                    qualified_name = EXCLUDED.qualified_name,
                    file_path = EXCLUDED.file_path,
                    source_location_json = EXCLUDED.source_location_json,
                    properties_json = EXCLUDED.properties_json,
                    dependencies_json = EXCLUDED.dependencies_json,
                    dependents_json = EXCLUDED.dependents_json,
                    extracted_at = EXCLUDED.extracted_at
                """;

            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                for (SemanticModelDto model : models) {
                    statement.setString(1, model.id());
                    statement.setString(2, model.elementId());
                    statement.setString(3, model.elementType());
                    statement.setString(4, model.name());
                    statement.setString(5, model.qualifiedName());
                    statement.setString(6, model.filePath());
                    statement.setString(7, writeSourceLocation(model.sourceLocation()));
                    statement.setString(8, writeStringList(withGovernanceProperties(model)));
                    statement.setString(9, writeStringList(model.dependencies()));
                    statement.setString(10, writeStringList(model.dependents()));
                    statement.setString(11, model.provenance());
                    statement.setTimestamp(12, Timestamp.from(model.extractedAt()));
                    statement.setString(13, model.snapshotId());
                    statement.setString(14, model.tenantId());
                    statement.setString(15, model.workspaceId());
                    statement.setString(16, model.projectId());
                    statement.addBatch();
                }
                int[] results = statement.executeBatch();
                int total = java.util.Arrays.stream(results).sum();
                log.info("Saved {} semantic models in batch", total);
                return total;
            }
        });
    }

    /**
     * Find a semantic model by ID.
     *
     * @param id the model ID
     * @return promise of optional model
     */
    public Promise<Optional<SemanticModelDto>> findById(String id) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                SELECT id, element_id, element_type, name, qualified_name, file_path,
                       source_location_json, properties_json, dependencies_json, dependents_json,
                       provenance, extracted_at, snapshot_id, tenant_id, workspace_id, project_id
                FROM semantic_models
                WHERE id = ?
                """;

            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, id);
                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapModel(rs));
                    }
                    return Optional.<SemanticModelDto>empty();
                }
            }
        });
    }

    /**
     * Find semantic models by snapshot ID.
     *
     * @param snapshotId the snapshot ID
     * @return promise of model list
     */
    public Promise<List<SemanticModelDto>> findBySnapshotId(String snapshotId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                SELECT id, element_id, element_type, name, qualified_name, file_path,
                       source_location_json, properties_json, dependencies_json, dependents_json,
                       provenance, extracted_at, snapshot_id, tenant_id, workspace_id, project_id
                FROM semantic_models
                WHERE snapshot_id = ?
                ORDER BY element_type, name
                """;

            List<SemanticModelDto> models = new ArrayList<>();
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, snapshotId);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        models.add(mapModel(rs));
                    }
                }
            }
            return models;
        });
    }

    /**
     * Find semantic models by tenant, workspace, and project scope.
     *
     * @param tenantId the tenant ID
     * @param workspaceId the workspace ID
     * @param projectId the project ID
     * @param limit maximum number of results
     * @return promise of model list
     */
    public Promise<List<SemanticModelDto>> findByScope(String tenantId, String workspaceId, String projectId, int limit) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        Objects.requireNonNull(projectId, "projectId must not be null");

        return Promise.ofBlocking(executor, () -> {
            String sql = """
                SELECT id, element_id, element_type, name, qualified_name, file_path,
                       source_location_json, properties_json, dependencies_json, dependents_json,
                       provenance, extracted_at, snapshot_id, tenant_id, workspace_id, project_id
                FROM semantic_models
                WHERE tenant_id = ? AND workspace_id = ? AND project_id = ?
                ORDER BY extracted_at DESC
                LIMIT ?
                """;

            List<SemanticModelDto> models = new ArrayList<>();
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, tenantId);
                statement.setString(2, workspaceId);
                statement.setString(3, projectId);
                statement.setInt(4, limit);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        models.add(mapModel(rs));
                    }
                }
            }
            return models;
        });
    }

    /**
     * Delete semantic models by snapshot ID.
     *
     * @param snapshotId the snapshot ID
     * @return promise of count of deleted models
     */
    public Promise<Integer> deleteBySnapshotId(String snapshotId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "DELETE FROM semantic_models WHERE snapshot_id = ?";
            
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, snapshotId);
                int deleted = statement.executeUpdate();
                log.info("Deleted {} semantic models for snapshot {}", deleted, snapshotId);
                return deleted;
            }
        });
    }

    private SemanticModelDto mapModel(ResultSet rs) throws SQLException {
        Map<String, Object> storedProperties = readStringMap(rs.getString("properties_json"));
        return new SemanticModelDto(
            rs.getString("id"),
            rs.getString("element_id"),
            rs.getString("element_type"),
            rs.getString("name"),
            rs.getString("qualified_name"),
            rs.getString("file_path"),
            readSourceLocation(rs.getString("source_location_json")),
            baseProperties(storedProperties),
            readStringList(rs.getString("dependencies_json")),
            readStringList(rs.getString("dependents_json")),
            asDouble(storedProperties.get("__confidence")),
            asBoolean(storedProperties.get("__reviewRequired")),
            asString(storedProperties.get("__reviewReason")),
            asStringList(storedProperties.get("__securityFlags")),
            asStringList(storedProperties.get("__privacyFlags")),
            asStringList(storedProperties.get("__graphNodeIds")),
            asStringList(storedProperties.get("__residualIslandIds")),
            asString(storedProperties.get("__sourceRef")),
            asString(storedProperties.get("__symbolRef")),
            asString(storedProperties.get("__extractorId")),
            asString(storedProperties.get("__extractorVersion")),
            asString(storedProperties.get("__modelVersionId")),
            asString(storedProperties.get("__syntheticReason")),
            rs.getString("provenance"),
            rs.getTimestamp("extracted_at").toInstant(),
            rs.getString("snapshot_id"),
            rs.getString("tenant_id"),
            rs.getString("workspace_id"),
            rs.getString("project_id")
        );
    }

    private Map<String, Object> withGovernanceProperties(SemanticModelDto model) {
        Map<String, Object> properties = new java.util.LinkedHashMap<>();
        properties.putAll(model.properties() == null ? Map.of() : model.properties());
        properties.put("__confidence", model.confidence());
        properties.put("__reviewRequired", model.reviewRequired());
        properties.put("__reviewReason", model.reviewReason());
        properties.put("__securityFlags", model.securityFlags());
        properties.put("__privacyFlags", model.privacyFlags());
        properties.put("__graphNodeIds", model.graphNodeIds());
        properties.put("__residualIslandIds", model.residualIslandIds());
        properties.put("__sourceRef", model.sourceRef());
        properties.put("__symbolRef", model.symbolRef());
        properties.put("__extractorId", model.extractorId());
        properties.put("__extractorVersion", model.extractorVersion());
        properties.put("__modelVersionId", model.modelVersionId());
        properties.put("__syntheticReason", model.syntheticReason());
        return properties;
    }

    private Map<String, Object> baseProperties(Map<String, Object> stored) {
        if (stored.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : stored.entrySet()) {
            if (!entry.getKey().startsWith("__")) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text && !text.isBlank()) {
            return Boolean.parseBoolean(text);
        }
        return null;
    }

    private static Double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Double.parseDouble(text);
        }
        return null;
    }

    private static List<String> asStringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().filter(java.util.Objects::nonNull).map(String::valueOf).toList();
        }
        return List.of();
    }

    private String writeSourceLocation(SemanticModelDto.SourceLocation location) throws JsonProcessingException {
        if (location == null) {
            return null;
        }
        return objectMapper.writeValueAsString(Map.of(
            "filePath", location.filePath(),
            "startLine", location.startLine(),
            "startColumn", location.startColumn(),
            "endLine", location.endLine(),
            "endColumn", location.endColumn()
        ));
    }

    private SemanticModelDto.SourceLocation readSourceLocation(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> map = objectMapper.readValue(json, STRING_MAP);
            return new SemanticModelDto.SourceLocation(
                (String) map.get("filePath"),
                ((Number) map.getOrDefault("startLine", 0)).intValue(),
                ((Number) map.getOrDefault("startColumn", 0)).intValue(),
                ((Number) map.getOrDefault("endLine", 0)).intValue(),
                ((Number) map.getOrDefault("endColumn", 0)).intValue()
            );
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse source location JSON", e);
            return null;
        }
    }

    private String writeStringList(List<String> list) throws JsonProcessingException {
        if (list == null || list.isEmpty()) {
            return "[]";
        }
        return objectMapper.writeValueAsString(list);
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse string list JSON", e);
            return List.of();
        }
    }

    private String writeStringList(Map<String, Object> map) throws JsonProcessingException {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        return objectMapper.writeValueAsString(map);
    }

    private Map<String, Object> readStringMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, STRING_MAP);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse string map JSON", e);
            return Map.of();
        }
    }
}
