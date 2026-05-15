/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.kernel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter;
import com.ghatana.kernel.adapter.datacloud.DataQueryRequest;
import com.ghatana.kernel.adapter.datacloud.DataReadRequest;
import com.ghatana.kernel.adapter.datacloud.DataResult;
import com.ghatana.kernel.adapter.datacloud.DataWriteRequest;
import com.ghatana.kernel.adapter.datacloud.QueryResult;
import com.ghatana.kernel.bridge.port.BridgeContext;
import com.ghatana.platform.core.util.JsonUtils;
import io.activej.promise.Promise;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Shared Data Cloud persistence helpers for kernel platform-mode providers.
 *
 * @doc.type class
 * @doc.purpose Reuse Data Cloud adapter persistence for provider records
 * @doc.layer adapter
 * @doc.pattern TemplateMethod
 */
abstract class DataCloudKernelProviderSupport {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final DataCloudKernelAdapter adapter;
    private final BridgeContext context;
    private final String datasetId;
    private final String providerName;

    DataCloudKernelProviderSupport(
            DataCloudKernelAdapter adapter,
            BridgeContext context,
            String datasetId,
            String providerName) {
        this.adapter = Objects.requireNonNull(adapter, "adapter cannot be null");
        this.context = Objects.requireNonNull(context, "context cannot be null");
        this.datasetId = Objects.requireNonNull(datasetId, "datasetId cannot be null");
        this.providerName = Objects.requireNonNull(providerName, "providerName cannot be null");
    }

    protected final Promise<Void> persistRecord(String recordId, Map<String, Object> record) {
        Objects.requireNonNull(recordId, "recordId cannot be null");
        Objects.requireNonNull(record, "record cannot be null");
        byte[] payload;
        try {
            payload = JsonUtils.toJsonBytes(record);
        } catch (Exception ex) {
            return Promise.ofException(new DataCloudProviderException(providerName, "serialize", ex));
        }
        return adapter.writeData(new DataWriteRequest(
            context,
            datasetId,
            recordId,
            payload,
            Map.of(
                "provider", providerName,
                "tenantId", context.getTenantId(),
                "recordId", recordId,
                "updatedAt", Instant.now().toString()
            )));
    }

    protected final Promise<Map<String, Object>> readRecord(String recordId) {
        Objects.requireNonNull(recordId, "recordId cannot be null");
        return adapter.readData(new DataReadRequest(context, datasetId, recordId, Map.of()))
            .map(this::decodeRecord);
    }

    protected final Promise<List<Map<String, Object>>> queryRecords(Map<String, Object> parameters, int limit) {
        return adapter.queryData(new DataQueryRequest(
                context,
                datasetId,
                "provider-records",
                parameters != null ? parameters : Map.of(),
                limit,
                0))
            .map(this::decodeQueryResult);
    }

    protected final BridgeContext context() {
        return context;
    }

    protected final String providerName() {
        return providerName;
    }

    private Map<String, Object> decodeRecord(DataResult result) {
        if (result == null || result.getData() == null) {
            throw new DataCloudProviderException(providerName, "read", "Record not found in " + datasetId);
        }
        try {
            return JsonUtils.fromJson(new String(result.getData(), StandardCharsets.UTF_8), MAP_TYPE);
        } catch (Exception ex) {
            throw new DataCloudProviderException(providerName, "deserialize", ex);
        }
    }

    private List<Map<String, Object>> decodeQueryResult(QueryResult queryResult) {
        return queryResult.getResults().stream()
            .map(this::decodeRecord)
            .toList();
    }
}
