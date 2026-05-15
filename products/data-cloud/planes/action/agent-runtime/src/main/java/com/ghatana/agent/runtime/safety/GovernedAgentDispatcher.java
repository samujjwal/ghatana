/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.runtime.safety;

import com.ghatana.agent.AgentResult;
import com.ghatana.agent.AgentResultStatus;
import com.ghatana.agent.audit.AgentTraceLedger;
import com.ghatana.agent.audit.DataCloudAgentTraceLedger;
import com.ghatana.agent.audit.HashChainedTraceAppender;
import com.ghatana.agent.audit.TraceEvent;
import com.ghatana.agent.audit.TraceEventBuilder;
import com.ghatana.agent.audit.TraceEventType;
import com.ghatana.agent.context.version.DependencyFingerprint;
import com.ghatana.agent.context.version.EnvironmentSnapshot;
import com.ghatana.agent.context.version.RepositoryConventionFingerprint;
import com.ghatana.agent.context.version.RuntimeFingerprint;
import com.ghatana.agent.context.version.VersionContext;
import com.ghatana.agent.context.version.VersionContextCodec;
import com.ghatana.agent.context.version.VersionContextResolver;
import com.ghatana.agent.dispatch.AgentDispatcher;
import com.ghatana.agent.dispatch.ExecutionTier;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.mastery.MasteryQuery;
import com.ghatana.agent.mastery.MasteryRegistry;
import com.ghatana.agent.memory.MemoryRetriever;
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
    @Nullable
    private final MemoryRetriever memoryRetriever;

    public GovernedAgentDispatcher(
            @NotNull AgentDispatcher delegate,
            @NotNull InvariantMonitor invariantMonitor,
            @NotNull AgentTraceLedger traceLedger) {
        this(delegate, invariantMonitor, traceLedger, null, null, null, null, null, null, null, null);
    }

    public GovernedAgentDispatcher(
            @NotNull AgentDispatcher delegate,
            @NotNull InvariantMonitor invariantMonitor,
            @NotNull AgentTraceLedger traceLedger,
            @Nullable AgentReleaseRepository releaseRepository) {
        this(delegate, invariantMonitor, traceLedger, releaseRepository, null, null, null, null, null, null, null);
    }

    public GovernedAgentDispatcher(
            @NotNull AgentDispatcher delegate,
            @NotNull InvariantMonitor invariantMonitor,
            @NotNull AgentTraceLedger traceLedger,
            @Nullable AgentReleaseRepository releaseRepository,
            @Nullable AgentRunTracer agentRunTracer) {
        this(delegate, invariantMonitor, traceLedger, releaseRepository, agentRunTracer, null, null, null, null, null, null);
    }

    public GovernedAgentDispatcher(
            @NotNull AgentDispatcher delegate,
            @NotNull InvariantMonitor invariantMonitor,
            @NotNull AgentTraceLedger traceLedger,
            @Nullable AgentReleaseRepository releaseRepository,
            @Nullable AgentRunTracer agentRunTracer,
            @Nullable AgentCapabilityManifest capabilityManifest) {
        this(delegate, invariantMonitor, traceLedger, releaseRepository, agentRunTracer, capabilityManifest, null, null, null, null, null);
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
            @Nullable MasteryAwareModeSelector modeSelector,
            @Nullable MemoryRetriever memoryRetriever) {
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
        this.memoryRetriever = memoryRetriever;
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
        this(delegate, invariantMonitor, traceLedger, null, null, null, null, null, null, modeSelector, null);
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
        this(delegate, invariantMonitor, traceLedger, releaseRepository, null, null, null, null, null, modeSelector, null);
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
        this(delegate, invariantMonitor, traceLedger, releaseRepository, agentRunTracer, capabilityManifest, null, null, null, modeSelector, null);
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
                currentLedgerHash(tenantId));
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
        
        // Only require skillId if mastery registry is configured AND the release is mastery-bound
        // A release is considered mastery-bound if it has a masteryPolicyPackId
        boolean isMasteryBound = masteryRegistry != null && release != null 
                && release.masteryPolicyPackId() != null && !release.masteryPolicyPackId().isBlank();
        
        if (isMasteryBound && skillId == null) {
            // Attempt deterministic derivation of skillId
            skillId = deriveSkillId(agentId, release, capabilityManifest);
            
            if (skillId == null) {
                String releaseId = release != null ? release.agentReleaseId() : "unknown";
                String releaseVersion = release != null ? release.releaseVersion() : "unknown";
                String reason = String.format(
                        "Agent [%s] with release [%s, version %s] is mastery-bound (has masteryPolicyPackId: %s) but no skillId is set in context. " +
                        "Automatic derivation failed. Recovery: provide skillId in context, ensure agent definition has skill metadata, or disable mastery binding by removing masteryPolicyPackId from the release.",
                        agentId, releaseId, releaseVersion, release.masteryPolicyPackId());
                log.warn("Dispatch rejected: {} (agentId={}, releaseId={}, tenantId={})", reason, agentId, releaseId, tenantId);
                return denyDispatch(agentId, traceId, tenantId, reason);
            }
            
            // Enrich context with derived skillId for downstream use
            enrichedCtx = enrichedCtx.toBuilder()
                    .addConfig("skillId", skillId)
                    .addConfig("skillIdDerived", "true")
                    .build();
            
            log.debug("Derived skillId for mastery-bound agent: agentId={}, skillId={}, derivationSource=agentId", agentId, skillId);
        }
        
        // Create final copy for use in lambda expressions
        final String finalSkillId = skillId;
        final AgentContext finalEnrichedCtx = enrichedCtx;

        // ── Phase 6: resolve VersionContext once per dispatch (promise-composed) ─
        Promise<AgentContext> ctxWithVersionPromise;
        if (versionContextResolver != null) {
            TraceEventBuilder vcEventBuilder = new TraceEventBuilder(
                    traceId, agentId, tenantId,
                    currentLedgerHash(tenantId));
            ctxWithVersionPromise = versionContextResolver.resolve(
                    new EnvironmentSnapshot(
                            VersionContext.empty(),
                            new DependencyFingerprint(Map.of(), ""),
                            new RuntimeFingerprint("", "", "", "", "", Map.of(), ""),
                            new RepositoryConventionFingerprint(Map.of(), ""),
                            Instant.now()))
                    .then(versionContext -> {
                        AgentContext ctx2 = finalEnrichedCtx.toBuilder()
                                .addConfig("versionContext", versionContext)
                                .build();
                        return traceLedger.append(vcEventBuilder.build(
                                TraceEventType.VERSION_CONTEXT_RESOLVED,
                                "Version context resolved for agent " + agentId,
                                Map.of("agentId", agentId, "versionContext", versionContext.toString())))
                                .map(ignored -> ctx2);
                    });
        } else {
            ctxWithVersionPromise = Promise.of(finalEnrichedCtx);
        }

        return ctxWithVersionPromise.then(ctxWithVersion -> {

            // ── Phase 6: version applicability check ───────────────────────────
            if (versionContextResolver != null && release != null) {
                Object vcObj = ctxWithVersion.getConfig("versionContext");
                if (vcObj instanceof VersionContext versionCtx) {
                    if (!isVersionCompatible(release, versionCtx)) {
                        String reason = "Agent release " + release.agentReleaseId()
                                + " is incompatible with the current runtime version context";
                        log.warn("Dispatch rejected: version incompatibility for agent {}", agentId);
                        return denyDispatch(agentId, traceId, tenantId, reason);
                    }
                }
            }

            // ── Phase 6: mastery state check (promise-composed) ──────────────
            if (masteryRegistry != null && finalSkillId != null) {
                TraceEventBuilder masteryEventBuilder = new TraceEventBuilder(
                        traceId, agentId, tenantId,
                        currentLedgerHash(tenantId));
                
                // Extract resolved version context and encode using canonical codec for mastery query
                String versionContextStr = "";
                Object vcObj = ctxWithVersion.getConfig("versionContext");
                if (vcObj instanceof VersionContext vc) {
                    versionContextStr = VersionContextCodec.INSTANCE.encode(vc);
                }
                
                MasteryQuery masteryQuery = MasteryQuery.bySkill(finalSkillId)
                        .withAgentId(agentId)
                        .withTenantId(tenantId)
                        .withVersionContext(versionContextStr);
                return masteryRegistry.decide(masteryQuery)
                        .then(masteryDecision ->
                                traceLedger.append(masteryEventBuilder.build(
                                        TraceEventType.MASTERY_DECISION_MADE,
                                        "Mastery decision for skill " + finalSkillId + ": " + masteryDecision.state(),
                                        Map.of("agentId", agentId, "finalSkillId", finalSkillId,
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
                                                String reason = "Mastery state for skill " + finalSkillId + " is " + ms
                                                        + " and cannot be executed";
                                                log.warn("Dispatch rejected: {}", reason);
                                                return denyDispatch(agentId, traceId, tenantId, reason);
                                            }
                                            // Deny if approval required and proof not valid
                                            if (masteryDecision.requiresHumanApproval()) {
                                                TraceEventBuilder approvalBuilder = new TraceEventBuilder(
                                                        traceId, agentId, tenantId,
                                                        currentLedgerHash(tenantId));
                                                com.ghatana.agent.approval.ApprovalProof approvalProof = ctxWithVersion.getApprovalProof();
                                                boolean approvalValid = approvalProof != null && approvalProof.isValidFor(
                                                        tenantId, agentId, 
                                                        release != null ? release.agentReleaseId() : null,
                                                        finalSkillId, null);
                                                java.util.Map<String, String> approvalMetadata = new java.util.HashMap<>();
                                                approvalMetadata.put("finalSkillId", finalSkillId);
                                                approvalMetadata.put("required", "true");
                                                approvalMetadata.put("present", String.valueOf(approvalProof != null));
                                                if (approvalProof != null) {
                                                    approvalMetadata.put("proofId", approvalProof.proofId());
                                                    approvalMetadata.put("outcome", approvalProof.outcome().name());
                                                    approvalMetadata.put("valid", String.valueOf(approvalValid));
                                                }
                                                return traceLedger.append(approvalBuilder.build(
                                                        TraceEventType.APPROVAL_CHECKED,
                                                        "Approval gate for skill " + finalSkillId,
                                                        approvalMetadata))
                                                        .then(ignored2 -> {
                                                            if (!approvalValid) {
                                                                String reason = approvalProof == null
                                                                        ? "Mastery decision for skill " + finalSkillId + " requires approval but approval proof is absent"
                                                                        : "Mastery decision for skill " + finalSkillId + " requires approval but proof " + approvalProof.proofId() + " is invalid (outcome: " + approvalProof.outcome() + ", expired: " + approvalProof.isExpired() + ")";
                                                                log.warn("Dispatch rejected: {}", reason);
                                                                return denyDispatch(agentId, traceId, tenantId, reason);
                                                            }
                                                            return doModeSelectionAndDispatch(agentId, input, ctxWithVersion, release,
                                                                    tenantId, traceId, finalSkillId);
                                                        });
                                            }
                                            // Deny if verification required and proof not valid
                                            if (masteryDecision.requiresVerification()) {
                                                TraceEventBuilder verifyBuilder = new TraceEventBuilder(
                                                        traceId, agentId, tenantId,
                                                        currentLedgerHash(tenantId));
                                                com.ghatana.agent.approval.VerificationProof verificationProof = ctxWithVersion.getVerificationProof();
                                                boolean verificationValid = verificationProof != null && verificationProof.isValidFor(
                                                        tenantId, agentId,
                                                        release != null ? release.agentReleaseId() : null,
                                                        finalSkillId, null);
                                                java.util.Map<String, String> verificationMetadata = new java.util.HashMap<>();
                                                verificationMetadata.put("finalSkillId", finalSkillId);
                                                verificationMetadata.put("required", "true");
                                                verificationMetadata.put("present", String.valueOf(verificationProof != null));
                                                if (verificationProof != null) {
                                                    verificationMetadata.put("proofId", verificationProof.proofId());
                                                    verificationMetadata.put("outcome", verificationProof.outcome().name());
                                                    verificationMetadata.put("type", verificationProof.verificationType().name());
                                                    verificationMetadata.put("valid", String.valueOf(verificationValid));
                                                }
                                                return traceLedger.append(verifyBuilder.build(
                                                        TraceEventType.VERIFICATION_CHECKED,
                                                        "Verification gate for skill " + finalSkillId,
                                                        verificationMetadata))
                                                        .then(ignored2 -> {
                                                            if (!verificationValid) {
                                                                String reason = verificationProof == null
                                                                        ? "Mastery decision for skill " + finalSkillId + " requires verification but verification proof is absent"
                                                                        : "Mastery decision for skill " + finalSkillId + " requires verification but proof " + verificationProof.proofId() + " is invalid (outcome: " + verificationProof.outcome() + ", expired: " + verificationProof.isExpired() + ")";
                                                                log.warn("Dispatch rejected: {}", reason);
                                                                return denyDispatch(agentId, traceId, tenantId, reason);
                                                            }
                                                            return doModeSelectionAndDispatch(agentId, input, ctxWithVersion, release,
                                                                    tenantId, traceId, finalSkillId);
                                                        });
                                            }
                                            return doModeSelectionAndDispatch(agentId, input, ctxWithVersion, release,
                                                    tenantId, traceId, finalSkillId);
                                        }));
            }

            return doModeSelectionAndDispatch(agentId, input, ctxWithVersion, release, tenantId, traceId, finalSkillId);
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
                    currentLedgerHash(tenantId));
            
            // Extract resolved version context from context if available
            VersionContext versionCtx = VersionContext.empty();
            Object vcObj = ctx.getConfig("versionContext");
            if (vcObj instanceof VersionContext) {
                versionCtx = (VersionContext) vcObj;
            }
            
            // Extract a meaningful task description from context (e.g. from input converted to String or a context tag)
            String taskDescription = extractTaskDescription(input, ctx);

            return modeSelector.selectMode(
                    skillId, agentId, tenantId,
                    taskDescription,
                    ctx.getTraceId() != null ? ctx.getTraceId() : "",
                    versionCtx)
                    .then(modeResult -> {
                        // Check if mode selection blocked execution
                        if (modeResult.supervision() == com.ghatana.agent.runtime.mode.SupervisionMode.BLOCKED) {
                            String reason = "Mode selection blocked execution for agent " + agentId;
                            log.warn("Dispatch rejected: {}", reason);
                            return denyDispatch(agentId, traceId, tenantId, reason);
                        }
                        
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
                                .then(ignored -> doInvariantsAndDispatch(agentId, input, ctxWithMode, release, tenantId, traceId, skillId));
                    });
        }

        return doInvariantsAndDispatch(agentId, input, ctx, release, tenantId, traceId, null);
    }

    /**
     * Evaluates invariants and then dispatches to the delegate.
     */
    private <I, O> Promise<AgentResult<O>> doInvariantsAndDispatch(
            String agentId, I input, AgentContext ctx,
            @Nullable AgentRelease release,
            String tenantId, String traceId,
            @Nullable String skillId) {

        String releaseId = release != null ? release.agentReleaseId() : "";
        AgentRunSpan runSpan = agentRunTracer != null
                ? agentRunTracer.startRun(agentId, releaseId, tenantId, traceId)
                : null;

        // Build trace event builder for this dispatch
        TraceEventBuilder eventBuilder = new TraceEventBuilder(
                traceId, agentId, tenantId,
                currentLedgerHash(tenantId));

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
        Promise<AgentContext> ctxWithMemoryPromise = Promise.of(finalCtx);
        
        return traceLedger.append(dispatchEvent)
                .then(v -> ctxWithMemoryPromise.then(ctxWithMemory -> 
                        delegate.<I, O>dispatch(agentId, finalInput, ctxWithMemory)))
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
     *
     * <p>Each entry in {@link AgentRelease#compatibleRuntimeVersions()} is formatted as
     * {@code name:pattern}, where {@code pattern} ends with {@code .x} for prefix matching
     * or is an exact version string. If no constraints are declared the release is considered
     * universally compatible.
     */
    private boolean isVersionCompatible(@NotNull AgentRelease release, @NotNull VersionContext versionContext) {
        java.util.List<String> constraints = release.compatibleRuntimeVersions();
        if (constraints.isEmpty()) {
            return true;
        }
        for (String constraint : constraints) {
            int colon = constraint.indexOf(':');
            if (colon < 0) {
                log.warn("Malformed compatibleRuntimeVersion constraint (missing ':'): {}", constraint);
                continue;
            }
            String runtimeName = constraint.substring(0, colon);
            String pattern = constraint.substring(colon + 1);
            String actualVersion = versionContext.runtimes().get(runtimeName);
            if (actualVersion == null) {
                log.warn("Required runtime '{}' not present in version context for release {}",
                        runtimeName, release.agentReleaseId());
                return false;
            }
            if (!runtimeVersionMatches(actualVersion, pattern)) {
                log.warn("Runtime '{}' version '{}' does not satisfy constraint '{}' for release {}",
                        runtimeName, actualVersion, pattern, release.agentReleaseId());
                return false;
            }
        }
        return true;
    }

    /**
     * Returns {@code true} if {@code actual} satisfies {@code pattern}.
     *
     * <p>Patterns ending with {@code .x} are treated as prefix patterns (major or minor);
     * all other patterns require an exact match.
     */
    private static boolean runtimeVersionMatches(@NotNull String actual, @NotNull String pattern) {
        if (pattern.endsWith(".x")) {
            String prefix = pattern.substring(0, pattern.length() - 1); // e.g. "2." or "2.1."
            return actual.startsWith(prefix);
        }
        return actual.equals(pattern);
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

    @NotNull
    private String currentLedgerHash(@NotNull String tenantId) {
        if (traceLedger instanceof HashChainedTraceAppender hashChainedTraceAppender) {
            return hashChainedTraceAppender.getLastHash(tenantId);
        }
        if (traceLedger instanceof DataCloudAgentTraceLedger dataCloudAgentTraceLedger) {
            return dataCloudAgentTraceLedger.getLastHash(tenantId);
        }
        return "";
    }

    /**
     * Extracts a meaningful task description from the dispatched input or agent context.
     *
     * <p>Priority order:
     * <ol>
     *   <li>Context config key {@code "taskDescription"} — set by callers with an explicit task.</li>
     *   <li>Context config key {@code "task"} — shorthand variant.</li>
     *   <li>{@code input.toString()} when the result is short enough (&le;200 chars) and non-default.</li>
     *   <li>Fallback: {@code agentId + " dispatch"} so mode-selector never receives a blank description.</li>
     * </ol>
     */
    @NotNull
    private <I> String extractTaskDescription(@Nullable I input, @NotNull AgentContext ctx) {
        Object taskDesc = ctx.getConfig("taskDescription");
        if (taskDesc instanceof String s && !s.isBlank()) {
            return s;
        }
        Object task = ctx.getConfig("task");
        if (task instanceof String s && !s.isBlank()) {
            return s;
        }
        if (input != null) {
            String repr = input.toString();
            // Avoid using unhelpful JVM default representations like "ClassName@hexhash"
            if (!repr.contains("@") && repr.length() <= 200 && !repr.isBlank()) {
                return repr;
            }
        }
        String agentId = ctx.getAgentId();
        return agentId + " dispatch";
    }

    /**
     * Derives a skillId deterministically from agent metadata when not explicitly provided.
     *
     * <p>Derivation priority:
     * <ol>
     *   <li>Capability manifest skillId (if manifest is configured)</li>
     *   <li>Release metadata skillId (if release has skill metadata)</li>
     *   <li>AgentId as skillId (fallback - assumes agentId represents the skill)</li>
     * </ol>
     *
     * <p>This ensures mastery-bound dispatch can proceed without requiring explicit skillId
     * in every context, while maintaining deterministic behavior.
     *
     * @param agentId agent identifier
     * @param release agent release (may be null)
     * @param capabilityManifest capability manifest (may be null)
     * @return derived skillId, or null if derivation fails
     */
    @Nullable
    private String deriveSkillId(
            @NotNull String agentId,
            @Nullable AgentRelease release,
            @Nullable AgentCapabilityManifest capabilityManifest) {
        // 1. Try capability manifest first
        if (capabilityManifest != null) {
            // Check if skillId is available via agentId or metadata
            Object manifestSkillId = capabilityManifest.agentId();
            if (manifestSkillId instanceof String s && !s.isBlank()) {
                return s;
            }
        }

        // 2. Try release agentId
        if (release != null) {
            Object releaseSkillId = release.agentId();
            if (releaseSkillId instanceof String s && !s.isBlank()) {
                return s;
            }
        }

        // 3. Fallback: use agentId as skillId
        // This assumes agentId represents the skill (common pattern in GAA)
        if (agentId != null && !agentId.isBlank()) {
            return agentId;
        }

        return null;
    }
}
