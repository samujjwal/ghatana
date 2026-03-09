package com.ghatana.platform.core.exception;

import java.util.HashMap;

/**
 * Exception thrown when data fails schema validation.
 *
 * <h2>Purpose</h2>
 * Signals that incoming or outgoing data does not conform to its schema:
 * <ul>
 *   <li>JSON structure doesn't match OpenAPI schema</li>
 *   <li>Protobuf serialization/deserialization fails</li>
 *   <li>Required fields are missing or null</li>
 *   <li>Field types don't match schema definition</li>
 *   <li>Enum values are invalid</li>
 * </ul>
 *
 * <h2>When to Use</h2>
 * <table>
 *   <tr>
 *     <th>Scenario</th>
 *     <th>Exception Type</th>
 *   </tr>
 *   <tr>
 *     <td>Data doesn't match schema</td>
 *     <td>✅ SchemaValidationException</td>
 *   </tr>
 *   <tr>
 *     <td>Schema itself is invalid</td>
 *     <td>SchemaEvolutionException</td>
 *   </tr>
 *   <tr>
 *     <td>Generic validation error</td>
 *     <td>ValidationException (parent)</td>
 *   </tr>
 * </table>
 *
 * <h2>Usage Examples</h2>
 * {@code
 * // 1. Simple message-based validation failure
 * if (!isValidEmail(userEmail)) {
 *     throw new SchemaValidationException(
 *         "email field must be valid email format"
 *     );
 * }
 *
 * // 2. With root cause (e.g., JSON parsing)
 * try {
 *     ObjectMapper mapper = new ObjectMapper();
 *     UserDTO user = mapper.readValue(json, UserDTO.class);
 * } catch (JsonMappingException jme) {
 *     throw new SchemaValidationException(
 *         "Failed to parse user JSON",
 *         jme  // Original parsing error
 *     );
 * }
 *
 * // 3. Schema name + reason (preferred for clarity)
 * if (event.getEventTime() == null) {
 *     throw new SchemaValidationException(
 *         "Event",  // Schema name
 *         "eventTime field is required and cannot be null"
 *     );
 * }
 *
 * // 4. Batch validation with multiple errors
 * List<String> errors = new ArrayList<>();
 * if (user.getAge() < 0) errors.add("age must be non-negative");
 * if (!user.getEmail().contains("@")) errors.add("email must contain @");
 * 
 * if (!errors.isEmpty()) {
 *     throw new SchemaValidationException(
 *         "UserSchema",
 *         String.join("; ", errors)
 *     );
 * }
 * }
 *
 * <h2>Caught By</h2>
 * <ul>
 *   <li>HTTP exception handlers → HTTP 400 Bad Request</li>
 *   <li>gRPC exception handlers → INVALID_ARGUMENT error</li>
 *   <li>Validation interceptors (input validation)</li>
 * </ul>
 *
 * <h2>Common Error Messages</h2>
 * <ul>
 *   <li>"Required field 'userId' is missing"</li>
 *   <li>"Field 'email' must match pattern: .+@.+"</li>
 *   <li>"Field 'status' must be one of: ACTIVE, INACTIVE, SUSPENDED"</li>
 *   <li>"Field 'age' must be an integer between 0 and 150"</li>
 *   <li>"Nested object 'address' is malformed"</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * Thread-safe after construction (immutable state).
 *
 * <h2>Best Practices</h2>
 * <ul>
 *   <li>Include schema/field names in messages (for debugging)</li>
 *   <li>Specify validation rule in reason (the "why" it failed)</li>
 *   <li>Don't leak implementation details (e.g., SQL, internal types)</li>
 *   <li>Include cause when wrapping lower-level exceptions</li>
 * </ul>
 *
 * @see ValidationException Parent exception for all validation errors
 * @see SchemaEvolutionException When schema definition itself is invalid
 * @doc.type exception
 * @doc.layer core
 * @doc.purpose exception for data that fails schema validation
 * @doc.pattern validation-exception schema-validation error-handling
 */
public class SchemaValidationException extends ValidationException {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Constructs a SchemaValidationException with a validation error message.
     *
     * <p>Use when validating data and a field/constraint fails. Message should
     * include which field failed and why.
     *
     * <p><b>Usage:</b>
     * <pre>{@code
     * throw new SchemaValidationException("email field must be in user@domain format");
     * }</pre>
     *
     * @param message description of validation failure (never null)
     */
    public SchemaValidationException(String message) {
        super(message);
    }

    /**
     * Constructs a SchemaValidationException with message and root cause.
     *
     * <p>Use when catching a lower-level parsing/validation exception
     * (e.g., JsonMappingException from Jackson, or validation framework errors)
     * and converting to SchemaValidationException for uniform handling.
     *
     * <p><b>Usage:</b>
     * <pre>{@code
     * try {
     *     jsonSchema.validate(data);
     * } catch (ValidationException ve) {
     *     throw new SchemaValidationException(
     *         "JSON validation failed",
     *         ve  // Original validation error preserved
     *     );
     * }
     * }</pre>
     *
     * @param message description of validation failure (never null)
     * @param cause the underlying validation/parsing exception (never null)
     */
    public SchemaValidationException(String message, Throwable cause) {
        super(message, new HashMap<>());
        initCause(cause);
    }
    
    /**
     * Constructs a SchemaValidationException with schema name and specific reason.
     *
     * <p>Preferred constructor for clarity. Automatically formats message as:
     * "Schema validation failed for 'schemaName': reason"
     *
     * <p><b>Usage (Recommended):</b>
     * <pre>{@code
     * // Clear, structured error message
     * throw new SchemaValidationException(
     *     "Event",  // Schema name
     *     "eventTime field is required and cannot be null"
     * );
     * 
     * // Results in message:
     * // "Schema validation failed for 'Event': eventTime field is required and cannot be null"
     * }</pre>
     *
     * @param schemaName name of the schema that validation failed for (never null)
     * @param reason specific reason why validation failed (never null)
     */
    public SchemaValidationException(String schemaName, String reason) {
        super("Schema validation failed for '" + schemaName + "': " + reason);
    }
}
