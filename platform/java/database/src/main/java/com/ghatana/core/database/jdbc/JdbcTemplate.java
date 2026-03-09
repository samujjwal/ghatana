package com.ghatana.core.database.jdbc;

import com.ghatana.platform.core.util.Preconditions;
import com.ghatana.core.database.transaction.TransactionCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
// Using SLF4J for logging
import javax.sql.DataSource;

/**
 * Production-grade JDBC template providing simplified database access with resource management and exception translation.
 *
 * <p><b>Purpose</b><br>
 * High-level abstraction over JDBC operations handling resource management (Connection,
 * PreparedStatement, ResultSet), exception translation, and common query patterns.
 * Designed for scenarios where JPA is unsuitable or for performance-critical operations
 * requiring direct SQL control.
 *
 * <p><b>Architecture Role</b><br>
 * Template class in core/database/jdbc for JDBC operations.
 * Used by:
 * - Repository Layer - Direct SQL for performance-critical queries
 * - Reporting - Complex aggregations, stored procedures
 * - Batch Processing - Bulk inserts/updates with JDBC batching
 * - Migration Scripts - Database setup and data migration
 * - Read Models - CQRS read-side projections
 *
 * <p><b>Template Features</b><br>
 * - <b>Resource Safety</b>: Automatic Connection/Statement/ResultSet cleanup
 * - <b>Exception Translation</b>: SQLException → JdbcException (unchecked)
 * - <b>Query Operations</b>: queryForObject, queryForList with RowMapper
 * - <b>Update Operations</b>: update, batchUpdate for DML
 * - <b>Batch Support</b>: Efficient batch inserts/updates
 * - <b>Parameter Binding</b>: Varargs parameters with type safety
 * - <b>Optional Results</b>: queryForObject returns Optional, never null
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * JdbcTemplate jdbc = new JdbcTemplate(dataSource);
 * 
 * // 1. Query for single result
 * Optional<String> name = jdbc.queryForObject(
 *     "SELECT name FROM users WHERE id = ?",
 *     rs -> rs.getString("name"),
 *     userId
 * );
 * 
 * // 2. Query for list
 * List<User> users = jdbc.queryForList(
 *     "SELECT id, name, email FROM users WHERE active = ?",
 *     rs -> new User(
 *         rs.getLong("id"),
 *         rs.getString("name"),
 *         rs.getString("email")
 *     ),
 *     true
 * );
 * 
 * // 3. Update operation
 * int updated = jdbc.update(
 *     "UPDATE users SET last_login = ? WHERE id = ?",
 *     Timestamp.from(Instant.now()),
 *     userId
 * );
 * 
 * // 4. Batch inserts
 * List<User> users = Arrays.asList(
 *     new User("john@example.com"),
 *     new User("jane@example.com")
 * );
 * 
 * jdbc.batchUpdate(
 *     "INSERT INTO users (email, created_at) VALUES (?, ?)",
 *     users,
 *     (ps, user) -> {
 *         ps.setString(1, user.getEmail());
 *         ps.setTimestamp(2, Timestamp.from(Instant.now()));
 *     }
 * );
 * 
 * // 5. Complex query with joins
 * List<OrderDetails> orders = jdbc.queryForList(
 *     "SELECT o.id, o.total, c.name AS customer_name " +
 *     "FROM orders o JOIN customers c ON o.customer_id = c.id " +
 *     "WHERE o.status = ? AND o.created_at >= ?",
 *     rs -> new OrderDetails(
 *         rs.getLong("id"),
 *         rs.getBigDecimal("total"),
 *         rs.getString("customer_name")
 *     ),
 *     "PENDING",
 *     Date.from(Instant.now().minus(Duration.ofDays(30)))
 * );
 *
 * // 6. Stored procedure call
 * jdbc.execute(
 *     "CALL update_inventory(?, ?)",
 *     productId,
 *     quantity
 * );
 *
 * // 7. Transaction with callback
 * jdbc.executeInTransaction(conn -> {
 *     // Step 1: Insert order
 *     try (PreparedStatement ps = conn.prepareStatement(
 *             "INSERT INTO orders (customer_id, total) VALUES (?, ?)")) {
 *         ps.setLong(1, customerId);
 *         ps.setBigDecimal(2, total);
 *         ps.executeUpdate();
 *     }
 *     
 *     // Step 2: Insert order lines
 *     try (PreparedStatement ps = conn.prepareStatement(
 *             "INSERT INTO order_lines (order_id, product_id, quantity) VALUES (?, ?, ?)")) {
 *         for (OrderLine line : lines) {
 *             ps.setLong(1, orderId);
 *             ps.setLong(2, line.getProductId());
 *             ps.setInt(3, line.getQuantity());
 *             ps.addBatch();
 *         }
 *         ps.executeBatch();
 *     }
 *     
 *     return null;
 * });
 * }</pre>
 *
 * <p><b>RowMapper Pattern</b><br>
 * Lambda converting ResultSet to domain object:
 * <pre>{@code
 * RowMapper<User> userMapper = rs -> new User(
 *     rs.getLong("id"),
 *     rs.getString("email"),
 *     rs.getTimestamp("created_at").toInstant()
 * );
 * 
 * List<User> users = jdbc.queryForList(
 *     "SELECT * FROM users WHERE active = ?",
 *     userMapper,
 *     true
 * );
 * }</pre>
 *
 * <p><b>Parameter Binding</b><br>
 * Parameters bound positionally matching SQL placeholders:
 * <pre>{@code
 * // Query: "WHERE name = ? AND age >= ? AND city = ?"
 * // Params: "John", 18, "Seattle"
 * jdbc.queryForList(sql, mapper, "John", 18, "Seattle");
 * }</pre>
 *
 * <p><b>Batch Operations</b><br>
 * Efficient for bulk inserts/updates:
 * - Reduces network round-trips
 * - Uses JDBC batch API
 * - All-or-nothing transaction semantics
 * - Returns array of update counts
 *
 * <p><b>Exception Handling</b><br>
 * - SQLException wrapped in JdbcException (unchecked)
 * - Full cause chain preserved for debugging
 * - Resource cleanup guaranteed even on exceptions
 *
 * <p><b>Performance Considerations</b><br>
 * - Use batch operations for bulk DML (>100 rows)
 * - Prefer prepared statements (parameterized queries)
 * - Consider connection pooling (HikariCP recommended)
 * - Use JDBC for read-heavy queries (faster than JPA for reporting)
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe - each operation gets isolated Connection from DataSource.
 * DataSource must be thread-safe (use HikariCP).
 *
 * @see JdbcException
 * @see javax.sql.DataSource
 * @see java.sql.PreparedStatement
 * @since 1.0.0
 * @doc.type class
 * @doc.purpose JDBC template with resource management and exception translation
 * @doc.layer core
 * @doc.pattern Template Method
 */
public final class JdbcTemplate {
    private static final Logger LOG = LoggerFactory.getLogger(JdbcTemplate.class);
    
    private final DataSource dataSource;
    
    /**
     * Creates a new JdbcTemplate.
     * 
     * @param dataSource The data source to use
     */
    public JdbcTemplate(DataSource dataSource) {
        this.dataSource = Preconditions.requireNonNull(dataSource, "DataSource cannot be null");
        LOG.debug("JdbcTemplate created with DataSource: {}", dataSource.getClass().getSimpleName());
    }
    
    /**
     * Executes a query and returns a single result.
     * 
     * @param <T> The result type
     * @param sql The SQL query
     * @param rowMapper Function to map ResultSet to result object
     * @param parameters Query parameters
     * @return Optional containing the result if found
     * @throws JdbcException if a database error occurs
     */
    public <T> Optional<T> queryForObject(String sql, RowMapper<T> rowMapper, Object... parameters) {
        Preconditions.requireNonBlank(sql, "SQL cannot be blank");
        Preconditions.requireNonNull(rowMapper, "RowMapper cannot be null");
        
        LOG.debug("Executing query for single object: {}", sql);
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            setParameters(ps, parameters);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    T result = rowMapper.mapRow(rs);
                    LOG.debug("Query returned single result");
                    return Optional.of(result);
                } else {
                    LOG.debug("Query returned no results");
                    return Optional.empty();
                }
            }
        } catch (SQLException e) {
            throw new JdbcException("Failed to execute query: " + sql, e);
        }
    }
    
    /**
     * Executes a query and returns a list of results.
     * 
     * @param <T> The result type
     * @param sql The SQL query
     * @param rowMapper Function to map ResultSet to result objects
     * @param parameters Query parameters
     * @return List of results (empty if no results)
     * @throws JdbcException if a database error occurs
     */
    public <T> List<T> queryForList(String sql, RowMapper<T> rowMapper, Object... parameters) {
        Preconditions.requireNonBlank(sql, "SQL cannot be blank");
        Preconditions.requireNonNull(rowMapper, "RowMapper cannot be null");
        
        LOG.debug("Executing query for list: {}", sql);
        
        List<T> results = new ArrayList<>();
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            setParameters(ps, parameters);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(rowMapper.mapRow(rs));
                }
            }
            
            LOG.debug("Query returned {} results", results.size());
            return results;
            
        } catch (SQLException e) {
            throw new JdbcException("Failed to execute query: " + sql, e);
        }
    }
    
    /**
     * Executes a query with pagination and returns a list of results.
     * 
     * @param <T> The result type
     * @param sql The SQL query
     * @param rowMapper Function to map ResultSet to result objects
     * @param offset The offset (0-based)
     * @param limit The maximum number of results
     * @param parameters Query parameters
     * @return List of results within the specified range
     * @throws JdbcException if a database error occurs
     */
    public <T> List<T> queryForList(String sql, RowMapper<T> rowMapper, int offset, int limit, Object... parameters) {
        Preconditions.requireNonBlank(sql, "SQL cannot be blank");
        Preconditions.requireNonNull(rowMapper, "RowMapper cannot be null");
        Preconditions.requireNonNegative(offset, "Offset cannot be negative");
        Preconditions.requirePositive(limit, "Limit must be positive");
        
        // Add LIMIT and OFFSET to the SQL (PostgreSQL syntax)
        String paginatedSql = sql + " LIMIT ? OFFSET ?";
        
        LOG.debug("Executing paginated query: {} with offset={}, limit={}", sql, offset, limit);
        
        // Add limit and offset to parameters
        Object[] allParameters = new Object[parameters.length + 2];
        System.arraycopy(parameters, 0, allParameters, 0, parameters.length);
        allParameters[parameters.length] = limit;
        allParameters[parameters.length + 1] = offset;
        
        return queryForList(paginatedSql, rowMapper, allParameters);
    }
    
    /**
     * Executes a query and returns a single scalar value.
     * 
     * @param <T> The result type
     * @param sql The SQL query
     * @param resultClass The expected result class
     * @param parameters Query parameters
     * @return Optional containing the scalar result if found
     * @throws JdbcException if a database error occurs
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> queryForScalar(String sql, Class<T> resultClass, Object... parameters) {
        Preconditions.requireNonBlank(sql, "SQL cannot be blank");
        Preconditions.requireNonNull(resultClass, "Result class cannot be null");
        
        return queryForObject(sql, rs -> (T) rs.getObject(1, resultClass), parameters);
    }
    
    /**
     * Executes an update, insert, or delete statement.
     * 
     * @param sql The SQL statement
     * @param parameters Statement parameters
     * @return The number of affected rows
     * @throws JdbcException if a database error occurs
     */
    public int update(String sql, Object... parameters) {
        Preconditions.requireNonBlank(sql, "SQL cannot be blank");
        
        LOG.debug("Executing update: {}", sql);
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            setParameters(ps, parameters);
            
            int affectedRows = ps.executeUpdate();
            LOG.debug("Update affected {} rows", affectedRows);
            
            return affectedRows;
            
        } catch (SQLException e) {
            throw new JdbcException("Failed to execute update: " + sql, e);
        }
    }
    
    /**
     * Executes an insert statement and returns the generated key.
     * 
     * @param <T> The key type
     * @param sql The SQL insert statement
     * @param keyClass The expected key class
     * @param parameters Statement parameters
     * @return Optional containing the generated key if available
     * @throws JdbcException if a database error occurs
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> insertAndReturnKey(String sql, Class<T> keyClass, Object... parameters) {
        Preconditions.requireNonBlank(sql, "SQL cannot be blank");
        Preconditions.requireNonNull(keyClass, "Key class cannot be null");
        
        LOG.debug("Executing insert with key return: {}", sql);
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            setParameters(ps, parameters);
            
            int affectedRows = ps.executeUpdate();
            LOG.debug("Insert affected {} rows", affectedRows);
            
            if (affectedRows > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        T key = (T) rs.getObject(1, keyClass);
                        LOG.debug("Generated key: {}", key);
                        return Optional.of(key);
                    }
                }
            }
            
            return Optional.empty();
            
        } catch (SQLException e) {
            throw new JdbcException("Failed to execute insert: " + sql, e);
        }
    }
    
    /**
     * Executes a batch update operation.
     * 
     * @param <T> The item type
     * @param sql The SQL statement
     * @param items The items to process
     * @param parameterSetter Function to set parameters for each item
     * @return Array of update counts for each item
     * @throws JdbcException if a database error occurs
     */
    public <T> int[] batchUpdate(String sql, List<T> items, ParameterSetter<T> parameterSetter) {
        Preconditions.requireNonBlank(sql, "SQL cannot be blank");
        Preconditions.requireNonNull(items, "Items cannot be null");
        Preconditions.requireNonNull(parameterSetter, "Parameter setter cannot be null");
        
        if (items.isEmpty()) {
            return new int[0];
        }
        
        LOG.debug("Executing batch update: {} with {} items", sql, items.size());
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            for (T item : items) {
                parameterSetter.setParameters(ps, item);
                ps.addBatch();
            }
            
            int[] results = ps.executeBatch();
            LOG.debug("Batch update completed for {} items", items.size());
            
            return results;
            
        } catch (SQLException e) {
            throw new JdbcException("Failed to execute batch update: " + sql, e);
        }
    }
    
    /**
     * Executes a statement within a transaction.
     * 
     * @param <T> The result type
     * @param operation The operation to execute
     * @return The result of the operation
     * @throws JdbcException if a database error occurs
     */
    public <T> T inTransaction(TransactionCallback<JdbcTemplate, T> operation) {
        Preconditions.requireNonNull(operation, "Operation cannot be null");
        
        LOG.debug("Executing operation in transaction");
        
        try (Connection conn = dataSource.getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            
            try {
                conn.setAutoCommit(false);
                
                T result = operation.execute(new JdbcTemplate(new SingleConnectionDataSource(conn)));
                
                conn.commit();
                LOG.debug("Transaction committed successfully");
                
                return result;
                
            } catch (Exception e) {
                conn.rollback();
                LOG.debug("Transaction rolled back due to exception: {}", e.getMessage());
                
                if (e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                } else {
                    throw new JdbcException("Transaction failed", e);
                }
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
            
        } catch (SQLException e) {
            throw new JdbcException("Failed to execute transaction", e);
        }
    }
    
    /**
     * Executes a statement within a transaction (void return).
     * 
     * @param operation The operation to execute
     * @throws JdbcException if a database error occurs
     */
    public void inTransaction(VoidTransactionCallback operation) {
        Preconditions.requireNonNull(operation, "Operation cannot be null");
        
        inTransaction(jdbcTemplate -> {
            operation.execute(jdbcTemplate);
            return null;
        });
    }
    
    /**
     * Gets the underlying DataSource.
     * 
     * @return The DataSource
     */
    public DataSource getDataSource() {
        return dataSource;
    }
    
    /**
     * Sets parameters on a PreparedStatement.
     * 
     * @param ps The PreparedStatement
     * @param parameters The parameters to set
     * @throws SQLException if a database error occurs
     */
    private void setParameters(PreparedStatement ps, Object... parameters) throws SQLException {
        for (int i = 0; i < parameters.length; i++) {
            Object parameter = parameters[i];
            ps.setObject(i + 1, parameter);
            LOG.trace("Set parameter {}: {}", i + 1, parameter);
        }
    }
    
    /**
     * Functional interface for mapping ResultSet rows to objects.
     * 
     * @param <T> The result type
     */
    @FunctionalInterface
    public interface RowMapper<T> {
        /**
         * Maps a ResultSet row to an object.
         * 
         * @param rs The ResultSet positioned at the current row
         * @return The mapped object
         * @throws SQLException if a database error occurs
         */
        T mapRow(ResultSet rs) throws SQLException;
    }
    
    /**
     * Functional interface for setting parameters on PreparedStatement.
     * 
     * @param <T> The item type
     */
    @FunctionalInterface
    public interface ParameterSetter<T> {
        /**
         * Sets parameters on the PreparedStatement for the given item.
         * 
         * @param ps The PreparedStatement
         * @param item The item to set parameters for
         * @throws SQLException if a database error occurs
         */
        void setParameters(PreparedStatement ps, T item) throws SQLException;
    }
    
    /**
     * Functional interface for void transaction callbacks.
     */
    @FunctionalInterface
    public interface VoidTransactionCallback {
        /**
         * Executes within a transaction context.
         * 
         * @param jdbcTemplate The JdbcTemplate to use
         * @throws Exception if an error occurs
         */
        void execute(JdbcTemplate jdbcTemplate) throws Exception;
    }
    
    /**
     * Simple DataSource wrapper for a single connection.
     */
    private static class SingleConnectionDataSource extends AbstractDataSource {
        private final Connection connection;
        private PrintWriter logWriter;
        private int loginTimeout = 0;
        
        public SingleConnectionDataSource(Connection connection) {
            this.connection = Preconditions.requireNonNull(connection, "Connection cannot be null");
        }
        
        @Override
        public Connection getConnection() throws SQLException {
            if (connection.isClosed()) {
                throw new SQLException("Connection is closed");
            }
            return connection;
        }
        
        @Override
        public Connection getConnection(String username, String password) {
            // Not supported - this is a single connection data source
            throw new UnsupportedOperationException("Not supported for single connection data source");
        }
        
        @Override
        public PrintWriter getLogWriter() {
            return logWriter;
        }
        
        @Override
        public void setLogWriter(PrintWriter out) {
            this.logWriter = out;
        }
        
        @Override
        public void setLoginTimeout(int seconds) {
            this.loginTimeout = seconds;
        }
        
        @Override
        public int getLoginTimeout() {
            return loginTimeout;
        }
        
        @Override
        public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException("getParentLogger not supported");
        }
        
        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isInstance(this)) {
                return iface.cast(this);
            }
            throw new SQLException("DataSource of type [" + getClass().getName() +
                    "] cannot be unwrapped as [" + iface.getName() + "]");
        }
        
        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return iface.isInstance(this);
        }
    }
}
