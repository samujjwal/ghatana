/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning.clustering;

import com.ghatana.agent.memory.model.episode.EnhancedEpisode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service for clustering episodes to identify patterns for learning.
 * Groups similar episodes based on embeddings and metadata.
 *
 * @doc.type class
 * @doc.purpose Episode clustering for pattern extraction and learning
 * @doc.layer agent-learning
 * @doc.pattern Service
 */
public final class EpisodeClusteringService {

    private static final Logger log = LoggerFactory.getLogger(EpisodeClusteringService.class);

    private final Map<String, Cluster> clusters = new ConcurrentHashMap<>();
    private final int maxClusters;
    private final double similarityThreshold;
    private final ClusteringAlgorithm algorithm;

    /**
     * Creates an episode clustering service with default settings.
     */
    public EpisodeClusteringService() {
        this(100, 0.7, ClusteringAlgorithm.HIERARCHICAL);
    }

    /**
     * Creates an episode clustering service with custom settings.
     *
     * @param maxClusters maximum number of clusters to maintain
     * @param similarityThreshold minimum similarity for cluster assignment
     * @param algorithm clustering algorithm to use
     */
    public EpisodeClusteringService(int maxClusters, double similarityThreshold, ClusteringAlgorithm algorithm) {
        this.maxClusters = maxClusters;
        this.similarityThreshold = similarityThreshold;
        this.algorithm = algorithm;
    }

    /**
     * Clusters a collection of episodes.
     *
     * @param episodes episodes to cluster
     * @return clustering result with clusters and metrics
     */
    public ClusteringResult clusterEpisodes(List<EnhancedEpisode> episodes) {
        log.info("[episode-clustering] Clustering {} episodes using {} algorithm", episodes.size(), algorithm);

        // Filter episodes with embeddings
        List<EnhancedEpisode> episodesWithEmbeddings = episodes.stream()
            .filter(e -> e.getEmbedding() != null && e.getEmbedding().length > 0)
            .collect(Collectors.toList());

        log.debug("[episode-clustering] {} episodes have embeddings", episodesWithEmbeddings.size());

        if (episodesWithEmbeddings.isEmpty()) {
            return new ClusteringResult(List.of(), 0, 0, "No episodes with embeddings");
        }

        // Perform clustering based on algorithm
        List<Cluster> newClusters = switch (algorithm) {
            case HIERARCHICAL -> hierarchicalClustering(episodesWithEmbeddings);
            case K_MEANS -> kMeansClustering(
                episodesWithEmbeddings,
                Math.max(1, Math.min(10, episodesWithEmbeddings.size() / 10)));
            case DBSCAN -> dbscanClustering(episodesWithEmbeddings);
        };

        // Update cluster store
        clusters.clear();
        newClusters.forEach(c -> clusters.put(c.id(), c));

        // Prune if over limit
        if (clusters.size() > maxClusters) {
            List<Cluster> sorted = clusters.values().stream()
                .sorted((a, b) -> Integer.compare(b.episodes().size(), a.episodes().size()))
                .toList();
            
            clusters.clear();
            sorted.stream().limit(maxClusters).forEach(c -> clusters.put(c.id(), c));
        }

        int totalEpisodesClustered = newClusters.stream().mapToInt(c -> c.episodes().size()).sum();
        double avgClusterSize = newClusters.isEmpty() ? 0 : (double) totalEpisodesClustered / newClusters.size();

        log.info("[episode-clustering] Created {} clusters, avg size: {}", newClusters.size(), avgClusterSize);

        return new ClusteringResult(
            List.copyOf(newClusters),
            newClusters.size(),
            totalEpisodesClustered,
            String.format("Clustering complete: %d clusters, avg size %.2f", newClusters.size(), avgClusterSize)
        );
    }

    /**
     * Hierarchical clustering algorithm.
     */
    private List<Cluster> hierarchicalClustering(List<EnhancedEpisode> episodes) {
        List<Cluster> currentClusters = episodes.stream()
            .map(this::createInitialCluster)
            .collect(Collectors.toList());

        // Iteratively merge closest clusters
        while (currentClusters.size() > 1) {
            double maxSimilarity = 0;
            Cluster mergeA = null;
            Cluster mergeB = null;

            for (int i = 0; i < currentClusters.size(); i++) {
                for (int j = i + 1; j < currentClusters.size(); j++) {
                    double sim = clusterSimilarity(currentClusters.get(i), currentClusters.get(j));
                    if (sim > maxSimilarity) {
                        maxSimilarity = sim;
                        mergeA = currentClusters.get(i);
                        mergeB = currentClusters.get(j);
                    }
                }
            }

            // Stop if similarity below threshold
            if (maxSimilarity < similarityThreshold) {
                break;
            }

            // Merge clusters
            if (mergeA != null && mergeB != null) {
                Cluster merged = mergeClusters(mergeA, mergeB);
                currentClusters.remove(mergeA);
                currentClusters.remove(mergeB);
                currentClusters.add(merged);
            }
        }

        return currentClusters;
    }

    /**
     * K-means clustering algorithm.
     */
    private List<Cluster> kMeansClustering(List<EnhancedEpisode> episodes, int k) {
        if (episodes.size() < k) {
            return episodes.stream().map(this::createInitialCluster).collect(Collectors.toList());
        }

        // Initialize centroids randomly
        List<float[]> centroids = new ArrayList<>();
        Random random = new Random();
        for (int i = 0; i < k; i++) {
            centroids.add(episodes.get(random.nextInt(episodes.size())).getEmbedding());
        }

        // Assign episodes to nearest centroid
        Map<Integer, List<EnhancedEpisode>> assignments = new HashMap<>();
        
        // Iterate k-means
        for (int iteration = 0; iteration < 10; iteration++) {
            assignments.clear();
            for (int i = 0; i < k; i++) {
                assignments.put(i, new ArrayList<>());
            }

            for (EnhancedEpisode episode : episodes) {
                int nearest = findNearestCentroid(episode.getEmbedding(), centroids);
                assignments.get(nearest).add(episode);
            }

            // Recompute centroids
            for (int i = 0; i < k; i++) {
                List<EnhancedEpisode> clusterEpisodes = assignments.get(i);
                if (!clusterEpisodes.isEmpty()) {
                    centroids.set(i, computeCentroid(clusterEpisodes));
                }
            }
        }

        // Build final clusters
        return java.util.stream.IntStream.range(0, centroids.size())
            .mapToObj(index -> {
                return new Cluster(
                    UUID.randomUUID().toString(),
                    "kmeans-cluster-" + index,
                    assignments.getOrDefault(index, List.of()),
                    centroids.get(index),
                    Instant.now()
                );
            })
            .filter(cluster -> !cluster.episodes().isEmpty())
            .collect(Collectors.toList());
    }

    /**
     * DBSCAN clustering algorithm.
     */
    private List<Cluster> dbscanClustering(List<EnhancedEpisode> episodes) {
        List<Cluster> clusters = new ArrayList<>();
        Set<EnhancedEpisode> visited = new HashSet<>();

        for (EnhancedEpisode episode : episodes) {
            if (visited.contains(episode)) {
                continue;
            }

            List<EnhancedEpisode> neighbors = findNeighbors(episode, episodes, similarityThreshold);
            
            if (neighbors.size() < 3) { // minPts = 3
                visited.add(episode);
            } else {
                Cluster cluster = new Cluster(
                    UUID.randomUUID().toString(),
                    "dbscan-cluster-" + clusters.size(),
                    new ArrayList<>(),
                    computeCentroid(neighbors),
                    Instant.now()
                );

                expandCluster(episode, neighbors, cluster, episodes, visited);
                clusters.add(cluster);
            }
        }

        return clusters;
    }

    private void expandCluster(EnhancedEpisode episode, List<EnhancedEpisode> neighbors,
                              Cluster cluster, List<EnhancedEpisode> allEpisodes, Set<EnhancedEpisode> visited) {
        cluster.episodes().add(episode);
        visited.add(episode);

        Queue<EnhancedEpisode> queue = new LinkedList<>(neighbors);
        
        while (!queue.isEmpty()) {
            EnhancedEpisode current = queue.poll();
            
            if (!visited.contains(current)) {
                visited.add(current);
                cluster.episodes().add(current);
                
                List<EnhancedEpisode> currentNeighbors = findNeighbors(current, allEpisodes, similarityThreshold);
                if (currentNeighbors.size() >= 3) {
                    queue.addAll(currentNeighbors);
                }
            }
        }
    }

    private List<EnhancedEpisode> findNeighbors(EnhancedEpisode episode, List<EnhancedEpisode> allEpisodes, double threshold) {
        return allEpisodes.stream()
            .filter(e -> !e.equals(episode))
            .filter(e -> cosineSimilarity(episode.getEmbedding(), e.getEmbedding()) >= threshold)
            .collect(Collectors.toList());
    }

    /**
     * Creates an initial cluster with a single episode.
     */
    private Cluster createInitialCluster(EnhancedEpisode episode) {
        return new Cluster(
            UUID.randomUUID().toString(),
            "cluster-" + episode.getTurnId(),
            List.of(episode),
            episode.getEmbedding(),
            Instant.now()
        );
    }

    /**
     * Merges two clusters.
     */
    private Cluster mergeClusters(Cluster a, Cluster b) {
        List<EnhancedEpisode> mergedEpisodes = new ArrayList<>();
        mergedEpisodes.addAll(a.episodes());
        mergedEpisodes.addAll(b.episodes());

        float[] mergedCentroid = computeCentroid(mergedEpisodes);

        return new Cluster(
            UUID.randomUUID().toString(),
            "merged-" + a.id() + "-" + b.id(),
            mergedEpisodes,
            mergedCentroid,
            Instant.now()
        );
    }

    /**
     * Computes similarity between two clusters using centroid similarity.
     */
    private double clusterSimilarity(Cluster a, Cluster b) {
        if (a.centroid() == null || b.centroid() == null) {
            return 0.0;
        }
        return cosineSimilarity(a.centroid(), b.centroid());
    }

    /**
     * Computes centroid of a list of episodes.
     */
    private float[] computeCentroid(List<EnhancedEpisode> episodes) {
        if (episodes.isEmpty()) {
            return new float[0];
        }

        int dim = episodes.get(0).getEmbedding().length;
        float[] centroid = new float[dim];

        for (EnhancedEpisode episode : episodes) {
            float[] embedding = episode.getEmbedding();
            for (int i = 0; i < dim; i++) {
                centroid[i] += embedding[i];
            }
        }

        for (int i = 0; i < dim; i++) {
            centroid[i] /= episodes.size();
        }

        return centroid;
    }

    /**
     * Finds nearest centroid for an embedding.
     */
    private int findNearestCentroid(float[] embedding, List<float[]> centroids) {
        int nearest = 0;
        double maxSim = -1;

        for (int i = 0; i < centroids.size(); i++) {
            double sim = cosineSimilarity(embedding, centroids.get(i));
            if (sim > maxSim) {
                maxSim = sim;
                nearest = i;
            }
        }

        return nearest;
    }

    /**
     * Computes cosine similarity between two embeddings.
     */
    private double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        if (normA == 0 || normB == 0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * Gets all current clusters.
     *
     * @return list of clusters
     */
    public List<Cluster> getClusters() {
        return List.copyOf(clusters.values());
    }

    /**
     * Gets a cluster by ID.
     *
     * @param clusterId cluster identifier
     * @return the cluster, or empty if not found
     */
    public Optional<Cluster> getCluster(String clusterId) {
        return Optional.ofNullable(clusters.get(clusterId));
    }

    /**
     * Clears all clusters.
     */
    public void clearClusters() {
        clusters.clear();
    }

    /**
     * Cluster record.
     *
     * @param id unique cluster identifier
     * @param name cluster name
     * @param episodes episodes in the cluster
     * @param centroid cluster centroid embedding
     * @param createdAt when the cluster was created
     */
    public record Cluster(
        String id,
        String name,
        List<EnhancedEpisode> episodes,
        float[] centroid,
        Instant createdAt
    ) {
        public Cluster {
            episodes = List.copyOf(episodes);
        }
    }

    /**
     * Clustering result.
     *
     * @param clusters list of clusters
     * @param clusterCount number of clusters
     * @param totalEpisodes total episodes clustered
     * @param message result message
     */
    public record ClusteringResult(
        List<Cluster> clusters,
        int clusterCount,
        int totalEpisodes,
        String message
    ) {}

    /**
     * Clustering algorithm enumeration.
     */
    public enum ClusteringAlgorithm {
        HIERARCHICAL,
        K_MEANS,
        DBSCAN
    }
}
