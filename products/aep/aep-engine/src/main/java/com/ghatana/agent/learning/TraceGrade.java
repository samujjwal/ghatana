package com.ghatana.agent.learning;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Result of grading a completed execution trace.
 * Contains the numeric score, qualitative feedback, and any lessons extracted.
 *
 * @doc.type class
 * @doc.purpose Trace grading result
 * @doc.layer agent-learning
 */
@Value
@Builder
public class TraceGrade {

    /** Trace being graded. */
    @NotNull String traceId;

    /** Agent that produced the trace. */
    @NotNull String agentId;

    /** Overall score (0.0 to 1.0). */
    double score;

    /** Qualitative grade (A, B, C, D, F). */
    @NotNull String grade;

    /** Human-readable feedback from the grading LLM. */
    @Nullable String feedback;

    /** Lessons extracted from the trace. */
    @Builder.Default
    @NotNull List<String> extractedLessons = List.of();

    /** Dimension-specific scores (e.g., accuracy, efficiency, safety). */
    @Builder.Default
    @NotNull Map<String, Double> dimensionScores = Map.of();

    /** When this grade was produced. */
    @Builder.Default
    @NotNull Instant gradedAt = Instant.now();
}
