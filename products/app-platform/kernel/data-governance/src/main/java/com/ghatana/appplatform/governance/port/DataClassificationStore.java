/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.governance.port;

import com.ghatana.appplatform.governance.DataCatalogService.Classification;

import java.util.List;

/**
 * Repository port for classification-related persistence operations.
 *
 * @doc.type interface
 * @doc.purpose Abstracts classification and override persistence from domain service
 * @doc.layer product
 * @doc.pattern Port
 */
public interface DataClassificationStore {

    AssetRow loadAsset(String assetId) throws Exception;

    List<String> loadAllAssetIds() throws Exception;

    void updateClassification(String assetId, Classification classification) throws Exception;

    void persistOverrideRequest(String requestId, String assetId, String taskId,
                                String classification, String requestedBy,
                                String reason) throws Exception;

    void markOverrideApplied(String requestId) throws Exception;

    OverrideRow loadOverrideRequest(String requestId) throws Exception;

    record AssetRow(String assetId, String name, String schemaContent,
                    Classification classification) {}

    record OverrideRow(String assetId, String requestedClassification) {}
}
