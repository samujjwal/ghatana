package com.ghatana.phr.repository;

import com.ghatana.phr.model.TenantConfig;
import com.ghatana.phr.support.PhrPersistenceTestSupport;
import com.ghatana.platform.database.connection.ConnectionPool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantConfigRepositoryPersistenceTest {

    private PostgreSQLContainer<?> postgres;
    private ConnectionPool connectionPool;

    @BeforeEach
    void setUp() {
        postgres = PhrPersistenceTestSupport.startPostgres();
        connectionPool = PhrPersistenceTestSupport.createConnectionPool(postgres, "phr-tenant-config-repo-test");
    }

    @AfterEach
    void tearDown() {
        PhrPersistenceTestSupport.closeConnectionPool(connectionPool);
        if (postgres != null) {
            postgres.stop();
        }
    }

    @Test
    void persistsTenantConfigAcrossRepositoryInstances() {
        TenantConfigRepository firstRepository = new TenantConfigRepository(connectionPool.getDataSource());
        TenantConfig config = new TenantConfig("tenant-1", "Nepal Health");
        config.addAllowedRegion("bagmati");
        config.addAllowedRegion("koshi");
        config.setHipaaCompliant(true);
        config.setMfaRequired(false);
        firstRepository.save(config);

        TenantConfig persisted = new TenantConfigRepository(connectionPool.getDataSource()).findById("tenant-1");

        assertEquals("Nepal Health", persisted.getTenantName());
        assertTrue(persisted.getAllowedRegions().contains("bagmati"));
        assertFalse(persisted.isMfaRequired());
    }

    @Test
    void deletesPersistedTenantConfig() {
        TenantConfigRepository repository = new TenantConfigRepository(connectionPool.getDataSource());
        TenantConfig config = new TenantConfig("tenant-1", "Nepal Health");
        repository.save(config);

        repository.delete("tenant-1");

        assertNull(new TenantConfigRepository(connectionPool.getDataSource()).findById("tenant-1"));
    }
}