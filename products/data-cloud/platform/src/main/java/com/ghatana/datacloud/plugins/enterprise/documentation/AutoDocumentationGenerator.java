/*
 * Copyright (c) 2025 Ghatana.
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ghatana.datacloud.plugins.enterprise.documentation;

import io.activej.promise.Promise;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.regex.Pattern;

/**
 * Auto-documentation generator for EventCloud datasets.
 *
 * <p>
 * Automatically generates and maintains documentation for datasets:
 * <ul>
 * <li>Schema documentation with column descriptions</li>
 * <li>AI-powered column description inference</li>
 * <li>PII detection and classification</li>
 * <li>Documentation versioning</li>
 * <li>Searchable documentation index</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Auto-documentation generation
 * @doc.layer product
 * @doc.pattern Service
 */
public class AutoDocumentationGenerator {

    /**
     * Common PII patterns for detection.
     */
    private static final Map<String, Pattern> PII_PATTERNS = Map.of(
            "email", Pattern.compile("^.*(_email|email_|e_mail).*$", Pattern.CASE_INSENSITIVE),
            "phone", Pattern.compile("^.*(phone|mobile|cell|tel).*$", Pattern.CASE_INSENSITIVE),
            "ssn", Pattern.compile("^.*(ssn|social_security|sin).*$", Pattern.CASE_INSENSITIVE),
            "credit_card", Pattern.compile("^.*(card_number|cc_num|credit_card).*$", Pattern.CASE_INSENSITIVE),
            "address", Pattern.compile("^.*(address|street|city|zip|postal).*$", Pattern.CASE_INSENSITIVE),
            "name", Pattern.compile("^.*(first_name|last_name|full_name|customer_name|user_name).*$", Pattern.CASE_INSENSITIVE),
            "ip", Pattern.compile("^.*(ip_address|client_ip|source_ip).*$", Pattern.CASE_INSENSITIVE),
            "password", Pattern.compile("^.*(password|passwd|pwd|secret).*$", Pattern.CASE_INSENSITIVE)
    );

    /**
     * Common column name patterns for description inference.
     */
    private static final Map<Pattern, String> COLUMN_DESCRIPTIONS = Map.ofEntries(
            Map.entry(Pattern.compile("^id$", Pattern.CASE_INSENSITIVE), "Unique identifier"),
            Map.entry(Pattern.compile("^.*_id$", Pattern.CASE_INSENSITIVE), "Foreign key reference to %s"),
            Map.entry(Pattern.compile("^created_at$", Pattern.CASE_INSENSITIVE), "Timestamp when the record was created"),
            Map.entry(Pattern.compile("^updated_at$", Pattern.CASE_INSENSITIVE), "Timestamp when the record was last updated"),
            Map.entry(Pattern.compile("^deleted_at$", Pattern.CASE_INSENSITIVE), "Soft delete timestamp"),
            Map.entry(Pattern.compile("^is_.*$", Pattern.CASE_INSENSITIVE), "Boolean flag indicating whether the entity %s"),
            Map.entry(Pattern.compile("^has_.*$", Pattern.CASE_INSENSITIVE), "Boolean flag indicating whether the entity %s"),
            Map.entry(Pattern.compile("^.*_count$", Pattern.CASE_INSENSITIVE), "Count of associated %s"),
            Map.entry(Pattern.compile("^.*_amount$", Pattern.CASE_INSENSITIVE), "Monetary amount for %s"),
            Map.entry(Pattern.compile("^.*_total$", Pattern.CASE_INSENSITIVE), "Total value for %s"),
            Map.entry(Pattern.compile("^status$", Pattern.CASE_INSENSITIVE), "Current status of the record"),
            Map.entry(Pattern.compile("^type$", Pattern.CASE_INSENSITIVE), "Type classification"),
            Map.entry(Pattern.compile("^name$", Pattern.CASE_INSENSITIVE), "Display name"),
            Map.entry(Pattern.compile("^description$", Pattern.CASE_INSENSITIVE), "Detailed description"),
            Map.entry(Pattern.compile("^email$", Pattern.CASE_INSENSITIVE), "Email address"),
            Map.entry(Pattern.compile("^timestamp$", Pattern.CASE_INSENSITIVE), "Event timestamp"),
            Map.entry(Pattern.compile("^version$", Pattern.CASE_INSENSITIVE), "Version number for optimistic locking"),
            Map.entry(Pattern.compile("^tenant_id$", Pattern.CASE_INSENSITIVE), "Multi-tenant organization identifier")
    );

    /**
     * Documentation storage.
     */
    private final Map<String, DatasetDocumentation> documentationStore;

    /**
     * Version history storage.
     */
    private final Map<String, List<DocumentationVersion>> versionHistory;

    /**
     * Full-text search index.
     */
    private final Map<String, List<String>> searchIndex;

    /**
     * Creates a new auto-documentation generator.
     */
    public AutoDocumentationGenerator() {
        this.documentationStore = new ConcurrentHashMap<>();
        this.versionHistory = new ConcurrentHashMap<>();
        this.searchIndex = new ConcurrentHashMap<>();
    }

    /**
     * Generates documentation for a dataset schema.
     *
     * @param datasetId Dataset identifier
     * @param datasetName Human-readable name
     * @param schema Schema definition
     * @param metadata Additional metadata
     * @return Promise of generated documentation
     */
    public Promise<DatasetDocumentation> generateDocumentation(
            String datasetId,
            String datasetName,
            SchemaDefinition schema,
            Map<String, String> metadata) {

        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            // Generate column documentation
            List<ColumnDocumentation> columnDocs = new ArrayList<>();
            for (SchemaColumn column : schema.getColumns()) {
                ColumnDocumentation columnDoc = generateColumnDocumentation(column);
                columnDocs.add(columnDoc);
            }

            // Calculate PII summary
            long piiColumnCount = columnDocs.stream()
                    .filter(ColumnDocumentation::isPii)
                    .count();

            // Build documentation
            DatasetDocumentation doc = DatasetDocumentation.builder()
                    .documentationId(UUID.randomUUID().toString())
                    .datasetId(datasetId)
                    .datasetName(datasetName)
                    .description(inferDatasetDescription(datasetName, schema))
                    .version(1)
                    .columns(columnDocs)
                    .metadata(metadata != null ? new HashMap<>(metadata) : new HashMap<>())
                    .totalColumns(columnDocs.size())
                    .piiColumns((int) piiColumnCount)
                    .hasPii(piiColumnCount > 0)
                    .build();

            // Store documentation
            documentationStore.put(datasetId, doc);

            // Store version
            storeVersion(datasetId, doc);

            // Index for search
            indexDocumentation(doc);

            return doc;
        });
    }

    /**
     * Updates existing documentation with new schema.
     *
     * @param datasetId Dataset identifier
     * @param newSchema Updated schema
     * @return Promise of updated documentation
     */
    public Promise<DatasetDocumentation> updateDocumentation(
            String datasetId,
            SchemaDefinition newSchema) {

        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            DatasetDocumentation existing = documentationStore.get(datasetId);
            if (existing == null) {
                throw new IllegalArgumentException("Documentation not found for: " + datasetId);
            }

            // Preserve existing descriptions where columns match
            Map<String, ColumnDocumentation> existingColumns = new HashMap<>();
            for (ColumnDocumentation col : existing.getColumns()) {
                existingColumns.put(col.getColumnName(), col);
            }

            List<ColumnDocumentation> updatedColumns = new ArrayList<>();
            for (SchemaColumn column : newSchema.getColumns()) {
                ColumnDocumentation existingCol = existingColumns.get(column.getName());
                if (existingCol != null && !existingCol.isAutoGenerated()) {
                    // Preserve manual descriptions
                    updatedColumns.add(existingCol.withDataType(column.getDataType()));
                } else {
                    // Generate new documentation
                    updatedColumns.add(generateColumnDocumentation(column));
                }
            }

            long piiColumnCount = updatedColumns.stream()
                    .filter(ColumnDocumentation::isPii)
                    .count();

            DatasetDocumentation updated = existing.toBuilder()
                    .documentationId(UUID.randomUUID().toString())
                    .version(existing.getVersion() + 1)
                    .columns(updatedColumns)
                    .totalColumns(updatedColumns.size())
                    .piiColumns((int) piiColumnCount)
                    .hasPii(piiColumnCount > 0)
                    .updatedAt(Instant.now())
                    .build();

            documentationStore.put(datasetId, updated);
            storeVersion(datasetId, updated);
            indexDocumentation(updated);

            return updated;
        });
    }

    /**
     * Manually updates a column description.
     *
     * @param datasetId Dataset identifier
     * @param columnName Column name
     * @param description New description
     * @param updatedBy User who made the update
     * @return Promise of updated documentation
     */
    public Promise<DatasetDocumentation> updateColumnDescription(
            String datasetId,
            String columnName,
            String description,
            String updatedBy) {

        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            DatasetDocumentation existing = documentationStore.get(datasetId);
            if (existing == null) {
                throw new IllegalArgumentException("Documentation not found for: " + datasetId);
            }

            List<ColumnDocumentation> updatedColumns = new ArrayList<>();
            boolean found = false;

            for (ColumnDocumentation col : existing.getColumns()) {
                if (col.getColumnName().equals(columnName)) {
                    found = true;
                    updatedColumns.add(col.toBuilder()
                            .description(description)
                            .autoGenerated(false)
                            .lastUpdatedBy(updatedBy)
                            .lastUpdatedAt(Instant.now())
                            .build());
                } else {
                    updatedColumns.add(col);
                }
            }

            if (!found) {
                throw new IllegalArgumentException("Column not found: " + columnName);
            }

            DatasetDocumentation updated = existing.toBuilder()
                    .documentationId(UUID.randomUUID().toString())
                    .version(existing.getVersion() + 1)
                    .columns(updatedColumns)
                    .updatedAt(Instant.now())
                    .build();

            documentationStore.put(datasetId, updated);
            storeVersion(datasetId, updated);
            indexDocumentation(updated);

            return updated;
        });
    }

    /**
     * Gets documentation for a dataset.
     *
     * @param datasetId Dataset identifier
     * @return Promise of documentation or null
     */
    public Promise<DatasetDocumentation> getDocumentation(String datasetId) {
        return Promise.of(documentationStore.get(datasetId));
    }

    /**
     * Gets documentation version history.
     *
     * @param datasetId Dataset identifier
     * @return Promise of version history
     */
    public Promise<List<DocumentationVersion>> getVersionHistory(String datasetId) {
        return Promise.of(versionHistory.getOrDefault(datasetId, List.of()));
    }

    /**
     * Searches documentation by keyword.
     *
     * @param query Search query
     * @param maxResults Maximum results to return
     * @return Promise of search results
     */
    public Promise<List<SearchResult>> search(String query, int maxResults) {
        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            String lowerQuery = query.toLowerCase();
            List<SearchResult> results = new ArrayList<>();

            for (Map.Entry<String, List<String>> entry : searchIndex.entrySet()) {
                String datasetId = entry.getKey();
                List<String> indexedTerms = entry.getValue();

                for (String term : indexedTerms) {
                    if (term.toLowerCase().contains(lowerQuery)) {
                        DatasetDocumentation doc = documentationStore.get(datasetId);
                        if (doc != null) {
                            results.add(SearchResult.builder()
                                    .datasetId(datasetId)
                                    .datasetName(doc.getDatasetName())
                                    .matchedTerm(term)
                                    .relevanceScore(calculateRelevance(term, lowerQuery))
                                    .build());
                            break; // One result per dataset
                        }
                    }
                }
            }

            // Sort by relevance
            results.sort((a, b) -> Double.compare(b.getRelevanceScore(), a.getRelevanceScore()));

            return results.size() > maxResults ? results.subList(0, maxResults) : results;
        });
    }

    /**
     * Exports documentation in Markdown format.
     *
     * @param datasetId Dataset identifier
     * @return Promise of Markdown documentation
     */
    public Promise<String> exportAsMarkdown(String datasetId) {
        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            DatasetDocumentation doc = documentationStore.get(datasetId);
            if (doc == null) {
                return null;
            }

            StringBuilder md = new StringBuilder();

            // Header
            md.append("# ").append(doc.getDatasetName()).append("\n\n");
            md.append("**Description**: ").append(doc.getDescription()).append("\n\n");

            if (doc.getMetadata().containsKey("owner")) {
                md.append("**Owner**: ").append(doc.getMetadata().get("owner")).append("\n");
            }
            if (doc.getMetadata().containsKey("sla")) {
                md.append("**SLA**: ").append(doc.getMetadata().get("sla")).append("\n");
            }

            md.append("\n");

            // PII Warning
            if (doc.isHasPii()) {
                md.append("⚠️ **This dataset contains PII** (")
                        .append(doc.getPiiColumns())
                        .append(" columns)\n\n");
            }

            // Schema Table
            md.append("## Schema\n\n");
            md.append("| Column | Type | Description | PII | Nullable |\n");
            md.append("|--------|------|-------------|-----|----------|\n");

            for (ColumnDocumentation col : doc.getColumns()) {
                md.append("| `").append(col.getColumnName()).append("` ");
                md.append("| ").append(col.getDataType()).append(" ");
                md.append("| ").append(col.getDescription()).append(" ");
                md.append("| ").append(col.isPii() ? "⚠️ Yes" : "No").append(" ");
                md.append("| ").append(col.isNullable() ? "Yes" : "No").append(" |\n");
            }

            // Footer
            md.append("\n---\n");
            md.append("*Generated by EventCloud Auto-Documentation*\n");
            md.append("*Version ").append(doc.getVersion())
                    .append(" | Updated: ").append(doc.getUpdatedAt()).append("*\n");

            return md.toString();
        });
    }

    // --- Private Helper Methods ---
    private ColumnDocumentation generateColumnDocumentation(SchemaColumn column) {
        String description = inferColumnDescription(column.getName());
        String piiType = detectPiiType(column.getName());

        return ColumnDocumentation.builder()
                .columnName(column.getName())
                .dataType(column.getDataType())
                .description(description)
                .nullable(column.isNullable())
                .pii(piiType != null)
                .piiType(piiType)
                .autoGenerated(true)
                .build();
    }

    private String inferColumnDescription(String columnName) {
        for (Map.Entry<Pattern, String> entry : COLUMN_DESCRIPTIONS.entrySet()) {
            if (entry.getKey().matcher(columnName).matches()) {
                String template = entry.getValue();
                // Handle templates with placeholders
                if (template.contains("%s")) {
                    String entity = extractEntityFromColumnName(columnName);
                    return String.format(template, entity);
                }
                return template;
            }
        }
        // Default description
        return "Field: " + columnName.replace("_", " ");
    }

    private String inferDatasetDescription(String datasetName, SchemaDefinition schema) {
        StringBuilder desc = new StringBuilder();
        desc.append("Dataset containing ");
        desc.append(datasetName.replace("_", " ").toLowerCase());
        desc.append(" data with ");
        desc.append(schema.getColumns().size());
        desc.append(" columns");

        long timestampCols = schema.getColumns().stream()
                .filter(c -> c.getDataType().toLowerCase().contains("timestamp"))
                .count();
        if (timestampCols > 0) {
            desc.append(", including timestamp fields for temporal analysis");
        }

        return desc.toString();
    }

    private String detectPiiType(String columnName) {
        for (Map.Entry<String, Pattern> entry : PII_PATTERNS.entrySet()) {
            if (entry.getValue().matcher(columnName).matches()) {
                return entry.getKey();
            }
        }
        return null;
    }

    private String extractEntityFromColumnName(String columnName) {
        // Extract entity from patterns like "user_id" -> "user"
        if (columnName.endsWith("_id")) {
            return columnName.substring(0, columnName.length() - 3).replace("_", " ");
        }
        if (columnName.startsWith("is_")) {
            return columnName.substring(3).replace("_", " ");
        }
        if (columnName.startsWith("has_")) {
            return columnName.substring(4).replace("_", " ");
        }
        if (columnName.endsWith("_count")) {
            return columnName.substring(0, columnName.length() - 6).replace("_", " ");
        }
        if (columnName.endsWith("_amount") || columnName.endsWith("_total")) {
            int idx = columnName.lastIndexOf("_");
            return columnName.substring(0, idx).replace("_", " ");
        }
        return columnName.replace("_", " ");
    }

    private void storeVersion(String datasetId, DatasetDocumentation doc) {
        DocumentationVersion version = DocumentationVersion.builder()
                .versionId(doc.getDocumentationId())
                .version(doc.getVersion())
                .createdAt(Instant.now())
                .columnCount(doc.getTotalColumns())
                .build();

        versionHistory.computeIfAbsent(datasetId, k -> new ArrayList<>()).add(version);
    }

    private void indexDocumentation(DatasetDocumentation doc) {
        List<String> terms = new ArrayList<>();
        terms.add(doc.getDatasetName());
        terms.add(doc.getDescription());

        for (ColumnDocumentation col : doc.getColumns()) {
            terms.add(col.getColumnName());
            terms.add(col.getDescription());
        }

        searchIndex.put(doc.getDatasetId(), terms);
    }

    private double calculateRelevance(String term, String query) {
        if (term.toLowerCase().equals(query)) {
            return 1.0;
        }
        if (term.toLowerCase().startsWith(query)) {
            return 0.8;
        }
        return 0.5;
    }

    // --- Inner Classes ---
    /**
     * Schema definition input.
     */
    @Getter
    @Builder
    public static class SchemaDefinition {

        @Builder.Default
        private final List<SchemaColumn> columns = List.of();
    }

    /**
     * Schema column definition.
     */
    @Getter
    @Builder
    public static class SchemaColumn {

        private final String name;
        private final String dataType;
        @Builder.Default
        private final boolean nullable = true;
    }

    /**
     * Complete dataset documentation.
     */
    @Getter
    @Builder(toBuilder = true)
    public static class DatasetDocumentation {

        private final String documentationId;
        private final String datasetId;
        private final String datasetName;
        private final String description;
        private final int version;
        @Builder.Default
        private final List<ColumnDocumentation> columns = List.of();
        @Builder.Default
        private final Map<String, String> metadata = Map.of();
        private final int totalColumns;
        private final int piiColumns;
        private final boolean hasPii;
        @Builder.Default
        private final Instant createdAt = Instant.now();
        @Builder.Default
        private final Instant updatedAt = Instant.now();
    }

    /**
     * Column documentation.
     */
    @Getter
    @Builder(toBuilder = true)
    public static class ColumnDocumentation {

        private final String columnName;
        private final String dataType;
        private final String description;
        private final boolean nullable;
        private final boolean pii;
        private final String piiType;
        @Builder.Default
        private final boolean autoGenerated = true;
        private final String lastUpdatedBy;
        @Builder.Default
        private final Instant lastUpdatedAt = Instant.now();

        public ColumnDocumentation withDataType(String newDataType) {
            return this.toBuilder().dataType(newDataType).build();
        }
    }

    /**
     * Documentation version record.
     */
    @Getter
    @Builder
    public static class DocumentationVersion {

        private final String versionId;
        private final int version;
        private final Instant createdAt;
        private final int columnCount;
    }

    /**
     * Search result.
     */
    @Getter
    @Builder
    public static class SearchResult {

        private final String datasetId;
        private final String datasetName;
        private final String matchedTerm;
        private final double relevanceScore;
    }
}
