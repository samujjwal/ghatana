package com.ghatana.yappc.services.shape;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.yappc.common.JsonMapper;
import com.ghatana.yappc.domain.shape.ShapeSpec;
import com.ghatana.yappc.domain.shape.SystemModel;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * @doc.type class
 * @doc.purpose Persists YAPPC Shape artifacts and generated system models to Data Cloud
 * @doc.layer service
 * @doc.pattern Repository Adapter
 */
public final class DataCloudShapeRepository implements ShapeRepository {

    static final String COLLECTION = "yappc_shape_artifacts";

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final DataCloudClient dataCloudClient;

    /**
     * Creates a Data Cloud-backed shape repository.
     *
     * @param dataCloudClient Data Cloud client
     */
    public DataCloudShapeRepository(@NotNull DataCloudClient dataCloudClient) {
        this.dataCloudClient = Objects.requireNonNull(dataCloudClient, "dataCloudClient is required");
    }

    @Override
    public Promise<ShapeVersionRecord> saveShape(
            @NotNull ShapeSpec shape,
            @NotNull ShapePersistenceContext context) {
        validateContext(context);
        return nextVersion(context.tenantId(), context.workspaceId(), context.projectId(), shape.id())
                .then(version -> saveRecord(new ShapeVersionRecord(
                        recordId(context.projectId(), shape.id(), version),
                        context.tenantId(),
                        context.workspaceId(),
                        context.projectId(),
                        shape.id(),
                        version,
                        shape,
                        null,
                        context.actorId(),
                        Instant.now(),
                        context.sourceIntentId(),
                        context.intentEvidenceIds(),
                        context.metadata())));
    }

    @Override
    public Promise<ShapeVersionRecord> saveSystemModel(
            @NotNull SystemModel model,
            @NotNull ShapePersistenceContext context) {
        validateContext(context);
        ShapeSpec shape = model.shape();
        return nextVersion(context.tenantId(), context.workspaceId(), context.projectId(), shape.id())
                .then(version -> saveRecord(new ShapeVersionRecord(
                        recordId(context.projectId(), shape.id(), version),
                        context.tenantId(),
                        context.workspaceId(),
                        context.projectId(),
                        shape.id(),
                        version,
                        shape,
                        model,
                        context.actorId(),
                        Instant.now(),
                        context.sourceIntentId(),
                        context.intentEvidenceIds(),
                        context.metadata())));
    }

    @Override
    public Promise<Optional<ShapeVersionRecord>> findLatest(
            @NotNull String tenantId,
            @NotNull String workspaceId,
            @NotNull String projectId,
            @NotNull String shapeId) {
        return query(tenantId, workspaceId, projectId, shapeId)
                .map(records -> records.stream().max(Comparator.comparingInt(ShapeVersionRecord::version)));
    }

    private Promise<Integer> nextVersion(String tenantId, String workspaceId, String projectId, String shapeId) {
        return query(tenantId, workspaceId, projectId, shapeId)
                .map(records -> records.stream()
                        .mapToInt(ShapeVersionRecord::version)
                        .max()
                        .orElse(0) + 1);
    }

    private Promise<List<ShapeVersionRecord>> query(
            String tenantId,
            String workspaceId,
            String projectId,
            String shapeId) {
        requireText(tenantId, "tenantId");
        requireText(workspaceId, "workspaceId");
        requireText(projectId, "projectId");
        requireText(shapeId, "shapeId");
        DataCloudClient.Query query = DataCloudClient.Query.builder()
                .filters(List.of(
                        DataCloudClient.Filter.eq("workspaceId", workspaceId),
                        DataCloudClient.Filter.eq("projectId", projectId),
                        DataCloudClient.Filter.eq("shapeId", shapeId)))
                .sorts(List.of(DataCloudClient.Sort.desc("version")))
                .limit(100)
                .build();
        return dataCloudClient.query(tenantId, COLLECTION, query)
                .map(entities -> entities.stream()
                        .map(entity -> toRecord(entity.id(), entity.data(), tenantId))
                        .filter(record -> workspaceId.equals(record.workspaceId()))
                        .filter(record -> projectId.equals(record.projectId()))
                        .filter(record -> shapeId.equals(record.shapeId()))
                        .sorted(Comparator.comparingInt(ShapeVersionRecord::version).reversed())
                        .toList());
    }

    private Promise<ShapeVersionRecord> saveRecord(ShapeVersionRecord record) {
        return dataCloudClient.save(record.tenantId(), COLLECTION, toDocument(record)).map(ignored -> record);
    }

    private static Map<String, Object> toDocument(ShapeVersionRecord record) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("id", record.recordId());
        document.put("recordId", record.recordId());
        document.put("tenantId", record.tenantId());
        document.put("workspaceId", record.workspaceId());
        document.put("projectId", record.projectId());
        document.put("shapeId", record.shapeId());
        document.put("version", record.version());
        document.put("sourceIntentId", record.sourceIntentId());
        document.put("intentEvidenceIds", record.intentEvidenceIds());
        document.put("createdBy", record.createdBy());
        document.put("createdAt", record.createdAt().toString());
        document.put("metadata", record.metadata());
        document.put("shape", JsonMapper.getMapper().convertValue(record.shape(), MAP_TYPE));
        if (record.systemModel() != null) {
            document.put("systemModel", JsonMapper.getMapper().convertValue(record.systemModel(), MAP_TYPE));
        }
        return document;
    }

    @SuppressWarnings("unchecked")
    private static ShapeVersionRecord toRecord(String entityId, Map<String, Object> document, String tenantId) {
        Map<String, Object> shapeDocument = document.get("shape") instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of();
        ShapeSpec shape = JsonMapper.getMapper().convertValue(shapeDocument, ShapeSpec.class);
        SystemModel systemModel = null;
        if (document.get("systemModel") instanceof Map<?, ?> modelMap) {
            systemModel = JsonMapper.getMapper().convertValue(modelMap, SystemModel.class);
        }
        Map<String, Object> metadata = document.get("metadata") instanceof Map<?, ?> metadataMap
                ? (Map<String, Object>) metadataMap
                : Map.of();
        return new ShapeVersionRecord(
                asString(document.get("recordId"), entityId),
                tenantId,
                asString(document.get("workspaceId"), ""),
                asString(document.get("projectId"), ""),
                asString(document.get("shapeId"), ""),
                asInt(document.get("version"), 1),
                shape,
                systemModel,
                asString(document.get("createdBy"), null),
                parseInstant(asString(document.get("createdAt"), null)),
                asString(document.get("sourceIntentId"), null),
                stringList(document.get("intentEvidenceIds")),
                metadata);
    }

    private static void validateContext(ShapePersistenceContext context) {
        if (context == null) {
            throw new IllegalArgumentException("context is required");
        }
        requireText(context.tenantId(), "tenantId");
        requireText(context.workspaceId(), "workspaceId");
        requireText(context.projectId(), "projectId");
        if ("default-tenant".equals(context.tenantId())) {
            throw new SecurityException("ShapeRepository does not allow default-tenant");
        }
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }

    private static String recordId(String projectId, String shapeId, int version) {
        return projectId + ":" + shapeId + ":v" + version;
    }

    private static String asString(Object value, String fallback) {
        return value instanceof String text && !text.isBlank() ? text : fallback;
    }

    private static int asInt(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static Instant parseInstant(String value) {
        return value == null || value.isBlank() ? Instant.now() : Instant.parse(value);
    }

    private static List<String> stringList(Object value) {
        if (value instanceof List<?> values) {
            return values.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .filter(item -> !item.isBlank())
                    .toList();
        }
        return List.of();
    }
}
