/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.governance.port;

import com.ghatana.appplatform.governance.DataCatalogService.Classification;
import com.ghatana.appplatform.governance.DataCatalogService.DataAsset;

import java.util.List;
import java.util.Optional;

/**
 * Repository port for data catalog persistence.
 *
 * <p>Hex-arch boundary: services depend on this port; JDBC adapters implement it.
 * Satisfies Mandate 2: no direct JDBC in domain services.
 *
 * @doc.type interface
 * @doc.purpose Repository port for DataCatalog CRUD and search
 * @doc.layer product
 * @doc.pattern Repository Port
 */
public interface DataCatalogStore {

    DataAsset upsert(String assetId, String name, String serviceOwner,
                     String schemaRef, Classification classification, String description) throws Exception;

    Optional<DataAsset> findById(String assetId) throws Exception;

    List<DataAsset> search(String query, String tag, Classification classification) throws Exception;

    DataAsset addTag(String assetId, String tag) throws Exception;

    DataAsset updateClassification(String assetId, Classification classification) throws Exception;

    long countAssets() throws Exception;
}
