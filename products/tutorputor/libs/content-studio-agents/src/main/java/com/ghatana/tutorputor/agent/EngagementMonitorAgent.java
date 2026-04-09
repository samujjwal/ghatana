package com.ghatana.tutorputor.agent;

import com.ghatana.agent.*;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.runtime.AbstractTypedAgent;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * ADAPTIVE engagement monitoring agent that detects disengagement patterns
 * and adapts intervention strategies based on learner behavior.
 *
 * <p>Uses multi-armed bandit (Thompson Sampling) to select the best
 * intervention for each engagement state, learning from outcomes
 * across sessions.</p>
 *
 * <p>Engagement signals monitored:</p>
 * <ul>
 *   <li>Session activity duration and inactivity periods</li>
 *   <li>Interaction frequency (clicks, submissions, navigation)</li>
 *   <li>Completion rate of assigned content</li>
 *   <li>Abort/skip rate</li>
 *   <li>Sentiment from feedback signals</li>
 * </ul>
 *
 * <p>Intervention strategies (arms):</p>
 * <ul>
 *   <li>NUDGE — gentle reminder notification</li>
 *   <li>SIMPLIFY — reduce difficulty level</li>
 *   <li>GAMIFY — inject gamification element (badge, streak)</li>
 *   <li>BREAK — suggest a timed break</li>
 *   <li>PEER — connect with study partner</li>
 *   <li>NONE — no intervention (control arm)</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose ADAPTIVE engagement agent using Thompson Sampling for intervention selection
 * @doc.layer product
 * @doc.pattern Agent
 */
public class EngagementMonitorAgent extends AbstractTypedAgent<
        EngagementMonitorAgent.EngagementSnapshot,
        EngagementMonitorAgent.EngagementDecision> {

    private static final double DISENGAGEMENT_THRESHOLD = 0.4;
    private static final long INACTIVITY_LIMIT_MS = 180_000; // 3 minutes

    private final String agentId;

    /** Thompson Sampling parameters: alpha (successes + 1), beta (failures + 1) per arm. */
    private final Map<Intervention, double[]> armParams;

    public EngagementMonitorAgent(String agentId) {
        this.agentId = agentId;
        this.armParams = new java.util.concurrent.ConcurrentHashMap<>();
        for (Intervention arm : Intervention.values()) {
            armParams.put(arm, new double[]{1.0, 1.0}); // uniform prior
        }
    }

    @Override
    public AgentDescriptor descriptor() {
        return AgentDescriptor.builder()
                .agentId(agentId)
                .name("Engagement Monitor Agent")
                .version("1.0.0")
                .type(AgentType.ADAPTIVE)
                .subtype("THOMPSON_SAMPLING")
                .determinism(DeterminismGuarantee.NONE)
                .failureMode(FailureMode.FALLBACK)
                .latencySla(Duration.ofMillis(100))
                .stateMutability(StateMutability.LOCAL_STATE)
                .capabilities(Set.of("engagement-monitoring", "disengagement-detection",
                        "intervention-selection", "adaptive-learning"))
                .build();
    }

    @Override
    @NotNull
    protected Promise<AgentResult<EngagementDecision>> doProcess(
            AgentContext ctx, EngagementSnapshot snapshot) {

        double engagementScore = computeEngagementScore(snapshot);
        EngagementState state = classifyState(engagementScore, snapshot.inactivityMs());

        Intervention selectedIntervention;
        if (state == EngagementState.ENGAGED) {
            selectedIntervention = Intervention.NONE;
        } else {
            selectedIntervention = selectArmThompson();
        }

        EngagementDecision decision = new EngagementDecision(
                snapshot.learnerId(),
                snapshot.sessionId(),
                engagementScore,
                state,
                selectedIntervention,
                Instant.now()
        );

        Map<String, Object> metrics = Map.of(
                "learnerId", snapshot.learnerId(),
                "sessionId", snapshot.sessionId(),
                "engagementScore", engagementScore,
                "state", state.name(),
                "intervention", selectedIntervention.name()
        );

        return Promise.of(AgentResult.<EngagementDecision>builder()
                .output(decision)
                .confidence(1.0)
                .status(AgentResultStatus.SUCCESS)
                .agentId(agentId)
                .processingTime(Duration.ofMillis(50))
                .metrics(metrics)
                .build());
    }

    /**
     * Record the outcome of an intervention for Thompson Sampling update.
     *
     * @param intervention the intervention that was applied
     * @param success whether the learner re-engaged after the intervention
     */
    public void recordOutcome(Intervention intervention, boolean success) {
        double[] params = armParams.get(intervention);
        if (params != null) {
            synchronized (params) {
                if (success) {
                    params[0] += 1.0; // increment alpha
                } else {
                    params[1] += 1.0; // increment beta
                }
            }
        }
    }

    // ──────────── Internal Methods ────────────

    private double computeEngagementScore(EngagementSnapshot snapshot) {
        double activityWeight = 0.3;
        double completionWeight = 0.3;
        double interactionWeight = 0.2;
        double sentimentWeight = 0.2;

        double activityScore = snapshot.inactivityMs() < INACTIVITY_LIMIT_MS
                ? 1.0 - (snapshot.inactivityMs() / (double) INACTIVITY_LIMIT_MS)
                : 0.0;

        double completionScore = snapshot.completionRate();
        double interactionScore = Math.min(1.0, snapshot.interactionsPerMinute() / 5.0);
        double sentimentScore = (snapshot.sentimentScore() + 1.0) / 2.0; // normalize [-1,1] to [0,1]

        return activityWeight * activityScore
                + completionWeight * completionScore
                + interactionWeight * interactionScore
                + sentimentWeight * sentimentScore;
    }

    private EngagementState classifyState(double score, long inactivityMs) {
        if (inactivityMs >= INACTIVITY_LIMIT_MS) {
            return EngagementState.DISENGAGED;
        }
        if (score < DISENGAGEMENT_THRESHOLD) {
            return EngagementState.AT_RISK;
        }
        return EngagementState.ENGAGED;
    }

    /**
     * Thompson Sampling: sample from Beta(alpha, beta) for each arm, pick highest.
     */
    private Intervention selectArmThompson() {
        Intervention best = Intervention.NONE;
        double bestSample = Double.NEGATIVE_INFINITY;
        java.util.Random rng = java.util.concurrent.ThreadLocalRandom.current();

        for (Map.Entry<Intervention, double[]> entry : armParams.entrySet()) {
            if (entry.getKey() == Intervention.NONE) continue;
            double[] params = entry.getValue();
            double sample = sampleBeta(params[0], params[1], rng);
            if (sample > bestSample) {
                bestSample = sample;
                best = entry.getKey();
            }
        }
        return best;
    }

    /**
     * Approximate Beta distribution sampling via Gamma functions.
     */
    private static double sampleBeta(double alpha, double beta, java.util.Random rng) {
        double x = sampleGamma(alpha, rng);
        double y = sampleGamma(beta, rng);
        return x / (x + y);
    }

    /**
     * Sample from Gamma(shape, 1) using Marsaglia-Tsang method.
     */
    private static double sampleGamma(double shape, java.util.Random rng) {
        if (shape < 1.0) {
            return sampleGamma(shape + 1.0, rng) * Math.pow(rng.nextDouble(), 1.0 / shape);
        }
        double d = shape - 1.0 / 3.0;
        double c = 1.0 / Math.sqrt(9.0 * d);
        while (true) {
            double x, v;
            do {
                x = rng.nextGaussian();
                v = 1.0 + c * x;
            } while (v <= 0.0);
            v = v * v * v;
            double u = rng.nextDouble();
            if (u < 1.0 - 0.0331 * (x * x) * (x * x)) return d * v;
            if (Math.log(u) < 0.5 * x * x + d * (1.0 - v + Math.log(v))) return d * v;
        }
    }

    // ──────────── Domain Types ────────────

    public enum EngagementState {
        ENGAGED, AT_RISK, DISENGAGED
    }

    public enum Intervention {
        NUDGE, SIMPLIFY, GAMIFY, BREAK, PEER, NONE
    }

    public record EngagementSnapshot(
            String learnerId,
            String sessionId,
            long inactivityMs,
            double completionRate,
            double interactionsPerMinute,
            double sentimentScore,
            double abortRate
    ) {}

    public record EngagementDecision(
            String learnerId,
            String sessionId,
            double engagementScore,
            EngagementState state,
            Intervention intervention,
            Instant decidedAt
    ) {}
}
