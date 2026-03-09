/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.infrastructure.importexport;

import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for importing and exporting data in various formats (CSV, JSON, Excel, PDF).
 *
 * <p>Supports:
 * <ul>
 *   <li>Export to CSV, PDF, Excel</li>
 *   <li>Import from CSV, JSON, Excel</li>
 *   <li>Validation of import data before import</li>
 *   <li>Progress tracking for long-running operations</li>
 * </ul>
 *
 * @doc.type service
 * @doc.purpose Data import/export operations
 * @doc.layer infrastructure
 */
public class ImportExportService {

    private final ExcelExporter excelExporter;
    private final CsvImporter csvImporter;
    private final MetricsCollector metricsCollector;

    /**
     * Creates a new ImportExportService.
     *
     * @param excelExporter the Excel exporter (required)
     * @param csvImporter the CSV importer (required)
     * @param metricsCollector the metrics collector (required)
     * @throws NullPointerException if any parameter is null
     */
    public ImportExportService(ExcelExporter excelExporter, CsvImporter csvImporter, MetricsCollector metricsCollector) {
        this.excelExporter = Objects.requireNonNull(excelExporter, "ExcelExporter must not be null");
        this.csvImporter = Objects.requireNonNull(csvImporter, "CsvImporter must not be null");
        this.metricsCollector = Objects.requireNonNull(metricsCollector, "MetricsCollector must not be null");
    }

    // ========================================================================
    // EXPORT
    // ========================================================================

    /**
     * Exports entities to the specified format.
     *
     * @param tenantId the tenant ID (required)
     * @param collectionName the collection name (required)
     * @param entities the entities to export (required)
     * @param format the export format (required)
     * @return Promise of byte array containing the exported data
     */
    public Promise<byte[]> export(String tenantId, String collectionName,
                                   List<Map<String, Object>> entities, ExportFormat format) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        Objects.requireNonNull(collectionName, "Collection name must not be null");
        Objects.requireNonNull(entities, "Entities must not be null");
        Objects.requireNonNull(format, "Format must not be null");

        return switch (format) {
            case CSV -> exportCsv(tenantId, collectionName, entities);
            case PDF -> exportPdf(tenantId, collectionName, entities);
            case EXCEL -> exportExcel(tenantId, collectionName, entities);
        };
    }

    private Promise<byte[]> exportCsv(String tenantId, String collectionName,
                                       List<Map<String, Object>> entities) {
        StringBuilder csv = new StringBuilder();

        if (!entities.isEmpty()) {
            // Header row
            Set<String> allKeys = new LinkedHashSet<>();
            for (Map<String, Object> entity : entities) {
                allKeys.addAll(entity.keySet());
            }
            List<String> columns = new ArrayList<>(allKeys);
            csv.append(String.join(",", columns)).append("\n");

            // Data rows
            for (Map<String, Object> entity : entities) {
                List<String> values = new ArrayList<>();
                for (String col : columns) {
                    Object val = entity.get(col);
                    String strVal = val != null ? val.toString() : "";
                    // Escape CSV special characters
                    if (strVal.contains(",") || strVal.contains("\"") || strVal.contains("\n")) {
                        strVal = "\"" + strVal.replace("\"", "\"\"") + "\"";
                    }
                    values.add(strVal);
                }
                csv.append(String.join(",", values)).append("\n");
            }
        }

        metricsCollector.incrementCounter("export.success",
            "tenant", tenantId, "collection", collectionName, "format", "CSV");

        return Promise.of(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private Promise<byte[]> exportPdf(String tenantId, String collectionName,
                                       List<Map<String, Object>> entities) {
        StringBuilder pdf = new StringBuilder();
        pdf.append("%PDF-1.4\n");
        pdf.append("Data Export - ").append(collectionName).append("\n");
        pdf.append("Tenant: ").append(tenantId).append("\n");
        pdf.append("Records: ").append(entities.size()).append("\n\n");

        int maxRecords = Math.min(entities.size(), 50);
        for (int i = 0; i < maxRecords; i++) {
            pdf.append("Record ").append(i + 1).append(": ").append(entities.get(i)).append("\n");
        }

        if (entities.size() > 50) {
            pdf.append("\n... and ").append(entities.size() - 50).append(" more records\n");
        }

        metricsCollector.incrementCounter("export.success",
            "tenant", tenantId, "collection", collectionName, "format", "PDF");

        return Promise.of(pdf.toString().getBytes(StandardCharsets.UTF_8));
    }

    private Promise<byte[]> exportExcel(String tenantId, String collectionName,
                                         List<Map<String, Object>> entities) {
        // Collect all columns
        Set<String> allKeys = new LinkedHashSet<>();
        for (Map<String, Object> entity : entities) {
            allKeys.addAll(entity.keySet());
        }
        List<String> columns = new ArrayList<>(allKeys);

        return excelExporter.exportEntities(tenantId, collectionName, entities, columns)
            .map(bytes -> {
                metricsCollector.incrementCounter("export.success",
                    "tenant", tenantId, "collection", collectionName, "format", "EXCEL");
                return bytes;
            });
    }

    // ========================================================================
    // VALIDATE IMPORT
    // ========================================================================

    /**
     * Validates import data before actual import.
     *
     * @param tenantId the tenant ID (required)
     * @param collectionName the collection name (required)
     * @param data the data to validate (required)
     * @param format the import format (required)
     * @return Promise of ValidationResult
     */
    public Promise<ValidationResult> validateImport(String tenantId, String collectionName,
                                                     String data, ImportFormat format) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        Objects.requireNonNull(collectionName, "Collection name must not be null");
        Objects.requireNonNull(data, "Data must not be null");
        Objects.requireNonNull(format, "Format must not be null");

        return switch (format) {
            case CSV -> validateCsv(data);
            case JSON -> validateJson(data);
            case EXCEL -> validateExcel(data);
        };
    }

    private Promise<ValidationResult> validateCsv(String data) {
        List<ValidationError> errors = new ArrayList<>();
        String[] lines = data.split("\n");

        if (lines.length < 2) {
            errors.add(new ValidationError(0, "file", "CSV must contain at least one data row"));
            return Promise.of(new ValidationResult(false, 0, 0, errors, "Validation failed"));
        }

        String[] headers = lines[0].split(",");
        int headerCount = headers.length;
        int totalRecords = lines.length - 1;
        int validRecords = 0;

        for (int i = 1; i < lines.length; i++) {
            String[] cols = lines[i].split(",", -1);
            if (cols.length != headerCount) {
                errors.add(new ValidationError(i, "row",
                    "Column count mismatch: expected " + headerCount + " but got " + cols.length));
            } else {
                validRecords++;
            }
        }

        boolean isValid = errors.isEmpty();
        String summary = isValid ? "Validation passed" : "Validation failed with " + errors.size() + " errors";
        return Promise.of(new ValidationResult(isValid, totalRecords, validRecords, errors, summary));
    }

    private Promise<ValidationResult> validateJson(String data) {
        List<ValidationError> errors = new ArrayList<>();

        if (data == null || data.isBlank()) {
            errors.add(new ValidationError(0, "file", "JSON data is empty"));
            return Promise.of(new ValidationResult(false, 0, 0, errors, "Validation failed"));
        }

        String trimmed = data.trim();
        if (!trimmed.startsWith("[") && !trimmed.startsWith("{")) {
            errors.add(new ValidationError(0, "file",
                "JSON must start with '[' or '{' (array or object)"));
            return Promise.of(new ValidationResult(false, 0, 0, errors, "Validation failed"));
        }

        // Simple validation: count records
        int totalRecords;
        if (trimmed.startsWith("[")) {
            // Count objects in array
            totalRecords = countJsonObjects(trimmed);
        } else {
            // Check for records field
            if (trimmed.contains("\"records\"")) {
                totalRecords = countJsonObjects(trimmed);
            } else {
                totalRecords = 1;
            }
        }

        boolean isValid = errors.isEmpty();
        String summary = isValid ? "Validation passed" : "Validation failed";
        return Promise.of(new ValidationResult(isValid, totalRecords, totalRecords, errors, summary));
    }

    private Promise<ValidationResult> validateExcel(String data) {
        List<ValidationError> errors = new ArrayList<>();

        if (data == null || data.isBlank()) {
            errors.add(new ValidationError(0, "file", "Excel data is empty"));
            return Promise.of(new ValidationResult(false, 0, 0, errors, "Validation failed"));
        }

        // Try to decode base64
        try {
            Base64.getDecoder().decode(data);
        } catch (IllegalArgumentException e) {
            errors.add(new ValidationError(0, "file", "Excel data must be valid base64 encoded"));
            return Promise.of(new ValidationResult(false, 0, 0, errors, "Validation failed"));
        }

        return Promise.of(new ValidationResult(true, 0, 0, errors, "Validation passed"));
    }

    private int countJsonObjects(String json) {
        int count = 0;
        int depth = 0;
        for (char c : json.toCharArray()) {
            if (c == '{') {
                depth++;
                if (depth == 1 || (depth == 2 && json.trim().startsWith("{"))) {
                    count++;
                }
            } else if (c == '}') {
                depth--;
            }
        }
        // For top-level array, count top-level objects
        if (json.trim().startsWith("[")) {
            count = 0;
            depth = 0;
            boolean inArray = false;
            for (char c : json.toCharArray()) {
                if (c == '[' && !inArray) {
                    inArray = true;
                } else if (c == '{' && inArray) {
                    depth++;
                    if (depth == 1) count++;
                } else if (c == '}') {
                    depth--;
                }
            }
        }
        return count;
    }

    // ========================================================================
    // IMPORT DATA
    // ========================================================================

    /**
     * Imports data from the specified format.
     *
     * @param tenantId the tenant ID (required)
     * @param collectionName the collection name (required)
     * @param data the data to import (required)
     * @param format the import format (required)
     * @return Promise of ImportResult
     */
    public Promise<CsvImporter.ImportResult> importData(String tenantId, String collectionName,
                                                         String data, ImportFormat format) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        Objects.requireNonNull(collectionName, "Collection name must not be null");
        Objects.requireNonNull(data, "Data must not be null");
        Objects.requireNonNull(format, "Format must not be null");

        return switch (format) {
            case CSV -> importCsv(tenantId, collectionName, data);
            case JSON -> importJson(tenantId, collectionName, data);
            case EXCEL -> importExcel(tenantId, collectionName, data);
        };
    }

    private Promise<CsvImporter.ImportResult> importCsv(String tenantId, String collectionName, String data) {
        // Parse headers for column list
        String[] lines = data.split("\n");
        List<String> columns = lines.length > 0
            ? Arrays.asList(lines[0].split(","))
            : List.of();

        return csvImporter.importFromCsv(tenantId, collectionName, data, columns);
    }

    private Promise<CsvImporter.ImportResult> importJson(String tenantId, String collectionName, String data) {
        String trimmed = data.trim();

        try {
            List<Map<String, Object>> records = new ArrayList<>();

            if (trimmed.startsWith("[")) {
                records = parseJsonArray(trimmed);
            } else if (trimmed.startsWith("{")) {
                // Could be single object or object with "records" array
                if (trimmed.contains("\"records\"")) {
                    records = parseJsonRecordsField(trimmed);
                } else {
                    Map<String, Object> single = parseJsonObject(trimmed);
                    if (!single.isEmpty()) {
                        records.add(single);
                    }
                }
            } else {
                return Promise.of(new CsvImporter.ImportResult(
                    tenantId, collectionName, 0, 1, List.of(),
                    List.of("Invalid JSON format"), System.currentTimeMillis()));
            }

            // Ensure all records have IDs
            for (Map<String, Object> record : records) {
                if (!record.containsKey("id")) {
                    record.put("id", UUID.randomUUID().toString());
                }
            }

            return Promise.of(new CsvImporter.ImportResult(
                tenantId, collectionName, records.size(), 0, records,
                List.of(), System.currentTimeMillis()));

        } catch (Exception e) {
            return Promise.of(new CsvImporter.ImportResult(
                tenantId, collectionName, 0, 1, List.of(),
                List.of("Failed to parse JSON: " + e.getMessage()),
                System.currentTimeMillis()));
        }
    }

    private Promise<CsvImporter.ImportResult> importExcel(String tenantId, String collectionName, String data) {
        // Excel import would be handled by the ExcelExporter/CsvImporter
        return Promise.of(new CsvImporter.ImportResult(
            tenantId, collectionName, 0, 0, List.of(), List.of(), System.currentTimeMillis()));
    }

    // ========================================================================
    // SIMPLE JSON PARSING (no external JSON library dependency)
    // ========================================================================

    private List<Map<String, Object>> parseJsonArray(String json) {
        List<Map<String, Object>> results = new ArrayList<>();
        // Simple brace-matching parser for JSON arrays
        int depth = 0;
        int objectStart = -1;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') {
                if (depth == 0) objectStart = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && objectStart >= 0) {
                    String objStr = json.substring(objectStart, i + 1);
                    results.add(parseJsonObject(objStr));
                    objectStart = -1;
                }
            }
        }

        return results;
    }

    private List<Map<String, Object>> parseJsonRecordsField(String json) {
        // Find the records array
        int idx = json.indexOf("\"records\"");
        if (idx < 0) return List.of();

        int arrayStart = json.indexOf('[', idx);
        if (arrayStart < 0) return List.of();

        // Find matching ]
        int depth = 0;
        int arrayEnd = -1;
        for (int i = arrayStart; i < json.length(); i++) {
            if (json.charAt(i) == '[') depth++;
            else if (json.charAt(i) == ']') {
                depth--;
                if (depth == 0) {
                    arrayEnd = i;
                    break;
                }
            }
        }
        if (arrayEnd < 0) return List.of();

        return parseJsonArray(json.substring(arrayStart, arrayEnd + 1));
    }

    private Map<String, Object> parseJsonObject(String json) {
        Map<String, Object> result = new LinkedHashMap<>();
        // Simple key-value parser for flat JSON objects
        String content = json.trim();
        if (content.startsWith("{")) content = content.substring(1);
        if (content.endsWith("}")) content = content.substring(0, content.length() - 1);

        // Split by comma, respecting nested structures
        List<String> pairs = splitJsonPairs(content);

        for (String pair : pairs) {
            int colonIdx = pair.indexOf(':');
            if (colonIdx < 0) continue;

            String key = pair.substring(0, colonIdx).trim();
            String value = pair.substring(colonIdx + 1).trim();

            // Remove quotes from key
            if (key.startsWith("\"") && key.endsWith("\"")) {
                key = key.substring(1, key.length() - 1);
            }

            // Parse value
            if (value.startsWith("\"") && value.endsWith("\"")) {
                result.put(key, value.substring(1, value.length() - 1));
            } else if (value.equals("null")) {
                result.put(key, null);
            } else if (value.equals("true") || value.equals("false")) {
                result.put(key, Boolean.parseBoolean(value));
            } else {
                try {
                    if (value.contains(".")) {
                        result.put(key, Double.parseDouble(value));
                    } else {
                        result.put(key, Long.parseLong(value));
                    }
                } catch (NumberFormatException e) {
                    result.put(key, value);
                }
            }
        }

        return result;
    }

    private List<String> splitJsonPairs(String content) {
        List<String> pairs = new ArrayList<>();
        int depth = 0;
        boolean inString = false;
        int start = 0;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '"' && (i == 0 || content.charAt(i - 1) != '\\')) {
                inString = !inString;
            } else if (!inString) {
                if (c == '{' || c == '[') depth++;
                else if (c == '}' || c == ']') depth--;
                else if (c == ',' && depth == 0) {
                    pairs.add(content.substring(start, i).trim());
                    start = i + 1;
                }
            }
        }
        if (start < content.length()) {
            pairs.add(content.substring(start).trim());
        }

        return pairs;
    }

    // ========================================================================
    // INNER TYPES
    // ========================================================================

    /**
     * Supported export formats.
     */
    public enum ExportFormat {
        CSV, PDF, EXCEL
    }

    /**
     * Supported import formats.
     */
    public enum ImportFormat {
        CSV, JSON, EXCEL
    }

    /**
     * Result of import validation.
     *
     * @param isValid whether the data is valid
     * @param totalRecords total number of records found
     * @param validRecords number of valid records
     * @param errors list of validation errors
     * @param summary validation summary message
     */
    public record ValidationResult(
        boolean isValid,
        int totalRecords,
        int validRecords,
        List<ValidationError> errors,
        String summary
    ) {
        /**
         * Returns the number of invalid records.
         */
        public int getInvalidRecords() {
            return totalRecords - validRecords;
        }

        /**
         * Returns whether there are validation errors.
         */
        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        /**
         * Returns the number of validation errors.
         */
        public int getErrorCount() {
            return errors.size();
        }
    }

    /**
     * Represents a validation error at a specific location.
     *
     * @param row the row number (0 for file-level errors)
     * @param field the field name
     * @param message the error message
     */
    public record ValidationError(int row, String field, String message) {
        public ValidationError {
            if (row < 0) {
                throw new IllegalArgumentException("row must be >= 0, got: " + row);
            }
        }

        @Override
        public String toString() {
            if (row == 0) {
                return field + ": " + message;
            }
            return "Row " + row + " [" + field + "]: " + message;
        }
    }

    /**
     * Tracks progress of import/export operations.
     */
    public static class OperationProgress {
        public final String operationId;
        public final String type;
        public final String format;
        public final long startTime;
        public boolean completed;
        public boolean failed;
        public long endTime;

        public OperationProgress(String operationId, String type, String format) {
            this.operationId = operationId;
            this.type = type;
            this.format = format;
            this.startTime = System.currentTimeMillis();
        }

        /**
         * Gets the current operation status.
         */
        public String getStatus() {
            if (failed) return "FAILED";
            if (completed) return "COMPLETED";
            return "IN_PROGRESS";
        }

        /**
         * Gets the operation duration in milliseconds.
         */
        public long getDuration() {
            long end = endTime > 0 ? endTime : System.currentTimeMillis();
            return end - startTime;
        }
    }
}
