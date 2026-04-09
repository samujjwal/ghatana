package com.ghatana.finance.ai;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @doc.type class
 * @doc.purpose Verifies PostgreSQL-backed model performance persistence
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
class ModelPerformanceRepositoryPersistenceTest {

    private PostgreSQLContainer<?> postgres;
    private DataSource dataSource;

    @BeforeEach
    void setUp() {
        postgres = FinanceAiPersistenceTestSupport.startPostgres();
        dataSource = FinanceAiPersistenceTestSupport.createDataSource(postgres, "finance-performance-repo-test");
        ModelRepository modelRepository = new ModelRepository(dataSource);
        ModelRecord modelRecord = new ModelRecord();
        modelRecord.setModelId("fraud-detection-v2");
        modelRepository.save(modelRecord);
    }

    @AfterEach
    void tearDown() {
        FinanceAiPersistenceTestSupport.closeDataSource(dataSource);
        if (postgres != null) {
            postgres.stop();
        }
    }

    @Test
    void appendsPerformanceRecordsAndReadsThemInTimestampOrder() {
        ModelPerformanceRepository repository = new ModelPerformanceRepository(dataSource);

        ModelPerformanceRecord first = new ModelPerformanceRecord();
        first.setModelId("fraud-detection-v2");
        first.setConfidence(0.81);
        first.setAccuracy(0.9);
        first.setLatency(100L);
        first.setTimestamp(Instant.parse("2026-04-06T10:15:30Z"));
        repository.save(first);

        ModelPerformanceRecord second = new ModelPerformanceRecord();
        second.setModelId("fraud-detection-v2");
        second.setConfidence(0.88);
        second.setAccuracy(0.93);
        second.setLatency(95L);
        second.setTimestamp(Instant.parse("2026-04-06T10:16:30Z"));
        repository.save(second);

        List<ModelPerformanceRecord> persisted = new ModelPerformanceRepository(dataSource)
            .findByModelId("fraud-detection-v2");

        assertEquals(2, persisted.size());
        assertEquals(0.81, persisted.get(0).getConfidence());
        assertEquals(0.88, persisted.get(1).getConfidence());
    }
}
