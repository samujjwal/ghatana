package com.ghatana.digitalmarketing.domain.content;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;

import java.time.Instant;
import java.util.Objects;

/**
 * Domain entity representing a DMOS content asset (creative material).
 *
 * <p>A {@code ContentAsset} is a named piece of marketing creative (copy, image URL,
 * HTML snippet, etc.) that can be linked to a campaign. Campaigns may not launch
 * without at least one approved content asset per compliance rule {@code DM-CES}.</p>
 *
 * <p>ContentAsset instances are immutable after construction.</p>
 *
 * @doc.type class
 * @doc.purpose DMOS content asset domain entity for creative management
 * @doc.layer product
 * @doc.pattern Entity
 */
public final class ContentAsset {

    private final String id;
    private final DmWorkspaceId workspaceId;
    private final String campaignId;
    private final String title;
    private final String assetType;
    private final String contentBody;
    private final ContentStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final String createdBy;

    private ContentAsset(Builder builder) {
        this.id          = Objects.requireNonNull(builder.id,          "id must not be null");
        this.workspaceId = Objects.requireNonNull(builder.workspaceId, "workspaceId must not be null");
        this.campaignId  = Objects.requireNonNull(builder.campaignId,  "campaignId must not be null");
        this.title       = Objects.requireNonNull(builder.title,       "title must not be null");
        this.assetType   = Objects.requireNonNull(builder.assetType,   "assetType must not be null");
        this.contentBody = Objects.requireNonNull(builder.contentBody, "contentBody must not be null");
        this.status      = Objects.requireNonNull(builder.status,      "status must not be null");
        this.createdAt   = Objects.requireNonNull(builder.createdAt,   "createdAt must not be null");
        this.updatedAt   = Objects.requireNonNull(builder.updatedAt,   "updatedAt must not be null");
        this.createdBy   = Objects.requireNonNull(builder.createdBy,   "createdBy must not be null");
        if (this.id.isBlank()) throw new IllegalArgumentException("id must not be blank");
        if (this.title.isBlank()) throw new IllegalArgumentException("title must not be blank");
        if (this.assetType.isBlank()) throw new IllegalArgumentException("assetType must not be blank");
    }

    /** Returns the asset identifier. Never {@code null} or blank. */
    public String getId() { return id; }

    /** Returns the owning workspace identifier. Never {@code null}. */
    public DmWorkspaceId getWorkspaceId() { return workspaceId; }

    /** Returns the campaign this asset belongs to. Never {@code null}. */
    public String getCampaignId() { return campaignId; }

    /** Returns the asset title. Never blank. */
    public String getTitle() { return title; }

    /** Returns the asset type (e.g. {@code "email"}, {@code "banner"}). Never blank. */
    public String getAssetType() { return assetType; }

    /** Returns the content body (copy, HTML, image URL). Never {@code null}. */
    public String getContentBody() { return contentBody; }

    /** Returns the approval status. Never {@code null}. */
    public ContentStatus getStatus() { return status; }

    /** Returns the creation timestamp. Never {@code null}. */
    public Instant getCreatedAt() { return createdAt; }

    /** Returns the last-updated timestamp. Never {@code null}. */
    public Instant getUpdatedAt() { return updatedAt; }

    /** Returns the actor who created this asset. Never {@code null}. */
    public String getCreatedBy() { return createdBy; }

    /** Returns {@code true} if the asset is approved and ready for campaign use. */
    public boolean isApproved() { return status == ContentStatus.APPROVED; }

    /** Returns a fresh {@link Builder}. */
    public static Builder builder() { return new Builder(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ContentAsset other)) return false;
        return id.equals(other.id) && workspaceId.equals(other.workspaceId);
    }

    @Override
    public int hashCode() { return Objects.hash(id, workspaceId); }

    @Override
    public String toString() {
        return "ContentAsset{id=" + id + ", title='" + title + "', status=" + status + '}';
    }

    /**
     * Fluent builder for {@link ContentAsset}.
     */
    public static final class Builder {
        private String id;
        private DmWorkspaceId workspaceId;
        private String campaignId;
        private String title;
        private String assetType;
        private String contentBody;
        private ContentStatus status;
        private Instant createdAt;
        private Instant updatedAt;
        private String createdBy;

        private Builder() { }

        public Builder id(String id) { this.id = id; return this; }
        public Builder workspaceId(DmWorkspaceId workspaceId) { this.workspaceId = workspaceId; return this; }
        public Builder campaignId(String campaignId) { this.campaignId = campaignId; return this; }
        public Builder title(String title) { this.title = title; return this; }
        public Builder assetType(String assetType) { this.assetType = assetType; return this; }
        public Builder contentBody(String contentBody) { this.contentBody = contentBody; return this; }
        public Builder status(ContentStatus status) { this.status = status; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }
        public Builder createdBy(String createdBy) { this.createdBy = createdBy; return this; }

        /** Builds a {@link ContentAsset}. Throws if required fields are missing or invalid. */
        public ContentAsset build() { return new ContentAsset(this); }
    }
}
