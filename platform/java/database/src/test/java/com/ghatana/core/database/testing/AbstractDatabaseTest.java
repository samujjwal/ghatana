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
    protected static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");
    
    protected DataSource dataSource;
    protected JpaConfig jpaConfig;
    protected EntityManagerFactory entityManagerFactory;
    
    @BeforeEach
    void setUp() {
        // Initialize JpaConfig with test database settings
        this.jpaConfig = JpaConfig.builder()
            .jdbcUrl(postgres.getJdbcUrl())
            .username(postgres.getUsername())
            .password(postgres.getPassword())
            .entityPackages("com.ghatana")
            .ddlAuto("create-drop")  // Create and drop schema for tests
            .showSql(true)           // Show SQL for debugging
            .formatSql(true)         // Format SQL for better readability
            .build();
            
        // Create and initialize the data source
        this.dataSource = jpaConfig.createDataSource();
        
        // Create the EntityManagerFactory
        this.entityManagerFactory = jpaConfig.createEntityManagerFactory(dataSource);
    }
    
    @AfterEach
    void tearDown() {
        if (entityManagerFactory != null && entityManagerFactory.isOpen()) {
            entityManagerFactory.close();
        }
        
        if (dataSource instanceof AutoCloseable) {
            try {
                ((AutoCloseable) dataSource).close();
            } catch (Exception e) {
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
    protected <T> T doInTransaction(TransactionCallback<EntityManager, T> callback) {
        EntityManager em = entityManagerFactory.createEntityManager();
        try {
            em.getTransaction().begin();
            T result = callback.execute(em);
            em.getTransaction().commit();
            return result;
        } catch (Exception e) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            throw new RuntimeException("Transaction failed", e);
        } finally {
            if (em.isOpen()) {
                em.close();
            }
        }
    }
    
}
