/*
 * Copyright (c) 2025 Ghatana.
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ghatana.datacloud.plugins.enterprise.lineage;

import io.activej.promise.Promise;
import lombok.Builder;
import lombok.Getter;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.traverse.BreadthFirstIterator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

/**
 * Data lineage tracking system for EventCloud.
 *
 * <p>
 * Tracks data flow and transformations across datasets, enabling:
 * <ul>
 * <li>Upstream lineage - where data came from</li>
 * <li>Downstream lineage - where data flows to</li>
 * <li>Impact analysis - what is affected by changes</li>
 * <li>Data provenance - complete history of data transformations</li>
 * </ul>
 *
 * <p>
 * Uses an in-memory directed graph for fast traversal. Can be extended to use
 * Neo4j or other graph databases.</p>
 *
 * @doc.type class
 * @doc.purpose Data lineage tracking
 * @doc.layer product
 * @doc.pattern Service
 */
public class LineageTracker {

    /**
     * The lineage graph - nodes are datasets, edges are transformations.
     */
    private final Graph<LineageNode, LineageEdge> lineageGraph;

    /**
     * Node lookup by ID.
     */
    private final Map<String, LineageNode> nodeIndex;

    /**
     * Edge lookup by ID.
     */
    private final Map<String, LineageEdge> edgeIndex;

    /**
     * Lineage event listeners.
     */
    private final List<LineageEventListener> listeners;

    /**
     * Creates a new lineage tracker.
     */
    public LineageTracker() {
        this.lineageGraph = new DefaultDirectedGraph<>(LineageEdge.class);
        this.nodeIndex = new ConcurrentHashMap<>();
        this.edgeIndex = new ConcurrentHashMap<>();
        this.listeners = new ArrayList<>();
    }

    /**
     * Registers a dataset node in the lineage graph.
     *
     * @param datasetId Dataset identifier
     * @param datasetName Human-readable name
     * @param tier Data tier (bronze/silver/gold)
     * @param metadata Additional metadata
     * @return Promise of the registered node
     */
    public Promise<LineageNode> registerDataset(
            String datasetId,
            String datasetName,
            DataTier tier,
            Map<String, String> metadata) {

        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            LineageNode existingNode = nodeIndex.get(datasetId);
            if (existingNode != null) {
                return existingNode;
            }

            LineageNode node = LineageNode.builder()
                    .nodeId(datasetId)
                    .name(datasetName)
                    .nodeType(NodeType.DATASET)
                    .tier(tier)
                    .metadata(metadata != null ? new HashMap<>(metadata) : new HashMap<>())
                    .build();

            synchronized (lineageGraph) {
                lineageGraph.addVertex(node);
            }
            nodeIndex.put(datasetId, node);

            // Emit event
            notifyListeners(LineageEvent.builder()
                    .eventType(LineageEventType.NODE_CREATED)
                    .nodeId(datasetId)
                    .build());

            return node;
        });
    }

    /**
     * Records a transformation relationship between datasets.
     *
     * @param sourceId Source dataset ID
     * @param targetId Target dataset ID
     * @param transformationType Type of transformation
     * @param transformationDetails Details about the transformation
     * @return Promise of the created edge
     */
    public Promise<LineageEdge> recordTransformation(
            String sourceId,
            String targetId,
            TransformationType transformationType,
            Map<String, Object> transformationDetails) {

        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            LineageNode sourceNode = nodeIndex.get(sourceId);
            LineageNode targetNode = nodeIndex.get(targetId);

            if (sourceNode == null) {
                throw new IllegalArgumentException("Source dataset not found: " + sourceId);
            }
            if (targetNode == null) {
                throw new IllegalArgumentException("Target dataset not found: " + targetId);
            }

            String edgeId = UUID.randomUUID().toString();
            LineageEdge edge = LineageEdge.builder()
                    .edgeId(edgeId)
                    .sourceId(sourceId)
                    .targetId(targetId)
                    .transformationType(transformationType)
                    .transformationDetails(transformationDetails != null
                            ? new HashMap<>(transformationDetails) : new HashMap<>())
                    .build();

            synchronized (lineageGraph) {
                lineageGraph.addEdge(sourceNode, targetNode, edge);
            }
            edgeIndex.put(edgeId, edge);

            // Update node statistics
            sourceNode.incrementDownstreamCount();
            targetNode.incrementUpstreamCount();

            // Emit event
            notifyListeners(LineageEvent.builder()
                    .eventType(LineageEventType.EDGE_CREATED)
                    .edgeId(edgeId)
                    .sourceNodeId(sourceId)
                    .targetNodeId(targetId)
                    .build());

            return edge;
        });
    }

    /**
     * Gets upstream lineage (data sources) for a dataset.
     *
     * @param datasetId Dataset to analyze
     * @param maxDepth Maximum depth to traverse (-1 for unlimited)
     * @return Promise of upstream lineage result
     */
    public Promise<LineageResult> getUpstreamLineage(String datasetId, int maxDepth) {
        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            LineageNode startNode = nodeIndex.get(datasetId);
            if (startNode == null) {
                return LineageResult.empty(datasetId);
            }

            Set<LineageNode> visitedNodes = new HashSet<>();
            Set<LineageEdge> visitedEdges = new HashSet<>();
            Map<String, Integer> nodeDepths = new HashMap<>();

            traverseUpstream(startNode, 0, maxDepth, visitedNodes, visitedEdges, nodeDepths);

            return LineageResult.builder()
                    .rootNodeId(datasetId)
                    .direction(LineageDirection.UPSTREAM)
                    .nodes(new ArrayList<>(visitedNodes))
                    .edges(new ArrayList<>(visitedEdges))
                    .nodeDepths(nodeDepths)
                    .maxDepthReached(calculateMaxDepth(nodeDepths))
                    .build();
        });
    }

    /**
     * Gets downstream lineage (data consumers) for a dataset.
     *
     * @param datasetId Dataset to analyze
     * @param maxDepth Maximum depth to traverse (-1 for unlimited)
     * @return Promise of downstream lineage result
     */
    public Promise<LineageResult> getDownstreamLineage(String datasetId, int maxDepth) {
        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> 
            getDownstreamLineageSync(datasetId, maxDepth));
    }

    /**
     * Synchronous version of downstream lineage calculation.
     * Used internally to avoid nested Promise.ofBlocking calls.
     *
     * @param datasetId Dataset to analyze
     * @param maxDepth Maximum depth to traverse (-1 for unlimited)
     * @return Downstream lineage result
     */
    private LineageResult getDownstreamLineageSync(String datasetId, int maxDepth) {
        LineageNode startNode = nodeIndex.get(datasetId);
        if (startNode == null) {
            return LineageResult.empty(datasetId);
        }

        Set<LineageNode> visitedNodes = new HashSet<>();
        Set<LineageEdge> visitedEdges = new HashSet<>();
        Map<String, Integer> nodeDepths = new HashMap<>();

        traverseDownstream(startNode, 0, maxDepth, visitedNodes, visitedEdges, nodeDepths);

        return LineageResult.builder()
                .rootNodeId(datasetId)
                .direction(LineageDirection.DOWNSTREAM)
                .nodes(new ArrayList<>(visitedNodes))
                .edges(new ArrayList<>(visitedEdges))
                .nodeDepths(nodeDepths)
                .maxDepthReached(calculateMaxDepth(nodeDepths))
                .build();
    }

    /**
     * Performs impact analysis to determine what would be affected by a change.
     *
     * @param datasetId Dataset being changed
     * @param changeType Type of change (schema, data, deletion)
     * @return Promise of impact analysis result
     */
    public Promise<ImpactAnalysis> analyzeImpact(String datasetId, ChangeType changeType) {
        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            LineageNode changedNode = nodeIndex.get(datasetId);
            if (changedNode == null) {
                return ImpactAnalysis.noImpact(datasetId);
            }

            // Get all downstream nodes that would be affected (use sync version to avoid nested blocking)
            LineageResult downstream = getDownstreamLineageSync(datasetId, -1);

            List<ImpactedDataset> impactedDatasets = new ArrayList<>();
            for (LineageNode node : downstream.getNodes()) {
                if (!node.getNodeId().equals(datasetId)) {
                    int depth = downstream.getNodeDepths().get(node.getNodeId());
                    ImpactSeverity severity = calculateImpactSeverity(changeType, depth, node.getTier());

                    impactedDatasets.add(ImpactedDataset.builder()
                            .datasetId(node.getNodeId())
                            .datasetName(node.getName())
                            .tier(node.getTier())
                            .distanceFromChange(depth)
                            .severity(severity)
                            .requiresReprocessing(severity.requiresReprocessing())
                            .build());
                }
            }

            // Sort by severity (highest first)
            impactedDatasets.sort((a, b) -> b.getSeverity().compareTo(a.getSeverity()));

            return ImpactAnalysis.builder()
                    .changedDatasetId(datasetId)
                    .changeType(changeType)
                    .totalImpactedDatasets(impactedDatasets.size())
                    .impactedDatasets(impactedDatasets)
                    .criticalImpacts(countBySeverity(impactedDatasets, ImpactSeverity.CRITICAL))
                    .highImpacts(countBySeverity(impactedDatasets, ImpactSeverity.HIGH))
                    .mediumImpacts(countBySeverity(impactedDatasets, ImpactSeverity.MEDIUM))
                    .lowImpacts(countBySeverity(impactedDatasets, ImpactSeverity.LOW))
                    .build();
        });
    }

    /**
     * Gets the full lineage graph for visualization.
     *
     * @return Promise of graph representation
     */
    public Promise<LineageGraphSnapshot> getGraphSnapshot() {
        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            List<LineageNode> nodes;
            List<LineageEdge> edges;

            synchronized (lineageGraph) {
                nodes = new ArrayList<>(lineageGraph.vertexSet());
                edges = new ArrayList<>(lineageGraph.edgeSet());
            }

            return LineageGraphSnapshot.builder()
                    .nodes(nodes)
                    .edges(edges)
                    .nodeCount(nodes.size())
                    .edgeCount(edges.size())
                    .snapshotTime(Instant.now())
                    .build();
        });
    }

    /**
     * Registers a lineage event listener.
     *
     * @param listener Listener to register
     */
    public void addListener(LineageEventListener listener) {
        listeners.add(listener);
    }

    /**
     * Removes a lineage event listener.
     *
     * @param listener Listener to remove
     */
    public void removeListener(LineageEventListener listener) {
        listeners.remove(listener);
    }

    // --- Private Helper Methods ---
    private void traverseUpstream(
            LineageNode node,
            int currentDepth,
            int maxDepth,
            Set<LineageNode> visitedNodes,
            Set<LineageEdge> visitedEdges,
            Map<String, Integer> nodeDepths) {

        if (visitedNodes.contains(node)) {
            return;
        }

        visitedNodes.add(node);
        nodeDepths.put(node.getNodeId(), currentDepth);

        if (maxDepth != -1 && currentDepth >= maxDepth) {
            return;
        }

        synchronized (lineageGraph) {
            Set<LineageEdge> incomingEdges = lineageGraph.incomingEdgesOf(node);
            for (LineageEdge edge : incomingEdges) {
                visitedEdges.add(edge);
                LineageNode sourceNode = lineageGraph.getEdgeSource(edge);
                traverseUpstream(sourceNode, currentDepth + 1, maxDepth, visitedNodes, visitedEdges, nodeDepths);
            }
        }
    }

    private void traverseDownstream(
            LineageNode node,
            int currentDepth,
            int maxDepth,
            Set<LineageNode> visitedNodes,
            Set<LineageEdge> visitedEdges,
            Map<String, Integer> nodeDepths) {

        if (visitedNodes.contains(node)) {
            return;
        }

        visitedNodes.add(node);
        nodeDepths.put(node.getNodeId(), currentDepth);

        if (maxDepth != -1 && currentDepth >= maxDepth) {
            return;
        }

        synchronized (lineageGraph) {
            Set<LineageEdge> outgoingEdges = lineageGraph.outgoingEdgesOf(node);
            for (LineageEdge edge : outgoingEdges) {
                visitedEdges.add(edge);
                LineageNode targetNode = lineageGraph.getEdgeTarget(edge);
                traverseDownstream(targetNode, currentDepth + 1, maxDepth, visitedNodes, visitedEdges, nodeDepths);
            }
        }
    }

    private int calculateMaxDepth(Map<String, Integer> nodeDepths) {
        return nodeDepths.values().stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);
    }

    private ImpactSeverity calculateImpactSeverity(ChangeType changeType, int depth, DataTier tier) {
        // Base severity from change type
        int baseSeverity = switch (changeType) {
            case SCHEMA_BREAKING ->
                4;
            case SCHEMA_ADDITIVE ->
                2;
            case DATA_QUALITY ->
                3;
            case DELETION ->
                5;
            case DEPRECATION ->
                1;
        };

        // Tier multiplier (gold is more critical)
        double tierMultiplier = switch (tier) {
            case GOLD ->
                1.5;
            case SILVER ->
                1.0;
            case BRONZE ->
                0.7;
            case ARCHIVE ->
                0.3;
        };

        // Distance decay (further = less impact)
        double distanceDecay = Math.max(0.5, 1.0 - (depth * 0.15));

        double finalScore = baseSeverity * tierMultiplier * distanceDecay;

        if (finalScore >= 4.0) {
            return ImpactSeverity.CRITICAL;
        }
        if (finalScore >= 3.0) {
            return ImpactSeverity.HIGH;
        }
        if (finalScore >= 2.0) {
            return ImpactSeverity.MEDIUM;
        }
        return ImpactSeverity.LOW;
    }

    private long countBySeverity(List<ImpactedDataset> datasets, ImpactSeverity severity) {
        return datasets.stream()
                .filter(d -> d.getSeverity() == severity)
                .count();
    }

    private void notifyListeners(LineageEvent event) {
        for (LineageEventListener listener : listeners) {
            try {
                listener.onLineageEvent(event);
            } catch (Exception e) {
                // Log but don't fail
            }
        }
    }

    // --- Inner Classes ---
    /**
     * Data tier classification.
     */
    public enum DataTier {
        BRONZE, SILVER, GOLD, ARCHIVE
    }

    /**
     * Node type in lineage graph.
     */
    public enum NodeType {
        DATASET, TRANSFORMATION, EXTERNAL_SOURCE, EXTERNAL_SINK
    }

    /**
     * Types of transformations.
     */
    public enum TransformationType {
        INGESTION,
        CLEANING,
        ENRICHMENT,
        AGGREGATION,
        FILTERING,
        JOINING,
        DEDUPLICATION,
        NORMALIZATION,
        DENORMALIZATION,
        MATERIALIZATION,
        EXPORT
    }

    /**
     * Direction of lineage traversal.
     */
    public enum LineageDirection {
        UPSTREAM, DOWNSTREAM, BOTH
    }

    /**
     * Types of changes that can affect lineage.
     */
    public enum ChangeType {
        SCHEMA_BREAKING,
        SCHEMA_ADDITIVE,
        DATA_QUALITY,
        DELETION,
        DEPRECATION
    }

    /**
     * Impact severity levels.
     */
    public enum ImpactSeverity {
        CRITICAL(true),
        HIGH(true),
        MEDIUM(false),
        LOW(false);

        private final boolean requiresReprocessing;

        ImpactSeverity(boolean requiresReprocessing) {
            this.requiresReprocessing = requiresReprocessing;
        }

        public boolean requiresReprocessing() {
            return requiresReprocessing;
        }
    }

    /**
     * Lineage event types.
     */
    public enum LineageEventType {
        NODE_CREATED,
        NODE_UPDATED,
        NODE_DELETED,
        EDGE_CREATED,
        EDGE_DELETED,
        GRAPH_SNAPSHOT
    }

    /**
     * A node in the lineage graph.
     */
    @Getter
    @Builder
    public static class LineageNode {

        private final String nodeId;
        private final String name;
        private final NodeType nodeType;
        private final DataTier tier;
        @Builder.Default
        private final Map<String, String> metadata = new HashMap<>();
        @Builder.Default
        private final Instant createdAt = Instant.now();
        @Builder.Default
        private int upstreamCount = 0;
        @Builder.Default
        private int downstreamCount = 0;

        public void incrementUpstreamCount() {
            upstreamCount++;
        }

        public void incrementDownstreamCount() {
            downstreamCount++;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            LineageNode that = (LineageNode) obj;
            return Objects.equals(nodeId, that.nodeId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(nodeId);
        }
    }

    /**
     * An edge in the lineage graph (transformation).
     */
    @Getter
    @Builder
    public static class LineageEdge extends DefaultEdge {

        private final String edgeId;
        private final String sourceId;
        private final String targetId;
        private final TransformationType transformationType;
        @Builder.Default
        private final Map<String, Object> transformationDetails = new HashMap<>();
        @Builder.Default
        private final Instant createdAt = Instant.now();

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            LineageEdge that = (LineageEdge) obj;
            return Objects.equals(edgeId, that.edgeId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(edgeId);
        }
    }

    /**
     * Result of lineage traversal.
     */
    @Getter
    @Builder
    public static class LineageResult {

        private final String rootNodeId;
        private final LineageDirection direction;
        @Builder.Default
        private final List<LineageNode> nodes = List.of();
        @Builder.Default
        private final List<LineageEdge> edges = List.of();
        @Builder.Default
        private final Map<String, Integer> nodeDepths = Map.of();
        private final int maxDepthReached;

        public static LineageResult empty(String rootNodeId) {
            return LineageResult.builder()
                    .rootNodeId(rootNodeId)
                    .direction(LineageDirection.UPSTREAM)
                    .maxDepthReached(0)
                    .build();
        }
    }

    /**
     * Result of impact analysis.
     */
    @Getter
    @Builder
    public static class ImpactAnalysis {

        private final String changedDatasetId;
        private final ChangeType changeType;
        private final int totalImpactedDatasets;
        @Builder.Default
        private final List<ImpactedDataset> impactedDatasets = List.of();
        private final long criticalImpacts;
        private final long highImpacts;
        private final long mediumImpacts;
        private final long lowImpacts;
        @Builder.Default
        private final Instant analyzedAt = Instant.now();

        public static ImpactAnalysis noImpact(String datasetId) {
            return ImpactAnalysis.builder()
                    .changedDatasetId(datasetId)
                    .changeType(ChangeType.DATA_QUALITY)
                    .totalImpactedDatasets(0)
                    .build();
        }

        public boolean hasCriticalImpact() {
            return criticalImpacts > 0;
        }

        public boolean requiresApproval() {
            return criticalImpacts > 0 || highImpacts > 2;
        }
    }

    /**
     * A dataset impacted by a change.
     */
    @Getter
    @Builder
    public static class ImpactedDataset {

        private final String datasetId;
        private final String datasetName;
        private final DataTier tier;
        private final int distanceFromChange;
        private final ImpactSeverity severity;
        private final boolean requiresReprocessing;
    }

    /**
     * Snapshot of the lineage graph.
     */
    @Getter
    @Builder
    public static class LineageGraphSnapshot {

        @Builder.Default
        private final List<LineageNode> nodes = List.of();
        @Builder.Default
        private final List<LineageEdge> edges = List.of();
        private final int nodeCount;
        private final int edgeCount;
        private final Instant snapshotTime;

        public Map<String, Object> toVisualizationFormat() {
            Map<String, Object> result = new HashMap<>();

            List<Map<String, Object>> nodeList = nodes.stream()
                    .map(n -> {
                        Map<String, Object> nodeMap = new HashMap<>();
                        nodeMap.put("id", n.getNodeId());
                        nodeMap.put("label", n.getName());
                        nodeMap.put("tier", n.getTier().name());
                        nodeMap.put("type", n.getNodeType().name());
                        return nodeMap;
                    })
                    .collect(Collectors.toList());

            List<Map<String, Object>> edgeList = edges.stream()
                    .map(e -> {
                        Map<String, Object> edgeMap = new HashMap<>();
                        edgeMap.put("source", e.getSourceId());
                        edgeMap.put("target", e.getTargetId());
                        edgeMap.put("type", e.getTransformationType().name());
                        return edgeMap;
                    })
                    .collect(Collectors.toList());

            result.put("nodes", nodeList);
            result.put("edges", edgeList);
            result.put("timestamp", snapshotTime.toString());

            return result;
        }
    }

    /**
     * Lineage event for listeners.
     */
    @Getter
    @Builder
    public static class LineageEvent {

        private final LineageEventType eventType;
        private final String nodeId;
        private final String edgeId;
        private final String sourceNodeId;
        private final String targetNodeId;
        @Builder.Default
        private final Instant timestamp = Instant.now();
    }

    /**
     * Listener interface for lineage events.
     */
    @FunctionalInterface
    public interface LineageEventListener {

        void onLineageEvent(LineageEvent event);
    }
}
