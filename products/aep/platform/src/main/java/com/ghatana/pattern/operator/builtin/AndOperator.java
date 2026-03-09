package com.ghatana.pattern.operator.builtin;

import com.ghatana.pattern.api.exception.PatternValidationException;
import com.ghatana.pattern.api.model.OperatorSpec;
import com.ghatana.pattern.operator.spi.Operator;
import com.ghatana.pattern.operator.spi.OperatorMetadata;
import com.ghatana.pattern.operator.spi.ValidationContext;

import java.util.Map;

/**
 * AND operator that matches when all operands match (conjunction).
 * 
 * <p>This operator matches when all specified events occur, regardless of order,
 * within an optional time window. For example, AND(A, B, C) matches when events
 * A, B, and C all occur (in any order).
 * 
 * @doc.pattern Strategy SPI Pattern - Pluggable operator implementation via {@link Operator} interface,
 *               enabling dynamic operator registration and polymorphic validation.
 * @doc.operator-type Pattern Composition Operator - Conjunction (AND) for unordered co-occurrence matching.
 *                    Part of the core pattern composition operators: SEQ, AND, OR, NOT. Unlike SEQ,
 *                    AND does not enforce temporal ordering.
 * @doc.statefulness Stateful Operator - Requires tracking which operands have matched and within what
 *                   time window. Cannot operate in stateless mode (supportsStateless=false). State includes
 *                   matched operand set, timestamps, and partial match tracking.
 * @doc.threading Thread-Safe Validation - Validation logic is stateless and thread-safe. Runtime execution
 *                (not implemented here) would require per-instance state isolation or external synchronization.
 * @doc.performance O(n) Validation Performance - Linear in number of operands for operand validation,
 *                  O(p) for parameter validation where p = parameter count. Typical validation time: <1ms
 *                  for 2-10 operands.
 * @doc.validation <strong>Operand Validation:</strong>
 *                 <ul>
 *                   <li><strong>Count:</strong> Minimum 2 operands (binary conjunction), no maximum</li>
 *                   <li><strong>Type:</strong> Each operand must have non-null, non-empty type identifier</li>
 *                   <li><strong>Order:</strong> Order-independent - all operands treated equally</li>
 *                 </ul>
 *                 <strong>Parameter Validation:</strong>
 *                 <table border="1">
 *                   <tr><th>Parameter</th><th>Type</th><th>Required</th><th>Constraint</th></tr>
 *                   <tr><td>maxTimeWindow</td><td>Number</td><td>No</td><td>&gt; 0 (milliseconds for all events to occur)</td></tr>
 *                   <tr><td>allowPartialMatch</td><td>Boolean</td><td>No</td><td>If true, emit partial matches before timeout</td></tr>
 *                 </table>
 * @doc.apiNote <strong>Usage in Pattern Specification:</strong>
 *              <pre>
 *              OperatorSpec.builder()
 *                  .type("AND")
 *                  .operands(Arrays.asList(loginSpec, ipChangeSpec, locationChangeSpec))
 *                  .parameter("maxTimeWindow", 300000L) // 5 minutes
 *                  .parameter("allowPartialMatch", false)
 *                  .build()
 *              </pre>
 *              AND is typically used for multi-factor detection (login + IP change + location change),
 *              compliance validation (all required approvals received), and correlated event detection.
 * @doc.limitation Compile-Time Only - This implementation validates pattern structure at compile time.
 *                 Runtime execution logic (partial match tracking, time window enforcement, event buffering)
 *                 is handled by the pattern engine's execution layer. Does not provide runtime matching logic.
 */
public class AndOperator implements Operator {
    
    private static final String TYPE = "AND";
    private static final OperatorMetadata METADATA = OperatorMetadata.builder()
            .type(TYPE)
            .description("AND operator that matches when all operands match (unordered)")
            .minOperands(2)
            .maxOperands(Integer.MAX_VALUE)
            .supportsStateful(true)
            .supportsStateless(false)
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
            throw new PatternValidationException("OperatorSpec cannot be null for AND operator");
        }
        
        if (!TYPE.equals(spec.getType())) {
            throw new PatternValidationException("Invalid operator type for AndOperator: " + spec.getType());
        }
        
        // Validate operand count
        int operandCount = spec.getOperandCount();
        if (operandCount < METADATA.getMinOperands()) {
            throw new PatternValidationException(
                String.format("AND operator requires at least %d operands, got %d", 
                    METADATA.getMinOperands(), operandCount));
        }
        
        if (operandCount > METADATA.getMaxOperands()) {
            throw new PatternValidationException(
                String.format("AND operator supports at most %d operands, got %d", 
                    METADATA.getMaxOperands(), operandCount));
        }
        
        // Validate operands
        if (spec.getOperands() != null) {
            for (int i = 0; i < spec.getOperands().size(); i++) {
                OperatorSpec operand = spec.getOperands().get(i);
                if (operand == null) {
                    throw new PatternValidationException(
                        String.format("AND operator operand %d cannot be null", i));
                }
                
                if (operand.getType() == null || operand.getType().trim().isEmpty()) {
                    throw new PatternValidationException(
                        String.format("AND operator operand %d must have a valid type", i));
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
        
        // Validate maxTimeWindow parameter if present
        Object maxTimeWindow = parameters.get("maxTimeWindow");
        if (maxTimeWindow != null) {
            if (!(maxTimeWindow instanceof Number)) {
                throw new PatternValidationException("AND operator maxTimeWindow parameter must be a number");
            }
            
            long maxTimeWindowMs = ((Number) maxTimeWindow).longValue();
            if (maxTimeWindowMs <= 0) {
                throw new PatternValidationException("AND operator maxTimeWindow parameter must be positive");
            }
        }
        
        // Validate allowPartialMatch parameter if present
        Object allowPartialMatch = parameters.get("allowPartialMatch");
        if (allowPartialMatch != null && !(allowPartialMatch instanceof Boolean)) {
            throw new PatternValidationException("AND operator allowPartialMatch parameter must be a boolean");
        }
    }
    
    @Override
    public boolean supports(OperatorSpec spec) {
        return TYPE.equals(spec.getType());
    }
}





