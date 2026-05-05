/**
 * P1-045: Database state assertion utilities for integration tests.
 *
 * <p>Provides helpers to verify database state after operations,
 * ensuring data persistence and integrity beyond API responses.</p>
 *
 * @doc.type class
 * @doc.purpose Database state assertion utilities for integration tests (P1-045)
 * @doc.layer test
 */
package com.ghatana.digitalmarketing.integration;

import org.assertj.core.api.Assertions;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DatabaseStateAssertions {

    private final DataSource dataSource;

    public DatabaseStateAssertions(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public DatabaseStateAssertion assertTable(String tableName) {
        return new DatabaseStateAssertion(dataSource, tableName);
    }

    public static class DatabaseStateAssertion {
        private final DataSource dataSource;
        private final String tableName;
        private String whereClause;
        private Map<String, Object> expectedValues;

        private DatabaseStateAssertion(DataSource dataSource, String tableName) {
            this.dataSource = dataSource;
            this.tableName = tableName;
            this.expectedValues = new HashMap<>();
        }

        public DatabaseStateAssertion where(String whereClause) {
            this.whereClause = whereClause;
            return this;
        }

        public DatabaseStateAssertion hasColumnValue(String columnName, Object expectedValue) {
            this.expectedValues.put(columnName, expectedValue);
            return this;
        }

        public void verify() {
            try (Connection conn = dataSource.getConnection()) {
                String sql = "SELECT * FROM " + tableName;
                if (whereClause != null && !whereClause.isEmpty()) {
                    sql += " WHERE " + whereClause;
                }

                try (PreparedStatement stmt = conn.prepareStatement(sql);
                     ResultSet rs = stmt.executeQuery()) {

                    if (!rs.next()) {
                        Assertions.fail("No rows found in table " + tableName + " with condition: " + whereClause);
                    }

                    for (Map.Entry<String, Object> entry : expectedValues.entrySet()) {
                        Object actualValue = rs.getObject(entry.getKey());
                        Object expectedValue = entry.getValue();
                        Assertions.assertThat(actualValue)
                            .as("Column %s in table %s", entry.getKey(), tableName)
                            .isEqualTo(expectedValue);
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to verify database state", e);
            }
        }

        public int countRows() {
            try (Connection conn = dataSource.getConnection()) {
                String sql = "SELECT COUNT(*) FROM " + tableName;
                if (whereClause != null && !whereClause.isEmpty()) {
                    sql += " WHERE " + whereClause;
                }

                try (PreparedStatement stmt = conn.prepareStatement(sql);
                     ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                    return 0;
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to count rows", e);
            }
        }

        public List<Map<String, Object>> fetchAll() {
            List<Map<String, Object>> results = new ArrayList<>();
            try (Connection conn = dataSource.getConnection()) {
                String sql = "SELECT * FROM " + tableName;
                if (whereClause != null && !whereClause.isEmpty()) {
                    sql += " WHERE " + whereClause;
                }

                try (PreparedStatement stmt = conn.prepareStatement(sql);
                     ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new HashMap<>();
                        int columnCount = rs.getMetaData().getColumnCount();
                        for (int i = 1; i <= columnCount; i++) {
                            row.put(rs.getMetaData().getColumnName(i), rs.getObject(i));
                        }
                        results.add(row);
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to fetch rows", e);
            }
            return results;
        }
    }

    public static class TableExistsAssertion {
        private final DataSource dataSource;

        private TableExistsAssertion(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        public void exists(String tableName) {
            try (Connection conn = dataSource.getConnection()) {
                try (ResultSet rs = conn.getMetaData().getTables(null, null, tableName, null)) {
                    Assertions.assertThat(rs.next())
                        .as("Table %s should exist", tableName)
                        .isTrue();
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to check table existence", e);
            }
        }

        public void doesNotExist(String tableName) {
            try (Connection conn = dataSource.getConnection()) {
                try (ResultSet rs = conn.getMetaData().getTables(null, null, tableName, null)) {
                    Assertions.assertThat(rs.next())
                        .as("Table %s should not exist", tableName)
                        .isFalse();
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to check table existence", e);
            }
        }
    }

    public TableExistsAssertion assertTables() {
        return new TableExistsAssertion(dataSource);
    }

    public void assertRowCount(String tableName, int expectedCount) {
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT COUNT(*) FROM " + tableName;
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int actualCount = rs.getInt(1);
                    Assertions.assertThat(actualCount)
                        .as("Row count in table %s", tableName)
                        .isEqualTo(expectedCount);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to assert row count", e);
        }
    }

    public void assertColumnNull(String tableName, String columnName, String whereClause) {
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT " + columnName + " FROM " + tableName + " WHERE " + whereClause;
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Object value = rs.getObject(columnName);
                    Assertions.assertThat(value)
                        .as("Column %s should be null", columnName)
                        .isNull();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to assert column null", e);
        }
    }

    public void assertColumnNotNull(String tableName, String columnName, String whereClause) {
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT " + columnName + " FROM " + tableName + " WHERE " + whereClause;
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Object value = rs.getObject(columnName);
                    Assertions.assertThat(value)
                        .as("Column %s should not be null", columnName)
                        .isNotNull();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to assert column not null", e);
        }
    }
}
