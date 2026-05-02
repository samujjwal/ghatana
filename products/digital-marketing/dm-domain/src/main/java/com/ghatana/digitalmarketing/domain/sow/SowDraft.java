package com.ghatana.digitalmarketing.domain.sow;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Aggregate root representing a DMOS Statement of Work draft.
 *
 * <p>A {@code SowDraft} is assembled from approved clause library entries, a
 * proposal reference, assumptions, and exclusions. It carries a mandatory legal
 * disclaimer and a list of auto-generated risk flags. Drafts must be reviewed and
 * approved by a human before they can be exported to the client.</p>
 *
 * <p>State transitions:</p>
 * <pre>
 *   DRAFT → PENDING_REVIEW → APPROVED → EXPORTED
 * </pre>
 *
 * <p>Instances are immutable; state-changing methods return new instances.</p>
 *
 * @doc.type class
 * @doc.purpose SOW draft aggregate root with human-review workflow for F1-016
 * @doc.layer product
 * @doc.pattern AggregateRoot
 */
public final class SowDraft {

    /** Mandatory disclaimer stamped into every SOW draft. */
    public static final String LEGAL_DISCLAIMER =
            "This document is a draft only and does not constitute legal advice. " +
            "It must be reviewed by qualified legal counsel before execution. " +
            "Do not distribute externally without obtaining proper approval.";

    private final String sowId;
    private final DmWorkspaceId workspaceId;
    private final String proposalId;
    private final String templateVersion;
    private final List<SowClause> selectedClauses;
    private final List<SowRiskFlag> riskFlags;
    private final String assumptions;
    private final String exclusions;
    private final String disclaimer;
    private final String modelVersion;
    private final SowStatus status;
    private final String approvedBy;
    private final Instant approvedAt;
    private final Instant createdAt;

    private SowDraft(Builder b) {
        this.sowId           = Objects.requireNonNull(b.sowId,           "sowId must not be null");
        this.workspaceId     = Objects.requireNonNull(b.workspaceId,     "workspaceId must not be null");
        this.proposalId      = Objects.requireNonNull(b.proposalId,      "proposalId must not be null");
        this.templateVersion = Objects.requireNonNull(b.templateVersion, "templateVersion must not be null");
        this.selectedClauses = Collections.unmodifiableList(
                new ArrayList<>(Objects.requireNonNull(b.selectedClauses, "selectedClauses must not be null")));
        this.riskFlags       = Collections.unmodifiableList(
                new ArrayList<>(Objects.requireNonNull(b.riskFlags, "riskFlags must not be null")));
        this.assumptions     = Objects.requireNonNull(b.assumptions, "assumptions must not be null");
        this.exclusions      = Objects.requireNonNull(b.exclusions, "exclusions must not be null");
        this.disclaimer      = Objects.requireNonNull(b.disclaimer, "disclaimer must not be null");
        this.modelVersion    = Objects.requireNonNull(b.modelVersion, "modelVersion must not be null");
        this.status          = Objects.requireNonNull(b.status, "status must not be null");
        this.approvedBy      = b.approvedBy;
        this.approvedAt      = b.approvedAt;
        this.createdAt       = Objects.requireNonNull(b.createdAt, "createdAt must not be null");

        if (sowId.isBlank()) {
            throw new IllegalArgumentException("sowId must not be blank");
        }
        if (proposalId.isBlank()) {
            throw new IllegalArgumentException("proposalId must not be blank");
        }
        if (templateVersion.isBlank()) {
            throw new IllegalArgumentException("templateVersion must not be blank");
        }
        if (modelVersion.isBlank()) {
            throw new IllegalArgumentException("modelVersion must not be blank");
        }
        if (selectedClauses.isEmpty()) {
            throw new IllegalArgumentException("selectedClauses must not be empty");
        }
    }

    // ---- state transitions ----

    /**
     * Transitions this draft from {@link SowStatus#DRAFT} to
     * {@link SowStatus#PENDING_REVIEW}.
     *
     * @return a new {@code SowDraft} in {@code PENDING_REVIEW} status
     * @throws IllegalStateException if the draft is not currently in {@code DRAFT} status
     */
    public SowDraft submitForReview() {
        if (status != SowStatus.DRAFT) {
            throw new IllegalStateException(
                    "Cannot submit for review; current status is " + status);
        }
        return toBuilder().status(SowStatus.PENDING_REVIEW).build();
    }

    /**
     * Transitions this draft from {@link SowStatus#PENDING_REVIEW} to
     * {@link SowStatus#APPROVED}.
     *
     * @param approvedBy  the principal ID of the approver
     * @param approvedAt  the instant of approval
     * @return a new {@code SowDraft} in {@code APPROVED} status
     * @throws IllegalStateException if the draft is not in {@code PENDING_REVIEW} status
     */
    public SowDraft approve(String approvedBy, Instant approvedAt) {
        if (status != SowStatus.PENDING_REVIEW) {
            throw new IllegalStateException(
                    "Cannot approve; current status is " + status);
        }
        Objects.requireNonNull(approvedBy, "approvedBy must not be null");
        Objects.requireNonNull(approvedAt, "approvedAt must not be null");
        return toBuilder()
                .status(SowStatus.APPROVED)
                .approvedBy(approvedBy)
                .approvedAt(approvedAt)
                .build();
    }

    /**
     * Transitions this draft from {@link SowStatus#APPROVED} to
     * {@link SowStatus#EXPORTED}.
     *
     * @return a new {@code SowDraft} in {@code EXPORTED} status
     * @throws IllegalStateException if the draft has not been approved
     */
    public SowDraft export() {
        if (status != SowStatus.APPROVED) {
            throw new IllegalStateException(
                    "Cannot export; current status is " + status + ". SOW must be approved first.");
        }
        return toBuilder().status(SowStatus.EXPORTED).build();
    }

    // ---- getters ----

    public String getSowId() { return sowId; }
    public DmWorkspaceId getWorkspaceId() { return workspaceId; }
    public String getProposalId() { return proposalId; }
    public String getTemplateVersion() { return templateVersion; }
    public List<SowClause> getSelectedClauses() { return selectedClauses; }
    public List<SowRiskFlag> getRiskFlags() { return riskFlags; }
    public String getAssumptions() { return assumptions; }
    public String getExclusions() { return exclusions; }
    public String getDisclaimer() { return disclaimer; }
    public String getModelVersion() { return modelVersion; }
    public SowStatus getStatus() { return status; }
    public String getApprovedBy() { return approvedBy; }
    public Instant getApprovedAt() { return approvedAt; }
    public Instant getCreatedAt() { return createdAt; }

    // ---- builder ----

    public Builder toBuilder() {
        return builder()
                .sowId(sowId)
                .workspaceId(workspaceId)
                .proposalId(proposalId)
                .templateVersion(templateVersion)
                .selectedClauses(selectedClauses)
                .riskFlags(riskFlags)
                .assumptions(assumptions)
                .exclusions(exclusions)
                .disclaimer(disclaimer)
                .modelVersion(modelVersion)
                .status(status)
                .approvedBy(approvedBy)
                .approvedAt(approvedAt)
                .createdAt(createdAt);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Fluent builder for {@link SowDraft}. */
    public static final class Builder {
        private String sowId;
        private DmWorkspaceId workspaceId;
        private String proposalId;
        private String templateVersion;
        private List<SowClause> selectedClauses = new ArrayList<>();
        private List<SowRiskFlag> riskFlags = new ArrayList<>();
        private String assumptions = "";
        private String exclusions = "";
        private String disclaimer = LEGAL_DISCLAIMER;
        private String modelVersion;
        private SowStatus status = SowStatus.DRAFT;
        private String approvedBy;
        private Instant approvedAt;
        private Instant createdAt;

        private Builder() {}

        public Builder sowId(String sowId) { this.sowId = sowId; return this; }
        public Builder workspaceId(DmWorkspaceId workspaceId) { this.workspaceId = workspaceId; return this; }
        public Builder proposalId(String proposalId) { this.proposalId = proposalId; return this; }
        public Builder templateVersion(String templateVersion) { this.templateVersion = templateVersion; return this; }
        public Builder selectedClauses(List<SowClause> selectedClauses) { this.selectedClauses = new ArrayList<>(selectedClauses); return this; }
        public Builder riskFlags(List<SowRiskFlag> riskFlags) { this.riskFlags = new ArrayList<>(riskFlags); return this; }
        public Builder assumptions(String assumptions) { this.assumptions = assumptions; return this; }
        public Builder exclusions(String exclusions) { this.exclusions = exclusions; return this; }
        public Builder disclaimer(String disclaimer) { this.disclaimer = disclaimer; return this; }
        public Builder modelVersion(String modelVersion) { this.modelVersion = modelVersion; return this; }
        public Builder status(SowStatus status) { this.status = status; return this; }
        public Builder approvedBy(String approvedBy) { this.approvedBy = approvedBy; return this; }
        public Builder approvedAt(Instant approvedAt) { this.approvedAt = approvedAt; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }

        public SowDraft build() {
            return new SowDraft(this);
        }
    }
}
