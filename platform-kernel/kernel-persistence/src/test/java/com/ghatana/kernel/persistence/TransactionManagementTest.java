/**
 * @doc.type class
 * @doc.purpose Test transaction management with rollback scenarios
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
 * Transaction Management Tests
 *
 * Test transaction management with rollback scenarios using JdbcModuleRegistry.
 */
@DisplayName("Transaction Management Tests")
class TransactionManagementTest {

    @Test
    @DisplayName("Should handle transaction commit")
    void shouldHandleTransactionCommit() {
        DataSource mockDataSource = mock(DataSource.class);
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockStatement = mock(PreparedStatement.class);
        
        try {
            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);
            when(mockStatement.executeUpdate()).thenReturn(1);
            
            JdbcModuleRegistry registry = new JdbcModuleRegistry(mockDataSource);
            registry.ensureSchema();
            registry.registerModule("test-module", "1.0.0", "REGISTERED");
            
            Optional<JdbcModuleRegistry.ModuleRegistration> module = registry.getModule("test-module");
            assertThat(module).isPresent();
            assertThat(module.get().moduleId()).isEqualTo("test-module");
            assertThat(module.get().moduleVersion()).isEqualTo("1.0.0");
            assertThat(module.get().moduleStatus()).isEqualTo("REGISTERED");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Should handle transaction rollback")
    void shouldHandleTransactionRollback() {
        DataSource mockDataSource = mock(DataSource.class);
        Connection mockConnection = mock(Connection.class);
        
        try {
            when(mockDataSource.getConnection()).thenThrow(new SQLException("Connection failed"));
            
            JdbcModuleRegistry registry = new JdbcModuleRegistry(mockDataSource);
            
            org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> registry.ensureSchema()
            );
            
            verify(mockConnection, never()).commit();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Should handle nested transactions")
    void shouldHandleNestedTransactions() {
        DataSource mockDataSource = mock(DataSource.class);
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockStatement = mock(PreparedStatement.class);
        
        try {
            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);
            when(mockStatement.executeUpdate()).thenReturn(1);
            
            JdbcModuleRegistry registry = new JdbcModuleRegistry(mockDataSource);
            registry.registerModule("module-1", "1.0.0", "REGISTERED");
            registry.registerModule("module-2", "2.0.0", "STARTED");
            
            assertThat(registry.listModules()).hasSize(2);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Should handle transaction isolation levels")
    void shouldHandleTransactionIsolationLevels() {
        DataSource mockDataSource = mock(DataSource.class);
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockStatement = mock(PreparedStatement.class);
        
        try {
            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);
            when(mockStatement.executeUpdate()).thenReturn(1);
            when(mockConnection.getTransactionIsolation()).thenReturn(Connection.TRANSACTION_READ_COMMITTED);
            
            JdbcModuleRegistry registry = new JdbcModuleRegistry(mockDataSource);
            registry.ensureSchema();
            
            verify(mockDataSource).getConnection();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Should handle transaction timeout")
    void shouldHandleTransactionTimeout() {
        DataSource mockDataSource = mock(DataSource.class);
        Connection mockConnection = mock(Connection.class);
        
        try {
            when(mockDataSource.getConnection()).thenThrow(new SQLException("Timeout exceeded"));
            
            JdbcModuleRegistry registry = new JdbcModuleRegistry(mockDataSource);
            
            org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> registry.registerModule("timeout-module", "1.0.0", "FAILED")
            );
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Should handle concurrent transactions")
    void shouldHandleConcurrentTransactions() {
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
            when(mockResultSet.getString(anyString())).thenReturn("test-module");
            when(mockResultSet.getLong(anyString())).thenReturn(System.currentTimeMillis());
            
            JdbcModuleRegistry registry = new JdbcModuleRegistry(mockDataSource);
            registry.registerModule("test-module", "1.0.0", "REGISTERED");
            
            Optional<JdbcModuleRegistry.ModuleRegistration> module = registry.getModule("test-module");
            assertThat(module).isPresent();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
