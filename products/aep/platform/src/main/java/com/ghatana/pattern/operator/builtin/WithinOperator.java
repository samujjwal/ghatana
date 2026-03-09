package com.ghatana.pattern.operator.builtin;

import com.ghatana.pattern.api.exception.PatternValidationException;
import com.ghatana.pattern.api.model.OperatorSpec;
import com.ghatana.pattern.operator.spi.Operator;
import com.ghatana.pattern.operator.spi.OperatorMetadata;
import com.ghatana.pattern.operator.spi.ValidationContext;

import java.util.Map;

/**
 * WITHIN operator that applies temporal constraints to its operand.
 * 
 * <p>This operator constrains its operand to match only within a specified time window.
 * For example, WITHIN(A, timeWindow=60000) matches event A only if it occurs within
 * 60 seconds of the pattern evaluation start. Supports optional start/end time bounds.
 * 
 * @doc.pattern Strategy SPI Pattern - Pluggable operator implementation via {@link Operator} interface,
 *               enabling dynamic operator registration and polymorphic validation.
 * @doc.operator-type Temporal Constraint Operator - Time-bounded matching (WITHIN) for absolute or relative
 *                    time windows. Unlike SEQ/AND which track inter-event timing, WITHIN constrains absolute
 *                    time from pattern evaluation start.
 * @doc.statefulness Stateful Operator - Requires tracking time window boundaries and event timestamps.
 *                   Cannot operate in stateless mode (supportsStateless=false). State includes window
 *                   start time, end time, and elapsed time tracking.
 * @doc.threading Thread-Safe Validation - Validation logic is stateless and thread-safe. Runtime execution
 *                (not implemented here) would require per-instance state isolation or external synchronization
 *                for time window tracking.
 * @doc.performance O(1) Validation Performance - Single operand validation, O(p) for parameter validation
 *                  where p = parameter count (typically 3-4 parameters). Typical validation time: <1ms.
 *                  Runtime performance is O(1) per event (simple timestamp comparison).
 * @doc.validation <strong>Operand Validation:</strong>
 *                 <ul>
 *                   <li><strong>Count:</strong> Exactly 1 operand (unary constraint)</li>
 *                   <li><strong>Type:</strong> Operand must have non-null, non-empty type identifier</li>
 *                 </ul>
 *                 <strong>Parameter Validation:</strong>
 *                 <table border="1">
 *                   <tr><th>Parameter</th><th>Type</th><th>Required</th><th>Constraint</th></tr>
 *                   <tr><td>timeWindow</td><td>Number</td><td><strong>YES</strong></td><td>&gt; 0 (milliseconds from evaluation start)</td></tr>
 *                   <tr><td>startTime</td><td>Number</td><td>No</td><td>≥ 0 (absolute start timestamp in milliseconds since epoch)</td></tr>
 *                   <tr><td>endTime</td><td>Number</td><td>No</td><td>≥ 0 (absolute end timestamp in milliseconds since epoch)</td></tr>
 *                   <tr><td>inclusive</td><td>Boolean</td><td>No</td><td>If true, include boundary timestamps (default: false)</td></tr>
 *                 </table>
 * @doc.apiNote <strong>Usage in Pattern Specification:</strong>
 *              <pre>
 *              // Relative time window (60 seconds from pattern start)
 *              OperatorSpec.builder()
 *                  .type("WITHIN")
 *                  .operands(Collections.singletonList(highValueTransactionSpec))
 *                  .parameter("timeWindow", 60000L)
 *                  .parameter("inclusive", true)
 *                  .build()
 *              
 *              // Absolute time bounds (business hours only)
 *              OperatorSpec.builder()
 *                  .type("WITHIN")
 *                  .operands(Collections.singletonList(tradingEventSpec))
 *                  .parameter("timeWindow", 28800000L) // 8 hours
 *                  .parameter("startTime", 1609488000000L) // 9:00 AM epoch
 *                  .parameter("endTime", 1609516800000L) // 5:00 PM epoch
 *                  .parameter("inclusive", false)
 *                  .build()
 *              </pre>
 *              WITHIN is typically used for SLA enforcement (response within 30s), business hour
 *              constraints (trading events 9-5), and time-bounded compliance checks.
 * @doc.limitation Compile-Time Only - This implementation validates pattern structure at compile time.
 *                 Runtime execution logic (time window enforcement, absolute time bounds, inclusive/exclusive
 *                 boundary checking) is handled by the pattern engine's execution layer. Does not provide
 *                 runtime matching logic.
 */
public class WithinOperator implements Operator {
    
    private static final String TYPE = "WITHIN";
    private static final OperatorMetadata METADATA = OperatorMetadata.builder()
            .type(TYPE)
            .description("WITHIN operator that applies temporal constraints to its operand")
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
            throw new PatternValidationException("OperatorSpec cannot be null for WITHIN operator");
        }
        
        if (!TYPE.equals(spec.getType())) {
            throw new PatternValidationException("Invalid operator type for WithinOperator: " + spec.getType());
        }
        
        // Validate operand count
        int operandCount = spec.getOperandCount();
        if (operandCount != 1) {
            throw new PatternValidationException(
                String.format("WITHIN operator requires exactly 1 operand, got %d", operandCount));
        }
        
        // Validate operand
        if (spec.getOperands() != null && !spec.getOperands().isEmpty()) {
            OperatorSpec operand = spec.getOperands().get(0);
            if (operand == null) {
                throw new PatternValidationException("WITHIN operator operand cannot be null");
            }
            
            if (operand.getType() == null || operand.getType().trim().isEmpty()) {
                throw new PatternValidationException("WITHIN operator operand must have a valid type");
            }
        } else {
            throw new PatternValidationException("WITHIN operator must have exactly one operand");
        }
        
        // Validate parameters
        validateParameters(spec, context);
    }
    
    private void validateParameters(OperatorSpec spec, ValidationContext context) throws PatternValidationException {
        Map<String, Object> parameters = spec.getParameters();
        if (parameters == null) {
            throw new PatternValidationException("WITHIN operator requires parameters");
        }
        
        // Validate timeWindow parameter (required for WITHIN operator)
        Object timeWindow = parameters.get("timeWindow");
        if (timeWindow == null) {
            throw new PatternValidationException("WITHIN operator requires timeWindow parameter");
        }
        
        if (!(timeWindow instanceof Number)) {
            throw new PatternValidationException("WITHIN operator timeWindow parameter must be a number");
        }
        
        long timeWindowMs = ((Number) timeWindow).longValue();
        if (timeWindowMs <= 0) {
            throw new PatternValidationException("WITHIN operator timeWindow parameter must be positive");
        }
        
        // Validate startTime parameter if present
        Object startTime = parameters.get("startTime");
        if (startTime != null) {
            if (!(startTime instanceof Number)) {
                throw new PatternValidationException("WITHIN operator startTime parameter must be a number");
            }
            
            long startTimeMs = ((Number) startTime).longValue();
            if (startTimeMs < 0) {
                throw new PatternValidationException("WITHIN operator startTime parameter must be non-negative");
            }
        }
        
        // Validate endTime parameter if present
        Object endTime = parameters.get("endTime");
        if (endTime != null) {
            if (!(endTime instanceof Number)) {
                throw new PatternValidationException("WITHIN operator endTime parameter must be a number");
            }
            
            long endTimeMs = ((Number) endTime).longValue();
            if (endTimeMs < 0) {
                throw new PatternValidationException("WITHIN operator endTime parameter must be non-negative");
            }
        }
        
        // Validate inclusive parameter if present
        Object inclusive = parameters.get("inclusive");
        if (inclusive != null && !(inclusive instanceof Boolean)) {
            throw new PatternValidationException("WITHIN operator inclusive parameter must be a boolean");
        }
    }
    
    @Override
    public boolean supports(OperatorSpec spec) {
        return TYPE.equals(spec.getType());
    }
}





