/*
 * Copyright (c) 2025 Ghatana Platform Contributors
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

package com.ghatana.yappc.domain.pageartifact;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Domain model for a page artifact document.
 * <p>
 * Represents the serialized state of a page artifact including the BuilderDocument,
 * metadata, sync status, trust level, and governance records.
 *
 * @doc.type class
 * @doc.purpose Domain model for page artifact persistence
 * @doc.layer product
 * @doc.pattern Domain Model
 */
public final class PageArtifactDocument {

    private final String artifactId;
    private final String documentId;
    private final String name;
    private final String createdBy;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final String syncStatus;
    private final String trustLevel;
    private final String dataClassification;
    private final Map<String, Object> builderDocument;
    private final ValidationSummary validationSummary;
    private final List<GovernanceRecord> aiChangeRecords;
    private final List<OperationRecord> operationLog;
    private final String source;
    private final int residualIslandCount;
    private final double roundTripFidelity;

    @JsonCreator
    public PageArtifactDocument(
            @JsonProperty("artifactId") @NotNull String artifactId,
            @JsonProperty("documentId") @NotNull String documentId,
            @JsonProperty("name") @NotNull String name,
            @JsonProperty("createdBy") @NotNull String createdBy,
            @JsonProperty("createdAt") @NotNull Instant createdAt,
            @JsonProperty("updatedAt") @NotNull Instant updatedAt,
            @JsonProperty("syncStatus") @NotNull String syncStatus,
            @JsonProperty("trustLevel") String trustLevel,
            @JsonProperty("dataClassification") String dataClassification,
            @JsonProperty("builderDocument") @NotNull Map<String, Object> builderDocument,
            @JsonProperty("validationSummary") ValidationSummary validationSummary,
            @JsonProperty("aiChangeRecords") List<GovernanceRecord> aiChangeRecords,
            @JsonProperty("source") String source,
            @JsonProperty("residualIslandCount") int residualIslandCount,
            @JsonProperty("roundTripFidelity") double roundTripFidelity,
            @JsonProperty("operationLog") List<OperationRecord> operationLog
    ) {
        this.artifactId = Objects.requireNonNull(artifactId, "artifactId is required");
        this.documentId = Objects.requireNonNull(documentId, "documentId is required");
        this.name = Objects.requireNonNull(name, "name is required");
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy is required");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt is required");
        this.syncStatus = Objects.requireNonNull(syncStatus, "syncStatus is required");
        this.trustLevel = trustLevel != null ? trustLevel : "UNKNOWN";
        this.dataClassification = dataClassification != null ? dataClassification : "UNCLASSIFIED";
        this.builderDocument = Objects.requireNonNull(builderDocument, "builderDocument is required");
        this.validationSummary = validationSummary;
        this.aiChangeRecords = aiChangeRecords != null ? aiChangeRecords : List.of();
        this.operationLog = operationLog != null ? operationLog : List.of();
        this.source = source;
        this.residualIslandCount = residualIslandCount;
        this.roundTripFidelity = roundTripFidelity;
    }

    public PageArtifactDocument(
            @NotNull String artifactId,
            @NotNull String documentId,
            @NotNull String name,
            @NotNull String createdBy,
            @NotNull Instant createdAt,
            @NotNull Instant updatedAt,
            @NotNull String syncStatus,
            String trustLevel,
            String dataClassification,
            @NotNull Map<String, Object> builderDocument,
            ValidationSummary validationSummary,
            List<GovernanceRecord> aiChangeRecords,
            String source,
            int residualIslandCount,
            double roundTripFidelity
    ) {
        this(
                artifactId,
                documentId,
                name,
                createdBy,
                createdAt,
                updatedAt,
                syncStatus,
                trustLevel,
                dataClassification,
                builderDocument,
                validationSummary,
                aiChangeRecords,
                source,
                residualIslandCount,
                roundTripFidelity,
                List.of()
        );
    }

    @NotNull
    @JsonProperty("artifactId")
    public String artifactId() {
        return artifactId;
    }

    @NotNull
    @JsonProperty("documentId")
    public String documentId() {
        return documentId;
    }

    @NotNull
    @JsonProperty("name")
    public String name() {
        return name;
    }

    @NotNull
    @JsonProperty("createdBy")
    public String createdBy() {
        return createdBy;
    }

    @NotNull
    @JsonProperty("createdAt")
    public Instant createdAt() {
        return createdAt;
    }

    @NotNull
    @JsonProperty("updatedAt")
    public Instant updatedAt() {
        return updatedAt;
    }

    @NotNull
    @JsonProperty("syncStatus")
    public String syncStatus() {
        return syncStatus;
    }

    @JsonProperty("trustLevel")
    public String trustLevel() {
        return trustLevel;
    }

    @JsonProperty("dataClassification")
    public String dataClassification() {
        return dataClassification;
    }

    @NotNull
    @JsonProperty("builderDocument")
    public Map<String, Object> builderDocument() {
        return builderDocument;
    }

    @JsonProperty("validationSummary")
    public ValidationSummary validationSummary() {
        return validationSummary;
    }

    @JsonProperty("aiChangeRecords")
    public List<GovernanceRecord> aiChangeRecords() {
        return aiChangeRecords;
    }

    @JsonProperty("operationLog")
    public List<OperationRecord> operationLog() {
        return operationLog;
    }

    @JsonProperty("source")
    public String source() {
        return source;
    }

    @JsonProperty("residualIslandCount")
    public int residualIslandCount() {
        return residualIslandCount;
    }

    @JsonProperty("roundTripFidelity")
    public double roundTripFidelity() {
        return roundTripFidelity;
    }

    /**
     * Validation summary for the page artifact.
     */
    public record ValidationSummary(
            boolean valid,
            int errorCount,
            int warningCount
    ) {
        @JsonCreator
        public ValidationSummary {
        }
    }

    /**
     * Governance record for tracking changes (formerly AI change records).
     */
    public record GovernanceRecord(
            String artifactId,
            String documentId,
            GovernanceLineage lineage
    ) {
        @JsonCreator
        public GovernanceRecord {
        }
    }

    /**
     * Lineage information for governance tracking.
     */
    public record GovernanceLineage(
            String actionId,
            String hookKind,
            String reason,
            double confidence,
            boolean reversible,
            String reviewState,
            List<String> affectedNodeIds,
            String appliedAt,
            List<String> evidence,
            Map<String, Object> rollbackMetadata
    ) {
        @JsonCreator
        public GovernanceLineage {
            affectedNodeIds = affectedNodeIds != null ? List.copyOf(affectedNodeIds) : List.of();
            evidence = evidence != null ? List.copyOf(evidence) : List.of();
            rollbackMetadata = rollbackMetadata != null
                    ? java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(rollbackMetadata))
                    : Map.of();
        }

        public GovernanceLineage(
                String actionId,
                String hookKind,
                String reason,
                double confidence,
                boolean reversible,
                String reviewState,
                List<String> affectedNodeIds,
                String appliedAt,
                List<String> evidence
        ) {
            this(
                    actionId,
                    hookKind,
                    reason,
                    confidence,
                    reversible,
                    reviewState,
                    affectedNodeIds,
                    appliedAt,
                    evidence,
                    Map.of()
            );
        }
    }

    /**
     * Page artifact operation record used for replay and audit export.
     */
    public record OperationRecord(
            String id,
            String artifactId,
            String documentId,
            String operation,
            String status,
            String actor,
            String summary,
            String createdAt,
            String phase,
            Map<String, Object> metadata
    ) {
        @JsonCreator
        public OperationRecord {
            metadata = metadata != null
                    ? java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(metadata))
                    : Map.of();
        }
    }

    /**
     * Deterministic replay/export view of the operation log.
     */
    public record OperationLogExport(
            int schemaVersion,
            String artifactId,
            String documentId,
            String exportedAt,
            String replayCursor,
            OperationLogSummary summary,
            List<OperationRecord> records
    ) {
        @JsonCreator
        public OperationLogExport {
            records = records != null ? List.copyOf(records) : List.of();
        }
    }

    /**
     * Summary counts for a page artifact operation log export.
     */
    public record OperationLogSummary(
            int total,
            Map<String, Long> byOperation,
            Map<String, Long> byStatus,
            String latestOperationAt
    ) {
        @JsonCreator
        public OperationLogSummary {
            byOperation = byOperation != null ? Map.copyOf(byOperation) : Map.of();
            byStatus = byStatus != null ? Map.copyOf(byStatus) : Map.of();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PageArtifactDocument that = (PageArtifactDocument) o;
        return artifactId.equals(that.artifactId) && documentId.equals(that.documentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(artifactId, documentId);
    }

    @Override
    public String toString() {
        return "PageArtifactDocument{" +
                "artifactId='" + artifactId + '\'' +
                ", documentId='" + documentId + '\'' +
                ", name='" + name + '\'' +
                ", syncStatus='" + syncStatus + '\'' +
                '}';
    }
}
