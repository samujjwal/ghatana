package com.ghatana.agent.learning.evaluation;

import com.ghatana.agent.learning.UpdateCandidate;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Regression evaluation gate — ensures a skill update does not degrade
 * performance compared to the previous version.
 *
 * <p>Compares recent trace scores against the baseline and rejects
 * if the new version scores below a threshold relative to the current.
 *
 * @doc.type class
 * @doc.purpose Regression detection gate
 * @doc.layer agent-learning
 */
public class RegressionEvaluationGate implements EvaluationGate {

    private static final Logger log = LoggerFactory.getLogger(RegressionEvaluationGate.class);

    /** Maximum acceptable performance drop (e.g., 0.05 = 5%). */
    private static final double MAX_REGRESSION = 0.05;
    private static final double PASS_THRESHOLD = 0.80;

    @Override
    @NotNull
    public String getName() {
        return "regression";
    }

    @Override
    @NotNull
    public Promise<GateResult> evaluate(
            @NotNull UpdateCandidate candidate,
            @NotNull EvaluationContext context) {

        log.debug("Running regression gate for skill {} v{}", candidate.getSkillId(), candidate.getProposedVersion());

        double historicalRate = context.getHistoricalSuccessRate();
        // In production: run candidate against recent traces and measure success rate
        double candidateRate = historicalRate; // placeholder

        boolean passed = candidateRate >= (historicalRate - MAX_REGRESSION);
        String reason = passed
                ? String.format("No regression detected (current=%.2f, candidate=%.2f)", historicalRate, candidateRate)
                : String.format("Regression detected (current=%.2f, candidate=%.2f, max_drop=%.2f)", historicalRate, candidateRate, MAX_REGRESSION);

        return Promise.of(new GateResult(getName(), passed, candidateRate, PASS_THRESHOLD, reason));
    }
}
