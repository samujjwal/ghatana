package com.ghatana.core.database.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.Properties;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for JpaConfig.
 */
@ExtendWith(MockitoExtension.class) // GH-90000
class JpaConfigTest {

    @Test
    void testBuilderWithRequiredFields() { // GH-90000
        JpaConfig config = JpaConfig.builder() // GH-90000
                .jdbcUrl("jdbc:postgresql://localhost:5432/testdb")
                .username("testuser")
                .password("testpass")
                .entityPackages("com.example.entity")
                .build(); // GH-90000

        assertThat(config.getJdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5432/testdb");
        assertThat(config.getUsername()).isEqualTo("testuser");
        assertThat(config.getEntityPackages()).containsExactly("com.example.entity");
        assertThat(config.getPoolSize()).isEqualTo(20); // Default // GH-90000
        assertThat(config.isShowSql()).isFalse(); // Default // GH-90000
    }

    @Test
    void testBuilderWithAllFields() { // GH-90000
        JpaConfig config = JpaConfig.builder() // GH-90000
                .jdbcUrl("jdbc:postgresql://localhost:5432/testdb")
                .username("testuser")
                .password("testpass")
                .entityPackages("com.example.entity", "com.example.model") // GH-90000
                .poolSize(10) // GH-90000
                .connectionTimeout(Duration.ofSeconds(15)) // GH-90000
                .idleTimeout(Duration.ofMinutes(5)) // GH-90000
                .maxLifetime(Duration.ofMinutes(15)) // GH-90000
                .showSql(true) // GH-90000
                .formatSql(true) // GH-90000
                .ddlAuto("create-drop")
                .dialect("org.hibernate.dialect.H2Dialect")
                .enableCache(false) // GH-90000
                .batchSize(50) // GH-90000
                .build(); // GH-90000

        assertThat(config.getJdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5432/testdb");
        assertThat(config.getUsername()).isEqualTo("testuser");
        assertThat(config.getEntityPackages()).containsExactly("com.example.entity", "com.example.model"); // GH-90000
        assertThat(config.getPoolSize()).isEqualTo(10); // GH-90000
        assertThat(config.getConnectionTimeout()).isEqualTo(Duration.ofSeconds(15)); // GH-90000
        assertThat(config.getIdleTimeout()).isEqualTo(Duration.ofMinutes(5)); // GH-90000
        assertThat(config.getMaxLifetime()).isEqualTo(Duration.ofMinutes(15)); // GH-90000
        assertThat(config.isShowSql()).isTrue(); // GH-90000
        assertThat(config.isFormatSql()).isTrue(); // GH-90000
        assertThat(config.getDdlAuto()).isEqualTo("create-drop");
        assertThat(config.getDialect()).isEqualTo("org.hibernate.dialect.H2Dialect");
        assertThat(config.isEnableCache()).isFalse(); // GH-90000
        assertThat(config.getBatchSize()).isEqualTo(50); // GH-90000
    }

    @Test
    void testBuilderValidation() { // GH-90000
        // Test null JDBC URL
        assertThatThrownBy(() // GH-90000
                -> JpaConfig.builder() // GH-90000
                        .jdbcUrl(null) // GH-90000
                        .username("user")
                        .password("pass")
                        .entityPackages("com.example")
                        .build() // GH-90000
        ).isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("JDBC URL cannot be blank");

        // Test blank JDBC URL
        assertThatThrownBy(() // GH-90000
                -> JpaConfig.builder() // GH-90000
                        .jdbcUrl("")
                        .username("user")
                        .password("pass")
                        .entityPackages("com.example")
                        .build() // GH-90000
        ).isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("JDBC URL cannot be blank");

        // Test null username
        assertThatThrownBy(() // GH-90000
                -> JpaConfig.builder() // GH-90000
                        .jdbcUrl("jdbc:postgresql://localhost:5432/testdb")
                        .username(null) // GH-90000
                        .password("pass")
                        .entityPackages("com.example")
                        .build() // GH-90000
        ).isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("Username cannot be blank");

        // Test null password
        assertThatThrownBy(() // GH-90000
                -> JpaConfig.builder() // GH-90000
                        .jdbcUrl("jdbc:postgresql://localhost:5432/testdb")
                        .username("user")
                        .password(null) // GH-90000
                        .entityPackages("com.example")
                        .build() // GH-90000
        ).isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("Password cannot be null");

        // Test empty entity packages
        assertThatThrownBy(() // GH-90000
                -> JpaConfig.builder() // GH-90000
                        .jdbcUrl("jdbc:postgresql://localhost:5432/testdb")
                        .username("user")
                        .password("pass")
                        .entityPackages() // GH-90000
                        .build() // GH-90000
        ).isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("Entity packages or entity class names must be provided");

        // Test invalid pool size
        assertThatThrownBy(() // GH-90000
                -> JpaConfig.builder() // GH-90000
                        .jdbcUrl("jdbc:postgresql://localhost:5432/testdb")
                        .username("user")
                        .password("pass")
                        .entityPackages("com.example")
                        .poolSize(0) // GH-90000
                        .build() // GH-90000
        ).isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("Pool size must be positive");

        // Test invalid batch size
        assertThatThrownBy(() // GH-90000
                -> JpaConfig.builder() // GH-90000
                        .jdbcUrl("jdbc:postgresql://localhost:5432/testdb")
                        .username("user")
                        .password("pass")
                        .entityPackages("com.example")
                        .batchSize(-1) // GH-90000
                        .build() // GH-90000
        ).isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("Batch size must be positive");
    }

    @Test
    void testCreateDataSource() { // GH-90000
        JpaConfig config = JpaConfig.builder() // GH-90000
                .jdbcUrl("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1")
                .username("sa")
                .password("")
                .entityPackages("com.example.entity")
                .poolSize(5) // GH-90000
                .build(); // GH-90000

        DataSource dataSource = config.createDataSource(); // GH-90000

        assertThat(dataSource).isInstanceOf(HikariDataSource.class); // GH-90000

        HikariDataSource hikariDataSource = (HikariDataSource) dataSource; // GH-90000
        assertThat(hikariDataSource.getJdbcUrl()).isEqualTo("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
        assertThat(hikariDataSource.getUsername()).isEqualTo("sa");
        assertThat(hikariDataSource.getMaximumPoolSize()).isEqualTo(5); // GH-90000

        // Clean up
        hikariDataSource.close(); // GH-90000
    }

    @Test
    void testCreateJpaProperties() { // GH-90000
        JpaConfig config = JpaConfig.builder() // GH-90000
                .jdbcUrl("jdbc:h2:mem:testdb")
                .username("sa")
                .password("")
                .entityPackages("com.example.entity")
                .showSql(true) // GH-90000
                .formatSql(true) // GH-90000
                .ddlAuto("create-drop")
                .dialect("org.hibernate.dialect.H2Dialect")
                .enableCache(false) // GH-90000
                .batchSize(50) // GH-90000
                .build(); // GH-90000

        Properties properties = config.createJpaProperties(); // GH-90000

        assertThat(properties.getProperty("hibernate.hbm2ddl.auto")).isEqualTo("create-drop");
        assertThat(properties.getProperty("hibernate.dialect")).isEqualTo("org.hibernate.dialect.H2Dialect");
        assertThat(properties.getProperty("hibernate.show_sql")).isEqualTo("true");
        assertThat(properties.getProperty("hibernate.format_sql")).isEqualTo("true");
        assertThat(properties.getProperty("hibernate.jdbc.batch_size")).isEqualTo("50");
        assertThat(properties.getProperty("hibernate.cache.use_second_level_cache")).isEqualTo("false");
        assertThat(properties.getProperty("hibernate.cache.use_query_cache")).isEqualTo("false");
    }

    @Test
    void testCreateJpaPropertiesWithCacheEnabled() { // GH-90000
        JpaConfig config = JpaConfig.builder() // GH-90000
                .jdbcUrl("jdbc:h2:mem:testdb")
                .username("sa")
                .password("")
                .entityPackages("com.example.entity")
                .enableCache(true) // GH-90000
                .build(); // GH-90000

        Properties properties = config.createJpaProperties(); // GH-90000

        assertThat(properties.getProperty("hibernate.cache.use_second_level_cache")).isEqualTo("true");
        assertThat(properties.getProperty("hibernate.cache.use_query_cache")).isEqualTo("true");
        assertThat(properties.getProperty("hibernate.cache.region.factory_class"))
                .isEqualTo("org.hibernate.cache.jcache.JCacheRegionFactory");
    }

    @Test
    void testShutdownDataSource() { // GH-90000
        JpaConfig config = JpaConfig.builder() // GH-90000
                .jdbcUrl("jdbc:h2:mem:testdb")
                .username("sa")
                .password("")
                .entityPackages("com.example.entity")
                .build(); // GH-90000

        HikariDataSource dataSource = (HikariDataSource) config.createDataSource(); // GH-90000
        assertThat(dataSource.isClosed()).isFalse(); // GH-90000

        JpaConfig.shutdownDataSource(dataSource); // GH-90000
        assertThat(dataSource.isClosed()).isTrue(); // GH-90000
    }

    @Test
    void testShutdownDataSourceWithNonHikari() { // GH-90000
        // Should not throw exception with non-HikariDataSource
        DataSource mockDataSource = new MockDataSource(); // GH-90000

        assertThatCode(() -> JpaConfig.shutdownDataSource(mockDataSource)) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
    }

    @Test
    void testShutdownDataSourceWithNull() { // GH-90000
        assertThatCode(() -> JpaConfig.shutdownDataSource(null)) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
    }

    // Mock DataSource for testing
    private static class MockDataSource implements DataSource {

        @Override
        public java.sql.Connection getConnection() { // GH-90000
            return null;
        }

        @Override
        public java.sql.Connection getConnection(String username, String password) { // GH-90000
            return null;
        }

        @Override
        public java.io.PrintWriter getLogWriter() { // GH-90000
            return null;
        }

        @Override
        public void setLogWriter(java.io.PrintWriter out) { // GH-90000
        }

        @Override
        public void setLoginTimeout(int seconds) { // GH-90000
        }

        @Override
        public int getLoginTimeout() { // GH-90000
            return 0;
        }

        @Override
        public java.util.logging.Logger getParentLogger() { // GH-90000
            return null;
        }

        @Override
        public <T> T unwrap(Class<T> iface) { // GH-90000
            return null;
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) { // GH-90000
            return false;
        }
    }
}
