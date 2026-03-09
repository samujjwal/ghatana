package com.ghatana.pattern.operator.registry;

import com.ghatana.pattern.api.exception.PatternValidationException;
import com.ghatana.pattern.api.model.OperatorSpec;
import com.ghatana.pattern.operator.spi.Operator;
import com.ghatana.pattern.operator.spi.OperatorMetadata;
import com.ghatana.pattern.operator.spi.ValidationContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for managing pattern detection operators with centralized discovery and validation.
 * 
 * <p>The OperatorRegistry provides:
 * <ul>
 *   <li><b>Registration</b>: Register built-in and custom operators via Operator SPI</li>
 *   <li><b>Discovery</b>: Query operators by type, list all types, retrieve metadata</li>
 *   <li><b>Validation</b>: Validate operator specifications against registered operators</li>
 *   <li><b>Thread-Safety</b>: Concurrent read/write access via ConcurrentHashMap</li>
 * </ul>
 * 
 * @doc.pattern Registry Pattern (operator lookup), Service Locator Pattern (operator discovery)
 * @doc.compiler-phase Operator Registry (operator discovery for validation and compilation)
 * @doc.threading Thread-safe (ConcurrentHashMap for concurrent access)
 * @doc.performance O(1) for register/lookup operations; O(n) for list/validate where n=operator count
 * @doc.memory O(n) where n=registered operator count
 * @doc.apiNote Register operators on startup; use during compilation for operator lookup
 * @doc.limitation No operator versioning; last registered operator wins for type conflicts
 * 
 * <h2>Operator Lifecycle</h2>
 * <pre>
 * // 1. Create registry
 * OperatorRegistry registry = new OperatorRegistry();
 * 
 * // 2. Register built-in operators
 * registry.register(new SeqOperator());
 * registry.register(new AndOperator());
 * registry.register(new OrOperator());
 * 
 * // 3. Register custom operators
 * registry.register(new CustomFilterOperator());
 * 
 * // 4. Query during compilation
 * Operator seqOp = registry.getOperator("SEQ");
 * OperatorMetadata metadata = registry.getMetadata("SEQ");
 * 
 * // 5. Validate operator spec
 * registry.validate(operatorSpec, validationContext);
 * </pre>
 * 
 * <p><b>Design Reference:</b>
 * This registry implements the Operator Catalog from WORLD_CLASS_DESIGN_MASTER.md.
 * See .github/copilot-instructions.md "Unified Operator Model" for operator integration.
 */
public class OperatorRegistry {
    
    private final Map<String, Operator> operators = new ConcurrentHashMap<>();
    private final Map<String, OperatorMetadata> metadata = new ConcurrentHashMap<>();
    
    /**
     * Register an operator with the registry.
     * 
     * @param operator the operator to register
     * @throws IllegalArgumentException if the operator is null or has an invalid type
     */
    public void register(Operator operator) {
        if (operator == null) {
            throw new IllegalArgumentException("Operator cannot be null");
        }
        
        String type = operator.getType();
        if (type == null || type.trim().isEmpty()) {
            throw new IllegalArgumentException("Operator type cannot be null or empty");
        }
        
        operators.put(type, operator);
        metadata.put(type, operator.getMetadata());
    }
    
    /**
     * Unregister an operator from the registry.
     * 
     * @param type the operator type to unregister
     * @return the unregistered operator, or null if not found
     */
    public Operator unregister(String type) {
        if (type == null || type.trim().isEmpty()) {
            return null;
        }
        
        metadata.remove(type);
        return operators.remove(type);
    }
    
    /**
     * Get an operator by type.
     * 
     * @param type the operator type
     * @return the operator, or null if not found
     */
    public Operator getOperator(String type) {
        if (type == null || type.trim().isEmpty()) {
            return null;
        }
        
        return operators.get(type);
    }
    
    /**
     * Get operator metadata by type.
     * 
     * @param type the operator type
     * @return the operator metadata, or null if not found
     */
    public OperatorMetadata getMetadata(String type) {
        if (type == null || type.trim().isEmpty()) {
            return null;
        }
        
        return metadata.get(type);
    }
    
    /**
     * Check if an operator type is supported.
     * 
     * @param type the operator type
     * @return true if the operator type is supported
     */
    public boolean supports(String type) {
        if (type == null || type.trim().isEmpty()) {
            return false;
        }
        
        return operators.containsKey(type);
    }
    
    /**
     * Get all registered operator types.
     * 
     * @return a set of all registered operator types
     */
    public Set<String> getSupportedTypes() {
        return new HashSet<>(operators.keySet());
    }
    
    /**
     * Get all registered operators.
     * 
     * @return a map of operator types to operators
     */
    public Map<String, Operator> getAllOperators() {
        return new HashMap<>(operators);
    }
    
    /**
     * Get all operator metadata.
     * 
     * @return a map of operator types to metadata
     */
    public Map<String, OperatorMetadata> getAllMetadata() {
        return new HashMap<>(metadata);
    }
    
    /**
     * Validate an operator specification using the registered operator.
     * 
     * @param spec the operator specification to validate
     * @param context the validation context
     * @throws PatternValidationException if validation fails
     */
    public void validate(OperatorSpec spec, ValidationContext context) throws PatternValidationException {
        if (spec == null) {
            throw new PatternValidationException("OperatorSpec cannot be null");
        }
        
        String type = spec.getType();
        if (type == null || type.trim().isEmpty()) {
            throw new PatternValidationException("Operator type cannot be null or empty");
        }
        
        Operator operator = getOperator(type);
        if (operator == null) {
            throw new PatternValidationException("Unsupported operator type: " + type);
        }
        
        operator.validate(spec, context);
    }
    
    /**
     * Check if an operator specification is supported.
     * 
     * @param spec the operator specification
     * @return true if the specification is supported
     */
    public boolean supports(OperatorSpec spec) {
        if (spec == null) {
            return false;
        }
        
        String type = spec.getType();
        if (type == null || type.trim().isEmpty()) {
            return false;
        }
        
        Operator operator = getOperator(type);
        return operator != null && operator.supports(spec);
    }
    
    /**
     * Get the number of registered operators.
     * 
     * @return the number of registered operators
     */
    public int size() {
        return operators.size();
    }
    
    /**
     * Check if the registry is empty.
     * 
     * @return true if the registry is empty
     */
    public boolean isEmpty() {
        return operators.isEmpty();
    }
    
    /**
     * Clear all registered operators.
     */
    public void clear() {
        operators.clear();
        metadata.clear();
    }
    
    /**
     * Create a new operator registry with built-in operators.
     *
     * @return a new operator registry with built-in operators
     */
    public static OperatorRegistry createDefault() {
        OperatorRegistry registry = new OperatorRegistry();

        // Register built-in operators
        registry.register(new com.ghatana.pattern.operator.builtin.PrimaryEventOperator());
        registry.register(new com.ghatana.pattern.operator.builtin.SeqOperator());
        registry.register(new com.ghatana.pattern.operator.builtin.AndOperator());
        registry.register(new com.ghatana.pattern.operator.builtin.OrOperator());
        registry.register(new com.ghatana.pattern.operator.builtin.NotOperator());
        registry.register(new com.ghatana.pattern.operator.builtin.WithinOperator());
        registry.register(new com.ghatana.pattern.operator.builtin.WindowOperator());
        registry.register(new com.ghatana.pattern.operator.builtin.RepeatOperator());
        registry.register(new com.ghatana.pattern.operator.builtin.UntilOperator());

        return registry;
    }
    
    @Override
    public String toString() {
        return "OperatorRegistry{" +
                "size=" + operators.size() +
                ", types=" + operators.keySet() +
                '}';
    }
}





