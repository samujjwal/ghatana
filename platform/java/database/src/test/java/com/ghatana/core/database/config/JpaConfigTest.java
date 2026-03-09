package com.ghatana.core.database.config;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
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
@ExtendWith(MockitoExtension.class)
class JpaConfigTest {

    @Test
    void testBuilderWithRequiredFields() {
        JpaConfig config = JpaConfig.builder()
                .jdbcUrl("jdbc:postgresql://localhost:5432/testdb")
                .username("testuser")
                .password("testpass")
                .entityPackages("com.example.entity")
                .build();

        assertThat(config.getJdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5432/testdb");
        assertThat(config.getUsername()).isEqualTo("testuser");
        assertThat(config.getEntityPackages()).containsExactly("com.example.entity");
        assertThat(config.getPoolSize()).isEqualTo(20); // Default
        assertThat(config.isShowSql()).isFalse(); // Default
    }

    @Test
    void testBuilderWithAllFields() {
        JpaConfig config = JpaConfig.builder()
                .jdbcUrl("jdbc:postgresql://localhost:5432/testdb")
                .username("testuser")
                .password("testpass")
                .entityPackages("com.example.entity", "com.example.model")
                .poolSize(10)
                .connectionTimeout(Duration.ofSeconds(15))
                .idleTimeout(Duration.ofMinutes(5))
                .maxLifetime(Duration.ofMinutes(15))
                .showSql(true)
                .formatSql(true)
                .ddlAuto("create-drop")
                .dialect("org.hibernate.dialect.H2Dialect")
                .enableCache(false)
                .batchSize(50)
                .build();

        assertThat(config.getJdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5432/testdb");
        assertThat(config.getUsername()).isEqualTo("testuser");
        assertThat(config.getEntityPackages()).containsExactly("com.example.entity", "com.example.model");
        assertThat(config.getPoolSize()).isEqualTo(10);
        assertThat(config.getConnectionTimeout()).isEqualTo(Duration.ofSeconds(15));
        assertThat(config.getIdleTimeout()).isEqualTo(Duration.ofMinutes(5));
        assertThat(config.getMaxLifetime()).isEqualTo(Duration.ofMinutes(15));
        assertThat(config.isShowSql()).isTrue();
        assertThat(config.isFormatSql()).isTrue();
        assertThat(config.getDdlAuto()).isEqualTo("create-drop");
        assertThat(config.getDialect()).isEqualTo("org.hibernate.dialect.H2Dialect");
        assertThat(config.isEnableCache()).isFalse();
        assertThat(config.getBatchSize()).isEqualTo(50);
    }

    @Test
    void testBuilderValidation() {
        // Test null JDBC URL
        assertThatThrownBy(()
                -> JpaConfig.builder()
                        .jdbcUrl(null)
                        .username("user")
                        .password("pass")
                        .entityPackages("com.example")
                        .build()
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JDBC URL cannot be blank");

        // Test blank JDBC URL
        assertThatThrownBy(()
                -> JpaConfig.builder()
                        .jdbcUrl("")
                        .username("user")
                        .password("pass")
                        .entityPackages("com.example")
                        .build()
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JDBC URL cannot be blank");

        // Test null username
        assertThatThrownBy(()
                -> JpaConfig.builder()
                        .jdbcUrl("jdbc:postgresql://localhost:5432/testdb")
                        .username(null)
                        .password("pass")
                        .entityPackages("com.example")
                        .build()
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username cannot be blank");

        // Test null password
        assertThatThrownBy(()
                -> JpaConfig.builder()
                        .jdbcUrl("jdbc:postgresql://localhost:5432/testdb")
                        .username("user")
                        .password(null)
                        .entityPackages("com.example")
                        .build()
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Password cannot be null");

        // Test empty entity packages
        assertThatThrownBy(()
                -> JpaConfig.builder()
                        .jdbcUrl("jdbc:postgresql://localhost:5432/testdb")
                        .username("user")
                        .password("pass")
                        .entityPackages()
                        .build()
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Entity packages or entity class names must be provided");

        // Test invalid pool size
        assertThatThrownBy(()
                -> JpaConfig.builder()
                        .jdbcUrl("jdbc:postgresql://localhost:5432/testdb")
                        .username("user")
                        .password("pass")
                        .entityPackages("com.example")
                        .poolSize(0)
                        .build()
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Pool size must be positive");

        // Test invalid batch size
        assertThatThrownBy(()
                -> JpaConfig.builder()
                        .jdbcUrl("jdbc:postgresql://localhost:5432/testdb")
                        .username("user")
                        .password("pass")
                        .entityPackages("com.example")
                        .batchSize(-1)
                        .build()
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Batch size must be positive");
    }

    @Test
    void testCreateDataSource() {
        JpaConfig config = JpaConfig.builder()
                .jdbcUrl("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1")
                .username("sa")
                .password("")
                .entityPackages("com.example.entity")
                .poolSize(5)
                .build();

        DataSource dataSource = config.createDataSource();

        assertThat(dataSource).isInstanceOf(HikariDataSource.class);

        HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
        assertThat(hikariDataSource.getJdbcUrl()).isEqualTo("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
        assertThat(hikariDataSource.getUsername()).isEqualTo("sa");
        assertThat(hikariDataSource.getMaximumPoolSize()).isEqualTo(5);

        // Clean up
        hikariDataSource.close();
    }

    @Test
    void testCreateJpaProperties() {
        JpaConfig config = JpaConfig.builder()
                .jdbcUrl("jdbc:h2:mem:testdb")
                .username("sa")
                .password("")
                .entityPackages("com.example.entity")
                .showSql(true)
                .formatSql(true)
                .ddlAuto("create-drop")
                .dialect("org.hibernate.dialect.H2Dialect")
                .enableCache(false)
                .batchSize(50)
                .build();

        Properties properties = config.createJpaProperties();

        assertThat(properties.getProperty("hibernate.hbm2ddl.auto")).isEqualTo("create-drop");
        assertThat(properties.getProperty("hibernate.dialect")).isEqualTo("org.hibernate.dialect.H2Dialect");
        assertThat(properties.getProperty("hibernate.show_sql")).isEqualTo("true");
        assertThat(properties.getProperty("hibernate.format_sql")).isEqualTo("true");
        assertThat(properties.getProperty("hibernate.jdbc.batch_size")).isEqualTo("50");
        assertThat(properties.getProperty("hibernate.cache.use_second_level_cache")).isEqualTo("false");
        assertThat(properties.getProperty("hibernate.cache.use_query_cache")).isEqualTo("false");
    }

    @Test
    void testCreateJpaPropertiesWithCacheEnabled() {
        JpaConfig config = JpaConfig.builder()
                .jdbcUrl("jdbc:h2:mem:testdb")
                .username("sa")
                .password("")
                .entityPackages("com.example.entity")
                .enableCache(true)
                .build();

        Properties properties = config.createJpaProperties();

        assertThat(properties.getProperty("hibernate.cache.use_second_level_cache")).isEqualTo("true");
        assertThat(properties.getProperty("hibernate.cache.use_query_cache")).isEqualTo("true");
        assertThat(properties.getProperty("hibernate.cache.region.factory_class"))
                .isEqualTo("org.hibernate.cache.jcache.JCacheRegionFactory");
    }

    @Test
    void testShutdownDataSource() {
        JpaConfig config = JpaConfig.builder()
                .jdbcUrl("jdbc:h2:mem:testdb")
                .username("sa")
                .password("")
                .entityPackages("com.example.entity")
                .build();

        HikariDataSource dataSource = (HikariDataSource) config.createDataSource();
        assertThat(dataSource.isClosed()).isFalse();

        JpaConfig.shutdownDataSource(dataSource);
        assertThat(dataSource.isClosed()).isTrue();
    }

    @Test
    void testShutdownDataSourceWithNonHikari() {
        // Should not throw exception with non-HikariDataSource
        DataSource mockDataSource = new MockDataSource();

        assertThatCode(() -> JpaConfig.shutdownDataSource(mockDataSource))
                .doesNotThrowAnyException();
    }

    @Test
    void testShutdownDataSourceWithNull() {
        assertThatCode(() -> JpaConfig.shutdownDataSource(null))
                .doesNotThrowAnyException();
    }

    // Mock DataSource for testing
    private static class MockDataSource implements DataSource {

        @Override
        public java.sql.Connection getConnection() {
            return null;
        }

        @Override
        public java.sql.Connection getConnection(String username, String password) {
            return null;
        }

        @Override
        public java.io.PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(java.io.PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public java.util.logging.Logger getParentLogger() {
            return null;
        }

        @Override
        public <T> T unwrap(Class<T> iface) {
            return null;
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }
}
