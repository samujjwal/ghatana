package com.ghatana.digitalmarketing.domain.budget;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Aggregate root representing a workspace-level budget recommendation.
 *
 * <p>A {@code BudgetRecommendation} is generated deterministically from the approved
 * strategy's monthly budget cap. It allocates that budget across channels, sets per-channel
 * daily spend caps, and provides rationale. The recommendation must be approved by the
 * workspace owner before any campaign launch command is accepted.</p>
 *
 * <p>State transitions:</p>
 * <pre>
 *   DRAFT → PENDING_APPROVAL → APPROVED
 *                           → REJECTED
 * </pre>
 *
 * <p>Instances are immutable; state-changing methods return new instances.</p>
 *
 * @doc.type class
 * @doc.purpose Budget recommendation aggregate with approval workflow for F1-014
 * @doc.layer product
 * @doc.pattern AggregateRoot
 */
public final class BudgetRecommendation {

    private final String recommendationId;
    private final DmWorkspaceId workspaceId;
    private final String strategyId;
    private final double totalMonthlyCap;
    private final double changeThresholdPct;
    private final List<BudgetChannelAllocation> channelAllocations;
    private final String rationale;
    private final String assumptions;
    private final String modelVersion;
    private final BudgetRecommendationStatus status;
    private final Instant generatedAt;
    private final String generatedBy;
    private final Instant approvedAt;
    private final String approvedBy;

    private BudgetRecommendation(Builder b) {
        this.recommendationId  = Objects.requireNonNull(b.recommendationId,  "recommendationId must not be null");
        this.workspaceId       = Objects.requireNonNull(b.workspaceId,       "workspaceId must not be null");
        this.strategyId        = Objects.requireNonNull(b.strategyId,        "strategyId must not be null");
        this.totalMonthlyCap   = b.totalMonthlyCap;
        this.changeThresholdPct = b.changeThresholdPct;
        this.channelAllocations = Collections.unmodifiableList(
            new ArrayList<>(Objects.requireNonNull(b.channelAllocations, "channelAllocations must not be null")));
        this.rationale         = Objects.requireNonNull(b.rationale,         "rationale must not be null");
        this.assumptions       = Objects.requireNonNull(b.assumptions,       "assumptions must not be null");
        this.modelVersion      = Objects.requireNonNull(b.modelVersion,      "modelVersion must not be null");
        this.status            = Objects.requireNonNull(b.status,            "status must not be null");
        this.generatedAt       = Objects.requireNonNull(b.generatedAt,       "generatedAt must not be null");
        this.generatedBy       = Objects.requireNonNull(b.generatedBy,       "generatedBy must not be null");
        this.approvedAt        = b.approvedAt;
        this.approvedBy        = b.approvedBy;

        if (this.recommendationId.isBlank()) {
            throw new IllegalArgumentException("recommendationId must not be blank");
        }
        if (this.totalMonthlyCap < 0) {
            throw new IllegalArgumentException("totalMonthlyCap must be non-negative");
        }
        if (this.changeThresholdPct < 0 || this.changeThresholdPct > 100) {
            throw new IllegalArgumentException("changeThresholdPct must be between 0 and 100");
        }
        if (this.channelAllocations.isEmpty()) {
            throw new IllegalArgumentException("channelAllocations must not be empty");
        }
        if (this.rationale.isBlank()) {
            throw new IllegalArgumentException("rationale must not be blank");
        }
        if (this.modelVersion.isBlank()) {
            throw new IllegalArgumentException("modelVersion must not be blank");
        }
    }

    /** Returns the unique recommendation identifier. Never null or blank. */
    public String getRecommendationId() { return recommendationId; }

    /** Returns the owning workspace. Never null. */
    public DmWorkspaceId getWorkspaceId() { return workspaceId; }

    /** Returns the source strategy ID. Never null. */
    public String getStrategyId() { return strategyId; }

    /** Returns the total recommended monthly spend cap in the workspace currency. Non-negative. */
    public double getTotalMonthlyCap() { return totalMonthlyCap; }

    /**
     * Returns the percentage change threshold above which a budget change requires re-approval.
     * Range 0–100.
     */
    public double getChangeThresholdPct() { return changeThresholdPct; }

    /** Returns per-channel allocations. Never null or empty. Immutable list. */
    public List<BudgetChannelAllocation> getChannelAllocations() { return channelAllocations; }

    /** Returns human-readable rationale for the total cap. Never null or blank. */
    public String getRationale() { return rationale; }

    /** Returns assumptions underlying this recommendation. Never null. */
    public String getAssumptions() { return assumptions; }

    /** Returns the generator model version. Never null or blank. */
    public String getModelVersion() { return modelVersion; }

    /** Returns the current approval status. Never null. */
    public BudgetRecommendationStatus getStatus() { return status; }

    /** Returns when this recommendation was generated. Never null. */
    public Instant getGeneratedAt() { return generatedAt; }

    /** Returns the principal who generated this recommendation. Never null. */
    public String getGeneratedBy() { return generatedBy; }

    /** Returns when this recommendation was approved, or {@code null} if not yet approved. */
    public Instant getApprovedAt() { return approvedAt; }

    /** Returns the approver principal, or {@code null} if not yet approved. */
    public String getApprovedBy() { return approvedBy; }

    /**
     * Returns a copy with status {@link BudgetRecommendationStatus#PENDING_APPROVAL}.
     * Only valid from {@link BudgetRecommendationStatus#DRAFT}.
     *
     * @throws IllegalStateException if status is not DRAFT
     */
    public BudgetRecommendation submitForApproval() {
        if (status != BudgetRecommendationStatus.DRAFT) {
            throw new IllegalStateException(
                "Cannot submit budget recommendation for approval from status: " + status);
        }
        return toBuilder().status(BudgetRecommendationStatus.PENDING_APPROVAL).build();
    }

    /**
     * Returns an approved copy of this recommendation.
     * Only valid from {@link BudgetRecommendationStatus#PENDING_APPROVAL}.
     *
     * @param approvedBy  the approver principal; must not be null or blank
     * @param approvedAt  the approval timestamp; must not be null
     * @throws IllegalStateException if status is not PENDING_APPROVAL
     */
    public BudgetRecommendation approve(String approvedBy, Instant approvedAt) {
        Objects.requireNonNull(approvedBy, "approvedBy must not be null");
        Objects.requireNonNull(approvedAt, "approvedAt must not be null");
        if (approvedBy.isBlank()) {
            throw new IllegalArgumentException("approvedBy must not be blank");
        }
        if (status != BudgetRecommendationStatus.PENDING_APPROVAL) {
            throw new IllegalStateException(
                "Cannot approve budget recommendation from status: " + status);
        }
        return toBuilder()
            .status(BudgetRecommendationStatus.APPROVED)
            .approvedBy(approvedBy)
            .approvedAt(approvedAt)
            .build();
    }

    /**
     * Returns a rejected copy of this recommendation.
     * Only valid from {@link BudgetRecommendationStatus#PENDING_APPROVAL}.
     *
     * @throws IllegalStateException if status is not PENDING_APPROVAL
     */
    public BudgetRecommendation reject() {
        if (status != BudgetRecommendationStatus.PENDING_APPROVAL) {
            throw new IllegalStateException(
                "Cannot reject budget recommendation from status: " + status);
        }
        return toBuilder().status(BudgetRecommendationStatus.REJECTED).build();
    }

    /** Returns a builder pre-populated with current values. */
    public Builder toBuilder() {
        return new Builder()
            .recommendationId(recommendationId)
            .workspaceId(workspaceId)
            .strategyId(strategyId)
            .totalMonthlyCap(totalMonthlyCap)
            .changeThresholdPct(changeThresholdPct)
            .channelAllocations(channelAllocations)
            .rationale(rationale)
            .assumptions(assumptions)
            .modelVersion(modelVersion)
            .status(status)
            .generatedAt(generatedAt)
            .generatedBy(generatedBy)
            .approvedAt(approvedAt)
            .approvedBy(approvedBy);
    }

    /** Returns a new {@link Builder}. */
    public static Builder builder() { return new Builder(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BudgetRecommendation other)) return false;
        return recommendationId.equals(other.recommendationId) && workspaceId.equals(other.workspaceId);
    }

    @Override
    public int hashCode() { return Objects.hash(recommendationId, workspaceId); }

    @Override
    public String toString() {
        return "BudgetRecommendation{id=" + recommendationId + ", status=" + status
            + ", totalMonthlyCap=" + totalMonthlyCap + '}';
    }

    /** Fluent builder. */
    public static final class Builder {
        private String recommendationId;
        private DmWorkspaceId workspaceId;
        private String strategyId;
        private double totalMonthlyCap;
        private double changeThresholdPct = 10.0;
        private List<BudgetChannelAllocation> channelAllocations = new ArrayList<>();
        private String rationale;
        private String assumptions = "";
        private String modelVersion;
        private BudgetRecommendationStatus status;
        private Instant generatedAt;
        private String generatedBy;
        private Instant approvedAt;
        private String approvedBy;

        private Builder() { }

        public Builder recommendationId(String id) { this.recommendationId = id; return this; }
        public Builder workspaceId(DmWorkspaceId w) { this.workspaceId = w; return this; }
        public Builder strategyId(String s) { this.strategyId = s; return this; }
        public Builder totalMonthlyCap(double c) { this.totalMonthlyCap = c; return this; }
        public Builder changeThresholdPct(double p) { this.changeThresholdPct = p; return this; }
        public Builder channelAllocations(List<BudgetChannelAllocation> a) {
            this.channelAllocations = new ArrayList<>(a); return this;
        }
        public Builder rationale(String r) { this.rationale = r; return this; }
        public Builder assumptions(String a) { this.assumptions = a; return this; }
        public Builder modelVersion(String v) { this.modelVersion = v; return this; }
        public Builder status(BudgetRecommendationStatus s) { this.status = s; return this; }
        public Builder generatedAt(Instant t) { this.generatedAt = t; return this; }
        public Builder generatedBy(String b) { this.generatedBy = b; return this; }
        public Builder approvedAt(Instant t) { this.approvedAt = t; return this; }
        public Builder approvedBy(String b) { this.approvedBy = b; return this; }

        /** Builds the recommendation. */
        public BudgetRecommendation build() { return new BudgetRecommendation(this); }
    }
}
