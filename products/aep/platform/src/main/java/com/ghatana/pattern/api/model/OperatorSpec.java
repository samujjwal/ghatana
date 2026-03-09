package com.ghatana.pattern.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a specification for an operator in the pattern detection tree.
 * 
 * <p>OperatorSpec defines a single operator node within a pattern specification.
 * Operators are organized hierarchically with child operands, forming a tree structure
 * that the compiler converts to an executable DAG.
 * 
 * @doc.pattern Composite Pattern (tree structure), Builder Pattern (construction)
 * @doc.compiler-phase OperatorSpec (input to AST building phase)
 * @doc.threading Thread-safe after construction (immutable specification)
 * @doc.performance O(1) for field access; O(c) for operand access where c=child count
 * @doc.memory O(1) per operator + O(c) for operands list
 * @doc.immutability Immutable after build(); use builder for modifications
 * @doc.serialization JSON serializable via Jackson; supports recursive operator trees
 * @doc.apiNote Use builder pattern; nest operands for tree structure
 * 
 * <h2>Operator Types</h2>
 * <table border="1" cellpadding="5">
 *   <tr>
 *     <th>Category</th>
 *     <th>Operator Types</th>
 *     <th>Operand Count</th>
 *   </tr>
 *   <tr>
 *     <td>Event Selectors</td>
 *     <td>SELECT, FILTER</td>
 *     <td>0 (leaf nodes)</td>
 *   </tr>
 *   <tr>
 *     <td>Stream Operators</td>
 *     <td>MAP, WINDOW, AGGREGATE</td>
 *     <td>1 (unary)</td>
 *   </tr>
 *   <tr>
 *     <td>Pattern Operators</td>
 *     <td>SEQ, AND, OR, NOT</td>
 *     <td>1-n (n-ary)</td>
 *   </tr>
 *   <tr>
 *     <td>Correlation</td>
 *     <td>JOIN, CORRELATE</td>
 *     <td>2 (binary)</td>
 *   </tr>
 * </table>
 * 
 * <p><b>Example Operator Tree</b>:
 * <pre>
 * OperatorSpec.builder()
 *   .type("SEQ")
 *   .id("pattern-root")
 *   .operand(
 *     OperatorSpec.builder()
 *       .type("SELECT")
 *       .id("event-login")
 *       .parameter("eventType", "user.login")
 *       .build()
 *   )
 *   .operand(
 *     OperatorSpec.builder()
 *       .type("SELECT")
 *       .id("event-purchase")
 *       .parameter("eventType", "order.created")
 *       .build()
 *   )
 *   .parameter("within", "PT5M")
 *   .build()
 * </pre>
 */
public class OperatorSpec {
    
    @JsonProperty("type")
    private String type;
    
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("operands")
    private List<OperatorSpec> operands;
    
    @JsonProperty("parameters")
    private Map<String, Object> parameters;
    
    @JsonProperty("metadata")
    private Map<String, Object> metadata;
    
    // Default constructor for JSON deserialization
    public OperatorSpec() {}
    
    // Builder pattern constructor
    public OperatorSpec(Builder builder) {
        this.type = builder.type;
        this.id = builder.id;
        this.operands = builder.operands;
        this.parameters = builder.parameters;
        this.metadata = builder.metadata;
    }
    
    // Getters
    public String getType() { return type; }
    public String getId() { return id; }
    public List<OperatorSpec> getOperands() { return operands; }
    public Map<String, Object> getParameters() { return parameters; }
    public Map<String, Object> getMetadata() { return metadata; }
    
    // Setters
    public void setType(String type) { this.type = type; }
    public void setId(String id) { this.id = id; }
    public void setOperands(List<OperatorSpec> operands) { this.operands = operands; }
    public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String type;
        private String id;
        private List<OperatorSpec> operands;
        private Map<String, Object> parameters;
        private Map<String, Object> metadata;
        
        public Builder type(String type) { this.type = type; return this; }
        public Builder id(String id) { this.id = id; return this; }
        public Builder operands(List<OperatorSpec> operands) { this.operands = operands; return this; }
        public Builder parameters(Map<String, Object> parameters) { this.parameters = parameters; return this; }
        public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }

        /**
         * Add a single operand to the operands list.
         *
         * @param operand the operand to add
         * @return this builder
         */
        public Builder operand(OperatorSpec operand) {
            if (this.operands == null) {
                this.operands = new ArrayList<>();
            }
            this.operands.add(operand);
            return this;
        }

        /**
         * Add a single parameter.
         *
         * @param key the parameter key
         * @param value the parameter value
         * @return this builder
         */
        public Builder parameter(String key, Object value) {
            if (this.parameters == null) {
                this.parameters = new HashMap<>();
            }
            this.parameters.put(key, value);
            return this;
        }

        /**
         * Add a single metadata entry.
         *
         * @param key the metadata key
         * @param value the metadata value
         * @return this builder
         */
        public Builder metadataEntry(String key, Object value) {
            if (this.metadata == null) {
                this.metadata = new HashMap<>();
            }
            this.metadata.put(key, value);
            return this;
        }

        public OperatorSpec build() {
            return new OperatorSpec(this);
        }
    }
    
    /**
     * Check if this operator has operands (is a composite operator).
     * 
     * @return true if the operator has operands
     */
    public boolean hasOperands() {
        return operands != null && !operands.isEmpty();
    }
    
    /**
     * Get the number of operands.
     * 
     * @return the number of operands
     */
    public int getOperandCount() {
        return operands != null ? operands.size() : 0;
    }
    
    /**
     * Get a parameter value by key.
     * 
     * @param key the parameter key
     * @return the parameter value, or null if not found
     */
    public Object getParameter(String key) {
        return parameters != null ? parameters.get(key) : null;
    }
    
    /**
     * Get a parameter value by key with a default value.
     * 
     * @param key the parameter key
     * @param defaultValue the default value to return if not found
     * @return the parameter value or the default value
     */
    public Object getParameter(String key, Object defaultValue) {
        Object value = getParameter(key);
        return value != null ? value : defaultValue;
    }
    
    @Override
    public String toString() {
        return "OperatorSpec{" +
                "type='" + type + '\'' +
                ", id='" + id + '\'' +
                ", operandCount=" + getOperandCount() +
                '}';
    }
}

