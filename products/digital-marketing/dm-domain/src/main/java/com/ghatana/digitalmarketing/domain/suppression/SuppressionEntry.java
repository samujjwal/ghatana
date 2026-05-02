package com.ghatana.digitalmarketing.domain.suppression;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;

import java.time.Instant;
import java.util.Objects;

/**
 * Domain entity representing a do-not-contact suppression entry.
 *
 * @doc.type class
 * @doc.purpose DMOS suppression and DNC record for contact channels
 * @doc.layer product
 * @doc.pattern Entity
 */
public final class SuppressionEntry {

    private final String id;
    private final DmWorkspaceId workspaceId;
    private final String email;
    private final String reason;
    private final boolean active;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final String createdBy;

    private SuppressionEntry(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "id must not be null");
        this.workspaceId = Objects.requireNonNull(builder.workspaceId, "workspaceId must not be null");
        this.email = Objects.requireNonNull(builder.email, "email must not be null");
        this.reason = builder.reason != null ? builder.reason : "";
        this.active = builder.active;
        this.createdAt = Objects.requireNonNull(builder.createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(builder.updatedAt, "updatedAt must not be null");
        this.createdBy = Objects.requireNonNull(builder.createdBy, "createdBy must not be null");

        if (this.id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (this.email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
    }

    /** Returns the suppression entry identifier. */
    public String getId() {
        return id;
    }

    /** Returns the workspace for this suppression entry. */
    public DmWorkspaceId getWorkspaceId() {
        return workspaceId;
    }

    /** Returns the suppressed email address. */
    public String getEmail() {
        return email;
    }

    /** Returns the suppression reason. */
    public String getReason() {
        return reason;
    }

    /** Returns whether this suppression entry is active. */
    public boolean isActive() {
        return active;
    }

    /** Returns when the suppression entry was created. */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Returns when the suppression entry was last updated. */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** Returns the principal who created the suppression entry. */
    public String getCreatedBy() {
        return createdBy;
    }

    /**
     * Returns a copy with active state set to false.
     *
     * @throws IllegalStateException if already inactive
     */
    public SuppressionEntry deactivate() {
        if (!active) {
            throw new IllegalStateException("Suppression entry is already inactive");
        }
        return toBuilder().active(false).updatedAt(Instant.now()).build();
    }

    /** Returns a builder pre-populated with this entry values. */
    public Builder toBuilder() {
        return new Builder()
            .id(id)
            .workspaceId(workspaceId)
            .email(email)
            .reason(reason)
            .active(active)
            .createdAt(createdAt)
            .updatedAt(updatedAt)
            .createdBy(createdBy);
    }

    /** Returns a fresh builder. */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SuppressionEntry other)) return false;
        return id.equals(other.id) && workspaceId.equals(other.workspaceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, workspaceId);
    }

    public static final class Builder {
        private String id;
        private DmWorkspaceId workspaceId;
        private String email;
        private String reason;
        private boolean active;
        private Instant createdAt;
        private Instant updatedAt;
        private String createdBy;

        private Builder() {
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder workspaceId(DmWorkspaceId workspaceId) {
            this.workspaceId = workspaceId;
            return this;
        }

        public Builder email(String email) {
            this.email = email;
            return this;
        }

        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public Builder active(boolean active) {
            this.active = active;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public Builder createdBy(String createdBy) {
            this.createdBy = createdBy;
            return this;
        }

        /** Builds the suppression entry. */
        public SuppressionEntry build() {
            return new SuppressionEntry(this);
        }
    }
}
