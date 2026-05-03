package com.ghatana.digitalmarketing.application.ai;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;

/**
 * Deterministic fallback adapter for {@link DmAgentOrchestrationPort} (DMOS-P1-018).
 *
 * <p>Provides deterministic, testable fallback behavior when the Kernel is unavailable
 * or for development/testing purposes. Generates consistent outputs based on input.</p>
 *
 * @doc.type class
 * @doc.purpose Deterministic fallback for agent orchestration (DMOS-P1-018)
 * @doc.layer application
 * @doc.pattern Adapter
 */
public final class DeterministicAgentOrchestrationAdapter implements DmAgentOrchestrationPort {

    private static final Logger logger = LoggerFactory.getLogger(DeterministicAgentOrchestrationAdapter.class);

    @Override
    public Promise<AgentResponse> invokeAgent(
        AgentType agentType,
        String prompt,
        String model,
        Map<String, Object> parameters,
        Duration timeout
    ) {
        return Promise.ofBlocking(() -> {
            logger.info("Using deterministic fallback for agent {}", agentType);
            return generateDeterministicResponse(agentType, prompt, model);
        });
    }

    @Override
    public Promise<Boolean> isAvailable() {
        return Promise.of(true);
    }

    @Override
    public Promise<AgentHealthStatus> getHealthStatus() {
        return Promise.of(AgentHealthStatus.HEALTHY);
    }

    private AgentResponse generateDeterministicResponse(AgentType agentType, String prompt, String model) {
        String output = switch (agentType) {
            case STRATEGY_GENERATOR -> "Strategy: " + generateHash(prompt) + " (deterministic)";
            case AD_COPY_GENERATOR -> "Ad Copy: " + generateHash(prompt) + " (deterministic)";
            case LANDING_PAGE_GENERATOR -> "Landing Page: " + generateHash(prompt) + " (deterministic)";
            case EMAIL_FOLLOW_UP_GENERATOR -> "Email: " + generateHash(prompt) + " (deterministic)";
            case PROPOSAL_SOW_GENERATOR -> "Proposal/SOW: " + generateHash(prompt) + " (deterministic)";
            case REPORT_NARRATIVE_GENERATOR -> "Report: " + generateHash(prompt) + " (deterministic)";
            case RECOMMENDATION_ENGINE -> "Recommendation: " + generateHash(prompt) + " (deterministic)";
        };

        return new AgentResponse(
            output,
            model != null ? model : "deterministic-model",
            0.85,
            "deterministic://" + agentType + "/" + generateHash(prompt),
            Duration.ofMillis(100),
            true,
            null
        );
    }

    private String generateHash(String input) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
