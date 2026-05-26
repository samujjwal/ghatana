/*
 * Copyright (c) 2026 Ghatana Platform Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.ghatana.yappc.kernelvisibility;

import com.ghatana.datacloud.DataCloudClient;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.HashMap;
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
            Map<String, Object> attributes
    ) {

        public KernelLifecycleTruthRecord {
            if (productUnitId == null || productUnitId.isBlank()) {
                throw new IllegalArgumentException("productUnitId is required");
            }
            if (status == null || status.isBlank()) {
                throw new IllegalArgumentException("status is required");
            }
            attributes = Map.copyOf(attributes);
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

            Map<String, Object> attributes = new HashMap<>(data);
            attributes.remove("productUnitId");
            attributes.remove("status");
            return new KernelLifecycleTruthRecord(productUnitId, status, attributes);
        }

        public Map<String, Object> toMap() {
            Map<String, Object> result = new LinkedHashMap<>(attributes);
            result.put("productUnitId", productUnitId);
            result.put("status", status);
            result.put("truthSource", "data-cloud");
            return Map.copyOf(result);
        }
    }
}
