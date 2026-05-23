package com.ghatana.yappc.services.learn;

import com.ghatana.yappc.domain.learn.Insights;
import com.ghatana.yappc.domain.observe.Observation;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Persists learning evidence generated from observations and AI analysis.
 *
 * @doc.type interface
 * @doc.purpose Durable learning evidence repository contract
 * @doc.layer service
 * @doc.pattern Repository Port
 */
public interface LearningEvidenceRepository {

    /**
     * Persists one learning evidence record.
     *
     * @param evidence learning evidence with source observation and generated insights
     * @return promise that completes after persistence
     */
    Promise<Void> save(@NotNull LearningEvidence evidence);

    /**
     * Creates a repository that intentionally performs no durable write.
     *
     * @return no-op repository for isolated tests that do not compose Data Cloud
     */
    static LearningEvidenceRepository noop() {
        return evidence -> Promise.complete();
    }

    /**
     * Durable learning evidence payload.
     *
     * @param evidenceId unique evidence identifier
     * @param tenantId tenant that owns the evidence
     * @param projectId project or run-derived project reference
     * @param runId runtime run reference
     * @param observation observation that produced the insights
     * @param insights generated insight graph
     * @param provenance ordered provenance references
     * @param metadata repository-specific metadata
     * @param createdAt evidence creation timestamp
     */
    record LearningEvidence(
            @NotNull String evidenceId,
            @NotNull String tenantId,
            @NotNull String projectId,
            @NotNull String runId,
            @NotNull Observation observation,
            @NotNull Insights insights,
            @NotNull List<String> provenance,
            @NotNull Map<String, Object> metadata,
            @NotNull Instant createdAt
    ) {
    }
}
