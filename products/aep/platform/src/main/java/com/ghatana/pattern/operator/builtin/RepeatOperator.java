package com.ghatana.pattern.operator.builtin;

import com.ghatana.pattern.api.exception.PatternValidationException;
import com.ghatana.pattern.api.model.OperatorSpec;
import com.ghatana.pattern.operator.spi.Operator;
import com.ghatana.pattern.operator.spi.OperatorMetadata;
import com.ghatana.pattern.operator.spi.ValidationContext;

import java.util.Map;

/**
 * REPEAT operator that matches repeated occurrences of its operand.
 * 
 * <p>This operator matches when its operand occurs a specified number of times.
 * For example, REPEAT(A, 3) matches when event A occurs exactly 3 times.
 * Supports quantifiers: + (1 or more), * (0 or more), {n} (exactly n), {n,m} (between n and m).
 * 
 * @doc.pattern Strategy SPI Pattern - Pluggable operator implementation via {@link Operator} interface,
 *               enabling dynamic operator registration and polymorphic validation.
 * @doc.operator-type Quantifier Operator - Repetition matching (REPEAT) for counting event occurrences.
 *                    Supports regex-style quantifiers (+, *, {n}, {n,m}) and optional maxGap between
 *                    occurrences. Essential for pattern frequency detection.
 * @doc.statefulness Stateful Operator - Requires tracking occurrence count, timestamps, and gaps between events.
 *                   Cannot operate in stateless mode (supportsStateless=false). State includes match count,
 *                   last event timestamp, and pending repetitions.
 * @doc.threading Thread-Safe Validation - Validation logic is stateless and thread-safe. Runtime execution
 *                (not implemented here) would require per-instance state isolation or external synchronization
 *                for occurrence counting and gap tracking.
 * @doc.performance O(1) Validation Performance - Single operand validation, O(1) for quantifier parsing,
 *                  O(p) for parameter validation where p = parameter count. Typical validation time: <1ms.
 *                  Runtime performance is O(n) where n = repetition count (must track n occurrences).
 * @doc.validation <strong>Operand Validation:</strong>
 *                 <ul>
 *                   <li><strong>Count:</strong> Exactly 1 operand (unary quantifier)</li>
 *                   <li><strong>Type:</strong> Operand must have non-null, non-empty type identifier</li>
 *                 </ul>
 *                 <strong>Parameter Validation:</strong>
 *                 <table border="1">
 *                   <tr><th>Parameter</th><th>Type</th><th>Required</th><th>Constraint</th></tr>
 *                   <tr><td>quantifier</td><td>String</td><td><strong>YES</strong></td><td>"+", "*", "{n}", or "{n,m}" where n,m ≥ 0, m ≥ n</td></tr>
 *                   <tr><td>minCount</td><td>Number</td><td>Auto-computed</td><td>≥ 0 (extracted from range quantifier)</td></tr>
 *                   <tr><td>maxCount</td><td>Number</td><td>Auto-computed</td><td>≥ minCount (extracted from range quantifier)</td></tr>
 *                   <tr><td>maxGap</td><td>Number</td><td>No</td><td>≥ 0 (milliseconds between consecutive occurrences)</td></tr>
 *                   <tr><td>greedy</td><td>Boolean</td><td>No</td><td>If true, match maximum occurrences (default: true)</td></tr>
 *                 </table>
 * @doc.apiNote <strong>Usage in Pattern Specification:</strong>
 *              <pre>
 *              // Exactly 3 failed logins
 *              OperatorSpec.builder()
 *                  .type("REPEAT")
 *                  .operands(Collections.singletonList(loginFailedSpec))
 *                  .parameter("quantifier", "{3}")
 *                  .parameter("maxGap", 300000L) // 5 minutes
 *                  .parameter("greedy", true)
 *                  .build()
 *              
 *              // 1 or more transactions (greedy)
 *              OperatorSpec.builder()
 *                  .type("REPEAT")
 *                  .operands(Collections.singletonList(transactionSpec))
 *                  .parameter("quantifier", "+")
 *                  .parameter("greedy", false)
 *                  .build()
 *              
 *              // Between 2-5 API calls
 *              OperatorSpec.builder()
 *                  .type("REPEAT")
 *                  .operands(Collections.singletonList(apiCallSpec))
 *                  .parameter("quantifier", "{2,5}")
 *                  .build()
 *              </pre>
 *              REPEAT is typically used for brute-force detection (3+ failed logins), rate limit
 *              enforcement (100+ requests/minute), and frequency-based anomaly detection.
 * @doc.limitation Compile-Time Only - This implementation validates pattern structure at compile time.
 *                 Runtime execution logic (occurrence counting, gap enforcement, greedy vs non-greedy
 *                 matching) is handled by the pattern engine's execution layer. Does not provide runtime
 *                 matching logic. Auto-computes minCount/maxCount from quantifier string during validation.
 */
public class RepeatOperator implements Operator {
    
    private static final String TYPE = "REPEAT";
    private static final OperatorMetadata METADATA = OperatorMetadata.builder()
            .type(TYPE)
            .description("REPEAT operator that matches repeated occurrences of its operand")
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
            throw new PatternValidationException("OperatorSpec cannot be null for REPEAT operator");
        }
        
        if (!TYPE.equals(spec.getType())) {
            throw new PatternValidationException("Invalid operator type for RepeatOperator: " + spec.getType());
        }
        
        // Validate operand count
        int operandCount = spec.getOperandCount();
        if (operandCount != 1) {
            throw new PatternValidationException(
                String.format("REPEAT operator requires exactly 1 operand, got %d", operandCount));
        }
        
        // Validate operand
        if (spec.getOperands() != null && !spec.getOperands().isEmpty()) {
            OperatorSpec operand = spec.getOperands().get(0);
            if (operand == null) {
                throw new PatternValidationException("REPEAT operator operand cannot be null");
            }
            
            if (operand.getType() == null || operand.getType().trim().isEmpty()) {
                throw new PatternValidationException("REPEAT operator operand must have a valid type");
            }
        } else {
            throw new PatternValidationException("REPEAT operator must have exactly one operand");
        }
        
        // Validate parameters
        validateParameters(spec, context);
    }
    
    private void validateParameters(OperatorSpec spec, ValidationContext context) throws PatternValidationException {
        Map<String, Object> parameters = spec.getParameters();
        if (parameters == null) {
            throw new PatternValidationException("REPEAT operator requires parameters");
        }
        
        // Validate quantifier parameter (required for REPEAT operator)
        Object quantifier = parameters.get("quantifier");
        if (quantifier == null) {
            throw new PatternValidationException("REPEAT operator requires quantifier parameter");
        }
        
        if (!(quantifier instanceof String)) {
            throw new PatternValidationException("REPEAT operator quantifier parameter must be a string");
        }
        
        String quantifierStr = (String) quantifier;
        if (!isValidQuantifier(quantifierStr)) {
            throw new PatternValidationException(
                String.format("REPEAT operator quantifier must be one of: +, *, {n}, {n,m}, got: %s", quantifierStr));
        }
        
        // Validate minCount and maxCount for range quantifiers
        if (quantifierStr.startsWith("{") && quantifierStr.endsWith("}")) {
            validateRangeQuantifier(quantifierStr, parameters);
        }
        
        // Validate maxGap parameter if present
        Object maxGap = parameters.get("maxGap");
        if (maxGap != null) {
            if (!(maxGap instanceof Number)) {
                throw new PatternValidationException("REPEAT operator maxGap parameter must be a number");
            }
            
            long maxGapMs = ((Number) maxGap).longValue();
            if (maxGapMs < 0) {
                throw new PatternValidationException("REPEAT operator maxGap parameter must be non-negative");
            }
        }
        
        // Validate greedy parameter if present
        Object greedy = parameters.get("greedy");
        if (greedy != null && !(greedy instanceof Boolean)) {
            throw new PatternValidationException("REPEAT operator greedy parameter must be a boolean");
        }
    }
    
    private boolean isValidQuantifier(String quantifier) {
        if ("+".equals(quantifier) || "*".equals(quantifier)) {
            return true;
        }
        
        if (quantifier.startsWith("{") && quantifier.endsWith("}")) {
            String range = quantifier.substring(1, quantifier.length() - 1);
            if (range.contains(",")) {
                String[] parts = range.split(",");
                if (parts.length == 2) {
                    try {
                        int min = Integer.parseInt(parts[0].trim());
                        int max = Integer.parseInt(parts[1].trim());
                        return min >= 0 && max >= min;
                    } catch (NumberFormatException e) {
                        return false;
                    }
                }
            } else {
                try {
                    int count = Integer.parseInt(range.trim());
                    return count >= 0;
                } catch (NumberFormatException e) {
                    return false;
                }
            }
        }
        
        return false;
    }
    
    private void validateRangeQuantifier(String quantifier, Map<String, Object> parameters) throws PatternValidationException {
        String range = quantifier.substring(1, quantifier.length() - 1);
        if (range.contains(",")) {
            String[] parts = range.split(",");
            if (parts.length == 2) {
                try {
                    int min = Integer.parseInt(parts[0].trim());
                    int max = Integer.parseInt(parts[1].trim());
                    
                    if (min < 0) {
                        throw new PatternValidationException("REPEAT operator minCount must be non-negative");
                    }
                    
                    if (max < min) {
                        throw new PatternValidationException("REPEAT operator maxCount must be greater than or equal to minCount");
                    }
                    
                    // Set minCount and maxCount in parameters for runtime use
                    parameters.put("minCount", min);
                    parameters.put("maxCount", max);
                } catch (NumberFormatException e) {
                    throw new PatternValidationException("REPEAT operator range quantifier must contain valid numbers");
                }
            }
        } else {
            try {
                int count = Integer.parseInt(range.trim());
                if (count < 0) {
                    throw new PatternValidationException("REPEAT operator count must be non-negative");
                }
                
                // Set minCount and maxCount in parameters for runtime use
                parameters.put("minCount", count);
                parameters.put("maxCount", count);
            } catch (NumberFormatException e) {
                throw new PatternValidationException("REPEAT operator count quantifier must contain a valid number");
            }
        }
    }
    
    @Override
    public boolean supports(OperatorSpec spec) {
        return TYPE.equals(spec.getType());
    }
}





