package com.ghatana.digitalmarketing.domain.strategy;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.ai.AiProvenance;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * A 30-day marketing strategy document scoped to a workspace.
 *
 * <p>Strategies start as DRAFT, require human approval before execution commands are created,
 * and become immutable once approved.</p>
 *
 * <p>P1-029: Includes AI provenance tracking for model attribution and reproducibility.</p>
 *
 * @doc.type class
 * @doc.purpose Aggregate root representing a workspace-scoped 30-day marketing strategy
 * @doc.layer product
 * @doc.pattern AggregateRoot
 */
public final class MarketingStrategy {

    private final String strategyId;
    private final DmWorkspaceId workspaceId;
    private final StrategyStatus status;
    private final List<StrategyGoal> goals;
    private final List<CampaignPlan> channelPlans;
    private final double budgetCap;
    private final String rationale;
    private final String assumptions;
    private final String measurementPlan;
    private final String contentPlan;
    private final String modelVersion;
    private final AiProvenance aiProvenance;
    private final Instant generatedAt;
    private final String generatedBy;
    private final Instant approvedAt;
    private final String approvedBy;

    private MarketingStrategy(Builder builder) {
        this.strategyId = builder.strategyId;
        this.workspaceId = builder.workspaceId;
        this.status = builder.status;
        this.goals = List.copyOf(builder.goals);
        this.channelPlans = List.copyOf(builder.channelPlans);
        this.budgetCap = builder.budgetCap;
        this.rationale = builder.rationale;
        this.assumptions = builder.assumptions;
        this.measurementPlan = builder.measurementPlan;
        this.contentPlan = builder.contentPlan;
        this.modelVersion = builder.modelVersion;
        this.aiProvenance = builder.aiProvenance;
        this.generatedAt = builder.generatedAt;
        this.generatedBy = builder.generatedBy;
        this.approvedAt = builder.approvedAt;
        this.approvedBy = builder.approvedBy;
    }

    /**
     * Returns a new strategy instance with status APPROVED.
     * Only a PENDING_APPROVAL strategy may be approved.
     *
     * @param approvedBy  identity of the approver
     * @param approvedAt  timestamp of approval
     * @return approved strategy copy
     * @throws IllegalStateException if strategy is not in PENDING_APPROVAL status
     */
    public MarketingStrategy approve(String approvedBy, Instant approvedAt) {
        Objects.requireNonNull(approvedBy, "approvedBy must not be null");
        if (approvedBy.isBlank()) {
            throw new IllegalArgumentException("approvedBy must not be blank");
        }
        Objects.requireNonNull(approvedAt, "approvedAt must not be null");
        if (status != StrategyStatus.PENDING_APPROVAL) {
            throw new IllegalStateException(
                "Only a PENDING_APPROVAL strategy can be approved. Current status: " + status);
        }
        return builder()
            .strategyId(strategyId)
            .workspaceId(workspaceId)
            .status(StrategyStatus.APPROVED)
            .goals(goals)
            .channelPlans(channelPlans)
            .budgetCap(budgetCap)
            .rationale(rationale)
            .assumptions(assumptions)
            .measurementPlan(measurementPlan)
            .contentPlan(contentPlan)
            .modelVersion(modelVersion)
            .aiProvenance(aiProvenance)
            .generatedAt(generatedAt)
            .generatedBy(generatedBy)
            .approvedAt(approvedAt)
            .approvedBy(approvedBy)
            .build();
    }

    /**
     * Returns a new strategy instance with status PENDING_APPROVAL.
     * Only a DRAFT strategy may be submitted.
     *
     * @return pending-approval strategy copy
     * @throws IllegalStateException if strategy is not in DRAFT status
     */
    public MarketingStrategy submitForApproval() {
        if (status != StrategyStatus.DRAFT) {
            throw new IllegalStateException(
                "Only a DRAFT strategy can be submitted for approval. Current status: " + status);
        }
        return builder()
            .strategyId(strategyId)
            .workspaceId(workspaceId)
            .status(StrategyStatus.PENDING_APPROVAL)
            .goals(goals)
            .channelPlans(channelPlans)
            .budgetCap(budgetCap)
            .rationale(rationale)
            .assumptions(assumptions)
            .measurementPlan(measurementPlan)
            .contentPlan(contentPlan)
            .modelVersion(modelVersion)
            .aiProvenance(aiProvenance)
            .generatedAt(generatedAt)
            .generatedBy(generatedBy)
            .approvedAt(null)
            .approvedBy(null)
            .build();
    }

    public String getStrategyId() {
        return strategyId;
    }

    public DmWorkspaceId getWorkspaceId() {
        return workspaceId;
    }

    public StrategyStatus getStatus() {
        return status;
    }

    public List<StrategyGoal> getGoals() {
        return goals;
    }

    public List<CampaignPlan> getChannelPlans() {
        return channelPlans;
    }

    public double getBudgetCap() {
        return budgetCap;
    }

    public String getRationale() {
        return rationale;
    }

    public String getAssumptions() {
        return assumptions;
    }

    public String getMeasurementPlan() {
        return measurementPlan;
    }

    public String getContentPlan() {
        return contentPlan;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public AiProvenance getAiProvenance() {
        return aiProvenance;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public String getGeneratedBy() {
        return generatedBy;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    /**
     * Returns a new Builder instance.
     *
     * @return builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link MarketingStrategy}.
     */
    public static final class Builder {
        private String strategyId;
        private DmWorkspaceId workspaceId;
        private StrategyStatus status;
        private List<StrategyGoal> goals = List.of();
        private List<CampaignPlan> channelPlans = List.of();
        private double budgetCap;
        private String rationale;
        private String assumptions;
        private String measurementPlan;
        private String contentPlan;
        private String modelVersion;
        private AiProvenance aiProvenance;
        private Instant generatedAt;
        private String generatedBy;
        private Instant approvedAt;
        private String approvedBy;

        private Builder() {
        }

        /**
         * Sets the strategy identifier.
         *
         * @param strategyId non-blank strategy identifier
         * @return this builder
         */
        public Builder strategyId(String strategyId) {
            this.strategyId = strategyId;
            return this;
        }

        /**
         * Sets the owning workspace.
         *
         * @param workspaceId non-null workspace
         * @return this builder
         */
        public Builder workspaceId(DmWorkspaceId workspaceId) {
            this.workspaceId = workspaceId;
            return this;
        }

        /**
         * Sets the lifecycle status.
         *
         * @param status non-null status
         * @return this builder
         */
        public Builder status(StrategyStatus status) {
            this.status = status;
            return this;
        }

        /**
         * Sets the strategy goals.
         *
         * @param goals non-null goal list
         * @return this builder
         */
        public Builder goals(List<StrategyGoal> goals) {
            this.goals = goals;
            return this;
        }

        /**
         * Sets the channel-specific campaign plans.
         *
         * @param channelPlans non-null plan list
         * @return this builder
         */
        public Builder channelPlans(List<CampaignPlan> channelPlans) {
            this.channelPlans = channelPlans;
            return this;
        }

        /**
         * Sets the total budget cap in currency minor units.
         *
         * @param budgetCap non-negative budget cap
         * @return this builder
         */
        public Builder budgetCap(double budgetCap) {
            this.budgetCap = budgetCap;
            return this;
        }

        /**
         * Sets the strategy rationale.
         *
         * @param rationale non-blank rationale text
         * @return this builder
         */
        public Builder rationale(String rationale) {
            this.rationale = rationale;
            return this;
        }

        /**
         * Sets the assumptions underlying the strategy.
         *
         * @param assumptions non-blank assumptions text
         * @return this builder
         */
        public Builder assumptions(String assumptions) {
            this.assumptions = assumptions;
            return this;
        }

        /**
         * Sets the measurement plan for the strategy.
         *
         * @param measurementPlan non-blank measurement plan
         * @return this builder
         */
        public Builder measurementPlan(String measurementPlan) {
            this.measurementPlan = measurementPlan;
            return this;
        }

        /**
         * Sets the content plan for the strategy.
         *
         * @param contentPlan non-blank content plan
         * @return this builder
         */
        public Builder contentPlan(String contentPlan) {
            this.contentPlan = contentPlan;
            return this;
        }

        /**
         * Sets the model/generator version.
         *
         * @param modelVersion non-blank version string
         * @return this builder
         */
        public Builder modelVersion(String modelVersion) {
            this.modelVersion = modelVersion;
            return this;
        }

        /**
         * P1-029: Sets the AI provenance record.
         *
         * @param aiProvenance AI provenance record
         * @return this builder
         */
        public Builder aiProvenance(AiProvenance aiProvenance) {
            this.aiProvenance = aiProvenance;
            return this;
        }

        /**
         * Sets the generation timestamp.
         *
         * @param generatedAt non-null timestamp
         * @return this builder
         */
        public Builder generatedAt(Instant generatedAt) {
            this.generatedAt = generatedAt;
            return this;
        }

        /**
         * Sets the identity of the generator.
         *
         * @param generatedBy non-blank generator identity
         * @return this builder
         */
        public Builder generatedBy(String generatedBy) {
            this.generatedBy = generatedBy;
            return this;
        }

        /**
         * Sets the approval timestamp (nullable until approved).
         *
         * @param approvedAt approval timestamp or null
         * @return this builder
         */
        public Builder approvedAt(Instant approvedAt) {
            this.approvedAt = approvedAt;
            return this;
        }

        /**
         * Sets the approver identity (nullable until approved).
         *
         * @param approvedBy approver identity or null
         * @return this builder
         */
        public Builder approvedBy(String approvedBy) {
            this.approvedBy = approvedBy;
            return this;
        }

        /**
         * Builds and validates a {@link MarketingStrategy}.
         *
         * @return validated marketing strategy
         */
        public MarketingStrategy build() {
            Objects.requireNonNull(strategyId, "strategyId must not be null");
            if (strategyId.isBlank()) {
                throw new IllegalArgumentException("strategyId must not be blank");
            }
            Objects.requireNonNull(workspaceId, "workspaceId must not be null");
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(goals, "goals must not be null");
            Objects.requireNonNull(channelPlans, "channelPlans must not be null");
            if (budgetCap < 0) {
                throw new IllegalArgumentException("budgetCap must not be negative");
            }
            Objects.requireNonNull(rationale, "rationale must not be null");
            if (rationale.isBlank()) {
                throw new IllegalArgumentException("rationale must not be blank");
            }
            Objects.requireNonNull(assumptions, "assumptions must not be null");
            if (assumptions.isBlank()) {
                throw new IllegalArgumentException("assumptions must not be blank");
            }
            Objects.requireNonNull(measurementPlan, "measurementPlan must not be null");
            if (measurementPlan.isBlank()) {
                throw new IllegalArgumentException("measurementPlan must not be blank");
            }
            Objects.requireNonNull(contentPlan, "contentPlan must not be null");
            if (contentPlan.isBlank()) {
                throw new IllegalArgumentException("contentPlan must not be blank");
            }
            Objects.requireNonNull(modelVersion, "modelVersion must not be null");
            if (modelVersion.isBlank()) {
                throw new IllegalArgumentException("modelVersion must not be blank");
            }
            Objects.requireNonNull(generatedAt, "generatedAt must not be null");
            Objects.requireNonNull(generatedBy, "generatedBy must not be null");
            if (generatedBy.isBlank()) {
                throw new IllegalArgumentException("generatedBy must not be blank");
            }
            return new MarketingStrategy(this);
        }
    }
}
