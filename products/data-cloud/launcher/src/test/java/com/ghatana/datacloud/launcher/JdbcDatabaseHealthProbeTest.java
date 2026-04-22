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
@DisplayName("JdbcDatabaseHealthProbe [GH-90000]")
class JdbcDatabaseHealthProbeTest {

    @Test
    void reportsUpWhenConnectionIsValid() throws Exception { // GH-90000
        DataSource dataSource = mock(DataSource.class); // GH-90000
        Connection connection = mock(Connection.class); // GH-90000
        DatabaseMetaData metadata = mock(DatabaseMetaData.class); // GH-90000

        when(dataSource.getConnection()).thenReturn(connection); // GH-90000
        when(connection.isValid(5)).thenReturn(true); // GH-90000
        when(connection.getMetaData()).thenReturn(metadata); // GH-90000
        when(metadata.getDatabaseProductName()).thenReturn("PostgreSQL [GH-90000]");
        when(metadata.getDatabaseProductVersion()).thenReturn("16.2 [GH-90000]");
        when(metadata.getDriverName()).thenReturn("PostgreSQL JDBC Driver [GH-90000]");

        Map<String, Object> snapshot = new JdbcDatabaseHealthProbe(dataSource, 5).get(); // GH-90000

        assertThat(snapshot.get("status [GH-90000]")).isEqualTo("UP [GH-90000]");
        assertThat(snapshot.get("databaseProduct [GH-90000]")).isEqualTo("PostgreSQL [GH-90000]");
        assertThat(snapshot.get("databaseVersion [GH-90000]")).isEqualTo("16.2 [GH-90000]");
        assertThat(snapshot.get("driver [GH-90000]")).isEqualTo("PostgreSQL JDBC Driver [GH-90000]");
        assertThat(snapshot.get("validationTimeoutSeconds [GH-90000]")).isEqualTo(5);
    }

    @Test
    void reportsDownWhenValidationFails() throws Exception { // GH-90000
        DataSource dataSource = mock(DataSource.class); // GH-90000
        Connection connection = mock(Connection.class); // GH-90000

        when(dataSource.getConnection()).thenReturn(connection); // GH-90000
        when(connection.isValid(3)).thenReturn(false); // GH-90000

        Map<String, Object> snapshot = new JdbcDatabaseHealthProbe(dataSource, 3).get(); // GH-90000

        assertThat(snapshot.get("status [GH-90000]")).isEqualTo("DOWN [GH-90000]");
        assertThat(snapshot.get("message [GH-90000]")).isEqualTo("Connection validation failed [GH-90000]");
        assertThat(snapshot.get("validationTimeoutSeconds [GH-90000]")).isEqualTo(3);
    }

    @Test
    void reportsDownWhenConnectionThrows() throws Exception { // GH-90000
        DataSource dataSource = mock(DataSource.class); // GH-90000
        when(dataSource.getConnection()).thenThrow(new SQLException("database unavailable [GH-90000]"));

        Map<String, Object> snapshot = new JdbcDatabaseHealthProbe(dataSource, 4).get(); // GH-90000

        assertThat(snapshot.get("status [GH-90000]")).isEqualTo("DOWN [GH-90000]");
        assertThat((String) snapshot.get("message [GH-90000]")).contains("SQLException [GH-90000]").contains("database unavailable [GH-90000]");
        assertThat(snapshot.get("validationTimeoutSeconds [GH-90000]")).isEqualTo(4);
    }
}
