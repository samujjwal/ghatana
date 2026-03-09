package com.ghatana.pattern.operator.builtin;

import com.ghatana.pattern.api.exception.PatternValidationException;
import com.ghatana.pattern.api.model.OperatorSpec;
import com.ghatana.pattern.operator.spi.Operator;
import com.ghatana.pattern.operator.spi.OperatorMetadata;
import com.ghatana.pattern.operator.spi.ValidationContext;

import java.util.Map;

/**
 * UNTIL operator that matches until a termination condition is met.
 * 
 * <p>This operator matches when its first operand occurs until the second operand (termination
 * condition) occurs. For example, UNTIL(A, B) matches when event A repeats until event B occurs,
 * then terminates the pattern. Essential for conditional termination and unbounded matching.
 * 
 * @doc.pattern Strategy SPI Pattern - Pluggable operator implementation via {@link Operator} interface,
 *               enabling dynamic operator registration and polymorphic validation.
 * @doc.operator-type Temporal Termination Operator - Conditional termination (UNTIL) for unbounded
 *                    matching with explicit stop condition. Matches first operand repeatedly until
 *                    second operand (termination) occurs. Binary operator: main pattern + terminator.
 * @doc.statefulness Stateful Operator - Requires tracking matched events, timestamps, and waiting for
 *                   termination condition. Cannot operate in stateless mode (supportsStateless=false).
 *                   State includes partial matches, termination detection, and optional time bounds.
 * @doc.threading Thread-Safe Validation - Validation logic is stateless and thread-safe. Runtime execution
 *                (not implemented here) would require per-instance state isolation or external synchronization
 *                for termination tracking and partial match buffering.
 * @doc.performance O(1) Validation Performance - Fixed 2-operand validation, O(p) for parameter validation
 *                  where p = parameter count. Typical validation time: <1ms. Runtime performance depends
 *                  on termination latency (may buffer many events before termination).
 * @doc.validation <strong>Operand Validation:</strong>
 *                 <ul>
 *                   <li><strong>Count:</strong> Exactly 2 operands (binary: main pattern + terminator)</li>
 *                   <li><strong>Type:</strong> Both operands must have non-null, non-empty type identifiers</li>
 *                   <li><strong>Semantics:</strong> Operand 0 = repeating pattern, Operand 1 = termination condition</li>
 *                 </ul>
 *                 <strong>Parameter Validation:</strong>
 *                 <table border="1">
 *                   <tr><th>Parameter</th><th>Type</th><th>Required</th><th>Constraint</th></tr>
 *                   <tr><td>maxTimeWindow</td><td>Number</td><td>No</td><td>&gt; 0 (milliseconds before forced termination)</td></tr>
 *                   <tr><td>inclusive</td><td>Boolean</td><td>No</td><td>If true, include terminator in match result</td></tr>
 *                   <tr><td>strict</td><td>Boolean</td><td>No</td><td>If true, fail if terminator never occurs</td></tr>
 *                   <tr><td>allowEmpty</td><td>Boolean</td><td>No</td><td>If true, match even if operand 0 never occurs (only terminator)</td></tr>
 *                 </table>
 * @doc.apiNote <strong>Usage in Pattern Specification:</strong>
 *              <pre>
 *              // Match login attempts until success (inclusive terminator)
 *              OperatorSpec.builder()
 *                  .type("UNTIL")
 *                  .operands(Arrays.asList(loginAttemptSpec, loginSuccessSpec))
 *                  .parameter("maxTimeWindow", 300000L) // 5 minutes
 *                  .parameter("inclusive", true)
 *                  .parameter("strict", false)
 *                  .build()
 *              
 *              // Match API calls until rate limit error (strict)
 *              OperatorSpec.builder()
 *                  .type("UNTIL")
 *                  .operands(Arrays.asList(apiCallSpec, rateLimitErrorSpec))
 *                  .parameter("maxTimeWindow", 60000L) // 1 minute
 *                  .parameter("inclusive", false)
 *                  .parameter("strict", true)
 *                  .parameter("allowEmpty", false)
 *                  .build()
 *              </pre>
 *              UNTIL is typically used for session tracking (events until logout), workflow completion
 *              (steps until final approval), and bounded iteration (attempts until success or timeout).
 * @doc.limitation Compile-Time Only - This implementation validates pattern structure at compile time.
 *                 Runtime execution logic (partial match buffering, termination detection, time window
 *                 enforcement, inclusive/exclusive terminator handling) is handled by the pattern engine's
 *                 execution layer. Does not provide runtime matching logic.
 */
public class UntilOperator implements Operator {
    
    private static final String TYPE = "UNTIL";
    private static final OperatorMetadata METADATA = OperatorMetadata.builder()
            .type(TYPE)
            .description("UNTIL operator that matches until a condition is met")
            .minOperands(2)
            .maxOperands(2)
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
            throw new PatternValidationException("OperatorSpec cannot be null for UNTIL operator");
        }
        
        if (!TYPE.equals(spec.getType())) {
            throw new PatternValidationException("Invalid operator type for UntilOperator: " + spec.getType());
        }
        
        // Validate operand count
        int operandCount = spec.getOperandCount();
        if (operandCount != 2) {
            throw new PatternValidationException(
                String.format("UNTIL operator requires exactly 2 operands, got %d", operandCount));
        }
        
        // Validate operands
        if (spec.getOperands() != null && spec.getOperands().size() == 2) {
            OperatorSpec operand1 = spec.getOperands().get(0);
            OperatorSpec operand2 = spec.getOperands().get(1);
            
            if (operand1 == null) {
                throw new PatternValidationException("UNTIL operator first operand cannot be null");
            }
            
            if (operand2 == null) {
                throw new PatternValidationException("UNTIL operator second operand cannot be null");
            }
            
            if (operand1.getType() == null || operand1.getType().trim().isEmpty()) {
                throw new PatternValidationException("UNTIL operator first operand must have a valid type");
            }
            
            if (operand2.getType() == null || operand2.getType().trim().isEmpty()) {
                throw new PatternValidationException("UNTIL operator second operand must have a valid type");
            }
        } else {
            throw new PatternValidationException("UNTIL operator must have exactly two operands");
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
                throw new PatternValidationException("UNTIL operator maxTimeWindow parameter must be a number");
            }
            
            long maxTimeWindowMs = ((Number) maxTimeWindow).longValue();
            if (maxTimeWindowMs <= 0) {
                throw new PatternValidationException("UNTIL operator maxTimeWindow parameter must be positive");
            }
        }
        
        // Validate inclusive parameter if present
        Object inclusive = parameters.get("inclusive");
        if (inclusive != null && !(inclusive instanceof Boolean)) {
            throw new PatternValidationException("UNTIL operator inclusive parameter must be a boolean");
        }
        
        // Validate strict parameter if present
        Object strict = parameters.get("strict");
        if (strict != null && !(strict instanceof Boolean)) {
            throw new PatternValidationException("UNTIL operator strict parameter must be a boolean");
        }
        
        // Validate allowEmpty parameter if present
        Object allowEmpty = parameters.get("allowEmpty");
        if (allowEmpty != null && !(allowEmpty instanceof Boolean)) {
            throw new PatternValidationException("UNTIL operator allowEmpty parameter must be a boolean");
        }
    }
    
    @Override
    public boolean supports(OperatorSpec spec) {
        return TYPE.equals(spec.getType());
    }
}





