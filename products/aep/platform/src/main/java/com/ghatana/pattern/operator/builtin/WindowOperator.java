package com.ghatana.pattern.operator.builtin;

import com.ghatana.pattern.api.exception.PatternValidationException;
import com.ghatana.pattern.api.model.OperatorSpec;
import com.ghatana.pattern.operator.spi.Operator;
import com.ghatana.pattern.operator.spi.OperatorMetadata;
import com.ghatana.pattern.operator.spi.ValidationContext;

import java.util.Map;

/**
 * WINDOW operator that applies windowing semantics to its operand.
 * 
 * <p>This operator applies windowing semantics (tumbling, sliding, session) to its operand,
 * enabling time-based or count-based event aggregation. For example, WINDOW(A, tumbling, 60000ms)
 * applies a 1-minute tumbling window to event A, grouping events into non-overlapping buckets.
 * 
 * @doc.pattern Strategy SPI Pattern - Pluggable operator implementation via {@link Operator} interface,
 *               enabling dynamic operator registration and polymorphic validation.
 * @doc.operator-type Aggregation/Windowing Operator - Temporal aggregation (WINDOW) for grouping events
 *                    into time-based or count-based buckets. Supports tumbling (non-overlapping), sliding
 *                    (overlapping), session (gap-based), and global (unbounded) windows. Essential for
 *                    time-series analytics and event batching.
 * @doc.statefulness Stateful Operator - Requires tracking window boundaries, buffering events within
 *                   windows, and managing window lifecycle (open/close/emit). Cannot operate in stateless
 *                   mode (supportsStateless=false). State includes active windows, buffered events,
 *                   and window metadata (start time, end time, event count).
 * @doc.threading Thread-Safe Validation - Validation logic is stateless and thread-safe. Runtime execution
 *                (not implemented here) would require per-instance state isolation or external synchronization
 *                for window state management and event buffering.
 * @doc.performance O(1) Validation Performance - Single operand validation, O(1) for window type validation,
 *                  O(p) for parameter validation where p = parameter count (3-4 parameters typical).
 *                  Typical validation time: <1ms. Runtime performance is O(w*e) where w = active window
 *                  count and e = events per window (sliding windows have higher w).
 * @doc.validation <strong>Operand Validation:</strong>
 *                 <ul>
 *                   <li><strong>Count:</strong> Exactly 1 operand (unary windowing)</li>
 *                   <li><strong>Type:</strong> Operand must have non-null, non-empty type identifier</li>
 *                 </ul>
 *                 <strong>Parameter Validation:</strong>
 *                 <table border="1">
 *                   <tr><th>Parameter</th><th>Type</th><th>Required</th><th>Constraint</th></tr>
 *                   <tr><td>windowType</td><td>String</td><td><strong>YES</strong></td><td>"tumbling", "sliding", "session", or "global"</td></tr>
 *                   <tr><td>windowSize</td><td>Number</td><td><strong>YES</strong></td><td>&gt; 0 (milliseconds for window duration)</td></tr>
 *                   <tr><td>slideSize</td><td>Number</td><td>Only for sliding</td><td>&gt; 0, ≤ windowSize (milliseconds for slide interval)</td></tr>
 *                   <tr><td>sessionTimeout</td><td>Number</td><td>Only for session</td><td>&gt; 0 (milliseconds of inactivity to close window)</td></tr>
 *                 </table>
 * @doc.apiNote <strong>Usage in Pattern Specification:</strong>
 *              <pre>
 *              // Tumbling window (non-overlapping 1-minute buckets)
 *              OperatorSpec.builder()
 *                  .type("WINDOW")
 *                  .operands(Collections.singletonList(transactionSpec))
 *                  .parameter("windowType", "tumbling")
 *                  .parameter("windowSize", 60000L)
 *                  .build()
 *              
 *              // Sliding window (overlapping 5-minute window, 1-minute slide)
 *              OperatorSpec.builder()
 *                  .type("WINDOW")
 *                  .operands(Collections.singletonList(apiCallSpec))
 *                  .parameter("windowType", "sliding")
 *                  .parameter("windowSize", 300000L)
 *                  .parameter("slideSize", 60000L)
 *                  .build()
 *              
 *              // Session window (close after 30 seconds of inactivity)
 *              OperatorSpec.builder()
 *                  .type("WINDOW")
 *                  .operands(Collections.singletonList(clickEventSpec))
 *                  .parameter("windowType", "session")
 *                  .parameter("windowSize", 1800000L) // 30 minutes max
 *                  .parameter("sessionTimeout", 30000L) // 30 seconds idle
 *                  .build()
 *              </pre>
 *              WINDOW is typically used for rate limiting (requests per minute), time-series aggregation
 *              (hourly summaries), session analysis (user activity sessions), and moving averages.
 * @doc.limitation Compile-Time Only - This implementation validates pattern structure at compile time.
 *                 Runtime execution logic (window lifecycle management, event buffering, window triggering,
 *                 slide scheduling, session gap detection) is handled by the pattern engine's execution layer.
 *                 Does not provide runtime matching or aggregation logic.
 */
public class WindowOperator implements Operator {
    
    private static final String TYPE = "WINDOW";
    private static final OperatorMetadata METADATA = OperatorMetadata.builder()
            .type(TYPE)
            .description("WINDOW operator that applies windowing to its operand")
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
            throw new PatternValidationException("OperatorSpec cannot be null for WINDOW operator");
        }
        
        if (!TYPE.equals(spec.getType())) {
            throw new PatternValidationException("Invalid operator type for WindowOperator: " + spec.getType());
        }
        
        // Validate operand count
        int operandCount = spec.getOperandCount();
        if (operandCount != 1) {
            throw new PatternValidationException(
                String.format("WINDOW operator requires exactly 1 operand, got %d", operandCount));
        }
        
        // Validate operand
        if (spec.getOperands() != null && !spec.getOperands().isEmpty()) {
            OperatorSpec operand = spec.getOperands().get(0);
            if (operand == null) {
                throw new PatternValidationException("WINDOW operator operand cannot be null");
            }
            
            if (operand.getType() == null || operand.getType().trim().isEmpty()) {
                throw new PatternValidationException("WINDOW operator operand must have a valid type");
            }
        } else {
            throw new PatternValidationException("WINDOW operator must have exactly one operand");
        }
        
        // Validate parameters
        validateParameters(spec, context);
    }
    
    private void validateParameters(OperatorSpec spec, ValidationContext context) throws PatternValidationException {
        Map<String, Object> parameters = spec.getParameters();
        if (parameters == null) {
            throw new PatternValidationException("WINDOW operator requires parameters");
        }
        
        // Validate windowType parameter (required for WINDOW operator)
        Object windowType = parameters.get("windowType");
        if (windowType == null) {
            throw new PatternValidationException("WINDOW operator requires windowType parameter");
        }
        
        if (!(windowType instanceof String)) {
            throw new PatternValidationException("WINDOW operator windowType parameter must be a string");
        }
        
        String windowTypeStr = (String) windowType;
        if (!isValidWindowType(windowTypeStr)) {
            throw new PatternValidationException(
                String.format("WINDOW operator windowType must be one of: tumbling, sliding, session, got: %s", windowTypeStr));
        }
        
        // Validate windowSize parameter (required for WINDOW operator)
        Object windowSize = parameters.get("windowSize");
        if (windowSize == null) {
            throw new PatternValidationException("WINDOW operator requires windowSize parameter");
        }
        
        if (!(windowSize instanceof Number)) {
            throw new PatternValidationException("WINDOW operator windowSize parameter must be a number");
        }
        
        long windowSizeMs = ((Number) windowSize).longValue();
        if (windowSizeMs <= 0) {
            throw new PatternValidationException("WINDOW operator windowSize parameter must be positive");
        }
        
        // Validate slideSize parameter for sliding windows
        if ("sliding".equals(windowTypeStr)) {
            Object slideSize = parameters.get("slideSize");
            if (slideSize == null) {
                throw new PatternValidationException("WINDOW operator requires slideSize parameter for sliding windows");
            }
            
            if (!(slideSize instanceof Number)) {
                throw new PatternValidationException("WINDOW operator slideSize parameter must be a number");
            }
            
            long slideSizeMs = ((Number) slideSize).longValue();
            if (slideSizeMs <= 0) {
                throw new PatternValidationException("WINDOW operator slideSize parameter must be positive");
            }
            
            if (slideSizeMs > windowSizeMs) {
                throw new PatternValidationException("WINDOW operator slideSize cannot be greater than windowSize");
            }
        }
        
        // Validate sessionTimeout parameter for session windows
        if ("session".equals(windowTypeStr)) {
            Object sessionTimeout = parameters.get("sessionTimeout");
            if (sessionTimeout == null) {
                throw new PatternValidationException("WINDOW operator requires sessionTimeout parameter for session windows");
            }
            
            if (!(sessionTimeout instanceof Number)) {
                throw new PatternValidationException("WINDOW operator sessionTimeout parameter must be a number");
            }
            
            long sessionTimeoutMs = ((Number) sessionTimeout).longValue();
            if (sessionTimeoutMs <= 0) {
                throw new PatternValidationException("WINDOW operator sessionTimeout parameter must be positive");
            }
        }
    }
    
    private boolean isValidWindowType(String windowType) {
        return "tumbling".equals(windowType) || 
               "sliding".equals(windowType) || 
               "session".equals(windowType) || 
               "global".equals(windowType);
    }
    
    @Override
    public boolean supports(OperatorSpec spec) {
        return TYPE.equals(spec.getType());
    }
}





