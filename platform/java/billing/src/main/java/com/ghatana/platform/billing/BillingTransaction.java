/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

package com.ghatana.platform.billing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Domain-neutral billing transaction — the unit of exchange between PHR billing
 * and the Finance ledger. Both products work against this shared value type
 * rather than leaking product-specific models across boundaries.
 *
 * <p>A {@code BillingTransaction} represents a single charge or credit that
 * must be posted to the financial ledger. Healthcare-specific metadata
 * (encounter ID, diagnosis codes, insurer) travel as optional context fields
 * so the Finance module does not need to know about FHIR or insurance concepts.
 *
 * @doc.type class
 * @doc.purpose Shared billing transaction value type for PHR-to-Finance ledger posting
 * @doc.layer platform
 * @doc.pattern ValueObject
 * @author Ghatana Platform Team
 * @since 1.0.0
 */
public final class BillingTransaction {

    private final String transactionId;
    private final String sourceProductId;
    private final String debitAccount;
    private final String creditAccount;
    private final BigDecimal amount;
    private final String currency;
    private final TransactionType type;
    private final String description;
    private final String externalReferenceId;
    private final String tenantId;
    private final Instant occurredAt;

    private BillingTransaction(Builder builder) {
        this.transactionId      = Objects.requireNonNull(builder.transactionId,      "transactionId");
        this.sourceProductId    = Objects.requireNonNull(builder.sourceProductId,    "sourceProductId");
        this.debitAccount       = Objects.requireNonNull(builder.debitAccount,       "debitAccount");
        this.creditAccount      = Objects.requireNonNull(builder.creditAccount,      "creditAccount");
        this.amount             = Objects.requireNonNull(builder.amount,             "amount");
        this.currency           = Objects.requireNonNull(builder.currency,           "currency");
        this.type               = Objects.requireNonNull(builder.type,               "type");
        this.description        = Objects.requireNonNull(builder.description,        "description");
        this.externalReferenceId = builder.externalReferenceId;
        this.tenantId           = Objects.requireNonNull(builder.tenantId,           "tenantId");
        this.occurredAt         = builder.occurredAt != null ? builder.occurredAt : Instant.now();

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be positive, got: " + amount);
        }
        if (debitAccount.equals(creditAccount)) {
            throw new IllegalArgumentException("debitAccount and creditAccount must differ");
        }
    }

    // =========================================================================
    // Accessors
    // =========================================================================

    public String getTransactionId()       { return transactionId; }
    public String getSourceProductId()     { return sourceProductId; }
    public String getDebitAccount()        { return debitAccount; }
    public String getCreditAccount()       { return creditAccount; }
    public BigDecimal getAmount()          { return amount; }
    public String getCurrency()            { return currency; }
    public TransactionType getType()       { return type; }
    public String getDescription()         { return description; }
    public String getExternalReferenceId() { return externalReferenceId; }
    public String getTenantId()            { return tenantId; }
    public Instant getOccurredAt()         { return occurredAt; }

    // =========================================================================
    // Factory
    // =========================================================================

    public static Builder builder() {
        return new Builder();
    }

    // =========================================================================
    // Types
    // =========================================================================

    /**
     * Coarse-grained transaction type used by the Finance ledger to categorize
     * the posting without knowing healthcare-specific billing codes.
     */
    public enum TransactionType {
        /** A service was rendered and a charge is being posted. */
        CHARGE,
        /** A payment was received and must be credited. */
        PAYMENT,
        /** An approved insurance claim settlement is being applied. */
        INSURANCE_SETTLEMENT,
        /** A prior charge is being reversed (e.g. billing error). */
        REVERSAL,
        /** A co-payment or patient out-of-pocket portion. */
        CO_PAYMENT,
        /** A refund is being issued to the patient or insurer. */
        REFUND
    }

    // =========================================================================
    // Builder
    // =========================================================================

    public static final class Builder {
        private String transactionId;
        private String sourceProductId;
        private String debitAccount;
        private String creditAccount;
        private BigDecimal amount;
        private String currency;
        private TransactionType type;
        private String description;
        private String externalReferenceId;
        private String tenantId;
        private Instant occurredAt;

        private Builder() {}

        public Builder transactionId(String transactionId) {
            this.transactionId = transactionId;
            return this;
        }

        public Builder sourceProductId(String sourceProductId) {
            this.sourceProductId = sourceProductId;
            return this;
        }

        public Builder debitAccount(String debitAccount) {
            this.debitAccount = debitAccount;
            return this;
        }

        public Builder creditAccount(String creditAccount) {
            this.creditAccount = creditAccount;
            return this;
        }

        public Builder amount(BigDecimal amount) {
            this.amount = amount;
            return this;
        }

        public Builder currency(String currency) {
            this.currency = currency;
            return this;
        }

        public Builder type(TransactionType type) {
            this.type = type;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder externalReferenceId(String externalReferenceId) {
            this.externalReferenceId = externalReferenceId;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder occurredAt(Instant occurredAt) {
            this.occurredAt = occurredAt;
            return this;
        }

        public BillingTransaction build() {
            return new BillingTransaction(this);
        }
    }

    @Override
    public String toString() {
        return "BillingTransaction{id=" + transactionId
            + ", type=" + type
            + ", amount=" + amount + " " + currency
            + ", debit=" + debitAccount
            + ", credit=" + creditAccount + "}";
    }
}
