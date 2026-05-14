/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.observability;

/**
 * OpenTelemetry span names for governance and mastery critical flows.
 *
 * <p>Phase 8.2: Defines span names for critical flows that should be instrumented
 * with OpenTelemetry to enable distributed tracing and observability.
 *
 * <p>Span naming convention follows OpenTelemetry semantic conventions:
 * - Use lowercase with dots for hierarchy (e.g., "governance.version.resolve")
 * - Include component name (e.g., "governance" for governance-related spans)
 * - Use verbs for actions (e.g., "resolve", "decide", "evaluate")
 *
 * @doc.type class
 * @doc.purpose OpenTelemetry span names for governance and mastery flows
 * @doc.layer agent-core
 * @doc.pattern Constants
 */
public final class GovernanceSpanNames {

    private GovernanceSpanNames() {
        // Utility class - prevent instantiation
    }

    // ── Version Resolution ──────────────────────────────────────────────────

    /**
     * Span for version context resolution.
     * Traces the resolution of version dependencies and constraints.
     */
    public static final String VERSION_CONTEXT_RESOLVE = "governance.version.resolve";

    // ── Mastery Decision ─────────────────────────────────────────────────────

    /**
     * Span for mastery decision making.
     * Traces the decision process for determining if a skill can execute.
     */
    public static final String MASTERY_DECIDE = "governance.mastery.decide";

    /**
     * Span for mastery state query.
     * Traces the query for current mastery state of a skill.
     */
    public static final String MASTERY_QUERY = "governance.mastery.query";

    // ── Task Classification ─────────────────────────────────────────────────

    /**
     * Span for task classification.
     * Traces the classification of a task based on evidence and patterns.
     */
    public static final String TASK_CLASSIFY = "governance.task.classify";

    // ── Mode Selection ──────────────────────────────────────────────────────

    /**
     * Span for execution mode selection.
     * Traces the selection of autonomous vs constrained execution mode.
     */
    public static final String MODE_SELECT = "governance.mode.select";

    // ── Memory Retrieval ─────────────────────────────────────────────────────

    /**
     * Span for memory retrieval.
     * Traces the retrieval of memory items for agent context.
     */
    public static final String MEMORY_RETRIEVE = "governance.memory.retrieve";

    /**
     * Span for memory query execution.
     * Traces the query execution against the memory plane.
     */
    public static final String MEMORY_QUERY = "governance.memory.query";

    /**
     * Span for memory filtering.
     * Traces the filtering of memory items by mastery, version, and freshness.
     */
    public static final String MEMORY_FILTER = "governance.memory.filter";

    /**
     * Span for memory reranking.
     * Traces the reranking of memory items by utility score.
     */
    public static final String MEMORY_RERANK = "governance.memory.rerank";

    // ── Policy Evaluation ────────────────────────────────────────────────────

    /**
     * Span for policy evaluation.
     * Traces the evaluation of governance policies for a request.
     */
    public static final String POLICY_EVALUATE = "governance.policy.evaluate";

    /**
     * Span for approval check.
     * Traces the check for human approval requirement.
     */
    public static final String APPROVAL_CHECK = "governance.approval.check";

    /**
     * Span for verification check.
     * Traces the check for verification requirement.
     */
    public static final String VERIFICATION_CHECK = "governance.verification.check";

    // ── Agent Execution ────────────────────────────────────────────────────

    /**
     * Span for agent execution.
     * Traces the execution of the agent task.
     */
    public static final String AGENT_EXECUTE = "governance.agent.execute";

    /**
     * Span for agent dispatch.
     * Traces the dispatch of the agent to execution.
     */
    public static final String AGENT_DISPATCH = "governance.agent.dispatch";

    // ── Learning Delta Evaluation ────────────────────────────────────────────

    /**
     * Span for learning delta evaluation.
     * Traces the evaluation of a learning delta by the promotion engine.
     */
    public static final String LEARNING_DELTA_EVALUATE = "governance.learning.evaluate";

    // ── Promotion ─────────────────────────────────────────────────────────────

    /**
     * Span for learning delta promotion.
     * Traces the promotion of a learning delta to active knowledge.
     */
    public static final String LEARNING_DELTA_PROMOTE = "governance.learning.promote";

    /**
     * Span for mastery transition.
     * Traces the transition of a mastery item to a new state.
     */
    public static final String MASTERY_TRANSITION = "governance.mastery.transition";

    // ── Obsolescence Scan ────────────────────────────────────────────────────

    /**
     * Span for obsolescence scan.
     * Traces the scanning for obsolete mastery items.
     */
    public static final String OBSOLESCENCE_SCAN = "governance.obsolescence.scan";

    /**
     * Span for obsolescence routing.
     * Traces the routing of obsolescence events to transitions.
     */
    public static final String OBSOLESCENCE_ROUTE = "governance.obsolescence.route";

    // ── Evaluation Pack Execution ───────────────────────────────────────────

    /**
     * Span for evaluation pack execution.
     * Traces the execution of an evaluation pack.
     */
    public static final String EVALUATION_PACK_EXECUTE = "governance.evaluation.execute";

    /**
     * Span for evaluation pack test case execution.
     * Traces the execution of a single test case in an evaluation pack.
     */
    public static final String EVALUATION_TEST_EXECUTE = "governance.evaluation.test.execute";

    // ── Metrics ─────────────────────────────────────────────────────────────

    /**
     * Metric name for governance request latency.
     * Measures the time from request to decision.
     */
    public static final String METRIC_GOVERNANCE_LATENCY = "governance.request.latency";

    /**
     * Metric name for governance denial count.
     * Counts the number of denied governance requests.
     */
    public static final String METRIC_DENIAL_COUNT = "governance.denial.count";

    /**
     * Metric name for promotion count.
     * Counts the number of successful learning delta promotions.
     */
    public static final String METRIC_PROMOTION_COUNT = "governance.promotion.count";

    /**
     * Metric name for stale items count.
     * Counts the number of stale mastery items detected.
     */
    public static final String METRIC_STALE_COUNT = "governance.mastery.stale.count";

    /**
     * Metric name for obsolescence count.
     * Counts the number of obsolescence events detected.
     */
    public static final String METRIC_OBSOLESCENCE_COUNT = "governance.obsolescence.count";

    /**
     * Metric name for evaluation pack execution duration.
     * Measures the time to execute an evaluation pack.
     */
    public static final String METRIC_EVALUATION_DURATION = "governance.evaluation.duration";

    /**
     * Metric name for memory retrieval duration.
     * Measures the time to retrieve and filter memory items.
     */
    public static final String METRIC_MEMORY_RETRIEVAL_DURATION = "governance.memory.retrieval.duration";
}
