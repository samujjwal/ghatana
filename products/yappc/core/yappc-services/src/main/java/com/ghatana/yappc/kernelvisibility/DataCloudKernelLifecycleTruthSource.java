/*
 * Copyright (c) 2026 Ghatana Platform Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.ghatana.yappc.kernelvisibility;

import com.ghatana.datacloud.DataCloudClient;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Data Cloud-backed Kernel lifecycle truth source for production deployments.
 *
 * @doc.type class
 * @doc.purpose Read Kernel lifecycle truth from Data Cloud instead of local Kernel output files
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class DataCloudKernelLifecycleTruthSource implements KernelLifecycleTruthSource {

    private static final Logger log = LoggerFactory.getLogger(DataCloudKernelLifecycleTruthSource.class);
    private static final String COLLECTION = "kernel_lifecycle_truth";

    private final DataCloudClient dataCloudClient;
    private final String tenantId;

    /**
     * Creates a Data Cloud truth source.
     *
     * @param dataCloudClient Data Cloud client
     * @param tenantId tenant identifier
     */
    public DataCloudKernelLifecycleTruthSource(
            @NotNull DataCloudClient dataCloudClient,
            @NotNull String tenantId
    ) {
        if (tenantId.isBlank() || "default-tenant".equals(tenantId)) {
            throw new IllegalArgumentException("tenantId must be a real tenant");
        }
        this.dataCloudClient = dataCloudClient;
        this.tenantId = tenantId;
    }

    @Override
    public Promise<Map<String, Object>> getProductUnitLifecycleData(String productUnitId) {
        return dataCloudClient.findById(tenantId, COLLECTION, productUnitId)
                .map(entity -> entity
                        .map(DataCloudClient.Entity::data)
                        .map(data -> normalizeRecord(productUnitId, data))
                        .orElseGet(() -> Map.of("productUnitId", productUnitId, "status", "not_found")))
                .then((data, error) -> {
                    if (error != null) {
                        log.error("Failed to read Kernel lifecycle truth: tenantId={}, productUnitId={}", tenantId, productUnitId, error);
                        return Promise.of(errorRecord(productUnitId, "KERNEL_LIFECYCLE_TRUTH_QUERY_FAILED", error));
                    }
                    return Promise.of(data);
                });
    }

    @Override
    public Promise<List<Map<String, Object>>> listAllProductUnitLifecycleData() {
        return dataCloudClient.query(tenantId, COLLECTION, DataCloudClient.Query.limit(500))
                .map(entities -> entities.stream()
                        .map(entity -> normalizeRecord(entity.id(), entity.data()))
                        .toList())
                .then((data, error) -> {
                    if (error != null) {
                        log.error("Failed to list Kernel lifecycle truth records: tenantId={}", tenantId, error);
                        return Promise.of(List.of(errorRecord(
                                "__list__",
                                "KERNEL_LIFECYCLE_TRUTH_LIST_FAILED",
                                error)));
                    }
                    return Promise.of(data);
                });
    }

    @Override
    public Promise<List<String>> listProductUnitIds() {
        return listAllProductUnitLifecycleData()
                .map(records -> records.stream()
                        .map(record -> record.get("productUnitId"))
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .filter(id -> !id.isBlank())
                        .toList());
    }

    @Override
    public Promise<Boolean> hasLifecycleData(String productUnitId) {
        return getProductUnitLifecycleData(productUnitId)
                .map(data -> !"not_found".equals(data.get("status")) && !"error".equals(data.get("status")));
    }

    private Map<String, Object> normalizeRecord(String productUnitId, Map<String, Object> data) {
        try {
            return KernelLifecycleTruthRecord.from(productUnitId, data).toMap();
        } catch (IllegalArgumentException e) {
            log.warn(
                    "Malformed Kernel lifecycle truth record: tenantId={}, productUnitId={}",
                    tenantId,
                    productUnitId,
                    e);
            return errorRecord(productUnitId, "MALFORMED_KERNEL_LIFECYCLE_TRUTH", e);
        }
    }

    private Map<String, Object> errorRecord(String productUnitId, String reason, Throwable error) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("productUnitId", productUnitId);
        result.put("status", "error");
        result.put("truthSource", "data-cloud");
        result.put("degraded", true);
        result.put("degradedReason", reason);
        result.put("errorType", error.getClass().getSimpleName());
        return Map.copyOf(result);
    }

    /**
     * Typed view of a Data Cloud kernel_lifecycle_truth record.
     *
     * @doc.type record
     * @doc.purpose Validates Kernel lifecycle truth records before YAPPC consumes them
     * @doc.layer product
     * @doc.pattern DTO
     */
    public record KernelLifecycleTruthRecord(
            String productUnitId,
            String status,
            @Nullable Map<String, Object> lifecycleResult,
            @Nullable HealthSnapshotSection healthSnapshot,
            @Nullable GateStateSection gates,
            @Nullable ArtifactStateSection artifacts,
            @Nullable DeploymentStateSection deployment,
            Map<String, Object> metadata
    ) {

        public KernelLifecycleTruthRecord {
            if (productUnitId == null || productUnitId.isBlank()) {
                throw new IllegalArgumentException("productUnitId is required");
            }
            if (status == null || status.isBlank()) {
                throw new IllegalArgumentException("status is required");
            }
            lifecycleResult = immutableMap(lifecycleResult);
            metadata = immutableMap(metadata);
        }

        public static KernelLifecycleTruthRecord from(String entityId, Map<String, Object> data) {
            if (entityId == null || entityId.isBlank()) {
                throw new IllegalArgumentException("Data Cloud entity id is required");
            }
            Object embeddedProductUnitId = data.get("productUnitId");
            String productUnitId = entityId;
            if (embeddedProductUnitId != null) {
                if (!(embeddedProductUnitId instanceof String embedded) || embedded.isBlank()) {
                    throw new IllegalArgumentException("productUnitId must be a non-blank string");
                }
                if (!entityId.equals(embedded)) {
                    throw new IllegalArgumentException("productUnitId must match Data Cloud entity id");
                }
                productUnitId = embedded;
            }

            Object rawStatus = data.getOrDefault("status", "found");
            if (!(rawStatus instanceof String status) || status.isBlank()) {
                throw new IllegalArgumentException("status must be a non-blank string");
            }

            Map<String, Object> metadata = new LinkedHashMap<>(data);
            metadata.remove("productUnitId");
            metadata.remove("status");

            Map<String, Object> lifecycleResult = extractSection(metadata, "lifecycleResult");
            HealthSnapshotSection healthSnapshot = HealthSnapshotSection.from(extractSection(metadata, "healthSnapshot"));
            GateStateSection gates = GateStateSection.from(extractSection(metadata, "gates"));
            ArtifactStateSection artifacts = ArtifactStateSection.from(extractSection(metadata, "artifacts"));
            DeploymentStateSection deployment = DeploymentStateSection.from(extractSection(metadata, "deployment"));

            return new KernelLifecycleTruthRecord(
                    productUnitId,
                    status,
                    lifecycleResult,
                    healthSnapshot,
                    gates,
                    artifacts,
                    deployment,
                    metadata);
        }

        public Map<String, Object> toMap() {
            Map<String, Object> result = new LinkedHashMap<>(metadata);
            if (lifecycleResult != null) {
                result.put("lifecycleResult", lifecycleResult);
            }
            if (healthSnapshot != null) {
                result.put("healthSnapshot", healthSnapshot.toMap());
            }
            if (gates != null) {
                result.put("gates", gates.toMap());
            }
            if (artifacts != null) {
                result.put("artifacts", artifacts.toMap());
            }
            if (deployment != null) {
                result.put("deployment", deployment.toMap());
            }
            result.put("productUnitId", productUnitId);
            result.put("status", status);
            result.put("truthSource", "data-cloud");
            return Map.copyOf(result);
        }

        private static Map<String, Object> extractSection(Map<String, Object> metadata, String key) {
            Object value = metadata.remove(key);
            if (value == null) {
                return null;
            }
            if (!(value instanceof Map<?, ?> rawMap)) {
                throw new IllegalArgumentException(key + " must be an object");
            }
            Map<String, Object> typedMap = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                if (!(entry.getKey() instanceof String stringKey) || stringKey.isBlank()) {
                    throw new IllegalArgumentException(key + " contains a non-string key");
                }
                typedMap.put(stringKey, entry.getValue());
            }
            return typedMap;
        }

        private static Map<String, Object> immutableMap(@Nullable Map<String, Object> value) {
            if (value == null) {
                return null;
            }
            return Collections.unmodifiableMap(new LinkedHashMap<>(value));
        }

        public record HealthSnapshotSection(
                @Nullable String status,
                @Nullable String lastChecked,
                Map<String, Object> metadata
        ) {
            public HealthSnapshotSection {
                metadata = immutableMap(metadata);
            }

            static HealthSnapshotSection from(@Nullable Map<String, Object> data) {
                if (data == null) {
                    return null;
                }
                Map<String, Object> metadata = new LinkedHashMap<>(data);
                return new HealthSnapshotSection(
                        readOptionalString(metadata, "status"),
                        readOptionalString(metadata, "lastChecked"),
                        metadata);
            }

            Map<String, Object> toMap() {
                Map<String, Object> result = new LinkedHashMap<>(metadata);
                putIfPresent(result, "status", status);
                putIfPresent(result, "lastChecked", lastChecked);
                return Map.copyOf(result);
            }
        }

        public record GateStateSection(
                @Nullable Integer failedCount,
                @Nullable Integer totalCount,
                Map<String, Object> metadata
        ) {
            public GateStateSection {
                metadata = immutableMap(metadata);
            }

            static GateStateSection from(@Nullable Map<String, Object> data) {
                if (data == null) {
                    return null;
                }
                Map<String, Object> metadata = new LinkedHashMap<>(data);
                return new GateStateSection(
                        readOptionalInteger(metadata, "failedCount"),
                        readOptionalInteger(metadata, "totalCount"),
                        metadata);
            }

            Map<String, Object> toMap() {
                Map<String, Object> result = new LinkedHashMap<>(metadata);
                putIfPresent(result, "failedCount", failedCount);
                putIfPresent(result, "totalCount", totalCount);
                return Map.copyOf(result);
            }
        }

        public record ArtifactStateSection(
                @Nullable String status,
                @Nullable Integer artifactCount,
                Map<String, Object> metadata
        ) {
            public ArtifactStateSection {
                metadata = immutableMap(metadata);
            }

            static ArtifactStateSection from(@Nullable Map<String, Object> data) {
                if (data == null) {
                    return null;
                }
                Map<String, Object> metadata = new LinkedHashMap<>(data);
                return new ArtifactStateSection(
                        readOptionalString(metadata, "status"),
                        readOptionalInteger(metadata, "artifactCount"),
                        metadata);
            }

            Map<String, Object> toMap() {
                Map<String, Object> result = new LinkedHashMap<>(metadata);
                putIfPresent(result, "status", status);
                putIfPresent(result, "artifactCount", artifactCount);
                return Map.copyOf(result);
            }
        }

        public record DeploymentStateSection(
                @Nullable String status,
                @Nullable String environment,
                Map<String, Object> metadata
        ) {
            public DeploymentStateSection {
                metadata = immutableMap(metadata);
            }

            static DeploymentStateSection from(@Nullable Map<String, Object> data) {
                if (data == null) {
                    return null;
                }
                Map<String, Object> metadata = new LinkedHashMap<>(data);
                return new DeploymentStateSection(
                        readOptionalString(metadata, "status"),
                        readOptionalString(metadata, "environment"),
                        metadata);
            }

            Map<String, Object> toMap() {
                Map<String, Object> result = new LinkedHashMap<>(metadata);
                putIfPresent(result, "status", status);
                putIfPresent(result, "environment", environment);
                return Map.copyOf(result);
            }
        }

        private static String readOptionalString(Map<String, Object> metadata, String key) {
            Object value = metadata.remove(key);
            if (value == null) {
                return null;
            }
            if (!(value instanceof String stringValue) || stringValue.isBlank()) {
                throw new IllegalArgumentException(key + " must be a non-blank string");
            }
            return stringValue;
        }

        private static Integer readOptionalInteger(Map<String, Object> metadata, String key) {
            Object value = metadata.remove(key);
            if (value == null) {
                return null;
            }
            if (value instanceof Number number) {
                return number.intValue();
            }
            throw new IllegalArgumentException(key + " must be numeric");
        }

        private static void putIfPresent(Map<String, Object> target, String key, @Nullable Object value) {
            if (value != null) {
                target.put(key, value);
            }
        }
    }
}
