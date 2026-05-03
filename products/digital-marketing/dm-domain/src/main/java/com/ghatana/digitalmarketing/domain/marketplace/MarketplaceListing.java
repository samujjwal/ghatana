package com.ghatana.digitalmarketing.domain.marketplace;

import com.ghatana.digitalmarketing.contracts.DmTenantId;

import java.time.Instant;

/**
 * Domain entity representing a marketplace listing (DMOS-P3-004).
 *
 * @doc.type class
 * @doc.purpose Represents a marketplace listing for playbooks
 * @doc.layer domain
 */
public final class MarketplaceListing {

    private final String listingId;
    private final String name;
    private final String description;
    private final String authorTenantId;
    private final String version;
    private final String status;
    private final double rating;
    private final int downloadCount;
    private final Instant createdAt;
    private final Instant updatedAt;

    private MarketplaceListing(Builder builder) {
        this.listingId = builder.listingId;
        this.name = builder.name;
        this.description = builder.description;
        this.authorTenantId = builder.authorTenantId;
        this.version = builder.version;
        this.status = builder.status;
        this.rating = builder.rating;
        this.downloadCount = builder.downloadCount;
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
    }

    public String getListingId() {
        return listingId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getAuthorTenantId() {
        return authorTenantId;
    }

    public String getVersion() {
        return version;
    }

    public String getStatus() {
        return status;
    }

    public double getRating() {
        return rating;
    }

    public int getDownloadCount() {
        return downloadCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String listingId;
        private String name;
        private String description;
        private String authorTenantId;
        private String version;
        private String status = "DRAFT";
        private double rating = 0.0;
        private int downloadCount = 0;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder listingId(String listingId) {
            this.listingId = listingId;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder authorTenantId(String authorTenantId) {
            this.authorTenantId = authorTenantId;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder rating(double rating) {
            this.rating = rating;
            return this;
        }

        public Builder downloadCount(int downloadCount) {
            this.downloadCount = downloadCount;
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

        public MarketplaceListing build() {
            return new MarketplaceListing(this);
        }
    }
}
