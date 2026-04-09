/*
 * Copyright (c) 2026 Ghatana
 */
package com.ghatana.platform.observability;

import io.micrometer.core.instrument.MeterRegistry;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Business KPI telemetry and observability metrics.
 *
 * <p>Tracks business-critical metrics beyond technical performance:
 * <ul>
 *   <li>User engagement (active users, sessions, feature usage)</li>
 *   <li>Product lifecycle metrics (projects created, phases completed)</li>
 *   <li>AI/ML metrics (agent invocations, LLM calls, token usage)</li>
 *   <li>Collaboration metrics (team size, concurrent users)</li>
 *   <li>Revenue indicators (tenant activity, feature adoption)</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Business KPI telemetry
 * @doc.layer platform
 * @doc.pattern Observer
 */
public class BusinessMetrics {

    private final MeterRegistry registry;
    private final ConcurrentHashMap<String, AtomicLong> gauges = new ConcurrentHashMap<>();

    public BusinessMetrics(MeterRegistry registry) {
        this.registry = registry;
        initializeMetrics();
    }

    private void initializeMetrics() {
        // Register gauges for real-time business metrics
        registry.gauge("business.users.active.daily", gauges.computeIfAbsent("users.active.daily", k -> new AtomicLong(0)));
        registry.gauge("business.users.active.monthly", gauges.computeIfAbsent("users.active.monthly", k -> new AtomicLong(0)));
        registry.gauge("business.sessions.concurrent", gauges.computeIfAbsent("sessions.concurrent", k -> new AtomicLong(0)));
        registry.gauge("business.projects.total", gauges.computeIfAbsent("projects.total", k -> new AtomicLong(0)));
        registry.gauge("business.tenants.active", gauges.computeIfAbsent("tenants.active", k -> new AtomicLong(0)));
    }

    // ─── User Engagement Metrics ─────────────────────────────────────────────

    public void recordUserLogin(String userId, String tenantId) {
        registry.counter("business.users.logins",
                "tenant", tenantId).increment();
        updateActiveUsers();
    }

    public void recordUserLogout(String userId, String tenantId) {
        registry.counter("business.users.logouts",
                "tenant", tenantId).increment();
    }

    public void recordSessionCreated(String tenantId, Duration duration) {
        registry.counter("business.sessions.created",
                "tenant", tenantId).increment();
        gauges.get("sessions.concurrent").incrementAndGet();
    }

    public void recordSessionEnded(String tenantId) {
        registry.counter("business.sessions.ended",
                "tenant", tenantId).increment();
        gauges.get("sessions.concurrent").decrementAndGet();
    }

    public void recordFeatureUsage(String feature, String tenantId) {
        registry.counter("business.features.usage",
                "feature", feature,
                "tenant", tenantId).increment();
    }

    // ─── Product Lifecycle Metrics ───────────────────────────────────────────

    public void recordProjectCreated(String tenantId, String projectType) {
        registry.counter("business.projects.created",
                "tenant", tenantId,
                "type", projectType).increment();
        gauges.get("projects.total").incrementAndGet();
    }

    public void recordProjectCompleted(String tenantId, String projectType, Duration duration) {
        registry.counter("business.projects.completed",
                "tenant", tenantId,
                "type", projectType).increment();
        registry.timer("business.projects.duration",
                "tenant", tenantId,
                "type", projectType).record(duration);
    }

    public void recordPhaseCompleted(String phase, String tenantId) {
        registry.counter("business.phases.completed",
                "phase", phase,
                "tenant", tenantId).increment();
    }

    public void recordWorkflowExecuted(String workflowType, String tenantId, boolean success) {
        registry.counter("business.workflows.executed",
                "type", workflowType,
                "tenant", tenantId,
                "status", success ? "success" : "failure").increment();
    }

    // ─── AI/ML Metrics ───────────────────────────────────────────────────────

    public void recordAgentInvocation(String agentType, String tenantId, boolean success) {
        registry.counter("business.ai.agent.invocations",
                "agent", agentType,
                "tenant", tenantId,
                "status", success ? "success" : "failure").increment();
    }

    public void recordLlmCall(String model, String tenantId, int inputTokens, int outputTokens) {
        registry.counter("business.ai.llm.calls",
                "model", model,
                "tenant", tenantId).increment();
        registry.counter("business.ai.llm.tokens.input",
                "model", model,
                "tenant", tenantId).increment(inputTokens);
        registry.counter("business.ai.llm.tokens.output",
                "model", model,
                "tenant", tenantId).increment(outputTokens);
    }

    public void recordLlmLatency(String model, String tenantId, Duration latency) {
        registry.timer("business.ai.llm.latency",
                "model", model,
                "tenant", tenantId).record(latency);
    }

    public void recordCodeGenerated(String language, String tenantId, int linesOfCode) {
        registry.counter("business.ai.code.generated",
                "language", language,
                "tenant", tenantId).increment();
        registry.counter("business.ai.code.lines",
                "language", language,
                "tenant", tenantId).increment(linesOfCode);
    }

    // ─── Collaboration Metrics ───────────────────────────────────────────────

    public void recordCollaborationSession(String tenantId, int participantCount) {
        registry.counter("business.collaboration.sessions",
                "tenant", tenantId).increment();
        registry.summary("business.collaboration.participants",
                "tenant", tenantId).record(participantCount);
    }

    public void recordCommentCreated(String tenantId, String entityType) {
        registry.counter("business.collaboration.comments",
                "tenant", tenantId,
                "entity", entityType).increment();
    }

    public void recordReviewCompleted(String tenantId, String reviewType) {
        registry.counter("business.collaboration.reviews",
                "tenant", tenantId,
                "type", reviewType).increment();
    }

    // ─── Data Cloud Metrics ──────────────────────────────────────────────────

    public void recordEventPublished(String eventType, String tenantId) {
        registry.counter("business.datacloud.events.published",
                "type", eventType,
                "tenant", tenantId).increment();
    }

    public void recordEventConsumed(String eventType, String tenantId) {
        registry.counter("business.datacloud.events.consumed",
                "type", eventType,
                "tenant", tenantId).increment();
    }

    public void recordEntityPersisted(String entityType, String tenantId) {
        registry.counter("business.datacloud.entities.persisted",
                "type", entityType,
                "tenant", tenantId).increment();
    }

    // ─── Tenant & Revenue Metrics ────────────────────────────────────────────

    public void recordTenantCreated(String tenantId, String plan) {
        registry.counter("business.tenants.created",
                "plan", plan).increment();
        gauges.get("tenants.active").incrementAndGet();
    }

    public void recordTenantUpgraded(String tenantId, String fromPlan, String toPlan) {
        registry.counter("business.tenants.upgraded",
                "from", fromPlan,
                "to", toPlan).increment();
    }

    public void recordApiCall(String endpoint, String tenantId, int statusCode) {
        registry.counter("business.api.calls",
                "endpoint", endpoint,
                "tenant", tenantId,
                "status", String.valueOf(statusCode)).increment();
    }

    // ─── Helper Methods ──────────────────────────────────────────────────────

    private void updateActiveUsers() {
        // This would typically query a session store or cache
        // For now, we increment the gauge
        gauges.get("users.active.daily").incrementAndGet();
    }

    public void setActiveUsers(long daily, long monthly) {
        gauges.get("users.active.daily").set(daily);
        gauges.get("users.active.monthly").set(monthly);
    }

    public void setActiveTenants(long count) {
        gauges.get("tenants.active").set(count);
    }

    public void setTotalProjects(long count) {
        gauges.get("projects.total").set(count);
    }

    public void setConcurrentSessions(long count) {
        gauges.get("sessions.concurrent").set(count);
    }
}
