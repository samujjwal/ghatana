package com.ghatana.plugin.ledger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable ledger transaction value object.
 *
 * @doc.type class
 * @doc.purpose Immutable ledger transaction data for double-entry posting
 * @doc.layer platform
 * @doc.pattern Value Object
 * @since 1.2.0
 */
public final class LedgerTransaction {

    private final String transactionId;
    private final String sourceId;
    private final String debitAccount;
    private final String creditAccount;
    private final BigDecimal amount;
    private final String currency;
    private final TransactionType type;
    private final String description;
    private final String externalReferenceId;
    private final String tenantId;
    private final Instant occurredAt;
    private final Map<String, Object> metadata;

    private LedgerTransaction(Builder builder) {
        this.transactionId = Objects.requireNonNull(builder.transactionId, "transactionId required");
        this.sourceId = Objects.requireNonNull(builder.sourceId, "sourceId required");
        this.debitAccount = Objects.requireNonNull(builder.debitAccount, "debitAccount required");
        this.creditAccount = Objects.requireNonNull(builder.creditAccount, "creditAccount required");
        this.amount = Objects.requireNonNull(builder.amount, "amount required");
        this.currency = Objects.requireNonNull(builder.currency, "currency required");
        this.type = Objects.requireNonNull(builder.type, "type required");
        this.description = builder.description;
        this.externalReferenceId = builder.externalReferenceId;
        this.tenantId = builder.tenantId;
        this.occurredAt = builder.occurredAt != null ? builder.occurredAt : Instant.now();
        this.metadata = builder.metadata != null ? Map.copyOf(builder.metadata) : Map.of();
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getTransactionId() { return transactionId; }
    /** The source scope or module ID that originated this transaction. */
    public String getSourceId() { return sourceId; }
    public String getDebitAccount() { return debitAccount; }
    public String getCreditAccount() { return creditAccount; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public TransactionType getType() { return type; }
    public String getDescription() { return description; }
    public String getExternalReferenceId() { return externalReferenceId; }
    public String getTenantId() { return tenantId; }
    public Instant getOccurredAt() { return occurredAt; }
    public Map<String, Object> getMetadata() { return metadata; }

    /**
     * Transaction types for double-entry ledger posting.
     */
    public enum TransactionType {
        CHARGE,
        REFUND,
        ADJUSTMENT,
        TRANSFER,
        FEE
    }

    /** Fluent builder for {@link LedgerTransaction}. */
    public static final class Builder {
        private String transactionId;
        private String sourceId;
        private String debitAccount;
        private String creditAccount;
        private BigDecimal amount;
        private String currency;
        private TransactionType type;
        private String description;
        private String externalReferenceId;
        private String tenantId;
        private Instant occurredAt;
        private Map<String, Object> metadata;

        private Builder() {}

        public Builder transactionId(String transactionId) {
            this.transactionId = transactionId;
            return this;
        }

        public Builder sourceId(String sourceId) {
            this.sourceId = sourceId;
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

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public LedgerTransaction build() {
            return new LedgerTransaction(this);
        }
    }
}
