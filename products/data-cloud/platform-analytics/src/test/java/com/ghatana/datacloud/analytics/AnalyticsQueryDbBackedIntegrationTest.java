package com.ghatana.datacloud.analytics;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose DB-backed integration checks for query filtering, sorting, joins, grouping and pagination
 * @doc.layer product
 * @doc.pattern Integration Test
 */
@DisplayName("Analytics Query DB-Backed Integration Tests")
class AnalyticsQueryDbBackedIntegrationTest extends EventloopTestBase {

    private String jdbcUrl;
    private QueryValidator validator;

    @BeforeEach
    void setUp() throws Exception {
        jdbcUrl = "jdbc:h2:mem:analytics_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        validator = new QueryValidator();

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE categories(id INT PRIMARY KEY, name VARCHAR(100))");
            st.execute("CREATE TABLE products(id INT PRIMARY KEY, name VARCHAR(100), category_id INT, price DECIMAL(10,2), active BOOLEAN)");

            st.execute("INSERT INTO categories(id, name) VALUES (1, 'Hardware'), (2, 'Software')");
            st.execute("INSERT INTO products(id, name, category_id, price, active) VALUES " +
                "(1, 'Widget', 1, 10.00, true)," +
                "(2, 'Gadget', 1, 50.00, true)," +
                "(3, 'App', 2, 120.00, false)," +
                "(4, 'Suite', 2, 200.00, true)," +
                "(5, 'Tool', 1, 75.00, true)");
        }
    }

    @Test
    @DisplayName("filter + sort + pagination executes correctly on real DB")
    void filterSortPaginationOnRealDb() throws Exception {
        String query = "SELECT id, name, price FROM products WHERE active = true ORDER BY price DESC LIMIT 2 OFFSET 1";
        assertThat(validator.validate("tenant-1", query, Map.of()).valid()).isTrue();

        List<String> names = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(query)) {
            while (rs.next()) {
                names.add(rs.getString("name"));
            }
        }

        assertThat(names).containsExactly("Tool", "Gadget");
    }

    @Test
    @DisplayName("grouping aggregation returns expected counts")
    void groupingAggregationReturnsExpectedCounts() throws Exception {
        String query = "SELECT category_id, COUNT(*) AS cnt FROM products GROUP BY category_id ORDER BY category_id";
        assertThat(validator.validate("tenant-1", query, Map.of()).valid()).isTrue();

        List<Integer> counts = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(query)) {
            while (rs.next()) {
                counts.add(rs.getInt("cnt"));
            }
        }

        assertThat(counts).containsExactly(3, 2);
    }

    @Test
    @DisplayName("join semantics return expected rows")
    void joinSemanticsReturnExpectedRows() throws Exception {
        String query = "SELECT p.name AS product, c.name AS category FROM products p JOIN categories c ON p.category_id = c.id WHERE p.price >= 75 ORDER BY p.id";
        assertThat(validator.validate("tenant-1", query, Map.of()).valid()).isTrue();

        List<String> rows = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(query)) {
            while (rs.next()) {
                rows.add(rs.getString("product") + ":" + rs.getString("category"));
            }
        }

        assertThat(rows).containsExactly("App:Software", "Suite:Software", "Tool:Hardware");
    }
}
