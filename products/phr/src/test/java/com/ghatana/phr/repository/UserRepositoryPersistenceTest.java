package com.ghatana.phr.repository;

import com.ghatana.phr.model.PHRUser;
import com.ghatana.phr.support.PhrPersistenceTestSupport;
import com.ghatana.platform.database.connection.ConnectionPool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserRepositoryPersistenceTest {

    private PostgreSQLContainer<?> postgres;
    private ConnectionPool connectionPool;

    @BeforeEach
    void setUp() {
        postgres = PhrPersistenceTestSupport.startPostgres();
        connectionPool = PhrPersistenceTestSupport.createConnectionPool(postgres, "phr-user-repo-test");
    }

    @AfterEach
    void tearDown() {
        PhrPersistenceTestSupport.closeConnectionPool(connectionPool);
        if (postgres != null) {
            postgres.stop();
        }
    }

    @Test
    void persistsUserCredentialsAcrossRepositoryInstances() {
        UserRepository firstRepository = new UserRepository(connectionPool.getDataSource());
        PHRUser user = new PHRUser("provider-1", "dr.smith", "dr.smith@hospital.com");
        user.setProviderId("PROV-001");
        user.setAccessLevel("FULL");
        user.addRole("HEALTHCARE_PROVIDER");
        user.addPermission("read:patient-records");
        user.setPasswordHash("hashed-password");
        user.setFailedLoginAttempts(2);
        user.setLockoutUntil(Instant.now().plusSeconds(60));
        firstRepository.save(user);

        UserRepository secondRepository = new UserRepository(connectionPool.getDataSource());
        PHRUser persisted = secondRepository.findByUsername("dr.smith").orElse(null);

        assertNotNull(persisted);
        assertEquals("PROV-001", persisted.getProviderId());
        assertTrue(persisted.getRoles().contains("HEALTHCARE_PROVIDER"));
        assertTrue(persisted.getPermissions().contains("read:patient-records"));
        assertEquals(2, persisted.getFailedLoginAttempts());
        assertTrue(secondRepository.exists("provider-1"));
    }

    @Test
    void deletesPersistedUser() {
        UserRepository repository = new UserRepository(connectionPool.getDataSource());
        PHRUser user = new PHRUser("provider-1", "dr.smith", "dr.smith@hospital.com");
        repository.save(user);

        repository.delete("provider-1");

        assertFalse(new UserRepository(connectionPool.getDataSource()).exists("provider-1"));
        assertNull(new UserRepository(connectionPool.getDataSource()).findById("provider-1"));
    }
}
