/**
 * Canonical Page Document Envelope
 * 
 * Single source of truth for page document structure.
 * Defines the canonical schema for page documents that are persisted and shared across
 * the YAPPC platform. All page-related operations must use this schema as the authoritative
 * contract.
 * 
 * @doc.type class
 * @doc.purpose Canonical page document envelope schema
 * @doc.layer product
 * @doc.pattern DTO
 */

package com.ghatana.yappc.api;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Canonical page document envelope representing the complete state of a page artifact.
 * This is the authoritative schema for all page persistence and operations.
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.NONE, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public final class PageDocumentEnvelope {

    private final String id;
    private final String artifactId;
    private final String projectId;
    private final String workspaceId;
    private final String tenantId;
    private final String version;
    private final PageMetadata metadata;
    private final PageDocument document;
    private final PageDocumentState state;
    private final List<PageOperationRecord> operationLog;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final String createdBy;
    private final String updatedBy;
    private final Long revision;

    @JsonCreator
    public PageDocumentEnvelope(
            @JsonProperty("id") @NotNull String id,
            @JsonProperty("artifactId") @NotNull String artifactId,
            @JsonProperty("projectId") @NotNull String projectId,
            @JsonProperty("workspaceId") @NotNull String workspaceId,
            @JsonProperty("tenantId") @NotNull String tenantId,
            @JsonProperty("version") @NotNull String version,
            @JsonProperty("metadata") @NotNull PageMetadata metadata,
            @JsonProperty("document") @NotNull PageDocument document,
            @JsonProperty("state") @NotNull PageDocumentState state,
            @JsonProperty("operationLog") @NotNull List<PageOperationRecord> operationLog,
            @JsonProperty("createdAt") @NotNull Instant createdAt,
            @JsonProperty("updatedAt") @NotNull Instant updatedAt,
            @JsonProperty("createdBy") @NotNull String createdBy,
            @JsonProperty("updatedBy") @NotNull String updatedBy,
            @JsonProperty("revision") @NotNull Long revision
    ) {
        this.id = id;
        this.artifactId = artifactId;
        this.projectId = projectId;
        this.workspaceId = workspaceId;
        this.tenantId = tenantId;
        this.version = version;
        this.metadata = metadata;
        this.document = document;
        this.state = state;
        this.operationLog = operationLog;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
        this.revision = revision;
    }

    @JsonProperty("id")
    public String id() {
        return id;
    }

    @JsonProperty("artifactId")
    public String artifactId() {
        return artifactId;
    }

    @JsonProperty("projectId")
    public String projectId() {
        return projectId;
    }

    @JsonProperty("workspaceId")
    public String workspaceId() {
        return workspaceId;
    }

    @JsonProperty("tenantId")
    public String tenantId() {
        return tenantId;
    }

    @JsonProperty("version")
    public String version() {
        return version;
    }

    @JsonProperty("metadata")
    public PageMetadata metadata() {
        return metadata;
    }

    @JsonProperty("document")
    public PageDocument document() {
        return document;
    }

    @JsonProperty("state")
    public PageDocumentState state() {
        return state;
    }

    @JsonProperty("operationLog")
    public List<PageOperationRecord> operationLog() {
        return operationLog;
    }

    @JsonProperty("createdAt")
    public Instant createdAt() {
        return createdAt;
    }

    @JsonProperty("updatedAt")
    public Instant updatedAt() {
        return updatedAt;
    }

    @JsonProperty("createdBy")
    public String createdBy() {
        return createdBy;
    }

    @JsonProperty("updatedBy")
    public String updatedBy() {
        return updatedBy;
    }

    @JsonProperty("revision")
    public Long revision() {
        return revision;
    }

    /**
     * Page metadata containing descriptive and governance information.
     */
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.NONE, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    public record PageMetadata(
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("lifecyclePhase") String lifecyclePhase,
            @JsonProperty("tags") Set<String> tags,
            @JsonProperty("customProperties") Map<String, String> customProperties
    ) {
        @JsonCreator
        public PageMetadata {
        }
    }

    /**
     * Page document containing the actual builder document data.
     */
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.NONE, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    public record PageDocument(
            @JsonProperty("documentId") String documentId,
            @JsonProperty("serializedDocument") String serializedDocument,
            @JsonProperty("format") DocumentFormat format,
            @JsonProperty("validation") DocumentValidation validation,
            @JsonProperty("fidelity") DocumentFidelity fidelity
    ) {
        @JsonCreator
        public PageDocument {
        }

        public enum DocumentFormat {
            BUILDER_JSON,
            SERIALIZED
        }
    }

    /**
     * Document validation result.
     */
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.NONE, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    public record DocumentValidation(
            @JsonProperty("isValid") boolean isValid,
            @JsonProperty("errors") List<String> errors,
            @JsonProperty("warnings") List<String> warnings,
            @JsonProperty("validatedAt") Instant validatedAt
    ) {
        @JsonCreator
        public DocumentValidation {
        }
    }

    /**
     * Document fidelity metrics for round-trip serialization.
     */
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.NONE, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    public record DocumentFidelity(
            @JsonProperty("roundTripSuccessful") boolean roundTripSuccessful,
            @JsonProperty("fidelityScore") double fidelityScore,
            @JsonProperty("fidelityIssues") List<String> fidelityIssues,
            @JsonProperty("measuredAt") Instant measuredAt
    ) {
        @JsonCreator
        public DocumentFidelity {
        }
    }

    /**
     * Page document state representing the overall document status.
     */
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.NONE, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    public record PageDocumentState(
            @JsonProperty("status") DocumentStatus status,
            @JsonProperty("isDirty") boolean isDirty,
            @JsonProperty("isValid") boolean isValid,
            @JsonProperty("isLocked") boolean isLocked,
            @JsonProperty("lockedBy") String lockedBy,
            @JsonProperty("lockedAt") Instant lockedAt,
            @JsonProperty("validationErrors") List<String> validationErrors
    ) {
        @JsonCreator
        public PageDocumentState {
        }

        public enum DocumentStatus {
            DRAFT,
            ACTIVE,
            ARCHIVED,
            LOCKED
        }
    }

    /**
     * Page operation record for audit logging.
     */
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.NONE, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    public record PageOperationRecord(
            @JsonProperty("id") String id,
            @JsonProperty("operationType") String operationType,
            @JsonProperty("status") OperationStatus status,
            @JsonProperty("actorId") String actorId,
            @JsonProperty("actorName") String actorName,
            @JsonProperty("summary") String summary,
            @JsonProperty("phase") String phase,
            @JsonProperty("metadata") Map<String, String> metadata,
            @JsonProperty("createdAt") Instant createdAt
    ) {
        @JsonCreator
        public PageOperationRecord {
        }

        public enum OperationStatus {
            PENDING,
            SUCCEEDED,
            FAILED,
            REQUIRES_REVIEW
        }
    }
}
