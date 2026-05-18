package com.ghatana.yappc.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.domain.artifact.SemanticModelDto;
import com.ghatana.yappc.domain.artifact.SourceLocationDto;
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
     * P0: Extracted shared binder logic to bindSemanticModelStatement for consistency.
     * P0: Added schema validation to ensure required columns exist.
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
                    confidence, review_required, review_reason, security_flags, privacy_flags,
                    graph_node_ids, residual_island_ids, source_ref, symbol_ref,
                    extractor_id, extractor_version, model_version_id, synthetic_reason,
                    provenance, extracted_at, snapshot_id, tenant_id, workspace_id, project_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    element_type = EXCLUDED.element_type,
                    name = EXCLUDED.name,
                    qualified_name = EXCLUDED.qualified_name,
                    file_path = EXCLUDED.file_path,
                    source_location_json = EXCLUDED.source_location_json,
                    properties_json = EXCLUDED.properties_json,
                    dependencies_json = EXCLUDED.dependencies_json,
                    dependents_json = EXCLUDED.dependents_json,
                    confidence = EXCLUDED.confidence,
                    review_required = EXCLUDED.review_required,
                    review_reason = EXCLUDED.review_reason,
                    security_flags = EXCLUDED.security_flags,
                    privacy_flags = EXCLUDED.privacy_flags,
                    graph_node_ids = EXCLUDED.graph_node_ids,
                    residual_island_ids = EXCLUDED.residual_island_ids,
                    source_ref = EXCLUDED.source_ref,
                    symbol_ref = EXCLUDED.symbol_ref,
                    extractor_id = EXCLUDED.extractor_id,
                    extractor_version = EXCLUDED.extractor_version,
                    model_version_id = EXCLUDED.model_version_id,
                    synthetic_reason = EXCLUDED.synthetic_reason,
                    extracted_at = EXCLUDED.extracted_at
                """;

            try (Connection connection = dataSource.getConnection()) {
                validateSchema(connection);
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    bindSemanticModelStatement(statement, model, 1);
                    statement.executeUpdate();
                    
                    log.debug("Saved semantic model {} for element {} in snapshot {}", 
                        model.id(), model.elementId(), model.snapshotId());
                    return model;
                }
            }
        });
    }

    /**
     * Save multiple semantic model elements in batch.
     *
     * P0: Extracted shared binder logic to bindSemanticModelStatement for consistency.
     * P0: Added schema validation to ensure required columns exist.
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
                    confidence, review_required, review_reason, security_flags, privacy_flags,
                    graph_node_ids, residual_island_ids, source_ref, symbol_ref,
                    extractor_id, extractor_version, model_version_id, synthetic_reason,
                    provenance, extracted_at, snapshot_id, tenant_id, workspace_id, project_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    element_type = EXCLUDED.element_type,
                    name = EXCLUDED.name,
                    qualified_name = EXCLUDED.qualified_name,
                    file_path = EXCLUDED.file_path,
                    source_location_json = EXCLUDED.source_location_json,
                    properties_json = EXCLUDED.properties_json,
                    dependencies_json = EXCLUDED.dependencies_json,
                    dependents_json = EXCLUDED.dependents_json,
                    confidence = EXCLUDED.confidence,
                    review_required = EXCLUDED.review_required,
                    review_reason = EXCLUDED.review_reason,
                    security_flags = EXCLUDED.security_flags,
                    privacy_flags = EXCLUDED.privacy_flags,
                    graph_node_ids = EXCLUDED.graph_node_ids,
                    residual_island_ids = EXCLUDED.residual_island_ids,
                    source_ref = EXCLUDED.source_ref,
                    symbol_ref = EXCLUDED.symbol_ref,
                    extractor_id = EXCLUDED.extractor_id,
                    extractor_version = EXCLUDED.extractor_version,
                    model_version_id = EXCLUDED.model_version_id,
                    synthetic_reason = EXCLUDED.synthetic_reason,
                    extracted_at = EXCLUDED.extracted_at
                """;

            try (Connection connection = dataSource.getConnection()) {
                validateSchema(connection);
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    for (SemanticModelDto model : models) {
                        bindSemanticModelStatement(statement, model, 1);
                        statement.addBatch();
                    }
                    int[] results = statement.executeBatch();
                    int total = java.util.Arrays.stream(results).sum();
                    log.info("Saved {} semantic models in batch", total);
                    return total;
                }
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
                       confidence, review_required, review_reason, security_flags, privacy_flags,
                       graph_node_ids, residual_island_ids, source_ref, symbol_ref,
                       extractor_id, extractor_version, model_version_id, synthetic_reason,
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
                       confidence, review_required, review_reason, security_flags, privacy_flags,
                       graph_node_ids, residual_island_ids, source_ref, symbol_ref,
                       extractor_id, extractor_version, model_version_id, synthetic_reason,
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
                       confidence, review_required, review_reason, security_flags, privacy_flags,
                       graph_node_ids, residual_island_ids, source_ref, symbol_ref,
                       extractor_id, extractor_version, model_version_id, synthetic_reason,
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
        
        // Try to read from first-class columns first, fall back to legacy __ properties for migration compatibility
        Double confidence = rs.getObject("confidence") != null ? rs.getDouble("confidence") : asDouble(storedProperties.get("__confidence"));
        Boolean reviewRequired = rs.getObject("review_required") != null ? rs.getBoolean("review_required") : asBoolean(storedProperties.get("__reviewRequired"));
        String reviewReason = rs.getString("review_reason");
        if (reviewReason == null) {
            reviewReason = asString(storedProperties.get("__reviewReason"));
        }
        
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
            confidence,
            reviewRequired,
            reviewReason,
            readStringList(rs.getString("security_flags")),
            readStringList(rs.getString("privacy_flags")),
            readStringList(rs.getString("graph_node_ids")),
            readStringList(rs.getString("residual_island_ids")),
            rs.getString("source_ref"),
            rs.getString("symbol_ref"),
            rs.getString("extractor_id"),
            rs.getString("extractor_version"),
            rs.getString("model_version_id"),
            rs.getString("synthetic_reason"),
            parseProvenance(rs.getString("provenance")),
            rs.getTimestamp("extracted_at").toInstant(),
            rs.getString("snapshot_id"),
            rs.getString("tenant_id"),
            rs.getString("workspace_id"),
            rs.getString("project_id")
        );
    }

    // Removed withGovernanceProperties - governance fields are now first-class columns
    // This method is no longer needed as of V24 migration

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

    private String writeSourceLocation(SourceLocationDto location) throws JsonProcessingException {
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

    private SourceLocationDto readSourceLocation(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> map = objectMapper.readValue(json, STRING_MAP);
            return new SourceLocationDto(
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

    private SemanticModelDto.Provenance parseProvenance(String provenance) {
        if (provenance == null || provenance.isBlank()) {
            return SemanticModelDto.Provenance.INFERRED;
        }
        return switch (provenance.toLowerCase()) {
            case "exact" -> SemanticModelDto.Provenance.EXACT;
            case "inferred" -> SemanticModelDto.Provenance.INFERRED;
            case "synthesized" -> SemanticModelDto.Provenance.SYNTHESIZED;
            case "manual" -> SemanticModelDto.Provenance.MANUAL;
            case "assumed" -> SemanticModelDto.Provenance.ASSUMED;
            default -> SemanticModelDto.Provenance.INFERRED;
        };
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

    /**
     * P0: Validates that the semantic_models table schema matches the expected columns.
     * Throws IllegalStateException if required columns are missing, preventing silent failures.
     */
    private void validateSchema(Connection connection) throws SQLException {
        java.util.Set<String> requiredColumns = java.util.Set.of(
            "id", "element_id", "element_type", "name", "qualified_name", "file_path",
            "source_location_json", "properties_json", "dependencies_json", "dependents_json",
            "confidence", "review_required", "review_reason", "security_flags", "privacy_flags",
            "graph_node_ids", "residual_island_ids", "source_ref", "symbol_ref",
            "extractor_id", "extractor_version", "model_version_id", "synthetic_reason",
            "provenance", "extracted_at", "snapshot_id", "tenant_id", "workspace_id", "project_id"
        );

        String schemaSql = """
            SELECT column_name
            FROM information_schema.columns
            WHERE table_name = 'semantic_models'
            """;

        java.util.Set<String> existingColumns = new java.util.HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(schemaSql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                existingColumns.add(rs.getString("column_name"));
            }
        }

        java.util.Set<String> missingColumns = new java.util.HashSet<>(requiredColumns);
        missingColumns.removeAll(existingColumns);

        if (!missingColumns.isEmpty()) {
            throw new IllegalStateException(
                "semantic_models table schema mismatch. Missing required columns: " + 
                String.join(", ", missingColumns) + 
                ". Run the latest database migration to fix the schema."
            );
        }
    }

    /**
     * P0: Shared binder for semantic model statements.
     * Ensures consistent parameter binding across saveModel and saveModels methods.
     * All 29 parameters must be bound in the correct order to match the SQL placeholder count.
     */
    private void bindSemanticModelStatement(PreparedStatement statement, SemanticModelDto model, int startIndex) 
            throws SQLException, JsonProcessingException {
        int idx = startIndex;
        statement.setString(idx++, model.id());
        statement.setString(idx++, model.elementId());
        statement.setString(idx++, model.elementType());
        statement.setString(idx++, model.name());
        statement.setString(idx++, model.qualifiedName());
        statement.setString(idx++, model.filePath());
        statement.setString(idx++, writeSourceLocation(model.sourceLocation()));
        statement.setString(idx++, writeStringList(model.properties()));
        statement.setString(idx++, writeStringList(model.dependencies()));
        statement.setString(idx++, writeStringList(model.dependents()));
        statement.setDouble(idx++, model.confidence() != null ? model.confidence() : 0.0);
        statement.setBoolean(idx++, model.reviewRequired() != null ? model.reviewRequired() : false);
        statement.setString(idx++, model.reviewReason());
        statement.setString(idx++, writeStringList(model.securityFlags()));
        statement.setString(idx++, writeStringList(model.privacyFlags()));
        statement.setString(idx++, writeStringList(model.graphNodeIds()));
        statement.setString(idx++, writeStringList(model.residualIslandIds()));
        statement.setString(idx++, model.sourceRef());
        statement.setString(idx++, model.symbolRef());
        statement.setString(idx++, model.extractorId());
        statement.setString(idx++, model.extractorVersion());
        statement.setString(idx++, model.modelVersionId());
        statement.setString(idx++, model.syntheticReason());
        statement.setString(idx++, model.provenance() != null ? model.provenance().name().toLowerCase() : null);
        statement.setTimestamp(idx++, Timestamp.from(model.extractedAt()));
        statement.setString(idx++, model.snapshotId());
        statement.setString(idx++, model.tenantId());
        statement.setString(idx++, model.workspaceId());
        statement.setString(idx++, model.projectId());
    }
}
