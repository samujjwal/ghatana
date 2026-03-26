package com.ghatana.datacloud.infrastructure.state.h2;

import com.ghatana.datacloud.entity.state.StateAdapter;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * H2 embedded database implementation of StateAdapter for local state storage.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides persistent, embedded SQL-based local state storage using H2
 * database. Offers a balance between the simplicity of InMemoryStateAdapter and
 * the performance of RocksDBStateAdapter, with full SQL query capabilities.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * Path dbPath = Path.of("/var/data/operator-state");
 * H2StateAdapter adapter = new H2StateAdapter(dbPath, "state_db");
 *
 * // Store state
 * adapter.put("tenant:op1:partition0:counter", "42", 60000).get();
 *
 * // Retrieve state
 * Optional<String> value = adapter.get("tenant:op1:partition0:counter").get();
 *
 * // Cleanup
 * adapter.close().get();
 * }</pre>
 *
 * <p>
 * <b>Performance</b><br>
 * - Put: ~5ms (SQL INSERT/UPDATE with index) - Get: ~2-5ms (SQL SELECT with
 * index) - Batch put: ~1ms per entry (batch INSERT) - Query: Full SQL support
 * for complex queries
 *
 * <p>
 * <b>Features</b><br>
 * - TTL support: Automatic expiration via SQL DELETE on access - Persistence:
 * MVCC file-based storage survives restarts - SQL queries: Full H2 SQL dialect
 * support - ACID: Transactional guarantees - Lightweight: Single JAR, no
 * external dependencies
 *
 * <p>
 * <b>Configuration</b><br>
 * Default configuration optimized for: - Embedded mode (no network overhead) -
 * MVCC for concurrent access - LOB compression for large values
 *
 * @see StateAdapter
 * @see <a href="https://h2database.com/">H2 Database</a>
 * @doc.type class
 * @doc.purpose Embedded SQL state adapter using H2
 * @doc.layer infrastructure
 * @doc.pattern Adapter
 */
public class H2StateAdapter implements StateAdapter<String, String> {

    private static final Logger logger = LoggerFactory.getLogger(H2StateAdapter.class);

    // Constants
    private static final String ADAPTER_TYPE = "H2";
    private static final String TABLE_NAME = "state_store";
    private static final String CREATE_TABLE_SQL = """
        CREATE TABLE IF NOT EXISTS %s (
            state_key VARCHAR(512) PRIMARY KEY,
            state_value CLOB NOT NULL,
            expires_at BIGINT,
            created_at BIGINT NOT NULL,
            updated_at BIGINT NOT NULL
        )
        """;
    private static final String CREATE_INDEX_SQL = """
        CREATE INDEX IF NOT EXISTS idx_%s_expires ON %s (expires_at)
        """;

    // H2 connection
    private final Connection connection;
    private final String tableName;
    private final Path dbPath;
    private final AtomicBoolean closed;

    // Prepared statements (cached for performance)
    private PreparedStatement putStmt;
    private PreparedStatement getStmt;
    private PreparedStatement deleteStmt;
    private PreparedStatement existsStmt;
    private PreparedStatement countStmt;

    /**
     * Construct H2 adapter with default table name.
     *
     * @param dbPath Path to database directory
     * @param dbName Database name (without extension)
     * @throws SQLException if database cannot be opened
     */
    public H2StateAdapter(Path dbPath, String dbName) throws SQLException {
        this(dbPath, dbName, TABLE_NAME);
    }

    /**
     * Construct H2 adapter with custom table name.
     *
     * @param dbPath Path to database directory
     * @param dbName Database name (without extension)
     * @param tableName Custom table name for state storage
     * @throws SQLException if database cannot be opened
     */
    public H2StateAdapter(Path dbPath, String dbName, String tableName) throws SQLException {
        Objects.requireNonNull(dbPath, "dbPath cannot be null");
        Objects.requireNonNull(dbName, "dbName cannot be null");
        Objects.requireNonNull(tableName, "tableName cannot be null");

        this.dbPath = dbPath;
        this.tableName = tableName;
        this.closed = new AtomicBoolean(false);

        // Build JDBC URL for embedded mode (MVCC is default in H2 2.x)
        String jdbcUrl = String.format(
                "jdbc:h2:%s/%s;MODE=PostgreSQL;AUTO_SERVER=FALSE;LOCK_TIMEOUT=10000",
                dbPath.toAbsolutePath(),
                dbName
        );

        // Open connection
        this.connection = DriverManager.getConnection(jdbcUrl, "sa", "");
        connection.setAutoCommit(true);

        // Initialize schema
        initializeSchema();

        // Prepare statements
        prepareStatements();

        logger.info("H2 adapter opened at {}/{}", dbPath, dbName);
    }

    /**
     * Initialize database schema.
     */
    private void initializeSchema() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // Create table
            stmt.execute(String.format(CREATE_TABLE_SQL, tableName));
            // Create expiration index
            stmt.execute(String.format(CREATE_INDEX_SQL, tableName, tableName));
        }
    }

    /**
     * Prepare cached statements for common operations.
     */
    private void prepareStatements() throws SQLException {
        // MERGE for upsert (H2 specific)
        putStmt = connection.prepareStatement(String.format(
                "MERGE INTO %s (state_key, state_value, expires_at, created_at, updated_at) "
                + "KEY (state_key) VALUES (?, ?, ?, ?, ?)",
                tableName
        ));

        getStmt = connection.prepareStatement(String.format(
                "SELECT state_value, expires_at FROM %s WHERE state_key = ?",
                tableName
        ));

        deleteStmt = connection.prepareStatement(String.format(
                "DELETE FROM %s WHERE state_key = ?",
                tableName
        ));

        existsStmt = connection.prepareStatement(String.format(
                "SELECT 1 FROM %s WHERE state_key = ? AND (expires_at IS NULL OR expires_at > ?)",
                tableName
        ));

        countStmt = connection.prepareStatement(String.format(
                "SELECT COUNT(*) FROM %s WHERE expires_at IS NULL OR expires_at > ?",
                tableName
        ));
    }

    /**
     * Put value in H2 with TTL.
     *
     * @param key State key
     * @param value State value
     * @param ttlMillis Time-to-live in milliseconds (0 = no expiry)
     * @return Promise completing when stored
     */
    @Override
    public Promise<Void> put(String key, String value, long ttlMillis) {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(value, "value cannot be null");

        if (closed.get()) {
            return Promise.ofException(new IllegalStateException("H2 adapter is closed"));
        }

        try {
            long now = System.currentTimeMillis();
            Long expiresAt = ttlMillis > 0 ? now + ttlMillis : null;

            synchronized (putStmt) {
                putStmt.setString(1, key);
                putStmt.setString(2, value);
                if (expiresAt != null) {
                    putStmt.setLong(3, expiresAt);
                } else {
                    putStmt.setNull(3, Types.BIGINT);
                }
                putStmt.setLong(4, now);
                putStmt.setLong(5, now);
                putStmt.executeUpdate();
            }

            logger.debug("Put key {} in H2 (ttl={}ms)", key, ttlMillis);
            return Promise.of(null);
        } catch (SQLException e) {
            logger.error("Failed to put key {} in H2", key, e);
            return Promise.ofException(e);
        }
    }

    /**
     * Batch put multiple key-value pairs.
     *
     * @param entries Key-value pairs to store
     * @param ttlMillis Time-to-live for all entries
     * @return Promise completing when batch stored
     */
    @Override
    public Promise<Void> putAll(Map<String, String> entries, long ttlMillis) {
        Objects.requireNonNull(entries, "entries cannot be null");

        if (closed.get()) {
            return Promise.ofException(new IllegalStateException("H2 adapter is closed"));
        }

        if (entries.isEmpty()) {
            return Promise.of(null);
        }

        try {
            long now = System.currentTimeMillis();
            Long expiresAt = ttlMillis > 0 ? now + ttlMillis : null;

            connection.setAutoCommit(false);
            try {
                synchronized (putStmt) {
                    for (Map.Entry<String, String> entry : entries.entrySet()) {
                        putStmt.setString(1, entry.getKey());
                        putStmt.setString(2, entry.getValue());
                        if (expiresAt != null) {
                            putStmt.setLong(3, expiresAt);
                        } else {
                            putStmt.setNull(3, Types.BIGINT);
                        }
                        putStmt.setLong(4, now);
                        putStmt.setLong(5, now);
                        putStmt.addBatch();
                    }
                    putStmt.executeBatch();
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }

            logger.debug("Batch put {} entries in H2", entries.size());
            return Promise.of(null);
        } catch (SQLException e) {
            logger.error("Failed to batch put entries in H2", e);
            return Promise.ofException(e);
        }
    }

    /**
     * Get value from H2.
     *
     * @param key State key
     * @return Promise<Optional<String>> with value or empty
     */
    @Override
    public Promise<Optional<String>> get(String key) {
        Objects.requireNonNull(key, "key cannot be null");

        if (closed.get()) {
            return Promise.ofException(new IllegalStateException("H2 adapter is closed"));
        }

        try {
            synchronized (getStmt) {
                getStmt.setString(1, key);
                try (ResultSet rs = getStmt.executeQuery()) {
                    if (rs.next()) {
                        String value = rs.getString(1);
                        long expiresAt = rs.getLong(2);
                        boolean hasExpiry = !rs.wasNull();

                        // Check expiration
                        if (hasExpiry && System.currentTimeMillis() > expiresAt) {
                            // Expired - delete asynchronously
                            deleteAsync(key);
                            logger.debug("Key {} expired, removed from H2", key);
                            return Promise.of(Optional.empty());
                        }

                        logger.debug("Got key {} from H2", key);
                        return Promise.of(Optional.of(value));
                    }
                }
            }
            return Promise.of(Optional.empty());
        } catch (SQLException e) {
            logger.error("Failed to get key {} from H2", key, e);
            return Promise.ofException(e);
        }
    }

    /**
     * Async delete helper for expired entries.
     */
    private void deleteAsync(String key) {
        try {
            synchronized (deleteStmt) {
                deleteStmt.setString(1, key);
                deleteStmt.executeUpdate();
            }
        } catch (SQLException e) {
            logger.warn("Failed to delete expired key {}", key, e);
        }
    }

    /**
     * Batch get multiple keys.
     *
     * @param keys List of state keys
     * @return Promise<Map<String, String>> with found values
     */
    @Override
    public Promise<Map<String, String>> getAll(Collection<String> keys) {
        Objects.requireNonNull(keys, "keys cannot be null");

        if (closed.get()) {
            return Promise.ofException(new IllegalStateException("H2 adapter is closed"));
        }

        if (keys.isEmpty()) {
            return Promise.of(Collections.emptyMap());
        }

        try {
            Map<String, String> result = new HashMap<>();
            long now = System.currentTimeMillis();

            // Build IN clause dynamically
            String placeholders = String.join(",", Collections.nCopies(keys.size(), "?"));
            String sql = String.format(
                    "SELECT state_key, state_value, expires_at FROM %s WHERE state_key IN (%s)",
                    tableName, placeholders
            );

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                int i = 1;
                for (String key : keys) {
                    stmt.setString(i++, key);
                }

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String key = rs.getString(1);
                        String value = rs.getString(2);
                        long expiresAt = rs.getLong(3);
                        boolean hasExpiry = !rs.wasNull();

                        // Skip expired entries
                        if (!hasExpiry || now <= expiresAt) {
                            result.put(key, value);
                        }
                    }
                }
            }

            logger.debug("Batch got {} of {} keys from H2", result.size(), keys.size());
            return Promise.of(result);
        } catch (SQLException e) {
            logger.error("Failed to batch get keys from H2", e);
            return Promise.ofException(e);
        }
    }

    /**
     * Delete value from H2.
     *
     * @param key State key
     * @return Promise completing when deleted
     */
    @Override
    public Promise<Void> delete(String key) {
        Objects.requireNonNull(key, "key cannot be null");

        if (closed.get()) {
            return Promise.ofException(new IllegalStateException("H2 adapter is closed"));
        }

        try {
            synchronized (deleteStmt) {
                deleteStmt.setString(1, key);
                deleteStmt.executeUpdate();
            }
            logger.debug("Deleted key {} from H2", key);
            return Promise.of(null);
        } catch (SQLException e) {
            logger.error("Failed to delete key {} from H2", key, e);
            return Promise.ofException(e);
        }
    }

    /**
     * Batch delete multiple keys.
     *
     * @param keys List of state keys
     * @return Promise completing when batch deleted
     */
    @Override
    public Promise<Void> deleteAll(Collection<String> keys) {
        Objects.requireNonNull(keys, "keys cannot be null");

        if (closed.get()) {
            return Promise.ofException(new IllegalStateException("H2 adapter is closed"));
        }

        if (keys.isEmpty()) {
            return Promise.of(null);
        }

        try {
            // Build IN clause dynamically
            String placeholders = String.join(",", Collections.nCopies(keys.size(), "?"));
            String sql = String.format("DELETE FROM %s WHERE state_key IN (%s)", tableName, placeholders);

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                int i = 1;
                for (String key : keys) {
                    stmt.setString(i++, key);
                }
                stmt.executeUpdate();
            }

            logger.debug("Batch deleted {} keys from H2", keys.size());
            return Promise.of(null);
        } catch (SQLException e) {
            logger.error("Failed to batch delete keys from H2", e);
            return Promise.ofException(e);
        }
    }

    /**
     * Clear all entries.
     *
     * @return Promise completing when cleared
     */
    @Override
    public Promise<Void> clear() {
        if (closed.get()) {
            return Promise.ofException(new IllegalStateException("H2 adapter is closed"));
        }

        logger.warn("DESTRUCTIVE OPERATION: clear() called on H2 state table '{}' — "
                + "deleting ALL entries across all tenants.", tableName);

        try {
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate(String.format("DELETE FROM %s", tableName));
            }
            logger.warn("DESTRUCTIVE OPERATION COMPLETED: Cleared H2 state table '{}'", tableName);
            return Promise.of(null);
        } catch (SQLException e) {
            logger.error("Failed to clear H2", e);
            return Promise.ofException(e);
        }
    }

    /**
     * Check if key exists and is not expired.
     *
     * @param key State key
     * @return Promise<Boolean> true if exists
     */
    @Override
    public Promise<Boolean> exists(String key) {
        Objects.requireNonNull(key, "key cannot be null");

        if (closed.get()) {
            return Promise.ofException(new IllegalStateException("H2 adapter is closed"));
        }

        try {
            synchronized (existsStmt) {
                existsStmt.setString(1, key);
                existsStmt.setLong(2, System.currentTimeMillis());
                try (ResultSet rs = existsStmt.executeQuery()) {
                    return Promise.of(rs.next());
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to check existence of key {} in H2", key, e);
            return Promise.ofException(e);
        }
    }

    /**
     * Get H2 statistics.
     *
     * @return Promise<Map<String, Object>> with statistics
     */
    @Override
    public Promise<Map<String, Object>> getStatistics() {
        if (closed.get()) {
            return Promise.ofException(new IllegalStateException("H2 adapter is closed"));
        }

        try {
            Map<String, Object> stats = new HashMap<>();
            stats.put("adapter_type", ADAPTER_TYPE);
            stats.put("db_path", dbPath.toString());
            stats.put("table_name", tableName);
            stats.put("timestamp", System.currentTimeMillis());

            // Get entry count (non-expired)
            synchronized (countStmt) {
                countStmt.setLong(1, System.currentTimeMillis());
                try (ResultSet rs = countStmt.executeQuery()) {
                    if (rs.next()) {
                        stats.put("entry_count", rs.getLong(1));
                    }
                }
            }

            // Get total count (including expired)
            try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(String.format("SELECT COUNT(*) FROM %s", tableName))) {
                if (rs.next()) {
                    stats.put("total_entries", rs.getLong(1));
                }
            }

            return Promise.of(stats);
        } catch (SQLException e) {
            logger.error("Failed to get H2 statistics", e);
            return Promise.ofException(e);
        }
    }

    /**
     * Get estimated database size.
     *
     * @return Promise<Long> approximate size in bytes
     */
    @Override
    public Promise<Long> getSize() {
        if (closed.get()) {
            return Promise.ofException(new IllegalStateException("H2 adapter is closed"));
        }

        try {
            // Estimate size from data length
            try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(String.format(
                    "SELECT COALESCE(SUM(LENGTH(state_key) + LENGTH(state_value)), 0) FROM %s",
                    tableName))) {
                if (rs.next()) {
                    return Promise.of(rs.getLong(1));
                }
            }
            return Promise.of(0L);
        } catch (SQLException e) {
            logger.error("Failed to get H2 size", e);
            return Promise.ofException(e);
        }
    }

    /**
     * Get entry count (non-expired).
     *
     * @return Promise<Long> number of entries
     */
    @Override
    public Promise<Long> getCount() {
        if (closed.get()) {
            return Promise.ofException(new IllegalStateException("H2 adapter is closed"));
        }

        try {
            synchronized (countStmt) {
                countStmt.setLong(1, System.currentTimeMillis());
                try (ResultSet rs = countStmt.executeQuery()) {
                    if (rs.next()) {
                        return Promise.of(rs.getLong(1));
                    }
                }
            }
            return Promise.of(0L);
        } catch (SQLException e) {
            logger.error("Failed to get H2 entry count", e);
            return Promise.ofException(e);
        }
    }

    /**
     * Close H2 and release resources.
     *
     * @return Promise completing when closed
     */
    @Override
    public Promise<Void> close() {
        if (closed.compareAndSet(false, true)) {
            try {
                // Close prepared statements
                closeQuietly(putStmt);
                closeQuietly(getStmt);
                closeQuietly(deleteStmt);
                closeQuietly(existsStmt);
                closeQuietly(countStmt);

                // Close connection
                if (connection != null && !connection.isClosed()) {
                    connection.close();
                }

                logger.info("H2 adapter closed at {}", dbPath);
                return Promise.of(null);
            } catch (SQLException e) {
                logger.error("Failed to close H2 adapter", e);
                return Promise.ofException(e);
            }
        }
        return Promise.of(null);
    }

    /**
     * Close statement quietly.
     */
    private void closeQuietly(PreparedStatement stmt) {
        if (stmt != null) {
            try {
                stmt.close();
            } catch (SQLException ignored) {
            }
        }
    }

    /**
     * Get adapter type identifier.
     *
     * @return "H2"
     */
    @Override
    public String getAdapterType() {
        return ADAPTER_TYPE;
    }

    /**
     * Check if adapter is healthy.
     *
     * @return Promise<Boolean> true if connection valid
     */
    @Override
    public Promise<Boolean> isHealthy() {
        if (closed.get()) {
            return Promise.of(false);
        }

        try {
            return Promise.of(connection.isValid(5));
        } catch (SQLException e) {
            logger.warn("H2 health check failed", e);
            return Promise.of(false);
        }
    }

    /**
     * Get database path.
     *
     * @return Path to database directory
     */
    public Path getDbPath() {
        return dbPath;
    }

    /**
     * Clean up expired entries.
     *
     * <p>
     * Call this periodically to reclaim space from expired entries.
     *
     * @return Promise<Integer> number of entries deleted
     */
    public Promise<Integer> cleanupExpired() {
        if (closed.get()) {
            return Promise.ofException(new IllegalStateException("H2 adapter is closed"));
        }

        try {
            try (Statement stmt = connection.createStatement()) {
                int deleted = stmt.executeUpdate(String.format(
                        "DELETE FROM %s WHERE expires_at IS NOT NULL AND expires_at < %d",
                        tableName, System.currentTimeMillis()
                ));
                if (deleted > 0) {
                    logger.info("Cleaned up {} expired entries from H2", deleted);
                }
                return Promise.of(deleted);
            }
        } catch (SQLException e) {
            logger.error("Failed to cleanup expired entries", e);
            return Promise.ofException(e);
        }
    }

    /**
     * Compact database to reclaim space.
     *
     * @return Promise completing when compaction finished
     */
    public Promise<Void> compact() {
        if (closed.get()) {
            return Promise.ofException(new IllegalStateException("H2 adapter is closed"));
        }

        try {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("SHUTDOWN COMPACT");
            }
            logger.info("H2 compaction completed");
            return Promise.of(null);
        } catch (SQLException e) {
            logger.error("Failed to compact H2", e);
            return Promise.ofException(e);
        }
    }
}
