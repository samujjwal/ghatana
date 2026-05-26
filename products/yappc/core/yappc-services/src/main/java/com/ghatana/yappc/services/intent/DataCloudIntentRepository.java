package com.ghatana.yappc.services.intent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.yappc.common.JsonMapper;
import com.ghatana.yappc.domain.intent.IntentSpec;
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
 * @doc.purpose Persists versioned YAPPC intent records to Data Cloud
 * @doc.layer service
 * @doc.pattern Repository Adapter
 */
public final class DataCloudIntentRepository implements IntentRepository {

    static final String COLLECTION = "yappc_intents";

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final DataCloudClient dataCloudClient;

    /**
     * Creates a Data Cloud-backed intent repository.
     *
     * @param dataCloudClient Data Cloud client
     */
    public DataCloudIntentRepository(@NotNull DataCloudClient dataCloudClient) {
        this.dataCloudClient = Objects.requireNonNull(dataCloudClient, "dataCloudClient is required");
    }

    @Override
    public Promise<IntentVersionRecord> saveVersion(
            @NotNull IntentSpec spec,
            @NotNull IntentPersistenceContext context
    ) {
        validateContext(context);
        if (spec == null) {
            return Promise.ofException(new IllegalArgumentException("spec is required"));
        }

        return history(context.tenantId(), context.workspaceId(), context.projectId(), spec.id())
                .then(history -> {
                    int nextVersion = history.stream()
                            .mapToInt(IntentVersionRecord::version)
                            .max()
                            .orElse(0) + 1;
                    IntentVersionRecord record = toRecord(spec, context, nextVersion);
                    return dataCloudClient.save(context.tenantId(), COLLECTION, toDocument(record))
                            .map(ignored -> record);
                });
    }

    @Override
    public Promise<Optional<IntentVersionRecord>> findLatest(
            @NotNull String tenantId,
            @NotNull String workspaceId,
            @NotNull String projectId,
            @NotNull String intentId
    ) {
        return history(tenantId, workspaceId, projectId, intentId)
                .map(records -> records.stream()
                        .max(Comparator.comparingInt(IntentVersionRecord::version)));
    }

    @Override
    public Promise<List<IntentVersionRecord>> history(
            @NotNull String tenantId,
            @NotNull String workspaceId,
            @NotNull String projectId,
            @NotNull String intentId
    ) {
        requireText(tenantId, "tenantId");
        requireText(workspaceId, "workspaceId");
        requireText(projectId, "projectId");
        requireText(intentId, "intentId");

        DataCloudClient.Query query = DataCloudClient.Query.builder()
                .filters(List.of(
                        DataCloudClient.Filter.eq("workspaceId", workspaceId),
                        DataCloudClient.Filter.eq("projectId", projectId),
                        DataCloudClient.Filter.eq("intentId", intentId)))
                .sorts(List.of(DataCloudClient.Sort.desc("version")))
                .limit(100)
                .build();

        return dataCloudClient.query(tenantId, COLLECTION, query)
                .map(entities -> entities.stream()
                        .map(entity -> toRecord(entity.id(), entity.data(), tenantId))
                        .filter(record -> workspaceId.equals(record.workspaceId()))
                        .filter(record -> projectId.equals(record.projectId()))
                        .filter(record -> intentId.equals(record.intentId()))
                        .sorted(Comparator.comparingInt(IntentVersionRecord::version).reversed())
                        .toList());
    }

    @Override
    public Promise<Long> count(@NotNull String tenantId) {
        requireText(tenantId, "tenantId");
        return dataCloudClient.query(tenantId, COLLECTION, DataCloudClient.Query.limit(1000))
                .map(records -> (long) records.size());
    }

    private static IntentVersionRecord toRecord(
            IntentSpec spec,
            IntentPersistenceContext context,
            int version
    ) {
        Instant createdAt = Instant.now();
        return new IntentVersionRecord(
                recordId(context.projectId(), spec.id(), version),
                context.tenantId(),
                context.workspaceId(),
                context.projectId(),
                spec.id(),
                version,
                spec,
                context.actorId(),
                createdAt,
                context.auditEventId(),
                context.evidenceIds(),
                context.metadata());
    }

    private static Map<String, Object> toDocument(IntentVersionRecord record) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("id", record.recordId());
        document.put("recordId", record.recordId());
        document.put("tenantId", record.tenantId());
        document.put("workspaceId", record.workspaceId());
        document.put("projectId", record.projectId());
        document.put("intentId", record.intentId());
        document.put("version", record.version());
        document.put("productName", record.spec().productName());
        document.put("createdBy", record.createdBy());
        document.put("createdAt", record.createdAt().toString());
        document.put("auditEventId", record.auditEventId());
        document.put("evidenceIds", record.evidenceIds());
        document.put("metadata", record.metadata());
        document.put("spec", JsonMapper.getMapper().convertValue(record.spec(), MAP_TYPE));
        return document;
    }

    @SuppressWarnings("unchecked")
    private static IntentVersionRecord toRecord(String entityId, Map<String, Object> document, String tenantId) {
        String recordId = asString(document.get("recordId"), entityId);
        String workspaceId = asString(document.get("workspaceId"), "");
        String projectId = asString(document.get("projectId"), "");
        String intentId = asString(document.get("intentId"), "");
        int version = asInt(document.get("version"), 1);
        Map<String, Object> specDocument = document.get("spec") instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of();
        IntentSpec spec = JsonMapper.getMapper().convertValue(specDocument, IntentSpec.class);
        String createdBy = asString(document.get("createdBy"), null);
        Instant createdAt = parseInstant(asString(document.get("createdAt"), null));
        String auditEventId = asString(document.get("auditEventId"), null);
        List<String> evidenceIds = stringList(document.get("evidenceIds"));
        Map<String, Object> metadata = document.get("metadata") instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of();

        return new IntentVersionRecord(
                recordId,
                tenantId,
                workspaceId,
                projectId,
                intentId,
                version,
                spec,
                createdBy,
                createdAt,
                auditEventId,
                evidenceIds,
                metadata);
    }

    private static void validateContext(IntentPersistenceContext context) {
        if (context == null) {
            throw new IllegalArgumentException("context is required");
        }
        requireText(context.tenantId(), "tenantId");
        requireText(context.workspaceId(), "workspaceId");
        requireText(context.projectId(), "projectId");
        if ("default-tenant".equals(context.tenantId())) {
            throw new SecurityException("IntentRepository does not allow default-tenant");
        }
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }

    private static String recordId(String projectId, String intentId, int version) {
        return projectId + ":" + intentId + ":v" + version;
    }

    private static String asString(Object value, String fallback) {
        return value instanceof String text && !text.isBlank() ? text : fallback;
    }

    private static int asInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Integer.parseInt(text);
        }
        return fallback;
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return Instant.now();
        }
        return Instant.parse(value);
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
