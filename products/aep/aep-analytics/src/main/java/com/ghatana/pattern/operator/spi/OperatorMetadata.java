package com.ghatana.pattern.operator.spi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Metadata about an operator for validation and compilation purposes.
 * 
 * <p>OperatorMetadata describes operator capabilities and constraints:
 * <ul>
 *   <li><b>Parameters</b>: Required and optional configuration parameters</li>
 *   <li><b>Operands</b>: Min/max child operator counts</li>
 *   <li><b>Statefulness</b>: Whether operator requires stateful/stateless execution</li>
 *   <li><b>Constraints</b>: Additional validation rules (type constraints, value ranges)</li>
 * </ul>
 * 
 * @doc.pattern Value Object Pattern (immutable metadata), Builder Pattern (construction)
 * @doc.compiler-phase Operator Metadata (validation constraints for compilation)
 * @doc.threading Thread-safe after construction (immutable value object)
 * @doc.performance O(1) for field access; O(p) for parameter lookups where p=parameter count
 * @doc.memory O(1) for fixed fields + O(p+c) where p=parameters, c=constraints
 * @doc.immutability Immutable after build(); use builder for modifications
 * @doc.apiNote Define metadata in operator constructor; use for compile-time validation
 * 
 * <h2>Parameter Types</h2>
 * <table border="1" cellpadding="5">
 *   <tr>
 *     <th>Operator</th>
 *     <th>Required Parameters</th>
 *     <th>Optional Parameters</th>
 *   </tr>
 *   <tr>
 *     <td>SEQ</td>
 *     <td>-</td>
 *     <td>within (Duration)</td>
 *   </tr>
 *   <tr>
 *     <td>REPEAT</td>
 *     <td>count (int) or min/max</td>
 *     <td>greedy (boolean)</td>
 *   </tr>
 *   <tr>
 *     <td>WITHIN</td>
 *     <td>duration (Duration)</td>
 *     <td>-</td>
 *   </tr>
 *   <tr>
 *     <td>WINDOW</td>
 *     <td>size (Duration)</td>
 *     <td>slide (Duration), type (WindowType)</td>
 *   </tr>
 * </table>
 * 
 * <p><b>Operand Constraints</b>:
 * <table border="1" cellpadding="5">
 *   <tr>
 *     <th>Operator</th>
 *     <th>Min Operands</th>
 *     <th>Max Operands</th>
 *     <th>Typical</th>
 *   </tr>
 *   <tr>
 *     <td>NOT</td>
 *     <td>1</td>
 *     <td>1</td>
 *     <td>Unary negation</td>
 *   </tr>
 *   <tr>
 *     <td>SEQ, AND, OR</td>
 *     <td>2</td>
 *     <td>∞</td>
 *     <td>N-ary composition</td>
 *   </tr>
 *   <tr>
 *     <td>REPEAT, WITHIN, WINDOW</td>
 *     <td>1</td>
 *     <td>1</td>
 *     <td>Unary transformation</td>
 *   </tr>
 *   <tr>
 *     <td>UNTIL</td>
 *     <td>2</td>
 *     <td>2</td>
 *     <td>Binary temporal</td>
 *   </tr>
 * </table>
 */
public class OperatorMetadata {
    
    private final String type;
    private final String description;
    private final List<String> requiredParameters;
    private final List<String> optionalParameters;
    private final int minOperands;
    private final int maxOperands;
    private final boolean supportsStateful;
    private final boolean supportsStateless;
    private final Map<String, Object> constraints;
    private final Map<String, Object> metadata;
    
    // Private constructor - use Builder
    private OperatorMetadata(Builder builder) {
        this.type = builder.type;
        this.description = builder.description;
        this.requiredParameters = builder.requiredParameters;
        this.optionalParameters = builder.optionalParameters;
        this.minOperands = builder.minOperands;
        this.maxOperands = builder.maxOperands;
        this.supportsStateful = builder.supportsStateful;
        this.supportsStateless = builder.supportsStateless;
        this.constraints = builder.constraints;
        this.metadata = builder.metadata;
    }
    
    // Getters
    public String getType() { return type; }
    public String getDescription() { return description; }
    public List<String> getRequiredParameters() { return requiredParameters; }
    public List<String> getOptionalParameters() { return optionalParameters; }
    public int getMinOperands() { return minOperands; }
    public int getMaxOperands() { return maxOperands; }
    public boolean isSupportsStateful() { return supportsStateful; }
    public boolean isSupportsStateless() { return supportsStateless; }
    public Map<String, Object> getConstraints() { return constraints; }
    public Map<String, Object> getMetadata() { return metadata; }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String type;
        private String description;
        private List<String> requiredParameters;
        private List<String> optionalParameters;
        private int minOperands = 0;
        private int maxOperands = Integer.MAX_VALUE;
        private boolean supportsStateful = false;
        private boolean supportsStateless = true;
        private Map<String, Object> constraints;
        private Map<String, Object> metadata;
        
        public Builder type(String type) { this.type = type; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder requiredParameters(List<String> requiredParameters) { this.requiredParameters = requiredParameters; return this; }
        public Builder optionalParameters(List<String> optionalParameters) { this.optionalParameters = optionalParameters; return this; }
        public Builder minOperands(int minOperands) { this.minOperands = minOperands; return this; }
        public Builder maxOperands(int maxOperands) { this.maxOperands = maxOperands; return this; }
        public Builder supportsStateful(boolean supportsStateful) { this.supportsStateful = supportsStateful; return this; }
        public Builder supportsStateless(boolean supportsStateless) { this.supportsStateless = supportsStateless; return this; }
        public Builder constraints(Map<String, Object> constraints) { this.constraints = constraints; return this; }
        public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }

        /**
         * Add a required parameter.
         *
         * @param parameterName the parameter name
         * @return this builder
         */
        public Builder requiredParameter(String parameterName) {
            if (this.requiredParameters == null) {
                this.requiredParameters = new ArrayList<>();
            }
            this.requiredParameters.add(parameterName);
            return this;
        }

        /**
         * Add an optional parameter.
         *
         * @param parameterName the parameter name
         * @return this builder
         */
        public Builder optionalParameter(String parameterName) {
            if (this.optionalParameters == null) {
                this.optionalParameters = new ArrayList<>();
            }
            this.optionalParameters.add(parameterName);
            return this;
        }

        /**
         * Add a constraint.
         *
         * @param key the constraint key
         * @param value the constraint value
         * @return this builder
         */
        public Builder constraint(String key, Object value) {
            if (this.constraints == null) {
                this.constraints = new HashMap<>();
            }
            this.constraints.put(key, value);
            return this;
        }

        /**
         * Add a metadata entry.
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

        public OperatorMetadata build() {
            return new OperatorMetadata(this);
        }
    }
    
    /**
     * Check if the operator requires a specific parameter.
     * 
     * @param parameterName the parameter name
     * @return true if the parameter is required
     */
    public boolean isRequiredParameter(String parameterName) {
        return requiredParameters != null && requiredParameters.contains(parameterName);
    }
    
    /**
     * Check if the operator supports a specific parameter.
     * 
     * @param parameterName the parameter name
     * @return true if the parameter is supported
     */
    public boolean isSupportedParameter(String parameterName) {
        return (requiredParameters != null && requiredParameters.contains(parameterName)) ||
               (optionalParameters != null && optionalParameters.contains(parameterName));
    }
    
    /**
     * Check if the operator supports the given number of operands.
     * 
     * @param operandCount the number of operands
     * @return true if the operand count is supported
     */
    public boolean supportsOperandCount(int operandCount) {
        return operandCount >= minOperands && operandCount <= maxOperands;
    }
    
    /**
     * Get a constraint value by key.
     * 
     * @param key the constraint key
     * @return the constraint value, or null if not found
     */
    public Object getConstraint(String key) {
        return constraints != null ? constraints.get(key) : null;
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
        return "OperatorMetadata{" +
                "type='" + type + '\'' +
                ", description='" + description + '\'' +
                ", minOperands=" + minOperands +
                ", maxOperands=" + maxOperands +
                ", supportsStateful=" + supportsStateful +
                ", supportsStateless=" + supportsStateless +
                '}';
    }
}





