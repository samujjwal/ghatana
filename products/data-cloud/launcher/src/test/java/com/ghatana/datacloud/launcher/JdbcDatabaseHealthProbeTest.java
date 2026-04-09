package com.ghatana.datacloud.launcher;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link JdbcDatabaseHealthProbe}.
 *
 * @doc.type class
 * @doc.purpose Verify standalone JDBC health snapshots for launcher health endpoints
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("JdbcDatabaseHealthProbe")
class JdbcDatabaseHealthProbeTest {

    @Test
    void reportsUpWhenConnectionIsValid() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(5)).thenReturn(true);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("PostgreSQL");
        when(metadata.getDatabaseProductVersion()).thenReturn("16.2");
        when(metadata.getDriverName()).thenReturn("PostgreSQL JDBC Driver");

        Map<String, Object> snapshot = new JdbcDatabaseHealthProbe(dataSource, 5).get();

        assertThat(snapshot.get("status")).isEqualTo("UP");
        assertThat(snapshot.get("databaseProduct")).isEqualTo("PostgreSQL");
        assertThat(snapshot.get("databaseVersion")).isEqualTo("16.2");
        assertThat(snapshot.get("driver")).isEqualTo("PostgreSQL JDBC Driver");
        assertThat(snapshot.get("validationTimeoutSeconds")).isEqualTo(5);
    }

    @Test
    void reportsDownWhenValidationFails() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(3)).thenReturn(false);

        Map<String, Object> snapshot = new JdbcDatabaseHealthProbe(dataSource, 3).get();

        assertThat(snapshot.get("status")).isEqualTo("DOWN");
        assertThat(snapshot.get("message")).isEqualTo("Connection validation failed");
        assertThat(snapshot.get("validationTimeoutSeconds")).isEqualTo(3);
    }

    @Test
    void reportsDownWhenConnectionThrows() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new SQLException("database unavailable"));

        Map<String, Object> snapshot = new JdbcDatabaseHealthProbe(dataSource, 4).get();

        assertThat(snapshot.get("status")).isEqualTo("DOWN");
        assertThat((String) snapshot.get("message")).contains("SQLException").contains("database unavailable");
        assertThat(snapshot.get("validationTimeoutSeconds")).isEqualTo(4);
    }
}
