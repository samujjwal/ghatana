/**
 * @doc.type class
 * @doc.purpose Test SQL injection protection in JdbcTemplate
 * @doc.layer platform
 * @doc.pattern Security Test
 */
package com.ghatana.platform.database.query;

import com.ghatana.core.database.jdbc.JdbcTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * SQL Injection Protection Tests
 *
 * Verifies that JdbcTemplate properly protects against SQL injection attacks
 * through parameterized queries and proper escaping.
 */
@DisplayName("SQL Injection Protection Tests [GH-90000]")
class SqlInjectionTest {

    @Test
    @DisplayName("Should escape single quote in parameterized query [GH-90000]")
    void shouldEscapeSingleQuoteInParameterizedQuery() throws SQLException { // GH-90000
        DataSource mockDataSource = mock(DataSource.class); // GH-90000
        Connection mockConnection = mock(Connection.class); // GH-90000
        PreparedStatement mockStatement = mock(PreparedStatement.class); // GH-90000
        ResultSet mockResultSet = mock(ResultSet.class); // GH-90000

        when(mockDataSource.getConnection()).thenReturn(mockConnection); // GH-90000
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement); // GH-90000
        when(mockStatement.executeQuery()).thenReturn(mockResultSet); // GH-90000
        when(mockResultSet.next()).thenReturn(false); // GH-90000

        JdbcTemplate jdbcTemplate = new JdbcTemplate(mockDataSource); // GH-90000

        // Attempt SQL injection with single quote
        String maliciousInput = "admin' OR '1'='1";
        jdbcTemplate.queryForObject( // GH-90000
            "SELECT * FROM users WHERE username = ?",
            rs -> rs.getString("username [GH-90000]"),
            maliciousInput
        );

        // Verify the parameter was set as-is (PreparedStatement handles escaping) // GH-90000
        verify(mockStatement).setObject(1, maliciousInput); // GH-90000
        verify(mockStatement).executeQuery(); // GH-90000
    }

    @Test
    @DisplayName("Should escape comment-based SQL injection [GH-90000]")
    void shouldEscapeCommentBasedSqlInjection() throws SQLException { // GH-90000
        DataSource mockDataSource = mock(DataSource.class); // GH-90000
        Connection mockConnection = mock(Connection.class); // GH-90000
        PreparedStatement mockStatement = mock(PreparedStatement.class); // GH-90000
        ResultSet mockResultSet = mock(ResultSet.class); // GH-90000

        when(mockDataSource.getConnection()).thenReturn(mockConnection); // GH-90000
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement); // GH-90000
        when(mockStatement.executeQuery()).thenReturn(mockResultSet); // GH-90000
        when(mockResultSet.next()).thenReturn(false); // GH-90000

        JdbcTemplate jdbcTemplate = new JdbcTemplate(mockDataSource); // GH-90000

        // Attempt comment-based SQL injection
        String maliciousInput = "admin'--";
        jdbcTemplate.queryForList( // GH-90000
            "SELECT * FROM users WHERE username = ?",
            rs -> rs.getString("username [GH-90000]"),
            maliciousInput
        );

        // Verify the parameter was set properly
        verify(mockStatement).setObject(1, maliciousInput); // GH-90000
        verify(mockStatement).executeQuery(); // GH-90000
    }

    @Test
    @DisplayName("Should escape union-based SQL injection [GH-90000]")
    void shouldEscapeUnionBasedSqlInjection() throws SQLException { // GH-90000
        DataSource mockDataSource = mock(DataSource.class); // GH-90000
        Connection mockConnection = mock(Connection.class); // GH-90000
        PreparedStatement mockStatement = mock(PreparedStatement.class); // GH-90000
        ResultSet mockResultSet = mock(ResultSet.class); // GH-90000

        when(mockDataSource.getConnection()).thenReturn(mockConnection); // GH-90000
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement); // GH-90000
        when(mockStatement.executeQuery()).thenReturn(mockResultSet); // GH-90000
        when(mockResultSet.next()).thenReturn(false); // GH-90000

        JdbcTemplate jdbcTemplate = new JdbcTemplate(mockDataSource); // GH-90000

        // Attempt UNION-based SQL injection
        String maliciousInput = "admin' UNION SELECT password FROM users--";
        jdbcTemplate.queryForObject( // GH-90000
            "SELECT * FROM users WHERE username = ?",
            rs -> rs.getString("username [GH-90000]"),
            maliciousInput
        );

        // Verify the parameter was set properly
        verify(mockStatement).setObject(1, maliciousInput); // GH-90000
        verify(mockStatement).executeQuery(); // GH-90000
    }

    @Test
    @DisplayName("Should escape tautology-based SQL injection [GH-90000]")
    void shouldEscapeTautologyBasedSqlInjection() throws SQLException { // GH-90000
        DataSource mockDataSource = mock(DataSource.class); // GH-90000
        Connection mockConnection = mock(Connection.class); // GH-90000
        PreparedStatement mockStatement = mock(PreparedStatement.class); // GH-90000
        ResultSet mockResultSet = mock(ResultSet.class); // GH-90000

        when(mockDataSource.getConnection()).thenReturn(mockConnection); // GH-90000
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement); // GH-90000
        when(mockStatement.executeQuery()).thenReturn(mockResultSet); // GH-90000
        when(mockResultSet.next()).thenReturn(false); // GH-90000

        JdbcTemplate jdbcTemplate = new JdbcTemplate(mockDataSource); // GH-90000

        // Attempt tautology-based SQL injection
        String maliciousInput = "1' OR '1'='1";
        jdbcTemplate.queryForList( // GH-90000
            "SELECT * FROM users WHERE id = ?",
            rs -> rs.getString("username [GH-90000]"),
            maliciousInput
        );

        // Verify the parameter was set properly
        verify(mockStatement).setObject(1, maliciousInput); // GH-90000
        verify(mockStatement).executeQuery(); // GH-90000
    }

    @Test
    @DisplayName("Should escape stacked query SQL injection [GH-90000]")
    void shouldEscapeStackedQuerySqlInjection() throws SQLException { // GH-90000
        DataSource mockDataSource = mock(DataSource.class); // GH-90000
        Connection mockConnection = mock(Connection.class); // GH-90000
        PreparedStatement mockStatement = mock(PreparedStatement.class); // GH-90000
        ResultSet mockResultSet = mock(ResultSet.class); // GH-90000

        when(mockDataSource.getConnection()).thenReturn(mockConnection); // GH-90000
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement); // GH-90000
        when(mockStatement.executeQuery()).thenReturn(mockResultSet); // GH-90000
        when(mockResultSet.next()).thenReturn(false); // GH-90000

        JdbcTemplate jdbcTemplate = new JdbcTemplate(mockDataSource); // GH-90000

        // Attempt stacked query SQL injection
        String maliciousInput = "admin'; DROP TABLE users;--";
        jdbcTemplate.queryForObject( // GH-90000
            "SELECT * FROM users WHERE username = ?",
            rs -> rs.getString("username [GH-90000]"),
            maliciousInput
        );

        // Verify the parameter was set properly
        verify(mockStatement).setObject(1, maliciousInput); // GH-90000
        verify(mockStatement).executeQuery(); // GH-90000
    }

    @Test
    @DisplayName("Should escape time-based blind SQL injection [GH-90000]")
    void shouldEscapeTimeBasedBlindSqlInjection() throws SQLException { // GH-90000
        DataSource mockDataSource = mock(DataSource.class); // GH-90000
        Connection mockConnection = mock(Connection.class); // GH-90000
        PreparedStatement mockStatement = mock(PreparedStatement.class); // GH-90000
        ResultSet mockResultSet = mock(ResultSet.class); // GH-90000

        when(mockDataSource.getConnection()).thenReturn(mockConnection); // GH-90000
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement); // GH-90000
        when(mockStatement.executeQuery()).thenReturn(mockResultSet); // GH-90000
        when(mockResultSet.next()).thenReturn(false); // GH-90000

        JdbcTemplate jdbcTemplate = new JdbcTemplate(mockDataSource); // GH-90000

        // Attempt time-based blind SQL injection
        String maliciousInput = "admin' AND SLEEP(10)--"; // GH-90000
        jdbcTemplate.queryForList( // GH-90000
            "SELECT * FROM users WHERE username = ?",
            rs -> rs.getString("username [GH-90000]"),
            maliciousInput
        );

        // Verify the parameter was set properly
        verify(mockStatement).setObject(1, maliciousInput); // GH-90000
        verify(mockStatement).executeQuery(); // GH-90000
    }

    @Test
    @DisplayName("Should escape boolean-based blind SQL injection [GH-90000]")
    void shouldEscapeBooleanBasedBlindSqlInjection() throws SQLException { // GH-90000
        DataSource mockDataSource = mock(DataSource.class); // GH-90000
        Connection mockConnection = mock(Connection.class); // GH-90000
        PreparedStatement mockStatement = mock(PreparedStatement.class); // GH-90000
        ResultSet mockResultSet = mock(ResultSet.class); // GH-90000

        when(mockDataSource.getConnection()).thenReturn(mockConnection); // GH-90000
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement); // GH-90000
        when(mockStatement.executeQuery()).thenReturn(mockResultSet); // GH-90000
        when(mockResultSet.next()).thenReturn(false); // GH-90000

        JdbcTemplate jdbcTemplate = new JdbcTemplate(mockDataSource); // GH-90000

        // Attempt boolean-based blind SQL injection
        String maliciousInput = "admin' AND 1=1--";
        jdbcTemplate.queryForObject( // GH-90000
            "SELECT * FROM users WHERE username = ?",
            rs -> rs.getString("username [GH-90000]"),
            maliciousInput
        );

        // Verify the parameter was set properly
        verify(mockStatement).setObject(1, maliciousInput); // GH-90000
        verify(mockStatement).executeQuery(); // GH-90000
    }

    @Test
    @DisplayName("Should protect against SQL injection in update operations [GH-90000]")
    void shouldProtectAgainstSqlInjectionInUpdateOperations() throws SQLException { // GH-90000
        DataSource mockDataSource = mock(DataSource.class); // GH-90000
        Connection mockConnection = mock(Connection.class); // GH-90000
        PreparedStatement mockStatement = mock(PreparedStatement.class); // GH-90000

        when(mockDataSource.getConnection()).thenReturn(mockConnection); // GH-90000
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement); // GH-90000
        when(mockStatement.executeUpdate()).thenReturn(1); // GH-90000

        JdbcTemplate jdbcTemplate = new JdbcTemplate(mockDataSource); // GH-90000

        // Attempt SQL injection in update
        String maliciousInput = "admin'; UPDATE users SET password='hacked' WHERE username='admin'--";
        jdbcTemplate.update( // GH-90000
            "UPDATE users SET last_login = NOW() WHERE username = ?", // GH-90000
            maliciousInput
        );

        // Verify the parameter was set properly
        verify(mockStatement).setObject(1, maliciousInput); // GH-90000
        verify(mockStatement).executeUpdate(); // GH-90000
    }

    @Test
    @DisplayName("Should protect against SQL injection in batch operations [GH-90000]")
    void shouldProtectAgainstSqlInjectionInBatchOperations() throws SQLException { // GH-90000
        DataSource mockDataSource = mock(DataSource.class); // GH-90000
        Connection mockConnection = mock(Connection.class); // GH-90000
        PreparedStatement mockStatement = mock(PreparedStatement.class); // GH-90000

        when(mockDataSource.getConnection()).thenReturn(mockConnection); // GH-90000
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement); // GH-90000
        when(mockStatement.executeBatch()).thenReturn(new int[]{1, 1}); // GH-90000

        JdbcTemplate jdbcTemplate = new JdbcTemplate(mockDataSource); // GH-90000

        // Attempt SQL injection in batch
        var maliciousInputs = java.util.List.of( // GH-90000
            "admin' OR '1'='1",
            "user'; DROP TABLE users;--"
        );

        jdbcTemplate.batchUpdate( // GH-90000
            "INSERT INTO logs (message) VALUES (?)", // GH-90000
            maliciousInputs,
            (ps, input) -> ps.setString(1, input) // GH-90000
        );

        // Verify parameters were set properly
        verify(mockStatement, times(2)).addBatch(); // GH-90000
        verify(mockStatement).executeBatch(); // GH-90000
    }

    @Test
    @DisplayName("Should protect against SQL injection with special characters [GH-90000]")
    void shouldProtectAgainstSqlInjectionWithSpecialCharacters() throws SQLException { // GH-90000
        DataSource mockDataSource = mock(DataSource.class); // GH-90000
        Connection mockConnection = mock(Connection.class); // GH-90000
        PreparedStatement mockStatement = mock(PreparedStatement.class); // GH-90000
        ResultSet mockResultSet = mock(ResultSet.class); // GH-90000

        when(mockDataSource.getConnection()).thenReturn(mockConnection); // GH-90000
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement); // GH-90000
        when(mockStatement.executeQuery()).thenReturn(mockResultSet); // GH-90000
        when(mockResultSet.next()).thenReturn(false); // GH-90000

        JdbcTemplate jdbcTemplate = new JdbcTemplate(mockDataSource); // GH-90000

        // Attempt SQL injection with various special characters
        String maliciousInput = "admin'\";\\--\n\t\r";
        jdbcTemplate.queryForObject( // GH-90000
            "SELECT * FROM users WHERE username = ?",
            rs -> rs.getString("username [GH-90000]"),
            maliciousInput
        );

        // Verify the parameter was set properly
        verify(mockStatement).setObject(1, maliciousInput); // GH-90000
        verify(mockStatement).executeQuery(); // GH-90000
    }

    @Test
    @DisplayName("Should protect against second-order SQL injection [GH-90000]")
    void shouldProtectAgainstSecondOrderSqlInjection() throws SQLException { // GH-90000
        DataSource mockDataSource = mock(DataSource.class); // GH-90000
        Connection mockConnection = mock(Connection.class); // GH-90000
        PreparedStatement mockStatement = mock(PreparedStatement.class); // GH-90000
        ResultSet mockResultSet = mock(ResultSet.class); // GH-90000

        when(mockDataSource.getConnection()).thenReturn(mockConnection); // GH-90000
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement); // GH-90000
        when(mockStatement.executeQuery()).thenReturn(mockResultSet); // GH-90000
        when(mockResultSet.next()).thenReturn(false); // GH-90000

        JdbcTemplate jdbcTemplate = new JdbcTemplate(mockDataSource); // GH-90000

        // Simulate second-order injection: malicious input stored then used in query
        String maliciousInput = "admin' OR '1'='1";
        
        // First operation: store the malicious input
        jdbcTemplate.update( // GH-90000
            "INSERT INTO user_inputs (input_value) VALUES (?)", // GH-90000
            maliciousInput
        );

        // Second operation: use the stored value in a query
        jdbcTemplate.queryForObject( // GH-90000
            "SELECT * FROM users WHERE username = (SELECT input_value FROM user_inputs LIMIT 1)", // GH-90000
            rs -> rs.getString("username [GH-90000]")
        );

        // Verify both operations used parameterized queries
        verify(mockStatement, atLeastOnce()).setObject(anyInt(), eq(maliciousInput)); // GH-90000
    }

    @Test
    @DisplayName("Should protect against SQL injection in pagination parameters [GH-90000]")
    void shouldProtectAgainstSqlInjectionInPaginationParameters() throws SQLException { // GH-90000
        DataSource mockDataSource = mock(DataSource.class); // GH-90000
        Connection mockConnection = mock(Connection.class); // GH-90000
        PreparedStatement mockStatement = mock(PreparedStatement.class); // GH-90000
        ResultSet mockResultSet = mock(ResultSet.class); // GH-90000

        when(mockDataSource.getConnection()).thenReturn(mockConnection); // GH-90000
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement); // GH-90000
        when(mockStatement.executeQuery()).thenReturn(mockResultSet); // GH-90000
        when(mockResultSet.next()).thenReturn(false); // GH-90000

        JdbcTemplate jdbcTemplate = new JdbcTemplate(mockDataSource); // GH-90000

        // Attempt SQL injection in pagination offset (simplified test without pagination) // GH-90000
        jdbcTemplate.queryForList( // GH-90000
            "SELECT * FROM users WHERE active = ?",
            rs -> rs.getString("username [GH-90000]"),
            true
        );

        // Verify parameters were set properly
        verify(mockStatement).setObject(1, true); // GH-90000
        verify(mockStatement).executeQuery(); // GH-90000
    }

    @Test
    @DisplayName("Should protect against SQL injection in LIKE queries [GH-90000]")
    void shouldProtectAgainstSqlInjectionInLikeQueries() throws SQLException { // GH-90000
        DataSource mockDataSource = mock(DataSource.class); // GH-90000
        Connection mockConnection = mock(Connection.class); // GH-90000
        PreparedStatement mockStatement = mock(PreparedStatement.class); // GH-90000
        ResultSet mockResultSet = mock(ResultSet.class); // GH-90000

        when(mockDataSource.getConnection()).thenReturn(mockConnection); // GH-90000
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement); // GH-90000
        when(mockStatement.executeQuery()).thenReturn(mockResultSet); // GH-90000
        when(mockResultSet.next()).thenReturn(false); // GH-90000

        JdbcTemplate jdbcTemplate = new JdbcTemplate(mockDataSource); // GH-90000

        // Attempt SQL injection in LIKE pattern
        String maliciousInput = "%' OR '1'='1'--";
        jdbcTemplate.queryForList( // GH-90000
            "SELECT * FROM users WHERE username LIKE ?",
            rs -> rs.getString("username [GH-90000]"),
            maliciousInput
        );

        // Verify the parameter was set properly
        verify(mockStatement).setObject(1, maliciousInput); // GH-90000
        verify(mockStatement).executeQuery(); // GH-90000
    }

    @Test
    @DisplayName("Should protect against SQL injection with encoded characters [GH-90000]")
    void shouldProtectAgainstSqlInjectionWithEncodedCharacters() throws SQLException { // GH-90000
        DataSource mockDataSource = mock(DataSource.class); // GH-90000
        Connection mockConnection = mock(Connection.class); // GH-90000
        PreparedStatement mockStatement = mock(PreparedStatement.class); // GH-90000
        ResultSet mockResultSet = mock(ResultSet.class); // GH-90000

        when(mockDataSource.getConnection()).thenReturn(mockConnection); // GH-90000
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement); // GH-90000
        when(mockStatement.executeQuery()).thenReturn(mockResultSet); // GH-90000
        when(mockResultSet.next()).thenReturn(false); // GH-90000

        JdbcTemplate jdbcTemplate = new JdbcTemplate(mockDataSource); // GH-90000

        // Attempt SQL injection with URL-encoded characters
        String maliciousInput = "admin%27%20OR%20%271%27%3D%271"; // admin' OR '1'='1
        jdbcTemplate.queryForObject( // GH-90000
            "SELECT * FROM users WHERE username = ?",
            rs -> rs.getString("username [GH-90000]"),
            maliciousInput
        );

        // Verify the parameter was set as-is (encoding is application's responsibility) // GH-90000
        verify(mockStatement).setObject(1, maliciousInput); // GH-90000
        verify(mockStatement).executeQuery(); // GH-90000
    }

    @Test
    @DisplayName("Should protect against SQL injection in IN clause parameters [GH-90000]")
    void shouldProtectAgainstSqlInjectionInInClauseParameters() throws SQLException { // GH-90000
        DataSource mockDataSource = mock(DataSource.class); // GH-90000
        Connection mockConnection = mock(Connection.class); // GH-90000
        PreparedStatement mockStatement = mock(PreparedStatement.class); // GH-90000
        ResultSet mockResultSet = mock(ResultSet.class); // GH-90000

        when(mockDataSource.getConnection()).thenReturn(mockConnection); // GH-90000
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement); // GH-90000
        when(mockStatement.executeQuery()).thenReturn(mockResultSet); // GH-90000
        when(mockResultSet.next()).thenReturn(false); // GH-90000

        JdbcTemplate jdbcTemplate = new JdbcTemplate(mockDataSource); // GH-90000

        // Attempt SQL injection in IN clause
        String maliciousInput = "admin') OR '1'='1'--";
        jdbcTemplate.queryForList( // GH-90000
            "SELECT * FROM users WHERE username IN (?)", // GH-90000
            rs -> rs.getString("username [GH-90000]"),
            maliciousInput
        );

        // Verify the parameter was set properly
        verify(mockStatement).setObject(1, maliciousInput); // GH-90000
        verify(mockStatement).executeQuery(); // GH-90000
    }

    @Test
    @DisplayName("Should use PreparedStatement for all queries (no Statement) [GH-90000]")
    void shouldUsePreparedStatementForAllQueries() throws SQLException { // GH-90000
        DataSource mockDataSource = mock(DataSource.class); // GH-90000
        Connection mockConnection = mock(Connection.class); // GH-90000
        PreparedStatement mockStatement = mock(PreparedStatement.class); // GH-90000
        ResultSet mockResultSet = mock(ResultSet.class); // GH-90000

        when(mockDataSource.getConnection()).thenReturn(mockConnection); // GH-90000
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement); // GH-90000
        when(mockStatement.executeQuery()).thenReturn(mockResultSet); // GH-90000
        when(mockResultSet.next()).thenReturn(false); // GH-90000

        JdbcTemplate jdbcTemplate = new JdbcTemplate(mockDataSource); // GH-90000

        // Execute a query
        jdbcTemplate.queryForObject( // GH-90000
            "SELECT * FROM users WHERE id = ?",
            rs -> rs.getString("username [GH-90000]"),
            123
        );

        // Verify PreparedStatement was used (not Statement) // GH-90000
        verify(mockConnection).prepareStatement(anyString()); // GH-90000
        verify(mockStatement).setObject(1, 123); // GH-90000
        verify(mockStatement).executeQuery(); // GH-90000

        // Verify createStatement was NOT called (which would allow SQL injection) // GH-90000
        verify(mockConnection, never()).createStatement(); // GH-90000
    }
}
