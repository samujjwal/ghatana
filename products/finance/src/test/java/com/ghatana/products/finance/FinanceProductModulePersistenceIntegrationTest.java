package com.ghatana.products.finance;

import com.ghatana.finance.ai.FinanceAiPersistenceTestSupport;
import com.ghatana.kernel.context.KernelTenantContext;
import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.event.EventHandler;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.eventloop.Eventloop;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @doc.type class
 * @doc.purpose Verifies finance product module starts a persistent AI runtime when DB config is present
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
class FinanceProductModulePersistenceIntegrationTest extends EventloopTestBase {

    private KernelContext context;

    private PostgreSQLContainer<?> postgres;

    @BeforeEach
    void setUp() {
        postgres = FinanceAiPersistenceTestSupport.startPostgres();
        context = new RecordingKernelContext(Map.ofEntries(
            Map.entry("finance.ai.persistence.enabled", true),
            Map.entry("finance.ai.database.jdbc-url", postgres.getJdbcUrl()),
            Map.entry("finance.ai.database.username", postgres.getUsername()),
            Map.entry("finance.ai.database.password", postgres.getPassword()),
            Map.entry("finance.ai.database.pool-name", "finance-product-module-pool"),
            Map.entry("finance.ai.database.minimum-idle", 1),
            Map.entry("finance.ai.database.maximum-pool-size", 4),
            Map.entry("finance.ai.database.connection-timeout-ms", 30_000L),
            Map.entry("finance.ai.database.idle-timeout-ms", 60_000L),
            Map.entry("finance.ai.database.max-lifetime-ms", 120_000L),
            Map.entry("finance.transaction.idempotency.persistence.enabled", true),
            Map.entry("finance.transaction.idempotency.database.jdbc-url", postgres.getJdbcUrl()),
            Map.entry("finance.transaction.idempotency.database.username", postgres.getUsername()),
            Map.entry("finance.transaction.idempotency.database.password", postgres.getPassword()),
            Map.entry("finance.transaction.idempotency.database.pool-name", "finance-product-module-transaction-pool"),
            Map.entry("finance.transaction.idempotency.database.minimum-idle", 1),
            Map.entry("finance.transaction.idempotency.database.maximum-pool-size", 4),
            Map.entry("finance.transaction.idempotency.database.connection-timeout-ms", 30_000L),
            Map.entry("finance.transaction.idempotency.database.idle-timeout-ms", 60_000L),
            Map.entry("finance.transaction.idempotency.database.max-lifetime-ms", 120_000L),
            Map.entry("finance.transaction.idempotency.ttl-hours", 24L)
        ));
    }

    @AfterEach
    void tearDown() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    @Test
    void startsPersistentAiRuntimeFromProductModuleConfig() throws Exception {
        FinanceProductModule module = new FinanceProductModule();
        module.initialize(context);
        runPromise(module::start);

        FinanceAiRuntimeService runtimeService = extractAiRuntime(module);
        runtimeService.registerModel(new com.ghatana.kernel.ai.ModelGovernanceService.ModelRegistration(
            "fraud-detection-v11",
            "Finance Fraud Model",
            "11.0.0",
            "classification",
            Map.of("jurisdiction", "NP")
        ));

        assertNotNull(runtimeService.getModelMetadata("fraud-detection-v11"));
        assertEquals("NP", runtimeService.getModelMetadata("fraud-detection-v11").getAttributes().get("jurisdiction"));

        FinanceTransactionRuntimeService transactionRuntimeService = extractTransactionRuntime(module);
        assertNotNull(transactionRuntimeService.getTransactionService());
        assertTrue(transactionRuntimeService.isPersistenceEnabled());
        assertTrue(transactionRuntimeService.isSharedRateLimitingEnabled());

        runPromise(module::stop);
    }

    private static FinanceAiRuntimeService extractAiRuntime(FinanceProductModule module) throws Exception {
        Field field = FinanceProductModule.class.getDeclaredField("aiRuntimeService");
        field.setAccessible(true);
        return (FinanceAiRuntimeService) field.get(module);
    }

    private static FinanceTransactionRuntimeService extractTransactionRuntime(FinanceProductModule module) throws Exception {
        Field field = FinanceProductModule.class.getDeclaredField("transactionRuntimeService");
        field.setAccessible(true);
        return (FinanceTransactionRuntimeService) field.get(module);
    }

    private static final class RecordingKernelContext implements KernelContext {
        private final Map<String, Object> configValues;
        private final Map<Class<?>, Object> services = new ConcurrentHashMap<>();

        private RecordingKernelContext(Map<String, Object> configValues) {
            this.configValues = configValues;
        }

        @Override
        public <T> T getDependency(Class<T> type) {
            return getOptionalDependency(type).orElseThrow(() ->
                new IllegalStateException("Dependency not found: " + type.getName())
            );
        }

        @Override
        public <T> Optional<T> getOptionalDependency(Class<T> type) {
            Object value = services.get(type);
            return type.isInstance(value) ? Optional.of(type.cast(value)) : Optional.empty();
        }

        @Override
        public <T> boolean hasDependency(Class<T> type) {
            return services.containsKey(type);
        }

        @Override
        public <T> T getDependency(String name, Class<T> type) {
            throw new IllegalStateException("Named dependency not found: " + name);
        }

        @Override
        public <E> void registerEventHandler(Class<E> eventType, EventHandler<E> handler) {
        }

        @Override
        public <E> void unregisterEventHandler(Class<E> eventType, EventHandler<E> handler) {
        }

        @Override
        public <E> void publishEvent(E event) {
        }

        @Override
        public KernelTenantContext getTenantContext() {
            return new KernelTenantContext("test", KernelTenantContext.TenantType.SYSTEM, Map.of(), Set.of(), null, null);
        }

        @Override
        public KernelTenantContext getTenantContext(String tenantId) {
            return getTenantContext();
        }

        @Override
        public Eventloop getEventloop() {
            return Eventloop.create();
        }

        @Override
        public Set<KernelCapability> getAvailableCapabilities() {
            return Set.of();
        }

        @Override
        public boolean hasCapability(KernelCapability capability) {
            return false;
        }

        @Override
        public <T> T getConfig(String key, Class<T> type) {
            return getOptionalConfig(key, type).orElseThrow(() -> new IllegalArgumentException("Config not found: " + key));
        }

        @Override
        public <T> Optional<T> getOptionalConfig(String key, Class<T> type) {
            Object value = configValues.get(key);
            return type.isInstance(value) ? Optional.of(type.cast(value)) : Optional.empty();
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
            return ForkJoinPool.commonPool();
        }

        @Override
        public <T> Optional<T> getCapability(String capabilityId) {
            return Optional.empty();
        }

        @Override
        public <T> void registerService(Class<T> type, T service) {
            services.put(type, service);
        }
    }
}
