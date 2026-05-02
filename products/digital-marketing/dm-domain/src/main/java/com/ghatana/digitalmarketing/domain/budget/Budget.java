package com.ghatana.digitalmarketing.domain.budget;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;

import java.time.Instant;
import java.util.Objects;

/**
 * Domain entity representing a DMOS campaign budget allocation.
 *
 * <p>A {@code Budget} records the approved spending limit for a campaign or
 * workspace-level spending pool. Budget approval is required before campaign launch
 * per boundary policy rule {@code DM-BP-006}.</p>
 *
 * <p>Budgets are immutable after construction; state changes return new instances.</p>
 *
 * @doc.type class
 * @doc.purpose DMOS budget domain entity for campaign spending governance
 * @doc.layer product
 * @doc.pattern Entity, AggregateRoot
 */
public final class Budget {

    private final String id;
    private final DmWorkspaceId workspaceId;
    private final String campaignId;
    private final double allocatedAmount;
    private final double spentAmount;
    private final String currency;
    private final BudgetStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final String createdBy;

    private Budget(Builder builder) {
        this.id              = Objects.requireNonNull(builder.id,          "id must not be null");
        this.workspaceId     = Objects.requireNonNull(builder.workspaceId, "workspaceId must not be null");
        this.campaignId      = Objects.requireNonNull(builder.campaignId,  "campaignId must not be null");
        this.allocatedAmount = builder.allocatedAmount;
        this.spentAmount     = builder.spentAmount;
        this.currency        = Objects.requireNonNull(builder.currency,    "currency must not be null");
        this.status          = Objects.requireNonNull(builder.status,      "status must not be null");
        this.createdAt       = Objects.requireNonNull(builder.createdAt,   "createdAt must not be null");
        this.updatedAt       = Objects.requireNonNull(builder.updatedAt,   "updatedAt must not be null");
        this.createdBy       = Objects.requireNonNull(builder.createdBy,   "createdBy must not be null");
        if (this.id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (this.allocatedAmount < 0) {
            throw new IllegalArgumentException("allocatedAmount must be non-negative");
        }
        if (this.spentAmount < 0) {
            throw new IllegalArgumentException("spentAmount must be non-negative");
        }
        if (this.currency.isBlank()) {
            throw new IllegalArgumentException("currency must not be blank");
        }
    }

    /** Returns the budget identifier. Never {@code null} or blank. */
    public String getId() { return id; }

    /** Returns the owning workspace identifier. Never {@code null}. */
    public DmWorkspaceId getWorkspaceId() { return workspaceId; }

    /** Returns the campaign this budget is allocated for. Never {@code null}. */
    public String getCampaignId() { return campaignId; }

    /** Returns the total approved budget amount. Non-negative. */
    public double getAllocatedAmount() { return allocatedAmount; }

    /** Returns the amount spent to date. Non-negative. */
    public double getSpentAmount() { return spentAmount; }

    /** Returns the remaining unspent budget. Non-negative. */
    public double getRemainingAmount() { return Math.max(0, allocatedAmount - spentAmount); }

    /** Returns the currency code (e.g. {@code "USD"}). Never blank. */
    public String getCurrency() { return currency; }

    /** Returns the current status. Never {@code null}. */
    public BudgetStatus getStatus() { return status; }

    /** Returns the creation timestamp. Never {@code null}. */
    public Instant getCreatedAt() { return createdAt; }

    /** Returns the last-updated timestamp. Never {@code null}. */
    public Instant getUpdatedAt() { return updatedAt; }

    /** Returns the actor who created this budget. Never {@code null}. */
    public String getCreatedBy() { return createdBy; }

    /**
     * Returns {@code true} when the budget is approved and has remaining funds.
     */
    public boolean isApprovedAndSolvent() {
        return status == BudgetStatus.APPROVED && getRemainingAmount() > 0;
    }

    /**
     * Returns a copy with status set to {@link BudgetStatus#APPROVED}.
     * Only valid from {@link BudgetStatus#DRAFT}.
     *
     * @throws IllegalStateException if not in DRAFT status
     */
    public Budget approve() {
        if (status != BudgetStatus.DRAFT) {
            throw new IllegalStateException(
                "Cannot approve budget in status " + status + "; must be DRAFT");
        }
        return toBuilder().status(BudgetStatus.APPROVED).updatedAt(Instant.now()).build();
    }

    /**
     * Returns a copy recording an additional spend amount.
     *
     * @param amount the amount to record as spent; must be positive
     * @throws IllegalStateException if budget is not APPROVED
     * @throws IllegalArgumentException if amount is non-positive or exceeds remaining budget
     */
    public Budget recordSpend(double amount) {
        if (status != BudgetStatus.APPROVED) {
            throw new IllegalStateException("Cannot record spend on budget with status " + status);
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Spend amount must be positive; got " + amount);
        }
        if (amount > getRemainingAmount()) {
            throw new IllegalArgumentException(
                "Spend amount " + amount + " exceeds remaining budget " + getRemainingAmount());
        }
        double newSpent = spentAmount + amount;
        BudgetStatus newStatus = newSpent >= allocatedAmount
            ? BudgetStatus.EXHAUSTED
            : BudgetStatus.APPROVED;
        return toBuilder()
            .spentAmount(newSpent)
            .status(newStatus)
            .updatedAt(Instant.now())
            .build();
    }

    /**
     * Returns a copy with status set to {@link BudgetStatus#CANCELLED}.
     *
     * @throws IllegalStateException if already exhausted or cancelled
     */
    public Budget cancel() {
        if (status == BudgetStatus.EXHAUSTED || status == BudgetStatus.CANCELLED) {
            throw new IllegalStateException("Cannot cancel budget in status " + status);
        }
        return toBuilder().status(BudgetStatus.CANCELLED).updatedAt(Instant.now()).build();
    }

    /** Returns a builder pre-populated with this budget's values. */
    public Builder toBuilder() {
        return new Builder()
            .id(id)
            .workspaceId(workspaceId)
            .campaignId(campaignId)
            .allocatedAmount(allocatedAmount)
            .spentAmount(spentAmount)
            .currency(currency)
            .status(status)
            .createdAt(createdAt)
            .updatedAt(updatedAt)
            .createdBy(createdBy);
    }

    /** Returns a fresh {@link Builder}. */
    public static Builder builder() { return new Builder(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Budget other)) return false;
        return id.equals(other.id) && workspaceId.equals(other.workspaceId);
    }

    @Override
    public int hashCode() { return Objects.hash(id, workspaceId); }

    @Override
    public String toString() {
        return "Budget{id=" + id + ", campaignId=" + campaignId
            + ", allocated=" + allocatedAmount + ", spent=" + spentAmount
            + ", status=" + status + '}';
    }

    /**
     * Fluent builder for {@link Budget}.
     */
    public static final class Builder {
        private String id;
        private DmWorkspaceId workspaceId;
        private String campaignId;
        private double allocatedAmount;
        private double spentAmount;
        private String currency = "USD";
        private BudgetStatus status;
        private Instant createdAt;
        private Instant updatedAt;
        private String createdBy;

        private Builder() { }

        public Builder id(String id) { this.id = id; return this; }
        public Builder workspaceId(DmWorkspaceId workspaceId) { this.workspaceId = workspaceId; return this; }
        public Builder campaignId(String campaignId) { this.campaignId = campaignId; return this; }
        public Builder allocatedAmount(double allocatedAmount) { this.allocatedAmount = allocatedAmount; return this; }
        public Builder spentAmount(double spentAmount) { this.spentAmount = spentAmount; return this; }
        public Builder currency(String currency) { this.currency = currency; return this; }
        public Builder status(BudgetStatus status) { this.status = status; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }
        public Builder createdBy(String createdBy) { this.createdBy = createdBy; return this; }

        /** Builds a {@link Budget}. Throws if required fields are missing or invalid. */
        public Budget build() { return new Budget(this); }
    }
}
