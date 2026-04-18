/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning.clustering;

import com.ghatana.agent.memory.model.episode.EnhancedEpisode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for episode clustering service.
 *
 * @doc.type class
 * @doc.purpose Unit tests for episode clustering
 * @doc.layer test
 */
@DisplayName("Episode Clustering Service Tests")
class EpisodeClusteringServiceTest {

    @Test
    @DisplayName("clusters episodes with embeddings")
    void clustersEpisodesWithEmbeddings() {
        EpisodeClusteringService service = new EpisodeClusteringService();

        List<EnhancedEpisode> episodes = List.of(
            createEpisode("ep1", new float[]{1.0f, 0.0f, 0.0f}),
            createEpisode("ep2", new float[]{0.9f, 0.1f, 0.0f}),
            createEpisode("ep3", new float[]{0.0f, 1.0f, 0.0f}),
            createEpisode("ep4", new float[]{0.1f, 0.9f, 0.0f})
        );

        EpisodeClusteringService.ClusteringResult result = service.clusterEpisodes(episodes);

        assertThat(result.clusterCount()).isGreaterThan(0);
        assertThat(result.totalEpisodes()).isGreaterThan(0);
        assertThat(result.clusters()).isNotEmpty();
    }

    @Test
    @DisplayName("handles episodes without embeddings")
    void handlesEpisodesWithoutEmbeddings() {
        EpisodeClusteringService service = new EpisodeClusteringService();

        List<EnhancedEpisode> episodes = List.of(
            createEpisode("ep1", null),
            createEpisode("ep2", null)
        );

        EpisodeClusteringService.ClusteringResult result = service.clusterEpisodes(episodes);

        assertThat(result.clusterCount()).isEqualTo(0);
        assertThat(result.totalEpisodes()).isEqualTo(0);
    }

    @Test
    @DisplayName("uses hierarchical clustering algorithm")
    void usesHierarchicalClustering() {
        EpisodeClusteringService service = new EpisodeClusteringService(
            100, 0.5, EpisodeClusteringService.ClusteringAlgorithm.HIERARCHICAL);

        List<EnhancedEpisode> episodes = List.of(
            createEpisode("ep1", new float[]{1.0f, 0.0f}),
            createEpisode("ep2", new float[]{0.9f, 0.1f}),
            createEpisode("ep3", new float[]{0.0f, 1.0f})
        );

        EpisodeClusteringService.ClusteringResult result = service.clusterEpisodes(episodes);

        assertThat(result.clusterCount()).isGreaterThan(0);
    }

    @Test
    @DisplayName("uses k-means clustering algorithm")
    void usesKMeansClustering() {
        EpisodeClusteringService service = new EpisodeClusteringService(
            100, 0.5, EpisodeClusteringService.ClusteringAlgorithm.K_MEANS);

        List<EnhancedEpisode> episodes = List.of(
            createEpisode("ep1", new float[]{1.0f, 0.0f}),
            createEpisode("ep2", new float[]{0.9f, 0.1f}),
            createEpisode("ep3", new float[]{0.0f, 1.0f}),
            createEpisode("ep4", new float[]{0.1f, 0.9f})
        );

        EpisodeClusteringService.ClusteringResult result = service.clusterEpisodes(episodes);

        assertThat(result.clusterCount()).isGreaterThan(0);
    }

    @Test
    @DisplayName("retrieves clusters by ID")
    void retrievesClustersById() {
        EpisodeClusteringService service = new EpisodeClusteringService();

        List<EnhancedEpisode> episodes = List.of(
            createEpisode("ep1", new float[]{1.0f, 0.0f})
        );

        service.clusterEpisodes(episodes);

        List<EpisodeClusteringService.Cluster> clusters = service.getClusters();
        assertThat(clusters).hasSize(1);

        EpisodeClusteringService.Cluster cluster = clusters.get(0);
        assertThat(service.getCluster(cluster.id())).isPresent();
    }

    @Test
    @DisplayName("clears clusters")
    void clearsClusters() {
        EpisodeClusteringService service = new EpisodeClusteringService();

        List<EnhancedEpisode> episodes = List.of(
            createEpisode("ep1", new float[]{1.0f, 0.0f})
        );

        service.clusterEpisodes(episodes);
        assertThat(service.getClusters()).hasSize(1);

        service.clearClusters();
        assertThat(service.getClusters()).isEmpty();
    }

    @Test
    @DisplayName("prunes clusters when over limit")
    void prunesClustersWhenOverLimit() {
        EpisodeClusteringService service = new EpisodeClusteringService(2, 0.5, EpisodeClusteringService.ClusteringAlgorithm.HIERARCHICAL);

        List<EnhancedEpisode> episodes = List.of(
            createEpisode("ep1", new float[]{1.0f, 0.0f}),
            createEpisode("ep2", new float[]{0.9f, 0.1f}),
            createEpisode("ep3", new float[]{0.0f, 1.0f}),
            createEpisode("ep4", new float[]{0.1f, 0.9f}),
            createEpisode("ep5", new float[]{0.0f, 0.0f})
        );

        service.clusterEpisodes(episodes);

        // Should prune to max 2 clusters
        assertThat(service.getClusters().size()).isLessThanOrEqualTo(2);
    }

    // Helper method

    private EnhancedEpisode createEpisode(String id, float[] embedding) {
        return EnhancedEpisode.builder()
            .id(id)
            .agentId("agent-1")
            .turnId("turn-" + id)
            .input("input")
            .output("output")
            .embedding(embedding)
            .createdAt(Instant.now())
            .build();
    }
}
