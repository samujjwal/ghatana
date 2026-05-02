package com.ghatana.digitalmarketing.domain.intake;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable domain entity for structured business intake questionnaire capture.
 *
 * @doc.type class
 * @doc.purpose DMOS structured intake profile for questionnaire draft and submission workflows
 * @doc.layer product
 * @doc.pattern Entity
 */
public final class BusinessIntakeProfile {

    private final String intakeId;
    private final DmWorkspaceId workspaceId;
    private final String businessName;
    private final String websiteUrl;
    private final String offerSummary;
    private final String targetAudience;
    private final String primaryGeography;
    private final BigDecimal monthlyBudgetAmount;
    private final List<String> competitorDomains;
    private final List<String> constraints;
    private final String growthGoal;
    private final String riskTolerance;
    private final String aiSummary;
    private final double aiConfidenceScore;
    private final List<String> aiUnknowns;
    private final IntakeStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final String createdBy;
    private final Instant submittedAt;

    private BusinessIntakeProfile(Builder builder) {
        this.intakeId = Objects.requireNonNull(builder.intakeId, "intakeId must not be null");
        this.workspaceId = Objects.requireNonNull(builder.workspaceId, "workspaceId must not be null");
        this.businessName = blankToEmpty(builder.businessName);
        this.websiteUrl = blankToEmpty(builder.websiteUrl);
        this.offerSummary = blankToEmpty(builder.offerSummary);
        this.targetAudience = blankToEmpty(builder.targetAudience);
        this.primaryGeography = blankToEmpty(builder.primaryGeography);
        this.monthlyBudgetAmount = builder.monthlyBudgetAmount;
        this.competitorDomains = builder.competitorDomains != null ? List.copyOf(builder.competitorDomains) : List.of();
        this.constraints = builder.constraints != null ? List.copyOf(builder.constraints) : List.of();
        this.growthGoal = blankToEmpty(builder.growthGoal);
        this.riskTolerance = blankToEmpty(builder.riskTolerance);
        this.aiSummary = blankToEmpty(builder.aiSummary);
        this.aiConfidenceScore = builder.aiConfidenceScore;
        this.aiUnknowns = builder.aiUnknowns != null ? List.copyOf(builder.aiUnknowns) : List.of();
        this.status = Objects.requireNonNull(builder.status, "status must not be null");
        this.createdAt = Objects.requireNonNull(builder.createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(builder.updatedAt, "updatedAt must not be null");
        this.createdBy = Objects.requireNonNull(builder.createdBy, "createdBy must not be null");
        this.submittedAt = builder.submittedAt;

        if (this.intakeId.isBlank()) {
            throw new IllegalArgumentException("intakeId must not be blank");
        }
        if (this.createdBy.isBlank()) {
            throw new IllegalArgumentException("createdBy must not be blank");
        }
        if (this.aiConfidenceScore < 0.0 || this.aiConfidenceScore > 1.0) {
            throw new IllegalArgumentException("aiConfidenceScore must be between 0.0 and 1.0");
        }
    }

    public String getIntakeId() {
        return intakeId;
    }

    public DmWorkspaceId getWorkspaceId() {
        return workspaceId;
    }

    public String getBusinessName() {
        return businessName;
    }

    public String getWebsiteUrl() {
        return websiteUrl;
    }

    public String getOfferSummary() {
        return offerSummary;
    }

    public String getTargetAudience() {
        return targetAudience;
    }

    public String getPrimaryGeography() {
        return primaryGeography;
    }

    public BigDecimal getMonthlyBudgetAmount() {
        return monthlyBudgetAmount;
    }

    public List<String> getCompetitorDomains() {
        return competitorDomains;
    }

    public List<String> getConstraints() {
        return constraints;
    }

    public String getGrowthGoal() {
        return growthGoal;
    }

    public String getRiskTolerance() {
        return riskTolerance;
    }

    public String getAiSummary() {
        return aiSummary;
    }

    public double getAiConfidenceScore() {
        return aiConfidenceScore;
    }

    public List<String> getAiUnknowns() {
        return aiUnknowns;
    }

    public IntakeStatus getStatus() {
        return status;
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

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    /**
     * Returns the required inputs that are still missing for final intake submission.
     */
    public Set<String> missingCriticalInputs() {
        Set<String> missing = new LinkedHashSet<>();
        if (businessName.isBlank()) {
            missing.add("businessName");
        }
        if (growthGoal.isBlank()) {
            missing.add("growthGoal");
        }
        if (offerSummary.isBlank()) {
            missing.add("offerSummary");
        }
        if (targetAudience.isBlank()) {
            missing.add("targetAudience");
        }
        if (primaryGeography.isBlank()) {
            missing.add("primaryGeography");
        }
        if (monthlyBudgetAmount == null || monthlyBudgetAmount.signum() <= 0) {
            missing.add("monthlyBudgetAmount");
        }
        return missing;
    }

    public BusinessIntakeProfile submit(String summary, double confidenceScore, List<String> unknowns, Instant now) {
        if (status != IntakeStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT intake can be submitted");
        }
        Set<String> missing = missingCriticalInputs();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Missing critical inputs: " + String.join(",", missing));
        }
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("aiSummary must not be blank");
        }

        return toBuilder()
            .aiSummary(summary)
            .aiConfidenceScore(confidenceScore)
            .aiUnknowns(unknowns != null ? unknowns : List.of())
            .status(IntakeStatus.SUBMITTED)
            .submittedAt(now)
            .updatedAt(now)
            .build();
    }

    public Builder toBuilder() {
        return new Builder()
            .intakeId(intakeId)
            .workspaceId(workspaceId)
            .businessName(businessName)
            .websiteUrl(websiteUrl)
            .offerSummary(offerSummary)
            .targetAudience(targetAudience)
            .primaryGeography(primaryGeography)
            .monthlyBudgetAmount(monthlyBudgetAmount)
            .competitorDomains(competitorDomains)
            .constraints(constraints)
            .growthGoal(growthGoal)
            .riskTolerance(riskTolerance)
            .aiSummary(aiSummary)
            .aiConfidenceScore(aiConfidenceScore)
            .aiUnknowns(aiUnknowns)
            .status(status)
            .createdAt(createdAt)
            .updatedAt(updatedAt)
            .createdBy(createdBy)
            .submittedAt(submittedAt);
    }

    public static Builder builder() {
        return new Builder();
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class Builder {
        private String intakeId;
        private DmWorkspaceId workspaceId;
        private String businessName;
        private String websiteUrl;
        private String offerSummary;
        private String targetAudience;
        private String primaryGeography;
        private BigDecimal monthlyBudgetAmount;
        private List<String> competitorDomains;
        private List<String> constraints;
        private String growthGoal;
        private String riskTolerance;
        private String aiSummary;
        private double aiConfidenceScore;
        private List<String> aiUnknowns;
        private IntakeStatus status = IntakeStatus.DRAFT;
        private Instant createdAt;
        private Instant updatedAt;
        private String createdBy;
        private Instant submittedAt;

        private Builder() {
        }

        public Builder intakeId(String intakeId) {
            this.intakeId = intakeId;
            return this;
        }

        public Builder workspaceId(DmWorkspaceId workspaceId) {
            this.workspaceId = workspaceId;
            return this;
        }

        public Builder businessName(String businessName) {
            this.businessName = businessName;
            return this;
        }

        public Builder websiteUrl(String websiteUrl) {
            this.websiteUrl = websiteUrl;
            return this;
        }

        public Builder offerSummary(String offerSummary) {
            this.offerSummary = offerSummary;
            return this;
        }

        public Builder targetAudience(String targetAudience) {
            this.targetAudience = targetAudience;
            return this;
        }

        public Builder primaryGeography(String primaryGeography) {
            this.primaryGeography = primaryGeography;
            return this;
        }

        public Builder monthlyBudgetAmount(BigDecimal monthlyBudgetAmount) {
            this.monthlyBudgetAmount = monthlyBudgetAmount;
            return this;
        }

        public Builder competitorDomains(List<String> competitorDomains) {
            this.competitorDomains = competitorDomains;
            return this;
        }

        public Builder constraints(List<String> constraints) {
            this.constraints = constraints;
            return this;
        }

        public Builder growthGoal(String growthGoal) {
            this.growthGoal = growthGoal;
            return this;
        }

        public Builder riskTolerance(String riskTolerance) {
            this.riskTolerance = riskTolerance;
            return this;
        }

        public Builder aiSummary(String aiSummary) {
            this.aiSummary = aiSummary;
            return this;
        }

        public Builder aiConfidenceScore(double aiConfidenceScore) {
            this.aiConfidenceScore = aiConfidenceScore;
            return this;
        }

        public Builder aiUnknowns(List<String> aiUnknowns) {
            this.aiUnknowns = aiUnknowns;
            return this;
        }

        public Builder status(IntakeStatus status) {
            this.status = status;
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

        public Builder submittedAt(Instant submittedAt) {
            this.submittedAt = submittedAt;
            return this;
        }

        public BusinessIntakeProfile build() {
            return new BusinessIntakeProfile(this);
        }
    }
}
