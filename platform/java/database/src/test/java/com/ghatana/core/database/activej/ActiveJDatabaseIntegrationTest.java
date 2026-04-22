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
@DisplayName("ActiveJ Database Integration Tests [GH-90000]")
@Tag("integration [GH-90000]")
@Execution(ExecutionMode.SAME_THREAD) // Force sequential execution to avoid schema conflicts // GH-90000
public class ActiveJDatabaseIntegrationTest extends EventloopTestBase {

    private static PostgreSQLContainer<?> POSTGRES;

    private static boolean dockerAvailable;

    @BeforeAll
    static void setupDatabase() { // GH-90000
        boolean available;
        try {
            available = DockerClientFactory.instance().isDockerAvailable(); // GH-90000
        } catch (Throwable ex) { // GH-90000
            available = false;
        }
        dockerAvailable = available;
        Assumptions.assumeTrue(dockerAvailable, // GH-90000
                () -> "Skipping ActiveJDatabaseIntegrationTest because Docker is unavailable"); // GH-90000

        POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine [GH-90000]")
                .withDatabaseName("testdb [GH-90000]")
                .withUsername("test [GH-90000]")
                .withPassword("test [GH-90000]")
                .waitingFor(Wait.forListeningPort()) // GH-90000
                .withStartupTimeout(Duration.ofMinutes(2)); // GH-90000

        POSTGRES.start(); // GH-90000
    }

    private EntityManagerFactory entityManagerFactory;
    private DataSource dataSource;

    /**
     * Test entity for persistence operations.
     */
    @Entity
    @Table(name = "test_entity") // GH-90000
    public static class TestEntity {

        @Id
        private Long id;
        private String name;

        // Getters and setters
        public Long getId() { // GH-90000
            return id;
        }

        public void setId(Long id) { // GH-90000
            this.id = id;
        }

        public String getName() { // GH-90000
            return name;
        }

        public void setName(String name) { // GH-90000
            this.name = name;
        }
    }

    @AfterAll
    static void teardownDatabase() { // GH-90000
        if (!dockerAvailable) { // GH-90000
            return;
        }
        if (POSTGRES != null) { // GH-90000
            POSTGRES.stop(); // GH-90000
        }
    }

    @BeforeEach
    void setUp() { // GH-90000
        // GIVEN: Test database and entity manager factory
        dataSource = createTestDataSource(); // GH-90000
        createTestSchema(dataSource); // GH-90000
        entityManagerFactory = createEntityManagerFactory(); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        // Cleanup resources
        if (entityManagerFactory != null && entityManagerFactory.isOpen()) { // GH-90000
            entityManagerFactory.close(); // GH-90000
        }
        if (dataSource instanceof AutoCloseable) { // GH-90000
            try {
                ((AutoCloseable) dataSource).close(); // GH-90000
            } catch (Exception e) { // GH-90000
                // Ignore close exceptions in tests
            }
        }
    }

    /**
     * Creates test database schema.
     *
     * @param dataSource data source to use
     */
    private void createTestSchema(DataSource dataSource) { // GH-90000
        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) { // GH-90000
            // Drop and recreate to avoid parallel test conflicts
            stmt.execute("DROP TABLE IF EXISTS test_entity CASCADE [GH-90000]");
            stmt.execute(""" // GH-90000
                CREATE TABLE test_entity ( // GH-90000
                    id BIGINT PRIMARY KEY,
                    name VARCHAR(255) // GH-90000
                )""");
        } catch (Exception e) { // GH-90000
            throw new RuntimeException("Failed to create test schema", e); // GH-90000
        }
    }

    /**
     * Creates test entity manager factory.
     *
     * @return configured entity manager factory
     */
    private EntityManagerFactory createEntityManagerFactory() { // GH-90000
        Map<String, Object> props = new HashMap<>(); // GH-90000
        props.put("jakarta.persistence.jdbc.url", POSTGRES.getJdbcUrl()); // GH-90000
        props.put("jakarta.persistence.jdbc.user", POSTGRES.getUsername()); // GH-90000
        props.put("jakarta.persistence.jdbc.password", POSTGRES.getPassword()); // GH-90000
        props.put("jakarta.persistence.jdbc.driver", "org.postgresql.Driver"); // GH-90000
        props.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect"); // GH-90000
        props.put("hibernate.hbm2ddl.auto", "validate"); // GH-90000
        props.put("hibernate.show_sql", "false"); // GH-90000

        return Persistence.createEntityManagerFactory("test-unit", props); // GH-90000
    }

    /**
     * Creates test data source.
     *
     * @return configured data source
     */
    private DataSource createTestDataSource() { // GH-90000
        var config = new com.zaxxer.hikari.HikariConfig(); // GH-90000
        config.setJdbcUrl(POSTGRES.getJdbcUrl()); // GH-90000
        config.setUsername(POSTGRES.getUsername()); // GH-90000
        config.setPassword(POSTGRES.getPassword()); // GH-90000
        config.setMaximumPoolSize(5); // GH-90000
        return new com.zaxxer.hikari.HikariDataSource(config); // GH-90000
    }

    /**
     * Verifies entity can be saved and retrieved.
     *
     * GIVEN: A test entity WHEN: Entity is persisted THEN: Entity can be
     * retrieved by ID
     */
    @Test
    @DisplayName("Should save and retrieve entity [GH-90000]")
    void shouldSaveAndRetrieveEntity() { // GH-90000
        // GIVEN: Test entity
        var entity = new TestEntity(); // GH-90000
        entity.setId(1L); // GH-90000
        entity.setName("Test [GH-90000]");

        // WHEN: Entity is persisted in transaction
        EntityManager em = entityManagerFactory.createEntityManager(); // GH-90000
        try {
            em.getTransaction().begin(); // GH-90000
            em.persist(entity); // GH-90000
            em.getTransaction().commit(); // GH-90000
        } finally {
            em.close(); // GH-90000
        }

        // THEN: Entity can be retrieved
        EntityManager em2 = entityManagerFactory.createEntityManager(); // GH-90000
        try {
            var foundEntity = em2.find(TestEntity.class, 1L); // GH-90000
            assertThat(foundEntity) // GH-90000
                    .as("Retrieved entity should not be null [GH-90000]")
                    .isNotNull(); // GH-90000
            assertThat(foundEntity.getName()) // GH-90000
                    .as("Entity name should match [GH-90000]")
                    .isEqualTo("Test [GH-90000]");
        } finally {
            em2.close(); // GH-90000
        }
    }

    /**
     * Verifies transaction rollback functionality.
     *
     * GIVEN: A test entity WHEN: Transaction is rolled back THEN: Entity is not
     * persisted
     */
    @Test
    @DisplayName("Should handle transaction rollback [GH-90000]")
    void shouldHandleTransactionRollback() { // GH-90000
        // GIVEN: Test entity
        var entity = new TestEntity(); // GH-90000
        entity.setId(2L); // GH-90000
        entity.setName("Rollback Test [GH-90000]");

        // WHEN: Transaction is rolled back
        EntityManager em = entityManagerFactory.createEntityManager(); // GH-90000
        try {
            em.getTransaction().begin(); // GH-90000
            em.persist(entity); // GH-90000
            em.getTransaction().rollback(); // GH-90000
        } finally {
            em.close(); // GH-90000
        }

        // THEN: Entity should not exist
        EntityManager em2 = entityManagerFactory.createEntityManager(); // GH-90000
        try {
            var foundEntity = em2.find(TestEntity.class, 2L); // GH-90000
            assertThat(foundEntity) // GH-90000
                    .as("Rolled back entity should not exist [GH-90000]")
                    .isNull(); // GH-90000
        } finally {
            em2.close(); // GH-90000
        }
    }

    /**
     * Verifies query execution functionality.
     *
     * GIVEN: Multiple test entities WHEN: Query is executed THEN: Correct count
     * is returned
     */
    @Test
    @DisplayName("Should execute queries [GH-90000]")
    void shouldExecuteQueries() { // GH-90000
        // GIVEN: Multiple entities
        EntityManager em = entityManagerFactory.createEntityManager(); // GH-90000
        try {
            em.getTransaction().begin(); // GH-90000
            for (int i = 0; i < 3; i++) { // GH-90000
                var entity = new TestEntity(); // GH-90000
                entity.setId((long) (100 + i)); // GH-90000
                entity.setName("Query Test " + i); // GH-90000
                em.persist(entity); // GH-90000
            }
            em.getTransaction().commit(); // GH-90000
        } finally {
            em.close(); // GH-90000
        }

        // WHEN: Count query is executed
        EntityManager em2 = entityManagerFactory.createEntityManager(); // GH-90000
        try {
            Long count = em2.createQuery( // GH-90000
                    "SELECT COUNT(e) FROM ActiveJDatabaseIntegrationTest$TestEntity e", Long.class) // GH-90000
                    .getSingleResult(); // GH-90000

            // THEN: Count should be correct
            assertThat(count) // GH-90000
                    .as("Entity count should be at least 3 [GH-90000]")
                    .isGreaterThanOrEqualTo(3L); // GH-90000
        } finally {
            em2.close(); // GH-90000
        }
    }
}
