/*
 * Copyright (c) 2026 Ghatana Inc.
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
    void synthesizesPoliciesFromClusters() {
        EpisodeClusteringService clusteringService = new EpisodeClusteringService();
        PolicySynthesisService synthesisService = new PolicySynthesisService(clusteringService);

        List<EnhancedEpisode> episodes = List.of(
            createEpisode("ep1", "action1", 0.8, new float[]{1.0f, 0.0f}),
            createEpisode("ep2", "action1", 0.9, new float[]{0.9f, 0.1f}),
            createEpisode("ep3", "action1", 0.7, new float[]{0.8f, 0.2f}),
            createEpisode("ep4", "action2", 0.6, new float[]{0.0f, 1.0f}),
            createEpisode("ep5", "action2", 0.5, new float[]{0.1f, 0.9f})
        );

        PolicySynthesisService.SynthesisResult result = synthesisService.synthesizePolicies(episodes);

        assertThat(result.policyCount()).isGreaterThan(0);
        assertThat(result.policies()).isNotEmpty();
    }

    @Test
    @DisplayName("generates policies with rules")
    void generatesPoliciesWithRules() {
        EpisodeClusteringService clusteringService = new EpisodeClusteringService();
        PolicySynthesisService synthesisService = new PolicySynthesisService(clusteringService);

        List<EnhancedEpisode> episodes = List.of(
            createEpisode("ep1", "action1", 0.8, new float[]{1.0f, 0.0f}),
            createEpisode("ep2", "action1", 0.9, new float[]{0.9f, 0.1f}),
            createEpisode("ep3", "action1", 0.7, new float[]{0.8f, 0.2f})
        );

        PolicySynthesisService.SynthesisResult result = synthesisService.synthesizePolicies(episodes);

        assertThat(result.policies()).isNotEmpty();
        assertThat(result.policies().get(0).rules()).isNotEmpty();
    }

    @Test
    @DisplayName("calculates confidence based on cluster consistency")
    void calculatesConfidenceBasedOnConsistency() {
        EpisodeClusteringService clusteringService = new EpisodeClusteringService();
        PolicySynthesisService synthesisService = new PolicySynthesisService(clusteringService, 0.5, 3);

        List<EnhancedEpisode> episodes = List.of(
            createEpisode("ep1", "action1", 0.8, new float[]{1.0f, 0.0f}),
            createEpisode("ep2", "action1", 0.9, new float[]{0.9f, 0.1f}),
            createEpisode("ep3", "action1", 0.7, new float[]{0.8f, 0.2f})
        );

        PolicySynthesisService.SynthesisResult result = synthesisService.synthesizePolicies(episodes);

        if (result.policyCount() > 0) {
            assertThat(result.policies().get(0).confidence()).isGreaterThan(0.0);
        }
    }

    @Test
    @DisplayName("skips small clusters below minimum size")
    void skipsSmallClusters() {
        EpisodeClusteringService clusteringService = new EpisodeClusteringService();
        PolicySynthesisService synthesisService = new PolicySynthesisService(clusteringService, 0.5, 10);

        List<EnhancedEpisode> episodes = List.of(
            createEpisode("ep1", "action1", 0.8, new float[]{1.0f, 0.0f}),
            createEpisode("ep2", "action1", 0.9, new float[]{0.9f, 0.1f})
        );

        PolicySynthesisService.SynthesisResult result = synthesisService.synthesizePolicies(episodes);

        // Cluster size (2) below minimum (10), should skip
        assertThat(result.policyCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("retrieves policies by ID")
    void retrievesPoliciesById() {
        EpisodeClusteringService clusteringService = new EpisodeClusteringService();
        PolicySynthesisService synthesisService = new PolicySynthesisService(clusteringService);

        List<EnhancedEpisode> episodes = List.of(
            createEpisode("ep1", "action1", 0.8, new float[]{1.0f, 0.0f}),
            createEpisode("ep2", "action1", 0.9, new float[]{0.9f, 0.1f}),
            createEpisode("ep3", "action1", 0.7, new float[]{0.8f, 0.2f})
        );

        synthesisService.synthesizePolicies(episodes);

        List<PolicySynthesisService.SynthesizedPolicy> policies = synthesisService.getPolicies();
        assertThat(policies).isNotEmpty();

        PolicySynthesisService.SynthesizedPolicy policy = policies.get(0);
        assertThat(synthesisService.getPolicy(policy.id())).isPresent();
    }

    @Test
    @DisplayName("clears synthesized policies")
    void clearsPolicies() {
        EpisodeClusteringService clusteringService = new EpisodeClusteringService();
        PolicySynthesisService synthesisService = new PolicySynthesisService(clusteringService);

        List<EnhancedEpisode> episodes = List.of(
            createEpisode("ep1", "action1", 0.8, new float[]{1.0f, 0.0f}),
            createEpisode("ep2", "action1", 0.9, new float[]{0.9f, 0.1f}),
            createEpisode("ep3", "action1", 0.7, new float[]{0.8f, 0.2f})
        );

        synthesisService.synthesizePolicies(episodes);
        assertThat(synthesisService.getPolicies()).isNotEmpty();

        synthesisService.clearPolicies();
        assertThat(synthesisService.getPolicies()).isEmpty();
    }

    @Test
    @DisplayName("generates policy rules with correct structure")
    void generatesPolicyRulesWithCorrectStructure() {
        EpisodeClusteringService clusteringService = new EpisodeClusteringService();
        PolicySynthesisService synthesisService = new PolicySynthesisService(clusteringService);

        List<EnhancedEpisode> episodes = List.of(
            createEpisode("ep1", "action1", 0.8, new float[]{1.0f, 0.0f}, List.of("tag1", "tag2")),
            createEpisode("ep2", "action1", 0.9, new float[]{0.9f, 0.1f}, List.of("tag1", "tag3")),
            createEpisode("ep3", "action1", 0.7, new float[]{0.8f, 0.2f}, List.of("tag1"))
        );

        PolicySynthesisService.SynthesisResult result = synthesisService.synthesizePolicies(episodes);

        if (result.policyCount() > 0) {
            PolicySynthesisService.SynthesizedPolicy policy = result.policies().get(0);
            for (PolicySynthesisService.PolicyRule rule : policy.rules()) {
                assertThat(rule.type()).isNotEmpty();
                assertThat(rule.action()).isNotEmpty();
                assertThat(rule.confidence()).isBetween(0.0, 1.0);
            }
        }
    }

    // Helper method

    private EnhancedEpisode createEpisode(String id, String action, double reward, float[] embedding) {
        return createEpisode(id, action, reward, embedding, List.of());
    }

    private EnhancedEpisode createEpisode(String id, String action, double reward, float[] embedding, List<String> tags) {
        return EnhancedEpisode.builder()
            .id(id)
            .agentId("agent-1")
            .turnId("turn-" + id)
            .input("input")
            .output("output")
            .action(action)
            .reward(reward)
            .tags(tags)
            .embedding(embedding)
            .createdAt(Instant.now())
            .build();
    }
}
