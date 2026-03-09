/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 *
 * Base repository class to reduce CRUD duplication across repositories.
 * Provides common CRUD operations with tenant isolation.
 */
package com.ghatana.yappc.api.repository;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * Abstract base repository providing common CRUD operations.
 * 
 * <p><b>Purpose</b><br>
 * Reduces duplication of CRUD patterns across repository implementations.
 * Subclasses provide entity-specific mapping via abstract methods.
 *
 * <p><b>Type Parameters</b><br>
 * - T: The entity type<br>
 * - ID: The identifier type (typically UUID)<br>
 *
 * @doc.type class
 * @doc.purpose Base repository for CRUD operations
 * @doc.layer domain
 * @doc.pattern Repository, Template Method
 */
public abstract class BaseRepository<T, ID> {

    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected final DataSource dataSource;
    protected final String tableName;
    protected final String idColumn;
    protected final Executor executor;

    protected BaseRepository(DataSource dataSource, String tableName, String idColumn) {
        this.dataSource = dataSource;
        this.tableName = tableName;
        this.idColumn = idColumn;
        this.executor = Executors.newCachedThreadPool();
    }

    // ========== Abstract Methods (to be implemented by subclasses) ==========

    /**
     * Maps a ResultSet row to an entity.
     */
    protected abstract T mapRow(ResultSet rs) throws SQLException;

    /**
     * Extracts the ID from an entity.
     */
    protected abstract ID getId(T entity);

    /**
     * Sets parameters for INSERT statement.
     */
    protected abstract void setInsertParameters(PreparedStatement ps, T entity) throws SQLException;

    /**
     * Sets parameters for UPDATE statement.
     */
    protected abstract void setUpdateParameters(PreparedStatement ps, T entity) throws SQLException;

    /**
     * Gets the INSERT SQL statement.
     */
    protected abstract String getInsertSql();

    /**
     * Gets the UPDATE SQL statement.
     */
    protected abstract String getUpdateSql();

    // ========== Common CRUD Operations ==========

    /**
     * Saves an entity (INSERT if new, UPDATE if exists).
     */
    public Promise<T> save(T entity) {
        ID id = getId(entity);
        if (id == null) {
            return insert(entity);
        }
        return existsById(id)
            .then(exists -> exists ? update(entity) : insert(entity));
    }

    /**
     * Inserts a new entity.
     */
    protected Promise<T> insert(T entity) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(getInsertSql(), new String[]{idColumn})) {
                
                setInsertParameters(ps, entity);
                ps.executeUpdate();
                
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        return mapRow(rs);
                    }
                }
                return entity;
            } catch (SQLException e) {
                logger.error("Failed to insert entity", e);
                throw new RuntimeException("Failed to insert entity", e);
            }
        });
    }

    /**
     * Updates an existing entity.
     */
    protected Promise<T> update(T entity) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(getUpdateSql())) {
                
                setUpdateParameters(ps, entity);
                ps.executeUpdate();
                return entity;
            } catch (SQLException e) {
                logger.error("Failed to update entity", e);
                throw new RuntimeException("Failed to update entity", e);
            }
        });
    }

    /**
     * Finds an entity by ID.
     */
    public Promise<Optional<T>> findById(ID id) {
        return findOne("SELECT * FROM " + tableName + " WHERE " + idColumn + " = ?", 
            ps -> ps.setObject(1, id));
    }

    /**
     * Finds an entity by ID with tenant isolation.
     */
    public Promise<Optional<T>> findById(String tenantId, ID id) {
        return findOne("SELECT * FROM " + tableName + " WHERE tenant_id = ? AND " + idColumn + " = ?",
            ps -> {
                ps.setString(1, tenantId);
                ps.setObject(2, id);
            });
    }

    /**
     * Checks if an entity exists by ID.
     */
    public Promise<Boolean> existsById(ID id) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT 1 FROM " + tableName + " WHERE " + idColumn + " = ?")) {
                ps.setObject(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                logger.error("Failed to check existence", e);
                throw new RuntimeException("Failed to check existence", e);
            }
        });
    }

    /**
     * Checks if an entity exists by ID with tenant isolation.
     */
    public Promise<Boolean> existsById(String tenantId, ID id) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT 1 FROM " + tableName + " WHERE tenant_id = ? AND " + idColumn + " = ?")) {
                ps.setString(1, tenantId);
                ps.setObject(2, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                logger.error("Failed to check existence", e);
                throw new RuntimeException("Failed to check existence", e);
            }
        });
    }

    /**
     * Deletes an entity by ID.
     */
    public Promise<Boolean> deleteById(ID id) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM " + tableName + " WHERE " + idColumn + " = ?")) {
                ps.setObject(1, id);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                logger.error("Failed to delete entity", e);
                throw new RuntimeException("Failed to delete entity", e);
            }
        });
    }

    /**
     * Deletes an entity by ID with tenant isolation.
     */
    public Promise<Boolean> deleteById(String tenantId, ID id) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM " + tableName + " WHERE tenant_id = ? AND " + idColumn + " = ?")) {
                ps.setString(1, tenantId);
                ps.setObject(2, id);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                logger.error("Failed to delete entity", e);
                throw new RuntimeException("Failed to delete entity", e);
            }
        });
    }

    // ========== Query Helpers ==========

    /**
     * Executes a query returning a single optional result.
     */
    protected Promise<Optional<T>> findOne(String sql, PreparedStatementSetter setter) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                setter.set(ps);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRow(rs));
                    }
                    return Optional.<T>empty();
                }
            } catch (SQLException e) {
                logger.error("Failed to execute query: {}", sql, e);
                throw new RuntimeException("Failed to execute query", e);
            }
        });
    }

    /**
     * Executes a query returning a list of results.
     */
    protected Promise<List<T>> findMany(String sql, PreparedStatementSetter setter) {
        return Promise.ofBlocking(executor, () -> {
            List<T> results = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                setter.set(ps);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        results.add(mapRow(rs));
                    }
                }
            } catch (SQLException e) {
                logger.error("Failed to execute query: {}", sql, e);
                throw new RuntimeException("Failed to execute query", e);
            }
            return results;
        });
    }

    /**
     * Functional interface for setting prepared statement parameters.
     */
    @FunctionalInterface
    protected interface PreparedStatementSetter {
        void set(PreparedStatement ps) throws SQLException;
    }
}
