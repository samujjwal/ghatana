package com.ghatana.digitalmarketing.domain.campaign;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;

import java.time.Instant;
import java.util.Objects;

/**
 * Domain entity representing a DMOS marketing campaign.
 *
 * <p>A {@code Campaign} is the central scheduling and execution unit in DMOS.
 * It is owned by a workspace, has a defined lifecycle, and connects one or more
 * audience segments with content assets and a budget allocation.</p>
 *
 * <p>Lifecycle transitions are enforced by {@link CampaignStatus}. Illegal transitions
 * throw {@link IllegalStateException}. All state-changing methods return a new
 * {@code Campaign} instance — the entity is immutable after construction.</p>
 *
 * @doc.type class
 * @doc.purpose DMOS campaign domain entity with lifecycle state machine
 * @doc.layer product
 * @doc.pattern Entity, AggregateRoot
 */
public final class Campaign {

    private final String id;
    private final DmWorkspaceId workspaceId;
    private final String name;
    private final CampaignStatus status;
    private final CampaignType type;
    private final String objective;
    private final Long budgetCents;
    private final String startDate;
    private final String endDate;
    private final String audience;
    private final String landingPageUrl;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final String createdBy;

    private Campaign(Builder builder) {
        this.id             = Objects.requireNonNull(builder.id, "id must not be null");
        this.workspaceId    = Objects.requireNonNull(builder.workspaceId, "workspaceId must not be null");
        this.name           = Objects.requireNonNull(builder.name, "name must not be null");
        this.status         = Objects.requireNonNull(builder.status, "status must not be null");
        this.type           = Objects.requireNonNull(builder.type, "type must not be null");
        this.objective      = builder.objective;
        this.budgetCents    = builder.budgetCents;
        this.startDate      = builder.startDate;
        this.endDate        = builder.endDate;
        this.audience       = builder.audience;
        this.landingPageUrl = builder.landingPageUrl;
        this.createdAt      = Objects.requireNonNull(builder.createdAt, "createdAt must not be null");
        this.updatedAt      = Objects.requireNonNull(builder.updatedAt, "updatedAt must not be null");
        this.createdBy      = Objects.requireNonNull(builder.createdBy, "createdBy must not be null");
        if (this.id.isBlank())   throw new IllegalArgumentException("id must not be blank");
        if (this.name.isBlank()) throw new IllegalArgumentException("name must not be blank");
    }

    public String getId()              { return id; }
    public DmWorkspaceId getWorkspaceId() { return workspaceId; }
    public String getName()            { return name; }
    public CampaignStatus getStatus()  { return status; }
    public CampaignType getType()      { return type; }
    public String getObjective()       { return objective; }
    public Long getBudgetCents()       { return budgetCents; }
    public String getStartDate()       { return startDate; }
    public String getEndDate()         { return endDate; }
    public String getAudience()        { return audience; }
    public String getLandingPageUrl()  { return landingPageUrl; }
    public Instant getCreatedAt()      { return createdAt; }
    public Instant getUpdatedAt()      { return updatedAt; }
    public String getCreatedBy()       { return createdBy; }

    /**
     * Transitions the campaign to {@link CampaignStatus#PENDING_LAUNCH}.
     * Only valid from {@link CampaignStatus#DRAFT}, {@link CampaignStatus#APPROVED},
     * or {@link CampaignStatus#PAUSED}.
     *
     * @throws IllegalStateException if the campaign is not in a launchable state
     */
    public Campaign requestLaunch() {
        requireStatus("request launch", CampaignStatus.DRAFT, CampaignStatus.APPROVED, CampaignStatus.PAUSED);
        return toBuilder().status(CampaignStatus.PENDING_LAUNCH).updatedAt(Instant.now()).build();
    }

    /**
     * Transitions the campaign to {@link CampaignStatus#LAUNCHED}.
     * Only valid once launch execution has completed.
     *
     * @throws IllegalStateException if the campaign is not in a launch-running state
     */
    public Campaign markLaunched() {
        requireStatus("mark launched", CampaignStatus.PENDING_LAUNCH, CampaignStatus.LAUNCH_RUNNING);
        return toBuilder().status(CampaignStatus.LAUNCHED).updatedAt(Instant.now()).build();
    }

    /**
     * Backward-compatible alias for non-external launch paths.
     *
     * @throws IllegalStateException if the campaign is not in a launchable state
     */
    public Campaign launch() {
        return requestLaunch().markLaunched();
    }

    /** Marks durable launch execution as running. */
    public Campaign markLaunchRunning() {
        requireStatus("mark launch running", CampaignStatus.PENDING_LAUNCH);
        return toBuilder().status(CampaignStatus.LAUNCH_RUNNING).updatedAt(Instant.now()).build();
    }

    /** Marks launch execution as failed. */
    public Campaign markLaunchFailed() {
        requireStatus("mark launch failed", CampaignStatus.PENDING_LAUNCH, CampaignStatus.LAUNCH_RUNNING);
        return toBuilder().status(CampaignStatus.LAUNCH_FAILED).updatedAt(Instant.now()).build();
    }

    /** Marks external execution as blocked by connector governance. */
    public Campaign markExternalExecutionBlocked() {
        requireStatus("mark external execution blocked", CampaignStatus.PENDING_LAUNCH);
        return toBuilder().status(CampaignStatus.EXTERNAL_EXECUTION_BLOCKED).updatedAt(Instant.now()).build();
    }

    /**
     * Transitions the campaign to {@link CampaignStatus#PAUSED}.
     * Only valid from {@link CampaignStatus#LAUNCHED}.
     *
     * @throws IllegalStateException if the campaign is not launched
     */
    public Campaign pause() {
        requireStatus("pause", CampaignStatus.LAUNCHED);
        return toBuilder().status(CampaignStatus.PAUSED).updatedAt(Instant.now()).build();
    }

    /**
     * Transitions the campaign to {@link CampaignStatus#COMPLETED}.
     * Only valid from {@link CampaignStatus#LAUNCHED} or {@link CampaignStatus#PAUSED}.
     *
     * @throws IllegalStateException if the campaign is not in a completable state
     */
    public Campaign complete() {
        requireStatus("complete", CampaignStatus.LAUNCHED, CampaignStatus.PAUSED);
        return toBuilder().status(CampaignStatus.COMPLETED).updatedAt(Instant.now()).build();
    }

    /**
     * Transitions the campaign to {@link CampaignStatus#ARCHIVED}.
     * Only valid from {@link CampaignStatus#COMPLETED}.
     *
     * @throws IllegalStateException if the campaign is not completed
     */
    public Campaign archive() {
        requireStatus("archive", CampaignStatus.COMPLETED);
        return toBuilder().status(CampaignStatus.ARCHIVED).updatedAt(Instant.now()).build();
    }

    /**
     * Transitions the campaign to {@link CampaignStatus#DRAFT}.
     * Only valid from {@link CampaignStatus#LAUNCHED} or {@link CampaignStatus#PAUSED}.
     *
     * <p>P1-005: Extends campaign lifecycle beyond create/launch/pause to include
     * rollback to draft for rework before re-launching.</p>
     *
     * @throws IllegalStateException if the campaign is not in a rollbackable state
     */
    public Campaign rollback() {
        requireStatus(
            "rollback",
            CampaignStatus.PENDING_LAUNCH,
            CampaignStatus.LAUNCH_RUNNING,
            CampaignStatus.LAUNCH_FAILED,
            CampaignStatus.EXTERNAL_EXECUTION_BLOCKED,
            CampaignStatus.LAUNCHED,
            CampaignStatus.PAUSED
        );
        return toBuilder().status(CampaignStatus.ROLLED_BACK).updatedAt(Instant.now()).build();
    }

    /** Returns {@code true} when a launch or resume is currently permitted. */
    public boolean isLaunchable() {
        return status == CampaignStatus.DRAFT || status == CampaignStatus.APPROVED || status == CampaignStatus.PAUSED;
    }

    private void requireStatus(String operation, CampaignStatus... allowed) {
        for (CampaignStatus s : allowed) {
            if (this.status == s) return;
        }
        throw new IllegalStateException(
            "Cannot " + operation + " campaign '" + id + "' in status " + status);
    }

    private Builder toBuilder() {
        return new Builder()
            .id(id)
            .workspaceId(workspaceId)
            .name(name)
            .status(status)
            .type(type)
            .objective(objective)
            .budgetCents(budgetCents)
            .startDate(startDate)
            .endDate(endDate)
            .audience(audience)
            .landingPageUrl(landingPageUrl)
            .createdAt(createdAt)
            .updatedAt(updatedAt)
            .createdBy(createdBy);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return id.equals(((Campaign) o).id);
    }

    @Override
    public int hashCode() { return id.hashCode(); }

    @Override
    public String toString() {
        return "Campaign{id='" + id + "', name='" + name + "', status=" + status + '}';
    }

    /** Fluent builder for {@link Campaign}. */
    public static final class Builder {
        private String id;
        private DmWorkspaceId workspaceId;
        private String name;
        private CampaignStatus status = CampaignStatus.DRAFT;
        private CampaignType type;
        private String objective;
        private Long budgetCents;
        private String startDate;
        private String endDate;
        private String audience;
        private String landingPageUrl;
        private Instant createdAt;
        private Instant updatedAt;
        private String createdBy;

        private Builder() {}

        public Builder id(String id)                       { this.id = id; return this; }
        public Builder workspaceId(DmWorkspaceId ws)       { this.workspaceId = ws; return this; }
        public Builder name(String name)                   { this.name = name; return this; }
        public Builder status(CampaignStatus status)       { this.status = status; return this; }
        public Builder type(CampaignType type)             { this.type = type; return this; }
        public Builder objective(String objective)          { this.objective = objective; return this; }
        public Builder budgetCents(Long budgetCents)       { this.budgetCents = budgetCents; return this; }
        public Builder startDate(String startDate)         { this.startDate = startDate; return this; }
        public Builder endDate(String endDate)             { this.endDate = endDate; return this; }
        public Builder audience(String audience)          { this.audience = audience; return this; }
        public Builder landingPageUrl(String landingPageUrl) { this.landingPageUrl = landingPageUrl; return this; }
        public Builder createdAt(Instant createdAt)        { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt)        { this.updatedAt = updatedAt; return this; }
        public Builder createdBy(String createdBy)         { this.createdBy = createdBy; return this; }

        public Campaign build() { return new Campaign(this); }
    }
}
