package com.ghatana.digitalmarketing.domain.proposal;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Aggregate root representing a DMOS client proposal.
 *
 * <p>A {@code Proposal} is generated from an approved 30-day strategy and a versioned
 * template. It bundles deliverables, pricing options, assumptions, timeline, legal
 * disclaimers, and a measurement plan. Proposals must be reviewed and approved by a
 * human before they are presented or exported to the client.</p>
 *
 * <p>State transitions:</p>
 * <pre>
 *   DRAFT → PENDING_REVIEW → APPROVED
 * </pre>
 *
 * <p>Instances are immutable; state-changing methods return new instances.</p>
 *
 * @doc.type class
 * @doc.purpose Proposal aggregate root with human-review workflow for F1-015
 * @doc.layer product
 * @doc.pattern AggregateRoot
 */
public final class Proposal {

    private final String proposalId;
    private final DmWorkspaceId workspaceId;
    private final String strategyId;
    private final String templateId;
    private final String templateVersion;
    private final List<ProposalDeliverable> deliverables;
    private final List<PricingOption> pricingOptions;
    private final String assumptions;
    private final String timeline;
    private final String rationale;
    private final String disclaimer;
    private final String exclusions;
    private final String measurementPlan;
    private final String modelVersion;
    private final ProposalStatus status;
    private final Instant generatedAt;
    private final String generatedBy;
    private final Instant reviewedAt;
    private final String approvedBy;

    private Proposal(Builder b) {
        this.proposalId       = Objects.requireNonNull(b.proposalId,       "proposalId must not be null");
        this.workspaceId      = Objects.requireNonNull(b.workspaceId,      "workspaceId must not be null");
        this.strategyId       = Objects.requireNonNull(b.strategyId,       "strategyId must not be null");
        this.templateId       = Objects.requireNonNull(b.templateId,       "templateId must not be null");
        this.templateVersion  = Objects.requireNonNull(b.templateVersion,  "templateVersion must not be null");
        this.deliverables     = Collections.unmodifiableList(
            new ArrayList<>(Objects.requireNonNull(b.deliverables, "deliverables must not be null")));
        this.pricingOptions   = Collections.unmodifiableList(
            new ArrayList<>(Objects.requireNonNull(b.pricingOptions, "pricingOptions must not be null")));
        this.assumptions      = Objects.requireNonNull(b.assumptions,      "assumptions must not be null");
        this.timeline         = Objects.requireNonNull(b.timeline,         "timeline must not be null");
        this.rationale        = Objects.requireNonNull(b.rationale,        "rationale must not be null");
        this.disclaimer       = Objects.requireNonNull(b.disclaimer,       "disclaimer must not be null");
        this.exclusions       = Objects.requireNonNull(b.exclusions,       "exclusions must not be null");
        this.measurementPlan  = Objects.requireNonNull(b.measurementPlan,  "measurementPlan must not be null");
        this.modelVersion     = Objects.requireNonNull(b.modelVersion,     "modelVersion must not be null");
        this.status           = Objects.requireNonNull(b.status,           "status must not be null");
        this.generatedAt      = Objects.requireNonNull(b.generatedAt,      "generatedAt must not be null");
        this.generatedBy      = Objects.requireNonNull(b.generatedBy,      "generatedBy must not be null");
        this.reviewedAt       = b.reviewedAt;
        this.approvedBy       = b.approvedBy;

        if (proposalId.isBlank()) {
            throw new IllegalArgumentException("proposalId must not be blank");
        }
        if (strategyId.isBlank()) {
            throw new IllegalArgumentException("strategyId must not be blank");
        }
        if (templateId.isBlank()) {
            throw new IllegalArgumentException("templateId must not be blank");
        }
        if (templateVersion.isBlank()) {
            throw new IllegalArgumentException("templateVersion must not be blank");
        }
        if (deliverables.isEmpty()) {
            throw new IllegalArgumentException("deliverables must not be empty");
        }
        if (pricingOptions.isEmpty()) {
            throw new IllegalArgumentException("pricingOptions must not be empty");
        }
        if (modelVersion.isBlank()) {
            throw new IllegalArgumentException("modelVersion must not be blank");
        }
    }

    /** Returns the unique proposal identifier. Never null or blank. */
    public String getProposalId() {
        return proposalId;
    }

    /** Returns the owning workspace. Never null. */
    public DmWorkspaceId getWorkspaceId() {
        return workspaceId;
    }

    /** Returns the source strategy ID. Never null or blank. */
    public String getStrategyId() {
        return strategyId;
    }

    /** Returns the template ID used to generate this proposal. Never null or blank. */
    public String getTemplateId() {
        return templateId;
    }

    /** Returns the template version used. Never null or blank. */
    public String getTemplateVersion() {
        return templateVersion;
    }

    /** Returns the list of deliverables. Never null or empty. Immutable. */
    public List<ProposalDeliverable> getDeliverables() {
        return deliverables;
    }

    /** Returns the list of pricing options. Never null or empty. Immutable. */
    public List<PricingOption> getPricingOptions() {
        return pricingOptions;
    }

    /** Returns the assumptions underlying this proposal. Never null. */
    public String getAssumptions() {
        return assumptions;
    }

    /** Returns the proposed project timeline description. Never null. */
    public String getTimeline() {
        return timeline;
    }

    /** Returns the rationale for the proposal approach. Never null. */
    public String getRationale() {
        return rationale;
    }

    /** Returns the legal disclaimer text from the template. Never null. */
    public String getDisclaimer() {
        return disclaimer;
    }

    /** Returns the exclusions section text from the template. Never null. */
    public String getExclusions() {
        return exclusions;
    }

    /** Returns the measurement plan text. Never null. */
    public String getMeasurementPlan() {
        return measurementPlan;
    }

    /** Returns the generator model version. Never null or blank. */
    public String getModelVersion() {
        return modelVersion;
    }

    /** Returns the current proposal status. Never null. */
    public ProposalStatus getStatus() {
        return status;
    }

    /** Returns when this proposal was generated. Never null. */
    public Instant getGeneratedAt() {
        return generatedAt;
    }

    /** Returns the principal who generated this proposal. Never null. */
    public String getGeneratedBy() {
        return generatedBy;
    }

    /** Returns when this proposal was reviewed/approved, or {@code null} if not yet reviewed. */
    public Instant getReviewedAt() {
        return reviewedAt;
    }

    /** Returns the approver principal, or {@code null} if not yet approved. */
    public String getApprovedBy() {
        return approvedBy;
    }

    /**
     * Returns a copy with status {@link ProposalStatus#PENDING_REVIEW}.
     * Only valid from {@link ProposalStatus#DRAFT}.
     *
     * @throws IllegalStateException if status is not DRAFT
     */
    public Proposal submitForReview() {
        if (status != ProposalStatus.DRAFT) {
            throw new IllegalStateException(
                "Cannot submit proposal for review from status: " + status);
        }
        return toBuilder().status(ProposalStatus.PENDING_REVIEW).build();
    }

    /**
     * Returns an approved copy of this proposal.
     * Only valid from {@link ProposalStatus#PENDING_REVIEW}.
     *
     * @param approvedBy the approver principal; must not be null or blank
     * @param reviewedAt the approval timestamp; must not be null
     * @throws IllegalStateException    if status is not PENDING_REVIEW
     * @throws IllegalArgumentException if approvedBy is blank
     */
    public Proposal approve(String approvedBy, Instant reviewedAt) {
        Objects.requireNonNull(approvedBy, "approvedBy must not be null");
        Objects.requireNonNull(reviewedAt, "reviewedAt must not be null");
        if (approvedBy.isBlank()) {
            throw new IllegalArgumentException("approvedBy must not be blank");
        }
        if (status != ProposalStatus.PENDING_REVIEW) {
            throw new IllegalStateException(
                "Cannot approve proposal from status: " + status);
        }
        return toBuilder()
            .status(ProposalStatus.APPROVED)
            .approvedBy(approvedBy)
            .reviewedAt(reviewedAt)
            .build();
    }

    /** Returns a new {@link Builder} pre-populated with this instance's values. */
    public Builder toBuilder() {
        return new Builder()
            .proposalId(proposalId)
            .workspaceId(workspaceId)
            .strategyId(strategyId)
            .templateId(templateId)
            .templateVersion(templateVersion)
            .deliverables(new ArrayList<>(deliverables))
            .pricingOptions(new ArrayList<>(pricingOptions))
            .assumptions(assumptions)
            .timeline(timeline)
            .rationale(rationale)
            .disclaimer(disclaimer)
            .exclusions(exclusions)
            .measurementPlan(measurementPlan)
            .modelVersion(modelVersion)
            .status(status)
            .generatedAt(generatedAt)
            .generatedBy(generatedBy)
            .reviewedAt(reviewedAt)
            .approvedBy(approvedBy);
    }

    /** Returns a new builder for constructing a {@link Proposal}. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Mutable builder for {@link Proposal}.
     */
    public static final class Builder {

        private String proposalId;
        private DmWorkspaceId workspaceId;
        private String strategyId;
        private String templateId;
        private String templateVersion;
        private List<ProposalDeliverable> deliverables;
        private List<PricingOption> pricingOptions;
        private String assumptions;
        private String timeline;
        private String rationale;
        private String disclaimer;
        private String exclusions;
        private String measurementPlan;
        private String modelVersion;
        private ProposalStatus status;
        private Instant generatedAt;
        private String generatedBy;
        private Instant reviewedAt;
        private String approvedBy;

        private Builder() {
        }

        /** Sets the proposal ID. */
        public Builder proposalId(String proposalId) {
            this.proposalId = proposalId;
            return this;
        }

        /** Sets the workspace ID. */
        public Builder workspaceId(DmWorkspaceId workspaceId) {
            this.workspaceId = workspaceId;
            return this;
        }

        /** Sets the source strategy ID. */
        public Builder strategyId(String strategyId) {
            this.strategyId = strategyId;
            return this;
        }

        /** Sets the template ID. */
        public Builder templateId(String templateId) {
            this.templateId = templateId;
            return this;
        }

        /** Sets the template version. */
        public Builder templateVersion(String templateVersion) {
            this.templateVersion = templateVersion;
            return this;
        }

        /** Sets the deliverables list. */
        public Builder deliverables(List<ProposalDeliverable> deliverables) {
            this.deliverables = deliverables;
            return this;
        }

        /** Sets the pricing options list. */
        public Builder pricingOptions(List<PricingOption> pricingOptions) {
            this.pricingOptions = pricingOptions;
            return this;
        }

        /** Sets the assumptions text. */
        public Builder assumptions(String assumptions) {
            this.assumptions = assumptions;
            return this;
        }

        /** Sets the timeline description. */
        public Builder timeline(String timeline) {
            this.timeline = timeline;
            return this;
        }

        /** Sets the rationale text. */
        public Builder rationale(String rationale) {
            this.rationale = rationale;
            return this;
        }

        /** Sets the legal disclaimer text. */
        public Builder disclaimer(String disclaimer) {
            this.disclaimer = disclaimer;
            return this;
        }

        /** Sets the exclusions text. */
        public Builder exclusions(String exclusions) {
            this.exclusions = exclusions;
            return this;
        }

        /** Sets the measurement plan text. */
        public Builder measurementPlan(String measurementPlan) {
            this.measurementPlan = measurementPlan;
            return this;
        }

        /** Sets the model version. */
        public Builder modelVersion(String modelVersion) {
            this.modelVersion = modelVersion;
            return this;
        }

        /** Sets the current status. */
        public Builder status(ProposalStatus status) {
            this.status = status;
            return this;
        }

        /** Sets the generation timestamp. */
        public Builder generatedAt(Instant generatedAt) {
            this.generatedAt = generatedAt;
            return this;
        }

        /** Sets the generating principal. */
        public Builder generatedBy(String generatedBy) {
            this.generatedBy = generatedBy;
            return this;
        }

        /** Sets the review/approval timestamp (nullable). */
        public Builder reviewedAt(Instant reviewedAt) {
            this.reviewedAt = reviewedAt;
            return this;
        }

        /** Sets the approver principal (nullable). */
        public Builder approvedBy(String approvedBy) {
            this.approvedBy = approvedBy;
            return this;
        }

        /** Builds the {@link Proposal}. Validates all required fields. */
        public Proposal build() {
            return new Proposal(this);
        }
    }
}
