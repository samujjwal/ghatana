package com.ghatana.digitalmarketing.domain.marketplace;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable entity representing a marketplace listing entry.
 *
 * @doc.type class
 * @doc.purpose Domain entity for marketplace foundation (DMOS-F5-001)
 * @doc.layer product
 * @doc.pattern Entity
 */
public final class DmMarketplaceListing {

    private final String id;
    private final String tenantId;
    private final String itemType;
    private final String itemId;
    private final String title;
    private final String description;
    private final long priceMicros;
    private final String currency;
    private final DmMarketplaceListingStatus status;
    private final Instant publishedAt;
    private final Instant createdAt;
    private final Instant updatedAt;

    private DmMarketplaceListing(Builder b) {
        this.id          = b.id;
        this.tenantId    = b.tenantId;
        this.itemType    = b.itemType;
        this.itemId      = b.itemId;
        this.title       = b.title;
        this.description = b.description;
        this.priceMicros = b.priceMicros;
        this.currency    = b.currency;
        this.status      = b.status;
        this.publishedAt = b.publishedAt;
        this.createdAt   = b.createdAt;
        this.updatedAt   = b.updatedAt;
    }

    public DmMarketplaceListing publish() {
        if (status != DmMarketplaceListingStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT listings can be published");
        }
        return toBuilder().status(DmMarketplaceListingStatus.PUBLISHED)
            .publishedAt(Instant.now()).updatedAt(Instant.now()).build();
    }

    public DmMarketplaceListing unpublish() {
        if (status != DmMarketplaceListingStatus.PUBLISHED) {
            throw new IllegalStateException("Only PUBLISHED listings can be unpublished");
        }
        return toBuilder().status(DmMarketplaceListingStatus.UNLISTED)
            .updatedAt(Instant.now()).build();
    }

    public String getId()                { return id; }
    public String getTenantId()          { return tenantId; }
    public String getItemType()          { return itemType; }
    public String getItemId()            { return itemId; }
    public String getTitle()             { return title; }
    public String getDescription()       { return description; }
    public long getPriceMicros()         { return priceMicros; }
    public String getCurrency()          { return currency; }
    public DmMarketplaceListingStatus getStatus() { return status; }
    public Instant getPublishedAt()      { return publishedAt; }
    public Instant getCreatedAt()        { return createdAt; }
    public Instant getUpdatedAt()        { return updatedAt; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DmMarketplaceListing)) return false;
        return id.equals(((DmMarketplaceListing) o).id);
    }
    @Override public int hashCode() { return id.hashCode(); }
    @Override public String toString() {
        return "DmMarketplaceListing{id='" + id + "', status=" + status + '}';
    }

    public Builder toBuilder() {
        return new Builder().id(id).tenantId(tenantId).itemType(itemType).itemId(itemId)
            .title(title).description(description).priceMicros(priceMicros).currency(currency)
            .status(status).publishedAt(publishedAt).createdAt(createdAt).updatedAt(updatedAt);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id, tenantId, itemType, itemId, title, description, currency;
        private long priceMicros;
        private DmMarketplaceListingStatus status;
        private Instant publishedAt, createdAt, updatedAt;

        public Builder id(String v)                       { this.id = v; return this; }
        public Builder tenantId(String v)                 { this.tenantId = v; return this; }
        public Builder itemType(String v)                 { this.itemType = v; return this; }
        public Builder itemId(String v)                   { this.itemId = v; return this; }
        public Builder title(String v)                    { this.title = v; return this; }
        public Builder description(String v)              { this.description = v; return this; }
        public Builder priceMicros(long v)                { this.priceMicros = v; return this; }
        public Builder currency(String v)                 { this.currency = v; return this; }
        public Builder status(DmMarketplaceListingStatus v) { this.status = v; return this; }
        public Builder publishedAt(Instant v)             { this.publishedAt = v; return this; }
        public Builder createdAt(Instant v)               { this.createdAt = v; return this; }
        public Builder updatedAt(Instant v)               { this.updatedAt = v; return this; }

        public DmMarketplaceListing build() {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
            if (title == null || title.isBlank()) throw new IllegalArgumentException("title must not be blank");
            if (itemType == null || itemType.isBlank()) throw new IllegalArgumentException("itemType must not be blank");
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            return new DmMarketplaceListing(this);
        }
    }
}
