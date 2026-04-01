package com.ghatana.tutorputor.agent;

import com.ghatana.agent.*;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.runtime.AbstractTypedAgent;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import com.ghatana.agent.AgentResultStatus;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * Bayesian Knowledge Tracing (BKT) agent implementing the PROBABILISTIC agent type.
 *
 * <p>Wraps the existing BKT evidence processing logic as a typed agent
 * in the Ghatana agent framework. Computes the posterior probability
 * P(L) that a learner has mastered a skill, given a sequence of binary
 * observations (correct/incorrect).</p>
 *
 * <p>BKT model parameters:</p>
 * <ul>
 *   <li>P(L0) — prior probability of knowing the skill</li>
 *   <li>P(T) — probability of learning the skill on each opportunity</li>
 *   <li>P(G) — probability of guessing correctly despite not knowing</li>
 *   <li>P(S) — probability of slipping (incorrect despite knowing)</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose PROBABILISTIC agent for Bayesian Knowledge Tracing mastery estimation
 * @doc.layer product
 * @doc.pattern Agent
 */
public class BKTMasteryAgent extends AbstractTypedAgent<BKTMasteryAgent.BKTInput, BKTMasteryAgent.BKTOutput> {

    private static final double DEFAULT_P_L0 = 0.1;
    private static final double DEFAULT_P_T = 0.3;
    private static final double DEFAULT_P_G = 0.2;
    private static final double DEFAULT_P_S = 0.1;
    private static final double MASTERY_THRESHOLD = 0.95;

    private final String agentId;

    public BKTMasteryAgent(String agentId) {
        this.agentId = agentId;
    }

    @Override
    public AgentDescriptor descriptor() {
        return AgentDescriptor.builder()
                .agentId(agentId)
                .name("BKT Mastery Agent")
                .version("1.0.0")
                .type(AgentType.PROBABILISTIC)
                .subtype("BAYESIAN_KNOWLEDGE_TRACING")
                .determinism(DeterminismGuarantee.NONE)
                .failureMode(FailureMode.FALLBACK)
                .latencySla(Duration.ofMillis(50))
                .capabilities(Set.of("mastery-estimation", "knowledge-tracing", "adaptive-learning"))
                .build();
    }

    @Override
    @NotNull
    protected Promise<AgentResult<BKTOutput>> doProcess(AgentContext ctx, BKTInput input) {
        double pL = input.priorPL() > 0 ? input.priorPL() : DEFAULT_P_L0;
        double pT = input.pT() > 0 ? input.pT() : DEFAULT_P_T;
        double pG = input.pG() > 0 ? input.pG() : DEFAULT_P_G;
        double pS = input.pS() > 0 ? input.pS() : DEFAULT_P_S;

        // Process each observation: update P(L) via Bayesian update
        for (boolean correct : input.observations()) {
            double pCorrectGivenLearned = 1.0 - pS;
            double pCorrectGivenNotLearned = pG;

            double pCorrect = pL * pCorrectGivenLearned + (1 - pL) * pCorrectGivenNotLearned;
            double pNotCorrect = 1 - pCorrect;

            if (correct) {
                // P(L | correct) = P(correct | L) * P(L) / P(correct)
                pL = (pCorrectGivenLearned * pL) / pCorrect;
            } else {
                // P(L | incorrect) = P(incorrect | L) * P(L) / P(incorrect)
                double pIncorrectGivenLearned = pS;
                pL = (pIncorrectGivenLearned * pL) / pNotCorrect;
            }

            // Apply learning transition
            pL = pL + (1 - pL) * pT;
        }

        boolean mastered = pL >= MASTERY_THRESHOLD;

        BKTOutput output = new BKTOutput(
                input.skillId(),
                pL,
                mastered,
                input.observations().length,
                MASTERY_THRESHOLD
        );

        Map<String, Object> metrics = Map.of(
                "skillId", input.skillId(),
                "posteriorPL", pL,
                "mastered", mastered,
                "observationCount", input.observations().length
        );

        return Promise.of(AgentResult.<BKTOutput>builder()
                .output(output)
                .confidence(1.0)
                .status(AgentResultStatus.SUCCESS)
                .agentId(agentId)
                .processingTime(Duration.ofMillis(50))
                .metrics(metrics)
                .build());
    }

    // ──────────── Input/Output Records ────────────

    public record BKTInput(
            String skillId,
            boolean[] observations,
            double priorPL,
            double pT,
            double pG,
            double pS
    ) {
        public BKTInput(String skillId, boolean[] observations) {
            this(skillId, observations, DEFAULT_P_L0, DEFAULT_P_T, DEFAULT_P_G, DEFAULT_P_S);
        }
    }

    public record BKTOutput(
            String skillId,
            double posteriorPL,
            boolean mastered,
            int observationCount,
            double masteryThreshold
    ) {}
}
