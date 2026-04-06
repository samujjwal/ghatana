package com.ghatana.kernel.persistence;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * JDBC-backed registry for kernel module lifecycle state.
 *
 * <p>Tracks module ID, version and current lifecycle status so kernel nodes can
 * recover the latest module state after restarts.
 *
 * @doc.type class
 * @doc.purpose Durable JDBC module-state registry for kernel lifecycle recovery
 * @doc.layer platform
 * @doc.pattern Repository
 */
public class JdbcModuleRegistry {

    private final DataSource dataSource;

    public JdbcModuleRegistry(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource cannot be null");
    }

    /** Creates the registry table if absent. */
    public void ensureSchema() {
        String sql = """
            CREATE TABLE IF NOT EXISTS kernel_module_registry (
              module_id VARCHAR(200) PRIMARY KEY,
              module_version VARCHAR(64) NOT NULL,
              module_status VARCHAR(32) NOT NULL,
              updated_at BIGINT NOT NULL
            )
            """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to create kernel_module_registry table", exception);
        }
    }

    /** Registers or updates a module state snapshot. */
    public void registerModule(String moduleId, String moduleVersion, String moduleStatus) {
        Objects.requireNonNull(moduleId, "moduleId cannot be null");
        Objects.requireNonNull(moduleVersion, "moduleVersion cannot be null");
        Objects.requireNonNull(moduleStatus, "moduleStatus cannot be null");

        String sql = """
            MERGE INTO kernel_module_registry (module_id, module_version, module_status, updated_at)
            KEY (module_id)
            VALUES (?, ?, ?, ?)
            """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, moduleId);
            statement.setString(2, moduleVersion);
            statement.setString(3, moduleStatus);
            statement.setLong(4, Instant.now().toEpochMilli());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to register module state", exception);
        }
    }

    /** Looks up a single module registration by ID. */
    public Optional<ModuleRegistration> getModule(String moduleId) {
        String sql = """
            SELECT module_id, module_version, module_status, updated_at
            FROM kernel_module_registry
            WHERE module_id = ?
            """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, moduleId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(new ModuleRegistration(
                    resultSet.getString("module_id"),
                    resultSet.getString("module_version"),
                    resultSet.getString("module_status"),
                    resultSet.getLong("updated_at")
                ));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to query module state", exception);
        }
    }

    /** Returns all module registrations ordered by module ID. */
    public List<ModuleRegistration> listModules() {
        String sql = """
            SELECT module_id, module_version, module_status, updated_at
            FROM kernel_module_registry
            ORDER BY module_id ASC
            """;
        List<ModuleRegistration> modules = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                modules.add(new ModuleRegistration(
                    resultSet.getString("module_id"),
                    resultSet.getString("module_version"),
                    resultSet.getString("module_status"),
                    resultSet.getLong("updated_at")
                ));
            }
            return modules;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to list module states", exception);
        }
    }

    /** Removes a module registration. */
    public void removeModule(String moduleId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "DELETE FROM kernel_module_registry WHERE module_id = ?")) {
            statement.setString(1, moduleId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to remove module state", exception);
        }
    }

    /**
     * Immutable module-state snapshot.
     *
     * @param moduleId kernel module ID (for example, platform:java:kernel)
     * @param moduleVersion semantic version of the module
     * @param moduleStatus lifecycle state (REGISTERED, STARTED, STOPPED, FAILED)
     * @param updatedAtEpochMs last update timestamp in epoch milliseconds
     */
    public record ModuleRegistration(
        String moduleId,
        String moduleVersion,
        String moduleStatus,
        long updatedAtEpochMs
    ) {}
}
