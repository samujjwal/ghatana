package com.ghatana.agent.learning.evaluation;

import com.ghatana.agent.learning.UpdateCandidate;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Composite evaluation gate that runs multiple gates and aggregates
 * their results. All gates must pass for the composite to pass.
 *
 * @doc.type class
 * @doc.purpose Composite evaluation gate
 * @doc.layer agent-learning
 */
public class CompositeEvaluationGate implements EvaluationGate {

    private static final Logger log = LoggerFactory.getLogger(CompositeEvaluationGate.class);

    private final List<EvaluationGate> gates;

    public CompositeEvaluationGate(@NotNull List<EvaluationGate> gates) {
        this.gates = Objects.requireNonNull(gates, "gates");
        if (gates.isEmpty()) {
            throw new IllegalArgumentException("At least one gate is required");
        }
    }

    /**
     * Creates a default composite with regression + safety gates.
     */
    public static CompositeEvaluationGate defaultGates() {
        return new CompositeEvaluationGate(List.of(
                new RegressionEvaluationGate(),
                new SafetyEvaluationGate()
        ));
    }

    @Override
    @NotNull
    public String getName() {
        return "composite";
    }

    @Override
    @NotNull
    public Promise<GateResult> evaluate(
            @NotNull UpdateCandidate candidate,
            @NotNull EvaluationContext context) {

        log.debug("Running composite evaluation ({} gates) for skill {}",
                gates.size(), candidate.getSkillId());

        // Run all gates sequentially, collecting results
        return runGatesSequentially(candidate, context, 0, new ArrayList<>());
    }

    private Promise<GateResult> runGatesSequentially(
            UpdateCandidate candidate,
            EvaluationContext context,
            int index,
            List<GateResult> results) {

        if (index >= gates.size()) {
            // All gates done — aggregate
            boolean allPassed = results.stream().allMatch(GateResult::passed);
            double avgScore = results.stream().mapToDouble(GateResult::score).average().orElse(0.0);
            String reasons = results.stream()
                    .map(r -> r.gateName() + ": " + r.reason())
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("no gates");

            return Promise.of(new GateResult(getName(), allPassed, avgScore, 0.0, reasons));
        }

        EvaluationGate gate = gates.get(index);
        return gate.evaluate(candidate, context)
                .then(result -> {
                    results.add(result);
                    if (!result.passed()) {
                        log.info("Gate '{}' failed for skill {}: {}", gate.getName(), candidate.getSkillId(), result.reason());
                        // Short-circuit: fail fast if any gate fails
                        String reasons = results.stream()
                                .map(r -> r.gateName() + ": " + r.reason())
                                .reduce((a, b) -> a + "; " + b)
                                .orElse("");
                        return Promise.of(new GateResult(getName(), false, result.score(), 0.0, reasons));
                    }
                    return runGatesSequentially(candidate, context, index + 1, results);
                });
    }
}
