/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.ghatana.agent.dispatch.tier;

import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.agent.AgentResult;
import com.ghatana.agent.AgentResultStatus;
import com.ghatana.agent.catalog.CatalogAgentEntry;
import com.ghatana.agent.framework.api.AgentContext;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Default {@link LlmExecutionPlan} implementation that builds prompts from
 * agent YAML generator templates and invokes the configured LLM provider.
 *
 * <p>Prompt variable substitution supports:
 * <ul>
 *   <li>{@code {{input}}} — serialized input payload</li>
 *   <li>{@code {{context}}} — serialized context from AgentContext</li>
 *   <li>Any key from the agent's memory or routing sections</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Default Tier-L execution via LLM prompt invocation
 * @doc.layer framework
 * @doc.pattern Strategy
 *
 * @author Ghatana AI Platform
 * @since 2.2.0
 */
public class DefaultLlmExecutionPlan implements LlmExecutionPlan {

    private static final Logger log = LoggerFactory.getLogger(DefaultLlmExecutionPlan.class);
    private static final double DEFAULT_INPUT_COST_PER_1K_USD = 0.01;
    private static final double DEFAULT_OUTPUT_COST_PER_1K_USD = 0.03;

    private final LlmProvider llmProvider;

    public DefaultLlmExecutionPlan(LlmProvider llmProvider) {
        this.llmProvider = llmProvider;
    }

    @Override
    @NotNull
    public Promise<AgentResult<Object>> execute(
            @NotNull CatalogAgentEntry entry,
            @NotNull Object input,
            @NotNull AgentContext ctx) {

        Instant start = Instant.now();
        String agentId = entry.getId();

        try {
            // Extract LLM step from generator definition
            LlmStepConfig stepConfig = extractLlmStep(entry);
            if (stepConfig == null) {
                return Promise.of(AgentResult.builder()
                        .status(AgentResultStatus.FAILED)
                        .confidence(0.0)
                        .agentId(agentId)
                        .explanation("No LLM step found in generator definition for agent: " + agentId)
                        .processingTime(Duration.between(start, Instant.now()))
                        .build());
            }

            // Build prompt with variable substitution
            String prompt = buildPrompt(stepConfig, input, ctx, entry);

            log.debug("Tier-L executing agent '{}' with provider={}, model={}, maxTokens={}",
                    agentId, stepConfig.provider(), stepConfig.model(), stepConfig.maxTokens());

            // Invoke LLM
            return llmProvider.invoke(
                    stepConfig.provider(),
                    stepConfig.model(),
                    prompt,
                    stepConfig.temperature(),
                    stepConfig.maxTokens(),
                    ctx
            ).then(completionResult -> {
                Duration elapsed = Duration.between(start, Instant.now());
                try {
                    return Promise.of(buildSuccessResult(agentId, ctx, stepConfig, completionResult, elapsed));
                } catch (AgentContext.BudgetExceededException e) {
                    log.warn("Tier-L execution exceeded budget for agent '{}': {}", agentId, e.getMessage());
                    return Promise.of(AgentResult.failure(e, agentId, elapsed));
                }
            });

        } catch (Exception e) {
            Duration elapsed = Duration.between(start, Instant.now());
            log.error("Tier-L execution failed for agent '{}': {}", agentId, e.getMessage(), e);
            return Promise.of(AgentResult.failure(e, agentId, elapsed));
        }
    }

    @SuppressWarnings("unchecked")
    private LlmStepConfig extractLlmStep(CatalogAgentEntry entry) {
        Map<String, Object> generator = entry.getGenerator();
        if (generator == null) return null;

        Object steps = generator.get("steps");
        if (!(steps instanceof List<?> stepList)) return null;

        for (Object step : stepList) {
            if (!(step instanceof Map<?, ?> stepMap)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> typedMap = (Map<String, Object>) stepMap;
            String type = String.valueOf(typedMap.getOrDefault("type", ""));
            if ("LLM".equalsIgnoreCase(type)) {
                if (!typedMap.containsKey("model")) {
                    throw new IllegalStateException(
                            "LLM execution plan step is missing required 'model' property — "
                            + "set it explicitly in the agent YAML (e.g. model: claude-3-5-sonnet-20241022). "
                            + "No hardcoded fallback is allowed.");
                }
                return new LlmStepConfig(
                        String.valueOf(typedMap.getOrDefault("provider", "ANTHROPIC")),
                        String.valueOf(typedMap.get("model")),
                        String.valueOf(typedMap.getOrDefault("prompt", "")),
                        parseDouble(typedMap.getOrDefault("temperature", 0.2)),
                        parseInt(typedMap.getOrDefault("max_tokens", 2000))
                );
            }
        }
        return null;
    }

    private String buildPrompt(LlmStepConfig config, Object input, AgentContext ctx, CatalogAgentEntry entry) {
        String prompt = config.promptTemplate();
        prompt = prompt.replace("{{input}}", String.valueOf(input));
        prompt = prompt.replace("{{context}}", ctx.toString());
        prompt = prompt.replace("{{agent_id}}", entry.getId());
        prompt = prompt.replace("{{agent_name}}", entry.getName());
        return prompt;
    }

    private double parseDouble(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(value)); }
        catch (NumberFormatException e) { return 0.2; }
    }

    private int parseInt(Object value) {
        if (value instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(value)); }
        catch (NumberFormatException e) { return 2000; }
    }

    private AgentResult<Object> buildSuccessResult(
            String agentId,
            AgentContext ctx,
            LlmStepConfig stepConfig,
            CompletionResult completionResult,
            Duration elapsed) throws AgentContext.BudgetExceededException {
        double actualCost = calculateActualCost(ctx, stepConfig.model(), completionResult);
        String resolvedModel = completionResult.getModelUsed() != null
                ? completionResult.getModelUsed()
                : stepConfig.model();

        ctx.addTraceTag("llm.provider", stepConfig.provider());
        ctx.addTraceTag("llm.model", resolvedModel);
        ctx.recordMetric("llm.cost", actualCost);
        ctx.recordMetric("llm.tokens.input", completionResult.getPromptTokens());
        ctx.recordMetric("llm.tokens.output", completionResult.getCompletionTokens());
        ctx.recordMetric("llm.tokens.total", completionResult.getTokensUsed());
        ctx.recordMetric("llm.latency", completionResult.getLatencyMs());
        ctx.deductCost(actualCost);

        return AgentResult.builder()
                .output(completionResult.getText())
                .confidence(0.85)
                .status(AgentResultStatus.SUCCESS)
                .agentId(agentId)
                .explanation("LLM execution completed via " + stepConfig.provider() + "/" + resolvedModel)
                .processingTime(elapsed)
                .metrics(Map.of(
                        "tier", "LLM_EXECUTED",
                        "provider", stepConfig.provider(),
                        "model", resolvedModel,
                        "prompt_tokens", completionResult.getPromptTokens(),
                        "completion_tokens", completionResult.getCompletionTokens(),
                        "total_tokens", completionResult.getTokensUsed(),
                        "latency_ms", completionResult.getLatencyMs(),
                        "cost_usd", actualCost
                ))
                .build();
    }

    private double calculateActualCost(AgentContext ctx, String model, CompletionResult completionResult) {
        double promptCostPer1k = resolveCostPer1k(ctx, model, "input", DEFAULT_INPUT_COST_PER_1K_USD);
        double completionCostPer1k = resolveCostPer1k(ctx, model, "output", DEFAULT_OUTPUT_COST_PER_1K_USD);
        return (completionResult.getPromptTokens() / 1000.0) * promptCostPer1k
                + (completionResult.getCompletionTokens() / 1000.0) * completionCostPer1k;
    }

    private double resolveCostPer1k(AgentContext ctx, String model, String direction, double defaultValue) {
        String normalizedModel = normalizeModelKey(model);
        Object modelOverride = ctx.getConfig("llm.cost." + normalizedModel + "." + direction + "Per1kUsd");
        if (modelOverride != null) {
            return parseDouble(modelOverride);
        }

        Object globalOverride = ctx.getConfig("llm.cost." + direction + "Per1kUsd");
        if (globalOverride != null) {
            return parseDouble(globalOverride);
        }

        return defaultValue;
    }

    private String normalizeModelKey(String model) {
        return model.toLowerCase().replaceAll("[^a-z0-9]+", "-");
    }

    private record LlmStepConfig(
            String provider,
            String model,
            String promptTemplate,
            double temperature,
            int maxTokens
    ) {}
}
