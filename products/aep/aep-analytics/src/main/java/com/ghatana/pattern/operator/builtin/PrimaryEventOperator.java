package com.ghatana.pattern.operator.builtin;

import com.ghatana.pattern.api.exception.PatternValidationException;
import com.ghatana.pattern.api.model.OperatorSpec;
import com.ghatana.pattern.operator.spi.Operator;
import com.ghatana.pattern.operator.spi.OperatorMetadata;
import com.ghatana.pattern.operator.spi.ValidationContext;

import java.util.List;
import java.util.Map;

/**
 * Primary event operator that filters incoming events by declared event types.
 *
 * <p>This operator acts as the entry point for pattern matching, ensuring that only
 * events matching the declared event types are processed by downstream operators.
 * It is automatically injected at the root of the operator tree during compilation.
 *
 * <p>Example:
 * <pre>
 * PRIMARY_EVENT(eventTypes=["com.ghatana.financial.TransactionEvent", "com.ghatana.financial.AccountEvent"])
 *   → SEQ(...)
 * </pre>
 *
 * <p><strong>Characteristics:</strong>
 * <ul>
 *   <li>Stateless filter operator</li>
 *   <li>Exactly one operand (next operator in chain)</li>
 *   <li>Required parameter: eventTypes (List&lt;String&gt;)</li>
 * </ul>
 * 
 * @doc.pattern Strategy SPI Pattern - Pluggable operator implementation via {@link Operator} interface,
 *               enabling dynamic operator registration and polymorphic validation.
 *               Auto-Injection Pattern - Compiler automatically injects PRIMARY_EVENT at DAG root if not
 *               explicitly declared, ensuring all patterns have event type filtering.
 * @doc.operator-type Event Selection/Filter Operator - Entry point filter (PRIMARY_EVENT) for type-based
 *                    event routing. Not a composition operator (SEQ/AND/OR), but a prerequisite filter
 *                    injected at the root of all operator DAGs. Validates event types against tenant's
 *                    event catalog before pattern matching begins.
 * @doc.statefulness Stateless Operator - Pure filter based on event type metadata. Does NOT require
 *                   cross-event state tracking. Each event is independently evaluated against the
 *                   eventTypes list. Cannot operate in stateful mode (supportsStateful=false).
 * @doc.threading Thread-Safe Validation - Validation logic is stateless and thread-safe. Runtime execution
 *                (not implemented here) is also inherently thread-safe - simple type membership check.
 *                Event type registry lookups (if enabled) must be thread-safe at catalog layer.
 * @doc.performance O(n) Validation Performance - Linear in number of event types for parameter validation
 *                  (must validate each type format + catalog existence). Typical validation time: <1ms
 *                  for 1-10 event types. Runtime filtering is O(n) per event where n = eventTypes.size().
 *                  In practice, n is small (1-5 types) and comparison is string equality check.
 * @doc.validation <strong>Operand Validation:</strong>
 *                 <ul>
 *                   <li><strong>Count:</strong> Exactly 1 operand (next operator in DAG)</li>
 *                   <li><strong>Type:</strong> Operand must have non-null, non-empty type identifier</li>
 *                   <li><strong>Purpose:</strong> Operand receives only events that pass type filter</li>
 *                 </ul>
 *                 <strong>Parameter Validation:</strong>
 *                 <table border="1">
 *                   <tr><th>Parameter</th><th>Type</th><th>Required</th><th>Constraint</th></tr>
 *                   <tr><td>eventTypes</td><td>List&lt;String&gt;</td><td><strong>YES</strong></td><td>Non-empty list of event type identifiers (namespace.name format)</td></tr>
 *                 </table>
 *                 <strong>Event Type Format Validation:</strong>
 *                 <ul>
 *                   <li>Must contain at least one dot separator (namespace.name)</li>
 *                   <li>Each part must start with letter, contain only alphanumeric + underscore</li>
 *                   <li>Example valid: "com.ghatana.financial.TransactionEvent"</li>
 *                   <li>Example invalid: "Transaction" (no namespace), "123.Event" (starts with digit)</li>
 *                 </ul>
 *                 <strong>Catalog Validation (if EventTypeRegistry available):</strong>
 *                 <ul>
 *                   <li>Event type must exist in tenant's event catalog</li>
 *                   <li>Event type status must be ACTIVE (not DRAFT or DELETED)</li>
 *                   <li>Validation skipped if context.hasEventTypeRegistry() is false</li>
 *                 </ul>
 * @doc.apiNote <strong>Usage in Pattern Specification:</strong>
 *              <pre>
 *              // Single event type filter
 *              OperatorSpec.builder()
 *                  .type("PRIMARY_EVENT")
 *                  .operands(Collections.singletonList(seqOperatorSpec))
 *                  .parameter("eventTypes", Arrays.asList("com.ghatana.financial.TransactionEvent"))
 *                  .build()
 *              
 *              // Multiple event types (OR semantics at filter level)
 *              OperatorSpec.builder()
 *                  .type("PRIMARY_EVENT")
 *                  .operands(Collections.singletonList(andOperatorSpec))
 *                  .parameter("eventTypes", Arrays.asList(
 *                      "com.ghatana.security.LoginEvent",
 *                      "com.ghatana.security.LogoutEvent",
 *                      "com.ghatana.security.AuthFailureEvent"
 *                  ))
 *                  .build()
 *              </pre>
 *              <strong>Compiler Auto-Injection:</strong>
 *              If pattern specification does not explicitly include PRIMARY_EVENT, the compiler
 *              automatically injects it at the root with eventTypes extracted from pattern's declared
 *              event types. This ensures all patterns have type-based event filtering.
 * @doc.limitation Compile-Time Only - This implementation validates pattern structure and event type
 *                 references at compile time. Runtime execution logic (event type membership check,
 *                 type-based routing) is handled by the pattern engine's execution layer. Does not
 *                 provide runtime filtering logic.
 *                 <p>
 *                 Catalog validation is optional and depends on ValidationContext providing EventTypeRegistry.
 *                 If registry is unavailable, only format validation is performed.
 */
public class PrimaryEventOperator implements Operator {

    private static final String TYPE = "PRIMARY_EVENT";
    private static final String PARAM_EVENT_TYPES = "eventTypes";

    private static final OperatorMetadata METADATA = OperatorMetadata.builder()
            .type(TYPE)
            .description("Filters events by primary event types before pattern matching")
            .requiredParameter(PARAM_EVENT_TYPES)
            .minOperands(1)
            .maxOperands(1)
            .supportsStateful(false)
            .supportsStateless(true)
            .constraint("eventTypes", "Must be a non-empty list of event type identifiers")
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
            throw new PatternValidationException("OperatorSpec cannot be null for PRIMARY_EVENT operator");
        }

        if (!TYPE.equals(spec.getType())) {
            throw new PatternValidationException("Invalid operator type for PrimaryEventOperator: " + spec.getType());
        }

        // Validate operand count (exactly one)
        int operandCount = spec.getOperandCount();
        if (operandCount != 1) {
            throw new PatternValidationException(
                String.format("PRIMARY_EVENT operator requires exactly 1 operand (next operator), got %d", operandCount));
        }

        // Validate operand
        if (spec.getOperands() != null && !spec.getOperands().isEmpty()) {
            OperatorSpec operand = spec.getOperands().get(0);
            if (operand == null) {
                throw new PatternValidationException("PRIMARY_EVENT operator operand cannot be null");
            }

            if (operand.getType() == null || operand.getType().trim().isEmpty()) {
                throw new PatternValidationException("PRIMARY_EVENT operator operand must have a valid type");
            }
        }

        // Validate required parameters
        validateParameters(spec, context);
    }

    private void validateParameters(OperatorSpec spec, ValidationContext context) throws PatternValidationException {
        Map<String, Object> parameters = spec.getParameters();

        // eventTypes parameter is required
        if (parameters == null || !parameters.containsKey(PARAM_EVENT_TYPES)) {
            throw new PatternValidationException(
                "PRIMARY_EVENT operator requires 'eventTypes' parameter");
        }

        Object eventTypesObj = parameters.get(PARAM_EVENT_TYPES);

        // Must be a list
        if (!(eventTypesObj instanceof List)) {
            throw new PatternValidationException(
                "PRIMARY_EVENT operator 'eventTypes' parameter must be a list");
        }

        @SuppressWarnings("unchecked")
        List<String> eventTypes = (List<String>) eventTypesObj;

        // Must not be empty
        if (eventTypes.isEmpty()) {
            throw new PatternValidationException(
                "PRIMARY_EVENT operator 'eventTypes' parameter must not be empty");
        }

        // Validate each event type
        for (int i = 0; i < eventTypes.size(); i++) {
            String eventType = eventTypes.get(i);

            if (eventType == null || eventType.trim().isEmpty()) {
                throw new PatternValidationException(
                    String.format("PRIMARY_EVENT operator eventTypes[%d] cannot be null or empty", i));
            }

            // Validate event type format (namespace.name)
            if (!isValidEventTypeFormat(eventType)) {
                throw new PatternValidationException(
                    String.format("PRIMARY_EVENT operator eventTypes[%d] has invalid format: %s (expected: namespace.name)",
                        i, eventType));
            }

            // Validate event type exists in catalog (if context provides this capability)
            if (context != null && context.hasEventTypeRegistry()) {
                String tenantId = context.getTenantId();
                if (!context.eventTypeExists(eventType, tenantId)) {
                    throw new PatternValidationException(
                        String.format("Event type not found in catalog: %s (tenant: %s)", eventType, tenantId));
                }

                // Validate event type is ACTIVE
                if (!context.isEventTypeActive(eventType, tenantId)) {
                    throw new PatternValidationException(
                        String.format("Event type is not active: %s (tenant: %s)", eventType, tenantId));
                }
            }
        }
    }

    /**
     * Validates event type format.
     *
     * <p>Expected format: namespace.name (e.g., "com.ghatana.financial.TransactionEvent")
     * Minimum requirement: at least one dot separator
     *
     * @param eventType the event type identifier to validate
     * @return true if format is valid, false otherwise
     */
    private boolean isValidEventTypeFormat(String eventType) {
        if (eventType == null || eventType.trim().isEmpty()) {
            return false;
        }

        // Must contain at least one dot
        if (!eventType.contains(".")) {
            return false;
        }

        // Split by dot and validate each part
        String[] parts = eventType.split("\\.");
        if (parts.length < 2) {
            return false;
        }

        // Each part must be non-empty and start with letter
        for (String part : parts) {
            if (part.isEmpty()) {
                return false;
            }

            // First character must be a letter
            if (!Character.isLetter(part.charAt(0))) {
                return false;
            }

            // Rest must be alphanumeric or underscore
            for (int i = 1; i < part.length(); i++) {
                char c = part.charAt(i);
                if (!Character.isLetterOrDigit(c) && c != '_') {
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    public boolean supports(OperatorSpec spec) {
        return TYPE.equals(spec.getType());
    }
}
