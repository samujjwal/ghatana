/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Lifecycle Service — Project Context Builder
 */
package com.ghatana.yappc.services.lifecycle.assessment;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Builds a {@link ProjectContext} snapshot by aggregating project state from multiple sources.
 *
 * <h2>Data sources</h2>
 * <ul>
 *   <li><b>Requirements</b>: count + average clarity score</li>
 *   <li><b>Code repository</b>: commit count, test coverage, build status</li>
 *   <li><b>Knowledge Graph (KG)</b>: architectural decision count</li>
 *   <li><b>Agent runtime</b>: active agent count</li>
 * </ul>
 *
 * <p>Each source is optional; if a source is not provided (null adapter), the
 * corresponding field defaults to its zero-value so the assessor can proceed gracefully.
 *
 * <p>Consumers plug in adapters via the builder:
 * <pre>{@code
 * ProjectContextBuilder builder = ProjectContextBuilder.builder()
 *     .requirementsAdapter(requirementsAdapter)
 *     .codeRepoAdapter(codeRepoAdapter)
 *     .kgAdapter(kgAdapter)
 *     .agentAdapter(agentAdapter)
 *     .build();
 *
 * ProjectContext ctx = runPromise(() ->
 *     builder.buildContext(projectId, tenantId, currentPhase));
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Aggregates project state from multiple sources into a ProjectContext for readiness evaluation
 * @doc.layer product
 * @doc.pattern Builder
 */
public final class ProjectContextBuilder {

    private static final Logger log = LoggerFactory.getLogger(ProjectContextBuilder.class);

    // ── Adapter interfaces ────────────────────────────────────────────────────

    /** Provides requirements data for a project. */
    public interface RequirementsAdapter {
        /** Returns the total requirement count (async). */
        Promise<Integer> requirementCount(@NotNull String projectId, @NotNull String tenantId);

        /** Returns the average clarity score [0.0, 1.0] across all requirements. */
        Promise<Double> averageClarityScore(@NotNull String projectId, @NotNull String tenantId);
    }

    /** Provides code repository metrics for a project. */
    public interface CodeRepoAdapter {
        /** Returns the total number of commits in the project repository. */
        Promise<Integer> commitCount(@NotNull String projectId, @NotNull String tenantId);

        /** Returns the latest test coverage percentage [0 – 100], or {@code -1} if unavailable. */
        Promise<Integer> testCoveragePercent(@NotNull String projectId, @NotNull String tenantId);

        /** Returns whether the last build was passing; {@code null} if unknown. */
        Promise<Boolean> buildPassing(@NotNull String projectId, @NotNull String tenantId);
    }

    /** Provides knowledge graph metrics for a project. */
    public interface KgAdapter {
        /** Returns the number of architectural/design decisions recorded in the KG. */
        Promise<Integer> decisionCount(@NotNull String projectId, @NotNull String tenantId);
    }

    /** Provides agent runtime metrics for a project. */
    public interface AgentAdapter {
        /** Returns the number of currently active (running or scheduled) agents. */
        Promise<Integer> activeAgentCount(@NotNull String projectId, @NotNull String tenantId);
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private final @Nullable RequirementsAdapter requirementsAdapter;
    private final @Nullable CodeRepoAdapter      codeRepoAdapter;
    private final @Nullable KgAdapter            kgAdapter;
    private final @Nullable AgentAdapter         agentAdapter;

    private ProjectContextBuilder(Builder b) {
        this.requirementsAdapter = b.requirementsAdapter;
        this.codeRepoAdapter     = b.codeRepoAdapter;
        this.kgAdapter           = b.kgAdapter;
        this.agentAdapter        = b.agentAdapter;
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Builds a {@link ProjectContext} by querying all configured adapters in parallel
     * and merging the results.
     *
     * <p>Any adapter that fails or is not configured contributes the zero / default value
     * for its field.
     *
     * @param projectId    project to aggregate
     * @param tenantId     tenant owning the project
     * @param currentPhase current lifecycle phase
     * @return promise of a fully-populated {@link ProjectContext}
     */
    public Promise<ProjectContext> buildContext(
            @NotNull String projectId,
            @NotNull String tenantId,
            @NotNull String currentPhase) {

        Objects.requireNonNull(projectId,    "projectId must not be null");
        Objects.requireNonNull(tenantId,     "tenantId must not be null");
        Objects.requireNonNull(currentPhase, "currentPhase must not be null");

        log.debug("Building ProjectContext for project={} tenant={} phase={}", projectId, tenantId, currentPhase);

        // Each promise resolves to its field value with graceful fallback
        Promise<Integer> reqCount = safeInt(
                requirementsAdapter != null
                        ? requirementsAdapter.requirementCount(projectId, tenantId)
                        : null,
                0, "requirementCount", projectId);

        Promise<Double> avgClarity = safeDouble(
                requirementsAdapter != null
                        ? requirementsAdapter.averageClarityScore(projectId, tenantId)
                        : null,
                0.0, "averageClarityScore", projectId);

        Promise<Integer> commits = safeInt(
                codeRepoAdapter != null
                        ? codeRepoAdapter.commitCount(projectId, tenantId)
                        : null,
                0, "commitCount", projectId);

        Promise<Integer> coverage = safeInt(
                codeRepoAdapter != null
                        ? codeRepoAdapter.testCoveragePercent(projectId, tenantId)
                        : null,
                -1, "testCoveragePercent", projectId);

        Promise<Boolean> build = safeBool(
                codeRepoAdapter != null
                        ? codeRepoAdapter.buildPassing(projectId, tenantId)
                        : null,
                null, "buildPassing", projectId);

        Promise<Integer> decisions = safeInt(
                kgAdapter != null
                        ? kgAdapter.decisionCount(projectId, tenantId)
                        : null,
                0, "decisionCount", projectId);

        Promise<Integer> agents = safeInt(
                agentAdapter != null
                        ? agentAdapter.activeAgentCount(projectId, tenantId)
                        : null,
                0, "activeAgentCount", projectId);

        // Chain all resolved values into a ProjectContext
        return reqCount.then(rc ->
               avgClarity.then(ac ->
               commits.then(co ->
               coverage.then(cv ->
               build.then(bp ->
               decisions.then(dc ->
               agents.map(ag -> new ProjectContext(
                       projectId, tenantId, currentPhase,
                       rc, ac, co, cv, bp, dc, ag))))))));
    }

    // ── Safe-fetch helpers ────────────────────────────────────────────────────

    private static Promise<Integer> safeInt(
            @Nullable Promise<Integer> source, int fallback, String field, String projectId) {
        if (source == null) return Promise.of(fallback);
        return source.then(
                value -> Promise.of(value != null ? value : fallback),
                ex -> {
                    log.warn("Failed to fetch {} for project={}: {}", field, projectId, ex.getMessage());
                    return Promise.of(fallback);
                });
    }

    private static Promise<Double> safeDouble(
            @Nullable Promise<Double> source, double fallback, String field, String projectId) {
        if (source == null) return Promise.of(fallback);
        return source.then(
                value -> Promise.of(value != null ? value : fallback),
                ex -> {
                    log.warn("Failed to fetch {} for project={}: {}", field, projectId, ex.getMessage());
                    return Promise.of(fallback);
                });
    }

    private static Promise<Boolean> safeBool(
            @Nullable Promise<Boolean> source, @Nullable Boolean fallback, String field, String projectId) {
        if (source == null) return Promise.of(fallback);
        return source.then(
                Promise::of,
                ex -> {
                    log.warn("Failed to fetch {} for project={}: {}", field, projectId, ex.getMessage());
                    return Promise.of(fallback);
                });
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    /** Returns a new {@link Builder}. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link ProjectContextBuilder}.
     */
    public static final class Builder {

        private RequirementsAdapter requirementsAdapter;
        private CodeRepoAdapter     codeRepoAdapter;
        private KgAdapter           kgAdapter;
        private AgentAdapter        agentAdapter;

        private Builder() {}

        /** Sets the requirements adapter (count + clarity). */
        public Builder requirementsAdapter(@Nullable RequirementsAdapter adapter) {
            this.requirementsAdapter = adapter;
            return this;
        }

        /** Sets the code repository adapter (commits + coverage + build). */
        public Builder codeRepoAdapter(@Nullable CodeRepoAdapter adapter) {
            this.codeRepoAdapter = adapter;
            return this;
        }

        /** Sets the knowledge graph adapter (decision count). */
        public Builder kgAdapter(@Nullable KgAdapter adapter) {
            this.kgAdapter = adapter;
            return this;
        }

        /** Sets the agent runtime adapter (active agent count). */
        public Builder agentAdapter(@Nullable AgentAdapter adapter) {
            this.agentAdapter = adapter;
            return this;
        }

        /** Builds the {@link ProjectContextBuilder}. */
        public ProjectContextBuilder build() {
            return new ProjectContextBuilder(this);
        }
    }
}
