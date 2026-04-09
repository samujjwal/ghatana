package com.ghatana.finance.ai;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @doc.type class
 * @doc.purpose Verifies PostgreSQL-backed model approval persistence
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
class ModelApprovalRepositoryPersistenceTest {

    private PostgreSQLContainer<?> postgres;
    private DataSource dataSource;

    @BeforeEach
    void setUp() {
        postgres = FinanceAiPersistenceTestSupport.startPostgres();
        dataSource = FinanceAiPersistenceTestSupport.createDataSource(postgres, "finance-approval-repo-test");
    }

    @AfterEach
    void tearDown() {
        FinanceAiPersistenceTestSupport.closeDataSource(dataSource);
        if (postgres != null) {
            postgres.stop();
        }
    }

    @Test
    void persistsApprovalConditionsAcrossRepositoryInstances() {
        ModelApprovalRepository repository = new ModelApprovalRepository(dataSource);
        ModelApprovalRecord record = new ModelApprovalRecord();
        record.setModelId("fraud-detection-v2");
        record.setApproved(true);
        record.setApprover("qa");
        record.setApprovalDate(Instant.parse("2026-04-06T10:15:30Z"));
        record.setVersion("2.0");
        record.setConditions(Map.of("approved_operations", List.of("detect_fraud", "assess_risk")));
        repository.save(record);

        ModelApprovalRecord persisted = new ModelApprovalRepository(dataSource).findByModelId("fraud-detection-v2");

        assertNotNull(persisted);
        assertTrue(persisted.isApproved());
        assertEquals("qa", persisted.getApprover());
        assertEquals("2.0", persisted.getVersion());
        assertEquals(List.of("detect_fraud", "assess_risk"), persisted.getConditions().get("approved_operations"));
    }

    @Test
    void deletesPersistedApproval() {
        ModelApprovalRepository repository = new ModelApprovalRepository(dataSource);
        ModelApprovalRecord record = new ModelApprovalRecord();
        record.setModelId("fraud-detection-v2");
        record.setApproved(true);
        repository.save(record);

        repository.delete("fraud-detection-v2");

        assertNull(new ModelApprovalRepository(dataSource).findByModelId("fraud-detection-v2"));
    }
}
