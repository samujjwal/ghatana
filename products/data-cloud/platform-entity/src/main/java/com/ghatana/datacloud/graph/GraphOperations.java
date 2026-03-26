/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
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

package com.ghatana.datacloud.graph;

import com.ghatana.datacloud.record.impl.FullGraphRecord;

import java.util.*;

/**
 * Graph traversal and query operations for Data-Cloud's property graph model.
 *
 * <p>Provides path-finding, neighbour discovery, sub-graph extraction, and
 * analytic queries against a collection of {@link FullGraphRecord} nodes and
 * edges. This SPI is intentionally <em>storage-agnostic</em> — implementations
 * can work in-memory, delegate to Neo4j, or use JanusGraph/TinkerPop.
 *
 * <h2>Key Concepts</h2>
 * <dl>
 *   <dt>Node</dt>
 *   <dd>A vertex carrying properties. Identified by {@code recordId}.</dd>
 *   <dt>Edge</dt>
 *   <dd>A directed/undirected relationship connecting two nodes.</dd>
 *   <dt>Label</dt>
 *   <dd>A classification tag on nodes ("Person", "Product") and edges
 *       ("KNOWS", "PURCHASED").</dd>
 *   <dt>Traversal</dt>
 *   <dd>Walking the graph starting from a node, optionally filtering by
 *       edge label, direction, or depth.</dd>
 * </dl>
 *
 * @doc.type interface
 * @doc.purpose Graph traversal SPI
 * @doc.layer core
 * @doc.pattern Strategy, SPI
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
public interface GraphOperations {

    // ═══════════════════════════════════════════════════════════════
    // Mutation
    // ═══════════════════════════════════════════════════════════════

    /**
     * Adds a node to the graph.
     *
     * @param node the node to add (must satisfy {@code node.isNode()})
     * @throws IllegalArgumentException if the record is not a node
     */
    void addNode(FullGraphRecord node);

    /**
     * Adds an edge to the graph.
     *
     * @param edge the edge to add (must satisfy {@code edge.isEdge()})
     * @throws IllegalArgumentException if the record is not an edge
     */
    void addEdge(FullGraphRecord edge);

    /**
     * Removes a node and all of its incident edges.
     *
     * @param nodeId the ID of the node to remove
     * @return true if the node existed and was removed
     */
    boolean removeNode(String nodeId);

    /**
     * Removes an edge by its record ID.
     *
     * @param edgeId the record ID of the edge to remove
     * @return true if the edge existed and was removed
     */
    boolean removeEdge(String edgeId);

    // ═══════════════════════════════════════════════════════════════
    // Lookup
    // ═══════════════════════════════════════════════════════════════

    /**
     * Returns the node with the given ID.
     *
     * @param nodeId the node ID
     * @return the node, or empty if not found
     */
    Optional<FullGraphRecord> getNode(String nodeId);

    /**
     * Returns all nodes with the given label.
     *
     * @param label the label filter (e.g. "Person")
     * @return unmodifiable list of matching nodes
     */
    List<FullGraphRecord> getNodesByLabel(String label);

    /**
     * Returns all edges between two nodes (in either direction).
     *
     * @param sourceId source node ID
     * @param targetId target node ID
     * @return matching edges
     */
    List<FullGraphRecord> getEdgesBetween(String sourceId, String targetId);

    // ═══════════════════════════════════════════════════════════════
    // Traversal
    // ═══════════════════════════════════════════════════════════════

    /**
     * Returns the immediate outgoing neighbours of a node.
     *
     * @param nodeId the starting node
     * @return list of (edge, target-node) pairs
     */
    List<EdgeTarget> outgoing(String nodeId);

    /**
     * Returns the immediate incoming neighbours of a node.
     *
     * @param nodeId the target node
     * @return list of (edge, source-node) pairs
     */
    List<EdgeTarget> incoming(String nodeId);

    /**
     * Returns all neighbours (incoming + outgoing) of a node.
     *
     * @param nodeId the node
     * @return combined list of neighbors
     */
    default List<EdgeTarget> neighbours(String nodeId) {
        var result = new ArrayList<>(outgoing(nodeId));
        result.addAll(incoming(nodeId));
        return Collections.unmodifiableList(result);
    }

    /**
     * Breadth-first traversal up to a maximum depth.
     *
     * @param startNodeId the starting node
     * @param maxDepth maximum hops (0 returns only the start node)
     * @return nodes reachable within {@code maxDepth} hops, in BFS order
     */
    List<FullGraphRecord> bfs(String startNodeId, int maxDepth);

    /**
     * Finds the shortest unweighted path between two nodes.
     *
     * @param fromId source node ID
     * @param toId target node ID
     * @return ordered list of nodes on the path, or empty if unreachable
     */
    List<FullGraphRecord> shortestPath(String fromId, String toId);

    // ═══════════════════════════════════════════════════════════════
    // Analytics
    // ═══════════════════════════════════════════════════════════════

    /** Total number of nodes. */
    long nodeCount();

    /** Total number of edges. */
    long edgeCount();

    /**
     * Returns the degree (number of incident edges) of a node.
     *
     * @param nodeId the node
     * @return number of edges connected to the node
     */
    int degree(String nodeId);

    // ═══════════════════════════════════════════════════════════════
    // Supporting Types
    // ═══════════════════════════════════════════════════════════════

    /**
     * A pair of (edge, adjacent-node) returned by traversal helpers.
     *
     * @param edge the connecting edge
     * @param node the adjacent node
     */
    record EdgeTarget(FullGraphRecord edge, FullGraphRecord node) {
        public EdgeTarget {
            Objects.requireNonNull(edge, "edge");
            Objects.requireNonNull(node, "node");
        }
    }
}
