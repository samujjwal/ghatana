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
import com.ghatana.datacloud.record.impl.FullGraphRecord.EdgeDirection;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory property graph backed by adjacency lists.
 *
 * <p>Provides O(1) node/edge lookup and O(degree) neighbour traversal using
 * two parallel adjacency maps (outgoing and incoming). Thread-safe via
 * {@link ConcurrentHashMap}.
 *
 * <h2>Implementation Notes</h2>
 * <ul>
 *   <li>Nodes stored by their {@code id().toString()} key.</li>
 *   <li>Edges stored by their own {@code id().toString()} key, plus indexed
 *       in outgoing/incoming adjacency lists keyed by source/target node ID.</li>
 *   <li>{@link EdgeDirection#BIDIRECTIONAL} edges appear in both the outgoing
 *       list of source and the outgoing list of target.</li>
 *   <li>{@link EdgeDirection#UNDIRECTED} edges appear in both adjacency directions
 *       for both nodes.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose In-memory property graph
 * @doc.layer core
 * @doc.pattern Strategy, Adjacency List
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
public class InMemoryGraphOperations implements GraphOperations {

    /** nodeId → node record */
    private final Map<String, FullGraphRecord> nodes = new ConcurrentHashMap<>();

    /** edgeId → edge record */
    private final Map<String, FullGraphRecord> edges = new ConcurrentHashMap<>();

    /** nodeId → outgoing edges from that node */
    private final Map<String, List<FullGraphRecord>> outgoing = new ConcurrentHashMap<>();

    /** nodeId → incoming edges into that node */
    private final Map<String, List<FullGraphRecord>> incoming = new ConcurrentHashMap<>();

    // ═══════════════════════════════════════════════════════════════
    // Mutation
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void addNode(FullGraphRecord node) {
        Objects.requireNonNull(node, "node required");
        if (!node.isNode()) throw new IllegalArgumentException("Expected NODE, got " + node.elementType());

        String id = node.id().toString();
        nodes.put(id, node);
        outgoing.putIfAbsent(id, Collections.synchronizedList(new ArrayList<>()));
        incoming.putIfAbsent(id, Collections.synchronizedList(new ArrayList<>()));
    }

    @Override
    public void addEdge(FullGraphRecord edge) {
        Objects.requireNonNull(edge, "edge required");
        if (!edge.isEdge()) throw new IllegalArgumentException("Expected EDGE, got " + edge.elementType());
        Objects.requireNonNull(edge.sourceNodeId(), "sourceNodeId required for edge");
        Objects.requireNonNull(edge.targetNodeId(), "targetNodeId required for edge");

        String edgeId = edge.id().toString();
        edges.put(edgeId, edge);

        // Index into adjacency lists
        outgoing.computeIfAbsent(edge.sourceNodeId(), k -> Collections.synchronizedList(new ArrayList<>()))
                .add(edge);
        incoming.computeIfAbsent(edge.targetNodeId(), k -> Collections.synchronizedList(new ArrayList<>()))
                .add(edge);

        // Bidirectional / undirected: reverse direction as well
        if (edge.direction() == EdgeDirection.BIDIRECTIONAL || edge.direction() == EdgeDirection.UNDIRECTED) {
            outgoing.computeIfAbsent(edge.targetNodeId(), k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(edge);
            incoming.computeIfAbsent(edge.sourceNodeId(), k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(edge);
        }
    }

    @Override
    public boolean removeNode(String nodeId) {
        if (nodeId == null || !nodes.containsKey(nodeId)) return false;

        // Remove incident edges
        List<FullGraphRecord> out = outgoing.getOrDefault(nodeId, List.of());
        List<FullGraphRecord> in = incoming.getOrDefault(nodeId, List.of());
        var allIncident = new HashSet<String>();
        out.forEach(e -> allIncident.add(e.id().toString()));
        in.forEach(e -> allIncident.add(e.id().toString()));

        for (String edgeId : allIncident) {
            removeEdge(edgeId);
        }

        nodes.remove(nodeId);
        outgoing.remove(nodeId);
        incoming.remove(nodeId);
        return true;
    }

    @Override
    public boolean removeEdge(String edgeId) {
        FullGraphRecord edge = edges.remove(edgeId);
        if (edge == null) return false;

        // Clean adjacency lists
        outgoing.getOrDefault(edge.sourceNodeId(), List.of())
                .removeIf(e -> e.id().toString().equals(edgeId));
        incoming.getOrDefault(edge.targetNodeId(), List.of())
                .removeIf(e -> e.id().toString().equals(edgeId));

        if (edge.direction() == EdgeDirection.BIDIRECTIONAL || edge.direction() == EdgeDirection.UNDIRECTED) {
            outgoing.getOrDefault(edge.targetNodeId(), List.of())
                    .removeIf(e -> e.id().toString().equals(edgeId));
            incoming.getOrDefault(edge.sourceNodeId(), List.of())
                    .removeIf(e -> e.id().toString().equals(edgeId));
        }
        return true;
    }

    // ═══════════════════════════════════════════════════════════════
    // Lookup
    // ═══════════════════════════════════════════════════════════════

    @Override
    public Optional<FullGraphRecord> getNode(String nodeId) {
        return Optional.ofNullable(nodes.get(nodeId));
    }

    @Override
    public List<FullGraphRecord> getNodesByLabel(String label) {
        return nodes.values().stream()
                .filter(n -> Objects.equals(n.label(), label))
                .toList();
    }

    @Override
    public List<FullGraphRecord> getEdgesBetween(String sourceId, String targetId) {
        return edges.values().stream()
                .filter(e -> (e.sourceNodeId().equals(sourceId) && e.targetNodeId().equals(targetId))
                        || (e.sourceNodeId().equals(targetId) && e.targetNodeId().equals(sourceId)))
                .toList();
    }

    // ═══════════════════════════════════════════════════════════════
    // Traversal
    // ═══════════════════════════════════════════════════════════════

    @Override
    public List<EdgeTarget> outgoing(String nodeId) {
        return outgoing.getOrDefault(nodeId, List.of()).stream()
                .map(edge -> {
                    // For this edge, the "other" node is the target if we came from source,
                    // or the source if this is a bidirectional reverse entry
                    String otherNodeId = edge.sourceNodeId().equals(nodeId)
                            ? edge.targetNodeId()
                            : edge.sourceNodeId();
                    FullGraphRecord otherNode = nodes.get(otherNodeId);
                    return otherNode != null ? new EdgeTarget(edge, otherNode) : null;
                })
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public List<EdgeTarget> incoming(String nodeId) {
        return incoming.getOrDefault(nodeId, List.of()).stream()
                .map(edge -> {
                    String otherNodeId = edge.targetNodeId().equals(nodeId)
                            ? edge.sourceNodeId()
                            : edge.targetNodeId();
                    FullGraphRecord otherNode = nodes.get(otherNodeId);
                    return otherNode != null ? new EdgeTarget(edge, otherNode) : null;
                })
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public List<FullGraphRecord> bfs(String startNodeId, int maxDepth) {
        if (!nodes.containsKey(startNodeId) || maxDepth < 0) return List.of();

        var visited = new LinkedHashSet<String>();
        var queue = new ArrayDeque<String>();
        var depthMap = new HashMap<String, Integer>();

        queue.add(startNodeId);
        visited.add(startNodeId);
        depthMap.put(startNodeId, 0);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            int currentDepth = depthMap.get(current);

            if (currentDepth < maxDepth) {
                for (EdgeTarget et : outgoing(current)) {
                    String neighborId = et.node().id().toString();
                    if (!visited.contains(neighborId)) {
                        visited.add(neighborId);
                        depthMap.put(neighborId, currentDepth + 1);
                        queue.add(neighborId);
                    }
                }
            }
        }

        return visited.stream()
                .map(nodes::get)
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public List<FullGraphRecord> shortestPath(String fromId, String toId) {
        if (!nodes.containsKey(fromId) || !nodes.containsKey(toId)) return List.of();
        if (fromId.equals(toId)) return List.of(nodes.get(fromId));

        var visited = new HashSet<String>();
        var queue = new ArrayDeque<String>();
        var parent = new HashMap<String, String>();

        queue.add(fromId);
        visited.add(fromId);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (EdgeTarget et : outgoing(current)) {
                String neighborId = et.node().id().toString();
                if (!visited.contains(neighborId)) {
                    visited.add(neighborId);
                    parent.put(neighborId, current);
                    if (neighborId.equals(toId)) {
                        return reconstructPath(parent, fromId, toId);
                    }
                    queue.add(neighborId);
                }
            }
        }

        return List.of(); // Unreachable
    }

    // ═══════════════════════════════════════════════════════════════
    // Analytics
    // ═══════════════════════════════════════════════════════════════

    @Override public long nodeCount() { return nodes.size(); }
    @Override public long edgeCount() { return edges.size(); }

    @Override
    public int degree(String nodeId) {
        int out = outgoing.getOrDefault(nodeId, List.of()).size();
        int in = incoming.getOrDefault(nodeId, List.of()).size();
        return out + in;
    }

    // ═══════════════════════════════════════════════════════════════
    // Internals
    // ═══════════════════════════════════════════════════════════════

    private List<FullGraphRecord> reconstructPath(Map<String, String> parent, String from, String to) {
        var path = new LinkedList<FullGraphRecord>();
        String current = to;
        while (current != null) {
            path.addFirst(nodes.get(current));
            if (current.equals(from)) break;
            current = parent.get(current);
        }
        return Collections.unmodifiableList(path);
    }
}
