/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning.synthesis;

import com.ghatana.agent.learning.clustering.EpisodeClusteringService;
import com.ghatana.agent.memory.model.episode.EnhancedEpisode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service for automatically synthesizing policies from clustered episode patterns.
 * Analyzes episode clusters to generate actionable policies for agent behavior.
 *
 * @doc.type class
 * @doc.purpose Automated policy synthesis from clustered episode patterns
 * @doc.layer agent-learning
 * @doc.pattern Service
 */
public final class PolicySynthesisService {

    private static final Logger log = LoggerFactory.getLogger(PolicySynthesisService.class);

    private final EpisodeClusteringService clusteringService;
    private final Map<String, SynthesizedPolicy> synthesizedPolicies = new ConcurrentHashMap<>();
    private final double confidenceThreshold;
    private final int minClusterSize;

    /**
     * Creates a policy synthesis service with default settings.
     *
     * @param clusteringService episode clustering service
     */
    public PolicySynthesisService(EpisodeClusteringService clusteringService) {
        this(clusteringService, 0.7, 5);
    }

    /**
     * Creates a policy synthesis service with custom settings.
     *
     * @param clusteringService episode clustering service
     * @param confidenceThreshold minimum confidence for policy generation
     * @param minClusterSize minimum cluster size for policy synthesis
     */
    public PolicySynthesisService(EpisodeClusteringService clusteringService, double confidenceThreshold, int minClusterSize) {
        this.clusteringService = clusteringService;
        this.confidenceThreshold = confidenceThreshold;
        this.minClusterSize = minClusterSize;
    }

    /**
     * Synthesizes policies from episode clusters.
     *
     * @param episodes episodes to analyze
     * @return synthesis result with generated policies
     */
    public SynthesisResult synthesizePolicies(List<EnhancedEpisode> episodes) {
        log.info("[policy-synthesis] Synthesizing policies from {} episodes", episodes.size());

        // First cluster the episodes
        EpisodeClusteringService.ClusteringResult clusteringResult = clusteringService.clusterEpisodes(episodes);

        if (clusteringResult.clusterCount() == 0) {
            return new SynthesisResult(List.of(), 0, "No clusters found for policy synthesis");
        }

        // Analyze each cluster to generate policies
        List<SynthesizedPolicy> newPolicies = new ArrayList<>();

        for (EpisodeClusteringService.Cluster cluster : clusteringResult.clusters()) {
            if (cluster.episodes().size() < minClusterSize) {
                log.debug("[policy-synthesis] Skipping cluster {} (size {} below minimum)", 
                    cluster.id(), cluster.episodes().size());
                continue;
            }

            SynthesizedPolicy policy = synthesizePolicyFromCluster(cluster);
            if (policy != null && policy.confidence() >= confidenceThreshold) {
                newPolicies.add(policy);
                synthesizedPolicies.put(policy.id(), policy);
                log.info("[policy-synthesis] Generated policy: {} with confidence {}", 
                    policy.name(), policy.confidence());
            }
        }

        log.info("[policy-synthesis] Synthesized {} policies from {} clusters", 
            newPolicies.size(), clusteringResult.clusterCount());

        return new SynthesisResult(
            List.copyOf(newPolicies),
            newPolicies.size(),
            String.format("Synthesized %d policies from %d clusters", newPolicies.size(), clusteringResult.clusterCount())
        );
    }

    /**
     * Synthesizes a policy from a single episode cluster.
     */
    private SynthesizedPolicy synthesizePolicyFromCluster(EpisodeClusteringService.Cluster cluster) {
        List<EnhancedEpisode> episodes = cluster.episodes();

        // Analyze common patterns in the cluster
        Map<String, Double> actionFrequencies = analyzeActionFrequencies(episodes);
        Map<String, Double> tagFrequencies = analyzeTagFrequencies(episodes);
        double avgReward = episodes.stream().mapToDouble(EnhancedEpisode::getReward).average().orElse(0.0);
        double successRate = calculateSuccessRate(episodes);

        // Generate policy name and description
        String policyName = generatePolicyName(episodes, actionFrequencies);
        String policyDescription = generatePolicyDescription(episodes, actionFrequencies, avgReward);

        // Calculate confidence based on cluster size and consistency
        double confidence = calculateConfidence(episodes, actionFrequencies, successRate);

        // Generate policy rules
        List<PolicyRule> rules = generatePolicyRules(episodes, actionFrequencies, tagFrequencies);

        return new SynthesizedPolicy(
            UUID.randomUUID().toString(),
            policyName,
            policyDescription,
            rules,
            confidence,
            avgReward,
            successRate,
            cluster.id(),
            Instant.now()
        );
    }

    /**
     * Analyzes action frequencies in episodes.
     */
    private Map<String, Double> analyzeActionFrequencies(List<EnhancedEpisode> episodes) {
        Map<String, Integer> actionCounts = new HashMap<>();
        
        for (EnhancedEpisode episode : episodes) {
            String action = episode.getAction();
            if (action != null && !action.isEmpty()) {
                actionCounts.merge(action, 1, Integer::sum);
            }
        }

        int total = actionCounts.values().stream().mapToInt(Integer::intValue).sum();
        Map<String, Double> frequencies = new HashMap<>();
        
        for (Map.Entry<String, Integer> entry : actionCounts.entrySet()) {
            frequencies.put(entry.getKey(), (double) entry.getValue() / total);
        }

        return frequencies;
    }

    /**
     * Analyzes tag frequencies in episodes.
     */
    private Map<String, Double> analyzeTagFrequencies(List<EnhancedEpisode> episodes) {
        Map<String, Integer> tagCounts = new HashMap<>();
        
        for (EnhancedEpisode episode : episodes) {
            for (String tag : episode.getTags()) {
                tagCounts.merge(tag, 1, Integer::sum);
            }
        }

        int total = tagCounts.values().stream().mapToInt(Integer::intValue).sum();
        Map<String, Double> frequencies = new HashMap<>();
        
        for (Map.Entry<String, Integer> entry : tagCounts.entrySet()) {
            frequencies.put(entry.getKey(), (double) entry.getValue() / total);
        }

        return frequencies;
    }

    /**
     * Calculates success rate based on rewards.
     */
    private double calculateSuccessRate(List<EnhancedEpisode> episodes) {
        long successful = episodes.stream().filter(e -> e.getReward() > 0).count();
        return (double) successful / episodes.size();
    }

    /**
     * Generates a policy name based on cluster characteristics.
     */
    private String generatePolicyName(List<EnhancedEpisode> episodes, Map<String, Double> actionFrequencies) {
        // Find most common action
        String mostCommonAction = actionFrequencies.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("general");

        return String.format("Auto-Policy-%s-%d", mostCommonAction, episodes.size());
    }

    /**
     * Generates a policy description.
     */
    private String generatePolicyDescription(List<EnhancedEpisode> episodes, 
                                            Map<String, Double> actionFrequencies,
                                            double avgReward) {
        String mostCommonAction = actionFrequencies.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("unknown");

        return String.format("Auto-generated policy for %s episodes. Avg reward: %.2f. Primary action: %s.",
            episodes.size(), avgReward, mostCommonAction);
    }

    /**
     * Calculates policy confidence based on cluster consistency.
     */
    private double calculateConfidence(List<EnhancedEpisode> episodes,
                                      Map<String, Double> actionFrequencies,
                                      double successRate) {
        // Confidence based on:
        // 1. Cluster size (more episodes = higher confidence)
        // 2. Action consistency (higher frequency of dominant action = higher confidence)
        // 3. Success rate (higher success rate = higher confidence)

        double sizeScore = Math.min(1.0, episodes.size() / 20.0);
        
        double dominantActionFreq = actionFrequencies.values().stream()
            .max(Double::compareTo)
            .orElse(0.0);
        
        double consistencyScore = dominantActionFreq;

        // Weighted average
        return (sizeScore * 0.3) + (consistencyScore * 0.4) + (successRate * 0.3);
    }

    /**
     * Generates policy rules from cluster patterns.
     */
    private List<PolicyRule> generatePolicyRules(List<EnhancedEpisode> episodes,
                                                  Map<String, Double> actionFrequencies,
                                                  Map<String, Double> tagFrequencies) {
        List<PolicyRule> rules = new ArrayList<>();

        // Rule for most common action
        actionFrequencies.entrySet().stream()
            .filter(e -> e.getValue() > 0.5)
            .max(Map.Entry.comparingByValue())
            .ifPresent(entry -> {
                rules.add(new PolicyRule(
                    "action-priority",
                    "prefer_action",
                    entry.getKey(),
                    entry.getValue(),
                    String.format("Prefer action %s based on cluster analysis", entry.getKey())
                ));
            });

        // Rule for common tags
        tagFrequencies.entrySet().stream()
            .filter(e -> e.getValue() > 0.3)
            .forEach(entry -> {
                rules.add(new PolicyRule(
                    "tag-association",
                    "require_tag",
                    entry.getKey(),
                    entry.getValue(),
                    String.format("Episodes with tag %s show consistent behavior", entry.getKey())
                ));
            });

        // Rule for reward threshold
        double avgReward = episodes.stream().mapToDouble(EnhancedEpisode::getReward).average().orElse(0.0);
        if (avgReward > 0.5) {
            rules.add(new PolicyRule(
                "reward-threshold",
                "min_reward",
                avgReward * 0.8,
                1.0,
                "Maintain minimum reward threshold based on cluster performance"
            ));
        }

        return rules;
    }

    /**
     * Gets all synthesized policies.
     *
     * @return list of synthesized policies
     */
    public List<SynthesizedPolicy> getPolicies() {
        return List.copyOf(synthesizedPolicies.values());
    }

    /**
     * Gets a policy by ID.
     *
     * @param policyId policy identifier
     * @return the policy, or empty if not found
     */
    public Optional<SynthesizedPolicy> getPolicy(String policyId) {
        return Optional.ofNullable(synthesizedPolicies.get(policyId));
    }

    /**
     * Clears all synthesized policies.
     */
    public void clearPolicies() {
        synthesizedPolicies.clear();
    }

    /**
     * Synthesized policy record.
     *
     * @param id unique policy identifier
     * @param name policy name
     * @param description policy description
     * @param rules list of policy rules
     * @param confidence policy confidence score
     * @param avgReward average reward from source cluster
     * @param successRate success rate from source cluster
     * @param sourceClusterId cluster ID this policy was synthesized from
     * @param createdAt when the policy was created
     */
    public record SynthesizedPolicy(
        String id,
        String name,
        String description,
        List<PolicyRule> rules,
        double confidence,
        double avgReward,
        double successRate,
        String sourceClusterId,
        Instant createdAt
    ) {
        public SynthesizedPolicy {
            rules = List.copyOf(rules);
            confidence = Math.max(0.0, Math.min(1.0, confidence));
        }
    }

    /**
     * Policy rule record.
     *
     * @param type rule type
     * @param action rule action
     * @param value rule value
     * @param confidence rule confidence
     * @param description rule description
     */
    public record PolicyRule(
        String type,
        String action,
        Object value,
        double confidence,
        String description
    ) {
        public PolicyRule {
            confidence = Math.max(0.0, Math.min(1.0, confidence));
        }
    }

    /**
     * Synthesis result.
     *
     * @param policies list of synthesized policies
     * @param policyCount number of policies
     * @param message result message
     */
    public record SynthesisResult(
        List<SynthesizedPolicy> policies,
        int policyCount,
        String message
    ) {}
}
