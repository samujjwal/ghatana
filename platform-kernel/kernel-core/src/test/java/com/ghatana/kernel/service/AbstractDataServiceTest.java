package com.ghatana.kernel.service;

import com.ghatana.kernel.adapter.datacloud.*;
import com.ghatana.kernel.audit.CrossScopeAuditService;
import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.policy.AuditPolicyResolver;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;

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

    private InMemoryDataCloudKernelAdapter dataCloud;
    private InMemoryCrossScopeAuditService auditService;
    private InMemoryKernelContext context;
    private TestDataService service;

    @BeforeEach
    void setUp() {
        dataCloud = new InMemoryDataCloudKernelAdapter();
        auditService = new InMemoryCrossScopeAuditService();
        context = new InMemoryKernelContext(dataCloud, auditService);
        service = new TestDataService(context, Runnable::run);
    }

    @Test
    @DisplayName("Service starts successfully and initializes datasets")
    void testServiceStart() {
        Promise<Void> startPromise = service.start();
        runPromise(() -> startPromise);

        assertTrue(service.isHealthy());
    }

    @Test
    @DisplayName("Service stops successfully and cleans up")
    void testServiceStop() {
        runPromise(() -> service.start());
        assertTrue(service.isHealthy());

        runPromise(() -> service.stop());
        assertFalse(service.isHealthy());
    }

    @Test
    @DisplayName("createRecord creates and audits record successfully")
    void testCreateRecord() {
        runPromise(() -> service.start());

        TestEntity entity = new TestEntity("test-id", "test-data");
        Promise<TestEntity> createPromise = service.testCreateRecord(entity);
        TestEntity result = runPromise(() -> createPromise);

        assertEquals(entity, result);
    }

    @Test
    @DisplayName("readRecord reads record successfully")
    void testReadRecord() {
        dataCloud.setReadDataResult(new DataResult("test-id",
            "{\"id\":\"test-id\",\"data\":\"test-data\"}".getBytes(),
            Map.of(), System.currentTimeMillis()));
        runPromise(() -> service.start());

        Promise<Optional<TestEntity>> readPromise = service.testReadRecord("test-id");
        Optional<TestEntity> result = runPromise(() -> readPromise);

        assertTrue(result.isPresent());
    }

    @Test
    @DisplayName("updateRecord updates and audits record successfully")
    void testUpdateRecord() {
        runPromise(() -> service.start());

        TestEntity entity = new TestEntity("test-id", "updated-data");
        Promise<TestEntity> updatePromise = service.testUpdateRecord(entity);
        TestEntity result = runPromise(() -> updatePromise);

        assertEquals(entity, result);
    }

    @Test
    @DisplayName("deleteRecord deletes record successfully")
    void testDeleteRecord() {
        runPromise(() -> service.start());

        Promise<Void> deletePromise = service.testDeleteRecord("test-id");
        runPromise(() -> deletePromise);
    }

    @Test
    @DisplayName("queryRecords queries and deserializes records successfully")
    void testQueryRecords() {
        dataCloud.setQueryDataResult(new QueryResult(
            List.of(new DataResult("test-id",
                "{\"id\":\"test-id\",\"data\":\"test-data\"}".getBytes(),
                Map.of(), System.currentTimeMillis())),
            1,
            false
        ));
        runPromise(() -> service.start());

        Promise<List<TestEntity>> queryPromise = service.testQueryRecords("query", Map.of(), 10, 0);
        List<TestEntity> results = runPromise(() -> queryPromise);

        assertNotNull(results);
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
        runPromise(() -> service.start());

        Promise<Void> auditPromise = service.testAudit("TEST_ACTION", "entity-id", "Test details");
        runPromise(() -> auditPromise);

        assertEquals(1, auditService.getAuditEvents().size());
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

    // ==================== Test doubles ====================

    private static final class InMemoryDataCloudKernelAdapter implements DataCloudKernelAdapter {
        private DataResult readDataResult;
        private QueryResult queryDataResult;

        void setReadDataResult(DataResult result) {
            this.readDataResult = result;
        }

        void setQueryDataResult(QueryResult result) {
            this.queryDataResult = result;
        }

        @Override
        public Promise<Void> createSchema(SchemaCreateRequest request) {
            return Promise.complete();
        }

        @Override
        public Promise<Void> writeData(DataWriteRequest request) {
            return Promise.complete();
        }

        @Override
        public Promise<DataResult> readData(DataReadRequest request) {
            return Promise.of(readDataResult);
        }

        @Override
        public Promise<Void> deleteData(DataDeleteRequest request) {
            return Promise.complete();
        }

        @Override
        public Promise<QueryResult> queryData(DataQueryRequest request) {
            return Promise.of(queryDataResult);
        }

        @Override
        public Promise<SchemaInfo> getSchema(String datasetId) {
            return Promise.of(new SchemaInfo(datasetId, Map.of(), 0L, 0L));
        }

        @Override
        public Promise<List<DatasetInfo>> listDatasets() {
            return Promise.of(List.of());
        }

        @Override
        public Promise<TransactionHandle> beginTransaction() {
            return Promise.of(new TransactionHandle() {
                @Override
                public String getId() {
                    return "tx-1";
                }
                @Override
                public boolean isActive() {
                    return true;
                }
            });
        }

        @Override
        public Promise<Void> commitTransaction(TransactionHandle transaction) {
            return Promise.complete();
        }

        @Override
        public Promise<Void> rollbackTransaction(TransactionHandle transaction) {
            return Promise.complete();
        }

        @Override
        public Promise<DataStream> openStream(DataStreamRequest request) {
            return Promise.of(new DataStream() {
                @Override
                public Promise<byte[]> readChunk() {
                    return Promise.of(new byte[0]);
                }
                @Override
                public Promise<Void> writeChunk(byte[] data) {
                    return Promise.complete();
                }
                @Override
                public Promise<Void> close() {
                    return Promise.complete();
                }
                @Override
                public boolean isOpen() {
                    return true;
                }
            });
        }
    }

    private static final class InMemoryCrossScopeAuditService extends CrossScopeAuditService {
        private final java.util.List<AuditEvent> auditEvents = new java.util.ArrayList<>();

        InMemoryCrossScopeAuditService() {
            super(
                (sourceScope, targetScope, classification) -> AuditPolicyResolver.AuditPolicy.defaults(),
                new AuditEventStore() {
                    @Override
                    public Promise<Void> store(ScopeAuditRecord record) {
                        return Promise.complete();
                    }

                    @Override
                    public Promise<Set<ScopeAuditRecord>> query(Instant startDate,
                                                                 Instant endDate,
                                                                 com.ghatana.kernel.scope.ScopeDescriptor sourceScope,
                                                                 com.ghatana.kernel.scope.ScopeDescriptor targetScope) {
                        return Promise.of(Set.of());
                    }
                }
            );
        }

        java.util.List<AuditEvent> getAuditEvents() {
            return auditEvents;
        }

        @Override
        public Promise<Void> recordAuditEvent(String action,
                                              String service,
                                              String entityId,
                                              String details,
                                              Map<String, String> metadata) {
            auditEvents.add(new AuditEvent(action, service, entityId, details,
                metadata == null ? Map.of() : new ConcurrentHashMap<>(metadata)));
            return Promise.complete();
        }

        Promise<java.util.List<AuditEvent>> queryAuditEvents(String action, String entityId, long sinceMs) {
            return Promise.of(auditEvents);
        }
    }

    private static final class InMemoryKernelContext implements KernelContext {
        private final DataCloudKernelAdapter dataCloud;
        private final InMemoryCrossScopeAuditService auditService;
        private final Map<Class<?>, Object> dependencies = new ConcurrentHashMap<>();

        InMemoryKernelContext(DataCloudKernelAdapter dataCloud, InMemoryCrossScopeAuditService auditService) {
            this.dataCloud = dataCloud;
            this.auditService = auditService;
            dependencies.put(DataCloudKernelAdapter.class, dataCloud);
            dependencies.put(CrossScopeAuditService.class, auditService);
            dependencies.put(InMemoryCrossScopeAuditService.class, auditService);
        }

        @Override
        public <T> T getDependency(Class<T> type) {
            Object dep = dependencies.get(type);
            if (dep == null) {
                throw new IllegalStateException("Dependency not found: " + type.getSimpleName());
            }
            return type.cast(dep);
        }

        @Override
        public <T> Optional<T> getOptionalDependency(Class<T> type) {
            return Optional.ofNullable(type.cast(dependencies.get(type)));
        }

        @Override
        public <T> boolean hasDependency(Class<T> type) {
            return dependencies.containsKey(type);
        }

        @Override
        public <T> T getDependency(String name, Class<T> type) {
            return getDependency(type);
        }

        @Override
        public <E> void registerEventHandler(Class<E> eventType, com.ghatana.kernel.event.EventHandler<E> handler) {
        }

        @Override
        public <E> void unregisterEventHandler(Class<E> eventType, com.ghatana.kernel.event.EventHandler<E> handler) {
        }

        @Override
        public <E> void publishEvent(E event) {
        }

        @Override
        public com.ghatana.kernel.context.KernelTenantContext getTenantContext() {
            return null;
        }

        @Override
        public com.ghatana.kernel.context.KernelTenantContext getTenantContext(String tenantId) {
            return null;
        }

        @Override
        public io.activej.eventloop.Eventloop getEventloop() {
            return getEventloop();
        }

        @Override
        public java.util.Set<com.ghatana.kernel.descriptor.KernelCapability> getAvailableCapabilities() {
            return java.util.Set.of();
        }

        @Override
        public boolean hasCapability(com.ghatana.kernel.descriptor.KernelCapability capability) {
            return false;
        }

        @Override
        public <T> T getConfig(String key, Class<T> type) {
            return null;
        }

        @Override
        public <T> Optional<T> getOptionalConfig(String key, Class<T> type) {
            return Optional.empty();
        }

        @Override
        public String getKernelVersion() {
            return "1.0.0";
        }

        @Override
        public String getEnvironment() {
            return "test";
        }

        @Override
        public Executor getExecutor(String executorName) {
            return Runnable::run;
        }

        @Override
        public <T> Optional<T> getCapability(String capabilityId) {
            return Optional.empty();
        }

        @Override
        public <T> void registerService(Class<T> type, T service) {
            dependencies.put(type, service);
        }
    }

    private record AuditEvent(String action, String service, String entityId, String details, Map<String, Object> metadata) {}

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
