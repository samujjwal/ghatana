package com.ghatana.platform.core.exception;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * Base exception class for all application-level exceptions.
 *
 * @doc.type class
 * @doc.purpose Root exception for the platform exception hierarchy
 * @doc.layer core
 * @doc.pattern Exception
 *
 * <h2>Purpose</h2>
 * Provides a standardized, type-safe exception hierarchy with:
 * <ul>
 *   <li>Structured error codes (not just messages)</li>
 *   <li>Contextual metadata (debugging aids)</li>
 *   <li>Automatic error message mapping</li>
 *   <li>Tenant-scoped error tracking</li>
 * </ul>
 *
 * <h2>Exception Hierarchy</h2>
 * <pre>
 * BaseException (root)
 *   ├── ServiceException (business logic failures)
 *   ├── ResourceNotFoundException (404 scenarios)
 *   ├── SchemaValidationException (schema mismatches)
 *   ├── SchemaEvolutionException (compatibility breaks)
 *   ├── InvalidQueryException (malformed queries)
 *   ├── RegistryException (catalog lookup failures)
 *   └── EventCreationException (event construction errors)
 * </pre>
 *
 * <h2>Key Differences from Standard Exception</h2>
 * <table>
 *   <tr>
 *     <th>Aspect</th>
 *     <th>Standard Exception</th>
 *     <th>BaseException</th>
 *   </tr>
 *   <tr>
 *     <td>Error identification</td>
 *     <td>Message string only</td>
 *     <td>Structured ErrorCode enum</td>
 *   </tr>
 *   <tr>
 *     <td>Metadata</td>
 *     <td>No structured context</td>
 *     <td>Map of debugging context</td>
 *   </tr>
 *   <tr>
 *     <td>Logging/monitoring</td>
 *     <td>String parsing required</td>
 *     <td>Direct error code queries</td>
 *   </tr>
 *   <tr>
 *     <td>Error codes</td>
 *     <td>N/A</td>
 *     <td>HTTP status, internal code, etc.</td>
 *   </tr>
 * </table>
 *
 * <h2>Usage Examples</h2>
 * {@code
 * // 1. Throw with error code only
 * throw new BaseException(ErrorCode.INVALID_INPUT);
 *
 * // 2. Throw with custom message (override default)
 * throw new BaseException(
 *     ErrorCode.INVALID_INPUT,
 *     "User ID must be a positive integer"
 * );
 *
 * // 3. Throw with cause (exception chaining)
 * try {
 *     database.save(entity);
 * } catch (SQLException e) {
 *     throw new BaseException(
 *         ErrorCode.DATABASE_ERROR,
 *         "Failed to persist entity",
 *         e  // Original exception as cause
 *     );
 * }
 *
 * // 4. Add debugging metadata
 * BaseException ex = new BaseException(ErrorCode.AUTHORIZATION_FAILED);
 * ex.addMetadata("userId", 12345);
 * ex.addMetadata("requiredRole", "ADMIN");
 * ex.addMetadata("userRoles", "USER,GUEST");
 * throw ex;
 *
 * // 5. Catch and inspect in handlers
 * try {
 *     processOrder(order);
 * } catch (BaseException e) {
 *     ErrorCode code = e.getErrorCode();
 *     int httpStatus = code.getHttpStatus();
 *     String message = code.getDefaultMessage();
 *     
 *     log.error("Operation failed [code={}, status={}]",
 *         code, httpStatus, e);
 *     
 *     return ResponseEntity
 *         .status(httpStatus)
 *         .body(new ErrorResponse(code.getCode(), message));
 * }
 * }
 *
 * <h2>Architecture Role</h2>
 * <ul>
 *   <li><b>Layer:</b> Application-layer exception wrapper</li>
 *   <li><b>Usage:</b> All business logic should throw BaseException subclasses</li>
 *   <li><b>Benefits:</b>
 *     <ul>
 *       <li>Consistent error handling across codebase</li>
 *       <li>Error codes enable programmatic error handling</li>
 *       <li>Metadata aids debugging without leaking sensitive info</li>
 *       <li>HTTP layer can directly map error codes to status codes</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <h2>ErrorCode Integration</h2>
 * Every BaseException includes an {@link ErrorCode} enum providing:
 * <pre>{@code
 * public interface ErrorCode {
 *     String getCode();          // Unique error identifier (e.g., "INVALID_USER_ID")
 *     String getDefaultMessage(); // Standard message (e.g., "Invalid user ID")
 *     int getHttpStatus();        // HTTP status code (200-599)
 *     String getCategory();       // Category (INPUT, RESOURCE, AUTH, SYSTEM, etc.)
 * }
 * }</pre>
 *
 * <h2>Metadata Best Practices</h2>
 * <ul>
 *   <li><b>Include:</b> Tenant ID, resource IDs, field names, validation details</li>
 *   <li><b>Exclude:</b> Passwords, tokens, personal info, database URLs</li>
 *   <li><b>Format:</b> Use consistent keys (userId, tenantId, fieldName, attempted, required)</li>
 * </ul>
 *
 * <h2>Exception Chaining (Cause)</h2>
 * <p>
 * When catching low-level exceptions (SQLException, IOException), always:
 * 1. Create BaseException with appropriate ErrorCode
 * 2. Pass original exception as cause
 * 3. Log full stack trace for debugging
 * </p>
 * <pre>{@code
 * try {
 *     // Low-level operation that might throw SQLException
 *     ResultSet rs = statement.executeQuery();
 * } catch (SQLException cause) {
 *     throw new BaseException(
 *         ErrorCode.DATABASE_ERROR,
 *         "Query execution failed",
 *         cause  // Preserves original stack trace
 *     );
 * }
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * Not thread-safe for metadata mutations. Each exception instance should be
 * single-threaded (throw immediately after creation).
 *
 * <h2>Performance Characteristics</h2>
 * <ul>
 *   <li><b>Construction</b>: O(1) - simple field assignment</li>
 *   <li><b>Stack trace capture</b>: O(depth) - platform-dependent</li>
 *   <li><b>addMetadata()</b>: O(1) amortized for HashMap</li>
 *   <li><b>Serialization</b>: O(metadata.size()) if serialized</li>
 * </ul>
 *
 * @see ErrorCode Error code abstraction
 * @see ServiceException Common service-layer exception
 * @see RuntimeException Parent exception class
 * @doc.type exception
 * @doc.layer core
 * @doc.purpose base exception for all application-layer errors with error codes and metadata
 * @doc.pattern exception-hierarchy error-code-pattern structured-error-handling
 */
@Getter
public class BaseException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, Object> metadata;

    /**
     * Constructs a BaseException with only an error code.
     *
     * <p>The error message is populated from {@code errorCode.getDefaultMessage()}.
     * Metadata is initialized as an empty map and can be populated via
     * {@link #addMetadata(String, Object)}.
     *
     * <p><b>Usage:</b>
     * <pre>{@code
     * throw new BaseException(ErrorCode.INVALID_INPUT);
     * }</pre>
     *
     * @param errorCode the error code identifying this exception type (never null)
     * @throws NullPointerException if errorCode is null
     */
    public BaseException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
        this.metadata = new HashMap<>();
    }

    /**
     * Constructs a BaseException with an error code and custom message.
     *
     * <p>Use this when you need to override the default message from the
     * error code with domain-specific context (e.g., field name, validation rule).
     *
     * <p><b>Usage:</b>
     * <pre>{@code
     * throw new BaseException(
     *     ErrorCode.INVALID_INPUT,
     *     "Email must be in user@domain.com format"
     * );
     * }</pre>
     *
     * @param errorCode the error code identifying this exception type (never null)
     * @param message the custom error message (never null)
     * @throws NullPointerException if errorCode or message is null
     */
    public BaseException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.metadata = new HashMap<>();
    }

    /**
     * Constructs a BaseException with error code, message, and root cause.
     *
     * <p>Use this when catching and rethrowing lower-level exceptions
     * (SQLException, IOException, etc.) to preserve the full stack trace
     * for debugging.
     *
     * <p><b>Usage:</b>
     * <pre>{@code
     * try {
     *     database.executeUpdate(sql);
     * } catch (SQLException sqlEx) {
     *     throw new BaseException(
     *         ErrorCode.DATABASE_ERROR,
     *         "Failed to update user record",
     *         sqlEx  // Preserved for debugging
     *     );
     * }
     * }</pre>
     *
     * @param errorCode the error code identifying this exception type (never null)
     * @param message the custom error message (never null)
     * @param cause the underlying exception that triggered this error (never null)
     * @throws NullPointerException if any parameter is null
     */
    public BaseException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.metadata = new HashMap<>();
    }

    /**
     * Creates a new BaseException with the specified error code and cause.
     *
     * @param errorCode The error code
     * @param cause The cause
     */
    public BaseException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getDefaultMessage(), cause);
        this.errorCode = errorCode;
        this.metadata = new HashMap<>();
    }

    /**
     * Creates a new BaseException with the specified error code, message, cause, and metadata.
     *
     * @param errorCode The error code
     * @param message The error message
     * @param cause The cause
     * @param metadata The metadata
     */
    public BaseException(ErrorCode errorCode, String message, Throwable cause, Map<String, Object> metadata) {
        super(message, cause);
        this.errorCode = errorCode;
        this.metadata = new HashMap<>(metadata);
    }

    /**
     * Adds metadata to the exception.
     *
     * @param key The metadata key
     * @param value The metadata value
     * @return This exception
     */
    public BaseException addMetadata(String key, Object value) {
        metadata.put(key, value);
        return this;
    }

    /**
     * Adds multiple metadata entries to the exception.
     *
     * @param metadata The metadata entries
     * @return This exception
     */
    public BaseException addMetadata(Map<String, Object> metadata) {
        this.metadata.putAll(metadata);
        return this;
    }

    /**
     * Gets a metadata value by key.
     *
     * @param key The metadata key
     * @param <T> The expected type of the metadata value
     * @return The metadata value, or null if not found
     */
    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key) {
        return (T) metadata.get(key);
    }

    /**
     * Gets the error code string.
     *
     * @return The error code string
     */
    public String getErrorCodeString() {
        return errorCode.getCode();
    }

    /**
     * Gets a string representation of the exception.
     *
     * @return The string representation
     */
    @Override
    public String toString() {
        return String.format("%s [%s]: %s %s",
                getClass().getSimpleName(),
                errorCode.getCode(),
                getMessage(),
                metadata.isEmpty() ? "" : "Metadata: " + metadata);
    }
}
