package com.ghatana.digitalmarketing.domain.content;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;

import java.time.Instant;
import java.util.Objects;

/**
 * A named content item in DMOS (e.g., a landing page, ad, email) that is versioned over time.
 *
 * <p>A {@code ContentItem} is the identity anchor for a versioned content artefact. Its
 * approved {@link ContentVersion} snapshots are referenced by campaign launches and audits.</p>
 *
 * <p>ContentItem instances are immutable after construction.</p>
 *
 * @doc.type class
 * @doc.purpose DMOS content item domain entity serving as the identity anchor for versioned content
 * @doc.layer product
 * @doc.pattern Entity
 */
public final class ContentItem {

    private final String itemId;
    private final DmWorkspaceId workspaceId;
    private final String title;
    private final ContentItemType itemType;
    private final String description;
    private final Instant createdAt;
    private final String createdBy;

    private ContentItem(Builder builder) {
        this.itemId      = Objects.requireNonNull(builder.itemId,      "itemId must not be null");
        this.workspaceId = Objects.requireNonNull(builder.workspaceId, "workspaceId must not be null");
        this.title       = Objects.requireNonNull(builder.title,       "title must not be null");
        this.itemType    = Objects.requireNonNull(builder.itemType,    "itemType must not be null");
        this.description = builder.description != null ? builder.description : "";
        this.createdAt   = Objects.requireNonNull(builder.createdAt,   "createdAt must not be null");
        this.createdBy   = Objects.requireNonNull(builder.createdBy,   "createdBy must not be null");
        if (this.itemId.isBlank())  throw new IllegalArgumentException("itemId must not be blank");
        if (this.title.isBlank())   throw new IllegalArgumentException("title must not be blank");
        if (this.createdBy.isBlank()) throw new IllegalArgumentException("createdBy must not be blank");
    }

    /** Returns the content item identifier. Never {@code null} or blank. */
    public String getItemId() { return itemId; }

    /** Returns the owning workspace. Never {@code null}. */
    public DmWorkspaceId getWorkspaceId() { return workspaceId; }

    /** Returns the item title. Never blank. */
    public String getTitle() { return title; }

    /** Returns the item type. Never {@code null}. */
    public ContentItemType getItemType() { return itemType; }

    /** Returns the item description (may be empty). Never {@code null}. */
    public String getDescription() { return description; }

    /** Returns the creation timestamp. Never {@code null}. */
    public Instant getCreatedAt() { return createdAt; }

    /** Returns the principal who created this item. Never blank. */
    public String getCreatedBy() { return createdBy; }

    /** Returns a new builder pre-populated with this item's values. */
    public Builder toBuilder() {
        return new Builder()
            .itemId(this.itemId)
            .workspaceId(this.workspaceId)
            .title(this.title)
            .itemType(this.itemType)
            .description(this.description)
            .createdAt(this.createdAt)
            .createdBy(this.createdBy);
    }

    /** Returns a fresh builder. */
    public static Builder builder() { return new Builder(); }

    /**
     * Builder for {@link ContentItem}.
     */
    public static final class Builder {
        private String itemId;
        private DmWorkspaceId workspaceId;
        private String title;
        private ContentItemType itemType;
        private String description;
        private Instant createdAt;
        private String createdBy;

        private Builder() {}

        public Builder itemId(String v)          { this.itemId = v;      return this; }
        public Builder workspaceId(DmWorkspaceId v) { this.workspaceId = v; return this; }
        public Builder title(String v)            { this.title = v;      return this; }
        public Builder itemType(ContentItemType v) { this.itemType = v;  return this; }
        public Builder description(String v)      { this.description = v; return this; }
        public Builder createdAt(Instant v)       { this.createdAt = v;  return this; }
        public Builder createdBy(String v)        { this.createdBy = v;  return this; }

        public ContentItem build() { return new ContentItem(this); }
    }
}
