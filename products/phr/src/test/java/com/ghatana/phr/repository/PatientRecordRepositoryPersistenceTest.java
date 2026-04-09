package com.ghatana.phr.repository;

import com.ghatana.phr.model.PatientRecord;
import com.ghatana.phr.model.PatientRecords;
import com.ghatana.phr.support.PhrPersistenceTestSupport;
import com.ghatana.platform.database.connection.ConnectionPool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PatientRecordRepositoryPersistenceTest {

    private PostgreSQLContainer<?> postgres;
    private ConnectionPool connectionPool;

    @BeforeEach
    void setUp() {
        postgres = PhrPersistenceTestSupport.startPostgres();
        connectionPool = PhrPersistenceTestSupport.createConnectionPool(postgres, "phr-patient-record-repo-test");
    }

    @AfterEach
    void tearDown() {
        PhrPersistenceTestSupport.closeConnectionPool(connectionPool);
        if (postgres != null) {
            postgres.stop();
        }
    }

    @Test
    void persistsPatientRecordsAcrossRepositoryInstances() {
        PatientRecordRepository firstRepository = new PatientRecordRepository(connectionPool.getDataSource());
        PatientRecord record = new PatientRecord();
        record.setPatientId("patient-1");
        record.setTenantId("tenant-1");
        record.setRecordType("encounter");
        record.setData(Map.of("diagnosis", "Hypertension", "severity", "moderate"));
        record.setCreatedAt(Instant.now());
        record.setUpdatedAt(Instant.now());
        record.setCreatedBy("provider-1");
        record.setUpdatedBy("provider-1");
        firstRepository.save(record);

        PatientRecordRepository secondRepository = new PatientRecordRepository(connectionPool.getDataSource());
        PatientRecords persisted = secondRepository.findByPatientId("patient-1");

        assertEquals(1, persisted.size());
        assertEquals("Hypertension", persisted.getRecords().getFirst().getData().get("diagnosis"));
        assertEquals(1, secondRepository.findByTenantId("tenant-1").size());
    }

    @Test
    void deletesPersistedPatientRecord() {
        PatientRecordRepository repository = new PatientRecordRepository(connectionPool.getDataSource());
        PatientRecord record = new PatientRecord();
        record.setPatientId("patient-1");
        repository.save(record);

        repository.delete(record.getRecordId());

        assertNull(new PatientRecordRepository(connectionPool.getDataSource()).findById(record.getRecordId()));
    }
}
