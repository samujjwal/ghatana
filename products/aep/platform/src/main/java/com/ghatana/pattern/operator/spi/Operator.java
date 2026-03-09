package com.ghatana.pattern.operator.spi;

import com.ghatana.pattern.api.model.OperatorSpec;
import com.ghatana.pattern.api.exception.PatternValidationException;

/**
 * Service Provider Interface (SPI) for pattern detection operators.
 * 
 * <p>This interface defines the contract for operators that can be used in pattern detection.
 * Operators are responsible for:
 * <ul>
 *   <li><b>Metadata</b>: Declaring operator capabilities and constraints</li>
 *   <li><b>Validation</b>: Compile-time validation of operator specifications</li>
 *   <li><b>Type Registry</b>: Registration with OperatorRegistry for discovery</li>
 * </ul>
 * 
 * <p><b>Important</b>: This SPI is for <b>compilation-time</b> operations only.
 * Actual execution logic is handled by the runtime engine (not part of this interface).
 * 
 * @doc.pattern Service Provider Interface (SPI), Strategy Pattern (pluggable operators)
 * @doc.compiler-phase Operator SPI (validation and metadata for compilation)
 * @doc.threading Thread-safe (stateless operators preferred)
 * @doc.performance O(1) metadata access; O(n) validation where n=operand count
 * @doc.apiNote Implement this interface to create custom operators; register with OperatorRegistry
 * @doc.limitation No runtime execution in SPI; see runtime engine for execution logic
 * 
 * <h2>Built-in Operator Types</h2>
 * <table border="1" cellpadding="5">
 *   <tr>
 *     <th>Type</th>
 *     <th>Category</th>
 *     <th>Operands</th>
 *     <th>Purpose</th>
 *   </tr>
 *   <tr>
 *     <td>SEQ</td>
 *     <td>Pattern</td>
 *     <td>2+</td>
 *     <td>Sequence matching (A followed by B)</td>
 *   </tr>
 *   <tr>
 *     <td>AND</td>
 *     <td>Pattern</td>
 *     <td>2+</td>
 *     <td>Conjunction (A and B simultaneously)</td>
 *   </tr>
 *   <tr>
 *     <td>OR</td>
 *     <td>Pattern</td>
 *     <td>2+</td>
 *     <td>Disjunction (A or B)</td>
 *   </tr>
 *   <tr>
 *     <td>NOT</td>
 *     <td>Pattern</td>
 *     <td>1</td>
 *     <td>Negation (absence of A)</td>
 *   </tr>
 *   <tr>
 *     <td>WITHIN</td>
 *     <td>Temporal</td>
 *     <td>1</td>
 *     <td>Time constraint (event within duration)</td>
 *   </tr>
 *   <tr>
 *     <td>REPEAT</td>
 *     <td>Quantifier</td>
 *     <td>1</td>
 *     <td>Repetition (A occurs n times)</td>
 *   </tr>
 *   <tr>
 *     <td>UNTIL</td>
 *     <td>Temporal</td>
 *     <td>2</td>
 *     <td>A continues until B occurs</td>
 *   </tr>
 *   <tr>
 *     <td>WINDOW</td>
 *     <td>Aggregation</td>
 *     <td>1</td>
 *     <td>Windowed aggregation</td>
 *   </tr>
 * </table>
 * 
 * <p><b>Design Reference:</b>
 * This SPI implements the Unified Operator Model from WORLD_CLASS_DESIGN_MASTER.md.
 * See .github/copilot-instructions.md "Unified Operator Model" for operator integration.
 */
public interface Operator {
    
    /**
     * Get the operator type identifier.
     * 
     * @return the operator type (e.g., "SEQ", "AND", "OR", "NOT", "WITHIN", "REPEAT", "WINDOW", "UNTIL")
     */
    String getType();
    
    /**
     * Get metadata about this operator for validation and compilation.
     * 
     * @return the operator metadata
     */
    OperatorMetadata getMetadata();
    
    /**
     * Validate the operator specification during compilation.
     * 
     * @param spec the operator specification to validate
     * @param context the validation context
     * @throws PatternValidationException if validation fails
     */
    void validate(OperatorSpec spec, ValidationContext context) throws PatternValidationException;
    
    /**
     * Check if this operator supports the given specification.
     * 
     * @param spec the operator specification
     * @return true if the operator supports the specification
     */
    default boolean supports(OperatorSpec spec) {
        return getType().equals(spec.getType());
    }
}





