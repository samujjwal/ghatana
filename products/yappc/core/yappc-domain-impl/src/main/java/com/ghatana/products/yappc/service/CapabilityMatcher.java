package com.ghatana.products.yappc.service;

import com.ghatana.products.yappc.domain.agent.AgentMetadata;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Matches tasks to agents based on capabilities.
 *
 * @doc.type class
 * @doc.purpose Capability-based agent selection
 * @doc.layer product
 * @doc.pattern Matcher, Strategy
 */
public class CapabilityMatcher {

    private static final Logger LOG = LoggerFactory.getLogger(CapabilityMatcher.class);

    /**
     * Finds agents that have ALL required capabilities.
     *
     * @param requiredCapabilities List of required capabilities
     * @param availableAgents     List of available agents
     * @return List of capable agents, sorted by suitability
     */
    @NotNull
    public List<AgentMetadata> findCapableAgents(
            @NotNull List<String> requiredCapabilities,
            @NotNull List<AgentMetadata> availableAgents
    ) {
        LOG.debug("Finding agents with capabilities: {}", requiredCapabilities);

        List<AgentMetadata> capableAgents = availableAgents.stream()
                .filter(agent -> hasAllCapabilities(agent, requiredCapabilities))
                .sorted(Comparator.comparingDouble(
                        (AgentMetadata agent) -> calculateCapabilityScore(agent, requiredCapabilities)
                ).reversed())
                .toList();

        LOG.debug("Found {} capable agents", capableAgents.size());
        return capableAgents;
    }

    /**
     * Checks if an agent has all required capabilities.
     */
    private boolean hasAllCapabilities(
            @NotNull AgentMetadata agent,
            @NotNull List<String> requiredCapabilities
    ) {
        Set<String> agentCapabilities = new HashSet<>(agent.capabilities());
        return agentCapabilities.containsAll(requiredCapabilities);
    }

    /**
     * Calculates capability score for an agent.
     * Higher score = better match.
     */
    private double calculateCapabilityScore(
            @NotNull AgentMetadata agent,
            @NotNull List<String> requiredCapabilities
    ) {
        Set<String> agentCaps = new HashSet<>(agent.capabilities());

        // Count matching capabilities
        long matchCount = requiredCapabilities.stream()
                .filter(agentCaps::contains)
                .count();

        // Base score from match ratio
        double baseScore = (double) matchCount / requiredCapabilities.size();

        // Bonus for specialization (fewer extra capabilities = more specialized)
        int extraCaps = agentCaps.size() - requiredCapabilities.size();
        double specializationBonus = Math.max(0, 1.0 - (extraCaps * 0.1));

        // Bonus for latency SLA (lower is better)
        double latencyBonus = 1.0 / (1.0 + agent.latencySLA() / 1000.0);

        // Penalty for cost (if available)
        double costPenalty = agent.costPerRequest() != null
                ? 1.0 / (1.0 + agent.costPerRequest())
                : 1.0;

        // Combined score
        double finalScore = (baseScore * 0.5) +
                (specializationBonus * 0.2) +
                (latencyBonus * 0.2) +
                (costPenalty * 0.1);

        LOG.trace("Agent {} score: {} (base: {}, spec: {}, latency: {}, cost: {})",
                agent.name(), finalScore, baseScore, specializationBonus, latencyBonus, costPenalty);

        return finalScore;
    }
}
