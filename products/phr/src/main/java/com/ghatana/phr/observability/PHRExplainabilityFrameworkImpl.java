package com.ghatana.phr.observability;

import com.ghatana.kernel.observability.ExplainabilityContext;
import com.ghatana.kernel.observability.ExplainabilityFramework;
import com.ghatana.kernel.observability.KernelTelemetryManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Component for PHRExplainabilityFrameworkImpl
 *
 * @doc.type class
 * @doc.purpose Component for PHRExplainabilityFrameworkImpl
 * @doc.layer product
 * @doc.pattern Service
 */
public class PHRExplainabilityFrameworkImpl implements ExplainabilityFramework {
    private final Map<String, Explanation> explanations = new ConcurrentHashMap<>();

    @Override
    public Explanation generateExplanation(KernelTelemetryManager.AgentAction action, ExecutionContext context) {
        return Explanation.builder()
            .decisionId(context.getAgentId() + "-" + System.currentTimeMillis())
            .summary("PHR decision explanation")
            .detailedReasoning("Decision made based on available health records")
            .featureContributions(Map.of())
            .confidence(0.85)
            .modelId("phr-model-v1")
            .metadata(Map.of())
            .build();
    }

    @Override
    public void recordDecisionExplanation(String decisionId, Explanation explanation) {
        explanations.put(decisionId, explanation);
    }

    @Override
    public Explanation getExplanation(String decisionId) {
        return explanations.get(decisionId);
    }

    @Override
    public ValidationResult validateExplanation(Explanation explanation) {
        if (explanation != null && explanation.getSummary() != null && !explanation.getSummary().isEmpty()) {
            return new ValidationResult(true, 0.85, "Explanation is valid");
        }
        return new ValidationResult(false, 0.0, "Explanation is invalid or empty");
    }
}
