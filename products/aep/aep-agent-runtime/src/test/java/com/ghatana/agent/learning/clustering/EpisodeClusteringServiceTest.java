/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
@DisplayName("Episode Clustering Service Tests [GH-90000]")
class EpisodeClusteringServiceTest {

    @Test
    @DisplayName("clusters episodes with embeddings [GH-90000]")
    void clustersEpisodesWithEmbeddings() { // GH-90000
        EpisodeClusteringService service = new EpisodeClusteringService(); // GH-90000

        List<EnhancedEpisode> episodes = List.of( // GH-90000
            createEpisode("ep1", new float[]{1.0f, 0.0f, 0.0f}), // GH-90000
            createEpisode("ep2", new float[]{0.9f, 0.1f, 0.0f}), // GH-90000
            createEpisode("ep3", new float[]{0.0f, 1.0f, 0.0f}), // GH-90000
            createEpisode("ep4", new float[]{0.1f, 0.9f, 0.0f}) // GH-90000
        );

        EpisodeClusteringService.ClusteringResult result = service.clusterEpisodes(episodes); // GH-90000

        assertThat(result.clusterCount()).isGreaterThan(0); // GH-90000
        assertThat(result.totalEpisodes()).isGreaterThan(0); // GH-90000
        assertThat(result.clusters()).isNotEmpty(); // GH-90000
    }

    @Test
    @DisplayName("handles episodes without embeddings [GH-90000]")
    void handlesEpisodesWithoutEmbeddings() { // GH-90000
        EpisodeClusteringService service = new EpisodeClusteringService(); // GH-90000

        List<EnhancedEpisode> episodes = List.of( // GH-90000
            createEpisode("ep1", null), // GH-90000
            createEpisode("ep2", null) // GH-90000
        );

        EpisodeClusteringService.ClusteringResult result = service.clusterEpisodes(episodes); // GH-90000

        assertThat(result.clusterCount()).isEqualTo(0); // GH-90000
        assertThat(result.totalEpisodes()).isEqualTo(0); // GH-90000
    }

    @Test
    @DisplayName("uses hierarchical clustering algorithm [GH-90000]")
    void usesHierarchicalClustering() { // GH-90000
        EpisodeClusteringService service = new EpisodeClusteringService( // GH-90000
            100, 0.5, EpisodeClusteringService.ClusteringAlgorithm.HIERARCHICAL);

        List<EnhancedEpisode> episodes = List.of( // GH-90000
            createEpisode("ep1", new float[]{1.0f, 0.0f}), // GH-90000
            createEpisode("ep2", new float[]{0.9f, 0.1f}), // GH-90000
            createEpisode("ep3", new float[]{0.0f, 1.0f}) // GH-90000
        );

        EpisodeClusteringService.ClusteringResult result = service.clusterEpisodes(episodes); // GH-90000

        assertThat(result.clusterCount()).isGreaterThan(0); // GH-90000
    }

    @Test
    @DisplayName("uses k-means clustering algorithm [GH-90000]")
    void usesKMeansClustering() { // GH-90000
        EpisodeClusteringService service = new EpisodeClusteringService( // GH-90000
            100, 0.5, EpisodeClusteringService.ClusteringAlgorithm.K_MEANS);

        List<EnhancedEpisode> episodes = List.of( // GH-90000
            createEpisode("ep1", new float[]{1.0f, 0.0f}), // GH-90000
            createEpisode("ep2", new float[]{0.9f, 0.1f}), // GH-90000
            createEpisode("ep3", new float[]{0.0f, 1.0f}), // GH-90000
            createEpisode("ep4", new float[]{0.1f, 0.9f}) // GH-90000
        );

        EpisodeClusteringService.ClusteringResult result = service.clusterEpisodes(episodes); // GH-90000

        assertThat(result.clusterCount()).isGreaterThan(0); // GH-90000
    }

    @Test
    @DisplayName("retrieves clusters by ID [GH-90000]")
    void retrievesClustersById() { // GH-90000
        EpisodeClusteringService service = new EpisodeClusteringService(); // GH-90000

        List<EnhancedEpisode> episodes = List.of( // GH-90000
            createEpisode("ep1", new float[]{1.0f, 0.0f}) // GH-90000
        );

        service.clusterEpisodes(episodes); // GH-90000

        List<EpisodeClusteringService.Cluster> clusters = service.getClusters(); // GH-90000
        assertThat(clusters).hasSize(1); // GH-90000

        EpisodeClusteringService.Cluster cluster = clusters.get(0); // GH-90000
        assertThat(service.getCluster(cluster.id())).isPresent(); // GH-90000
    }

    @Test
    @DisplayName("clears clusters [GH-90000]")
    void clearsClusters() { // GH-90000
        EpisodeClusteringService service = new EpisodeClusteringService(); // GH-90000

        List<EnhancedEpisode> episodes = List.of( // GH-90000
            createEpisode("ep1", new float[]{1.0f, 0.0f}) // GH-90000
        );

        service.clusterEpisodes(episodes); // GH-90000
        assertThat(service.getClusters()).hasSize(1); // GH-90000

        service.clearClusters(); // GH-90000
        assertThat(service.getClusters()).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("prunes clusters when over limit [GH-90000]")
    void prunesClustersWhenOverLimit() { // GH-90000
        EpisodeClusteringService service = new EpisodeClusteringService(2, 0.5, EpisodeClusteringService.ClusteringAlgorithm.HIERARCHICAL); // GH-90000

        List<EnhancedEpisode> episodes = List.of( // GH-90000
            createEpisode("ep1", new float[]{1.0f, 0.0f}), // GH-90000
            createEpisode("ep2", new float[]{0.9f, 0.1f}), // GH-90000
            createEpisode("ep3", new float[]{0.0f, 1.0f}), // GH-90000
            createEpisode("ep4", new float[]{0.1f, 0.9f}), // GH-90000
            createEpisode("ep5", new float[]{0.0f, 0.0f}) // GH-90000
        );

        service.clusterEpisodes(episodes); // GH-90000

        // Should prune to max 2 clusters
        assertThat(service.getClusters().size()).isLessThanOrEqualTo(2); // GH-90000
    }

    // Helper method

    private EnhancedEpisode createEpisode(String id, float[] embedding) { // GH-90000
        return EnhancedEpisode.builder() // GH-90000
            .id(id) // GH-90000
            .agentId("agent-1 [GH-90000]")
            .turnId("turn-" + id) // GH-90000
            .input("input [GH-90000]")
            .output("output [GH-90000]")
            .embedding(embedding) // GH-90000
            .createdAt(Instant.now()) // GH-90000
            .build(); // GH-90000
    }
}
