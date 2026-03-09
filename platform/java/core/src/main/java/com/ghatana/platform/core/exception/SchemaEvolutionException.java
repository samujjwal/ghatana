package com.ghatana.platform.core.exception;

/**
 * Exception thrown when schema changes break backward/forward compatibility.
 *
 * <h2>Purpose</h2>
 * Signals that a schema change is incompatible with existing data or clients:
 * <ul>
 *   <li>Removing required fields (breaks existing clients)</li>
 *   <li>Changing field types (breaks serialization)</li>
 *   <li>Adding required fields without defaults (breaks existing data)</li>
 *   <li>Renaming fields (breaks field mapping)</li>
 *   <li>Migration errors during schema transition</li>
 * </ul>
 *
 * <h2>Distinction from Similar Exceptions</h2>
 * <table>
 *   <tr>
 *     <th>Exception</th>
 *     <th>Scenario</th>
 *   </tr>
 *   <tr>
 *     <td>SchemaValidationException</td>
 *     <td>Data doesn't match current schema</td>
 *   </tr>
 *   <tr>
 *     <td>✅ SchemaEvolutionException</td>
 *     <td>Schema change itself is incompatible</td>
 *   </tr>
 *   <tr>
 *     <td>InvalidQueryException</td>
 *     <td>Query references wrong schema version</td>
 *   </tr>
 * </table>
 *
 * <h2>Usage Examples</h2>
 * {@code
 * // 1. Detecting incompatible schema change
 * SchemaVersion oldSchema = registry.getSchema("user", 1);
 * SchemaVersion newSchema = SchemaVersion.of("user", 2);
 * 
 * if (!oldSchema.isCompatibleWith(newSchema)) {
 *     throw new SchemaEvolutionException(
 *         "Schema change breaks compatibility: " +
 *         "removing required field 'email' without providing default"
 *     );
 * }
 *
 * // 2. Migration failure
 * try {
 *     migrator.migrateData(oldSchema, newSchema, data);
 * } catch (MigrationException me) {
 *     throw new SchemaEvolutionException(
 *         "Failed to migrate data from schema v1 to v2",
 *         me
 *     );
 * }
 *
 * // 3. With custom error code
 * throw new SchemaEvolutionException(
 *     ErrorCode.SCHEMA_INCOMPATIBILITY,
 *     "Cannot remove field 'id' - breaks all existing clients"
 * );
 *
 * // 4. Detecting breaking change in events
 * if (eventSchema.removedMandatoryField("eventId")) {
 *     throw new SchemaEvolutionException(
 *         "Event schema change breaks compatibility"
 *     );
 * }
 * }
 *
 * <h2>Schema Versioning Pattern</h2>
 * <pre>
 * Schema v1 → v2 → v3 (compatible chain)
 * Schema v2 contains: all v1 fields + new optional fields
 * Schema v3 contains: all v2 fields + new optional fields
 *
 * Breaking change: Removing field, changing type
 * Non-breaking change: Adding optional field, adding default to required
 * </pre>
 *
 * <h2>Caught By</h2>
 * <ul>
 *   <li>Schema registry deployment hooks</li>
 *   <li>Admin APIs (prevented from deploying breaking changes)</li>
 *   <li>Migration jobs (for data rewriting)</li>
 * </ul>
 *
 * <h2>Common Error Messages</h2>
 * <ul>
 *   <li>"Cannot remove required field 'userId' without migration plan"</li>
 *   <li>"Cannot change field 'status' type from ENUM to STRING"</li>
 *   <li>"Adding required field 'createdAt' without default breaks existing records"</li>
 *   <li>"Schema renaming not supported: use alias instead"</li>
 *   <li>"Migration from v1 to v3 failed: no direct upgrade path"</li>
 * </ul>
 *
 * <h2>Prevention Strategies</h2>
 * <ul>
 *   <li>Always provide defaults for new required fields</li>
 *   <li>Use aliases instead of renaming fields</li>
 *   <li>Mark deprecated fields instead of removing them</li>
 *   <li>Use Protobuf reserved field numbers for removed fields</li>
 *   <li>Test schema changes with real data before deploying</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * Thread-safe after construction (immutable state).
 *
 * <h2>See Also</h2>
 * <ul>
 *   <li>Protobuf: Field reserved numbers, backward compatibility checks</li>
 *   <li>JSON Schema: draft versions, schema validation tools</li>
 *   <li>EventCloud: Event version management, compatibility checking</li>
 * </ul>
 *
 * @see SchemaValidationException For data validation failures
 * @see BaseException Parent exception class
 * @doc.type exception
 * @doc.layer core
 * @doc.purpose exception for schema changes that break compatibility
 * @doc.pattern domain-exception schema-versioning compatibility-checking
 */
public class SchemaEvolutionException extends BaseException {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Constructs a SchemaEvolutionException with an error message.
     *
     * <p>Use when you've detected an incompatible schema change and want to
     * provide specific details about what breaks compatibility.
     *
     * <p><b>Usage:</b>
     * <pre>{@code
     * throw new SchemaEvolutionException(
     *     "Cannot remove required field 'userId' - breaks all v1 clients"
     * );
     * }</pre>
     *
     * @param message description of schema incompatibility (never null)
     */
    public SchemaEvolutionException(String message) {
        super(ErrorCode.VALIDATION_ERROR, message);
    }

    /**
     * Constructs a SchemaEvolutionException with message and root cause.
     *
     * <p>Use when catching a lower-level exception (MigrationException, 
     * SchemaRegistryException) during schema evolution and converting to
     * SchemaEvolutionException for uniform handling.
     *
     * <p><b>Usage:</b>
     * <pre>{@code
     * try {
     *     schemaMigrator.migrate(oldSchema, newSchema, data);
     * } catch (MigrationException me) {
     *     throw new SchemaEvolutionException(
     *         "Failed to migrate records from v1 to v2",
     *         me
     *     );
     * }
     * }</pre>
     *
     * @param message description of evolution failure (never null)
     * @param cause the underlying exception (never null)
     */
    public SchemaEvolutionException(String message, Throwable cause) {
        super(ErrorCode.VALIDATION_ERROR, message, cause);
    }

    /**
     * Constructs a SchemaEvolutionException from a root cause only.
     *
     * <p>Use when the cause exception's message already clearly describes
     * why the schema change is problematic (e.g., validation framework error).
     *
     * <p><b>Usage:</b>
     * <pre>{@code
     * try {
     *     compatibilityChecker.validate(oldSchema, newSchema);
     * } catch (ValidationException ve) {
     *     throw new SchemaEvolutionException(ve);
     * }
     * }</pre>
     *
     * @param cause the underlying exception (never null)
     */
    public SchemaEvolutionException(Throwable cause) {
        super(ErrorCode.VALIDATION_ERROR, cause);
    }
    
    /**
     * Constructs a SchemaEvolutionException with custom error code and message.
     *
     * <p>Use when you need fine-grained error codes for different compatibility
     * scenarios (removal vs type change vs migration failure).
     *
     * <p><b>Usage:</b>
     * <pre>{@code
     * throw new SchemaEvolutionException(
     *     ErrorCode.SCHEMA_INCOMPATIBILITY,
     *     "Field 'status' type change: ENUM→STRING breaks deserializers"
     * );
     * }</pre>
     *
     * @param errorCode specific error code for this evolution problem (never null)
     * @param message description of schema incompatibility (never null)
     */
    public SchemaEvolutionException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
