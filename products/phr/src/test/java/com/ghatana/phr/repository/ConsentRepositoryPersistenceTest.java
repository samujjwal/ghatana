package com.ghatana.phr.repository;

import com.ghatana.phr.model.PatientConsent;
import com.ghatana.phr.support.PhrPersistenceTestSupport;
import com.ghatana.platform.database.connection.ConnectionPool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConsentRepositoryPersistenceTest {

    private PostgreSQLContainer<?> postgres;
    private ConnectionPool connectionPool;

    @BeforeEach
    void setUp() {
        postgres = PhrPersistenceTestSupport.startPostgres();
        connectionPool = PhrPersistenceTestSupport.createConnectionPool(postgres, "phr-consent-repo-test");
    }

    @AfterEach
    void tearDown() {
        PhrPersistenceTestSupport.closeConnectionPool(connectionPool);
        if (postgres != null) {
            postgres.stop();
        }
    }

    @Test
    void persistsConsentAcrossRepositoryInstances() {
        ConsentRepository firstRepository = new ConsentRepository(connectionPool.getDataSource());
        PatientConsent consent = new PatientConsent();
        consent.setPatientId("patient-1");
        consent.setTenantId("tenant-1");
        consent.setPurpose("treatment");
        consent.setGranted(true);
        consent.setTimestamp(Instant.now());
        consent.setExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
        consent.setGrantedBy("provider-1");
        firstRepository.save(consent);

        ConsentRepository secondRepository = new ConsentRepository(connectionPool.getDataSource());
        PatientConsent persisted = secondRepository.findByPatientAndPurpose("patient-1", "treatment");

        assertNotNull(persisted);
        assertEquals("tenant-1", persisted.getTenantId());
        assertEquals("provider-1", persisted.getGrantedBy());
        assertEquals(1, secondRepository.findByPatientId("patient-1").size());
    }

    @Test
    void deletesPersistedConsent() {
        ConsentRepository repository = new ConsentRepository(connectionPool.getDataSource());
        PatientConsent consent = new PatientConsent();
        consent.setPatientId("patient-1");
        consent.setPurpose("research");
        repository.save(consent);

        repository.delete(consent.getConsentId());

        assertNull(new ConsentRepository(connectionPool.getDataSource()).findById(consent.getConsentId()));
    }
}
