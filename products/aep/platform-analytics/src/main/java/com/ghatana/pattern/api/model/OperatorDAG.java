package com.ghatana.pattern.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Represents a Directed Acyclic Graph (DAG) of operators for pattern detection.
 * 
 * <p>The OperatorDAG is the executable representation produced by the pattern compiler.
 * It models operator dependencies as a directed graph where:
 * <ul>
 *   <li><b>Nodes</b>: Individual operators with configuration</li>
 *   <li><b>Edges</b>: Data flow dependencies between operators</li>
 *   <li><b>Root</b>: Final operator (sink) producing detection results</li>
 *   <li><b>Leaves</b>: Event selectors (sources) reading event streams</li>
 * </ul>
 * 
 * @doc.pattern Graph Pattern (operator dependencies), Builder Pattern (construction)
 * @doc.compiler-phase DAG representation (output of DAG generation phase)
 * @doc.threading Thread-safe after construction (immutable graph structure)
 * @doc.performance O(1) root access; O(n+e) graph traversal where n=nodes, e=edges
 * @doc.memory O(n+e) for adjacency representation
 * @doc.serialization JSON serializable via Jackson annotations
 * @doc.apiNote Use builder pattern for construction; traverse from root to leaves
 * @doc.limitation No cycle detection; assumes acyclic structure from compiler
 * 
 * <h2>DAG Structure</h2>
 * <pre>
 * Leaves (Sources)         Operators              Root (Sink)
 * ────────────────         ─────────             ────────────
 *    EventA ────┐
 *               ├──→ Filter ───┐
 *    EventB ────┘              │
 *                              ├──→ Join ───→ Output
 *    EventC ────┐              │
 *               ├──→ Window ───┘
 *    EventD ────┘
 * </pre>
 * 
 * <p><b>Design Reference:</b>
 * This DAG format implements the operator graph from WORLD_CLASS_DESIGN_MASTER.md.
 * See .github/copilot-instructions.md "Unified Operator Model" for operator types.
 */
public class OperatorDAG {
    
    @JsonProperty("nodes")
    private List<OperatorNode> nodes;
    
    @JsonProperty("edges")
    private List<OperatorEdge> edges;
    
    @JsonProperty("rootNodeId")
    private String rootNodeId;
    
    @JsonProperty("metadata")
    private Map<String, Object> metadata;
    
    // Default constructor for JSON deserialization
    public OperatorDAG() {}
    
    // Builder pattern constructor
    public OperatorDAG(Builder builder) {
        this.nodes = builder.nodes;
        this.edges = builder.edges;
        this.rootNodeId = builder.rootNodeId;
        this.metadata = builder.metadata;
    }
    
    // Getters
    public List<OperatorNode> getNodes() { return nodes; }
    public List<OperatorEdge> getEdges() { return edges; }
    public String getRootNodeId() { return rootNodeId; }
    public Map<String, Object> getMetadata() { return metadata; }
    
    // Setters
    public void setNodes(List<OperatorNode> nodes) { this.nodes = nodes; }
    public void setEdges(List<OperatorEdge> edges) { this.edges = edges; }
    public void setRootNodeId(String rootNodeId) { this.rootNodeId = rootNodeId; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private List<OperatorNode> nodes;
        private List<OperatorEdge> edges;
        private String rootNodeId;
        private Map<String, Object> metadata;
        
        public Builder nodes(List<OperatorNode> nodes) { this.nodes = nodes; return this; }
        public Builder edges(List<OperatorEdge> edges) { this.edges = edges; return this; }
        public Builder rootNodeId(String rootNodeId) { this.rootNodeId = rootNodeId; return this; }
        public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }
        
        public OperatorDAG build() {
            return new OperatorDAG(this);
        }
    }
    
    /**
     * Represents a node in the operator DAG.
     */
    public static class OperatorNode {
        
        @JsonProperty("id")
        private String id;
        
        @JsonProperty("type")
        private String type;
        
        @JsonProperty("operatorSpec")
        private OperatorSpec operatorSpec;
        
        @JsonProperty("metadata")
        private Map<String, Object> metadata;
        
        // Default constructor for JSON deserialization
        public OperatorNode() {}
        
        // Builder pattern constructor
        public OperatorNode(Builder builder) {
            this.id = builder.id;
            this.type = builder.type;
            this.operatorSpec = builder.operatorSpec;
            this.metadata = builder.metadata;
        }
        
        // Getters
        public String getId() { return id; }
        public String getType() { return type; }
        public OperatorSpec getOperatorSpec() { return operatorSpec; }
        public Map<String, Object> getMetadata() { return metadata; }
        
        // Setters
        public void setId(String id) { this.id = id; }
        public void setType(String type) { this.type = type; }
        public void setOperatorSpec(OperatorSpec operatorSpec) { this.operatorSpec = operatorSpec; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private String id;
            private String type;
            private OperatorSpec operatorSpec;
            private Map<String, Object> metadata;
            
            public Builder id(String id) { this.id = id; return this; }
            public Builder type(String type) { this.type = type; return this; }
            public Builder operatorSpec(OperatorSpec operatorSpec) { this.operatorSpec = operatorSpec; return this; }
            public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }
            
            public OperatorNode build() {
                return new OperatorNode(this);
            }
        }
        
        @Override
        public String toString() {
            return "OperatorNode{" +
                    "id='" + id + '\'' +
                    ", type='" + type + '\'' +
                    '}';
        }
    }
    
    /**
     * Represents an edge in the operator DAG.
     */
    public static class OperatorEdge {
        
        @JsonProperty("fromNodeId")
        private String fromNodeId;
        
        @JsonProperty("toNodeId")
        private String toNodeId;
        
        @JsonProperty("edgeType")
        private EdgeType edgeType;
        
        @JsonProperty("metadata")
        private Map<String, Object> metadata;
        
        // Default constructor for JSON deserialization
        public OperatorEdge() {}
        
        // Builder pattern constructor
        public OperatorEdge(Builder builder) {
            this.fromNodeId = builder.fromNodeId;
            this.toNodeId = builder.toNodeId;
            this.edgeType = builder.edgeType;
            this.metadata = builder.metadata;
        }
        
        // Getters
        public String getFromNodeId() { return fromNodeId; }
        public String getToNodeId() { return toNodeId; }
        public EdgeType getEdgeType() { return edgeType; }
        public Map<String, Object> getMetadata() { return metadata; }
        
        // Setters
        public void setFromNodeId(String fromNodeId) { this.fromNodeId = fromNodeId; }
        public void setToNodeId(String toNodeId) { this.toNodeId = toNodeId; }
        public void setEdgeType(EdgeType edgeType) { this.edgeType = edgeType; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private String fromNodeId;
            private String toNodeId;
            private EdgeType edgeType = EdgeType.DATA_FLOW;
            private Map<String, Object> metadata;
            
            public Builder fromNodeId(String fromNodeId) { this.fromNodeId = fromNodeId; return this; }
            public Builder toNodeId(String toNodeId) { this.toNodeId = toNodeId; return this; }
            public Builder edgeType(EdgeType edgeType) { this.edgeType = edgeType; return this; }
            public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }
            
            public OperatorEdge build() {
                return new OperatorEdge(this);
            }
        }
        
        @Override
        public String toString() {
            return "OperatorEdge{" +
                    "fromNodeId='" + fromNodeId + '\'' +
                    ", toNodeId='" + toNodeId + '\'' +
                    ", edgeType=" + edgeType +
                    '}';
        }
    }
    
    /**
     * Types of edges in the operator DAG.
     */
    public enum EdgeType {
        /**
         * Data flow edge (normal processing flow).
         */
        DATA_FLOW("dataFlow"),
        
        /**
         * Control flow edge (conditional processing).
         */
        CONTROL_FLOW("controlFlow"),
        
        /**
         * Side effect edge (non-data processing).
         */
        SIDE_EFFECT("sideEffect");
        
        private final String value;
        
        EdgeType(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
        
        public static EdgeType fromValue(String value) {
            for (EdgeType type : values()) {
                if (type.value.equals(value)) {
                    return type;
                }
            }
            return null;
        }
    }
    
    @Override
    public String toString() {
        return "OperatorDAG{" +
                "nodeCount=" + (nodes != null ? nodes.size() : 0) +
                ", edgeCount=" + (edges != null ? edges.size() : 0) +
                ", rootNodeId='" + rootNodeId + '\'' +
                '}';
    }
}

