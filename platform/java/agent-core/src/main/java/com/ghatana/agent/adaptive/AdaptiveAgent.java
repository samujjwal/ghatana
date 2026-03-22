/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.adaptive;

import com.ghatana.agent.*;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.runtime.AbstractTypedAgent;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Adaptive agent — learns from feedback to self-tune parameters over time.
 *
 * <p>Implements multi-armed bandit algorithms:
 * <ul>
 *   <li><b>UCB1</b> — Upper Confidence Bound (balance exploration/exploitation)</li>
 *   <li><b>THOMPSON_SAMPLING</b> — Bayesian sampling with Beta distributions</li>
 *   <li><b>EPSILON_GREEDY</b> — Random exploration with fixed probability</li>
 * </ul>
 *
 * <p>The agent discretizes a continuous parameter range into arms and tracks
 * reward for each arm. On each invocation, it selects the best arm according
 * to the chosen algorithm and returns the corresponding parameter value as output.
 *
 * @since 2.0.0
 *
 * @doc.type class
 * @doc.purpose Agent that adapts behavior based on learned patterns
 * @doc.layer platform
 * @doc.pattern Service
 * @doc.gaa.lifecycle reason
 */
public class AdaptiveAgent
        extends AbstractTypedAgent<Map<String, Object>, Map<String, Object>> {

    private final AgentDescriptor descriptor;
    private volatile AdaptiveAgentConfig adaptiveConfig;

    // ── Bandit state ────────────────────────────────────────────────────────
    private volatile double[] armValues;       // parameter value per arm
    private volatile double[] totalReward;     // cumulative reward per arm
    private volatile long[] pullCount;         // times each arm was pulled
    private volatile double[] successCount;    // successes per arm (for Thompson)
    private volatile double[] failureCountArr; // failures per arm (for Thompson)
    private final AtomicLong totalPulls = new AtomicLong(0);

    // ═══════════════════════════════════════════════════════════════════════════
    // Construction
    // ═══════════════════════════════════════════════════════════════════════════

    public AdaptiveAgent(@NotNull String agentId) {
        this.descriptor = AgentDescriptor.builder()
                .agentId(agentId)
                .name(agentId)
                .description("Adaptive agent — self-tuning via multi-armed bandit")
                .version("1.0.0")
                .type(AgentType.ADAPTIVE)
                .determinism(DeterminismGuarantee.NONE)
                .stateMutability(StateMutability.LOCAL_STATE)
                .failureMode(FailureMode.FALLBACK)
                .latencySla(Duration.ofMillis(5))
                .throughputTarget(50_000)
                .build();
    }

    public AdaptiveAgent(@NotNull AgentDescriptor descriptor) {
        this.descriptor = Objects.requireNonNull(descriptor);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TypedAgent Contract
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    @NotNull
    public AgentDescriptor descriptor() { return descriptor; }

    @Override
    @NotNull
    protected Promise<Void> doInitialize(@NotNull AgentConfig config) {
        if (!(config instanceof AdaptiveAgentConfig ac)) {
            return Promise.ofException(new IllegalArgumentException(
                    "Expected AdaptiveAgentConfig but got " + config.getClass().getSimpleName()));
        }

        this.adaptiveConfig = ac;
        int arms = ac.getArmCount();
        this.armValues = new double[arms];
        this.totalReward = new double[arms];
        this.pullCount = new long[arms];
        this.successCount = new double[arms];
        this.failureCountArr = new double[arms];
        this.totalPulls.set(0);

        // Initialise arm values — evenly distributed across parameter range
        double step = (ac.getParameterMax() - ac.getParameterMin()) / Math.max(arms - 1, 1);
        for (int i = 0; i < arms; i++) {
            armValues[i] = ac.getParameterMin() + i * step;
            successCount[i] = 1.0;   // Prior: Beta(1,1) = uniform
            failureCountArr[i] = 1.0;
        }

        log.info("Initialized adaptive agent: {} arms={} range=[{}, {}] algo={}",
                ac.getAgentId(), arms, ac.getParameterMin(), ac.getParameterMax(),
                ac.getBanditAlgorithm());
        return Promise.complete();
    }

    @Override
    @NotNull
    protected Promise<AgentResult<Map<String, Object>>> doProcess(
            @NotNull AgentContext ctx, @NotNull Map<String, Object> input) {

        if (adaptiveConfig == null) {
            return Promise.of(AgentResult.failure(
                    new IllegalStateException("Not configured"),
                    descriptor.getAgentId(), Duration.ZERO));
        }

        int selectedArm = selectArm();
        double parameterValue = armValues[selectedArm];
        pullCount[selectedArm]++;
        totalPulls.incrementAndGet();

        Map<String, Object> output = new LinkedHashMap<>();
        output.put(adaptiveConfig.getTunedParameter(), parameterValue);
        output.put("_adaptive.selectedArm", selectedArm);
        output.put("_adaptive.algorithm", adaptiveConfig.getBanditAlgorithm().name());
        output.put("_adaptive.totalPulls", totalPulls.get());
        output.put("_adaptive.armPulls", pullCount[selectedArm]);

        ctx.recordMetric("agent.adaptive.arm", selectedArm);
        ctx.recordMetric("agent.adaptive.parameterValue", parameterValue);

        return Promise.of(AgentResult.<Map<String, Object>>builder()
                .output(output)
                .confidence(computeExploitationRatio())
                .status(AgentResultStatus.SUCCESS)
                .explanation(String.format("Selected arm %d: %s=%.4f",
                        selectedArm, adaptiveConfig.getTunedParameter(), parameterValue))
                .build());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Arm Selection Algorithms
    // ═══════════════════════════════════════════════════════════════════════════

    private int selectArm() {
        return switch (adaptiveConfig.getBanditAlgorithm()) {
            case UCB1 -> selectUCB1();
            case THOMPSON_SAMPLING -> selectThompson();
            case EPSILON_GREEDY -> selectEpsilonGreedy();
        };
    }

    /** UCB1: maximize( avgReward + sqrt(2 * ln(totalPulls) / armPulls) ) */
    private int selectUCB1() {
        long total = totalPulls.get();
        int best = 0;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < armValues.length; i++) {
            if (pullCount[i] == 0) return i; // Explore unpulled arms first

            double avgReward = totalReward[i] / pullCount[i];
            double exploration = Math.sqrt(2.0 * Math.log(total + 1) / pullCount[i]);
            double score = avgReward + exploration;

            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    /** Thompson Sampling: sample from Beta(success, failure) per arm. */
    private int selectThompson() {
        int best = 0;
        double bestSample = Double.NEGATIVE_INFINITY;
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        for (int i = 0; i < armValues.length; i++) {
            // Approximate Beta sampling via Gamma distributions
            double sample = sampleBeta(successCount[i], failureCountArr[i], rng);
            if (sample > bestSample) {
                bestSample = sample;
                best = i;
            }
        }
        return best;
    }

    /** Epsilon-Greedy: with probability ε explore randomly, otherwise exploit. */
    private int selectEpsilonGreedy() {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        if (rng.nextDouble() < adaptiveConfig.getExplorationRate()) {
            return rng.nextInt(armValues.length);
        }
        // Exploit: pick arm with best average reward
        int best = 0;
        double bestAvg = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < armValues.length; i++) {
            double avg = pullCount[i] > 0 ? totalReward[i] / pullCount[i] : 0;
            if (avg > bestAvg) {
                bestAvg = avg;
                best = i;
            }
        }
        return best;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Feedback
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Records feedback (reward) for a previous arm selection.
     *
     * @param arm    the arm that was selected
     * @param reward the reward value (typically 0.0 to 1.0)
     */
    public void recordFeedback(int arm, double reward) {
        if (arm < 0 || arm >= armValues.length) {
            throw new IllegalArgumentException("Invalid arm index: " + arm);
        }
        totalReward[arm] += reward;
        if (reward > 0.5) {
            successCount[arm] += 1.0;
        } else {
            failureCountArr[arm] += 1.0;
        }
    }

    /**
     * Returns the current best arm (highest average reward).
     */
    public int getBestArm() {
        int best = 0;
        double bestAvg = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < armValues.length; i++) {
            double avg = pullCount[i] > 0 ? totalReward[i] / pullCount[i] : 0;
            if (avg > bestAvg) { bestAvg = avg; best = i; }
        }
        return best;
    }

    /**
     * Returns the parameter value of the best arm.
     */
    public double getBestParameterValue() {
        return armValues[getBestArm()];
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /** Exploitation ratio: fraction of pulls on the best arm. */
    private double computeExploitationRatio() {
        long total = totalPulls.get();
        if (total == 0) return 0.5;
        int best = getBestArm();
        return (double) pullCount[best] / total;
    }

    /** Approximate Beta(a,b) sampling via the Jöhnk method (good for small a,b). */
    private static double sampleBeta(double alpha, double beta, ThreadLocalRandom rng) {
        // Use Gamma ratio: X~Gamma(a,1), Y~Gamma(b,1) → X/(X+Y) ~ Beta(a,b)
        double x = sampleGamma(alpha, rng);
        double y = sampleGamma(beta, rng);
        return (x + y) > 0 ? x / (x + y) : 0.5;
    }

    /** Simple Gamma(k,1) sampling using Marsaglia & Tsang method for k>=1. */
    private static double sampleGamma(double shape, ThreadLocalRandom rng) {
        if (shape < 1.0) {
            // Boost: Gamma(a) = Gamma(a+1) * U^(1/a)
            return sampleGamma(shape + 1.0, rng) * Math.pow(rng.nextDouble(), 1.0 / shape);
        }
        double d = shape - 1.0 / 3.0;
        double c = 1.0 / Math.sqrt(9.0 * d);
        while (true) {
            double x, v;
            do {
                x = rng.nextGaussian();
                v = 1.0 + c * x;
            } while (v <= 0);
            v = v * v * v;
            double u = rng.nextDouble();
            if (u < 1 - 0.0331 * (x * x) * (x * x)
                    || Math.log(u) < 0.5 * x * x + d * (1 - v + Math.log(v))) {
                return d * v;
            }
        }
    }

    /** Snapshot of current arm statistics. */
    public Map<String, Object> getArmStatistics() {
        Map<String, Object> stats = new LinkedHashMap<>();
        for (int i = 0; i < armValues.length; i++) {
            Map<String, Object> arm = new LinkedHashMap<>();
            arm.put("value", armValues[i]);
            arm.put("pulls", pullCount[i]);
            arm.put("totalReward", totalReward[i]);
            arm.put("avgReward", pullCount[i] > 0 ? totalReward[i] / pullCount[i] : 0.0);
            stats.put("arm_" + i, arm);
        }
        stats.put("totalPulls", totalPulls.get());
        stats.put("bestArm", getBestArm());
        return stats;
    }
}
