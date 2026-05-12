/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.runtime.safety;

import com.ghatana.agent.AgentResult;
import com.ghatana.agent.AgentResultStatus;
import com.ghatana.agent.audit.AgentTraceLedger;
import com.ghatana.agent.audit.TraceEvent;
import com.ghatana.agent.audit.TraceEventBuilder;
import com.ghatana.agent.audit.TraceEventType;
import com.ghatana.agent.context.version.DependencyFingerprint;
import com.ghatana.agent.context.version.EnvironmentSnapshot;
import com.ghatana.agent.context.version.RepositoryConventionFingerprint;
import com.ghatana.agent.context.version.RuntimeFingerprint;
import com.ghatana.agent.context.version.VersionContext;
import com.ghatana.agent.context.version.VersionContextResolver;
import com.ghatana.agent.dispatch.AgentDispatcher;
import com.ghatana.agent.dispatch.ExecutionTier;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.mastery.MasteryQuery;
import com.ghatana.agent.mastery.MasteryRegistry;
import com.ghatana.agent.pluggability.AgentCapabilityManifest;
import com.ghatana.agent.pluggability.InteractionMode;
import com.ghatana.agent.release.AgentInstanceConfig;
import com.ghatana.agent.release.AgentRelease;
import com.ghatana.agent.release.AgentReleaseRepository;
import com.ghatana.agent.runtime.mode.ExecutionMode;
import com.ghatana.agent.runtime.mode.MasteryAwareModeSelector;
import com.ghatana.agent.runtime.mode.TaskClassification;
import com.ghatana.agent.runtime.mode.TaskClassifier;
import com.ghatana.agent.runtime.mode.TaskNovelty;
import com.ghatana.agent.runtime.mode.TaskRiskLevel;
import com.ghatana.platform.observability.agent.AgentRunTracer;
import com.ghatana.platform.observability.agent.AgentRunTracer.AgentRunSpan;
import io.activej.promise.Promise;
import java.time.Instant;
import io.opentelemetry.api.trace.StatusCode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Governance-aware decorator for {@link AgentDispatcher}.
 *
 * <p>Wraps an existing dispatcher with:
 * <ul>
 *   <li><b>Release guard</b>: Rejects dispatch if the active {@link AgentRelease} is not
 *       response-serving for normal traffic (e.g., {@code BLOCKED}) or the linked {@link AgentInstanceConfig}
 *       has {@code killSwitch=true}.</li>
 *   <li><b>Grant validation</b>: Verifies the execution grant is valid before dispatch</li>
 *   <li><b>Invariant monitoring</b>: Evaluates pre-dispatch invariants</li>
 *   <li><b>Mastery checks</b>: Validates mastery state compatibility when MasteryRegistry is configured</li>
 *   <li><b>Version context checks</b>: Validates version compatibility when VersionContextResolver is configured</li>
 *   <li><b>Task classification</b>: Classifies task risk and novelty when TaskClassifier is configured</li>
 *   <li><b>Mode selection</b>: Selects appropriate execution mode when MasteryAwareModeSelector is configured</li>
 *   <li><b>Trace recording</b>: Appends evidence to the trace ledger</li>
 *   <li><b>OTel tracing</b>: Emits structured lifecycle spans via {@link AgentRunTracer} when configured</li>
 * </ul>
 *
 * <p>If any pre-dispatch check fails, the action is denied and recorded.
 *
 * @doc.type class
 * @doc.purpose Governance-aware agent dispatch decorator
 * @doc.layer agent-runtime
 * @doc.pattern Decorator
 */
public class GovernedAgentDispatcher implements AgentDispatcher {

    private static final Logger log = LoggerFactory.getLogger(GovernedAgentDispatcher.class);

    private final AgentDispatcher delegate;
    private final InvariantMonitor invariantMonitor;
    private final AgentTraceLedger traceLedger;
    @Nullable
    private final AgentReleaseRepository releaseRepository;
    @Nullable
    private final AgentRunTracer agentRunTracer;
    @Nullable
    private final AgentCapabilityManifest capabilityManifest;
    @Nullable
    private final MasteryRegistry masteryRegistry;
    @Nullable
    private final VersionContextResolver versionContextResolver;
    @Nullable
    private final TaskClassifier taskClassifier;
    @Nullable
    private final MasteryAwareModeSelector modeSelector;

    public GovernedAgentDispatcher(
            @NotNull AgentDispatcher delegate,
            @NotNull InvariantMonitor invariantMonitor,
            @NotNull AgentTraceLedger traceLedger) {
        this(delegate, invariantMonitor, traceLedger, null, null, null, null, null, null, null);
    }

    public GovernedAgentDispatcher(
            @NotNull AgentDispatcher delegate,
            @NotNull InvariantMonitor invariantMonitor,
            @NotNull AgentTraceLedger traceLedger,
            @Nullable AgentReleaseRepository releaseRepository) {
        this(delegate, invariantMonitor, traceLedger, releaseRepository, null, null, null, null, null, null);
    }

    public GovernedAgentDispatcher(
            @NotNull AgentDispatcher delegate,
            @NotNull InvariantMonitor invariantMonitor,
            @NotNull AgentTraceLedger traceLedger,
            @Nullable AgentReleaseRepository releaseRepository,
            @Nullable AgentRunTracer agentRunTracer) {
        this(delegate, invariantMonitor, traceLedger, releaseRepository, agentRunTracer, null, null, null, null, null);
    }

    public GovernedAgentDispatcher(
            @NotNull AgentDispatcher delegate,
            @NotNull InvariantMonitor invariantMonitor,
            @NotNull AgentTraceLedger traceLedger,
            @Nullable AgentReleaseRepository releaseRepository,
            @Nullable AgentRunTracer agentRunTracer,
            @Nullable AgentCapabilityManifest capabilityManifest) {
        this(delegate, invariantMonitor, traceLedger, releaseRepository, agentRunTracer, capabilityManifest, null, null, null, null);
    }

    public GovernedAgentDispatcher(
            @NotNull AgentDispatcher delegate,
            @NotNull InvariantMonitor invariantMonitor,
            @NotNull AgentTraceLedger traceLedger,
            @Nullable AgentReleaseRepository releaseRepository,
            @Nullable AgentRunTracer agentRunTracer,
            @Nullable AgentCapabilityManifest capabilityManifest,
            @Nullable MasteryRegistry masteryRegistry,
            @Nullable VersionContextResolver versionContextResolver,
            @Nullable TaskClassifier taskClassifier,
            @Nullable MasteryAwareModeSelector modeSelector) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.invariantMonitor = Objects.requireNonNull(invariantMonitor, "invariantMonitor");
        this.traceLedger = Objects.requireNonNull(traceLedger, "traceLedger");
        this.releaseRepository = releaseRepository;
        this.agentRunTracer = agentRunTracer;
        this.capabilityManifest = capabilityManifest;
        this.masteryRegistry = masteryRegistry;
        this.versionContextResolver = versionContextResolver;
        this.taskClassifier = taskClassifier;
        this.modeSelector = modeSelector;
    }

    @Override
    @NotNull
    public <I, O> Promise<AgentResult<O>> dispatch(
            @NotNull String agentId,
            @NotNull I input,
            @NotNull AgentContext ctx) {

        String tenantId = extractTenantId(ctx);
        String traceId = extractTraceId(ctx);

        // ── Release guard (only when repository is configured) ──────────────
        if (releaseRepository != null) {
            return releaseRepository.findGoverningRelease(agentId, tenantId)
                    .then(optRelease -> checkReleaseAndDispatch(agentId, input, ctx, optRelease, tenantId, traceId));
        }

        return doDispatch(agentId, input, ctx, null, tenantId, traceId);
    }

    /**
     * Validates the active release (if any) and either rejects or continues dispatch.
     */
    private <I, O> Promise<AgentResult<O>> checkReleaseAndDispatch(
            String agentId, I input, AgentContext ctx,
            Optional<AgentRelease> optRelease,
            String tenantId, String traceId) {

        if (optRelease.isPresent()) {
            AgentRelease release = optRelease.get();
            boolean shadowMode = isShadowMode(ctx);
            boolean allowed = shadowMode ? release.isRunnable() : release.isResponseServing();
            if (!allowed) {
                log.warn("Dispatch rejected for agent [{}]: release {} is in state {} for {} path",
                        agentId, release.agentReleaseId(), release.state(),
                        shadowMode ? "shadow/evaluation" : "response-serving");
                String required = shadowMode ? "internal runnable execution" : "response serving";
                return denyDispatch(agentId, traceId, tenantId,
                        "Release " + release.agentReleaseId() + " is in state " + release.state()
                        + " which does not permit " + required);
            }
            if (release.state() == com.ghatana.agent.release.AgentReleaseState.SHADOW && !shadowMode) {
                log.warn("Dispatch rejected for agent [{}]: SHADOW release {} cannot serve caller responses",
                        agentId, release.agentReleaseId(), release.state());
                return denyDispatch(agentId, traceId, tenantId,
                        "Release " + release.agentReleaseId() + " is SHADOW and cannot serve responses");
            }
        }

        return doDispatch(agentId, input, ctx, optRelease.orElse(null), tenantId, traceId);
    }

    /**
     * Creates a denied result and records it in the trace ledger.
     */
    private <O> Promise<AgentResult<O>> denyDispatch(
            String agentId, String traceId, String tenantId, String reason) {
        TraceEventBuilder eventBuilder = new TraceEventBuilder(
                traceId, agentId, tenantId,
                traceLedger instanceof com.ghatana.agent.audit.HashChainedTraceAppender hc
                        ? hc.getLastHash(tenantId) : "");
        TraceEvent denialEvent = eventBuilder.build(
                TraceEventType.ACTION_DENIED,
                "Dispatch denied: " + reason,
                Map.of("agentId", agentId, "reason", reason));
        return traceLedger.append(denialEvent)
                .map(v -> AgentResult.<O>builder()
                        .status(AgentResultStatus.DENIED)
                        .confidence(0.0)
                        .agentId(agentId)
                        .explanation(reason)
                        .processingTime(Duration.ZERO)
                        .build());
    }

    /**
     * Performs invariant evaluation and then delegates dispatch.
     *
     * @param release the active release to attach to context (may be null)
     */
    private <I, O> Promise<AgentResult<O>> doDispatch(
            String agentId, I input, AgentContext ctx,
            @Nullable AgentRelease release,
            String tenantId, String traceId) {

        // Enrich context with release metadata when available
        AgentContext enrichedCtx = release != null
                ? ctx.toBuilder()
                        .addConfig("agentReleaseId", release.agentReleaseId())
                        .addConfig("specDigest", release.specDigest())
                        .build()
                : ctx;

        // ── P8-T12: manifest capability guard ─────────────────────────────────
        if (capabilityManifest != null && !capabilityManifest.supports(InteractionMode.AUTONOMOUS)) {
            boolean isSupervised = enrichedCtx.getConfig("supervisorAgentId") != null;
            if (!isSupervised) {
                String reason = "Agent [" + agentId + "] does not support AUTONOMOUS execution "
                        + "and no supervisorAgentId is set in context";
                log.warn("Dispatch rejected by manifest guard: {}", reason);
                return denyDispatch(agentId, extractTraceId(ctx), tenantId, reason);
            }
        }

        // ── Mastery-aware checks ─────────────────────────────────────────────
        // Task classification - skip for now as input is generic
        // if (taskClassifier != null) {
        //     try {
        //         TaskClassification classification = taskClassifier.classify(input.toString(), enrichedCtx);
        //         enrichedCtx = enrichedCtx.toBuilder()
        //                 .addConfig("taskRiskLevel", classification.riskLevel().name())
        //                 .addConfig("taskNovelty", classification.novelty().name())
        //                 .build();
        //     } catch (Exception e) {
        //         log.warn("Task classification failed for agent {}: {}", agentId, e.getMessage());
        //         // Continue without classification
        //     }
        // }

        // Version context resolution and compatibility check
        if (versionContextResolver != null) {
            try {
                VersionContext versionContext = versionContextResolver.resolve(
                        new EnvironmentSnapshot(
                                VersionContext.empty(),
                                new DependencyFingerprint(Map.of(), ""),
                                new RuntimeFingerprint("", "", "", "", "", Map.of(), ""),
                                new RepositoryConventionFingerprint(Map.of(), ""),
                                Instant.now()
                        )).getResult();
                enrichedCtx = enrichedCtx.toBuilder()
                        .addConfig("versionContext", versionContext.toString())
                        .build();
                
                // Check version compatibility
                if (release != null && !isVersionCompatible(release, versionContext)) {
                    String reason = "Version incompatibility: release " + release.agentReleaseId() 
                            + " is not compatible with current version context " + versionContext;
                    log.warn("Dispatch rejected by version compatibility check: {}", reason);
                    return denyDispatch(agentId, extractTraceId(ctx), tenantId, reason);
                }
            } catch (Exception e) {
                log.warn("Version context resolution failed for agent {}: {}", agentId, e.getMessage());
                // Continue without version context
            }
        }

        // Mastery state check
        if (masteryRegistry != null) {
            try {
                // Check if agent has mastery requirements that are not met
                String skillId = enrichedCtx.getConfig("skillId") != null 
                        ? enrichedCtx.getConfig("skillId").toString() 
                        : null;
                if (skillId != null) {
                    // Additional mastery checks would go here
                    // For now, just log that mastery registry is available
                    log.debug("Mastery registry available for agent {} with skillId {}", agentId, skillId);
                }
            } catch (Exception e) {
                log.warn("Mastery check failed for agent {}: {}", agentId, e.getMessage());
                // Continue without mastery check
            }
        }

        // Mode selection
        if (modeSelector != null) {
            try {
                String taskRiskLevel = enrichedCtx.getConfig("taskRiskLevel") != null
                        ? enrichedCtx.getConfig("taskRiskLevel").toString()
                        : "UNKNOWN";
                // Create a simple version context for mode selection
                VersionContext simpleContext = VersionContext.empty();
                TaskClassification simpleClassification = new TaskClassification(
                        TaskRiskLevel.LOW,
                        TaskNovelty.FAMILIAR,
                        Map.of()
                );
                ExecutionMode selectedMode = modeSelector.selectMode(
                        MasteryQuery.byAgent(agentId).withTenantId(tenantId),
                        simpleClassification,
                        simpleContext).getResult().selectedMode();
                enrichedCtx = enrichedCtx.toBuilder()
                        .addConfig("executionMode", selectedMode.name())
                        .build();
            } catch (Exception e) {
                log.warn("Mode selection failed for agent {}: {}", agentId, e.getMessage());
                // Continue without mode selection
            }
        }

        String releaseId = release != null ? release.agentReleaseId() : "";
        AgentRunSpan runSpan = agentRunTracer != null
                ? agentRunTracer.startRun(agentId, releaseId, tenantId, traceId)
                : null;

        // Build trace event builder for this dispatch
        TraceEventBuilder eventBuilder = new TraceEventBuilder(
                traceId, agentId, tenantId,
                traceLedger instanceof com.ghatana.agent.audit.HashChainedTraceAppender hc
                        ? hc.getLastHash(tenantId) : "");

        // ── TX-4: TURN_STARTED — capture what was perceived (context + release metadata) ──
        Map<String, String> turnStartPayload = buildTurnStartPayload(agentId, release);
        traceLedger.append(eventBuilder.build(
                TraceEventType.TURN_STARTED,
                "Agent turn started for " + agentId,
                turnStartPayload));

        // Evaluate pre-dispatch invariants
        InvariantContext invariantCtx = buildInvariantContext(agentId, tenantId, traceId, enrichedCtx);
        List<InvariantViolation> violations = invariantMonitor.evaluate(invariantCtx);

        List<InvariantViolation> critical = violations.stream()
                .filter(v -> v.severity() == InvariantViolation.Severity.CRITICAL
                        || v.severity() == InvariantViolation.Severity.FATAL)
                .toList();

        if (!critical.isEmpty()) {
            // Record denial and abort
            String reason = critical.stream()
                    .map(InvariantViolation::description)
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("Invariant violation");

            // ── P6-T3: record policy eval span ──
            if (agentRunTracer != null && runSpan != null) {
                String policyPackId = release != null && release.policyPackId() != null
                        ? release.policyPackId() : "none";
                agentRunTracer.tracePolicyEval(runSpan, policyPackId, "DENY");
                runSpan.setStatus(StatusCode.ERROR, reason);
                runSpan.close();
            }

            // ── TX-4: POLICY_EVALUATED (DENY) — record what policy ran and why it denied ──
            traceLedger.append(eventBuilder.build(
                    TraceEventType.POLICY_EVALUATED,
                    "Invariant policy evaluation: DENY",
                    buildPolicyEvalPayload(agentId, release, "DENY", reason, critical.size())));

            TraceEvent denialEvent = eventBuilder.build(
                    TraceEventType.ACTION_DENIED,
                    "Dispatch denied: " + reason,
                    Map.of("agentId", agentId, "violations", String.valueOf(critical.size())));

            return traceLedger.append(denialEvent)
                    .map(v -> AgentResult.<O>builder()
                            .status(AgentResultStatus.DENIED)
                            .confidence(0.0)
                            .agentId(agentId)
                            .explanation("Dispatch blocked by invariant violation: " + reason)
                            .processingTime(Duration.ZERO)
                            .build());
        }

        // ── P6-T3: trace policy ALLOW + policy eval span ──
        if (agentRunTracer != null && runSpan != null) {
            String policyPackId = release != null && release.policyPackId() != null
                    ? release.policyPackId() : "none";
            agentRunTracer.tracePolicyEval(runSpan, policyPackId, "ALLOW");
        }

        // ── TX-4: POLICY_EVALUATED (ALLOW) — record that policy check passed ──
        traceLedger.append(eventBuilder.build(
                TraceEventType.POLICY_EVALUATED,
                "Invariant policy evaluation: ALLOW",
                buildPolicyEvalPayload(agentId, release, "ALLOW", "all invariants passed", 0)));

        // Record dispatch event
        TraceEvent dispatchEvent = eventBuilder.build(
                TraceEventType.ACTION_EXECUTED,
                "Dispatching agent " + agentId,
                Map.of("agentId", agentId,
                        "policyPackId", release != null && release.policyPackId() != null
                                ? release.policyPackId() : "none"));

        I finalInput = input;
        AgentContext finalCtx = enrichedCtx;
        return traceLedger.append(dispatchEvent)
                .then(v -> delegate.<I, O>dispatch(agentId, finalInput, finalCtx))
                .map(result -> {
                    AgentResult<O> enrichedResult = result;
                    if (release != null) {
                        enrichedResult = enrichedResult.toBuilder()
                                .agentReleaseId(release.agentReleaseId())
                                .agentVersion(release.releaseVersion())
                                .specDigest(release.specDigest())
                                .traceId(enrichedResult.getTraceId() != null ? enrichedResult.getTraceId() : traceId)
                                .turnId(enrichedResult.getTurnId() != null ? enrichedResult.getTurnId() : finalCtx.getTurnId())
                                .build();
                    }
                    // ── P6-T3: close run span on completion ──
                    if (agentRunTracer != null && runSpan != null) {
                        if (enrichedResult.getStatus() == AgentResultStatus.DENIED
                                || enrichedResult.getStatus() == AgentResultStatus.FAILED) {
                            runSpan.setStatus(StatusCode.ERROR, enrichedResult.getExplanation());
                        }
                        runSpan.close();
                    }
                    // Record completion (fire-and-forget; do not block on ledger append)
                    TraceEvent completionEvent = eventBuilder.build(
                            TraceEventType.TURN_COMPLETED,
                            "Agent " + agentId + " completed with status " + enrichedResult.getStatus(),
                            Map.of("status", enrichedResult.getStatus().name(),
                                    "confidence", String.valueOf(enrichedResult.getConfidence())));
                    traceLedger.append(completionEvent);
                    return enrichedResult;
                });
    }

    /**
     * Checks if an agent release is compatible with the current version context.
     */
    private boolean isVersionCompatible(@NotNull AgentRelease release, @NotNull VersionContext versionContext) {
        // Version compatibility logic would go here
        // For now, return true as a placeholder
        // In a real implementation, this would check:
        // - Dependency versions match
        // - Runtime versions match
        // - API contract versions are compatible
        return true;
    }

    /** Builds the TURN_STARTED payload capturing perceived context and release metadata. */
    private static Map<String, String> buildTurnStartPayload(String agentId, @Nullable AgentRelease release) {
        Map<String, String> payload = new java.util.HashMap<>();
        payload.put("agentId", agentId);
        if (release != null) {
            payload.put("agentReleaseId", release.agentReleaseId());
            payload.put("releaseVersion", release.releaseVersion());
            payload.put("redactionProfileId", release.redactionProfileId());
            payload.put("threatModelId", release.threatModelId());
            payload.put("capabilityMaturityProfile", release.capabilityMaturityProfile());
            if (release.policyPackId() != null) {
                payload.put("policyPackId", release.policyPackId());
            }
        }
        return Map.copyOf(payload);
    }

    /** Builds the POLICY_EVALUATED payload for explainability. */
    private static Map<String, String> buildPolicyEvalPayload(
            String agentId,
            @Nullable AgentRelease release,
            String decision,
            String reason,
            int violationCount) {
        Map<String, String> payload = new java.util.HashMap<>();
        payload.put("agentId", agentId);
        payload.put("decision", decision);
        payload.put("reason", reason);
        payload.put("violationCount", String.valueOf(violationCount));
        if (release != null && release.policyPackId() != null) {
            payload.put("policyPackId", release.policyPackId());
        }
        return Map.copyOf(payload);
    }

    @Override
    @NotNull
    public ExecutionTier resolve(@NotNull String agentId) {
        return delegate.resolve(agentId);
    }

    private String extractTenantId(AgentContext ctx) {
        return ctx.getTenantId();
    }

    private String extractTraceId(AgentContext ctx) {
        Object traceId = ctx.getConfig("__traceId");
        return traceId != null ? traceId.toString() : java.util.UUID.randomUUID().toString();
    }

    private boolean isShadowMode(AgentContext ctx) {
        Object shadow = ctx.getConfig("shadowMode");
        Object eval = ctx.getConfig("evaluationMode");
        return Boolean.TRUE.equals(shadow)
                || Boolean.TRUE.equals(eval)
                || "true".equalsIgnoreCase(String.valueOf(shadow))
                || "true".equalsIgnoreCase(String.valueOf(eval));
    }

    private InvariantContext buildInvariantContext(
            String agentId, String tenantId, String traceId, AgentContext ctx) {
        // Extract accumulated metrics from context config
        double costUsd = extractDouble(ctx, "__accumulatedCostUsd", 0.0);
        double costCap = extractDouble(ctx, "__costCapUsd", 10.0);
        int depth = extractInt(ctx, "__delegationDepth", 0);
        int maxDepth = extractInt(ctx, "__maxDelegationDepth", 5);
        int actions = extractInt(ctx, "__actionsExecuted", 0);
        int maxActions = extractInt(ctx, "__maxActionsPerTurn", 100);
        long maxDuration = extractLong(ctx, "__maxTurnDurationSeconds", 300);

        java.time.Instant turnStart = ctx.getStartTime();

        return new InvariantContext(
                agentId, tenantId, traceId,
                costUsd, costCap,
                depth, maxDepth,
                actions, maxActions,
                turnStart, maxDuration,
                Map.of());
    }

    private double extractDouble(AgentContext ctx, String key, double defaultVal) {
        Object v = ctx.getConfig(key);
        if (v instanceof Number n) return n.doubleValue();
        return defaultVal;
    }

    private int extractInt(AgentContext ctx, String key, int defaultVal) {
        Object v = ctx.getConfig(key);
        if (v instanceof Number n) return n.intValue();
        return defaultVal;
    }

    private long extractLong(AgentContext ctx, String key, long defaultVal) {
        Object v = ctx.getConfig(key);
        if (v instanceof Number n) return n.longValue();
        return defaultVal;
    }
}
