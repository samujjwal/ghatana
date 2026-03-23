package com.ghatana.pattern.api.exception;

/**
 * Exception thrown when pattern validation fails during compilation.
 * 
 * <p>PatternValidationException is raised during the validation phase when:
 * <ul>
 *   <li>Pattern specification violates schema constraints</li>
 *   <li>Operator specifications are invalid or malformed</li>
 *   <li>Tenant lacks permissions for requested operators/event types</li>
 *   <li>Event types are not registered in the catalog</li>
 *   <li>Window specifications have invalid parameters</li>
 * </ul>
 * 
 * @doc.pattern Exception Pattern (validation errors), Value Object Pattern (error details)
 * @doc.compiler-phase Validation (thrown during first compilation phase)
 * @doc.threading Thread-safe (immutable after construction)
 * @doc.apiNote Catch during compile() to handle validation errors gracefully
 * @doc.errorHandling Contains patternId and specific validation error for debugging
 * 
 * <h2>Common Validation Errors</h2>
 * <table border="1" cellpadding="5">
 *   <tr>
 *     <th>Error Category</th>
 *     <th>Example Message</th>
 *     <th>Resolution</th>
 *   </tr>
 *   <tr>
 *     <td>Schema Violation</td>
 *     <td>"Pattern ID cannot be null"</td>
 *     <td>Provide required fields in PatternSpecification</td>
 *   </tr>
 *   <tr>
 *     <td>Invalid Operator</td>
 *     <td>"Unknown operator type: CUSTOM_FILTER"</td>
 *     <td>Use registered operator types from catalog</td>
 *   </tr>
 *   <tr>
 *     <td>Permission Denied</td>
 *     <td>"Tenant 'acme' cannot access event type 'internal.audit'"</td>
 *     <td>Request access or use permitted event types</td>
 *   </tr>
 *   <tr>
 *     <td>Window Error</td>
 *     <td>"Window duration must be positive"</td>
 *     <td>Specify valid duration (> 0)</td>
 *   </tr>
 * </table>
 */
public class PatternValidationException extends RuntimeException {
    
    private final String patternId;
    private final String validationError;
    
    public PatternValidationException(String message) {
        super(message);
        this.patternId = null;
        this.validationError = message;
    }
    
    public PatternValidationException(String patternId, String message) {
        super("Pattern validation failed for pattern " + patternId + ": " + message);
        this.patternId = patternId;
        this.validationError = message;
    }
    
    public PatternValidationException(String message, Throwable cause) {
        super(message, cause);
        this.patternId = null;
        this.validationError = message;
    }
    
    public PatternValidationException(String patternId, String message, Throwable cause) {
        super("Pattern validation failed for pattern " + patternId + ": " + message, cause);
        this.patternId = patternId;
        this.validationError = message;
    }
    
    public String getPatternId() {
        return patternId;
    }
    
    public String getValidationError() {
        return validationError;
    }
}

