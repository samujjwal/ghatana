/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.infrastructure.importexport;

import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;

/**
 * Interface for importing data from CSV format.
 *
 * @doc.type interface
 * @doc.purpose CSV import capability
 * @doc.layer infrastructure
 */
public interface CsvImporter {

    /**
     * Imports data from a CSV string.
     *
     * @param tenantId the tenant ID
     * @param collectionName the target collection name
     * @param data the CSV data as a string
     * @param columns the expected column names
     * @return Promise of ImportResult with success/failure counts
     */
    Promise<ImportResult> importFromCsv(
        String tenantId,
        String collectionName,
        String data,
        List<String> columns
    );

    /**
     * Result of a CSV import operation.
     */
    class ImportResult {
        public final String tenantId;
        public final String collectionName;
        public final int successCount;
        public final int failureCount;
        public final List<Map<String, Object>> entities;
        public final List<String> errors;
        public final long timestamp;

        public ImportResult(String tenantId, String collectionName,
                            int successCount, int failureCount,
                            List<Map<String, Object>> entities,
                            List<String> errors, long timestamp) {
            this.tenantId = tenantId;
            this.collectionName = collectionName;
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.entities = entities != null ? entities : List.of();
            this.errors = errors != null ? errors : List.of();
            this.timestamp = timestamp;
        }
    }
}
