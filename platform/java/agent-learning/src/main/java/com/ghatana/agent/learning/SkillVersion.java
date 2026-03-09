package com.ghatana.agent.learning;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/**
 * Represents a version of a skill (procedure) along with its evaluation outcome.
 *
 * @doc.type class
 * @doc.purpose Skill version record
 * @doc.layer agent-learning
 */
@Value
@Builder
public class SkillVersion {

    /** Unique version identifier. */
    @NotNull String versionId;

    /** Parent skill ID. */
    @NotNull String skillId;

    /** Semantic version string (e.g., "1.0.0", "1.1.0"). */
    @NotNull String version;

    /** Confidence at time of this version. */
    double confidence;

    /** Status: ACTIVE, PROMOTED, ROLLED_BACK, SUPERSEDED. */
    @NotNull String status;

    /** What changed in this version. */
    @Nullable String changeLog;

    /** Source of the version (trace-grading, consolidation, manual). */
    @Nullable String source;

    /** Evaluation score if it went through evaluation gates. */
    double evaluationScore;

    /** When this version was created. */
    @Builder.Default
    @NotNull Instant createdAt = Instant.now();

    /** When this version was promoted/activated (if applicable). */
    @Nullable Instant promotedAt;

    /** When this version was rolled back (if applicable). */
    @Nullable Instant rolledBackAt;
}
