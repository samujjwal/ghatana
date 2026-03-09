package com.ghatana.platform.core.exception;

/**
 * Exception thrown when a query is invalid or malformed.
 *
 * <h2>Purpose</h2>
 * Signals that a query (SQL, EventCloud, GraphQL, etc.) has:
 * <ul>
 *   <li>Syntax errors (missing keywords, invalid operators)</li>
 *   <li>Invalid parameters (type mismatches, null where not allowed)</li>
 *   <li>Unsupported operations</li>
 *   <li>Schema mismatches (query references non-existent fields/tables)</li>
 *   <li>Permission issues (attempting to access unauthorized tables)</li>
 * </ul>
 *
 * <h2>When to Use</h2>
 * <table>
 *   <tr>
 *     <th>Scenario</th>
 *     <th>Exception Type</th>
 *   </tr>
 *   <tr>
 *     <td>Query syntax is invalid</td>
 *     <td>✅ InvalidQueryException</td>
 *   </tr>
 *   <tr>
 *     <td>Database constraint violated</td>
 *     <td>BaseException (generic)</td>
 *   </tr>
 *   <tr>
 *     <td>Query execution timeout</td>
 *     <td>BaseException (generic)</td>
 *   </tr>
 *   <tr>
 *     <td>Query parameter is invalid (e.g., enum value)</td>
 *     <td>✅ InvalidQueryException</td>
 *   </tr>
 * </table>
 *
 * <h2>Usage Examples</h2>
 * {@code
 * // 1. SQL query syntax error
 * try {
 *     PreparedStatement stmt = conn.prepareStatement(userQuery);
 * } catch (SQLException e) {
 *     throw new InvalidQueryException(
 *         "SQL query has syntax error: " + userQuery,
 *         e
 *     );
 * }
 *
 * // 2. Query parameter validation
 * if (offset < 0 || limit > 10000) {
 *     throw new InvalidQueryException(
 *         "Query parameters invalid: offset must be >= 0, limit must be <= 10000"
 *     );
 * }
 *
 * // 3. Custom error code with message
 * throw new InvalidQueryException(
 *     ErrorCode.INVALID_QUERY_PARAMETER,
 *     "Sort field 'unknownField' does not exist in schema"
 * );
 *
 * // 4. Parsing user-provided query
 * try {
 *     Query query = queryParser.parse(userInput);
 * } catch (ParseException pe) {
 *     throw new InvalidQueryException(
 *         "Failed to parse query: " + pe.getLocalizedMessage(),
 *         pe
 *     );
 * }
 * }
 *
 * <h2>Caught By</h2>
 * <ul>
 *   <li>HTTP exception handlers → HTTP 400 Bad Request</li>
 *   <li>Query validation interceptors</li>
 *   <li>API gateway validators</li>
 * </ul>
 *
 * <h2>Common Error Messages</h2>
 * <ul>
 *   <li>"Query must include WHERE clause for safety"</li>
 *   <li>"Sort field 'unknownField' does not exist"</li>
 *   <li>"Limit parameter must be between 1 and 10000"</li>
 *   <li>"Filter expression has syntax error at position 42"</li>
 *   <li>"Aggregation function SUM requires numeric field"</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * Thread-safe after construction (immutable state).
 *
 * <h2>Best Practices</h2>
 * <ul>
 *   <li>Include the problematic query/parameter in message</li>
 *   <li>Specify what makes it invalid (syntax, range, type)</li>
 *   <li>Suggest correction if possible</li>
 *   <li>Never include sensitive data (passwords, full tables)</li>
 * </ul>
 *
 * @see BaseException Parent exception class
 * @see SchemaValidationException For data-to-schema mismatches
 * @doc.type exception
 * @doc.layer core
 * @doc.purpose exception for invalid or malformed queries
 * @doc.pattern domain-exception query-validation error-handling
 */
public class InvalidQueryException extends BaseException {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Constructs an InvalidQueryException with an error message.
     *
     * <p>Use for query validation failures where you want to provide
     * specific details about what makes the query invalid.
     *
     * <p><b>Usage:</b>
     * <pre>{@code
     * throw new InvalidQueryException(
     *     "Limit parameter 50000 exceeds maximum of 10000"
     * );
     * }</pre>
     *
     * @param message description of why query is invalid (never null)
     */
    public InvalidQueryException(String message) {
        super(ErrorCode.QUERY_ERROR, message);
    }

    /**
     * Constructs an InvalidQueryException with message and root cause.
     *
     * <p>Use when catching a lower-level exception (SQLException, ParseException)
     * and converting to InvalidQueryException for uniform handling.
     *
     * <p><b>Usage:</b>
     * <pre>{@code
     * try {
     *     queryValidator.validate(query);
     * } catch (ValidationException ve) {
     *     throw new InvalidQueryException(
     *         "Query validation failed",
     *         ve
     *     );
     * }
     * }</pre>
     *
     * @param message description of validation failure (never null)
     * @param cause the underlying exception (never null)
     */
    public InvalidQueryException(String message, Throwable cause) {
        super(ErrorCode.QUERY_ERROR, message, cause);
    }

    /**
     * Constructs an InvalidQueryException from a root cause only.
     *
     * <p>Use when the cause exception's message already provides sufficient
     * context about why the query is invalid.
     *
     * <p><b>Usage:</b>
     * <pre>{@code
     * try {
     *     parser.parse(query);
     * } catch (ParseException pe) {
     *     throw new InvalidQueryException(pe);
     * }
     * }</pre>
     *
     * @param cause the underlying exception (never null)
     */
    public InvalidQueryException(Throwable cause) {
        super(ErrorCode.QUERY_ERROR, cause);
    }
    
    /**
     * Constructs an InvalidQueryException with a custom error code and message.
     *
     * <p>Use when you need fine-grained error code control for different
     * query failure scenarios (syntax vs parameter vs permission).
     *
     * <p><b>Usage:</b>
     * <pre>{@code
     * throw new InvalidQueryException(
     *     ErrorCode.UNAUTHORIZED_QUERY,
     *     "Cannot query sensitive table 'users_pii'"
     * );
     * }</pre>
     *
     * @param errorCode specific error code for this query failure (never null)
     * @param message description of query problem (never null)
     */
    public InvalidQueryException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
