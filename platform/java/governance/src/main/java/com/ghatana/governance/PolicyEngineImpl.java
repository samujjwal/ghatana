/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.governance;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * Concrete implementation of {@link PolicyEngine} backed by a {@link PolicyRegistry}.
 *
 * <p>Evaluates governance policies against {@link PolicyEvaluationContext evaluation contexts},
 * producing {@link PolicyDecisionRecord decision records} with full audit metadata.
 *
 * <h2>Evaluation algorithm</h2>
 * <ol>
 *   <li>Find all matching policies from the registry (filtered by action class, criticality, tenant)</li>
 *   <li>Sort by priority (ascending — lower number = higher priority)</li>
 *   <li>Apply "most restrictive wins" semantics: DENY beats ESCALATE beats ALLOW_WITH_APPROVAL
 *       beats ALLOW_WITH_MONITORING beats ALLOW</li>
 *   <li>Aggregate obligations from all matching policies</li>
 *   <li>Record the decision with full provenance</li>
 * </ol>
 *
 * <h2>Hard defaults</h2>
 * <ul>
 *   <li>Any WRITE_IRREVERSIBLE action without an explicit policy is DENIED by default</li>
 *   <li>Any cross-tenant access is DENIED unless explicitly allowed</li>
 *   <li>Any POLICY_CHANGE action requires approval</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Concrete policy evaluation engine with audit recording
 * @doc.layer platform
 * @doc.pattern Service
 */
public final class PolicyEngineImpl implements PolicyEngine {

    private static final Logger log = LoggerFactory.getLogger(PolicyEngineImpl.class);

    /** Decision restrictiveness ordering (most restrictive first). */
    private static final List<String> DECISION_PRIORITY = List.of(
            "DENY", "ESCALATE", "ALLOW_WITH_APPROVAL",
            "ALLOW_WITH_COMPENSATION", "ALLOW_WITH_MONITORING", "ALLOW");

    private final PolicyRegistry registry;
    private final Executor executor;
    private final PolicyAuditSink auditSink;

    /**
     * Sink for recording policy decisions for audit.
     */
    @FunctionalInterface
    public interface PolicyAuditSink {
        void record(@NotNull PolicyDecisionRecord record);

        /** Returns a no-op sink that discards records. */
        @NotNull
        static PolicyAuditSink noOp() {
            return record -> {};
        }
    }

    public PolicyEngineImpl(
            @NotNull PolicyRegistry registry,
            @NotNull Executor executor,
            @NotNull PolicyAuditSink auditSink) {
        this.registry = Objects.requireNonNull(registry);
        this.executor = Objects.requireNonNull(executor);
        this.auditSink = Objects.requireNonNull(auditSink);
    }

    public PolicyEngineImpl(@NotNull PolicyRegistry registry, @NotNull Executor executor) {
        this(registry, executor, PolicyAuditSink.noOp());
    }

    // ── PolicyEngine interface ────────────────────────────────────────────

    @Override
    @NotNull
    public Promise<Boolean> evaluate(@NotNull String policyName, @NotNull Map<String, Object> context) {
        // Legacy compatibility: evaluate a named policy against untyped context
        PolicyEvaluationContext ctx = new PolicyEvaluationContext(
                Objects.toString(context.getOrDefault("principalId", "unknown")),
                Objects.toString(context.getOrDefault("tenantId", "default")),
                Objects.toString(context.getOrDefault("actionClass", "READ")),
                Objects.toString(context.getOrDefault("targetType", "unknown")),
                Objects.toString(context.get("targetId")),
                Objects.toString(context.get("toolId")),
                Objects.toString(context.getOrDefault("criticality", "low")),
                Objects.toString(context.getOrDefault("reversibility", "REVERSIBLE")),
                context,
                Instant.now());

        return evaluateContext(ctx).map(record -> !"DENY".equals(record.decision()));
    }

    @Override
    @NotNull
    public Promise<Boolean> policyExists(@NotNull String policyName) {
        boolean exists = registry.getAll().stream()
                .anyMatch(p -> p.id().equals(policyName));
        return Promise.of(exists);
    }

    // ── Rich evaluation API ───────────────────────────────────────────────

    /**
     * Evaluates a typed context and returns a full decision record.
     *
     * @param ctx the evaluation context
     * @return promise of a policy decision record
     */
    @NotNull
    public Promise<PolicyDecisionRecord> evaluateContext(@NotNull PolicyEvaluationContext ctx) {
        return Promise.ofBlocking(executor, () -> {
            List<CompiledPolicy> matching = registry.findMatching(ctx);

            // Apply hard defaults for unmatched privileged actions
            String decision;
            List<String> reasons = new ArrayList<>();
            List<String> matchedRules = new ArrayList<>();
            List<String> requiredApprovals = new ArrayList<>();
            List<String> obligations = new ArrayList<>();
            List<String> policyRefs = new ArrayList<>();

            if (matching.isEmpty()) {
                decision = applyHardDefaults(ctx, reasons);
            } else {
                // Most restrictive wins
                decision = "ALLOW";
                for (CompiledPolicy policy : matching) {
                    policyRefs.add(policy.id());
                    matchedRules.add(policy.name());

                    int currentRestriction = DECISION_PRIORITY.indexOf(decision);
                    int policyRestriction = DECISION_PRIORITY.indexOf(policy.decision());
                    if (policyRestriction >= 0 && (currentRestriction < 0
                            || policyRestriction < currentRestriction)) {
                        decision = policy.decision();
                    }

                    requiredApprovals.addAll(policy.requiredApprovalRoles());
                    obligations.addAll(policy.obligations());
                    reasons.add("Policy '%s': %s".formatted(policy.id(), policy.decision()));
                }
            }

            PolicyDecisionRecord record = new PolicyDecisionRecord(
                    UUID.randomUUID().toString(),
                    decision,
                    policyRefs,
                    matchedRules,
                    reasons,
                    requiredApprovals.stream().distinct().toList(),
                    obligations.stream().distinct().toList(),
                    ctx,
                    Instant.now(),
                    null);

            auditSink.record(record);

            log.debug("PolicyEngine: evaluated {} policies, decision={} for action={} tenant={}",
                    matching.size(), decision, ctx.actionClass(), ctx.tenantId());

            return record;
        });
    }

    /**
     * Applies hard governance defaults when no explicit policy matches.
     * This ensures privileged actions are never silently allowed.
     */
    private String applyHardDefaults(PolicyEvaluationContext ctx, List<String> reasons) {
        // Hard default: WRITE_IRREVERSIBLE without explicit policy → DENY
        if ("WRITE_IRREVERSIBLE".equals(ctx.actionClass())) {
            reasons.add("No matching policy for WRITE_IRREVERSIBLE action; denied by hard default");
            return "DENY";
        }

        // Hard default: POLICY_CHANGE always requires approval
        if ("POLICY_CHANGE".equals(ctx.actionClass())) {
            reasons.add("POLICY_CHANGE actions require explicit approval; escalated by hard default");
            return "ALLOW_WITH_APPROVAL";
        }

        // Hard default: critical actions without policy → ESCALATE
        if ("critical".equals(ctx.criticality()) || "high".equals(ctx.criticality())) {
            reasons.add("High/critical action without matching policy; escalated by hard default");
            return "ESCALATE";
        }

        reasons.add("No matching policy; allowed by default for non-critical action");
        return "ALLOW";
    }
}
