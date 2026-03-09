package com.ghatana.core.database.activej;

import com.ghatana.core.database.TransactionManager;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.DockerClientFactory;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Id;
import jakarta.persistence.Persistence;
import jakarta.persistence.Table;
import javax.sql.DataSource;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for ActiveJ database functionality.
 *
 * Tests validate: - Database connection and configuration - Transaction
 * management with ActiveJ - Entity persistence operations - Connection pooling
 *
 * @see TransactionManager
 * @doc.type class
 * @doc.purpose ActiveJ database integration testing
 * @doc.layer core
 * @doc.pattern Integration Test
 */
@DisplayName("ActiveJ Database Integration Tests")
@Tag("integration")
@Execution(ExecutionMode.SAME_THREAD) // Force sequential execution to avoid schema conflicts
public class ActiveJDatabaseIntegrationTest extends EventloopTestBase {

    private static PostgreSQLContainer<?> POSTGRES;

    private static boolean dockerAvailable;

    @BeforeAll
    static void setupDatabase() {
        boolean available;
        try {
            available = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable ex) {
            available = false;
        }
        dockerAvailable = available;
        Assumptions.assumeTrue(dockerAvailable,
                () -> "Skipping ActiveJDatabaseIntegrationTest because Docker is unavailable");

        POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine")
                .withDatabaseName("testdb")
                .withUsername("test")
                .withPassword("test")
                .waitingFor(Wait.forListeningPort())
                .withStartupTimeout(Duration.ofMinutes(2));

        POSTGRES.start();
    }

    private EntityManagerFactory entityManagerFactory;
    private DataSource dataSource;

    /**
     * Test entity for persistence operations.
     */
    @Entity
    @Table(name = "test_entity")
    public static class TestEntity {

        @Id
        private Long id;
        private String name;

        // Getters and setters
        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @AfterAll
    static void teardownDatabase() {
        if (!dockerAvailable) {
            return;
        }
        if (POSTGRES != null) {
            POSTGRES.stop();
        }
    }

    @BeforeEach
    void setUp() {
        // GIVEN: Test database and entity manager factory
        dataSource = createTestDataSource();
        createTestSchema(dataSource);
        entityManagerFactory = createEntityManagerFactory();
    }

    @AfterEach
    void tearDown() {
        // Cleanup resources
        if (entityManagerFactory != null && entityManagerFactory.isOpen()) {
            entityManagerFactory.close();
        }
        if (dataSource instanceof AutoCloseable) {
            try {
                ((AutoCloseable) dataSource).close();
            } catch (Exception e) {
                // Ignore close exceptions in tests
            }
        }
    }

    /**
     * Creates test database schema.
     *
     * @param dataSource data source to use
     */
    private void createTestSchema(DataSource dataSource) {
        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            // Drop and recreate to avoid parallel test conflicts
            stmt.execute("DROP TABLE IF EXISTS test_entity CASCADE");
            stmt.execute("""
                CREATE TABLE test_entity (
                    id BIGINT PRIMARY KEY,
                    name VARCHAR(255)
                )""");
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test schema", e);
        }
    }

    /**
     * Creates test entity manager factory.
     *
     * @return configured entity manager factory
     */
    private EntityManagerFactory createEntityManagerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put("jakarta.persistence.jdbc.url", POSTGRES.getJdbcUrl());
        props.put("jakarta.persistence.jdbc.user", POSTGRES.getUsername());
        props.put("jakarta.persistence.jdbc.password", POSTGRES.getPassword());
        props.put("jakarta.persistence.jdbc.driver", "org.postgresql.Driver");
        props.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        props.put("hibernate.hbm2ddl.auto", "validate");
        props.put("hibernate.show_sql", "false");

        return Persistence.createEntityManagerFactory("test-unit", props);
    }

    /**
     * Creates test data source.
     *
     * @return configured data source
     */
    private DataSource createTestDataSource() {
        var config = new com.zaxxer.hikari.HikariConfig();
        config.setJdbcUrl(POSTGRES.getJdbcUrl());
        config.setUsername(POSTGRES.getUsername());
        config.setPassword(POSTGRES.getPassword());
        config.setMaximumPoolSize(5);
        return new com.zaxxer.hikari.HikariDataSource(config);
    }

    /**
     * Verifies entity can be saved and retrieved.
     *
     * GIVEN: A test entity WHEN: Entity is persisted THEN: Entity can be
     * retrieved by ID
     */
    @Test
    @DisplayName("Should save and retrieve entity")
    void shouldSaveAndRetrieveEntity() {
        // GIVEN: Test entity
        var entity = new TestEntity();
        entity.setId(1L);
        entity.setName("Test");

        // WHEN: Entity is persisted in transaction
        EntityManager em = entityManagerFactory.createEntityManager();
        try {
            em.getTransaction().begin();
            em.persist(entity);
            em.getTransaction().commit();
        } finally {
            em.close();
        }

        // THEN: Entity can be retrieved
        EntityManager em2 = entityManagerFactory.createEntityManager();
        try {
            var foundEntity = em2.find(TestEntity.class, 1L);
            assertThat(foundEntity)
                    .as("Retrieved entity should not be null")
                    .isNotNull();
            assertThat(foundEntity.getName())
                    .as("Entity name should match")
                    .isEqualTo("Test");
        } finally {
            em2.close();
        }
    }

    /**
     * Verifies transaction rollback functionality.
     *
     * GIVEN: A test entity WHEN: Transaction is rolled back THEN: Entity is not
     * persisted
     */
    @Test
    @DisplayName("Should handle transaction rollback")
    void shouldHandleTransactionRollback() {
        // GIVEN: Test entity
        var entity = new TestEntity();
        entity.setId(2L);
        entity.setName("Rollback Test");

        // WHEN: Transaction is rolled back
        EntityManager em = entityManagerFactory.createEntityManager();
        try {
            em.getTransaction().begin();
            em.persist(entity);
            em.getTransaction().rollback();
        } finally {
            em.close();
        }

        // THEN: Entity should not exist
        EntityManager em2 = entityManagerFactory.createEntityManager();
        try {
            var foundEntity = em2.find(TestEntity.class, 2L);
            assertThat(foundEntity)
                    .as("Rolled back entity should not exist")
                    .isNull();
        } finally {
            em2.close();
        }
    }

    /**
     * Verifies query execution functionality.
     *
     * GIVEN: Multiple test entities WHEN: Query is executed THEN: Correct count
     * is returned
     */
    @Test
    @DisplayName("Should execute queries")
    void shouldExecuteQueries() {
        // GIVEN: Multiple entities
        EntityManager em = entityManagerFactory.createEntityManager();
        try {
            em.getTransaction().begin();
            for (int i = 0; i < 3; i++) {
                var entity = new TestEntity();
                entity.setId((long) (100 + i));
                entity.setName("Query Test " + i);
                em.persist(entity);
            }
            em.getTransaction().commit();
        } finally {
            em.close();
        }

        // WHEN: Count query is executed
        EntityManager em2 = entityManagerFactory.createEntityManager();
        try {
            Long count = em2.createQuery(
                    "SELECT COUNT(e) FROM ActiveJDatabaseIntegrationTest$TestEntity e", Long.class)
                    .getSingleResult();

            // THEN: Count should be correct
            assertThat(count)
                    .as("Entity count should be at least 3")
                    .isGreaterThanOrEqualTo(3L);
        } finally {
            em2.close();
        }
    }
}
