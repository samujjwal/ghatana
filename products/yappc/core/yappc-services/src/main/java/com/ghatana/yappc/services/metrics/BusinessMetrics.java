/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Services — Business KPI Metrics
 */
package com.ghatana.yappc.services.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Business KPI metrics for the YAPPC lifecycle service.
 *
 * <p>Exposes domain-level metrics that represent the health and activity of the
 * YAPPC product — beyond infrastructure-level counters. These feed the Grafana
 * YAPPC Business Dashboard and the Alertmanager SLA rules.
 *
 * <p><b>Tracked KPIs</b></p>
 * <ul>
 *   <li><b>Active projects</b> — gauge of projects in an active phase</li>
 *   <li><b>Phase transitions</b> — counter of lifecycle phase advances</li>
 *   <li><b>Approval gates</b> — pending / approved / rejected approval records</li>
 *   <li><b>Scaffold generations</b> — code generation operations</li>
 *   <li><b>Policy violations</b> — governance policy blocks</li>
 *   <li><b>Active tenants</b> — gauge of tenants that have had activity in the last hour</li>
 * </ul>
 *
 * <p><b>Metric Naming Convention (OBS-001)</b></p>
 * {@code yappc.lifecycle.<domain>}, lowercase, dot-separated.
 *
 * @doc.type class
 * @doc.purpose Business KPI metric publisher for YAPPC lifecycle operations
 * @doc.layer product
 * @doc.pattern Facade
 */
public final class BusinessMetrics {

    private static final Logger log = LoggerFactory.getLogger(BusinessMetrics.class);

    // Gauge backing fields (current live values)
    private final AtomicInteger activeProjects   = new AtomicInteger(0);
    private final AtomicInteger pendingApprovals = new AtomicInteger(0);
    private final AtomicInteger activeTenants    = new AtomicInteger(0);

    // Cumulative counter backing fields
    private final AtomicLong totalPhaseTransitions = new AtomicLong(0);

    private final MeterRegistry registry;

    public BusinessMetrics(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "MeterRegistry must not be null");
        registerGauges();
    }

    // ── Gauge registration ────────────────────────────────────────────────────

    private void registerGauges() {
        Gauge.builder("yappc.lifecycle.projects.active",
                        activeProjects, AtomicInteger::get)
                .description("Number of projects currently in an active lifecycle phase")
                .register(registry);

        Gauge.builder("yappc.lifecycle.approvals.pending",
                        pendingApprovals, AtomicInteger::get)
                .description("Number of lifecycle approval gates awaiting human decision")
                .register(registry);

        Gauge.builder("yappc.lifecycle.tenants.active",
                        activeTenants, AtomicInteger::get)
                .description("Number of tenants with activity in the last hour")
                .register(registry);
    }

    // ── Gauge setters ─────────────────────────────────────────────────────────

    /**
     * Updates the gauge for currently active projects.
     * Call after each project state change that affects active-project count.
     */
    public void setActiveProjects(int count) {
        activeProjects.set(Math.max(0, count));
    }

    /**
     * Updates the gauge for pending approval gates.
     * Call after approval records are created or resolved.
     */
    public void setPendingApprovals(int count) {
        pendingApprovals.set(Math.max(0, count));
    }

    /**
     * Updates the active-tenant gauge.
     * Call periodically from a scheduled job that counts recently-active tenants.
     */
    public void setActiveTenants(int count) {
        activeTenants.set(Math.max(0, count));
    }

    // ── Event counters ────────────────────────────────────────────────────────

    /**
     * Records a lifecycle phase transition event.
     *
     * @param tenantId   tenant that owns the project
     * @param fromPhase  source phase
     * @param toPhase    target phase
     */
    public void recordPhaseTransition(String tenantId, String fromPhase, String toPhase) {
        Tags tags = Tags.of(
                Tag.of("tenant",    safe(tenantId)),
                Tag.of("from_phase", safe(fromPhase)),
                Tag.of("to_phase",  safe(toPhase))
        );
        Counter.builder("yappc.lifecycle.phase.transitions.total")
                .description("Total lifecycle phase transitions")
                .tags(tags)
                .register(registry)
                .increment();
        totalPhaseTransitions.incrementAndGet();
        log.debug("Phase transition recorded: tenant={} {} → {}", tenantId, fromPhase, toPhase);
    }

    /**
     * Records an approval gate event.
     *
     * @param tenantId  tenant scope
     * @param outcome   {@code "APPROVED"}, {@code "REJECTED"}, or {@code "PENDING"}
     * @param gateType  approval gate type (e.g. {@code "human"}, {@code "policy"})
     */
    public void recordApprovalEvent(String tenantId, String outcome, String gateType) {
        Tags tags = Tags.of(
                Tag.of("tenant",    safe(tenantId)),
                Tag.of("outcome",   safe(outcome)),
                Tag.of("gate_type", safe(gateType))
        );
        Counter.builder("yappc.lifecycle.approvals.total")
                .description("Total lifecycle approval gate events")
                .tags(tags)
                .register(registry)
                .increment();
    }

    /**
     * Records a scaffold code generation operation.
     *
     * @param tenantId  tenant scope
     * @param template  scaffold template used
     * @param success   whether generation succeeded
     */
    public void recordScaffoldGeneration(String tenantId, String template, boolean success) {
        Tags tags = Tags.of(
                Tag.of("tenant",   safe(tenantId)),
                Tag.of("template", safe(template)),
                Tag.of("status",   success ? "success" : "error")
        );
        Counter.builder("yappc.lifecycle.scaffold.generations.total")
                .description("Total scaffold code generation operations")
                .tags(tags)
                .register(registry)
                .increment();
    }

    /**
     * Records a governance policy violation (a request blocked by the policy engine).
     *
     * @param tenantId   tenant scope
     * @param policyId   the policy that was violated
     * @param resource   resource that triggered the violation
     */
    public void recordPolicyViolation(String tenantId, String policyId, String resource) {
        Tags tags = Tags.of(
                Tag.of("tenant",    safe(tenantId)),
                Tag.of("policy_id", safe(policyId)),
                Tag.of("resource",  safe(resource))
        );
        Counter.builder("yappc.lifecycle.policy.violations.total")
                .description("Total governance policy violations")
                .tags(tags)
                .register(registry)
                .increment();
        log.warn("Policy violation: tenant={} policy={} resource={}", tenantId, policyId, resource);
    }

    /**
     * Records a project being created.
     *
     * @param tenantId tenant scope
     */
    public void recordProjectCreated(String tenantId) {
        Tags tags = Tags.of(Tag.of("tenant", safe(tenantId)));
        Counter.builder("yappc.lifecycle.projects.created.total")
                .description("Total projects created")
                .tags(tags)
                .register(registry)
                .increment();
    }

    /**
     * Records a phase gate validation outcome.
     *
     * @param tenantId   tenant scope
     * @param phase      lifecycle phase being validated (e.g. {@code "Build"}, {@code "Test"})
     * @param outcome    {@code "PASS"} when all gates are satisfied, {@code "BLOCK"} otherwise
     * @param durationMs gate validation duration in milliseconds
     */
    public void recordPhaseGateValidation(String tenantId, String phase, String outcome, long durationMs) {
        Tags tags = Tags.of(
                Tag.of("tenant",  safe(tenantId)),
                Tag.of("phase",   safe(phase)),
                Tag.of("outcome", safe(outcome))
        );
        Counter.builder("yappc.lifecycle.phase.gate.validations.total")
                .description("Total phase gate validation outcomes")
                .tags(tags)
                .register(registry)
                .increment();
        DistributionSummary.builder("yappc.lifecycle.phase.gate.duration.ms")
                .description("Phase gate validation duration in milliseconds")
                .tags(Tags.of(Tag.of("tenant", safe(tenantId)), Tag.of("phase", safe(phase))))
                .register(registry)
                .record(durationMs);
        log.debug("Phase gate validation: tenant={} phase={} outcome={} duration={}ms",
                tenantId, phase, outcome, durationMs);
    }

    /**
     * Returns the total number of phase transitions since service start (for testing).
     */
    public long getTotalPhaseTransitions() {
        return totalPhaseTransitions.get();
    }

    // ── Critical-flow canonical metrics ──────────────────────────────────────

    /**
     * Canonical metric names for critical YAPPC flows.
     *
     * <p>All of these emit tags: {@code tenantId}, {@code workspaceId},
     * {@code projectId}, {@code phase}, {@code operation}, {@code outcome},
     * {@code degraded}, {@code errorClass}, {@code correlationId}.
     */
    public static final String METRIC_PHASE_PACKET_BUILD    = "yappc.flow.phase_packet_build.total";
    public static final String METRIC_DASHBOARD_ACTION      = "yappc.flow.dashboard_action.total";
    public static final String METRIC_GENERATION_RUN        = "yappc.flow.generation_run.total";
    public static final String METRIC_GENERATION_REVIEW     = "yappc.flow.generation_review.total";
    public static final String METRIC_PREVIEW_SESSION       = "yappc.flow.preview_session.total";
    public static final String METRIC_SOURCE_IMPORT         = "yappc.flow.source_import.total";
    public static final String METRIC_RESIDUAL_REVIEW       = "yappc.flow.residual_review.total";
    public static final String METRIC_EVIDENCE_SEARCH       = "yappc.flow.evidence_search.total";
    public static final String METRIC_POLICY_EVALUATION     = "yappc.flow.policy_evaluation.total";
    public static final String METRIC_LEARNING_PROMOTION    = "yappc.flow.learning_promotion.total";
    public static final String METRIC_KERNEL_TRUTH_SOURCE   = "yappc.flow.kernel_truth_source.total";
    public static final String METRIC_GENERATION_ASSURANCE  = "yappc.flow.generation_assurance.total";

    /**
     * Records a single increment for any critical YAPPC flow using the canonical nine-tag set.
     *
     * <p>Prefer the flow-specific helper methods below for self-documenting call sites.
     *
     * @param metric        metric name — use one of the {@code METRIC_*} constants
     * @param tenantId      owning tenant
     * @param workspaceId   workspace within the tenant
     * @param projectId     project within the workspace
     * @param phase         lifecycle phase
     * @param operation     specific sub-operation
     * @param outcome       {@code "SUCCESS"}, {@code "BLOCKED"}, {@code "ERROR"}, etc.
     * @param degraded      {@code true} when served in degraded mode
     * @param errorClass    exception class name, or {@code null}
     * @param correlationId request correlation / trace id
     */
    public void recordCriticalFlow(
            String metric,
            String tenantId,
            String workspaceId,
            String projectId,
            String phase,
            String operation,
            String outcome,
            boolean degraded,
            String errorClass,
            String correlationId) {
        Tags tags = Tags.of(
                Tag.of("tenantId",      safe(tenantId)),
                Tag.of("workspaceId",   safe(workspaceId)),
                Tag.of("projectId",     safe(projectId)),
                Tag.of("phase",         safe(phase)),
                Tag.of("operation",     safe(operation)),
                Tag.of("outcome",       safe(outcome)),
                Tag.of("degraded",      String.valueOf(degraded)),
                Tag.of("errorClass",    errorClass  != null ? errorClass  : "none"),
                Tag.of("correlationId", correlationId != null ? correlationId : "none")
        );
        Counter.builder(metric)
                .description("YAPPC critical flow metric: " + metric)
                .tags(tags)
                .register(registry)
                .increment();
    }

    /** Records a phase packet build event. */
    public void recordPhasePacketBuild(String tenantId, String workspaceId, String projectId,
            String phase, String operation, String outcome, boolean degraded,
            String errorClass, String correlationId) {
        recordCriticalFlow(METRIC_PHASE_PACKET_BUILD,
                tenantId, workspaceId, projectId, phase, operation, outcome, degraded, errorClass, correlationId);
    }

    /** Records a dashboard action execution event. */
    public void recordDashboardAction(String tenantId, String workspaceId, String projectId,
            String phase, String operation, String outcome, boolean degraded,
            String errorClass, String correlationId) {
        recordCriticalFlow(METRIC_DASHBOARD_ACTION,
                tenantId, workspaceId, projectId, phase, operation, outcome, degraded, errorClass, correlationId);
    }

    /** Records a generation run event (code generation attempt). */
    public void recordGenerationRun(String tenantId, String workspaceId, String projectId,
            String phase, String operation, String outcome, boolean degraded,
            String errorClass, String correlationId) {
        recordCriticalFlow(METRIC_GENERATION_RUN,
                tenantId, workspaceId, projectId, phase, operation, outcome, degraded, errorClass, correlationId);
    }

    /** Records a generation review event (apply / reject / rollback). */
    public void recordGenerationReview(String tenantId, String workspaceId, String projectId,
            String phase, String operation, String outcome, boolean degraded,
            String errorClass, String correlationId) {
        recordCriticalFlow(METRIC_GENERATION_REVIEW,
                tenantId, workspaceId, projectId, phase, operation, outcome, degraded, errorClass, correlationId);
    }

    /** Records a preview session event (create / validate). */
    public void recordPreviewSession(String tenantId, String workspaceId, String projectId,
            String phase, String operation, String outcome, boolean degraded,
            String errorClass, String correlationId) {
        recordCriticalFlow(METRIC_PREVIEW_SESSION,
                tenantId, workspaceId, projectId, phase, operation, outcome, degraded, errorClass, correlationId);
    }

    /** Records a source import event. */
    public void recordSourceImport(String tenantId, String workspaceId, String projectId,
            String phase, String operation, String outcome, boolean degraded,
            String errorClass, String correlationId) {
        recordCriticalFlow(METRIC_SOURCE_IMPORT,
                tenantId, workspaceId, projectId, phase, operation, outcome, degraded, errorClass, correlationId);
    }

    /** Records a residual island review event. */
    public void recordResidualIslandReview(String tenantId, String workspaceId, String projectId,
            String phase, String operation, String outcome, boolean degraded,
            String errorClass, String correlationId) {
        recordCriticalFlow(METRIC_RESIDUAL_REVIEW,
                tenantId, workspaceId, projectId, phase, operation, outcome, degraded, errorClass, correlationId);
    }

    /** Records a platform evidence search event. */
    public void recordPlatformEvidenceSearch(String tenantId, String workspaceId, String projectId,
            String phase, String operation, String outcome, boolean degraded,
            String errorClass, String correlationId) {
        recordCriticalFlow(METRIC_EVIDENCE_SEARCH,
                tenantId, workspaceId, projectId, phase, operation, outcome, degraded, errorClass, correlationId);
    }

    /** Records a policy evaluation event. */
    public void recordPolicyEvaluation(String tenantId, String workspaceId, String projectId,
            String phase, String operation, String outcome, boolean degraded,
            String errorClass, String correlationId) {
        recordCriticalFlow(METRIC_POLICY_EVALUATION,
                tenantId, workspaceId, projectId, phase, operation, outcome, degraded, errorClass, correlationId);
    }

    /** Records a Kernel lifecycle truth-source lookup event. */
    public void recordKernelTruthSource(String tenantId, String workspaceId, String projectId,
            String phase, String operation, String outcome, boolean degraded,
            String errorClass, String correlationId) {
        recordCriticalFlow(METRIC_KERNEL_TRUTH_SOURCE,
                tenantId, workspaceId, projectId, phase, operation, outcome, degraded, errorClass, correlationId);
    }

    /** Records generated-artifact assurance check outcomes. */
    public void recordGenerationAssurance(String tenantId, String workspaceId, String projectId,
            String phase, String operation, String outcome, boolean degraded,
            String errorClass, String correlationId) {
        recordCriticalFlow(METRIC_GENERATION_ASSURANCE,
                tenantId, workspaceId, projectId, phase, operation, outcome, degraded, errorClass, correlationId);
    }

    /** Records a learning promotion proposal event. */
    public void recordLearningPromotionProposal(String tenantId, String workspaceId, String projectId,
            String phase, String operation, String outcome, boolean degraded,
            String errorClass, String correlationId) {
        recordCriticalFlow(METRIC_LEARNING_PROMOTION,
                tenantId, workspaceId, projectId, phase, operation, outcome, degraded, errorClass, correlationId);
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private static String safe(String value) {
        return value != null ? value : "unknown";
    }
}
