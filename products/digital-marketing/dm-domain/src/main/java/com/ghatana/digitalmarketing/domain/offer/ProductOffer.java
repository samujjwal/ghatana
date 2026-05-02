package com.ghatana.digitalmarketing.domain.offer;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable domain entity describing a productized marketing offer.
 *
 * @doc.type class
 * @doc.purpose DMOS product offer entity for offer catalog lifecycle
 * @doc.layer product
 * @doc.pattern Entity
 */
public final class ProductOffer {

    private final String id;
    private final DmWorkspaceId workspaceId;
    private final String offerName;
    private final String offerDescription;
    private final BigDecimal basePrice;
    private final String currencyCode;
    private final boolean active;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final String createdBy;

    private ProductOffer(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "id must not be null");
        this.workspaceId = Objects.requireNonNull(builder.workspaceId, "workspaceId must not be null");
        this.offerName = Objects.requireNonNull(builder.offerName, "offerName must not be null");
        this.offerDescription = builder.offerDescription != null ? builder.offerDescription : "";
        this.basePrice = Objects.requireNonNull(builder.basePrice, "basePrice must not be null");
        this.currencyCode = Objects.requireNonNull(builder.currencyCode, "currencyCode must not be null");
        this.active = builder.active;
        this.createdAt = Objects.requireNonNull(builder.createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(builder.updatedAt, "updatedAt must not be null");
        this.createdBy = Objects.requireNonNull(builder.createdBy, "createdBy must not be null");

        if (this.id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (this.offerName.isBlank()) {
            throw new IllegalArgumentException("offerName must not be blank");
        }
        if (this.currencyCode.isBlank()) {
            throw new IllegalArgumentException("currencyCode must not be blank");
        }
        if (this.basePrice.signum() < 0) {
            throw new IllegalArgumentException("basePrice must be non-negative");
        }
    }

    public String getId() {
        return id;
    }

    public DmWorkspaceId getWorkspaceId() {
        return workspaceId;
    }

    public String getOfferName() {
        return offerName;
    }

    public String getOfferDescription() {
        return offerDescription;
    }

    public BigDecimal getBasePrice() {
        return basePrice;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public ProductOffer deactivate() {
        return toBuilder().active(false).updatedAt(Instant.now()).build();
    }

    public ProductOffer update(String name, String description, BigDecimal price, String currencyCode) {
        return toBuilder()
            .offerName(name)
            .offerDescription(description)
            .basePrice(price)
            .currencyCode(currencyCode)
            .updatedAt(Instant.now())
            .build();
    }

    public Builder toBuilder() {
        return new Builder()
            .id(id)
            .workspaceId(workspaceId)
            .offerName(offerName)
            .offerDescription(offerDescription)
            .basePrice(basePrice)
            .currencyCode(currencyCode)
            .active(active)
            .createdAt(createdAt)
            .updatedAt(updatedAt)
            .createdBy(createdBy);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private DmWorkspaceId workspaceId;
        private String offerName;
        private String offerDescription;
        private BigDecimal basePrice;
        private String currencyCode;
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

        public Builder offerName(String offerName) {
            this.offerName = offerName;
            return this;
        }

        public Builder offerDescription(String offerDescription) {
            this.offerDescription = offerDescription;
            return this;
        }

        public Builder basePrice(BigDecimal basePrice) {
            this.basePrice = basePrice;
            return this;
        }

        public Builder currencyCode(String currencyCode) {
            this.currencyCode = currencyCode;
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

        public ProductOffer build() {
            return new ProductOffer(this);
        }
    }
}
