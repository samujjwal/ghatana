package com.ghatana.pattern.operator.builtin;

import com.ghatana.pattern.api.exception.PatternValidationException;
import com.ghatana.pattern.api.model.OperatorSpec;
import com.ghatana.pattern.operator.spi.Operator;
import com.ghatana.pattern.operator.spi.OperatorMetadata;
import com.ghatana.pattern.operator.spi.ValidationContext;

import java.util.Map;

/**
 * NOT operator that matches the absence of an event (negation).
 * 
 * <p>This operator matches when the specified event does NOT occur within a
 * required time window. For example, NOT(A, timeWindow=60000) matches if event A
 * does not occur within 60 seconds. Essential for absence detection and anomaly patterns.
 * 
 * @doc.pattern Strategy SPI Pattern - Pluggable operator implementation via {@link Operator} interface,
 *               enabling dynamic operator registration and polymorphic validation.
 * @doc.operator-type Pattern Composition Operator - Negation (NOT) for absence detection.
 *                    Part of the core pattern composition operators: SEQ, AND, OR, NOT. Detects when
 *                    expected events do NOT occur within a time bound. Requires mandatory timeWindow parameter.
 * @doc.statefulness Stateful Operator - Requires tracking time windows and checking for event absence.
 *                   Cannot operate in stateless mode (supportsStateless=false). State includes window
 *                   start time, elapsed time, and pending absence checks.
 * @doc.threading Thread-Safe Validation - Validation logic is stateless and thread-safe. Runtime execution
 *                (not implemented here) would require per-instance state isolation or external synchronization
 *                for time window tracking.
 * @doc.performance O(1) Validation Performance - Single operand validation, O(p) for parameter validation
 *                  where p = parameter count. Typical validation time: <1ms. Runtime performance depends
 *                  on time window size (longer windows = more state retention).
 * @doc.validation <strong>Operand Validation:</strong>
 *                 <ul>
 *                   <li><strong>Count:</strong> Exactly 1 operand (unary negation)</li>
 *                   <li><strong>Type:</strong> Operand must have non-null, non-empty type identifier</li>
 *                 </ul>
 *                 <strong>Parameter Validation:</strong>
 *                 <table border="1">
 *                   <tr><th>Parameter</th><th>Type</th><th>Required</th><th>Constraint</th></tr>
 *                   <tr><td>timeWindow</td><td>Number</td><td><strong>YES</strong></td><td>&gt; 0 (milliseconds to check for absence)</td></tr>
 *                   <tr><td>strict</td><td>Boolean</td><td>No</td><td>If true, fail immediately if event occurs (don't wait for window)</td></tr>
 *                 </table>
 * @doc.apiNote <strong>Usage in Pattern Specification:</strong>
 *              <pre>
 *              // Detect missing heartbeat (absence detection)
 *              OperatorSpec.builder()
 *                  .type("NOT")
 *                  .operands(Collections.singletonList(heartbeatSpec))
 *                  .parameter("timeWindow", 30000L) // 30 seconds
 *                  .parameter("strict", false)
 *                  .build()
 *              
 *              // Detect unauthorized access (strict negation)
 *              OperatorSpec.builder()
 *                  .type("NOT")
 *                  .operands(Collections.singletonList(authApprovalSpec))
 *                  .parameter("timeWindow", 5000L) // 5 seconds
 *                  .parameter("strict", true)
 *                  .build()
 *              </pre>
 *              NOT is typically used for timeout detection (missing response), anomaly detection
 *              (unexpected event absence), and compliance validation (required event did not occur).
 * @doc.limitation Compile-Time Only - This implementation validates pattern structure at compile time.
 *                 Runtime execution logic (time window tracking, absence detection, strict mode enforcement)
 *                 is handled by the pattern engine's execution layer. Does not provide runtime matching logic.
 */
public class NotOperator implements Operator {
    
    private static final String TYPE = "NOT";
    private static final OperatorMetadata METADATA = OperatorMetadata.builder()
            .type(TYPE)
            .description("NOT operator that matches when its operand does NOT match")
            .minOperands(1)
            .maxOperands(1)
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
            throw new PatternValidationException("OperatorSpec cannot be null for NOT operator");
        }
        
        if (!TYPE.equals(spec.getType())) {
            throw new PatternValidationException("Invalid operator type for NotOperator: " + spec.getType());
        }
        
        // Validate operand count
        int operandCount = spec.getOperandCount();
        if (operandCount != 1) {
            throw new PatternValidationException(
                String.format("NOT operator requires exactly 1 operand, got %d", operandCount));
        }
        
        // Validate operand
        if (spec.getOperands() != null && !spec.getOperands().isEmpty()) {
            OperatorSpec operand = spec.getOperands().get(0);
            if (operand == null) {
                throw new PatternValidationException("NOT operator operand cannot be null");
            }
            
            if (operand.getType() == null || operand.getType().trim().isEmpty()) {
                throw new PatternValidationException("NOT operator operand must have a valid type");
            }
        } else {
            throw new PatternValidationException("NOT operator must have exactly one operand");
        }
        
        // Validate parameters
        validateParameters(spec, context);
    }
    
    private void validateParameters(OperatorSpec spec, ValidationContext context) throws PatternValidationException {
        Map<String, Object> parameters = spec.getParameters();
        if (parameters == null) {
            return;
        }
        
        // Validate timeWindow parameter (required for NOT operator)
        Object timeWindow = parameters.get("timeWindow");
        if (timeWindow == null) {
            throw new PatternValidationException("NOT operator requires timeWindow parameter");
        }
        
        if (!(timeWindow instanceof Number)) {
            throw new PatternValidationException("NOT operator timeWindow parameter must be a number");
        }
        
        long timeWindowMs = ((Number) timeWindow).longValue();
        if (timeWindowMs <= 0) {
            throw new PatternValidationException("NOT operator timeWindow parameter must be positive");
        }
        
        // Validate strict parameter if present
        Object strict = parameters.get("strict");
        if (strict != null && !(strict instanceof Boolean)) {
            throw new PatternValidationException("NOT operator strict parameter must be a boolean");
        }
    }
    
    @Override
    public boolean supports(OperatorSpec spec) {
        return TYPE.equals(spec.getType());
    }
}





