package com.ghatana.digitalmarketing.domain.audience;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Domain entity representing a DMOS audience segment.
 *
 * <p>An {@code Audience} is a named, filterable set of contacts that can be
 * targeted by a campaign. Audience exports and sync operations require consent
 * per boundary policy rule {@code DM-BP-004}.</p>
 *
 * <p>Audiences are immutable after construction.</p>
 *
 * @doc.type class
 * @doc.purpose DMOS audience segment domain entity
 * @doc.layer product
 * @doc.pattern Entity
 */
public final class Audience {

    private final String id;
    private final DmWorkspaceId workspaceId;
    private final String name;
    private final String description;
    private final List<String> contactIds;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final String createdBy;

    private Audience(Builder builder) {
        this.id          = Objects.requireNonNull(builder.id,          "id must not be null");
        this.workspaceId = Objects.requireNonNull(builder.workspaceId, "workspaceId must not be null");
        this.name        = Objects.requireNonNull(builder.name,        "name must not be null");
        this.description = builder.description != null ? builder.description : "";
        this.contactIds  = builder.contactIds != null
            ? List.copyOf(builder.contactIds)
            : List.of();
        this.createdAt   = Objects.requireNonNull(builder.createdAt,   "createdAt must not be null");
        this.updatedAt   = Objects.requireNonNull(builder.updatedAt,   "updatedAt must not be null");
        this.createdBy   = Objects.requireNonNull(builder.createdBy,   "createdBy must not be null");
        if (this.id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (this.name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }

    /** Returns the audience identifier. Never {@code null} or blank. */
    public String getId() { return id; }

    /** Returns the owning workspace identifier. Never {@code null}. */
    public DmWorkspaceId getWorkspaceId() { return workspaceId; }

    /** Returns the audience display name. Never blank. */
    public String getName() { return name; }

    /** Returns the optional description. Never {@code null}; may be empty. */
    public String getDescription() { return description; }

    /** Returns an unmodifiable list of contact IDs in this audience. Never null. */
    public List<String> getContactIds() { return contactIds; }

    /** Returns the size of the audience (number of contact IDs). */
    public int size() { return contactIds.size(); }

    /** Returns the creation timestamp. Never {@code null}. */
    public Instant getCreatedAt() { return createdAt; }

    /** Returns the last-updated timestamp. Never {@code null}. */
    public Instant getUpdatedAt() { return updatedAt; }

    /** Returns the actor who created this audience. Never {@code null}. */
    public String getCreatedBy() { return createdBy; }

    /** Returns a fresh {@link Builder}. */
    public static Builder builder() { return new Builder(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Audience other)) return false;
        return id.equals(other.id) && workspaceId.equals(other.workspaceId);
    }

    @Override
    public int hashCode() { return Objects.hash(id, workspaceId); }

    @Override
    public String toString() {
        return "Audience{id=" + id + ", name='" + name + "', size=" + contactIds.size() + '}';
    }

    /**
     * Fluent builder for {@link Audience}.
     */
    public static final class Builder {
        private String id;
        private DmWorkspaceId workspaceId;
        private String name;
        private String description;
        private List<String> contactIds;
        private Instant createdAt;
        private Instant updatedAt;
        private String createdBy;

        private Builder() { }

        public Builder id(String id) { this.id = id; return this; }
        public Builder workspaceId(DmWorkspaceId workspaceId) { this.workspaceId = workspaceId; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder contactIds(List<String> contactIds) { this.contactIds = contactIds; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }
        public Builder createdBy(String createdBy) { this.createdBy = createdBy; return this; }

        /** Builds an {@link Audience}. Throws if required fields are missing or invalid. */
        public Audience build() { return new Audience(this); }
    }
}
