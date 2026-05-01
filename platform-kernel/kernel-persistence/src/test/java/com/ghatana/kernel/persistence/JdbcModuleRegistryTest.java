package com.ghatana.kernel.persistence;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @doc.type class
 * @doc.purpose Tests JDBC module-state registry persistence operations
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("JdbcModuleRegistry")
class JdbcModuleRegistryTest {

    private JdbcModuleRegistry registry;

    @BeforeEach
    void setUp() { 
        JdbcDataSource dataSource = new JdbcDataSource(); 
        dataSource.setURL("jdbc:h2:mem:registry_" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1"); 
        dataSource.setUser("sa");
        dataSource.setPassword("sa");

        registry = new JdbcModuleRegistry(dataSource); 
        registry.ensureSchema(); 
    }

    @Test
    @DisplayName("registers, updates, and fetches module state")
    void registerAndFetch() { 
        registry.registerModule("platform:java:kernel", "1.0.0", "REGISTERED"); 
        registry.registerModule("platform:java:kernel", "1.0.1", "STARTED"); 

        JdbcModuleRegistry.ModuleRegistration registration =
            registry.getModule("platform:java:kernel").orElseThrow();

        assertEquals("1.0.1", registration.moduleVersion()); 
        assertEquals("STARTED", registration.moduleStatus()); 
    }

    @Test
    @DisplayName("lists and removes modules")
    void listAndRemove() { 
        registry.registerModule("m1", "1.0", "REGISTERED"); 
        registry.registerModule("m2", "1.1", "FAILED"); 

        List<JdbcModuleRegistry.ModuleRegistration> all = registry.listModules(); 
        assertEquals(2, all.size()); 

        registry.removeModule("m1");
        assertTrue(registry.getModule("m1").isEmpty());
    }
}
