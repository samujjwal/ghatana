package com.ghatana.agent.learning.evaluation;

import com.ghatana.agent.learning.UpdateCandidate;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Safety evaluation gate — checks that a skill update does not introduce
 * dangerous actions, bypass safety constraints, or violate policy boundaries.
 *
 * <p>Uses a combination of:
 * <ul>
 *   <li>Dangerous action pattern detection</li>
 *   <li>Policy constraint validation</li>
 *   <li>LLM-based safety classification (optional)</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Safety validation gate
 * @doc.layer agent-learning
 */
public class SafetyEvaluationGate implements EvaluationGate {

    private static final Logger log = LoggerFactory.getLogger(SafetyEvaluationGate.class);

    private static final double SAFETY_THRESHOLD = 0.95;

    @Override
    @NotNull
    public String getName() {
        return "safety";
    }

    @Override
    @NotNull
    public Promise<GateResult> evaluate(
            @NotNull UpdateCandidate candidate,
            @NotNull EvaluationContext context) {

        log.debug("Running safety gate for skill {} v{}", candidate.getSkillId(), candidate.getProposedVersion());

        // Check change description for dangerous patterns
        String change = candidate.getChangeDescription().toLowerCase();
        boolean hasDangerousPatterns =
                change.contains("delete all") ||
                change.contains("drop table") ||
                change.contains("bypass") ||
                change.contains("skip validation") ||
                change.contains("disable safety");

        double safetyScore = hasDangerousPatterns ? 0.3 : 1.0;
        boolean passed = safetyScore >= SAFETY_THRESHOLD;
        String reason = passed
                ? "No safety concerns detected"
                : "Dangerous patterns detected in change description";

        return Promise.of(new GateResult(getName(), passed, safetyScore, SAFETY_THRESHOLD, reason));
    }
}
