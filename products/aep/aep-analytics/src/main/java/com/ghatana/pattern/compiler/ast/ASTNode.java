package com.ghatana.pattern.compiler.ast;

import com.ghatana.pattern.api.model.OperatorSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Represents a node in the Abstract Syntax Tree (AST) for pattern compilation.
 * 
 * <p>Each ASTNode represents a single operator in the pattern, with references to
 * its children (operator inputs) and metadata about the operator configuration.
 * Nodes are organized hierarchically with the root node representing the final
 * output operator.
 * 
 * @doc.pattern Composite Pattern (tree structure), Builder Pattern (construction)
 * @doc.compiler-phase AST Node (building block of AST representation)
 * @doc.threading Thread-safe after construction (defensive copying of children)
 * @doc.performance O(1) for field access; O(c) for child operations where c=child count
 * @doc.memory O(1) per node + O(c) for children list
 * @doc.immutability Immutable after construction via builder; children list defensively copied
 * @doc.apiNote Use builder pattern for construction; access children via getChildren()
 * @doc.limitation No parent pointer; tree traversal is root-to-leaf only
 * 
 * <h2>Node Classification</h2>
 * <table border="1" cellpadding="5">
 *   <tr>
 *     <th>Node Type</th>
 *     <th>Children Count</th>
 *     <th>Example Operators</th>
 *     <th>Purpose</th>
 *   </tr>
 *   <tr>
 *     <td>Leaf</td>
 *     <td>0</td>
 *     <td>Event selectors</td>
 *     <td>Source of event stream</td>
 *   </tr>
 *   <tr>
 *     <td>Unary</td>
 *     <td>1</td>
 *     <td>Filter, Map, Window</td>
 *     <td>Transform single stream</td>
 *   </tr>
 *   <tr>
 *     <td>Binary</td>
 *     <td>2</td>
 *     <td>Join, Correlation</td>
 *     <td>Combine two streams</td>
 *   </tr>
 *   <tr>
 *     <td>N-ary</td>
 *     <td>n</td>
 *     <td>Sequence, Aggregation</td>
 *     <td>Combine multiple streams</td>
 *   </tr>
 * </table>
 */
public class ASTNode {
    
    private final String type;
    private final String id;
    private final OperatorSpec operatorSpec;
    private final List<ASTNode> children;
    private final Map<String, Object> metadata;
    private final int depth;
    
    // Private constructor - use Builder
    private ASTNode(Builder builder) {
        this.type = builder.type;
        this.id = builder.id;
        this.operatorSpec = builder.operatorSpec;
        this.children = builder.children != null ? new ArrayList<>(builder.children) : new ArrayList<>();
        this.metadata = builder.metadata;
        this.depth = builder.depth;
    }
    
    // Getters
    public String getType() { return type; }
    public String getId() { return id; }
    public OperatorSpec getOperatorSpec() { return operatorSpec; }
    public List<ASTNode> getChildren() { return new ArrayList<>(children); }
    public Map<String, Object> getMetadata() { return metadata; }
    public int getDepth() { return depth; }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String type;
        private String id;
        private OperatorSpec operatorSpec;
        private List<ASTNode> children;
        private Map<String, Object> metadata;
        private int depth = 0;
        
        public Builder type(String type) { this.type = type; return this; }
        public Builder id(String id) { this.id = id; return this; }
        public Builder operatorSpec(OperatorSpec operatorSpec) { this.operatorSpec = operatorSpec; return this; }
        public Builder children(List<ASTNode> children) { this.children = children; return this; }
        public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }
        public Builder depth(int depth) { this.depth = depth; return this; }
        
        public ASTNode build() {
            return new ASTNode(this);
        }
    }
    
    /**
     * Add a child node to this node.
     * 
     * @param child the child node to add
     */
    public void addChild(ASTNode child) {
        if (child != null) {
            children.add(child);
        }
    }
    
    /**
     * Remove a child node from this node.
     * 
     * @param child the child node to remove
     * @return true if the child was removed
     */
    public boolean removeChild(ASTNode child) {
        return children.remove(child);
    }
    
    /**
     * Check if this node is a leaf node (has no children).
     * 
     * @return true if this node is a leaf
     */
    public boolean isLeaf() {
        return children.isEmpty();
    }
    
    /**
     * Check if this node is a root node (depth is 0).
     * 
     * @return true if this node is a root
     */
    public boolean isRoot() {
        return depth == 0;
    }
    
    /**
     * Get the number of children.
     * 
     * @return the number of children
     */
    public int getChildCount() {
        return children.size();
    }
    
    /**
     * Get the maximum depth of the subtree rooted at this node.
     * 
     * @return the maximum depth
     */
    public int getMaxDepth() {
        if (children.isEmpty()) {
            return depth;
        }
        
        return children.stream()
                .mapToInt(ASTNode::getMaxDepth)
                .max()
                .orElse(depth);
    }
    
    /**
     * Get the total number of nodes in the subtree rooted at this node.
     * 
     * @return the total number of nodes
     */
    public int getNodeCount() {
        return 1 + children.stream()
                .mapToInt(ASTNode::getNodeCount)
                .sum();
    }
    
    /**
     * Get all leaf nodes in the subtree rooted at this node.
     * 
     * @return a list of leaf nodes
     */
    public List<ASTNode> getLeafNodes() {
        if (isLeaf()) {
            return List.of(this);
        }
        
        return children.stream()
                .flatMap(child -> child.getLeafNodes().stream())
                .collect(Collectors.toList());
    }
    
    /**
     * Get all nodes of a specific type in the subtree rooted at this node.
     * 
     * @param type the node type to filter by
     * @return a list of nodes of the specified type
     */
    public List<ASTNode> getNodesByType(String type) {
        List<ASTNode> result = new ArrayList<>();
        
        if (this.type.equals(type)) {
            result.add(this);
        }
        
        children.forEach(child -> result.addAll(child.getNodesByType(type)));
        
        return result;
    }
    
    /**
     * Get a parameter value from the operator specification.
     * 
     * @param key the parameter key
     * @return the parameter value, or null if not found
     */
    public Object getParameter(String key) {
        return operatorSpec != null ? operatorSpec.getParameter(key) : null;
    }
    
    /**
     * Get a parameter value from the operator specification with a default value.
     * 
     * @param key the parameter key
     * @param defaultValue the default value to return if not found
     * @return the parameter value or the default value
     */
    public Object getParameter(String key, Object defaultValue) {
        return operatorSpec != null ? operatorSpec.getParameter(key, defaultValue) : defaultValue;
    }
    
    /**
     * Get a metadata value by key.
     * 
     * @param key the metadata key
     * @return the metadata value, or null if not found
     */
    public Object getMetadata(String key) {
        return metadata != null ? metadata.get(key) : null;
    }
    
    @Override
    public String toString() {
        return "ASTNode{" +
                "type='" + type + '\'' +
                ", id='" + id + '\'' +
                ", childCount=" + getChildCount() +
                ", depth=" + depth +
                '}';
    }
}





