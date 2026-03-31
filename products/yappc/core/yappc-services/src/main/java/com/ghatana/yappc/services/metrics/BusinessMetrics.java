/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Services — Business KPI Metrics
 */
package com.ghatana.yappc.services.metrics;

import io.micrometer.core.instrument.Counter;
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
 * <h2>Tracked KPIs</h2>
 * <ul>
 *   <li><b>Active projects</b> — gauge of projects in an active phase</li>
 *   <li><b>Phase transitions</b> — counter of lifecycle phase advances</li>
 *   <li><b>Approval gates</b> — pending / approved / rejected approval records</li>
 *   <li><b>Scaffold generations</b> — code generation operations</li>
 *   <li><b>Policy violations</b> — governance policy blocks</li>
 *   <li><b>Active tenants</b> — gauge of tenants that have had activity in the last hour</li>
 * </ul>
 *
 * <h2>Metric Naming Convention (OBS-001)</h2>
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
     * Returns the total number of phase transitions since service start (for testing).
     */
    public long getTotalPhaseTransitions() {
        return totalPhaseTransitions.get();
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private static String safe(String value) {
        return value != null ? value : "unknown";
    }
}
