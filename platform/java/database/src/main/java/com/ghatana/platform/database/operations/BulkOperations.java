package com.ghatana.platform.database.operations;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Utility class for executing bulk database operations.
 *
 * @doc.type class
 * @doc.purpose Provides utility methods for bulk insert, update, and delete operations
 * @doc.layer platform
 * @doc.pattern Utility
 */
public final class BulkOperations {

    private BulkOperations() {
        // Utility class
    }

    /**
     * Execute a batch insert operation.
     *
     * @param conn database connection
     * @param sql insert SQL statement with placeholders
     * @param entities list of entities to insert
     * @param binder function to bind entity to prepared statement
     * @param <T> entity type
     * @return array of update counts
     * @throws SQLException if an error occurs
     */
    public static <T> int[] batchInsert(
            Connection conn,
            String sql,
            List<T> entities,
            BiConsumer<PreparedStatement, T> binder) throws SQLException {
        return executeBatch(conn, sql, entities, binder);
    }

    /**
     * Execute a batch update operation.
     *
     * @param conn database connection
     * @param sql update SQL statement with placeholders
     * @param entities list of entities to update
     * @param binder function to bind entity to prepared statement
     * @param <T> entity type
     * @return array of update counts
     * @throws SQLException if an error occurs
     */
    public static <T> int[] batchUpdate(
            Connection conn,
            String sql,
            List<T> entities,
            BiConsumer<PreparedStatement, T> binder) throws SQLException {
        return executeBatch(conn, sql, entities, binder);
    }

    /**
     * Execute a batch delete operation by IDs.
     *
     * @param conn database connection
     * @param sql delete SQL statement with ID placeholder
     * @param ids list of IDs to delete
     * @param <ID> ID type
     * @return array of update counts
     * @throws SQLException if an error occurs
     */
    public static <ID> int[] batchDeleteById(
            Connection conn,
            String sql,
            List<ID> ids) throws SQLException {
        Objects.requireNonNull(ids, "ids must not be null");
        if (ids.isEmpty()) {
            return new int[0];
        }

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (ID id : ids) {
                stmt.setObject(1, id);
                stmt.addBatch();
            }
            return stmt.executeBatch();
        }
    }

    /**
     * Execute a batch operation with chunking for large datasets.
     *
     * @param conn database connection
     * @param sql SQL statement
     * @param entities list of entities
     * @param binder function to bind entity to prepared statement
     * @param chunkSize size of each batch chunk
     * @param <T> entity type
     * @return total number of affected rows
     * @throws SQLException if an error occurs
     */
    public static <T> int chunkedBatchInsert(
            Connection conn,
            String sql,
            List<T> entities,
            BiConsumer<PreparedStatement, T> binder,
            int chunkSize) throws SQLException {
        Objects.requireNonNull(entities, "entities must not be null");
        if (entities.isEmpty()) {
            return 0;
        }

        int totalAffected = 0;
        int size = entities.size();

        for (int i = 0; i < size; i += chunkSize) {
            List<T> chunk = entities.subList(i, Math.min(i + chunkSize, size));
            int[] results = batchInsert(conn, sql, chunk, binder);
            for (int result : results) {
                if (result >= 0) {
                    totalAffected += result;
                }
            }
        }

        return totalAffected;
    }

    /**
     * Execute an upsert (insert or update) operation.
     *
     * @param conn database connection
     * @param upsertSql upsert SQL statement
     * @param entity entity to upsert
     * @param binder function to bind entity to prepared statement
     * @param <T> entity type
     * @return number of affected rows
     * @throws SQLException if an error occurs
     */
    public static <T> int upsert(
            Connection conn,
            String upsertSql,
            T entity,
            BiConsumer<PreparedStatement, T> binder) throws SQLException {
        Objects.requireNonNull(entity, "entity must not be null");

        try (PreparedStatement stmt = conn.prepareStatement(upsertSql)) {
            binder.accept(stmt, entity);
            return stmt.executeUpdate();
        }
    }

    private static <T> int[] executeBatch(
            Connection conn,
            String sql,
            List<T> entities,
            BiConsumer<PreparedStatement, T> binder) throws SQLException {
        Objects.requireNonNull(entities, "entities must not be null");
        if (entities.isEmpty()) {
            return new int[0];
        }

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (T entity : entities) {
                binder.accept(stmt, entity);
                stmt.addBatch();
            }
            return stmt.executeBatch();
        }
    }
}
