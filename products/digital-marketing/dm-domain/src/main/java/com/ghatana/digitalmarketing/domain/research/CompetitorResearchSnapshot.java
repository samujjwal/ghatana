package com.ghatana.digitalmarketing.domain.research;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable competitor and keyword research snapshot for a workspace.
 *
 * @doc.type class
 * @doc.purpose DMOS competitor and keyword research aggregate for F1-011 strategy context
 * @doc.layer product
 * @doc.pattern Entity
 */
public final class CompetitorResearchSnapshot {

    private final String snapshotId;
    private final DmWorkspaceId workspaceId;
    private final List<CompetitorFinding> competitorFindings;
    private final List<KeywordFinding> keywordFindings;
    private final String opportunitySummary;
    private final Instant generatedAt;
    private final String generatedBy;

    private CompetitorResearchSnapshot(Builder builder) {
        this.snapshotId = Objects.requireNonNull(builder.snapshotId, "snapshotId must not be null");
        this.workspaceId = Objects.requireNonNull(builder.workspaceId, "workspaceId must not be null");
        this.competitorFindings = builder.competitorFindings != null
            ? List.copyOf(builder.competitorFindings) : List.of();
        this.keywordFindings = builder.keywordFindings != null
            ? List.copyOf(builder.keywordFindings) : List.of();
        this.opportunitySummary = Objects.requireNonNull(builder.opportunitySummary, "opportunitySummary must not be null");
        this.generatedAt = Objects.requireNonNull(builder.generatedAt, "generatedAt must not be null");
        this.generatedBy = Objects.requireNonNull(builder.generatedBy, "generatedBy must not be null");

        if (snapshotId.isBlank()) {
            throw new IllegalArgumentException("snapshotId must not be blank");
        }
        if (opportunitySummary.isBlank()) {
            throw new IllegalArgumentException("opportunitySummary must not be blank");
        }
        if (generatedBy.isBlank()) {
            throw new IllegalArgumentException("generatedBy must not be blank");
        }
    }

    public String getSnapshotId() {
        return snapshotId;
    }

    public DmWorkspaceId getWorkspaceId() {
        return workspaceId;
    }

    public List<CompetitorFinding> getCompetitorFindings() {
        return competitorFindings;
    }

    public List<KeywordFinding> getKeywordFindings() {
        return keywordFindings;
    }

    public String getOpportunitySummary() {
        return opportunitySummary;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public String getGeneratedBy() {
        return generatedBy;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String snapshotId;
        private DmWorkspaceId workspaceId;
        private List<CompetitorFinding> competitorFindings;
        private List<KeywordFinding> keywordFindings;
        private String opportunitySummary;
        private Instant generatedAt;
        private String generatedBy;

        private Builder() {
        }

        public Builder snapshotId(String snapshotId) {
            this.snapshotId = snapshotId;
            return this;
        }

        public Builder workspaceId(DmWorkspaceId workspaceId) {
            this.workspaceId = workspaceId;
            return this;
        }

        public Builder competitorFindings(List<CompetitorFinding> competitorFindings) {
            this.competitorFindings = competitorFindings;
            return this;
        }

        public Builder keywordFindings(List<KeywordFinding> keywordFindings) {
            this.keywordFindings = keywordFindings;
            return this;
        }

        public Builder opportunitySummary(String opportunitySummary) {
            this.opportunitySummary = opportunitySummary;
            return this;
        }

        public Builder generatedAt(Instant generatedAt) {
            this.generatedAt = generatedAt;
            return this;
        }

        public Builder generatedBy(String generatedBy) {
            this.generatedBy = generatedBy;
            return this;
        }

        public CompetitorResearchSnapshot build() {
            return new CompetitorResearchSnapshot(this);
        }
    }
}
