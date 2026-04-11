package com.ghatana.platform.database.repository;

import com.ghatana.core.database.repository.Repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.Serializable;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Base class for JDBC-based repository implementations providing common
 * database operations and utilities.
 *
 * @doc.type class
 * @doc.purpose Provides common JDBC operations for repository implementations
 * @doc.layer platform
 * @doc.pattern Base Class
 * @param <T> Entity type
 * @param <ID> Entity identifier type
 */
public abstract class JdbcRepositoryBase<T, ID extends Serializable> implements Repository<T, ID> {

    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected final DataSource dataSource;

    protected JdbcRepositoryBase(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
    }

    /**
     * Execute a query that returns a single optional result.
     */
    protected Optional<T> querySingle(String sql, ID id, Function<ResultSet, T> mapper) {
        Objects.requireNonNull(id, "id must not be null");

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            setIdParameter(stmt, 1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapper.apply(rs));
                }
            }
            return Optional.empty();

        } catch (SQLException e) {
            logger.error("Failed to find entity by id: {}", id, e);
            throw new RuntimeException("Failed to find entity", e);
        }
    }

    /**
     * Execute a query that returns a list of results.
     */
    protected List<T> queryList(String sql, Function<ResultSet, T> mapper) {
        return queryList(sql, null, mapper);
    }

    /**
     * Execute a query with a parameter that returns a list of results.
     */
    protected List<T> queryList(String sql, Object param, Function<ResultSet, T> mapper) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            if (param != null) {
                stmt.setObject(1, param);
            }

            return executeQueryAndMap(conn, stmt, mapper);

        } catch (SQLException e) {
            logger.error("Failed to execute query", e);
            throw new RuntimeException("Failed to execute query", e);
        }
    }

    /**
     * Execute an update statement within a transaction.
     */
    protected int executeUpdate(String sql, StatementPreparer preparer) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                preparer.prepare(stmt);
                int result = stmt.executeUpdate();
                conn.commit();
                return result;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            logger.error("Failed to execute update", e);
            throw new RuntimeException("Failed to execute update", e);
        }
    }

    /**
     * Execute a transaction with multiple operations.
     */
    protected <R> R executeInTransaction(TransactionFunction<R> function) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                R result = function.apply(conn);
                conn.commit();
                return result;
            } catch (Exception e) {
                conn.rollback();
                throw new RuntimeException("Transaction failed", e);
            }
        } catch (SQLException e) {
            logger.error("Transaction failed", e);
            throw new RuntimeException("Transaction failed", e);
        }
    }

    /**
     * Execute batch insert/update operation.
     */
    protected int[] executeBatch(String sql, Connection conn, BatchPreparer preparer) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            preparer.prepare(stmt);
            return stmt.executeBatch();
        }
    }

    @Override
    public long count() {
        String sql = "SELECT COUNT(*) FROM " + getTableName();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
            return 0L;
        } catch (SQLException e) {
            logger.error("Failed to count entities", e);
            throw new RuntimeException("Failed to count entities", e);
        }
    }

    @Override
    public void delete(T entity) {
        Objects.requireNonNull(entity, "entity must not be null");
        deleteById(getEntityId(entity));
    }

    public void deleteAll() {
        String sql = "DELETE FROM " + getTableName();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to delete all entities", e);
            throw new RuntimeException("Failed to delete all entities", e);
        }
    }

    @Override
    public List<T> findAll() {
        return queryList(getSelectAllSql(), getEntityMapper());
    }

    @Override
    public Optional<T> findById(ID id) {
        return querySingle(getSelectByIdSql(), id, getEntityMapper());
    }

    @Override
    public boolean existsById(ID id) {
        return findById(id).isPresent();
    }

    // Abstract methods to be implemented by subclasses

    protected abstract String getTableName();

    protected abstract String getSelectByIdSql();

    protected abstract String getSelectAllSql();

    protected abstract Function<ResultSet, T> getEntityMapper();

    protected abstract ID getEntityId(T entity);

    protected abstract void setIdParameter(PreparedStatement stmt, int index, ID id) throws SQLException;

    // Functional interfaces

    @FunctionalInterface
    protected interface StatementPreparer {
        void prepare(PreparedStatement stmt) throws SQLException;
    }

    @FunctionalInterface
    protected interface BatchPreparer {
        void prepare(PreparedStatement stmt) throws SQLException;
    }

    @FunctionalInterface
    protected interface TransactionFunction<R> {
        R apply(Connection conn) throws Exception;
    }

    // Helper methods

    protected List<T> executeQueryAndMap(Connection conn, PreparedStatement stmt, Function<ResultSet, T> mapper) throws SQLException {
        List<T> results = new ArrayList<>();
        try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                results.add(mapper.apply(rs));
            }
        }
        return results;
    }
}
