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
                        .map(data -> withProductUnitId(productUnitId, data))
                        .orElseGet(() -> Map.of("productUnitId", productUnitId, "status", "not_found")))
                .then((data, error) -> {
                    if (error != null) {
                        log.error("Failed to read Kernel lifecycle truth: tenantId={}, productUnitId={}", tenantId, productUnitId, error);
                        return Promise.of(Map.of("productUnitId", productUnitId, "status", "error"));
                    }
                    return Promise.of(data);
                });
    }

    @Override
    public Promise<List<Map<String, Object>>> listAllProductUnitLifecycleData() {
        return dataCloudClient.query(tenantId, COLLECTION, DataCloudClient.Query.limit(500))
                .map(entities -> entities.stream()
                        .map(entity -> withProductUnitId(entity.id(), entity.data()))
                        .toList())
                .then((data, error) -> {
                    if (error != null) {
                        log.error("Failed to list Kernel lifecycle truth records: tenantId={}", tenantId, error);
                        return Promise.of(List.of());
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

    private Map<String, Object> withProductUnitId(String productUnitId, Map<String, Object> data) {
        Map<String, Object> result = new HashMap<>(data);
        result.putIfAbsent("productUnitId", productUnitId);
        result.putIfAbsent("status", "found");
        result.put("truthSource", "data-cloud");
        return Map.copyOf(result);
    }
}
