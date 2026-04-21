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
import java.util.concurrent.Executors;

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
    void setUp() {
        mockDataCloud = mock(DataCloudKernelAdapter.class);
        mockAuditService = mock(CrossScopeAuditService.class);
        mockContext = mock(KernelContext.class);

        when(mockContext.getDependency(DataCloudKernelAdapter.class)).thenReturn(mockDataCloud);
        when(mockContext.getDependency(CrossScopeAuditService.class)).thenReturn(mockAuditService);
        when(mockContext.getOptionalDependency(CrossScopeAuditService.class)).thenReturn(Optional.of(mockAuditService));
        when(mockAuditService.recordAuditEvent(any(), any(), any(), any(), any()))
            .thenReturn(Promise.complete());

        service = new TestDataService(mockContext, Executors.newCachedThreadPool());
    }

    @Test
    @DisplayName("Service starts successfully and initializes datasets")
    void testServiceStart() {
        when(mockDataCloud.createSchema(any(SchemaCreateRequest.class)))
            .thenReturn(Promise.complete());

        Promise<Void> startPromise = service.start();
        runPromise(() -> startPromise);

        assertTrue(service.isHealthy());
        verify(mockDataCloud).createSchema(any(SchemaCreateRequest.class));
    }

    @Test
    @DisplayName("Service stops successfully and cleans up")
    void testServiceStop() {
        when(mockDataCloud.createSchema(any(SchemaCreateRequest.class)))
            .thenReturn(Promise.complete());

        runPromise(() -> service.start());
        assertTrue(service.isHealthy());

        runPromise(() -> service.stop());
        assertFalse(service.isHealthy());
    }

    @Test
    @DisplayName("createRecord creates and audits record successfully")
    void testCreateRecord() {
        when(mockDataCloud.createSchema(any(SchemaCreateRequest.class)))
            .thenReturn(Promise.complete());
        when(mockDataCloud.writeData(any(DataWriteRequest.class)))
            .thenReturn(Promise.complete());

        runPromise(() -> service.start());

        TestEntity entity = new TestEntity("test-id", "test-data");
        Promise<TestEntity> createPromise = service.testCreateRecord(entity);
        TestEntity result = runPromise(() -> createPromise);

        assertEquals(entity, result);
        verify(mockDataCloud).writeData(any(DataWriteRequest.class));
        verify(mockAuditService).recordAuditEvent(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("readRecord reads record successfully")
    void testReadRecord() {
        when(mockDataCloud.createSchema(any(SchemaCreateRequest.class)))
            .thenReturn(Promise.complete());
        when(mockDataCloud.readData(any(DataReadRequest.class)))
            .thenReturn(Promise.of(new DataResult("test-id",
                "{\"id\":\"test-id\",\"data\":\"test-data\"}".getBytes(),
                Map.of(), System.currentTimeMillis())));
        runPromise(() -> service.start());

        Promise<Optional<TestEntity>> readPromise = service.testReadRecord("test-id");
        Optional<TestEntity> result = runPromise(() -> readPromise);

        assertTrue(result.isPresent());
        verify(mockDataCloud).readData(any(DataReadRequest.class));
    }

    @Test
    @DisplayName("updateRecord updates and audits record successfully")
    void testUpdateRecord() {
        when(mockDataCloud.createSchema(any(SchemaCreateRequest.class)))
            .thenReturn(Promise.complete());
        when(mockDataCloud.writeData(any(DataWriteRequest.class)))
            .thenReturn(Promise.complete());

        runPromise(() -> service.start());

        TestEntity entity = new TestEntity("test-id", "updated-data");
        Promise<TestEntity> updatePromise = service.testUpdateRecord(entity);
        TestEntity result = runPromise(() -> updatePromise);

        assertEquals(entity, result);
        verify(mockDataCloud).writeData(any(DataWriteRequest.class));
        verify(mockAuditService).recordAuditEvent(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("deleteRecord deletes record successfully")
    void testDeleteRecord() {
        when(mockDataCloud.createSchema(any(SchemaCreateRequest.class)))
            .thenReturn(Promise.complete());
        when(mockDataCloud.deleteData(any(DataDeleteRequest.class)))
            .thenReturn(Promise.complete());

        runPromise(() -> service.start());

        Promise<Void> deletePromise = service.testDeleteRecord("test-id");
        runPromise(() -> deletePromise);

        verify(mockDataCloud).deleteData(any(DataDeleteRequest.class));
    }

    @Test
    @DisplayName("queryRecords queries and deserializes records successfully")
    void testQueryRecords() {
        when(mockDataCloud.createSchema(any(SchemaCreateRequest.class)))
            .thenReturn(Promise.complete());
        when(mockDataCloud.queryData(any(DataQueryRequest.class)))
            .thenReturn(Promise.of(new QueryResult(
                List.of(new DataResult("test-id",
                    "{\"id\":\"test-id\",\"data\":\"test-data\"}".getBytes(),
                    Map.of(), System.currentTimeMillis())),
                1,
                false
            )));

        runPromise(() -> service.start());

        Promise<List<TestEntity>> queryPromise = service.testQueryRecords("query", Map.of(), 10, 0);
        List<TestEntity> results = runPromise(() -> queryPromise);

        assertNotNull(results);
        verify(mockDataCloud).queryData(any(DataQueryRequest.class));
    }

    @Test
    @DisplayName("ensureRunning throws when service not started")
    void testEnsureRunningThrows() {
        assertThrows(IllegalStateException.class, () -> service.testEnsureRunning());
    }

    @Test
    @DisplayName("validateRequired throws for null value")
    void testValidateRequiredThrowsForNull() {
        assertThrows(IllegalArgumentException.class,
            () -> service.testValidateRequired(null, "testField"));
    }

    @Test
    @DisplayName("validateRequired throws for blank string")
    void testValidateRequiredThrowsForBlank() {
        assertThrows(IllegalArgumentException.class,
            () -> service.testValidateRequired("", "testField"));
    }

    @Test
    @DisplayName("validateRequired passes for valid value")
    void testValidateRequiredPasses() {
        assertDoesNotThrow(() -> service.testValidateRequired("valid", "testField"));
    }

    @Test
    @DisplayName("generateId with prefix creates valid ID")
    void testGenerateIdWithPrefix() {
        String id = service.testGenerateId("test");
        assertNotNull(id);
        assertTrue(id.startsWith("test-"));
        assertEquals(21, id.length()); // "test-" + 16 chars
    }

    @Test
    @DisplayName("generateId without prefix creates valid ID")
    void testGenerateIdWithoutPrefix() {
        String id = service.testGenerateId();
        assertNotNull(id);
        assertEquals(32, id.length());
    }

    @Test
    @DisplayName("audit records event with kernel audit service")
    void testAudit() {
        when(mockDataCloud.createSchema(any(SchemaCreateRequest.class)))
            .thenReturn(Promise.complete());

        runPromise(() -> service.start());

        Promise<Void> auditPromise = service.testAudit("TEST_ACTION", "entity-id", "Test details");
        runPromise(() -> auditPromise);

        verify(mockAuditService).recordAuditEvent(
            eq("TEST_ACTION"),
            eq("test-service"),
            eq("entity-id"),
            eq("Test details"),
            any()
        );
    }

    @Test
    @DisplayName("createAuditMetadata creates proper metadata map")
    void testCreateAuditMetadata() {
        Map<String, String> metadata = service.testCreateAuditMetadata("key1", "value1", "key2", "value2");

        assertNotNull(metadata);
        assertTrue(metadata.containsKey("timestamp"));
        assertTrue(metadata.containsKey("service"));
        assertEquals("value1", metadata.get("key1"));
        assertEquals("value2", metadata.get("key2"));
        assertEquals("test-service", metadata.get("service"));
    }

    // ==================== Test Implementation ====================

    static class TestDataService extends AbstractDataService {
        private static final String DATASET_ID = "test.dataset";

        public TestDataService(KernelContext context) {
            super(context);
        }

        public TestDataService(KernelContext context, Executor executor) {
            super(context, executor);
        }

        @Override
        public String getName() {
            return "test-service";
        }

        @Override
        protected Promise<Void> initializeDatasets() {
            return createSchema(DATASET_ID, Map.of("id", "string", "data", "string"), Map.of());
        }

        // Expose protected methods for testing
        public Promise<TestEntity> testCreateRecord(TestEntity entity) {
            return createRecord(DATASET_ID, entity.id(), entity, Map.of("type", "test"), "TestEntity", 1);
        }

        @Override
        protected <T> byte[] serialize(T object, String typeName, int version) {
            // Override to avoid blocking serialization in tests
            // Return a dummy byte array that represents the serialized object
            return ("serialized:" + object.toString()).getBytes();
        }

        public Promise<Optional<TestEntity>> testReadRecord(String id) {
            return readRecord(DATASET_ID, id, TestEntity.class);
        }

        public Promise<TestEntity> testUpdateRecord(TestEntity entity) {
            return updateRecord(DATASET_ID, entity.id(), entity, Map.of("type", "test"), "TestEntity", 1);
        }

        public Promise<Void> testDeleteRecord(String id) {
            return deleteRecord(DATASET_ID, id);
        }

        public Promise<List<TestEntity>> testQueryRecords(String query, Map<String, Object> params, int limit, int offset) {
            return queryRecords(DATASET_ID, query, params, limit, offset, TestEntity.class);
        }

        public void testEnsureRunning() {
            ensureRunning();
        }

        public void testValidateRequired(Object value, String fieldName) {
            validateRequired(value, fieldName);
        }

        public String testGenerateId(String prefix) {
            return generateId(prefix);
        }

        public String testGenerateId() {
            return generateId();
        }

        public Promise<Void> testAudit(String action, String entityId, String details) {
            return audit(action, entityId, details);
        }

        public Map<String, String> testCreateAuditMetadata(String... keyValuePairs) {
            return createAuditMetadata(keyValuePairs);
        }
    }

    record TestEntity(String id, String data) {}
}
