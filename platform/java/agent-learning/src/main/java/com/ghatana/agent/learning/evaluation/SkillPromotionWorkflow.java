package com.ghatana.agent.learning.evaluation;

import com.ghatana.agent.learning.*;
import com.ghatana.agent.memory.store.MemoryPlane;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * Orchestrates the skill promotion workflow:
 * <ol>
 *   <li>Build evaluation context from memory plane</li>
 *   <li>Run composite evaluation gates</li>
 *   <li>Promote or reject based on gate results</li>
 *   <li>Record the decision in the learning plane</li>
 * </ol>
 *
 * @doc.type class
 * @doc.purpose Skill promotion orchestration
 * @doc.layer agent-learning
 */
public class SkillPromotionWorkflow {

    private static final Logger log = LoggerFactory.getLogger(SkillPromotionWorkflow.class);

    private final LearningPlane learningPlane;
    private final CompositeEvaluationGate evaluationGates;

    public SkillPromotionWorkflow(
            @NotNull LearningPlane learningPlane,
            @NotNull CompositeEvaluationGate evaluationGates) {
        this.learningPlane = Objects.requireNonNull(learningPlane, "learningPlane");
        this.evaluationGates = Objects.requireNonNull(evaluationGates, "evaluationGates");
    }

    /**
     * Attempts to promote a skill through evaluation gates.
     *
     * @param candidate The update candidate
     * @param context Evaluation context
     * @return Promotion result
     */
    @NotNull
    public Promise<PromotionResult> attemptPromotion(
            @NotNull UpdateCandidate candidate,
            @NotNull EvaluationContext context) {

        log.info("Attempting promotion for skill {} to version {}",
                candidate.getSkillId(), candidate.getProposedVersion());

        return evaluationGates.evaluate(candidate, context)
                .then(gateResult -> {
                    if (gateResult.passed()) {
                        log.info("All gates passed — promoting skill {} to v{}",
                                candidate.getSkillId(), candidate.getProposedVersion());
                        return learningPlane.promoteSkill(candidate.getSkillId(), candidate.getProposedVersion());
                    } else {
                        log.info("Gate(s) failed — rejecting promotion for skill {}: {}",
                                candidate.getSkillId(), gateResult.reason());
                        return Promise.of(PromotionResult.builder()
                                .skillId(candidate.getSkillId())
                                .targetVersion(candidate.getProposedVersion())
                                .previousVersion(candidate.getCurrentVersion())
                                .success(false)
                                .explanation("Gate evaluation failed: " + gateResult.reason())
                                .gateResults(List.of(
                                        PromotionResult.GateResult.builder()
                                                .gateName(gateResult.gateName())
                                                .passed(false)
                                                .score(gateResult.score())
                                                .reason(gateResult.reason())
                                                .build()))
                                .build());
                    }
                });
    }
}
