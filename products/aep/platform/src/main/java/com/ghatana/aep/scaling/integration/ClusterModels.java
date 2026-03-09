package com.ghatana.aep.scaling.integration;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

/**
 * Cluster state and model classes for cluster management.
 *
 * <p>Purpose: Contains immutable value objects representing cluster state,
 * node information, and cluster topology. Used for monitoring and managing
 * cluster membership and health.</p>
 *
 * @doc.type class
 * @doc.purpose Container for cluster state model classes
 * @doc.layer product
 * @doc.pattern ValueObject
 * @since 2.0.0
 */
class ClusterState {
    private final List<NodeInfo> nodes;
    private final String state;
    private final long timestamp;
    
    public ClusterState(List<NodeInfo> nodes, String state) {
        this.nodes = nodes != null ? new ArrayList<>(nodes) : new ArrayList<>();
        this.state = state != null ? state : "UNKNOWN";
        this.timestamp = System.currentTimeMillis();
    }
    
    public List<NodeInfo> getNodes() { return new ArrayList<>(nodes); }
    public String getState() { return state; }
    public long getTimestamp() { return timestamp; }
    
    public int getNodeCount() { return nodes.size(); }
    
    public NodeInfo getNode(String nodeId) {
        return nodes.stream()
            .filter(node -> node.getId().equals(nodeId))
            .findFirst()
            .orElse(null);
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String clusterId;
        private int nodeCount;
        private long timestamp;
        
        public Builder clusterId(String clusterId) {
            this.clusterId = clusterId;
            return this;
        }
        
        public Builder nodeCount(int nodeCount) {
            this.nodeCount = nodeCount;
            return this;
        }
        
        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public ClusterState build() {
            return new ClusterState(new ArrayList<>(), "ACTIVE");
        }
    }
}

/**
 * Node information
 */
class NodeInfo {
    private final String id;
    private final String host;
    private final int port;
    private final String status;
    private final double cpuUsage;
    private final double memoryUsage;
    private final int activePatterns;
    private final Map<String, Object> metadata;
    
    public NodeInfo(String id, String host, int port, String status, 
                   double cpuUsage, double memoryUsage, int activePatterns) {
        this.id = id;
        this.host = host;
        this.port = port;
        this.status = status;
        this.cpuUsage = cpuUsage;
        this.memoryUsage = memoryUsage;
        this.activePatterns = activePatterns;
        this.metadata = new HashMap<>();
    }
    
    public String getId() { return id; }
    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getStatus() { return status; }
    public double getCpuUsage() { return cpuUsage; }
    public double getMemoryUsage() { return memoryUsage; }
    public int getActivePatterns() { return activePatterns; }
    public Map<String, Object> getMetadata() { return new HashMap<>(metadata); }
    
    public void addMetadata(String key, Object value) {
        metadata.put(key, value);
    }
    
    @Override
    public String toString() {
        return "NodeInfo{" +
                "id='" + id + '\'' +
                ", host='" + host + '\'' +
                ", port=" + port +
                ", status='" + status + '\'' +
                '}';
    }
}

/**
 * Routing table for load balancer
 */
class RoutingTable {
    private final Map<String, List<String>> patternToNodes;
    private final Map<String, NodeWeight> nodeWeights;
    private final long timestamp;
    
    public RoutingTable() {
        this.patternToNodes = new HashMap<>();
        this.nodeWeights = new HashMap<>();
        this.timestamp = System.currentTimeMillis();
    }
    
    public RoutingTable(Map<String, List<String>> patternToNodes, Map<String, NodeWeight> nodeWeights) {
        this.patternToNodes = patternToNodes != null ? new HashMap<>(patternToNodes) : new HashMap<>();
        this.nodeWeights = nodeWeights != null ? new HashMap<>(nodeWeights) : new HashMap<>();
        this.timestamp = System.currentTimeMillis();
    }
    
    public Map<String, List<String>> getPatternToNodes() { return new HashMap<>(patternToNodes); }
    public Map<String, NodeWeight> getNodeWeights() { return new HashMap<>(nodeWeights); }
    public long getTimestamp() { return timestamp; }
    
    public List<String> getNodesForPattern(String patternId) {
        return patternToNodes.getOrDefault(patternId, new ArrayList<>());
    }
    
    public NodeWeight getNodeWeight(String nodeId) {
        return nodeWeights.get(nodeId);
    }
    
    public void addPatternMapping(String patternId, List<String> nodes) {
        patternToNodes.put(patternId, new ArrayList<>(nodes));
    }
    
    public void setNodeWeight(String nodeId, NodeWeight weight) {
        nodeWeights.put(nodeId, weight);
    }
}

/**
 * Node weight for load balancing
 */
class NodeWeight {
    private final String nodeId;
    private final double weight;
    private final long lastUpdated;
    
    public NodeWeight(String nodeId, double weight) {
        this.nodeId = nodeId;
        this.weight = weight;
        this.lastUpdated = System.currentTimeMillis();
    }
    
    public String getNodeId() { return nodeId; }
    public double getWeight() { return weight; }
    public long getLastUpdated() { return lastUpdated; }
    
    @Override
    public String toString() {
        return "NodeWeight{" +
                "nodeId='" + nodeId + '\'' +
                ", weight=" + weight +
                '}';
    }
}
