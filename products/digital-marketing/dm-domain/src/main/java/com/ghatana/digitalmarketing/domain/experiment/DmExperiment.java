package com.ghatana.digitalmarketing.domain.experiment;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable entity representing an A/B test experiment.
 *
 * @doc.type class
 * @doc.purpose Domain entity for A/B experiment model and framework (DMOS-F3-003)
 * @doc.layer product
 * @doc.pattern Entity
 */
public final class DmExperiment {

    private final String id;
    private final String tenantId;
    private final String workspaceId;
    private final String name;
    private final String hypothesis;
    private final List<DmExperimentVariant> variants;
    private final DmExperimentStatus status;
    private final String winnerVariantId;
    private final Instant startedAt;
    private final Instant endedAt;
    private final Instant createdAt;

    private DmExperiment(Builder b) {
        this.id              = b.id;
        this.tenantId        = b.tenantId;
        this.workspaceId     = b.workspaceId;
        this.name            = b.name;
        this.hypothesis      = b.hypothesis;
        this.variants        = List.copyOf(b.variants);
        this.status          = b.status;
        this.winnerVariantId = b.winnerVariantId;
        this.startedAt       = b.startedAt;
        this.endedAt         = b.endedAt;
        this.createdAt       = b.createdAt;
    }

    public DmExperiment start() {
        if (status != DmExperimentStatus.DRAFT) {
            throw new IllegalStateException("Cannot start experiment in status: " + status);
        }
        return toBuilder().status(DmExperimentStatus.RUNNING).startedAt(Instant.now()).build();
    }

    public DmExperiment conclude(String winnerVariantId) {
        if (status != DmExperimentStatus.RUNNING) {
            throw new IllegalStateException("Cannot conclude experiment in status: " + status);
        }
        return toBuilder().status(DmExperimentStatus.CONCLUDED)
            .winnerVariantId(winnerVariantId).endedAt(Instant.now()).build();
    }

    public String getId()              { return id; }
    public String getTenantId()        { return tenantId; }
    public String getWorkspaceId()     { return workspaceId; }
    public String getName()            { return name; }
    public String getHypothesis()      { return hypothesis; }
    public List<DmExperimentVariant> getVariants() { return variants; }
    public DmExperimentStatus getStatus() { return status; }
    public String getWinnerVariantId() { return winnerVariantId; }
    public Instant getStartedAt()      { return startedAt; }
    public Instant getEndedAt()        { return endedAt; }
    public Instant getCreatedAt()      { return createdAt; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DmExperiment)) return false;
        return id.equals(((DmExperiment) o).id);
    }
    @Override public int hashCode() { return id.hashCode(); }
    @Override public String toString() {
        return "DmExperiment{id='" + id + "', name='" + name + "', status=" + status + '}';
    }

    public Builder toBuilder() {
        return new Builder().id(id).tenantId(tenantId).workspaceId(workspaceId)
            .name(name).hypothesis(hypothesis).variants(variants).status(status)
            .winnerVariantId(winnerVariantId).startedAt(startedAt).endedAt(endedAt)
            .createdAt(createdAt);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id, tenantId, workspaceId, name, hypothesis, winnerVariantId;
        private List<DmExperimentVariant> variants = List.of();
        private DmExperimentStatus status;
        private Instant startedAt, endedAt, createdAt;

        public Builder id(String v)               { this.id = v; return this; }
        public Builder tenantId(String v)         { this.tenantId = v; return this; }
        public Builder workspaceId(String v)      { this.workspaceId = v; return this; }
        public Builder name(String v)             { this.name = v; return this; }
        public Builder hypothesis(String v)       { this.hypothesis = v; return this; }
        public Builder variants(List<DmExperimentVariant> v) { this.variants = v; return this; }
        public Builder status(DmExperimentStatus v) { this.status = v; return this; }
        public Builder winnerVariantId(String v)  { this.winnerVariantId = v; return this; }
        public Builder startedAt(Instant v)       { this.startedAt = v; return this; }
        public Builder endedAt(Instant v)         { this.endedAt = v; return this; }
        public Builder createdAt(Instant v)       { this.createdAt = v; return this; }

        public DmExperiment build() {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
            if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            Objects.requireNonNull(variants, "variants must not be null");
            return new DmExperiment(this);
        }
    }

    /** A variant within the experiment. */
    public record DmExperimentVariant(String variantId, String name, int trafficPercent) {
        public DmExperimentVariant {
            Objects.requireNonNull(variantId, "variantId must not be null");
            Objects.requireNonNull(name, "name must not be null");
            if (trafficPercent < 0 || trafficPercent > 100) {
                throw new IllegalArgumentException("trafficPercent must be between 0 and 100");
            }
        }
    }
}
