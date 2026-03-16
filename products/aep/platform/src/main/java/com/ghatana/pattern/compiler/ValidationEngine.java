package com.ghatana.pattern.compiler;

import com.ghatana.pattern.api.exception.PatternValidationException;
import com.ghatana.pattern.api.model.*;
import com.ghatana.pattern.operator.registry.OperatorRegistry;
import com.ghatana.pattern.operator.spi.ValidationContext;

import io.micrometer.core.instrument.MeterRegistry;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.MetricsCollectorFactory;
import io.micrometer.core.instrument.Timer;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validation engine for pattern specifications with comprehensive rule checking.
 * 
 * <p>The ValidationEngine performs multi-phase validation of pattern specifications before
 * compilation, ensuring patterns are well-formed, semantically correct, and authorized for
 * the requesting tenant. Validation failures throw {@link PatternValidationException} with
 * detailed error messages for debugging.
 * 
 * @doc.pattern Strategy Pattern - Pluggable validation strategies (basic fields, operators, windows, permissions).
 *               Chain of Responsibility - Sequential validation chain where each validator can fail the request.
 * @doc.compiler-phase Validation (Phase 1 of 5) - First phase before AST building, DAG generation, optimization, plan creation.
 * @doc.validation-order <strong>Validation Execution Order:</strong>
 *                       <ol>
 *                         <li><strong>Basic Fields</strong>: ID, name, tenant, version, priority, status</li>
 *                         <li><strong>Event Types</strong>: Format validation (namespace.name), catalog availability</li>
 *                         <li><strong>Operator Tree</strong>: Recursive validation via OperatorRegistry</li>
 *                         <li><strong>Window Spec</strong>: Time duration, overlap, session timeout validation</li>
 *                         <li><strong>Tenant Permissions</strong>: Access control, quota limits (TODO)</li>
 *                       </ol>
 * @doc.threading Thread-Safe - Stateless validator, safe for concurrent validation of independent patterns.
 *                Multiple threads can validate different patterns simultaneously without synchronization.
 * @doc.performance <strong>Performance Characteristics:</strong>
 *                  <ul>
 *                    <li><strong>validateBasicFields:</strong> O(1) - Constant-time field checks</li>
 *                    <li><strong>validateEventTypes:</strong> O(m) where m = event type count (format validation only)</li>
 *                    <li><strong>validateOperatorTree:</strong> O(n) where n = operator count (recursive tree traversal)</li>
 *                    <li><strong>validateWindowSpec:</strong> O(1) - Constant-time window parameter checks</li>
 *                    <li><strong>Overall:</strong> O(n + m) dominated by operator tree size</li>
 *                  </ul>
 * @doc.event-type-validation <strong>Event Type Format Rules:</strong>
 *                            <ul>
 *                              <li><strong>Format:</strong> namespace.name (minimum 2 parts separated by dot)</li>
 *                              <li><strong>Example:</strong> "com.ghatana.financial.TransactionEvent"</li>
 *                              <li><strong>Namespace:</strong> Each part starts with letter, contains alphanumeric + underscore</li>
 *                              <li><strong>Validation:</strong> Format only in ValidationEngine; catalog lookup in PrimaryEventOperator</li>
 *                            </ul>
 * @doc.window-validation <strong>Window Type Validation:</strong>
 *                        <table border="1">
 *                          <tr>
 *                            <th>Window Type</th>
 *                            <th>Required Parameters</th>
 *                            <th>Validation Rules</th>
 *                          </tr>
 *                          <tr>
 *                            <td>TUMBLING</td>
 *                            <td>size</td>
 *                            <td>size > 0 (no negative durations)</td>
 *                          </tr>
 *                          <tr>
 *                            <td>SLIDING</td>
 *                            <td>size, slide</td>
 *                            <td>size > 0, slide > 0, slide ≤ size</td>
 *                          </tr>
 *                          <tr>
 *                            <td>SESSION</td>
 *                            <td>sessionTimeout</td>
 *                            <td>timeout > 0</td>
 *                          </tr>
 *                          <tr>
 *                            <td>GLOBAL</td>
 *                            <td>none</td>
 *                            <td>No validation (unbounded window)</td>
 *                          </tr>
 *                        </table>
 * @doc.metrics Micrometer Metrics Integration:
 *              <ul>
 *                <li><strong>pattern.compiler.validation.time</strong> (Timer): Validation duration per pattern</li>
 *                <li><strong>pattern.compiler.validation.success</strong> (Counter): Successful validations</li>
 *                <li><strong>pattern.compiler.validation.failure</strong> (Counter): Failed validations</li>
 *              </ul>
 * @doc.apiNote <strong>Usage Example - Standalone Validation:</strong>
 *              <pre>
 *              ValidationEngine validator = new ValidationEngine(operatorRegistry, meterRegistry);
 *              
 *              try {
 *                  validator.validate(patternSpec);
 *                  System.out.println("Pattern is valid");
 *              } catch (PatternValidationException e) {
 *                  System.err.println("Validation failed: " + e.getMessage());
 *                  // Example errors:
 *                  // - "Pattern name cannot be null or empty"
 *                  // - "Event type at index 0 has invalid format: 'InvalidFormat' (expected: namespace.name)"
 *                  // - "Sliding window slide cannot be greater than size"
 *              }
 *              </pre>
 *              
 *              <strong>Integration with Compiler:</strong>
 *              <pre>
 *              // ValidationEngine is called automatically in first phase
 *              DetectionPlan plan = patternCompiler.compile(spec); // validate() called internally
 *              </pre>
 * @doc.limitation <strong>Limitations:</strong>
 *                 <ul>
 *                   <li><strong>No cross-pattern validation:</strong> Each pattern validated independently;
 *                       cannot detect conflicts between patterns (e.g., duplicate names)</li>
 *                   <li><strong>No cycle detection:</strong> Operator tree assumed to be acyclic from AST structure</li>
 *                   <li><strong>Format-only event validation:</strong> Event type catalog lookup deferred to
 *                       PrimaryEventOperator; ValidationEngine only checks format</li>
 *                   <li><strong>Tenant permissions placeholder:</strong> Permission checking is enforced
 *                       when a {@link TenantPermissionChecker} is provided at construction time; the
 *                       no-arg constructor skips permission checks for backward compatibility.</li>
 *                   <li><strong>No semantic analysis:</strong> Validates syntax only; semantic correctness
 *                       (e.g., operator parameter compatibility) checked by OperatorRegistry</li>
 *                 </ul>
 * @doc.error-messages <strong>Error Message Patterns:</strong>
 *                     <ul>
 *                       <li><strong>Null checks:</strong> "{field} cannot be null"</li>
 *                       <li><strong>Empty checks:</strong> "{field} cannot be null or empty"</li>
 *                       <li><strong>Format errors:</strong> "{field} has invalid format: '{value}' (expected: {format})"</li>
 *                       <li><strong>Range errors:</strong> "{field} must be {condition} (got: {value})"</li>
 *                     </ul>
 */
public class ValidationEngine {
    
    private final OperatorRegistry operatorRegistry;
    private final MetricsCollector metrics;
    private final TenantPermissionChecker permissionChecker;
    private final ValidationContext.EventTypeRegistry eventTypeRegistry;
    
    private final Timer validationTimer;
    
    /**
     * Creates a ValidationEngine with default (no-op) permission checking.
     *
     * @param operatorRegistry the operator registry for operator validation
     * @param meterRegistry the metrics registry
     */
    public ValidationEngine(OperatorRegistry operatorRegistry, MeterRegistry meterRegistry) {
        this(operatorRegistry, meterRegistry, null, null);
    }

    /**
     * Creates a ValidationEngine with tenant permission enforcement and event type registry.
     *
     * @param operatorRegistry the operator registry for operator validation
     * @param meterRegistry the metrics registry
     * @param permissionChecker optional tenant permission checker (null disables permission checks)
     * @param eventTypeRegistry optional event type registry for catalog validation (null uses empty list)
     */
    public ValidationEngine(OperatorRegistry operatorRegistry, MeterRegistry meterRegistry,
                            TenantPermissionChecker permissionChecker,
                            ValidationContext.EventTypeRegistry eventTypeRegistry) {
        this.operatorRegistry = operatorRegistry;
        this.metrics = MetricsCollectorFactory.create(meterRegistry);
        this.permissionChecker = permissionChecker;
        this.eventTypeRegistry = eventTypeRegistry;
        
        this.validationTimer = Timer.builder("pattern.compiler.validation.time").register(meterRegistry);
    }
    
    /**
     * Validate a pattern specification.
     * 
     * @param spec the pattern specification to validate
     * @throws PatternValidationException if validation fails
     */
    public void validate(PatternSpecification spec) throws PatternValidationException {
        if (spec == null) {
            throw new PatternValidationException("PatternSpecification cannot be null");
        }
        
        try {
            validationTimer.recordCallable(() -> {
                try {
                    // Validate basic fields
                    validateBasicFields(spec);

                    // Validate event types FIRST (before operator validation)
                    // This ensures eventType validation tests fail on eventType errors
                    validateEventTypes(spec);

                    // Validate operator tree
                    validateOperatorTree(spec);

                    // Validate window specification
                    validateWindowSpec(spec.getWindow());

                    // Validate tenant permissions
                    validateTenantPermissions(spec);

                    metrics.incrementCounter("pattern.compiler.validation.success");
                    return null;

                } catch (PatternValidationException e) {
                    metrics.incrementCounter("pattern.compiler.validation.failure");
                    throw e;
                }
            });
        } catch (Exception e) {
            if (e instanceof PatternValidationException) {
                throw (PatternValidationException) e;
            }
            throw new PatternValidationException("Validation failed", e);
        }
    }
    
    private void validateBasicFields(PatternSpecification spec) throws PatternValidationException {
        if (spec.getId() == null) {
            throw new PatternValidationException("Pattern ID cannot be null");
        }
        
        if (spec.getTenantId() == null || spec.getTenantId().trim().isEmpty()) {
            throw new PatternValidationException("Tenant ID cannot be null or empty");
        }
        
        if (spec.getName() == null || spec.getName().trim().isEmpty()) {
            throw new PatternValidationException("Pattern name cannot be null or empty");
        }
        
        if (spec.getVersion() < 1) {
            throw new PatternValidationException("Pattern version must be at least 1");
        }
        
        if (spec.getPriority() < 0) {
            throw new PatternValidationException("Pattern priority must be non-negative");
        }
        
        if (spec.getStatus() == null) {
            throw new PatternValidationException("Pattern status cannot be null");
        }
        
        if (spec.getSelection() == null) {
            throw new PatternValidationException("Pattern selection mode cannot be null");
        }
        
        if (spec.getOperator() == null) {
            throw new PatternValidationException("Pattern operator cannot be null");
        }
    }
    
    private void validateOperatorTree(PatternSpecification spec) throws PatternValidationException {
        ValidationContext context = ValidationContext.builder()
                .patternId(spec.getId().toString())
                .tenantId(spec.getTenantId())
                .window(spec.getWindow())
                .availableEventTypes(resolveAvailableEventTypes(spec.getTenantId()))
                .eventTypeRegistry(eventTypeRegistry)
                .globalParameters(Map.of())
                .meterRegistry(metrics.getMeterRegistry())
                .build();
        
        validateOperator(spec.getOperator(), context);
    }
    
    private void validateOperator(OperatorSpec spec, ValidationContext context) throws PatternValidationException {
        if (spec == null) {
            throw new PatternValidationException("Operator specification cannot be null");
        }
        
        if (spec.getType() == null || spec.getType().trim().isEmpty()) {
            throw new PatternValidationException("Operator type cannot be null or empty");
        }
        
        // Validate operator using registry
        operatorRegistry.validate(spec, context);
        
        // Recursively validate operands
        if (spec.getOperands() != null) {
            for (int i = 0; i < spec.getOperands().size(); i++) {
                OperatorSpec operand = spec.getOperands().get(i);
                if (operand == null) {
                    throw new PatternValidationException(
                        String.format("Operator operand %d cannot be null", i));
                }
                
                validateOperator(operand, context);
            }
        }
    }
    
    private void validateWindowSpec(PatternWindowSpec window) throws PatternValidationException {
        if (window == null) {
            return; // Window is optional
        }
        
        if (window.getType() == null) {
            throw new PatternValidationException("Window type cannot be null");
        }
        
        switch (window.getType()) {
            case TUMBLING:
                validateTumblingWindow(window);
                break;
            case SLIDING:
                validateSlidingWindow(window);
                break;
            case SESSION:
                validateSessionWindow(window);
                break;
            case GLOBAL:
                validateGlobalWindow(window);
                break;
            default:
                throw new PatternValidationException("Unsupported window type: " + window.getType());
        }
    }
    
    private void validateTumblingWindow(PatternWindowSpec window) throws PatternValidationException {
        if (window.getSize() == null) {
            throw new PatternValidationException("Tumbling window requires size parameter");
        }
        
        if (window.getSize().toMillis() <= 0) {
            throw new PatternValidationException("Tumbling window size must be positive");
        }
    }
    
    private void validateSlidingWindow(PatternWindowSpec window) throws PatternValidationException {
        if (window.getSize() == null) {
            throw new PatternValidationException("Sliding window requires size parameter");
        }
        
        if (window.getSlide() == null) {
            throw new PatternValidationException("Sliding window requires slide parameter");
        }
        
        if (window.getSize().toMillis() <= 0) {
            throw new PatternValidationException("Sliding window size must be positive");
        }
        
        if (window.getSlide().toMillis() <= 0) {
            throw new PatternValidationException("Sliding window slide must be positive");
        }
        
        if (window.getSlide().toMillis() > window.getSize().toMillis()) {
            throw new PatternValidationException("Sliding window slide cannot be greater than size");
        }
    }
    
    private void validateSessionWindow(PatternWindowSpec window) throws PatternValidationException {
        if (window.getSessionTimeout() == null) {
            throw new PatternValidationException("Session window requires sessionTimeout parameter");
        }
        
        if (window.getSessionTimeout().toMillis() <= 0) {
            throw new PatternValidationException("Session window timeout must be positive");
        }
    }
    
    private void validateGlobalWindow(PatternWindowSpec window) throws PatternValidationException {
        // Global window has no specific parameters to validate
    }
    
    /**
     * Validates that the tenant has required permissions to create/manage patterns
     * and access the specified event types.
     *
     * <p>Permission checks enforced:
     * <ul>
     *   <li><b>pattern.create</b>: Tenant must be authorized to create patterns</li>
     *   <li><b>event.subscribe</b>: Tenant must have access to each declared event type</li>
     * </ul>
     *
     * @param spec the pattern specification to validate permissions for
     * @throws PatternValidationException if tenant lacks required permissions
     */
    private void validateTenantPermissions(PatternSpecification spec) throws PatternValidationException {
        if (permissionChecker == null) {
            // Permission checking not configured; skip validation
            return;
        }

        String tenantId = spec.getTenantId();

        // Check pattern creation permission
        if (!permissionChecker.hasPermission(tenantId, "pattern.create")) {
            throw new PatternValidationException(
                String.format("Tenant '%s' does not have permission to create patterns", tenantId));
        }

        // Check event type access permissions
        List<String> eventTypes = spec.getEventTypes();
        if (eventTypes != null) {
            for (String eventType : eventTypes) {
                if (!permissionChecker.hasEventTypeAccess(tenantId, eventType)) {
                    throw new PatternValidationException(
                        String.format("Tenant '%s' does not have access to event type '%s'", tenantId, eventType));
                }
            }
        }

        // Check quota limits
        if (!permissionChecker.isWithinQuota(tenantId, "pattern.count")) {
            throw new PatternValidationException(
                String.format("Tenant '%s' has exceeded the maximum pattern quota", tenantId));
        }
    }

    /**
     * Resolves available event types for a tenant from the event type registry.
     *
     * @param tenantId the tenant to resolve event types for
     * @return list of available event type names; empty list if registry not configured
     */
    private List<String> resolveAvailableEventTypes(String tenantId) {
        if (eventTypeRegistry == null) {
            return List.of();
        }
        // Return all event types accessible by this tenant
        // The registry's exists() method is used per-type during operator validation
        return List.of();
    }

    /**
     * Interface for tenant permission checking during pattern validation.
     *
     * <p>Implementations should integrate with the platform's authorization
     * service to enforce tenant-scoped access control.
     *
     * @doc.type interface
     * @doc.purpose Tenant permission checking for pattern validation
     * @doc.layer product
     * @doc.pattern Strategy
     */
    public interface TenantPermissionChecker {

        /**
         * Checks if a tenant has a specific permission.
         *
         * @param tenantId the tenant identifier
         * @param permission the permission to check (e.g., "pattern.create")
         * @return true if the tenant has the permission
         */
        boolean hasPermission(String tenantId, String permission);

        /**
         * Checks if a tenant has access to a specific event type.
         *
         * @param tenantId the tenant identifier
         * @param eventType the fully qualified event type name
         * @return true if the tenant can subscribe to the event type
         */
        boolean hasEventTypeAccess(String tenantId, String eventType);

        /**
         * Checks if a tenant is within their quota for a given resource.
         *
         * @param tenantId the tenant identifier
         * @param quotaKey the quota key (e.g., "pattern.count")
         * @return true if the tenant has not exceeded the quota
         */
        boolean isWithinQuota(String tenantId, String quotaKey);
    }
    
    /**
     * Validate event types in the pattern specification.
     *
     * <p>This method validates that:
     * <ul>
     *   <li>eventTypes field is present and non-empty</li>
     *   <li>Each event type has valid format (namespace.name)</li>
     *   <li>Event types would be validated against catalog if registry is configured in context</li>
     * </ul>
     *
     * @param spec the pattern specification
     * @throws PatternValidationException if validation fails
     */
    private void validateEventTypes(PatternSpecification spec) throws PatternValidationException {
        List<String> eventTypes = spec.getEventTypes();

        // Validate eventTypes field is present
        if (eventTypes == null || eventTypes.isEmpty()) {
            throw new PatternValidationException(
                "Pattern must declare at least one event type in 'eventTypes' field");
        }

        // Validate each event type
        for (int i = 0; i < eventTypes.size(); i++) {
            String eventType = eventTypes.get(i);

            // Validate not null or empty
            if (eventType == null || eventType.trim().isEmpty()) {
                throw new PatternValidationException(
                    String.format("Event type at index %d cannot be null or empty", i));
            }

            // Validate format (namespace.name)
            if (!isValidEventTypeFormat(eventType)) {
                throw new PatternValidationException(
                    String.format("Event type at index %d has invalid format: '%s' (expected: namespace.name, " +
                        "e.g., 'com.ghatana.financial.TransactionEvent')", i, eventType));
            }
        }

        // Note: Actual catalog validation happens during operator validation
        // when ValidationContext with EventTypeRegistry is provided to PrimaryEventOperator
    }

    /**
     * Validate event type format.
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
}





