package com.ghatana.digitalmarketing.domain.funnel;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable entity representing a trial onboarding workflow for self-marketing acquisition funnel.
 *
 * @doc.type class
 * @doc.purpose Trial onboarding workflow for product-led growth (P3-001)
 * @doc.layer product
 * @doc.pattern Entity
 */
public final class TrialOnboarding {

    private final String id;
    private final String tenantId;
    private final String workspaceId;
    private final String leadId;
    private final String demoWorkspaceId;
    private final TrialOnboardingStatus status;
    private final int currentStep;
    private final int totalSteps;
    private final Map<String, Object> stepProgress;
    private final Instant createdAt;
    private final Instant startedAt;
    private final Instant completedAt;
    private final String cancellationReason;

    private TrialOnboarding(Builder builder) {
        this.id = builder.id;
        this.tenantId = builder.tenantId;
        this.workspaceId = builder.workspaceId;
        this.leadId = builder.leadId;
        this.demoWorkspaceId = builder.demoWorkspaceId;
        this.status = builder.status;
        this.currentStep = builder.currentStep;
        this.totalSteps = builder.totalSteps;
        this.stepProgress = Map.copyOf(builder.stepProgress);
        this.createdAt = builder.createdAt;
        this.startedAt = builder.startedAt;
        this.completedAt = builder.completedAt;
        this.cancellationReason = builder.cancellationReason;
    }

    public TrialOnboarding start() {
        if (status != TrialOnboardingStatus.PENDING) {
            throw new IllegalStateException("Cannot start onboarding in status: " + status);
        }
        return toBuilder()
            .status(TrialOnboardingStatus.IN_PROGRESS)
            .startedAt(Instant.now())
            .currentStep(1)
            .build();
    }

    public TrialOnboarding advanceStep(int stepNumber, Map<String, Object> progress) {
        if (status != TrialOnboardingStatus.IN_PROGRESS) {
            throw new IllegalStateException("Cannot advance step in status: " + status);
        }
        if (stepNumber <= currentStep || stepNumber > totalSteps) {
            throw new IllegalArgumentException("Invalid step number: " + stepNumber);
        }
        return toBuilder()
            .currentStep(stepNumber)
            .stepProgress(progress)
            .build();
    }

    public TrialOnboarding complete() {
        if (status != TrialOnboardingStatus.IN_PROGRESS) {
            throw new IllegalStateException("Cannot complete onboarding in status: " + status);
        }
        return toBuilder()
            .status(TrialOnboardingStatus.COMPLETED)
            .currentStep(totalSteps)
            .completedAt(Instant.now())
            .build();
    }

    public TrialOnboarding cancel(String reason) {
        if (status == TrialOnboardingStatus.COMPLETED || status == TrialOnboardingStatus.CANCELLED) {
            throw new IllegalStateException("Cannot cancel onboarding in status: " + status);
        }
        return toBuilder()
            .status(TrialOnboardingStatus.CANCELLED)
            .cancellationReason(reason)
            .build();
    }

    public double getProgressPercentage() {
        if (totalSteps == 0) return 0.0;
        return (double) currentStep / totalSteps * 100.0;
    }

    public boolean isComplete() {
        return status == TrialOnboardingStatus.COMPLETED;
    }

    public boolean isInProgress() {
        return status == TrialOnboardingStatus.IN_PROGRESS;
    }

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getWorkspaceId() { return workspaceId; }
    public String getLeadId() { return leadId; }
    public String getDemoWorkspaceId() { return demoWorkspaceId; }
    public TrialOnboardingStatus getStatus() { return status; }
    public int getCurrentStep() { return currentStep; }
    public int getTotalSteps() { return totalSteps; }
    public Map<String, Object> getStepProgress() { return stepProgress; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public String getCancellationReason() { return cancellationReason; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TrialOnboarding)) return false;
        return id.equals(((TrialOnboarding) o).id);
    }

    @Override
    public int hashCode() { return id.hashCode(); }

    @Override
    public String toString() {
        return "TrialOnboarding{id='" + id + "', workspaceId='" + workspaceId + "', status=" + status + ", progress=" + currentStep + "/" + totalSteps + '}';
    }

    public Builder toBuilder() {
        return new Builder()
            .id(id)
            .tenantId(tenantId)
            .workspaceId(workspaceId)
            .leadId(leadId)
            .demoWorkspaceId(demoWorkspaceId)
            .status(status)
            .currentStep(currentStep)
            .totalSteps(totalSteps)
            .stepProgress(stepProgress)
            .createdAt(createdAt)
            .startedAt(startedAt)
            .completedAt(completedAt)
            .cancellationReason(cancellationReason);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id;
        private String tenantId;
        private String workspaceId;
        private String leadId;
        private String demoWorkspaceId;
        private TrialOnboardingStatus status = TrialOnboardingStatus.PENDING;
        private int currentStep = 0;
        private int totalSteps = 5;
        private Map<String, Object> stepProgress = Map.of();
        private Instant createdAt;
        private Instant startedAt;
        private Instant completedAt;
        private String cancellationReason;

        public Builder id(String v) { this.id = v; return this; }
        public Builder tenantId(String v) { this.tenantId = v; return this; }
        public Builder workspaceId(String v) { this.workspaceId = v; return this; }
        public Builder leadId(String v) { this.leadId = v; return this; }
        public Builder demoWorkspaceId(String v) { this.demoWorkspaceId = v; return this; }
        public Builder status(TrialOnboardingStatus v) { this.status = v; return this; }
        public Builder currentStep(int v) { this.currentStep = v; return this; }
        public Builder totalSteps(int v) { this.totalSteps = v; return this; }
        public Builder stepProgress(Map<String, Object> v) { this.stepProgress = v; return this; }
        public Builder createdAt(Instant v) { this.createdAt = v; return this; }
        public Builder startedAt(Instant v) { this.startedAt = v; return this; }
        public Builder completedAt(Instant v) { this.completedAt = v; return this; }
        public Builder cancellationReason(String v) { this.cancellationReason = v; return this; }

        public TrialOnboarding build() {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
            if (workspaceId == null || workspaceId.isBlank()) throw new IllegalArgumentException("workspaceId must not be blank");
            if (leadId == null || leadId.isBlank()) throw new IllegalArgumentException("leadId must not be blank");
            if (totalSteps <= 0) throw new IllegalArgumentException("totalSteps must be positive");
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            return new TrialOnboarding(this);
        }
    }
}
