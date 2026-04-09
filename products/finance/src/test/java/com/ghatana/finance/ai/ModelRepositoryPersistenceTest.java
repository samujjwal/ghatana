package com.ghatana.finance.ai;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @doc.type class
 * @doc.purpose Verifies PostgreSQL-backed model repository persistence
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
class ModelRepositoryPersistenceTest {

    private PostgreSQLContainer<?> postgres;
    private DataSource dataSource;

    @BeforeEach
    void setUp() {
        postgres = FinanceAiPersistenceTestSupport.startPostgres();
        dataSource = FinanceAiPersistenceTestSupport.createDataSource(postgres, "finance-model-repo-test");
    }

    @AfterEach
    void tearDown() {
        FinanceAiPersistenceTestSupport.closeDataSource(dataSource);
        if (postgres != null) {
            postgres.stop();
        }
    }

    @Test
    void persistsModelMetadataAcrossRepositoryInstances() {
        ModelRepository firstRepository = new ModelRepository(dataSource);
        ModelRecord record = new ModelRecord();
        record.setModelId("fraud-detection-v2");
        record.setName("Fraud Detector");
        record.setVersion("2.1.0");
        record.setType("classification");
        record.setMetadata(Map.of("endpoint", "http://fraud-model.internal/predict"));
        record.setStatus("ACTIVE");
        firstRepository.save(record);

        ModelRepository secondRepository = new ModelRepository(dataSource);
        ModelRecord persisted = secondRepository.findByModelId("fraud-detection-v2");

        assertNotNull(persisted);
        assertEquals("Fraud Detector", persisted.getName());
        assertEquals("http://fraud-model.internal/predict", persisted.getMetadata().get("endpoint"));
        assertEquals("ACTIVE", persisted.getStatus());
    }

    @Test
    void deletesPersistedModelMetadata() {
        ModelRepository repository = new ModelRepository(dataSource);
        ModelRecord record = new ModelRecord();
        record.setModelId("fraud-detection-v2");
        repository.save(record);

        repository.delete("fraud-detection-v2");

        assertNull(new ModelRepository(dataSource).findByModelId("fraud-detection-v2"));
    }
}
