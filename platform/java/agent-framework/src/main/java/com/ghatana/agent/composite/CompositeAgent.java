/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.composite;

import com.ghatana.agent.*;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.runtime.AbstractTypedAgent;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Composite agent — aggregates results from multiple sub-agents.
 *
 * <p>Supports:
 * <ul>
 *   <li><b>WEIGHTED_AVERAGE</b> — weighted average of a numeric field</li>
 *   <li><b>MAJORITY_VOTE</b> — majority vote on a categorical field</li>
 *   <li><b>FIRST_MATCH</b> — first successful sub-agent result</li>
 *   <li><b>UNANIMOUS</b> — all sub-agents must agree</li>
 * </ul>
 *
 * <p>Creates a fan-out to all sub-agents, then aggregates results.
 * Individual sub-agent failures are isolated — they do not fail the composite.
 *
 * @since 2.0.0
 *
 * @doc.type class
 * @doc.purpose Agent composed of multiple sub-agents with orchestration logic
 * @doc.layer platform
 * @doc.pattern Service
 */
public class CompositeAgent
        extends AbstractTypedAgent<Map<String, Object>, Map<String, Object>> {

    private final AgentDescriptor descriptor;
    private volatile CompositeAgentConfig compositeConfig;
    private volatile List<TypedAgent<Map<String, Object>, Map<String, Object>>> subAgents = List.of();

    // ═══════════════════════════════════════════════════════════════════════════
    // Construction
    // ═══════════════════════════════════════════════════════════════════════════

    public CompositeAgent(@NotNull String agentId) {
        this.descriptor = AgentDescriptor.builder()
                .agentId(agentId)
                .name(agentId)
                .description("Composite agent — ensemble of sub-agents")
                .version("1.0.0")
                .type(AgentType.COMPOSITE)
                .determinism(DeterminismGuarantee.NONE)
                .stateMutability(StateMutability.STATELESS)
                .failureMode(FailureMode.FALLBACK)
                .latencySla(Duration.ofMillis(100))
                .throughputTarget(10_000)
                .build();
    }

    public CompositeAgent(@NotNull AgentDescriptor descriptor) {
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
        if (!(config instanceof CompositeAgentConfig cc)) {
            return Promise.ofException(new IllegalArgumentException(
                    "Expected CompositeAgentConfig but got " + config.getClass().getSimpleName()));
        }
        this.compositeConfig = cc;

        if (subAgents.isEmpty()) {
            return Promise.ofException(new IllegalStateException(
                    "CompositeAgent requires at least one sub-agent"));
        }

        log.info("Initialized composite agent: {} subAgents={} strategy={}",
                descriptor.getAgentId(), subAgents.size(), cc.getAggregationStrategy());
        return Promise.complete();
    }

    @Override
    @NotNull
    protected Promise<AgentResult<Map<String, Object>>> doProcess(
            @NotNull AgentContext ctx, @NotNull Map<String, Object> input) {

        if (compositeConfig == null || subAgents.isEmpty()) {
            return Promise.of(AgentResult.failure(
                    new IllegalStateException("Not configured or no sub-agents"),
                    descriptor.getAgentId(), Duration.ZERO));
        }

        // Fan-out to all sub-agents
        List<Promise<AgentResult<Map<String, Object>>>> promises = new ArrayList<>();
        for (TypedAgent<Map<String, Object>, Map<String, Object>> sub : subAgents) {
            promises.add(sub.process(ctx, input)
                    .then(
                            Promise::of,
                            ex -> {
                                log.warn("Sub-agent {} failed: {}",
                                        sub.descriptor().getAgentId(), ex.getMessage());
                                return Promise.of(AgentResult.<Map<String, Object>>builder()
                                        .output(Map.of())
                                        .confidence(0)
                                        .status(AgentResultStatus.FAILED)
                                        .explanation("Error: " + ex.getMessage())
                                        .build());
                            }
                    ));
        }

        return Promises.toList(promises)
                .map(results -> aggregate(ctx, results));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Aggregation Strategies
    // ═══════════════════════════════════════════════════════════════════════════

    private AgentResult<Map<String, Object>> aggregate(
            AgentContext ctx, List<AgentResult<Map<String, Object>>> results) {

        List<AgentResult<Map<String, Object>>> successful = results.stream()
                .filter(AgentResult::isSuccess)
                .toList();

        ctx.recordMetric("agent.composite.totalSubAgents", results.size());
        ctx.recordMetric("agent.composite.successfulSubAgents", successful.size());

        if (successful.isEmpty()) {
            return AgentResult.<Map<String, Object>>builder()
                    .output(Map.of())
                    .confidence(0)
                    .status(AgentResultStatus.FAILED)
                    .explanation("All sub-agents failed")
                    .build();
        }

        return switch (compositeConfig.getAggregationStrategy()) {
            case WEIGHTED_AVERAGE -> aggregateWeightedAverage(successful);
            case MAJORITY_VOTE -> aggregateMajorityVote(successful);
            case FIRST_MATCH -> aggregateFirstMatch(successful);
            case UNANIMOUS -> aggregateUnanimous(successful, results.size());
        };
    }

    private AgentResult<Map<String, Object>> aggregateWeightedAverage(
            List<AgentResult<Map<String, Object>>> results) {

        String numField = compositeConfig.getNumericField();
        double weightedSum = 0;
        double totalWeight = 0;

        List<Double> weights = compositeConfig.getWeights();
        for (int i = 0; i < results.size() && i < (weights.isEmpty() ? results.size() : weights.size()); i++) {
            AgentResult<Map<String, Object>> r = results.get(i);
            double w = weights.isEmpty() ? 1.0 : (i < weights.size() ? weights.get(i) : 0.0);
            Object val = r.getOutput() != null ? r.getOutput().get(numField) : null;
            if (val instanceof Number n) {
                weightedSum += n.doubleValue() * w;
                totalWeight += w;
            }
        }

        double avg = totalWeight > 0 ? weightedSum / totalWeight : 0;
        double avgConfidence = results.stream()
                .mapToDouble(AgentResult::getConfidence).average().orElse(0);

        Map<String, Object> output = new LinkedHashMap<>();
        output.put(numField, avg);
        output.put("_composite.strategy", "WEIGHTED_AVERAGE");
        output.put("_composite.subAgentCount", results.size());

        return AgentResult.<Map<String, Object>>builder()
                .output(output)
                .confidence(avgConfidence)
                .status(AgentResultStatus.SUCCESS)
                .explanation(String.format("Weighted average of %d sub-agents: %s=%.4f",
                        results.size(), numField, avg))
                .build();
    }

    private AgentResult<Map<String, Object>> aggregateMajorityVote(
            List<AgentResult<Map<String, Object>>> results) {

        String voteField = compositeConfig.getVotingField();
        Map<String, Long> votes = new LinkedHashMap<>();

        for (AgentResult<Map<String, Object>> r : results) {
            Object val = r.getOutput() != null ? r.getOutput().get(voteField) : null;
            if (val != null) {
                votes.merge(val.toString(), 1L, Long::sum);
            }
        }

        if (votes.isEmpty()) {
            return AgentResult.<Map<String, Object>>builder()
                    .output(Map.of())
                    .confidence(0)
                    .status(AgentResultStatus.DEGRADED)
                    .explanation("No votes collected for field: " + voteField)
                    .build();
        }

        Map.Entry<String, Long> winner = votes.entrySet().stream()
                .max(Map.Entry.comparingByValue()).orElseThrow();
        double confidence = (double) winner.getValue() / results.size();

        Map<String, Object> output = new LinkedHashMap<>();
        output.put(voteField, winner.getKey());
        output.put("_composite.strategy", "MAJORITY_VOTE");
        output.put("_composite.votes", votes);
        output.put("_composite.voteCount", results.size());

        return AgentResult.<Map<String, Object>>builder()
                .output(output)
                .confidence(confidence)
                .status(AgentResultStatus.SUCCESS)
                .explanation(String.format("Majority vote: %s=%s (%d/%d)",
                        voteField, winner.getKey(), winner.getValue(), results.size()))
                .build();
    }

    private AgentResult<Map<String, Object>> aggregateFirstMatch(
            List<AgentResult<Map<String, Object>>> results) {

        AgentResult<Map<String, Object>> first = results.get(0);
        Map<String, Object> output = first.getOutput() != null
                ? new LinkedHashMap<>(first.getOutput()) : new LinkedHashMap<>();
        output.put("_composite.strategy", "FIRST_MATCH");

        return AgentResult.<Map<String, Object>>builder()
                .output(output)
                .confidence(first.getConfidence())
                .status(first.getStatus())
                .explanation("First match from " + results.size() + " sub-agents")
                .build();
    }

    private AgentResult<Map<String, Object>> aggregateUnanimous(
            List<AgentResult<Map<String, Object>>> successful, int totalCount) {

        if (successful.size() < totalCount) {
            return AgentResult.<Map<String, Object>>builder()
                    .output(Map.of())
                    .confidence(0)
                    .status(AgentResultStatus.DEGRADED)
                    .explanation(String.format("Unanimity failed: %d/%d agreed",
                            successful.size(), totalCount))
                    .build();
        }

        // Check that all outputs agree on the voting field
        String voteField = compositeConfig.getVotingField();
        Set<String> distinct = successful.stream()
                .map(r -> r.getOutput() != null ? String.valueOf(r.getOutput().get(voteField)) : "null")
                .collect(Collectors.toSet());

        if (distinct.size() > 1) {
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("_composite.strategy", "UNANIMOUS");
            output.put("_composite.distinctValues", distinct);
            return AgentResult.<Map<String, Object>>builder()
                    .output(output)
                    .confidence(0)
                    .status(AgentResultStatus.DEGRADED)
                    .explanation("Unanimous disagreement on " + voteField + ": " + distinct)
                    .build();
        }

        // All agree
        AgentResult<Map<String, Object>> first = successful.get(0);
        Map<String, Object> output = first.getOutput() != null
                ? new LinkedHashMap<>(first.getOutput()) : new LinkedHashMap<>();
        output.put("_composite.strategy", "UNANIMOUS");
        output.put("_composite.agreementCount", successful.size());

        return AgentResult.<Map<String, Object>>builder()
                .output(output)
                .confidence(1.0) // Full agreement = highest confidence
                .status(AgentResultStatus.SUCCESS)
                .explanation("Unanimous agreement across " + successful.size() + " sub-agents")
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Sub-agent Management
    // ═══════════════════════════════════════════════════════════════════════════

    public void setSubAgents(
            @NotNull List<TypedAgent<Map<String, Object>, Map<String, Object>>> agents) {
        this.subAgents = List.copyOf(agents);
    }

    public List<TypedAgent<Map<String, Object>, Map<String, Object>>> getSubAgents() {
        return subAgents;
    }
}
