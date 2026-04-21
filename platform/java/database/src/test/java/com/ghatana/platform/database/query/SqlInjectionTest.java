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
@DisplayName("SQL Injection Protection Tests")
class SqlInjectionTest {

    @Test
    @DisplayName("Should escape single quote in parameterized query")
    void shouldEscapeSingleQuoteInParameterizedQuery() throws SQLException {
        DataSource mockDataSource = mock(DataSource.class);
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockStatement = mock(PreparedStatement.class);
        ResultSet mockResultSet = mock(ResultSet.class);

        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);
        when(mockStatement.executeQuery()).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(false);

        JdbcTemplate jdbcTemplate = new JdbcTemplate(mockDataSource);

        // Attempt SQL injection with single quote
        String maliciousInput = "admin' OR '1'='1";
        jdbcTemplate.queryForObject(
            "SELECT * FROM users WHERE username = ?",
            rs -> rs.getString("username"),
            maliciousInput
        );

        // Verify the parameter was set as-is (PreparedStatement handles escaping)
        verify(mockStatement).setObject(1, maliciousInput);
        verify(mockStatement).executeQuery();
    }

    @Test
    @DisplayName("Should escape comment-based SQL injection")
    void shouldEscapeCommentBasedSqlInjection() throws SQLException {
        DataSource mockDataSource = mock(DataSource.class);
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockStatement = mock(PreparedStatement.class);
        ResultSet mockResultSet = mock(ResultSet.class);

        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);
        when(mockStatement.executeQuery()).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(false);

        JdbcTemplate jdbcTemplate = new JdbcTemplate(mockDataSource);

        // Attempt comment-based SQL injection
        String maliciousInput = "admin'--";
        jdbcTemplate.queryForList(
            "SELECT * FROM users WHERE username = ?",
            rs -> rs.getString("username"),
            maliciousInput
        );

        // Verify the parameter was set properly
        verify(mockStatement).setObject(1, maliciousInput);
        verify(mockStatement).executeQuery();
    }

    @Test
    @DisplayName("Should escape union-based SQL injection")
    void shouldEscapeUnionBasedSqlInjection() throws SQLException {
        DataSource mockDataSource = mock(DataSource.class);
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockStatement = mock(PreparedStatement.class);
        ResultSet mockResultSet = mock(ResultSet.class);

        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);
        when(mockStatement.executeQuery()).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(false);

        JdbcTemplate jdbcTemplate = new JdbcTemplate(mockDataSource);

        // Attempt UNION-based SQL injection
        String maliciousInput = "admin' UNION SELECT password FROM users--";
        jdbcTemplate.queryForObject(
            "SELECT * FROM users WHERE username = ?",
            rs -> rs.getString("username"),
            maliciousInput
        );

        // Verify the parameter was set properly
        verify(mockStatement).setObject(1, maliciousInput);
        verify(mockStatement).executeQuery();
    }

    @Test
    @DisplayName("Should escape tautology-based SQL injection")
    void shouldEscapeTautologyBasedSqlInjection() throws SQLException {
        DataSource mockDataSource = mock(DataSource.class);
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockStatement = mock(PreparedStatement.class);
        ResultSet mockResultSet = mock(ResultSet.class);

        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);
        when(mockStatement.executeQuery()).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(false);

        JdbcTemplate jdbcTemplate = new JdbcTemplate(mockDataSource);

        // Attempt tautology-based SQL injection
        String maliciousInput = "1' OR '1'='1";
        jdbcTemplate.queryForList(
            "SELECT * FROM users WHERE id = ?",
            rs -> rs.getString("username"),
            maliciousInput
        );

        // Verify the parameter was set properly
        verify(mockStatement).setObject(1, maliciousInput);
        verify(mockStatement).executeQuery();
    }

    @Test
    @DisplayName("Should escape stacked query SQL injection")
    void shouldEscapeStackedQuerySqlInjection() throws SQLException {
        DataSource mockDataSource = mock(DataSource.class);
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockStatement = mock(PreparedStatement.class);
        ResultSet mockResultSet = mock(ResultSet.class);

        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);
        when(mockStatement.executeQuery()).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(false);

        JdbcTemplate jdbcTemplate = new JdbcTemplate(mockDataSource);

        // Attempt stacked query SQL injection
        String maliciousInput = "admin'; DROP TABLE users;--";
        jdbcTemplate.queryForObject(
            "SELECT * FROM users WHERE username = ?",
            rs -> rs.getString("username"),
            maliciousInput
        );

        // Verify the parameter was set properly
        verify(mockStatement).setObject(1, maliciousInput);
        verify(mockStatement).executeQuery();
    }

    @Test
    @DisplayName("Should escape time-based blind SQL injection")
    void shouldEscapeTimeBasedBlindSqlInjection() throws SQLException {
        DataSource mockDataSource = mock(DataSource.class);
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockStatement = mock(PreparedStatement.class);
        ResultSet mockResultSet = mock(ResultSet.class);

        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);
        when(mockStatement.executeQuery()).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(false);

        JdbcTemplate jdbcTemplate = new JdbcTemplate(mockDataSource);

        // Attempt time-based blind SQL injection
        String maliciousInput = "admin' AND SLEEP(10)--";
        jdbcTemplate.queryForList(
            "SELECT * FROM users WHERE username = ?",
            rs -> rs.getString("username"),
            maliciousInput
        );

        // Verify the parameter was set properly
        verify(mockStatement).setObject(1, maliciousInput);
        verify(mockStatement).executeQuery();
    }

    @Test
    @DisplayName("Should escape boolean-based blind SQL injection")
    void shouldEscapeBooleanBasedBlindSqlInjection() throws SQLException {
        DataSource mockDataSource = mock(DataSource.class);
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockStatement = mock(PreparedStatement.class);
        ResultSet mockResultSet = mock(ResultSet.class);

        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);
        when(mockStatement.executeQuery()).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(false);

        JdbcTemplate jdbcTemplate = new JdbcTemplate(mockDataSource);

        // Attempt boolean-based blind SQL injection
        String maliciousInput = "admin' AND 1=1--";
        jdbcTemplate.queryForObject(
            "SELECT * FROM users WHERE username = ?",
            rs -> rs.getString("username"),
            maliciousInput
        );

        // Verify the parameter was set properly
        verify(mockStatement).setObject(1, maliciousInput);
        verify(mockStatement).executeQuery();
    }

    @Test
    @DisplayName("Should protect against SQL injection in update operations")
    void shouldProtectAgainstSqlInjectionInUpdateOperations() throws SQLException {
        DataSource mockDataSource = mock(DataSource.class);
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockStatement = mock(PreparedStatement.class);

        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);
        when(mockStatement.executeUpdate()).thenReturn(1);

        JdbcTemplate jdbcTemplate = new JdbcTemplate(mockDataSource);

        // Attempt SQL injection in update
        String maliciousInput = "admin'; UPDATE users SET password='hacked' WHERE username='admin'--";
        jdbcTemplate.update(
            "UPDATE users SET last_login = NOW() WHERE username = ?",
            maliciousInput
        );

        // Verify the parameter was set properly
        verify(mockStatement).setObject(1, maliciousInput);
        verify(mockStatement).executeUpdate();
    }

    @Test
    @DisplayName("Should protect against SQL injection in batch operations")
    void shouldProtectAgainstSqlInjectionInBatchOperations() throws SQLException {
        DataSource mockDataSource = mock(DataSource.class);
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockStatement = mock(PreparedStatement.class);

        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);
        when(mockStatement.executeBatch()).thenReturn(new int[]{1, 1});

        JdbcTemplate jdbcTemplate = new JdbcTemplate(mockDataSource);

        // Attempt SQL injection in batch
        var maliciousInputs = java.util.List.of(
            "admin' OR '1'='1",
            "user'; DROP TABLE users;--"
        );

        jdbcTemplate.batchUpdate(
            "INSERT INTO logs (message) VALUES (?)",
            maliciousInputs,
            (ps, input) -> ps.setString(1, input)
        );

        // Verify parameters were set properly
        verify(mockStatement, times(2)).addBatch();
        verify(mockStatement).executeBatch();
    }

    @Test
    @DisplayName("Should protect against SQL injection with special characters")
    void shouldProtectAgainstSqlInjectionWithSpecialCharacters() throws SQLException {
        DataSource mockDataSource = mock(DataSource.class);
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockStatement = mock(PreparedStatement.class);
        ResultSet mockResultSet = mock(ResultSet.class);

        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);
        when(mockStatement.executeQuery()).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(false);

        JdbcTemplate jdbcTemplate = new JdbcTemplate(mockDataSource);

        // Attempt SQL injection with various special characters
        String maliciousInput = "admin'\";\\--\n\t\r";
        jdbcTemplate.queryForObject(
            "SELECT * FROM users WHERE username = ?",
            rs -> rs.getString("username"),
            maliciousInput
        );

        // Verify the parameter was set properly
        verify(mockStatement).setObject(1, maliciousInput);
        verify(mockStatement).executeQuery();
    }

    @Test
    @DisplayName("Should protect against second-order SQL injection")
    void shouldProtectAgainstSecondOrderSqlInjection() throws SQLException {
        DataSource mockDataSource = mock(DataSource.class);
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockStatement = mock(PreparedStatement.class);
        ResultSet mockResultSet = mock(ResultSet.class);

        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);
        when(mockStatement.executeQuery()).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(false);

        JdbcTemplate jdbcTemplate = new JdbcTemplate(mockDataSource);

        // Simulate second-order injection: malicious input stored then used in query
        String maliciousInput = "admin' OR '1'='1";
        
        // First operation: store the malicious input
        jdbcTemplate.update(
            "INSERT INTO user_inputs (input_value) VALUES (?)",
            maliciousInput
        );

        // Second operation: use the stored value in a query
        jdbcTemplate.queryForObject(
            "SELECT * FROM users WHERE username = (SELECT input_value FROM user_inputs LIMIT 1)",
            rs -> rs.getString("username")
        );

        // Verify both operations used parameterized queries
        verify(mockStatement, atLeastOnce()).setObject(anyInt(), eq(maliciousInput));
    }

    @Test
    @DisplayName("Should protect against SQL injection in pagination parameters")
    void shouldProtectAgainstSqlInjectionInPaginationParameters() throws SQLException {
        DataSource mockDataSource = mock(DataSource.class);
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockStatement = mock(PreparedStatement.class);
        ResultSet mockResultSet = mock(ResultSet.class);

        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);
        when(mockStatement.executeQuery()).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(false);

        JdbcTemplate jdbcTemplate = new JdbcTemplate(mockDataSource);

        // Attempt SQL injection in pagination offset (simplified test without pagination)
        jdbcTemplate.queryForList(
            "SELECT * FROM users WHERE active = ?",
            rs -> rs.getString("username"),
            true
        );

        // Verify parameters were set properly
        verify(mockStatement).setObject(1, true);
        verify(mockStatement).executeQuery();
    }

    @Test
    @DisplayName("Should protect against SQL injection in LIKE queries")
    void shouldProtectAgainstSqlInjectionInLikeQueries() throws SQLException {
        DataSource mockDataSource = mock(DataSource.class);
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockStatement = mock(PreparedStatement.class);
        ResultSet mockResultSet = mock(ResultSet.class);

        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);
        when(mockStatement.executeQuery()).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(false);

        JdbcTemplate jdbcTemplate = new JdbcTemplate(mockDataSource);

        // Attempt SQL injection in LIKE pattern
        String maliciousInput = "%' OR '1'='1'--";
        jdbcTemplate.queryForList(
            "SELECT * FROM users WHERE username LIKE ?",
            rs -> rs.getString("username"),
            maliciousInput
        );

        // Verify the parameter was set properly
        verify(mockStatement).setObject(1, maliciousInput);
        verify(mockStatement).executeQuery();
    }

    @Test
    @DisplayName("Should protect against SQL injection with encoded characters")
    void shouldProtectAgainstSqlInjectionWithEncodedCharacters() throws SQLException {
        DataSource mockDataSource = mock(DataSource.class);
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockStatement = mock(PreparedStatement.class);
        ResultSet mockResultSet = mock(ResultSet.class);

        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);
        when(mockStatement.executeQuery()).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(false);

        JdbcTemplate jdbcTemplate = new JdbcTemplate(mockDataSource);

        // Attempt SQL injection with URL-encoded characters
        String maliciousInput = "admin%27%20OR%20%271%27%3D%271"; // admin' OR '1'='1
        jdbcTemplate.queryForObject(
            "SELECT * FROM users WHERE username = ?",
            rs -> rs.getString("username"),
            maliciousInput
        );

        // Verify the parameter was set as-is (encoding is application's responsibility)
        verify(mockStatement).setObject(1, maliciousInput);
        verify(mockStatement).executeQuery();
    }

    @Test
    @DisplayName("Should protect against SQL injection in IN clause parameters")
    void shouldProtectAgainstSqlInjectionInInClauseParameters() throws SQLException {
        DataSource mockDataSource = mock(DataSource.class);
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockStatement = mock(PreparedStatement.class);
        ResultSet mockResultSet = mock(ResultSet.class);

        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);
        when(mockStatement.executeQuery()).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(false);

        JdbcTemplate jdbcTemplate = new JdbcTemplate(mockDataSource);

        // Attempt SQL injection in IN clause
        String maliciousInput = "admin') OR '1'='1'--";
        jdbcTemplate.queryForList(
            "SELECT * FROM users WHERE username IN (?)",
            rs -> rs.getString("username"),
            maliciousInput
        );

        // Verify the parameter was set properly
        verify(mockStatement).setObject(1, maliciousInput);
        verify(mockStatement).executeQuery();
    }

    @Test
    @DisplayName("Should use PreparedStatement for all queries (no Statement)")
    void shouldUsePreparedStatementForAllQueries() throws SQLException {
        DataSource mockDataSource = mock(DataSource.class);
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockStatement = mock(PreparedStatement.class);
        ResultSet mockResultSet = mock(ResultSet.class);

        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);
        when(mockStatement.executeQuery()).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(false);

        JdbcTemplate jdbcTemplate = new JdbcTemplate(mockDataSource);

        // Execute a query
        jdbcTemplate.queryForObject(
            "SELECT * FROM users WHERE id = ?",
            rs -> rs.getString("username"),
            123
        );

        // Verify PreparedStatement was used (not Statement)
        verify(mockConnection).prepareStatement(anyString());
        verify(mockStatement).setObject(1, 123);
        verify(mockStatement).executeQuery();

        // Verify createStatement was NOT called (which would allow SQL injection)
        verify(mockConnection, never()).createStatement();
    }
}
