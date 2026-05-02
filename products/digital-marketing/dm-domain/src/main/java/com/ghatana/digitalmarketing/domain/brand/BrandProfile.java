package com.ghatana.digitalmarketing.domain.brand;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Domain entity representing a DMOS brand profile.
 *
 * <p>A {@code BrandProfile} captures the brand identity settings for a workspace —
 * display name, voice tone, color palette, and geographic targeting. Brand profiles
 * govern content generation and compliance checks for campaign messaging.</p>
 *
 * <p>BrandProfile instances are immutable after construction.</p>
 *
 * @doc.type class
 * @doc.purpose DMOS brand profile domain entity for brand identity governance
 * @doc.layer product
 * @doc.pattern Entity
 */
public final class BrandProfile {

    private final String id;
    private final DmWorkspaceId workspaceId;
    private final String displayName;
    private final String voiceTone;
    private final List<String> brandColors;
    private final List<String> targetGeographies;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final String createdBy;

    private BrandProfile(Builder builder) {
        this.id                = Objects.requireNonNull(builder.id,          "id must not be null");
        this.workspaceId       = Objects.requireNonNull(builder.workspaceId, "workspaceId must not be null");
        this.displayName       = Objects.requireNonNull(builder.displayName, "displayName must not be null");
        this.voiceTone         = builder.voiceTone != null ? builder.voiceTone : "";
        this.brandColors       = builder.brandColors != null ? List.copyOf(builder.brandColors) : List.of();
        this.targetGeographies = builder.targetGeographies != null ? List.copyOf(builder.targetGeographies) : List.of();
        this.createdAt         = Objects.requireNonNull(builder.createdAt,   "createdAt must not be null");
        this.updatedAt         = Objects.requireNonNull(builder.updatedAt,   "updatedAt must not be null");
        this.createdBy         = Objects.requireNonNull(builder.createdBy,   "createdBy must not be null");
        if (this.id.isBlank()) throw new IllegalArgumentException("id must not be blank");
        if (this.displayName.isBlank()) throw new IllegalArgumentException("displayName must not be blank");
    }

    /** Returns the brand profile identifier. Never {@code null} or blank. */
    public String getId() { return id; }

    /** Returns the owning workspace identifier. Never {@code null}. */
    public DmWorkspaceId getWorkspaceId() { return workspaceId; }

    /** Returns the brand display name. Never blank. */
    public String getDisplayName() { return displayName; }

    /** Returns the brand voice tone guidance. May be empty. */
    public String getVoiceTone() { return voiceTone; }

    /** Returns the unmodifiable list of brand color hex codes. Never null. */
    public List<String> getBrandColors() { return brandColors; }

    /** Returns the unmodifiable list of ISO country codes targeting this brand. Never null. */
    public List<String> getTargetGeographies() { return targetGeographies; }

    /** Returns the creation timestamp. Never {@code null}. */
    public Instant getCreatedAt() { return createdAt; }

    /** Returns the last-updated timestamp. Never {@code null}. */
    public Instant getUpdatedAt() { return updatedAt; }

    /** Returns the actor who created this brand profile. Never {@code null}. */
    public String getCreatedBy() { return createdBy; }

    /** Returns a fresh {@link Builder}. */
    public static Builder builder() { return new Builder(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BrandProfile other)) return false;
        return id.equals(other.id) && workspaceId.equals(other.workspaceId);
    }

    @Override
    public int hashCode() { return Objects.hash(id, workspaceId); }

    @Override
    public String toString() {
        return "BrandProfile{id=" + id + ", displayName='" + displayName + "'}";
    }

    /**
     * Fluent builder for {@link BrandProfile}.
     */
    public static final class Builder {
        private String id;
        private DmWorkspaceId workspaceId;
        private String displayName;
        private String voiceTone;
        private List<String> brandColors;
        private List<String> targetGeographies;
        private Instant createdAt;
        private Instant updatedAt;
        private String createdBy;

        private Builder() { }

        public Builder id(String id) { this.id = id; return this; }
        public Builder workspaceId(DmWorkspaceId workspaceId) { this.workspaceId = workspaceId; return this; }
        public Builder displayName(String displayName) { this.displayName = displayName; return this; }
        public Builder voiceTone(String voiceTone) { this.voiceTone = voiceTone; return this; }
        public Builder brandColors(List<String> brandColors) { this.brandColors = brandColors; return this; }
        public Builder targetGeographies(List<String> targetGeographies) { this.targetGeographies = targetGeographies; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }
        public Builder createdBy(String createdBy) { this.createdBy = createdBy; return this; }

        /** Builds a {@link BrandProfile}. Throws if required fields are missing or invalid. */
        public BrandProfile build() { return new BrandProfile(this); }
    }
}
