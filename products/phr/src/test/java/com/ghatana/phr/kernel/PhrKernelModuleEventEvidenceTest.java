package com.ghatana.phr.kernel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter;
import com.ghatana.kernel.adapter.datacloud.DataDeleteRequest;
import com.ghatana.kernel.adapter.datacloud.DataQueryRequest;
import com.ghatana.kernel.adapter.datacloud.DataReadRequest;
import com.ghatana.kernel.adapter.datacloud.DataResult;
import com.ghatana.kernel.adapter.datacloud.DataStream;
import com.ghatana.kernel.adapter.datacloud.DataStreamRequest;
import com.ghatana.kernel.adapter.datacloud.DataWriteRequest;
import com.ghatana.kernel.adapter.datacloud.DatasetInfo;
import com.ghatana.kernel.adapter.datacloud.QueryResult;
import com.ghatana.kernel.adapter.datacloud.SchemaCreateRequest;
import com.ghatana.kernel.adapter.datacloud.SchemaInfo;
import com.ghatana.kernel.adapter.datacloud.TransactionHandle;
import com.ghatana.kernel.bridge.port.BridgeContext;
import com.ghatana.kernel.config.KernelConfigResolver;
import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.context.KernelTenantContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.event.EventHandler;
import com.ghatana.platform.cache.DistributedCachePort;
import com.ghatana.phr.kernel.event.PhrAuditEvent;
import com.ghatana.phr.kernel.event.PhrConsentEvent;
import com.ghatana.phr.kernel.event.PhrLifecycleEvent;
import io.activej.promise.Promise;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PhrKernelModule Event Evidence")
class PhrKernelModuleEventEvidenceTest extends EventloopTestBase {

    @Test
    @DisplayName("writes lifecycle, audit, and consent evidence to Data Cloud and invalidates consent cache")
    void writesEvidenceAndInvalidatesConsentCache() throws Exception {
        CapturingDataCloudAdapter dataCloudAdapter = new CapturingDataCloudAdapter();
        CapturingConsentCache consentCache = new CapturingConsentCache();
        CapturingKernelContext context = new CapturingKernelContext(dataCloudAdapter, consentCache);

        PhrKernelModule module = new PhrKernelModule();
        module.initialize(context);

        EventHandler<PhrLifecycleEvent> lifecycleHandler = context.handlerFor(PhrLifecycleEvent.class);
        EventHandler<PhrAuditEvent> auditHandler = context.handlerFor(PhrAuditEvent.class);
        EventHandler<PhrConsentEvent> consentHandler = context.handlerFor(PhrConsentEvent.class);

        lifecycleHandler.handle(PhrLifecycleEvent.builder()
                .eventId("evt-lifecycle-1")
                .productId("phr")
                .phase("verify")
                .status("passed")
                .runId("run-1")
                .correlationId("corr-1")
                .environment("prod")
                .tenantId("tenant-1")
                .timestamp(Instant.parse("2026-05-23T10:00:00Z"))
                .build());

        auditHandler.handle(PhrAuditEvent.builder()
                .eventId("evt-audit-1")
                .productId("phr")
                .auditType("patient-access")
                .action("read")
                .resourceType("patient")
                .resourceId("patient-1")
                .actorId("provider-1")
                .actorRole("PROVIDER")
                .tenantId("tenant-1")
                .patientId("patient-1")
                .metadata(Map.of("source", "api"))
                .correlationId("corr-2")
                .timestamp(Instant.parse("2026-05-23T10:05:00Z"))
                .build());

        consentHandler.handle(PhrConsentEvent.builder()
                .eventId("evt-consent-1")
                .productId("phr")
                .consentType("data-access")
                .action("revoked")
                .patientId("patient-1")
                .recipientId("provider-1")
                .resourceType("patient-record")
                .purpose("care")
                .tenantId("tenant-1")
                .metadata(Map.of("reason", "user-request"))
                .correlationId("corr-3")
                .timestamp(Instant.parse("2026-05-23T10:10:00Z"))
                .build());

        assertThat(dataCloudAdapter.requests)
                .hasSizeGreaterThanOrEqualTo(3);

        assertThat(dataCloudAdapter.requests.stream().map(DataWriteRequest::getDatasetId))
                .contains("phr.lifecycle.evidence", "phr.audit.evidence", "phr.consent.evidence");

        assertThat(consentCache.invalidatedKeys)
                .contains("patient-1:provider-1");

        DataWriteRequest lifecycleWrite = dataCloudAdapter.requests.stream()
                .filter(r -> "phr.lifecycle.evidence".equals(r.getDatasetId()))
                .findFirst()
                .orElseThrow();

        @SuppressWarnings("unchecked")
        Map<String, Object> lifecyclePayload = new ObjectMapper().readValue(lifecycleWrite.getData(), Map.class);
        assertThat(lifecyclePayload)
                .containsEntry("eventId", "evt-lifecycle-1")
                .containsEntry("eventType", "phr.lifecycle.phase-transition")
                .containsEntry("phase", "verify")
                .containsEntry("status", "passed");
    }

    private static final class CapturingKernelContext implements KernelContext {
        private final CapturingDataCloudAdapter dataCloudAdapter;
        private final CapturingConsentCache consentCache;
        private final ConcurrentHashMap<Class<?>, EventHandler<?>> handlers = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<Class<?>, Object> registeredServices = new ConcurrentHashMap<>();

        private CapturingKernelContext(CapturingDataCloudAdapter dataCloudAdapter, CapturingConsentCache consentCache) {
            this.dataCloudAdapter = dataCloudAdapter;
            this.consentCache = consentCache;
        }

        @SuppressWarnings("unchecked")
        <E> EventHandler<E> handlerFor(Class<E> type) {
            return (EventHandler<E>) handlers.get(type);
        }

        @Override
        public <T> T getDependency(Class<T> type) {
            if (type == KernelConfigResolver.class) {
                return type.cast(new KernelConfigResolver() {
                    @Override public <R> R resolve(String key, Class<R> configType, KernelTenantContext tenantContext) { return null; }
                    @Override public <R> R resolveWithDefault(String key, Class<R> configType, R defaultValue, KernelTenantContext tenantContext) { return defaultValue; }
                    @Override public <R> Optional<R> resolveOptional(String key, Class<R> configType, KernelTenantContext tenantContext) { return Optional.empty(); }
                    @Override public void addConfigProvider(ConfigProvider provider) { }
                    @Override public Promise<Void> reloadConfig(String tenantId) { return Promise.complete(); }
                    @Override public List<String> getAvailableKeys(KernelTenantContext tenantContext) { return List.of(); }
                });
            }
            if (type == DataCloudKernelAdapter.class) {
                return type.cast(dataCloudAdapter);
            }
            if (type == DistributedCachePort.class) {
                return type.cast(consentCache);
            }
            Object registered = registeredServices.get(type);
            if (registered != null) {
                return type.cast(registered);
            }
            throw new IllegalStateException("Dependency not found: " + type);
        }

        @Override
        public <T> Optional<T> getOptionalDependency(Class<T> type) {
            Object registered = registeredServices.get(type);
            return registered == null ? Optional.empty() : Optional.of(type.cast(registered));
        }

        @Override
        public <T> boolean hasDependency(Class<T> type) {
            return type == KernelConfigResolver.class
                    || type == DataCloudKernelAdapter.class
                    || type == DistributedCachePort.class
                    || registeredServices.containsKey(type);
        }

        @Override public <T> T getDependency(String name, Class<T> type) { return null; }

        @Override
        public <E> void registerEventHandler(Class<E> eventType, EventHandler<E> handler) {
            handlers.put(eventType, handler);
        }

        @Override public <E> void unregisterEventHandler(Class<E> eventType, EventHandler<E> handler) { handlers.remove(eventType); }
        @Override public <E> void publishEvent(E event) { }
        @Override public KernelTenantContext getTenantContext() { return null; }
        @Override public KernelTenantContext getTenantContext(String tenantId) { return null; }
        @Override public io.activej.eventloop.Eventloop getEventloop() { return io.activej.eventloop.Eventloop.create(); }
        @Override public Set<KernelCapability> getAvailableCapabilities() { return Set.of(); }
        @Override public boolean hasCapability(KernelCapability capability) { return false; }
        @Override public <T> T getConfig(String key, Class<T> type) { return null; }
        @Override
        public <T> Optional<T> getOptionalConfig(String key, Class<T> type) {
            if (type == String.class && "phr.notification.email.endpoint".equals(key)) {
                return Optional.of(type.cast("https://notifications.local/email"));
            }
            if (type == Long.class && "phr.notification.provider.timeoutMillis".equals(key)) {
                return Optional.of(type.cast(5000L));
            }
            return Optional.empty();
        }
        @Override public String getKernelVersion() { return "1.0.0"; }
        @Override public String getEnvironment() { return "test"; }
        @Override public Executor getExecutor(String executorName) { return Runnable::run; }
        @Override public <T> Optional<T> getCapability(String capabilityId) { return Optional.empty(); }

        @Override
        public <T> void registerService(Class<T> type, T service) {
            registeredServices.put(type, service);
        }
    }

    private static final class CapturingDataCloudAdapter implements DataCloudKernelAdapter {
        private final List<DataWriteRequest> requests = new ArrayList<>();

        @Override public Promise<DataResult> readData(DataReadRequest request) { return Promise.of(null); }
        @Override public Promise<Void> writeData(DataWriteRequest request) { requests.add(request); return Promise.complete(); }
        @Override public Promise<Void> deleteData(DataDeleteRequest request) { return Promise.complete(); }
        @Override public Promise<QueryResult> queryData(DataQueryRequest request) { return Promise.of(new QueryResult(List.of(), 0, false)); }
        @Override public Promise<Void> createSchema(SchemaCreateRequest request) { return Promise.complete(); }
        @Override public Promise<SchemaInfo> getSchema(BridgeContext context, String datasetId) { return Promise.of(null); }
        @Override public Promise<List<DatasetInfo>> listDatasets(BridgeContext context) { return Promise.of(List.of()); }
        @Override public Promise<TransactionHandle> beginTransaction(BridgeContext context) { return Promise.of(null); }
        @Override public Promise<Void> commitTransaction(BridgeContext context, TransactionHandle transaction) { return Promise.complete(); }
        @Override public Promise<Void> rollbackTransaction(BridgeContext context, TransactionHandle transaction) { return Promise.complete(); }
        @Override public Promise<DataStream> openStream(DataStreamRequest request) { return Promise.of(null); }
    }

    private static final class CapturingConsentCache implements DistributedCachePort<String, com.ghatana.phr.kernel.service.ConsentManagementService.ConsentCacheEntry> {
        private final List<String> invalidatedKeys = new ArrayList<>();

        @Override
        public Promise<Optional<com.ghatana.phr.kernel.service.ConsentManagementService.ConsentCacheEntry>> get(String key) {
            return Promise.of(Optional.empty());
        }

        @Override
        public Promise<Void> put(String key, com.ghatana.phr.kernel.service.ConsentManagementService.ConsentCacheEntry value) {
            return Promise.complete();
        }

        @Override
        public Promise<Void> put(String key, com.ghatana.phr.kernel.service.ConsentManagementService.ConsentCacheEntry value, Duration ttl) {
            return Promise.complete();
        }

        @Override
        public Promise<com.ghatana.phr.kernel.service.ConsentManagementService.ConsentCacheEntry> getOrLoad(
                String key,
                Function<String, Promise<com.ghatana.phr.kernel.service.ConsentManagementService.ConsentCacheEntry>> loader) {
            return loader.apply(key);
        }

        @Override
        public Promise<Void> invalidate(String key) {
            invalidatedKeys.add(key);
            return Promise.complete();
        }

        @Override
        public Promise<Void> invalidateAll() {
            invalidatedKeys.add("*");
            return Promise.complete();
        }

        @Override
        public boolean isHealthy() {
            return true;
        }
    }
}
