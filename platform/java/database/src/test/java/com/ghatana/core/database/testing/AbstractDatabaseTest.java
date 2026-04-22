package com.ghatana.core.database.testing;

import com.ghatana.core.database.config.JpaConfig;
import com.ghatana.core.database.transaction.TransactionCallback;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;

/**
 * Base class for database integration tests using TestContainers.
 */
@Testcontainers
public abstract class AbstractDatabaseTest {

    @Container
    protected static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine [GH-90000]")
        .withDatabaseName("testdb [GH-90000]")
        .withUsername("test [GH-90000]")
        .withPassword("test [GH-90000]");

    protected DataSource dataSource;
    protected JpaConfig jpaConfig;
    protected EntityManagerFactory entityManagerFactory;

    @BeforeEach
    void setUp() { // GH-90000
        // Initialize JpaConfig with test database settings
        this.jpaConfig = JpaConfig.builder() // GH-90000
            .jdbcUrl(postgres.getJdbcUrl()) // GH-90000
            .username(postgres.getUsername()) // GH-90000
            .password(postgres.getPassword()) // GH-90000
            .entityPackages("com.ghatana [GH-90000]")
            .ddlAuto("create-drop [GH-90000]")  // Create and drop schema for tests
            .showSql(true)           // Show SQL for debugging // GH-90000
            .formatSql(true)         // Format SQL for better readability // GH-90000
            .build(); // GH-90000

        // Create and initialize the data source
        this.dataSource = jpaConfig.createDataSource(); // GH-90000

        // Create the EntityManagerFactory
        this.entityManagerFactory = jpaConfig.createEntityManagerFactory(dataSource); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (entityManagerFactory != null && entityManagerFactory.isOpen()) { // GH-90000
            entityManagerFactory.close(); // GH-90000
        }

        if (dataSource instanceof AutoCloseable) { // GH-90000
            try {
                ((AutoCloseable) dataSource).close(); // GH-90000
            } catch (Exception e) { // GH-90000
                // Ignore
            }
        }
    }

    /**
     * Helper method to execute a database transaction.
     *
     * @param callback The callback to execute within a transaction
     * @param <T> The return type of the callback
     * @return The result of the callback
     */
    protected <T> T doInTransaction(TransactionCallback<EntityManager, T> callback) { // GH-90000
        EntityManager em = entityManagerFactory.createEntityManager(); // GH-90000
        try {
            em.getTransaction().begin(); // GH-90000
            T result = callback.execute(em); // GH-90000
            em.getTransaction().commit(); // GH-90000
            return result;
        } catch (Exception e) { // GH-90000
            if (em.getTransaction().isActive()) { // GH-90000
                em.getTransaction().rollback(); // GH-90000
            }
            throw new RuntimeException("Transaction failed", e); // GH-90000
        } finally {
            if (em.isOpen()) { // GH-90000
                em.close(); // GH-90000
            }
        }
    }

}
