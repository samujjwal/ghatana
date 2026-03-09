package com.ghatana.core.database.jdbc;

/**
 * Unchecked runtime exception for JDBC operations with cause chain preservation.
 *
 * <p><b>Purpose</b><br>
 * Wraps checked SQLException and JDBC errors as unchecked exceptions, simplifying
 * error handling in repository/service layers. Preserves full cause chain for
 * debugging while allowing clean exception propagation.
 *
 * <p><b>Architecture Role</b><br>
 * Exception class in core/database/jdbc for JDBC error translation.
 * Used by:
 * - JdbcTemplate - Wrap SQLException from JDBC operations
 * - Repositories - Propagate database errors without checked exception handling
 * - Service Layer - Handle database errors with uniform exception type
 * - Error Handlers - Translate to HTTP error codes (500, 503)
 *
 * <p><b>Exception Translation Strategy</b><br>
 * - <b>Connection Errors</b>: Network issues, authentication failures
 * - <b>Query Errors</b>: Syntax errors, constraint violations
 * - <b>Transaction Errors</b>: Deadlocks, timeout, rollback failures
 * - <b>Resource Errors</b>: Connection pool exhaustion, statement limits
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // JdbcTemplate throws JdbcException instead of SQLException
 * try {
 *     List<User> users = jdbcTemplate.queryForList(
 *         "SELECT * FROM users WHERE active = ?",
 *         rs -> new User(rs.getLong("id"), rs.getString("email")),
 *         true
 *     );
 * } catch (JdbcException e) {
 *     // Handle database error
 *     if (e.getCause() instanceof SQLException) {
 *         SQLException sqlEx = (SQLException) e.getCause();
 *         logger.error("SQL error code: {}, state: {}", 
 *             sqlEx.getErrorCode(), sqlEx.getSQLState(), e);
 *     }
 *     throw new ServiceException("Failed to retrieve users", e);
 * }
 * 
 * // Throwing from custom JDBC code
 * try {
 *     connection.prepareStatement(sql).executeQuery();
 * } catch (SQLException e) {
 *     throw new JdbcException("Failed to execute query: " + sql, e);
 * }
 * 
 * // Error code checking
 * catch (JdbcException e) {
 *     if (e.getCause() instanceof SQLException) {
 *         SQLException sqlEx = (SQLException) e.getCause();
 *         if ("23505".equals(sqlEx.getSQLState())) {
 *             // Unique constraint violation
 *             throw new DuplicateKeyException("Duplicate entry", e);
 *         }
 *     }
 *     throw e;
 * }
 * }</pre>
 *
 * <p><b>SQL State Codes (PostgreSQL Examples)</b><br>
 * - <b>23505</b>: Unique constraint violation
 * - <b>23503</b>: Foreign key constraint violation
 * - <b>23502</b>: NOT NULL constraint violation
 * - <b>40001</b>: Serialization failure (deadlock)
 * - <b>57P03</b>: Connection failure
 * - <b>42P01</b>: Undefined table
 *
 * <p><b>Error Handling Best Practices</b><br>
 * - Catch specific JDBC exceptions (unique constraint, timeout)
 * - Log full cause chain for debugging
 * - Translate to domain exceptions where appropriate
 * - Include contextual information (query, parameters)
 * - Retry transient errors (connection timeout, deadlock)
 *
 * <p><b>Thread Safety</b><br>
 * Immutable exception - safe to throw across threads.
 *
 * @see JdbcTemplate
 * @see java.sql.SQLException
 * @since 1.0.0
 * @doc.type exception
 * @doc.purpose Unchecked JDBC exception with cause chain preservation
 * @doc.layer core
 * @doc.pattern Exception
 */
public class JdbcException extends RuntimeException {
    
    /**
     * Creates a new JdbcException with the specified message.
     * 
     * @param message The exception message
     */
    public JdbcException(String message) {
        super(message);
    }
    
    /**
     * Creates a new JdbcException with the specified message and cause.
     * 
     * @param message The exception message
     * @param cause The underlying cause
     */
    public JdbcException(String message, Throwable cause) {
        super(message, cause);
    }
    
    /**
     * Creates a new JdbcException with the specified cause.
     * 
     * @param cause The underlying cause
     */
    public JdbcException(Throwable cause) {
        super(cause);
    }
}
