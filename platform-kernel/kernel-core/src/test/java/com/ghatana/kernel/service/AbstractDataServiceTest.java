package com.ghatana.kernel.service;

import com.ghatana.kernel.adapter.datacloud.*;
import com.ghatana.kernel.audit.CrossScopeAuditService;
import com.ghatana.kernel.context.KernelContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Comprehensive contract tests for AbstractDataService.
 *
 * @doc.type class
 * @doc.purpose AbstractDataService contract and behavior tests
 * @doc.layer kernel
 * @doc.pattern Test
 */
@DisplayName("AbstractDataService Contract Tests")
class AbstractDataServiceTest extends EventloopTestBase {

    private DataCloudKernelAdapter mockDataCloud;
    private CrossScopeAuditService mockAuditService;
    private KernelContext mockContext;
    private TestDataService service;

    @BeforeEach
    void setUp() { // GH-90000
        mockDataCloud = mock(DataCloudKernelAdapter.class); // GH-90000
        mockAuditService = mock(CrossScopeAuditService.class); // GH-90000
        mockContext = mock(KernelContext.class); // GH-90000

        when(mockContext.getDependency(DataCloudKernelAdapter.class)).thenReturn(mockDataCloud); // GH-90000
        when(mockContext.getDependency(CrossScopeAuditService.class)).thenReturn(mockAuditService); // GH-90000
        when(mockContext.getOptionalDependency(CrossScopeAuditService.class)).thenReturn(Optional.of(mockAuditService)); // GH-90000
        when(mockAuditService.recordAuditEvent(any(), any(), any(), any(), any())) // GH-90000
            .thenReturn(Promise.complete()); // GH-90000

        service = new TestDataService(mockContext, Runnable::run); // GH-90000
    }

    @Test
    @DisplayName("Service starts successfully and initializes datasets")
    void testServiceStart() { // GH-90000
        when(mockDataCloud.createSchema(any(SchemaCreateRequest.class))) // GH-90000
            .thenReturn(Promise.complete()); // GH-90000

        Promise<Void> startPromise = service.start(); // GH-90000
        runPromise(() -> startPromise); // GH-90000

        assertTrue(service.isHealthy()); // GH-90000
        verify(mockDataCloud).createSchema(any(SchemaCreateRequest.class)); // GH-90000
    }

    @Test
    @DisplayName("Service stops successfully and cleans up")
    void testServiceStop() { // GH-90000
        when(mockDataCloud.createSchema(any(SchemaCreateRequest.class))) // GH-90000
            .thenReturn(Promise.complete()); // GH-90000

        runPromise(() -> service.start()); // GH-90000
        assertTrue(service.isHealthy()); // GH-90000

        runPromise(() -> service.stop()); // GH-90000
        assertFalse(service.isHealthy()); // GH-90000
    }

    @Test
    @DisplayName("createRecord creates and audits record successfully")
    void testCreateRecord() { // GH-90000
        when(mockDataCloud.createSchema(any(SchemaCreateRequest.class))) // GH-90000
            .thenReturn(Promise.complete()); // GH-90000
        when(mockDataCloud.writeData(any(DataWriteRequest.class))) // GH-90000
            .thenReturn(Promise.complete()); // GH-90000

        runPromise(() -> service.start()); // GH-90000

        TestEntity entity = new TestEntity("test-id", "test-data"); // GH-90000
        Promise<TestEntity> createPromise = service.testCreateRecord(entity); // GH-90000
        TestEntity result = runPromise(() -> createPromise); // GH-90000

        assertEquals(entity, result); // GH-90000
        verify(mockDataCloud).writeData(any(DataWriteRequest.class)); // GH-90000
        verify(mockAuditService).recordAuditEvent(any(), any(), any(), any(), any()); // GH-90000
    }

    @Test
    @DisplayName("readRecord reads record successfully")
    void testReadRecord() { // GH-90000
        when(mockDataCloud.createSchema(any(SchemaCreateRequest.class))) // GH-90000
            .thenReturn(Promise.complete()); // GH-90000
        when(mockDataCloud.readData(any(DataReadRequest.class))) // GH-90000
            .thenReturn(Promise.of(new DataResult("test-id", // GH-90000
                "{\"id\":\"test-id\",\"data\":\"test-data\"}".getBytes(), // GH-90000
                Map.of(), System.currentTimeMillis()))); // GH-90000
        runPromise(() -> service.start()); // GH-90000

        Promise<Optional<TestEntity>> readPromise = service.testReadRecord("test-id");
        Optional<TestEntity> result = runPromise(() -> readPromise); // GH-90000

        assertTrue(result.isPresent()); // GH-90000
        verify(mockDataCloud).readData(any(DataReadRequest.class)); // GH-90000
    }

    @Test
    @DisplayName("updateRecord updates and audits record successfully")
    void testUpdateRecord() { // GH-90000
        when(mockDataCloud.createSchema(any(SchemaCreateRequest.class))) // GH-90000
            .thenReturn(Promise.complete()); // GH-90000
        when(mockDataCloud.writeData(any(DataWriteRequest.class))) // GH-90000
            .thenReturn(Promise.complete()); // GH-90000

        runPromise(() -> service.start()); // GH-90000

        TestEntity entity = new TestEntity("test-id", "updated-data"); // GH-90000
        Promise<TestEntity> updatePromise = service.testUpdateRecord(entity); // GH-90000
        TestEntity result = runPromise(() -> updatePromise); // GH-90000

        assertEquals(entity, result); // GH-90000
        verify(mockDataCloud).writeData(any(DataWriteRequest.class)); // GH-90000
        verify(mockAuditService).recordAuditEvent(any(), any(), any(), any(), any()); // GH-90000
    }

    @Test
    @DisplayName("deleteRecord deletes record successfully")
    void testDeleteRecord() { // GH-90000
        when(mockDataCloud.createSchema(any(SchemaCreateRequest.class))) // GH-90000
            .thenReturn(Promise.complete()); // GH-90000
        when(mockDataCloud.deleteData(any(DataDeleteRequest.class))) // GH-90000
            .thenReturn(Promise.complete()); // GH-90000

        runPromise(() -> service.start()); // GH-90000

        Promise<Void> deletePromise = service.testDeleteRecord("test-id");
        runPromise(() -> deletePromise); // GH-90000

        verify(mockDataCloud).deleteData(any(DataDeleteRequest.class)); // GH-90000
    }

    @Test
    @DisplayName("queryRecords queries and deserializes records successfully")
    void testQueryRecords() { // GH-90000
        when(mockDataCloud.createSchema(any(SchemaCreateRequest.class))) // GH-90000
            .thenReturn(Promise.complete()); // GH-90000
        when(mockDataCloud.queryData(any(DataQueryRequest.class))) // GH-90000
            .thenReturn(Promise.of(new QueryResult( // GH-90000
                List.of(new DataResult("test-id", // GH-90000
                    "{\"id\":\"test-id\",\"data\":\"test-data\"}".getBytes(), // GH-90000
                    Map.of(), System.currentTimeMillis())), // GH-90000
                1,
                false
            )));

        runPromise(() -> service.start()); // GH-90000

        Promise<List<TestEntity>> queryPromise = service.testQueryRecords("query", Map.of(), 10, 0); // GH-90000
        List<TestEntity> results = runPromise(() -> queryPromise); // GH-90000

        assertNotNull(results); // GH-90000
        verify(mockDataCloud).queryData(any(DataQueryRequest.class)); // GH-90000
    }

    @Test
    @DisplayName("ensureRunning throws when service not started")
    void testEnsureRunningThrows() { // GH-90000
        assertThrows(IllegalStateException.class, () -> service.testEnsureRunning()); // GH-90000
    }

    @Test
    @DisplayName("validateRequired throws for null value")
    void testValidateRequiredThrowsForNull() { // GH-90000
        assertThrows(IllegalArgumentException.class, // GH-90000
            () -> service.testValidateRequired(null, "testField")); // GH-90000
    }

    @Test
    @DisplayName("validateRequired throws for blank string")
    void testValidateRequiredThrowsForBlank() { // GH-90000
        assertThrows(IllegalArgumentException.class, // GH-90000
            () -> service.testValidateRequired("", "testField")); // GH-90000
    }

    @Test
    @DisplayName("validateRequired passes for valid value")
    void testValidateRequiredPasses() { // GH-90000
        assertDoesNotThrow(() -> service.testValidateRequired("valid", "testField")); // GH-90000
    }

    @Test
    @DisplayName("generateId with prefix creates valid ID")
    void testGenerateIdWithPrefix() { // GH-90000
        String id = service.testGenerateId("test");
        assertNotNull(id); // GH-90000
        assertTrue(id.startsWith("test-"));
        assertEquals(21, id.length()); // "test-" + 16 chars // GH-90000
    }

    @Test
    @DisplayName("generateId without prefix creates valid ID")
    void testGenerateIdWithoutPrefix() { // GH-90000
        String id = service.testGenerateId(); // GH-90000
        assertNotNull(id); // GH-90000
        assertEquals(32, id.length()); // GH-90000
    }

    @Test
    @DisplayName("audit records event with kernel audit service")
    void testAudit() { // GH-90000
        when(mockDataCloud.createSchema(any(SchemaCreateRequest.class))) // GH-90000
            .thenReturn(Promise.complete()); // GH-90000

        runPromise(() -> service.start()); // GH-90000

        Promise<Void> auditPromise = service.testAudit("TEST_ACTION", "entity-id", "Test details"); // GH-90000
        runPromise(() -> auditPromise); // GH-90000

        verify(mockAuditService).recordAuditEvent( // GH-90000
            eq("TEST_ACTION"),
            eq("test-service"),
            eq("entity-id"),
            eq("Test details"),
            any() // GH-90000
        );
    }

    @Test
    @DisplayName("createAuditMetadata creates proper metadata map")
    void testCreateAuditMetadata() { // GH-90000
        Map<String, String> metadata = service.testCreateAuditMetadata("key1", "value1", "key2", "value2"); // GH-90000

        assertNotNull(metadata); // GH-90000
        assertTrue(metadata.containsKey("timestamp"));
        assertTrue(metadata.containsKey("service"));
        assertEquals("value1", metadata.get("key1"));
        assertEquals("value2", metadata.get("key2"));
        assertEquals("test-service", metadata.get("service"));
    }

    // ==================== Test Implementation ====================

    static class TestDataService extends AbstractDataService {
        private static final String DATASET_ID = "test.dataset";

        public TestDataService(KernelContext context) { // GH-90000
            super(context); // GH-90000
        }

        public TestDataService(KernelContext context, Executor executor) { // GH-90000
            super(context, executor); // GH-90000
        }

        @Override
        public String getName() { // GH-90000
            return "test-service";
        }

        @Override
        protected Promise<Void> initializeDatasets() { // GH-90000
            return createSchema(DATASET_ID, Map.of("id", "string", "data", "string"), Map.of()); // GH-90000
        }

        // Expose protected methods for testing
        public Promise<TestEntity> testCreateRecord(TestEntity entity) { // GH-90000
            return createRecord(DATASET_ID, entity.id(), entity, Map.of("type", "test"), "TestEntity", 1); // GH-90000
        }

        @Override
        protected <T> byte[] serialize(T object, String typeName, int version) { // GH-90000
            // Override to avoid blocking serialization in tests
            // Return a dummy byte array that represents the serialized object
            return ("serialized:" + object.toString()).getBytes(); // GH-90000
        }

        public Promise<Optional<TestEntity>> testReadRecord(String id) { // GH-90000
            return readRecord(DATASET_ID, id, TestEntity.class); // GH-90000
        }

        public Promise<TestEntity> testUpdateRecord(TestEntity entity) { // GH-90000
            return updateRecord(DATASET_ID, entity.id(), entity, Map.of("type", "test"), "TestEntity", 1); // GH-90000
        }

        public Promise<Void> testDeleteRecord(String id) { // GH-90000
            return deleteRecord(DATASET_ID, id); // GH-90000
        }

        public Promise<List<TestEntity>> testQueryRecords(String query, Map<String, Object> params, int limit, int offset) { // GH-90000
            return queryRecords(DATASET_ID, query, params, limit, offset, TestEntity.class); // GH-90000
        }

        public void testEnsureRunning() { // GH-90000
            ensureRunning(); // GH-90000
        }

        public void testValidateRequired(Object value, String fieldName) { // GH-90000
            validateRequired(value, fieldName); // GH-90000
        }

        public String testGenerateId(String prefix) { // GH-90000
            return generateId(prefix); // GH-90000
        }

        public String testGenerateId() { // GH-90000
            return generateId(); // GH-90000
        }

        public Promise<Void> testAudit(String action, String entityId, String details) { // GH-90000
            return audit(action, entityId, details); // GH-90000
        }

        public Map<String, String> testCreateAuditMetadata(String... keyValuePairs) { // GH-90000
            return createAuditMetadata(keyValuePairs); // GH-90000
        }
    }

    record TestEntity(String id, String data) {} // GH-90000
}
