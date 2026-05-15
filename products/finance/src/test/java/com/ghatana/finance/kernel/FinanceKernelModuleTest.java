package com.ghatana.finance.kernel;

import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter;
import com.ghatana.kernel.adapter.datacloud.DataDeleteRequest;
import com.ghatana.kernel.adapter.datacloud.DataQueryRequest;
import com.ghatana.kernel.adapter.datacloud.DataReadRequest;
import com.ghatana.kernel.adapter.datacloud.DataResult;
import com.ghatana.kernel.adapter.datacloud.DatasetInfo;
import com.ghatana.kernel.adapter.datacloud.DataStream;
import com.ghatana.kernel.adapter.datacloud.DataStreamRequest;
import com.ghatana.kernel.adapter.datacloud.DataWriteRequest;
import com.ghatana.kernel.adapter.datacloud.QueryResult;
import com.ghatana.kernel.adapter.datacloud.SchemaCreateRequest;
import com.ghatana.kernel.adapter.datacloud.SchemaInfo;
import com.ghatana.kernel.adapter.datacloud.TransactionHandle;
import com.ghatana.kernel.config.KernelConfigResolver;
import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.platform.health.HealthStatus;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link FinanceKernelModule}.
 *
 * @doc.type test
 * @doc.purpose Unit tests for Finance kernel module with 8 services
 * @doc.layer test
 * @author Ghatana Kernel Team
 */
@DisplayName("FinanceKernelModule Tests")
class FinanceKernelModuleTest extends EventloopTestBase {

    private FinanceKernelModule module;
    private KernelContext mockContext;

    @BeforeEach
    void setUp() {
        module = new FinanceKernelModule();
        mockContext = createMockContext();
    }

    @Test
    @DisplayName("Should return correct module metadata")
    void shouldReturnCorrectModuleMetadata() {
        assertEquals("finance-core", module.getModuleId());
        assertEquals("1.0.0", module.getVersion());
    }

    @Test
    @DisplayName("Should declare correct capabilities")
    void shouldDeclareCorrectCapabilities() {
        Set<KernelCapability> capabilities = module.getCapabilities();

        // Should have 10 capabilities (5 Finance-specific + 5 shared)
        assertEquals(10, capabilities.size());

        // Finance-specific capabilities
        assertTrue(capabilities.contains(FinanceCapabilities.TRADE_PROCESSING));
        assertTrue(capabilities.contains(FinanceCapabilities.RISK_MANAGEMENT));
        assertTrue(capabilities.contains(FinanceCapabilities.COMPLIANCE_CHECKING));
        assertTrue(capabilities.contains(FinanceCapabilities.LEDGER_MANAGEMENT));
        assertTrue(capabilities.contains(FinanceCapabilities.PORTFOLIO_MANAGEMENT));

        // Shared capabilities
        assertTrue(capabilities.contains(KernelCapability.Core.USER_AUTHENTICATION));
        assertTrue(capabilities.contains(KernelCapability.Core.DATA_STORAGE));
        assertTrue(capabilities.contains(KernelCapability.Core.API_FRAMEWORK));
        assertTrue(capabilities.contains(KernelCapability.Core.WORKFLOW_ENGINE));
        assertTrue(capabilities.contains(KernelCapability.Core.OBSERVABILITY_FRAMEWORK));
    }

    @Test
    @DisplayName("Should declare correct dependencies")
    void shouldDeclareCorrectDependencies() {
        Set<KernelDependency> dependencies = module.getDependencies();

        // Should have 5 dependencies
        assertEquals(5, dependencies.size());

        // Check for required dependencies
        assertTrue(dependencies.stream().anyMatch(d -> d.getDependencyId().equals("kernel-core")));
        assertTrue(dependencies.stream().anyMatch(d -> d.getDependencyId().equals("event-processing")));
        assertTrue(dependencies.stream().anyMatch(d -> d.getDependencyId().equals("ai-ml-framework")));

        // Check external services (all should be non-optional for finance)
        assertTrue(dependencies.stream().anyMatch(d -> d.getDependencyId().equals("market-data-feed")));
        assertTrue(dependencies.stream().anyMatch(d -> d.getDependencyId().equals("risk-models")));
    }

    @Test
    @DisplayName("Should initialize successfully")
    void shouldInitializeSuccessfully() {
        assertDoesNotThrow(() -> module.initialize(mockContext));

        // Second initialization should throw
        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> module.initialize(mockContext));
        assertTrue(exception.getMessage().contains("already initialized"));
    }

    @Test
    @DisplayName("Should throw when ConfigResolver not available during init")
    void shouldThrowWhenConfigResolverNotAvailableDuringInit() {
        KernelContext contextWithoutConfig = createMockContextWithoutConfig();

        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> module.initialize(contextWithoutConfig));
        assertTrue(exception.getMessage().contains("KernelConfigResolver"));
    }

    @Test
    @DisplayName("Should start successfully after initialization")
    void shouldStartSuccessfullyAfterInitialization() {
        module.initialize(mockContext);

        assertDoesNotThrow(() -> runPromise(module::start));
    }

    @Test
    @DisplayName("Should fail to start when not initialized")
    void shouldFailToStartWhenNotInitialized() {
        assertThrows(Exception.class, () -> runPromise(module::start));
    }

    @Test
    @DisplayName("Should stop successfully")
    void shouldStopSuccessfully() {
        module.initialize(mockContext);
        runPromise(module::start);

        assertDoesNotThrow(() -> runPromise(module::stop));
    }

    @Test
    @DisplayName("Should report healthy status when initialized and started")
    void shouldReportHealthyStatusWhenInitializedAndStarted() {
        module.initialize(mockContext);
        runPromise(module::start);

        HealthStatus status = module.getHealthStatus();

        assertEquals(HealthStatus.Status.HEALTHY, status.getStatus());
        assertTrue(status.getMessage().contains("operational"));
        assertEquals(6, status.getChecks().size());
    }

    @Test
    @DisplayName("Should report unhealthy status when not initialized")
    void shouldReportUnhealthyStatusWhenNotInitialized() {
        HealthStatus status = module.getHealthStatus();

        assertEquals(HealthStatus.Status.UNHEALTHY, status.getStatus());
        assertTrue(status.getMessage().contains("not initialized"));
    }

    @Test
    @DisplayName("Should report all service health checks")
    void shouldReportAllServiceHealthChecks() {
        module.initialize(mockContext);
        runPromise(module::start);

        HealthStatus status = module.getHealthStatus();

        // Verify all currently initialized Finance services are checked
        assertTrue(status.getChecks().containsKey("order-management"));
        assertTrue(status.getChecks().containsKey("risk-management"));
        assertTrue(status.getChecks().containsKey("compliance"));

        // All should be healthy after start
        status.getChecks().values().forEach(check ->
            assertEquals(HealthStatus.Status.HEALTHY, check.getStatus())
        );
    }

    @Test
    @DisplayName("Should handle concurrent start/stop operations safely")
    void shouldHandleConcurrentStartStopOperationsSafely() {
        module.initialize(mockContext);

        // Multiple operations should be safe
        runPromise(module::start);
        runPromise(module::start);
        runPromise(module::stop);
        runPromise(module::start);

        // Health should be healthy after final start
        HealthStatus status = module.getHealthStatus();
        assertEquals(HealthStatus.Status.HEALTHY, status.getStatus());
    }

    // ==================== Test Helpers ====================

    private KernelContext createMockContext() {
        DataCloudKernelAdapter dataCloudAdapter = new InMemoryDataCloudKernelAdapter();
        return new KernelContext() {
            @Override public <T> T getDependency(Class<T> type) {
                if (type == KernelConfigResolver.class) {
                    return type.cast(new KernelConfigResolver() {
                        @Override public <R> R resolve(String key, Class<R> type, com.ghatana.kernel.context.KernelTenantContext tenantContext) { return null; }
                        @Override public <R> R resolveWithDefault(String key, Class<R> type, R defaultValue, com.ghatana.kernel.context.KernelTenantContext tenantContext) { return defaultValue; }
                        @Override public <R> java.util.Optional<R> resolveOptional(String key, Class<R> type, com.ghatana.kernel.context.KernelTenantContext tenantContext) { return java.util.Optional.empty(); }
                        @Override public void addConfigProvider(com.ghatana.kernel.config.KernelConfigResolver.ConfigProvider provider) {}
                        @Override public java.util.List<String> getAvailableKeys(com.ghatana.kernel.context.KernelTenantContext tenantContext) { return java.util.List.of(); }
                        @Override public io.activej.promise.Promise<Void> reloadConfig(String tenantId) { return io.activej.promise.Promise.complete(); }
                    });
                }
                if (type == DataCloudKernelAdapter.class) {
                    return type.cast(dataCloudAdapter);
                }
                throw new IllegalStateException("Dependency not found: " + type);
            }
            @Override public <T> java.util.Optional<T> getOptionalDependency(Class<T> type) {
                return java.util.Optional.empty();
            }
            @Override public <T> boolean hasDependency(Class<T> type) {
                return type == KernelConfigResolver.class || type == DataCloudKernelAdapter.class;
            }
            @Override public <T> T getDependency(String name, Class<T> type) { return null; }
            @Override public <E> void registerEventHandler(Class<E> eventType, com.ghatana.kernel.event.EventHandler<E> handler) {}
            @Override public <E> void unregisterEventHandler(Class<E> eventType, com.ghatana.kernel.event.EventHandler<E> handler) {}
            @Override public <E> void publishEvent(E event) {}
            @Override public com.ghatana.kernel.context.KernelTenantContext getTenantContext() { return null; }
            @Override public com.ghatana.kernel.context.KernelTenantContext getTenantContext(String tenantId) { return null; }
            @Override public io.activej.eventloop.Eventloop getEventloop() { return io.activej.eventloop.Eventloop.create(); }
            @Override public java.util.Set<KernelCapability> getAvailableCapabilities() { return java.util.Set.of(); }
            @Override public boolean hasCapability(KernelCapability capability) { return false; }
            @Override public <T> T getConfig(String key, Class<T> type) { return null; }
            @Override public <T> java.util.Optional<T> getOptionalConfig(String key, Class<T> type) { return java.util.Optional.empty(); }
            @Override public String getKernelVersion() { return "1.0.0"; }
            @Override public String getEnvironment() { return "test"; }
            @Override public java.util.concurrent.Executor getExecutor(String executorName) { return java.util.concurrent.Executors.newSingleThreadExecutor(); }
            @Override public <T> java.util.Optional<T> getCapability(String capabilityId) { return java.util.Optional.empty(); }
            @Override public <T> void registerService(Class<T> type, T service) {}
        };
    }

    private KernelContext createMockContextWithoutConfig() {
        return new KernelContext() {
            @Override public <T> T getDependency(Class<T> type) { throw new IllegalStateException("Not found"); }
            @Override public <T> java.util.Optional<T> getOptionalDependency(Class<T> type) { return java.util.Optional.empty(); }
            @Override public <T> boolean hasDependency(Class<T> type) { return false; }
            @Override public <T> T getDependency(String name, Class<T> type) { return null; }
            @Override public <E> void registerEventHandler(Class<E> eventType, com.ghatana.kernel.event.EventHandler<E> handler) {}
            @Override public <E> void unregisterEventHandler(Class<E> eventType, com.ghatana.kernel.event.EventHandler<E> handler) {}
            @Override public <E> void publishEvent(E event) {}
            @Override public com.ghatana.kernel.context.KernelTenantContext getTenantContext() { return null; }
            @Override public com.ghatana.kernel.context.KernelTenantContext getTenantContext(String tenantId) { return null; }
            @Override public io.activej.eventloop.Eventloop getEventloop() { return io.activej.eventloop.Eventloop.create(); }
            @Override public java.util.Set<KernelCapability> getAvailableCapabilities() { return java.util.Set.of(); }
            @Override public boolean hasCapability(KernelCapability capability) { return false; }
            @Override public <T> T getConfig(String key, Class<T> type) { return null; }
            @Override public <T> java.util.Optional<T> getOptionalConfig(String key, Class<T> type) { return java.util.Optional.empty(); }
            @Override public String getKernelVersion() { return "1.0.0"; }
            @Override public String getEnvironment() { return "test"; }
            @Override public java.util.concurrent.Executor getExecutor(String executorName) { return java.util.concurrent.Executors.newSingleThreadExecutor(); }
            @Override public <T> java.util.Optional<T> getCapability(String capabilityId) { return java.util.Optional.empty(); }
            @Override public <T> void registerService(Class<T> type, T service) {}
        };
    }

    private static final class InMemoryDataCloudKernelAdapter implements DataCloudKernelAdapter {
        private final java.util.Map<String, java.util.Map<String, byte[]>> datasets = new java.util.concurrent.ConcurrentHashMap<>();

        @Override public Promise<DataResult> readData(DataReadRequest request) {
            byte[] data = datasets.getOrDefault(request.getDatasetId(), java.util.Map.of()).get(request.getRecordId());
            return Promise.of(new DataResult(request.getRecordId(), data, java.util.Map.of(), System.currentTimeMillis()));
        }

        @Override public Promise<Void> writeData(DataWriteRequest request) {
            datasets.computeIfAbsent(request.getDatasetId(), ignored -> new java.util.concurrent.ConcurrentHashMap<>())
                .put(request.getRecordId(), request.getData());
            return Promise.complete();
        }

        @Override public Promise<Void> deleteData(DataDeleteRequest request) {
            java.util.Map<String, byte[]> dataset = datasets.get(request.getDatasetId());
            if (dataset != null) {
                dataset.remove(request.getRecordId());
            }
            return Promise.complete();
        }

        @Override public Promise<QueryResult> queryData(DataQueryRequest request) {
            java.util.List<DataResult> results = datasets.getOrDefault(request.getDatasetId(), java.util.Map.of())
                .entrySet()
                .stream()
                .map(entry -> new DataResult(entry.getKey(), entry.getValue(), java.util.Map.of(), System.currentTimeMillis()))
                .toList();
            return Promise.of(new QueryResult(results, results.size(), false));
        }

        @Override public Promise<Void> createSchema(SchemaCreateRequest request) {
            datasets.computeIfAbsent(request.getDatasetId(), ignored -> new java.util.concurrent.ConcurrentHashMap<>());
            return Promise.complete();
        }

        @Override public Promise<SchemaInfo> getSchema(com.ghatana.kernel.bridge.port.BridgeContext context, String datasetId) {
            return Promise.of(new SchemaInfo(datasetId, java.util.Map.of(), System.currentTimeMillis(), System.currentTimeMillis()));
        }

        @Override public Promise<java.util.List<DatasetInfo>> listDatasets(com.ghatana.kernel.bridge.port.BridgeContext context) {
            return Promise.of(datasets.keySet().stream()
                .map(datasetId -> new DatasetInfo(datasetId, datasetId, "test dataset", 0L, System.currentTimeMillis()))
                .toList());
        }

        @Override public Promise<TransactionHandle> beginTransaction(com.ghatana.kernel.bridge.port.BridgeContext context) {
            return Promise.of(new TransactionHandle() {
                @Override public String getId() { return "tx-1"; }
                @Override public boolean isActive() { return true; }
            });
        }

        @Override public Promise<Void> commitTransaction(com.ghatana.kernel.bridge.port.BridgeContext context, TransactionHandle handle) {
            return Promise.complete();
        }

        @Override public Promise<Void> rollbackTransaction(com.ghatana.kernel.bridge.port.BridgeContext context, TransactionHandle handle) {
            return Promise.complete();
        }

        @Override public Promise<DataStream> openStream(DataStreamRequest request) {
            return Promise.ofException(new UnsupportedOperationException("Not needed for tests"));
        }
    }
}
