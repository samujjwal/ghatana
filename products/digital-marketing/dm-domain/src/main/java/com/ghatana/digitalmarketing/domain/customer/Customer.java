package com.ghatana.digitalmarketing.domain.customer;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Customer account domain entity.
 *
 * @doc.type class
 * @doc.purpose Represents a customer account in the Digital Marketing system
 * @doc.layer product
 * @doc.pattern Domain Entity
 */
public class Customer {

    private final String customerId;
    private final String tenantId;
    private CustomerStatus status;
    private final CustomerProfile profile;
    private final ConsentStatus consentStatus;
    private final Instant createdAt;
    private Instant activatedAt;
    private Instant deactivatedAt;
    private final String createdBy;
    private String deactivatedBy;

    private Customer(Builder builder) {
        this.customerId = Objects.requireNonNull(builder.customerId, "customerId must not be null");
        this.tenantId = Objects.requireNonNull(builder.tenantId, "tenantId must not be null");
        this.status = Objects.requireNonNull(builder.status, "status must not be null");
        this.profile = Objects.requireNonNull(builder.profile, "profile must not be null");
        this.consentStatus = Objects.requireNonNull(builder.consentStatus, "consentStatus must not be null");
        this.createdAt = Objects.requireNonNull(builder.createdAt, "createdAt must not be null");
        this.activatedAt = builder.activatedAt;
        this.deactivatedAt = builder.deactivatedAt;
        this.createdBy = Objects.requireNonNull(builder.createdBy, "createdBy must not be null");
        this.deactivatedBy = builder.deactivatedBy;
    }

    public String customerId() {
        return customerId;
    }

    public String tenantId() {
        return tenantId;
    }

    public CustomerStatus status() {
        return status;
    }

    public CustomerProfile profile() {
        return profile;
    }

    public ConsentStatus consentStatus() {
        return consentStatus;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant activatedAt() {
        return activatedAt;
    }

    public Instant deactivatedAt() {
        return deactivatedAt;
    }

    public String createdBy() {
        return createdBy;
    }

    public String deactivatedBy() {
        return deactivatedBy;
    }

    /**
     * Activate the customer account.
     *
     * @throws IllegalStateException if consent is not granted or account is already active
     */
    public void activate() {
        if (status != CustomerStatus.PENDING) {
            throw new IllegalStateException("Cannot activate customer in status: " + status);
        }
        if (consentStatus != ConsentStatus.GRANTED) {
            throw new IllegalStateException("Cannot activate customer without consent");
        }
        this.status = CustomerStatus.ACTIVE;
        this.activatedAt = Instant.now();
    }

    /**
     * Deactivate the customer account.
     *
     * @param deactivatedBy user who is deactivating the account
     * @throws IllegalStateException if account is not active
     */
    public void deactivate(String deactivatedBy) {
        if (status != CustomerStatus.ACTIVE) {
            throw new IllegalStateException("Cannot deactivate customer in status: " + status);
        }
        this.status = CustomerStatus.DEACTIVATED;
        this.deactivatedAt = Instant.now();
        this.deactivatedBy = Objects.requireNonNull(deactivatedBy, "deactivatedBy must not be null");
    }

    /**
     * Update customer profile.
     *
     * @param newProfile the new profile
     * @throws IllegalStateException if account is deactivated
     */
    public void updateProfile(CustomerProfile newProfile) {
        if (status == CustomerStatus.DEACTIVATED) {
            throw new IllegalStateException("Cannot update profile for deactivated customer");
        }
        // Profile update logic would go here
    }

    public static Builder builder() {
        return new Builder();
    }

    public static String generateCustomerId() {
        return "CUST-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    public static final class Builder {
        private String customerId;
        private String tenantId;
        private CustomerStatus status = CustomerStatus.PENDING;
        private CustomerProfile profile;
        private ConsentStatus consentStatus = ConsentStatus.PENDING;
        private Instant createdAt = Instant.now();
        private Instant activatedAt;
        private Instant deactivatedAt;
        private String createdBy;
        private String deactivatedBy;

        public Builder customerId(String customerId) {
            this.customerId = customerId;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder status(CustomerStatus status) {
            this.status = status;
            return this;
        }

        public Builder profile(CustomerProfile profile) {
            this.profile = profile;
            return this;
        }

        public Builder consentStatus(ConsentStatus consentStatus) {
            this.consentStatus = consentStatus;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder activatedAt(Instant activatedAt) {
            this.activatedAt = activatedAt;
            return this;
        }

        public Builder deactivatedAt(Instant deactivatedAt) {
            this.deactivatedAt = deactivatedAt;
            return this;
        }

        public Builder createdBy(String createdBy) {
            this.createdBy = createdBy;
            return this;
        }

        public Builder deactivatedBy(String deactivatedBy) {
            this.deactivatedBy = deactivatedBy;
            return this;
        }

        public Customer build() {
            return new Customer(this);
        }
    }

    public enum CustomerStatus {
        PENDING,
        ACTIVE,
        DEACTIVATED
    }

    public enum ConsentStatus {
        PENDING,
        GRANTED,
        REVOKED
    }
}
