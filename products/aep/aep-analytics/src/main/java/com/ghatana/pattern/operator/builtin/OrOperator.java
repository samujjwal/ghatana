package com.ghatana.pattern.operator.builtin;

import com.ghatana.pattern.api.exception.PatternValidationException;
import com.ghatana.pattern.api.model.OperatorSpec;
import com.ghatana.pattern.operator.spi.Operator;
import com.ghatana.pattern.operator.spi.OperatorMetadata;
import com.ghatana.pattern.operator.spi.ValidationContext;

import java.util.Map;

/**
 * OR operator that matches when any operand matches (disjunction).
 * 
 * <p>This operator matches when at least one of the specified events occurs.
 * For example, OR(A, B, C) matches when event A or B or C occurs. Supports
 * exclusive OR (XOR) semantics via the {@code exclusive} parameter.
 * 
 * @doc.pattern Strategy SPI Pattern - Pluggable operator implementation via {@link Operator} interface,
 *               enabling dynamic operator registration and polymorphic validation.
 * @doc.operator-type Pattern Composition Operator - Disjunction (OR) for alternative matching.
 *                    Part of the core pattern composition operators: SEQ, AND, OR, NOT. Matches on
 *                    first occurrence of any operand (non-exclusive) or exactly one operand (exclusive XOR).
 * @doc.statefulness Stateless Operator - Does NOT require cross-event state tracking. Each event is evaluated
 *                   independently against operands. Cannot operate in stateful mode (supportsStateful=false).
 *                   This is the only stateless composition operator in the builtin set.
 * @doc.threading Thread-Safe Validation - Validation logic is stateless and thread-safe. Runtime execution
 *                (not implemented here) is also inherently thread-safe due to stateless nature - each event
 *                evaluation is independent.
 * @doc.performance O(n) Validation Performance - Linear in number of operands for operand validation,
 *                  O(p) for parameter validation where p = parameter count. Typical validation time: <1ms
 *                  for 2-10 operands. Runtime matching is O(n) per event (check each operand).
 * @doc.validation <strong>Operand Validation:</strong>
 *                 <ul>
 *                   <li><strong>Count:</strong> Minimum 2 operands (binary disjunction), no maximum</li>
 *                   <li><strong>Type:</strong> Each operand must have non-null, non-empty type identifier</li>
 *                   <li><strong>Order:</strong> Order may affect priority-based matching if priority parameter set</li>
 *                 </ul>
 *                 <strong>Parameter Validation:</strong>
 *                 <table border="1">
 *                   <tr><th>Parameter</th><th>Type</th><th>Required</th><th>Constraint</th></tr>
 *                   <tr><td>exclusive</td><td>Boolean</td><td>No</td><td>If true, match only if exactly one operand matches (XOR)</td></tr>
 *                   <tr><td>priority</td><td>Number</td><td>No</td><td>≥ 0 (operand evaluation priority, higher = first)</td></tr>
 *                 </table>
 * @doc.apiNote <strong>Usage in Pattern Specification:</strong>
 *              <pre>
 *              // Non-exclusive OR (any match)
 *              OperatorSpec.builder()
 *                  .type("OR")
 *                  .operands(Arrays.asList(creditCardSpec, debitCardSpec, cashSpec))
 *                  .parameter("exclusive", false)
 *                  .build()
 *              
 *              // Exclusive OR (XOR - exactly one)
 *              OperatorSpec.builder()
 *                  .type("OR")
 *                  .operands(Arrays.asList(emailAuthSpec, smsAuthSpec))
 *                  .parameter("exclusive", true)
 *                  .build()
 *              </pre>
 *              OR is typically used for alternative event sources (multiple payment methods),
 *              fallback scenarios (primary or backup service), and mutually exclusive states.
 * @doc.limitation Compile-Time Only - This implementation validates pattern structure at compile time.
 *                 Runtime execution logic (XOR tracking, priority-based evaluation) is handled by the
 *                 pattern engine's execution layer. Does not provide runtime matching logic.
 */
public class OrOperator implements Operator {
    
    private static final String TYPE = "OR";
    private static final OperatorMetadata METADATA = OperatorMetadata.builder()
            .type(TYPE)
            .description("OR operator that matches when at least one operand matches")
            .minOperands(2)
            .maxOperands(Integer.MAX_VALUE)
            .supportsStateful(false)
            .supportsStateless(true)
            .build();
    
    @Override
    public String getType() {
        return TYPE;
    }
    
    @Override
    public OperatorMetadata getMetadata() {
        return METADATA;
    }
    
    @Override
    public void validate(OperatorSpec spec, ValidationContext context) throws PatternValidationException {
        if (spec == null) {
            throw new PatternValidationException("OperatorSpec cannot be null for OR operator");
        }
        
        if (!TYPE.equals(spec.getType())) {
            throw new PatternValidationException("Invalid operator type for OrOperator: " + spec.getType());
        }
        
        // Validate operand count
        int operandCount = spec.getOperandCount();
        if (operandCount < METADATA.getMinOperands()) {
            throw new PatternValidationException(
                String.format("OR operator requires at least %d operands, got %d", 
                    METADATA.getMinOperands(), operandCount));
        }
        
        if (operandCount > METADATA.getMaxOperands()) {
            throw new PatternValidationException(
                String.format("OR operator supports at most %d operands, got %d", 
                    METADATA.getMaxOperands(), operandCount));
        }
        
        // Validate operands
        if (spec.getOperands() != null) {
            for (int i = 0; i < spec.getOperands().size(); i++) {
                OperatorSpec operand = spec.getOperands().get(i);
                if (operand == null) {
                    throw new PatternValidationException(
                        String.format("OR operator operand %d cannot be null", i));
                }
                
                if (operand.getType() == null || operand.getType().trim().isEmpty()) {
                    throw new PatternValidationException(
                        String.format("OR operator operand %d must have a valid type", i));
                }
            }
        }
        
        // Validate parameters
        validateParameters(spec, context);
    }
    
    private void validateParameters(OperatorSpec spec, ValidationContext context) throws PatternValidationException {
        Map<String, Object> parameters = spec.getParameters();
        if (parameters == null) {
            return;
        }
        
        // Validate exclusive parameter if present
        Object exclusive = parameters.get("exclusive");
        if (exclusive != null && !(exclusive instanceof Boolean)) {
            throw new PatternValidationException("OR operator exclusive parameter must be a boolean");
        }
        
        // Validate priority parameter if present
        Object priority = parameters.get("priority");
        if (priority != null) {
            if (!(priority instanceof Number)) {
                throw new PatternValidationException("OR operator priority parameter must be a number");
            }
            
            int priorityValue = ((Number) priority).intValue();
            if (priorityValue < 0) {
                throw new PatternValidationException("OR operator priority parameter must be non-negative");
            }
        }
    }
    
    @Override
    public boolean supports(OperatorSpec spec) {
        return TYPE.equals(spec.getType());
    }
}





