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
    void setUp() { // GH-90000
        JdbcDataSource dataSource = new JdbcDataSource(); // GH-90000
        dataSource.setURL("jdbc:h2:mem:registry_" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1"); // GH-90000
        dataSource.setUser("sa");
        dataSource.setPassword("sa");

        registry = new JdbcModuleRegistry(dataSource); // GH-90000
        registry.ensureSchema(); // GH-90000
    }

    @Test
    @DisplayName("registers, updates, and fetches module state")
    void registerAndFetch() { // GH-90000
        registry.registerModule("platform:java:kernel", "1.0.0", "REGISTERED"); // GH-90000
        registry.registerModule("platform:java:kernel", "1.0.1", "STARTED"); // GH-90000

        JdbcModuleRegistry.ModuleRegistration registration =
            registry.getModule("platform:java:kernel").orElseThrow();

        assertEquals("1.0.1", registration.moduleVersion()); // GH-90000
        assertEquals("STARTED", registration.moduleStatus()); // GH-90000
    }

    @Test
    @DisplayName("lists and removes modules")
    void listAndRemove() { // GH-90000
        registry.registerModule("m1", "1.0", "REGISTERED"); // GH-90000
        registry.registerModule("m2", "1.1", "FAILED"); // GH-90000

        List<JdbcModuleRegistry.ModuleRegistration> all = registry.listModules(); // GH-90000
        assertEquals(2, all.size()); // GH-90000

        registry.removeModule("m1");
        assertTrue(registry.getModule("m1").isEmpty());
    }
}
