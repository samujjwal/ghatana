/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.infrastructure.importexport;

import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;

/**
 * Interface for exporting data to Excel format.
 *
 * @doc.type interface
 * @doc.purpose Excel export capability
 * @doc.layer infrastructure
 */
public interface ExcelExporter {

    /**
     * Exports entities to Excel format.
     *
     * @param tenantId the tenant ID
     * @param collectionName the collection name
     * @param entities the entities to export as list of maps
     * @param columns the columns to include
     * @return Promise of byte array containing Excel file data
     */
    Promise<byte[]> exportEntities(
        String tenantId,
        String collectionName,
        List<Map<String, Object>> entities,
        List<String> columns
    );
}
