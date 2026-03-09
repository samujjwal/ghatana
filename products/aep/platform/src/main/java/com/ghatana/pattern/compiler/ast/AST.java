package com.ghatana.pattern.compiler.ast;

import com.ghatana.pattern.api.model.OperatorSpec;

import java.util.List;
import java.util.Map;

/**
 * Abstract Syntax Tree (AST) representation of a pattern.
 * 
 * <p>The AST is an intermediate representation that captures the hierarchical structure
 * of the pattern in tree format, making it easier to analyze, transform, and optimize
 * before converting to the executable DAG representation.
 * 
 * @doc.pattern Composite Pattern (tree structure), Immutable Object Pattern
 * @doc.compiler-phase AST representation (between validation and DAG generation)
 * @doc.threading Thread-safe (immutable after construction)
 * @doc.performance O(1) root access; O(n) for tree traversal operations
 * @doc.memory O(n) space where n=operator count; recursive node storage
 * @doc.immutability Immutable after build(); all modifications create new AST instances
 * @doc.apiNote Use builder pattern for construction; traverse via getRoot().visit()
 * @doc.limitation No parent pointers; tree traversal is root-to-leaf only
 * 
 * <h2>AST Query Methods</h2>
 * <table border="1" cellpadding="5">
 *   <tr>
 *     <th>Method</th>
 *     <th>Complexity</th>
 *     <th>Purpose</th>
 *   </tr>
 *   <tr>
 *     <td>getDepth()</td>
 *     <td>O(n)</td>
 *     <td>Maximum tree depth (operator nesting level)</td>
 *   </tr>
 *   <tr>
 *     <td>getNodeCount()</td>
 *     <td>O(n)</td>
 *     <td>Total operator count in pattern</td>
 *   </tr>
 *   <tr>
 *     <td>getLeafNodes()</td>
 *     <td>O(n)</td>
 *     <td>Event selectors (source operators)</td>
 *   </tr>
 *   <tr>
 *     <td>getNodesByType()</td>
 *     <td>O(n)</td>
 *     <td>Find all operators of specific type</td>
 *   </tr>
 * </table>
 */
public class AST {
    
    private final ASTNode root;
    private final Map<String, Object> metadata;
    
    // Private constructor - use Builder
    private AST(Builder builder) {
        this.root = builder.root;
        this.metadata = builder.metadata;
    }
    
    // Getters
    public ASTNode getRoot() { return root; }
    public Map<String, Object> getMetadata() { return metadata; }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private ASTNode root;
        private Map<String, Object> metadata;
        
        public Builder root(ASTNode root) { this.root = root; return this; }
        public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }
        
        public AST build() {
            return new AST(this);
        }
    }
    
    /**
     * Get the depth of the AST.
     * 
     * @return the maximum depth of the tree
     */
    public int getDepth() {
        return root != null ? root.getDepth() : 0;
    }
    
    /**
     * Get the number of nodes in the AST.
     * 
     * @return the total number of nodes
     */
    public int getNodeCount() {
        return root != null ? root.getNodeCount() : 0;
    }
    
    /**
     * Get all leaf nodes in the AST.
     * 
     * @return a list of leaf nodes
     */
    public List<ASTNode> getLeafNodes() {
        return root != null ? root.getLeafNodes() : List.of();
    }
    
    /**
     * Get all nodes of a specific type.
     * 
     * @param type the node type to filter by
     * @return a list of nodes of the specified type
     */
    public List<ASTNode> getNodesByType(String type) {
        return root != null ? root.getNodesByType(type) : List.of();
    }
    
    @Override
    public String toString() {
        return "AST{" +
                "depth=" + getDepth() +
                ", nodeCount=" + getNodeCount() +
                ", root=" + (root != null ? root.getType() : "null") +
                '}';
    }
}





