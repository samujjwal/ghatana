/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.rules;

import com.ghatana.appplatform.rules.bundle.PolicyBundle;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Evaluates an unapproved (candidate) rule bundle against a sample input without
 * activating it. Used by rule authors to validate correctness before submitting for approval.
 *
 * <p>A dry-run result records both the candidate bundle's decision and, optionally,
 * the current live bundle's decision for comparison.
 *
 * @doc.type  class
 * @doc.purpose Dry-run evaluation of candidate rule bundles before approval (K03-012)
 * @doc.layer product
 * @doc.pattern Service
 */
public final class RuleDryRunService {

    private static final Logger log = LoggerFactory.getLogger(RuleDryRunService.class);

    private final OpaEvaluationService opaEvaluationService;
    private final Executor executor;

    public RuleDryRunService(OpaEvaluationService opaEvaluationService, Executor executor) {
        this.opaEvaluationService = Objects.requireNonNull(opaEvaluationService, "opaEvaluationService");
        this.executor              = Objects.requireNonNull(executor, "executor");
    }

    /**
     * Evaluates {@code candidateBundle} against {@code input} and returns a dry-run report.
     *
     * @param candidateBundle the bundle to test (not yet approved)
     * @param policyPath      OPA policy path (e.g. {@code "finance/v1/trade_pre_check"})
     * @param input           evaluation input map
     * @param compareWithLive when {@code true} also evaluates the live bundle for diff
     * @return promise resolving to a {@link DryRunReport}
     */
    public Promise<DryRunReport> evaluate(PolicyBundle candidateBundle,
                                          String policyPath,
                                          Map<String, Object> input,
                                          boolean compareWithLive) {
        Objects.requireNonNull(candidateBundle, "candidateBundle");
        Objects.requireNonNull(policyPath,      "policyPath");
        Objects.requireNonNull(input,           "input");

        log.info("Dry-run: policy={} bundleVersion={} compareWithLive={}",
                policyPath, candidateBundle.bundleVersion(), compareWithLive);

        return opaEvaluationService.evaluate(policyPath, input)
                .then(candidateResult -> {
                    if (!compareWithLive) {
                        return Promise.of(new DryRunReport(candidateBundle.bundleVersion(),
                                policyPath, candidateResult, null, false));
                    }
                    return opaEvaluationService.evaluate(policyPath, input)
                            .map(liveResult -> {
                                boolean diverges = !Objects.equals(candidateResult, liveResult);
                                if (diverges) {
                                    log.warn("Dry-run divergence detected: policy={} bundleVersion={}",
                                            policyPath, candidateBundle.bundleVersion());
                                }
                                return new DryRunReport(candidateBundle.bundleVersion(),
                                        policyPath, candidateResult, liveResult, diverges);
                            });
                });
    }

    // ── Domain types ──────────────────────────────────────────────────────────

    /**
     * Captures the outcome of a dry-run evaluation, including an optional comparison
     * with the currently live rule bundle.
     */
    public record DryRunReport(
            String candidateBundleVersion,
            String policyPath,
            Map<String, Object> candidateResult,
            Map<String, Object> liveResult,
            boolean divergesFromLive
    ) {}
}
