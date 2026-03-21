package com.ghatana.agent.registry;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Factory class for creating and managing the JPA EntityManagerFactory.
 * This class ensures that the EntityManagerFactory is properly initialized
 * and closed when the application shuts down.
 */
public class EntityManagerFactoryFactory {
    private static final Logger log = LoggerFactory.getLogger(EntityManagerFactoryFactory.class);
    private static EntityManagerFactory instance;
    private static final Object lock = new Object();

    /**
     * Gets the singleton instance of EntityManagerFactory.
     * If the instance doesn't exist, it will be created.
     *
     * @return the singleton EntityManagerFactory instance
     */
    public static EntityManagerFactory getEntityManagerFactory() {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = createEntityManagerFactory();
                }
            }
        }
        return instance;
    }

    /**
     * Creates a new EntityManagerFactory with the specified persistence unit name.
     * This method can be overridden in tests to provide a test-specific configuration.
     *
     * @return a new EntityManagerFactory instance
     */
    protected static EntityManagerFactory createEntityManagerFactory() {
        log.info("Creating EntityManagerFactory for persistence unit: agent-registry-pu");
        Map<String, String> properties = new HashMap<>();
        
        // Configure HikariCP connection pooling
        properties.put("hibernate.connection.provider_class", "org.hibernate.hikaricp.internal.HikariCPConnectionProvider");
        properties.put("hibernate.hikari.minimumIdle", "5");
        properties.put("hibernate.hikari.maximumPoolSize", "20");
        properties.put("hibernate.hikari.idleTimeout", "30000");
        properties.put("hibernate.hikari.maxLifetime", "2000000");
        properties.put("hibernate.hikari.connectionTimeout", "30000");
        properties.put("hibernate.hikari.autoCommit", "false");
        
        // Enable second-level and query cache
        properties.put("hibernate.cache.use_second_level_cache", "true");
        properties.put("hibernate.cache.use_query_cache", "true");
        properties.put("hibernate.cache.region.factory_class", "org.hibernate.cache.jcache.JCacheRegionFactory");
        properties.put("hibernate.javax.cache.provider", "org.ehcache.jsr107.EhcacheCachingProvider");
        
        // Log SQL for development (disable in production)
        properties.put("hibernate.show_sql", "true");
        properties.put("hibernate.format_sql", "true");
        properties.put("hibernate.use_sql_comments", "true");
        
        // Schema validation
        properties.put("hibernate.hbm2ddl.auto", "validate");
        
        // Register shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(EntityManagerFactoryFactory::close));
        
        return Persistence.createEntityManagerFactory("agent-registry-pu", properties);
    }

    /**
     * Closes the EntityManagerFactory if it's open.
     * This method is called automatically when the JVM shuts down.
     */
    public static void close() {
        if (instance != null && instance.isOpen()) {
            log.info("Closing EntityManagerFactory");
            instance.close();
            instance = null;
        }
    }

    /**
     * Resets the singleton instance (for testing purposes).
     */
    public static void reset() {
        synchronized (lock) {
            if (instance != null) {
                close();
            }
        }
    }
}
