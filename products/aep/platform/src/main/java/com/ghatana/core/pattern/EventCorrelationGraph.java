package com.ghatana.core.pattern;

import com.ghatana.platform.domain.domain.event.Event;
import java.util.*;

/**
 * Graph representation of event correlations for connected component analysis.
 *
 * @see AdvancedCorrelationEngine
 * @doc.type class
 * @doc.purpose Graph-based correlation analysis
 * @doc.layer core
 * @doc.pattern Graph
 */
public class EventCorrelationGraph {

    private final Map<String, EventNode> nodes;
    private final Map<String, List<Edge>> adjacencyList;
    private final List<Edge> edges;

    public EventCorrelationGraph() {
        this.nodes = new HashMap<>();
        this.adjacencyList = new HashMap<>();
        this.edges = new ArrayList<>();
    }

    /**
     * Add node to graph.
     */
    public void addNode(String eventId, Event event) {
        nodes.put(eventId, new EventNode(eventId, event));
        adjacencyList.putIfAbsent(eventId, new ArrayList<>());
    }

    /**
     * Add edge between nodes.
     */
    public void addEdge(String fromId, String toId, double weight) {
        if (!nodes.containsKey(fromId) || !nodes.containsKey(toId)) {
            return;
        }

        Edge edge = new Edge(fromId, toId, weight);
        edges.add(edge);
        adjacencyList.get(fromId).add(edge);
        
        // Add reverse edge for undirected graph
        Edge reverseEdge = new Edge(toId, fromId, weight);
        adjacencyList.get(toId).add(reverseEdge);
    }

    /**
     * Find connected components using DFS.
     */
    public List<Set<String>> findConnectedComponents() {
        Set<String> visited = new HashSet<>();
        List<Set<String>> components = new ArrayList<>();

        for (String nodeId : nodes.keySet()) {
            if (!visited.contains(nodeId)) {
                Set<String> component = new HashSet<>();
                dfs(nodeId, visited, component);
                if (component.size() > 1) { // Only include components with multiple nodes
                    components.add(component);
                }
            }
        }

        return components;
    }

    /**
     * Depth-first search to find connected component.
     */
    private void dfs(String nodeId, Set<String> visited, Set<String> component) {
        visited.add(nodeId);
        component.add(nodeId);

        for (Edge edge : adjacencyList.get(nodeId)) {
            String neighborId = edge.getTo();
            if (!visited.contains(neighborId)) {
                dfs(neighborId, visited, component);
            }
        }
    }

    /**
     * Calculate connectivity score for a component.
     */
    public double calculateConnectivity(Set<String> component) {
        if (component.size() < 2) {
            return 0.0;
        }

        int possibleEdges = component.size() * (component.size() - 1) / 2;
        int actualEdges = 0;
        double totalWeight = 0.0;

        for (Edge edge : edges) {
            if (component.contains(edge.getFrom()) && component.contains(edge.getTo())) {
                actualEdges++;
                totalWeight += edge.getWeight();
            }
        }

        double edgeRatio = possibleEdges > 0 ? (double) actualEdges / possibleEdges : 0.0;
        double avgWeight = actualEdges > 0 ? totalWeight / actualEdges : 0.0;

        return edgeRatio * avgWeight;
    }

    /**
     * Get node count.
     */
    public int getNodeCount() {
        return nodes.size();
    }

    /**
     * Get edge count.
     */
    public int getEdgeCount() {
        return edges.size();
    }

    /**
     * Get node by ID.
     */
    public EventNode getNode(String eventId) {
        return nodes.get(eventId);
    }

    /**
     * Get all nodes.
     */
    public Collection<EventNode> getAllNodes() {
        return nodes.values();
    }

    /**
     * Get all edges.
     */
    public List<Edge> getAllEdges() {
        return new ArrayList<>(edges);
    }

    /**
     * Graph node representing an event.
     */
    public static class EventNode {
        private final String id;
        private final Event event;

        public EventNode(String id, Event event) {
            this.id = id;
            this.event = event;
        }

        public String getId() { return id; }
        public Event getEvent() { return event; }

        @Override
        public String toString() {
            return String.format("EventNode{id=%s, type=%s}", id, event.getType());
        }
    }

    /**
     * Graph edge representing correlation between events.
     */
    public static class Edge {
        private final String from;
        private final String to;
        private final double weight;

        public Edge(String from, String to, double weight) {
            this.from = from;
            this.to = to;
            this.weight = weight;
        }

        public String getFrom() { return from; }
        public String getTo() { return to; }
        public double getWeight() { return weight; }

        @Override
        public String toString() {
            return String.format("Edge{%s->%s, weight=%.2f}", from, to, weight);
        }
    }
}
