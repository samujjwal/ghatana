/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.rules.approval;

import com.ghatana.appplatform.rules.bundle.PolicyBundle;
import com.ghatana.appplatform.rules.bundle.PolicyBundleService;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Jurisdiction rule pack registry — CRUD management of jurisdiction-to-bundle mappings
 * (STORY-K03-010).
 *
 * <p>Manages jurisdiction→bundle assignments. All changes require maker-checker
 * approval via {@link RuleChangeApprovalService}. The registry:
 * <ul>
 *   <li>Lists configured jurisdictions and their active bundle versions</li>
 *   <li>Sets a new bundle for a jurisdiction (creates pending approval)</li>
 *   <li>Deprecates a jurisdiction's bundle</li>
 * </ul>
 *
 * @doc.type  class
 * @doc.purpose Jurisdiction-scoped OPA bundle registry with audit trail (K03-010)
 * @doc.layer product
 * @doc.pattern Service
 */
public final class JurisdictionRuleRegistry {

    private static final Logger log = LoggerFactory.getLogger(JurisdictionRuleRegistry.class);

    /** In-memory store: jurisdictionCode → JurisdictionBinding */
    private final Map<String, JurisdictionBinding> bindings = new ConcurrentHashMap<>();

    private final PolicyBundleService bundleService;
    private final RuleChangeApprovalService approvalService;
    private final Executor executor;

    public JurisdictionRuleRegistry(PolicyBundleService bundleService,
                                    RuleChangeApprovalService approvalService,
                                    Executor executor) {
        this.bundleService   = Objects.requireNonNull(bundleService,   "bundleService");
        this.approvalService = Objects.requireNonNull(approvalService, "approvalService");
        this.executor        = Objects.requireNonNull(executor,        "executor");
    }

    // ── List jurisdictions ────────────────────────────────────────────────────

    /**
     * Lists all configured jurisdiction bindings.
     *
     * @return list of active bindings (not including deprecated)
     */
    public Promise<List<JurisdictionBinding>> listJurisdictions() {
        return Promise.ofBlocking(executor, () ->
                bindings.values().stream()
                        .filter(b -> b.status() != BindingStatus.DEPRECATED)
                        .toList());
    }

    /**
     * Gets the active binding for a specific jurisdiction.
     *
     * @param jurisdictionCode ISO country/jurisdiction code (e.g. {@code NP}, {@code IN})
     */
    public Promise<JurisdictionBinding> getJurisdiction(String jurisdictionCode) {
        Objects.requireNonNull(jurisdictionCode, "jurisdictionCode");
        return Promise.ofBlocking(executor, () -> {
            JurisdictionBinding binding = bindings.get(jurisdictionCode.toUpperCase());
            if (binding == null || binding.status() == BindingStatus.DEPRECATED) {
                throw new JurisdictionNotFoundException("No active binding for jurisdiction: " + jurisdictionCode);
            }
            return binding;
        });
    }

    // ── Set bundle (maker-checker gated) ─────────────────────────────────────

    /**
     * Submits a new bundle assignment for a jurisdiction for maker-checker approval.
     *
     * @param jurisdictionCode jurisdiction to configure
     * @param bundleName       OPA bundle name
     * @param bundleVersion    bundle version string
     * @param submittedBy      maker principal ID
     * @return approval request ID
     */
    public Promise<String> submitBundleAssignment(String jurisdictionCode,
                                                  String bundleName,
                                                  String bundleVersion,
                                                  String submittedBy) {
        Objects.requireNonNull(jurisdictionCode, "jurisdictionCode");
        Objects.requireNonNull(bundleName,       "bundleName");
        Objects.requireNonNull(bundleVersion,    "bundleVersion");
        Objects.requireNonNull(submittedBy,      "submittedBy");

        return approvalService.propose(
                submittedBy,
                "SET_BUNDLE:" + jurisdictionCode + "=" + bundleName + "@" + bundleVersion,
                Map.of(
                        "action",        "SET_BUNDLE",
                        "jurisdiction",  jurisdictionCode,
                        "bundleName",    bundleName,
                        "bundleVersion", bundleVersion
                ));
    }

    /**
     * Applies an approved bundle assignment. Called after approval is granted
     * by a checker via {@link RuleChangeApprovalService#approve(String, String)}.
     *
     * @param jurisdictionCode jurisdiction code
     * @param bundleName       OPA bundle name
     * @param bundleVersion    bundle version
     * @param approvalId       approval record ID for audit linkage
     */
    public void applyBundleAssignment(String jurisdictionCode, String bundleName,
                                      String bundleVersion, String approvalId) {
        JurisdictionBinding binding = new JurisdictionBinding(
                jurisdictionCode.toUpperCase(),
                bundleName,
                bundleVersion,
                BindingStatus.ACTIVE,
                approvalId
        );
        bindings.put(jurisdictionCode.toUpperCase(), binding);
        log.info("Applied bundle assignment: jurisdiction={} bundle={}@{} approval={}",
                jurisdictionCode, bundleName, bundleVersion, approvalId);
    }

    // ── Deprecate ─────────────────────────────────────────────────────────────

    /**
     * Marks a jurisdiction's bundle as deprecated. No new rules are evaluated
     * for the jurisdiction until a new bundle is configured.
     *
     * @param jurisdictionCode jurisdiction code
     * @param deprecatedBy     principal ID of the admin performing the action
     */
    public Promise<Void> deprecate(String jurisdictionCode, String deprecatedBy) {
        Objects.requireNonNull(jurisdictionCode, "jurisdictionCode");
        Objects.requireNonNull(deprecatedBy,     "deprecatedBy");

        return Promise.ofBlocking(executor, () -> {
            JurisdictionBinding existing = bindings.get(jurisdictionCode.toUpperCase());
            if (existing == null) {
                throw new JurisdictionNotFoundException("No binding found: " + jurisdictionCode);
            }
            bindings.put(jurisdictionCode.toUpperCase(),
                    new JurisdictionBinding(existing.jurisdictionCode(), existing.bundleName(),
                            existing.bundleVersion(), BindingStatus.DEPRECATED, existing.approvalId()));
            log.info("Deprecated bundle for jurisdiction={} by={}", jurisdictionCode, deprecatedBy);
            return null;
        });
    }

    // ── Domain types ──────────────────────────────────────────────────────────

    public record JurisdictionBinding(
            String jurisdictionCode,
            String bundleName,
            String bundleVersion,
            BindingStatus status,
            String approvalId
    ) {}

    public enum BindingStatus { ACTIVE, PENDING, DEPRECATED }

    /** Thrown when no binding exists for a requested jurisdiction. */
    public static final class JurisdictionNotFoundException extends RuntimeException {
        public JurisdictionNotFoundException(String message) { super(message); }
    }
}
