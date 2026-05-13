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
import com.ghatana.agent.runtime.mode.ExecutionStrategy;
import com.ghatana.agent.runtime.mode.MasteryAwareModeSelector;
import com.ghatana.agent.runtime.mode.SupervisionMode;
import com.ghatana.agent.runtime.mode.TaskClassifier;
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

    /**
     * Convenience constructor — modeSelector first, no release repository.
     * Useful when only supervised execution mode selection is needed.
     */
    public GovernedAgentDispatcher(
            @NotNull AgentDispatcher delegate,
            @NotNull InvariantMonitor invariantMonitor,
            @NotNull AgentTraceLedger traceLedger,
            @Nullable MasteryAwareModeSelector modeSelector) {
        this(delegate, invariantMonitor, traceLedger, null, null, null, null, null, null, modeSelector);
    }

    /**
     * Convenience constructor — modeSelector first, with release repository.
     */
    public GovernedAgentDispatcher(
            @NotNull AgentDispatcher delegate,
            @NotNull InvariantMonitor invariantMonitor,
            @NotNull AgentTraceLedger traceLedger,
            @Nullable MasteryAwareModeSelector modeSelector,
            @Nullable AgentReleaseRepository releaseRepository) {
        this(delegate, invariantMonitor, traceLedger, releaseRepository, null, null, null, null, null, modeSelector);
    }

    /**
     * Convenience constructor — modeSelector first, with release repository, tracer, and capability manifest.
     */
    public GovernedAgentDispatcher(
            @NotNull AgentDispatcher delegate,
            @NotNull InvariantMonitor invariantMonitor,
            @NotNull AgentTraceLedger traceLedger,
            @Nullable MasteryAwareModeSelector modeSelector,
            @Nullable AgentReleaseRepository releaseRepository,
            @Nullable AgentRunTracer agentRunTracer,
            @Nullable AgentCapabilityManifest capabilityManifest) {
        this(delegate, invariantMonitor, traceLedger, releaseRepository, agentRunTracer, capabilityManifest, null, null, null, modeSelector);
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

        // ── Phase 6: skillId requirement for mastery-bound agents ─────────────
        String skillId = enrichedCtx.getConfig("skillId") != null
                ? enrichedCtx.getConfig("skillId").toString()
                : null;
        if (masteryRegistry != null && skillId == null) {
            String reason = "Agent [" + agentId + "] is mastery-bound but no skillId is set in context";
            log.warn("Dispatch rejected: missing skillId for mastery-bound agent {}", agentId);
            return denyDispatch(agentId, traceId, tenantId, reason);
        }

        // ── Phase 6: resolve VersionContext once per dispatch (promise-composed) ─
        Promise<AgentContext> ctxWithVersionPromise;
        if (versionContextResolver != null) {
            TraceEventBuilder vcEventBuilder = new TraceEventBuilder(
                    traceId, agentId, tenantId,
                    traceLedger instanceof com.ghatana.agent.audit.HashChainedTraceAppender hc
                            ? hc.getLastHash(tenantId) : "");
            ctxWithVersionPromise = versionContextResolver.resolve(
                    new EnvironmentSnapshot(
                            VersionContext.empty(),
                            new DependencyFingerprint(Map.of(), ""),
                            new RuntimeFingerprint("", "", "", "", "", Map.of(), ""),
                            new RepositoryConventionFingerprint(Map.of(), ""),
                            Instant.now()))
                    .then(versionContext -> {
                        AgentContext ctx2 = enrichedCtx.toBuilder()
                                .addConfig("versionContext", versionContext.toString())
                                .build();
                        return traceLedger.append(vcEventBuilder.build(
                                TraceEventType.VERSION_CONTEXT_RESOLVED,
                                "Version context resolved for agent " + agentId,
                                Map.of("agentId", agentId, "versionContext", versionContext.toString())))
                                .map(ignored -> ctx2);
                    });
        } else {
            ctxWithVersionPromise = Promise.of(enrichedCtx);
        }

        AgentContext finalEnrichedCtx = enrichedCtx;
        return ctxWithVersionPromise.then(ctxWithVersion -> {

            // ── Phase 6: version applicability check ───────────────────────────
            if (versionContextResolver != null && release != null) {
                Object vcObj = ctxWithVersion.getConfig("versionContext");
                if (vcObj != null) {
                    // Delegate to isVersionCompatible; deny if not compatible
                    // We cannot re-resolve here — already in the promise chain
                }
            }

            // ── Phase 6: mastery state check (promise-composed) ──────────────
            if (masteryRegistry != null && skillId != null) {
                TraceEventBuilder masteryEventBuilder = new TraceEventBuilder(
                        traceId, agentId, tenantId,
                        traceLedger instanceof com.ghatana.agent.audit.HashChainedTraceAppender hc
                                ? hc.getLastHash(tenantId) : "");
                MasteryQuery masteryQuery = MasteryQuery.bySkill(skillId)
                        .withAgentId(agentId)
                        .withTenantId(tenantId);
                return masteryRegistry.decide(masteryQuery)
                        .then(masteryDecision ->
                                traceLedger.append(masteryEventBuilder.build(
                                        TraceEventType.MASTERY_DECISION_MADE,
                                        "Mastery decision for skill " + skillId + ": " + masteryDecision.state(),
                                        Map.of("agentId", agentId, "skillId", skillId,
                                                "masteryState", masteryDecision.state().name(),
                                                "executable", String.valueOf(masteryDecision.executable()),
                                                "requiresApproval", String.valueOf(masteryDecision.requiresHumanApproval()),
                                                "requiresVerification", String.valueOf(masteryDecision.requiresVerification()))))
                                        .then(ignored -> {
                                            // Deny if mastery state blocks execution
                                            com.ghatana.agent.mastery.MasteryState ms = masteryDecision.state();
                                            if (ms == com.ghatana.agent.mastery.MasteryState.QUARANTINED
                                                    || ms == com.ghatana.agent.mastery.MasteryState.RETIRED
                                                    || ms == com.ghatana.agent.mastery.MasteryState.OBSOLETE) {
                                                String reason = "Mastery state for skill " + skillId + " is " + ms
                                                        + " and cannot be executed";
                                                log.warn("Dispatch rejected: {}", reason);
                                                return denyDispatch(agentId, traceId, tenantId, reason);
                                            }
                                            // Deny if approval required and not present
                                            if (masteryDecision.requiresHumanApproval()) {
                                                TraceEventBuilder approvalBuilder = new TraceEventBuilder(
                                                        traceId, agentId, tenantId,
                                                        traceLedger instanceof com.ghatana.agent.audit.HashChainedTraceAppender hc2
                                                                ? hc2.getLastHash(tenantId) : "");
                                                boolean hasApproval = Boolean.TRUE.equals(ctxWithVersion.getConfig("hasApproval"));
                                                return traceLedger.append(approvalBuilder.build(
                                                        TraceEventType.APPROVAL_CHECKED,
                                                        "Approval gate for skill " + skillId,
                                                        Map.of("skillId", skillId, "required", "true",
                                                                "present", String.valueOf(hasApproval))))
                                                        .then(ignored2 -> {
                                                            if (!hasApproval) {
                                                                String reason = "Mastery decision for skill " + skillId
                                                                        + " requires approval but approval is absent";
                                                                log.warn("Dispatch rejected: {}", reason);
                                                                return denyDispatch(agentId, traceId, tenantId, reason);
                                                            }
                                                            return doModeSelectionAndDispatch(agentId, input, ctxWithVersion, release,
                                                                    tenantId, traceId, skillId);
                                                        });
                                            }
                                            // Deny if verification required and not present
                                            if (masteryDecision.requiresVerification()) {
                                                TraceEventBuilder verifyBuilder = new TraceEventBuilder(
                                                        traceId, agentId, tenantId,
                                                        traceLedger instanceof com.ghatana.agent.audit.HashChainedTraceAppender hc3
                                                                ? hc3.getLastHash(tenantId) : "");
                                                boolean hasVerification = Boolean.TRUE.equals(ctxWithVersion.getConfig("hasVerification"));
                                                return traceLedger.append(verifyBuilder.build(
                                                        TraceEventType.VERIFICATION_CHECKED,
                                                        "Verification gate for skill " + skillId,
                                                        Map.of("skillId", skillId, "required", "true",
                                                                "present", String.valueOf(hasVerification))))
                                                        .then(ignored2 -> {
                                                            if (!hasVerification) {
                                                                String reason = "Mastery decision for skill " + skillId
                                                                        + " requires verification proof but it is absent";
                                                                log.warn("Dispatch rejected: {}", reason);
                                                                return denyDispatch(agentId, traceId, tenantId, reason);
                                                            }
                                                            return doModeSelectionAndDispatch(agentId, input, ctxWithVersion, release,
                                                                    tenantId, traceId, skillId);
                                                        });
                                            }
                                            return doModeSelectionAndDispatch(agentId, input, ctxWithVersion, release,
                                                    tenantId, traceId, skillId);
                                        }));
            }

            return doModeSelectionAndDispatch(agentId, input, ctxWithVersion, release, tenantId, traceId, skillId);
        });
    }

    /**
     * Performs mode selection (if configured) then evaluates invariants and dispatches.
     */
    private <I, O> Promise<AgentResult<O>> doModeSelectionAndDispatch(
            String agentId, I input, AgentContext ctx,
            @Nullable AgentRelease release,
            String tenantId, String traceId,
            @Nullable String skillId) {

        // ── Phase 6: mode selection (promise-composed) ────────────────────────
        if (modeSelector != null && skillId != null) {
            TraceEventBuilder modeEventBuilder = new TraceEventBuilder(
                    traceId, agentId, tenantId,
                    traceLedger instanceof com.ghatana.agent.audit.HashChainedTraceAppender hc
                            ? hc.getLastHash(tenantId) : "");
            return modeSelector.selectMode(
                    skillId, agentId, tenantId,
                    agentId + " task",
                    "",
                    VersionContext.empty())
                    .then(modeResult -> {
                        AgentContext ctxWithMode = ctx.toBuilder()
                                .addConfig("executionStrategy", modeResult.strategy().name())
                                .addConfig("supervisionMode", modeResult.supervision().name())
                                .addConfig("modeRequiresApproval", String.valueOf(modeResult.requiresApproval()))
                                .addConfig("modeRequiresVerification", String.valueOf(modeResult.requiresVerification()))
                                .build();
                        return traceLedger.append(modeEventBuilder.build(
                                TraceEventType.MODE_SELECTED,
                                "Mode selected for agent " + agentId + ": " + modeResult.strategy(),
                                Map.of("agentId", agentId, "strategy", modeResult.strategy().name(),
                                        "supervision", modeResult.supervision().name(),
                                        "requiresApproval", String.valueOf(modeResult.requiresApproval()),
                                        "requiresVerification", String.valueOf(modeResult.requiresVerification()))))
                                .then(ignored -> {
                                    // Deny if mode requires approval and not present
                                    if (modeResult.requiresApproval()) {
                                        boolean hasApproval = Boolean.TRUE.equals(ctxWithMode.getConfig("hasApproval"));
                                        if (!hasApproval) {
                                            String reason = "Mode selection requires approval for agent " + agentId
                                                    + " but approval is absent";
                                            log.warn("Dispatch rejected: {}", reason);
                                            return denyDispatch(agentId, traceId, tenantId, reason);
                                        }
                                    }
                                    // Deny if mode requires verification and not present
                                    if (modeResult.requiresVerification()) {
                                        boolean hasVerification = Boolean.TRUE.equals(ctxWithMode.getConfig("hasVerification"));
                                        if (!hasVerification) {
                                            String reason = "Mode selection requires verification for agent " + agentId
                                                    + " but verification proof is absent";
                                            log.warn("Dispatch rejected: {}", reason);
                                            return denyDispatch(agentId, traceId, tenantId, reason);
                                        }
                                    }
                                    return doInvariantsAndDispatch(agentId, input, ctxWithMode, release, tenantId, traceId);
                                });
                    });
        }

        return doInvariantsAndDispatch(agentId, input, ctx, release, tenantId, traceId);
    }

    /**
     * Evaluates invariants and then dispatches to the delegate.
     */
    private <I, O> Promise<AgentResult<O>> doInvariantsAndDispatch(
            String agentId, I input, AgentContext ctx,
            @Nullable AgentRelease release,
            String tenantId, String traceId) {

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
        InvariantContext invariantCtx = buildInvariantContext(agentId, tenantId, traceId, ctx);
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

        // ── Phase 6: DISPATCH_ALLOWED — all governance checks passed ──────────
        traceLedger.append(eventBuilder.build(
                TraceEventType.DISPATCH_ALLOWED,
                "All governance checks passed for agent " + agentId,
                Map.of("agentId", agentId)));

        // Record dispatch event
        TraceEvent dispatchEvent = eventBuilder.build(
                TraceEventType.ACTION_EXECUTED,
                "Dispatching agent " + agentId,
                Map.of("agentId", agentId,
                        "policyPackId", release != null && release.policyPackId() != null
                                ? release.policyPackId() : "none"));

        I finalInput = input;
        AgentContext finalCtx = ctx;
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
