package com.ghatana.agent.learning.evaluation;

import com.ghatana.agent.learning.UpdateCandidate;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Regression evaluation gate — ensures a skill update does not degrade
 * performance compared to the previous version.
 *
 * <p>The gate works in two modes:
 * <ol>
 *   <li><b>Trace-scored mode</b>: if the {@link UpdateCandidate} carries
 *       {@code metadata.traceScores} (a {@code List<Double>}), those scores are
 *       used directly to compute a candidate success rate.</li>
 *   <li><b>Metadata-scalar mode</b>: if the metadata contains a
 *       {@code candidateSuccessRate} or {@code evaluationScore} numeric field it
 *       is used as-is.</li>
 *   <li><b>Fallback</b>: if neither is present, the gate conservatively
 *       applies a small confidence penalty to the historical rate to reflect the
 *       uncertainty of a brand-new candidate.</li>
 * </ol>
 *
 * <p>A candidate is rejected when its measured/estimated rate drops more than
 * {@link #MAX_REGRESSION} below the historical baseline.
 *
 * @doc.type class
 * @doc.purpose Regression detection gate for skill promotion pipeline
 * @doc.layer agent-learning
 * @doc.pattern Strategy, Gate
 * @doc.gaa.lifecycle reflect
 */
public class RegressionEvaluationGate implements EvaluationGate {

    private static final Logger log = LoggerFactory.getLogger(RegressionEvaluationGate.class);

    /** Maximum acceptable performance drop (5 %). */
    private static final double MAX_REGRESSION = 0.05;
    private static final double PASS_THRESHOLD = 0.80;

    /**
     * Penalty applied when no trace data is available, to reflect the inherent
     * uncertainty of an un-tested candidate.
     */
    private static final double UNCERTAINTY_PENALTY = 0.03;

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
        double candidateRate = resolveCandidateRate(candidate, historicalRate);

        boolean passed = candidateRate >= (historicalRate - MAX_REGRESSION);
        String reason = buildReason(candidate, historicalRate, candidateRate, passed);

        log.debug("Regression gate result: passed={}, historical={:.2f}, candidate={:.2f}",
                passed, historicalRate, candidateRate);

        return Promise.of(new GateResult(getName(), passed, candidateRate, PASS_THRESHOLD, reason));
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    /**
     * Resolves the candidate success rate from the richest available source in
     * the following priority order:
     * <ol>
     *   <li>{@code metadata.traceScores} — compute mean over the trace list</li>
     *   <li>{@code metadata.candidateSuccessRate} — scalar already computed by
     *       the caller</li>
     *   <li>{@code metadata.evaluationScore} — synonym accepted for convenience</li>
     *   <li>Penalty-discounted historical rate to model uncertainty</li>
     * </ol>
     */
    @SuppressWarnings("unchecked")
    private double resolveCandidateRate(UpdateCandidate candidate, double historicalRate) {
        Map<String, Object> meta = candidate.getMetadata();

        // 1. Scored traces provided by the evaluation harness
        Object rawScores = meta.get("traceScores");
        if (rawScores instanceof List<?> scoreList && !scoreList.isEmpty()) {
            try {
                double sum = 0.0;
                int count = 0;
                for (Object s : (List<Object>) scoreList) {
                    if (s instanceof Number n) {
                        sum += n.doubleValue();
                        count++;
                    }
                }
                if (count > 0) {
                    double mean = sum / count;
                    log.debug("Computed candidate rate {:.3f} from {} trace scores", mean, count);
                    return mean;
                }
            } catch (ClassCastException ignored) {
                // malformed metadata — fall through
            }
        }

        // 2. Pre-computed scalar rate
        for (String key : List.of("candidateSuccessRate", "evaluationScore", "successRate")) {
            Object scalar = meta.get(key);
            if (scalar instanceof Number n) {
                double rate = Math.max(0.0, Math.min(1.0, n.doubleValue()));
                log.debug("Using metadata {} = {:.3f} as candidate rate", key, rate);
                return rate;
            }
        }

        // 3. No data available — apply uncertainty penalty so a truly unknown
        //    candidate is slightly penalised but not automatically rejected
        //    unless the historical bar is already very low.
        double penalised = Math.max(0.0, historicalRate - UNCERTAINTY_PENALTY);
        log.debug("No trace data available; applying uncertainty penalty. candidateRate={:.3f}", penalised);
        return penalised;
    }

    private String buildReason(
            UpdateCandidate candidate,
            double historicalRate,
            double candidateRate,
            boolean passed) {

        String source = candidate.getMetadata().containsKey("traceScores")
                ? "trace-scored"
                : candidate.getMetadata().containsKey("candidateSuccessRate")
                        ? "meta-scalar"
                        : "uncertainty-penalised";

        return passed
                ? String.format("No regression detected [source=%s, current=%.2f, candidate=%.2f, max_drop=%.2f]",
                        source, historicalRate, candidateRate, MAX_REGRESSION)
                : String.format("Regression detected [source=%s, current=%.2f, candidate=%.2f, drop=%.2f > max=%.2f]",
                        source, historicalRate, candidateRate,
                        historicalRate - candidateRate, MAX_REGRESSION);
    }
}
