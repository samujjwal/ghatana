/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.agent.learning.synthesis;

import com.ghatana.agent.learning.clustering.EpisodeClusteringService;
import com.ghatana.agent.memory.model.episode.EnhancedEpisode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for policy synthesis service.
 *
 * @doc.type class
 * @doc.purpose Unit tests for automated policy synthesis
 * @doc.layer test
 */
@DisplayName("Policy Synthesis Service Tests")
class PolicySynthesisServiceTest {

    @Test
    @DisplayName("synthesizes policies from episode clusters")
    void synthesizesPoliciesFromClusters() { // GH-90000
        EpisodeClusteringService clusteringService = new EpisodeClusteringService(); // GH-90000
        PolicySynthesisService synthesisService = new PolicySynthesisService(clusteringService); // GH-90000

        List<EnhancedEpisode> episodes = List.of( // GH-90000
            createEpisode("ep1", "action1", 0.8, new float[]{1.0f, 0.0f}), // GH-90000
            createEpisode("ep2", "action1", 0.9, new float[]{0.9f, 0.1f}), // GH-90000
            createEpisode("ep3", "action1", 0.7, new float[]{0.8f, 0.2f}), // GH-90000
            createEpisode("ep4", "action2", 0.6, new float[]{0.0f, 1.0f}), // GH-90000
            createEpisode("ep5", "action2", 0.5, new float[]{0.1f, 0.9f}) // GH-90000
        );

        PolicySynthesisService.SynthesisResult result = synthesisService.synthesizePolicies(episodes); // GH-90000

        assertThat(result.policyCount()).isGreaterThan(0); // GH-90000
        assertThat(result.policies()).isNotEmpty(); // GH-90000
    }

    @Test
    @DisplayName("generates policies with rules")
    void generatesPoliciesWithRules() { // GH-90000
        EpisodeClusteringService clusteringService = new EpisodeClusteringService(); // GH-90000
        PolicySynthesisService synthesisService = new PolicySynthesisService(clusteringService); // GH-90000

        List<EnhancedEpisode> episodes = List.of( // GH-90000
            createEpisode("ep1", "action1", 0.8, new float[]{1.0f, 0.0f}), // GH-90000
            createEpisode("ep2", "action1", 0.9, new float[]{0.9f, 0.1f}), // GH-90000
            createEpisode("ep3", "action1", 0.7, new float[]{0.8f, 0.2f}) // GH-90000
        );

        PolicySynthesisService.SynthesisResult result = synthesisService.synthesizePolicies(episodes); // GH-90000

        assertThat(result.policies()).isNotEmpty(); // GH-90000
        assertThat(result.policies().get(0).rules()).isNotEmpty(); // GH-90000
    }

    @Test
    @DisplayName("calculates confidence based on cluster consistency")
    void calculatesConfidenceBasedOnConsistency() { // GH-90000
        EpisodeClusteringService clusteringService = new EpisodeClusteringService(); // GH-90000
        PolicySynthesisService synthesisService = new PolicySynthesisService(clusteringService, 0.5, 3); // GH-90000

        List<EnhancedEpisode> episodes = List.of( // GH-90000
            createEpisode("ep1", "action1", 0.8, new float[]{1.0f, 0.0f}), // GH-90000
            createEpisode("ep2", "action1", 0.9, new float[]{0.9f, 0.1f}), // GH-90000
            createEpisode("ep3", "action1", 0.7, new float[]{0.8f, 0.2f}) // GH-90000
        );

        PolicySynthesisService.SynthesisResult result = synthesisService.synthesizePolicies(episodes); // GH-90000

        if (result.policyCount() > 0) { // GH-90000
            assertThat(result.policies().get(0).confidence()).isGreaterThan(0.0); // GH-90000
        }
    }

    @Test
    @DisplayName("skips small clusters below minimum size")
    void skipsSmallClusters() { // GH-90000
        EpisodeClusteringService clusteringService = new EpisodeClusteringService(); // GH-90000
        PolicySynthesisService synthesisService = new PolicySynthesisService(clusteringService, 0.5, 10); // GH-90000

        List<EnhancedEpisode> episodes = List.of( // GH-90000
            createEpisode("ep1", "action1", 0.8, new float[]{1.0f, 0.0f}), // GH-90000
            createEpisode("ep2", "action1", 0.9, new float[]{0.9f, 0.1f}) // GH-90000
        );

        PolicySynthesisService.SynthesisResult result = synthesisService.synthesizePolicies(episodes); // GH-90000

        // Cluster size (2) below minimum (10), should skip // GH-90000
        assertThat(result.policyCount()).isEqualTo(0); // GH-90000
    }

    @Test
    @DisplayName("retrieves policies by ID")
    void retrievesPoliciesById() { // GH-90000
        EpisodeClusteringService clusteringService = new EpisodeClusteringService(); // GH-90000
        PolicySynthesisService synthesisService = new PolicySynthesisService(clusteringService); // GH-90000

        List<EnhancedEpisode> episodes = List.of( // GH-90000
            createEpisode("ep1", "action1", 0.8, new float[]{1.0f, 0.0f}), // GH-90000
            createEpisode("ep2", "action1", 0.9, new float[]{0.9f, 0.1f}), // GH-90000
            createEpisode("ep3", "action1", 0.7, new float[]{0.8f, 0.2f}) // GH-90000
        );

        synthesisService.synthesizePolicies(episodes); // GH-90000

        List<PolicySynthesisService.SynthesizedPolicy> policies = synthesisService.getPolicies(); // GH-90000
        assertThat(policies).isNotEmpty(); // GH-90000

        PolicySynthesisService.SynthesizedPolicy policy = policies.get(0); // GH-90000
        assertThat(synthesisService.getPolicy(policy.id())).isPresent(); // GH-90000
    }

    @Test
    @DisplayName("clears synthesized policies")
    void clearsPolicies() { // GH-90000
        EpisodeClusteringService clusteringService = new EpisodeClusteringService(); // GH-90000
        PolicySynthesisService synthesisService = new PolicySynthesisService(clusteringService); // GH-90000

        List<EnhancedEpisode> episodes = List.of( // GH-90000
            createEpisode("ep1", "action1", 0.8, new float[]{1.0f, 0.0f}), // GH-90000
            createEpisode("ep2", "action1", 0.9, new float[]{0.9f, 0.1f}), // GH-90000
            createEpisode("ep3", "action1", 0.7, new float[]{0.8f, 0.2f}) // GH-90000
        );

        synthesisService.synthesizePolicies(episodes); // GH-90000
        assertThat(synthesisService.getPolicies()).isNotEmpty(); // GH-90000

        synthesisService.clearPolicies(); // GH-90000
        assertThat(synthesisService.getPolicies()).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("generates policy rules with correct structure")
    void generatesPolicyRulesWithCorrectStructure() { // GH-90000
        EpisodeClusteringService clusteringService = new EpisodeClusteringService(); // GH-90000
        PolicySynthesisService synthesisService = new PolicySynthesisService(clusteringService); // GH-90000

        List<EnhancedEpisode> episodes = List.of( // GH-90000
            createEpisode("ep1", "action1", 0.8, new float[]{1.0f, 0.0f}, List.of("tag1", "tag2")), // GH-90000
            createEpisode("ep2", "action1", 0.9, new float[]{0.9f, 0.1f}, List.of("tag1", "tag3")), // GH-90000
            createEpisode("ep3", "action1", 0.7, new float[]{0.8f, 0.2f}, List.of("tag1"))
        );

        PolicySynthesisService.SynthesisResult result = synthesisService.synthesizePolicies(episodes); // GH-90000

        if (result.policyCount() > 0) { // GH-90000
            PolicySynthesisService.SynthesizedPolicy policy = result.policies().get(0); // GH-90000
            for (PolicySynthesisService.PolicyRule rule : policy.rules()) { // GH-90000
                assertThat(rule.type()).isNotEmpty(); // GH-90000
                assertThat(rule.action()).isNotEmpty(); // GH-90000
                assertThat(rule.confidence()).isBetween(0.0, 1.0); // GH-90000
            }
        }
    }

    // Helper method

    private EnhancedEpisode createEpisode(String id, String action, double reward, float[] embedding) { // GH-90000
        return createEpisode(id, action, reward, embedding, List.of()); // GH-90000
    }

    private EnhancedEpisode createEpisode(String id, String action, double reward, float[] embedding, List<String> tags) { // GH-90000
        return EnhancedEpisode.builder() // GH-90000
            .id(id) // GH-90000
            .agentId("agent-1")
            .turnId("turn-" + id) // GH-90000
            .input("input")
            .output("output")
            .action(action) // GH-90000
            .reward(reward) // GH-90000
            .tags(tags) // GH-90000
            .embedding(embedding) // GH-90000
            .createdAt(Instant.now()) // GH-90000
            .build(); // GH-90000
    }
}
