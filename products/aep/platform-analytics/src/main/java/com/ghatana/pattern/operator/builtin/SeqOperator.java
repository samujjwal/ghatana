package com.ghatana.pattern.operator.builtin;

import com.ghatana.pattern.api.exception.PatternValidationException;
import com.ghatana.pattern.api.model.OperatorSpec;
import com.ghatana.pattern.operator.spi.Operator;
import com.ghatana.pattern.operator.spi.OperatorMetadata;
import com.ghatana.pattern.operator.spi.ValidationContext;

import java.util.List;
import java.util.Map;

/**
 * SEQ operator that matches events in sequence.
 * 
 * <p>This operator matches when events occur in the specified sequential order.
 * For example, SEQ(A, B) matches when event A occurs, followed by event B.
 * 
 * @doc.pattern Strategy SPI Pattern - Pluggable operator implementation via {@link Operator} interface,
 *               enabling dynamic operator registration and polymorphic validation.
 * @doc.operator-type Pattern Composition Operator - Sequential matching (SEQ) for strict temporal ordering.
 *                    Part of the core pattern composition operators: SEQ, AND, OR, NOT.
 * @doc.statefulness Stateful Operator - Requires tracking event occurrences and their order across time.
 *                   Cannot operate in stateless mode (supportsStateless=false). State includes matched
 *                   events, timestamps, and sequence position.
 * @doc.threading Thread-Safe Validation - Validation logic is stateless and thread-safe. Runtime execution
 *                (not implemented here) would require per-instance state isolation or external synchronization.
 * @doc.performance O(n) Validation Performance - Linear in number of operands for operand validation,
 *                  O(p) for parameter validation where p = parameter count. Typical validation time: <1ms
 *                  for 2-10 operands.
 * @doc.validation <strong>Operand Validation:</strong>
 *                 <ul>
 *                   <li><strong>Count:</strong> Minimum 2 operands (binary sequence), no maximum</li>
 *                   <li><strong>Type:</strong> Each operand must have non-null, non-empty type identifier</li>
 *                 </ul>
 *                 <strong>Parameter Validation:</strong>
 *                 <table border="1">
 *                   <tr><th>Parameter</th><th>Type</th><th>Required</th><th>Constraint</th></tr>
 *                   <tr><td>maxGap</td><td>Number</td><td>No</td><td>≥ 0 (milliseconds between events)</td></tr>
 *                   <tr><td>strictOrder</td><td>Boolean</td><td>No</td><td>If true, no intervening events allowed</td></tr>
 *                 </table>
 * @doc.apiNote <strong>Usage in Pattern Specification:</strong>
 *              <pre>
 *              OperatorSpec.builder()
 *                  .type("SEQ")
 *                  .operands(Arrays.asList(loginFailedSpec, transactionSpec))
 *                  .parameter("maxGap", 60000L) // 1 minute max gap
 *                  .parameter("strictOrder", true)
 *                  .build()
 *              </pre>
 *              SEQ is typically used for fraud detection (login-fail → high-value transaction),
 *              workflow validation (step1 → step2 → step3), and temporal causality patterns.
 * @doc.limitation Compile-Time Only - This implementation validates pattern structure at compile time.
 *                 Runtime execution logic (state management, event buffering, gap checking) is handled
 *                 by the pattern engine's execution layer. Does not provide runtime matching logic.
 */
public class SeqOperator implements Operator {
    
    private static final String TYPE = "SEQ";
    private static final OperatorMetadata METADATA = OperatorMetadata.builder()
            .type(TYPE)
            .description("Sequence operator that matches events in strict sequential order")
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
            throw new PatternValidationException("OperatorSpec cannot be null for SEQ operator");
        }
        
        if (!TYPE.equals(spec.getType())) {
            throw new PatternValidationException("Invalid operator type for SeqOperator: " + spec.getType());
        }
        
        // Validate operand count
        int operandCount = spec.getOperandCount();
        if (operandCount < METADATA.getMinOperands()) {
            throw new PatternValidationException(
                String.format("SEQ operator requires at least %d operands, got %d", 
                    METADATA.getMinOperands(), operandCount));
        }
        
        if (operandCount > METADATA.getMaxOperands()) {
            throw new PatternValidationException(
                String.format("SEQ operator supports at most %d operands, got %d", 
                    METADATA.getMaxOperands(), operandCount));
        }
        
        // Validate operands
        if (spec.getOperands() != null) {
            for (int i = 0; i < spec.getOperands().size(); i++) {
                OperatorSpec operand = spec.getOperands().get(i);
                if (operand == null) {
                    throw new PatternValidationException(
                        String.format("SEQ operator operand %d cannot be null", i));
                }
                
                if (operand.getType() == null || operand.getType().trim().isEmpty()) {
                    throw new PatternValidationException(
                        String.format("SEQ operator operand %d must have a valid type", i));
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
        
        // Validate maxGap parameter if present
        Object maxGap = parameters.get("maxGap");
        if (maxGap != null) {
            if (!(maxGap instanceof Number)) {
                throw new PatternValidationException("SEQ operator maxGap parameter must be a number");
            }
            
            long maxGapMs = ((Number) maxGap).longValue();
            if (maxGapMs < 0) {
                throw new PatternValidationException("SEQ operator maxGap parameter must be non-negative");
            }
        }
        
        // Validate strictOrder parameter if present
        Object strictOrder = parameters.get("strictOrder");
        if (strictOrder != null && !(strictOrder instanceof Boolean)) {
            throw new PatternValidationException("SEQ operator strictOrder parameter must be a boolean");
        }
    }
    
    @Override
    public boolean supports(OperatorSpec spec) {
        return TYPE.equals(spec.getType());
    }
}





