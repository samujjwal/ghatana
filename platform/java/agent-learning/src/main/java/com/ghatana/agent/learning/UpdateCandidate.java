package com.ghatana.agent.learning;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * A proposed update to a skill (procedure) that needs evaluation
 * before being promoted.
 *
 * @doc.type class
 * @doc.purpose Candidate for skill evaluation
 * @doc.layer agent-learning
 */
@Value
@Builder
public class UpdateCandidate {

    /** The skill being updated. */
    @NotNull String skillId;

    /** The proposed new version identifier. */
    @NotNull String proposedVersion;

    /** The current active version (or null if brand new). */
    @Nullable String currentVersion;

    /** Description of what changed. */
    @NotNull String changeDescription;

    /** The agent proposing the update. */
    @NotNull String agentId;

    /** Source of the update (e.g., "trace-grading", "manual", "consolidation"). */
    @NotNull String source;

    /** Additional metadata about the candidate. */
    @Builder.Default
    @NotNull Map<String, Object> metadata = Map.of();
}
