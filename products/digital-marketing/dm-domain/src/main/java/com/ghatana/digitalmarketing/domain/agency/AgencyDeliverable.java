package com.ghatana.digitalmarketing.domain.agency;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable entity representing a deliverable for an agency client.
 *
 * @doc.type class
 * @doc.purpose Agency deliverable for tracking client work (P3-002)
 * @doc.layer product
 * @doc.pattern Entity
 */
public final class AgencyDeliverable {

    private final String id;
    private final String contractId;
    private final String agencyTenantId;
    private final String clientId;
    private final String deliverableType;
    private final String title;
    private final String description;
    private final LocalDate dueDate;
    private final LocalDate completedDate;
    private final String assignedTo;
    private final Map<String, Object> metadata;
    private final AgencyDeliverableStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;

    private AgencyDeliverable(Builder builder) {
        this.id = builder.id;
        this.contractId = builder.contractId;
        this.agencyTenantId = builder.agencyTenantId;
        this.clientId = builder.clientId;
        this.deliverableType = builder.deliverableType;
        this.title = builder.title;
        this.description = builder.description;
        this.dueDate = builder.dueDate;
        this.completedDate = builder.completedDate;
        this.assignedTo = builder.assignedTo;
        this.metadata = Map.copyOf(builder.metadata);
        this.status = builder.status;
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
    }

    public AgencyDeliverable start() {
        if (status != AgencyDeliverableStatus.PENDING) {
            throw new IllegalStateException("Cannot start deliverable in status: " + status);
        }
        return toBuilder()
            .status(AgencyDeliverableStatus.IN_PROGRESS)
            .updatedAt(Instant.now())
            .build();
    }

    public AgencyDeliverable complete() {
        if (status != AgencyDeliverableStatus.IN_PROGRESS) {
            throw new IllegalStateException("Cannot complete deliverable in status: " + status);
        }
        return toBuilder()
            .status(AgencyDeliverableStatus.COMPLETED)
            .completedDate(LocalDate.now())
            .updatedAt(Instant.now())
            .build();
    }

    public AgencyDeliverable reject(String reason) {
        if (status != AgencyDeliverableStatus.SUBMITTED && status != AgencyDeliverableStatus.IN_REVIEW) {
            throw new IllegalStateException("Cannot reject deliverable in status: " + status);
        }
        return toBuilder()
            .status(AgencyDeliverableStatus.REJECTED)
            .updatedAt(Instant.now())
            .build();
    }

    public AgencyDeliverable submitForReview() {
        if (status != AgencyDeliverableStatus.IN_PROGRESS) {
            throw new IllegalStateException("Cannot submit deliverable in status: " + status);
        }
        return toBuilder()
            .status(AgencyDeliverableStatus.SUBMITTED)
            .updatedAt(Instant.now())
            .build();
    }

    public boolean isOverdue() {
        return dueDate != null && LocalDate.now().isAfter(dueDate) && status != AgencyDeliverableStatus.COMPLETED;
    }

    public String getId() { return id; }
    public String getContractId() { return contractId; }
    public String getAgencyTenantId() { return agencyTenantId; }
    public String getClientId() { return clientId; }
    public String getDeliverableType() { return deliverableType; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public LocalDate getDueDate() { return dueDate; }
    public LocalDate getCompletedDate() { return completedDate; }
    public String getAssignedTo() { return assignedTo; }
    public Map<String, Object> getMetadata() { return metadata; }
    public AgencyDeliverableStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AgencyDeliverable)) return false;
        return id.equals(((AgencyDeliverable) o).id);
    }

    @Override
    public int hashCode() { return id.hashCode(); }

    @Override
    public String toString() {
        return "AgencyDeliverable{id='" + id + "', title='" + title + "', status=" + status + '}';
    }

    public Builder toBuilder() {
        return new Builder()
            .id(id)
            .contractId(contractId)
            .agencyTenantId(agencyTenantId)
            .clientId(clientId)
            .deliverableType(deliverableType)
            .title(title)
            .description(description)
            .dueDate(dueDate)
            .completedDate(completedDate)
            .assignedTo(assignedTo)
            .metadata(metadata)
            .status(status)
            .createdAt(createdAt)
            .updatedAt(updatedAt);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id;
        private String contractId;
        private String agencyTenantId;
        private String clientId;
        private String deliverableType;
        private String title;
        private String description;
        private LocalDate dueDate;
        private LocalDate completedDate;
        private String assignedTo;
        private Map<String, Object> metadata = Map.of();
        private AgencyDeliverableStatus status = AgencyDeliverableStatus.PENDING;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder id(String v) { this.id = v; return this; }
        public Builder contractId(String v) { this.contractId = v; return this; }
        public Builder agencyTenantId(String v) { this.agencyTenantId = v; return this; }
        public Builder clientId(String v) { this.clientId = v; return this; }
        public Builder deliverableType(String v) { this.deliverableType = v; return this; }
        public Builder title(String v) { this.title = v; return this; }
        public Builder description(String v) { this.description = v; return this; }
        public Builder dueDate(LocalDate v) { this.dueDate = v; return this; }
        public Builder completedDate(LocalDate v) { this.completedDate = v; return this; }
        public Builder assignedTo(String v) { this.assignedTo = v; return this; }
        public Builder metadata(Map<String, Object> v) { this.metadata = v; return this; }
        public Builder status(AgencyDeliverableStatus v) { this.status = v; return this; }
        public Builder createdAt(Instant v) { this.createdAt = v; return this; }
        public Builder updatedAt(Instant v) { this.updatedAt = v; return this; }

        public AgencyDeliverable build() {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
            if (contractId == null || contractId.isBlank()) throw new IllegalArgumentException("contractId must not be blank");
            if (agencyTenantId == null || agencyTenantId.isBlank()) throw new IllegalArgumentException("agencyTenantId must not be blank");
            if (clientId == null || clientId.isBlank()) throw new IllegalArgumentException("clientId must not be blank");
            if (title == null || title.isBlank()) throw new IllegalArgumentException("title must not be blank");
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            return new AgencyDeliverable(this);
        }
    }
}
