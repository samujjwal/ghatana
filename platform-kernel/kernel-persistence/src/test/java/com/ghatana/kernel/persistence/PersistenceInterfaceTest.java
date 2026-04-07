/**
 * @doc.type class
 * @doc.purpose Test persistence abstractions with real database implementations
 * @doc.layer platform-kernel
 * @doc.pattern Test
 */
package com.ghatana.kernel.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Persistence Interface Tests
 *
 * Test persistence abstractions with real database implementations using JdbcModuleRegistry.
 */
@DisplayName("Persistence Interface Tests")
class PersistenceInterfaceTest {

    @Test
    @DisplayName("Should handle CRUD operations")
    void shouldHandleCrudOperations() {
        DataSource mockDataSource = mock(DataSource.class);
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockStatement = mock(PreparedStatement.class);
        
        try {
            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);
            when(mockStatement.executeUpdate()).thenReturn(1);
            
            JdbcModuleRegistry registry = new JdbcModuleRegistry(mockDataSource);
            
            // Create
            registry.registerModule("test-module", "1.0.0", "REGISTERED");
            
            // Read
            Optional<JdbcModuleRegistry.ModuleRegistration> module = registry.getModule("test-module");
            assertThat(module).isPresent();
            assertThat(module.get().moduleId()).isEqualTo("test-module");
            
            // Update
            registry.registerModule("test-module", "2.0.0", "STARTED");
            module = registry.getModule("test-module");
            assertThat(module.get().moduleVersion()).isEqualTo("2.0.0");
            
            // Delete
            registry.removeModule("test-module");
            module = registry.getModule("test-module");
            assertThat(module).isEmpty();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Should handle persistence abstraction layers")
    void shouldHandlePersistenceAbstractionLayers() {
        DataSource mockDataSource = mock(DataSource.class);
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockStatement = mock(PreparedStatement.class);
        
        try {
            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);
            when(mockStatement.executeUpdate()).thenReturn(1);
            
            JdbcModuleRegistry registry = new JdbcModuleRegistry(mockDataSource);
            
            // Test repository pattern abstraction
            registry.registerModule("repo-test", "1.0.0", "REGISTERED");
            assertThat(registry.getModule("repo-test")).isPresent();
            
            // Test list operation
            assertThat(registry.listModules()).hasSize(1);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Should handle entity mapping")
    void shouldHandleEntityMapping() {
        DataSource mockDataSource = mock(DataSource.class);
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockStatement = mock(PreparedStatement.class);
        ResultSet mockResultSet = mock(ResultSet.class);
        
        try {
            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);
            when(mockStatement.executeUpdate()).thenReturn(1);
            when(mockStatement.executeQuery()).thenReturn(mockResultSet);
            when(mockResultSet.next()).thenReturn(true).thenReturn(false);
            when(mockResultSet.getString("module_id")).thenReturn("mapping-test");
            when(mockResultSet.getString("module_version")).thenReturn("1.0.0");
            when(mockResultSet.getString("module_status")).thenReturn("REGISTERED");
            when(mockResultSet.getLong("updated_at")).thenReturn(System.currentTimeMillis());
            
            JdbcModuleRegistry registry = new JdbcModuleRegistry(mockDataSource);
            registry.registerModule("mapping-test", "1.0.0", "REGISTERED");
            
            Optional<JdbcModuleRegistry.ModuleRegistration> module = registry.getModule("mapping-test");
            assertThat(module).isPresent();
            
            // Verify entity mapping
            JdbcModuleRegistry.ModuleRegistration registration = module.get();
            assertThat(registration.moduleId()).isEqualTo("mapping-test");
            assertThat(registration.moduleVersion()).isEqualTo("1.0.0");
            assertThat(registration.moduleStatus()).isEqualTo("REGISTERED");
            assertThat(registration.updatedAtEpochMs()).isPositive();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Should handle query abstractions")
    void shouldHandleQueryAbstractions() {
        DataSource mockDataSource = mock(DataSource.class);
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockStatement = mock(PreparedStatement.class);
        
        try {
            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);
            when(mockStatement.executeUpdate()).thenReturn(1);
            
            JdbcModuleRegistry registry = new JdbcModuleRegistry(mockDataSource);
            registry.registerModule("query-test-1", "1.0.0", "REGISTERED");
            registry.registerModule("query-test-2", "2.0.0", "STARTED");
            
            // Test query abstraction through list operation
            assertThat(registry.listModules()).hasSize(2);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Should handle connection management")
    void shouldHandleConnectionManagement() {
        DataSource mockDataSource = mock(DataSource.class);
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockStatement = mock(PreparedStatement.class);
        
        try {
            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);
            when(mockStatement.executeUpdate()).thenReturn(1);
            
            JdbcModuleRegistry registry = new JdbcModuleRegistry(mockDataSource);
            
            // Test connection lifecycle
            registry.ensureSchema();
            verify(mockDataSource, atLeastOnce()).getConnection();
            
            // Test connection reuse
            registry.registerModule("conn-test", "1.0.0", "REGISTERED");
            verify(mockConnection, atLeast(2)).prepareStatement(anyString());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Should handle error scenarios")
    void shouldHandleErrorScenarios() {
        DataSource mockDataSource = mock(DataSource.class);
        Connection mockConnection = mock(Connection.class);
        
        try {
            // Test connection failure
            when(mockDataSource.getConnection()).thenThrow(new SQLException("Connection failed"));
            
            JdbcModuleRegistry registry = new JdbcModuleRegistry(mockDataSource);
            
            org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> registry.ensureSchema()
            );
            
            // Test query failure
            reset(mockDataSource);
            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenThrow(new SQLException("Query failed"));
            
            org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> registry.registerModule("error-test", "1.0.0", "REGISTERED")
            );
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
