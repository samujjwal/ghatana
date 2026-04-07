package com.ghatana.phr.kernel.service;

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
import com.ghatana.kernel.context.KernelContext;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared test infrastructure for PHR service unit tests.
 *
 * <p>Provides a reusable {@link StubDataCloudAdapter} (in-memory key-value store)
 * and a {@link #createTestContext(DataCloudKernelAdapter)} factory used by all
 * PHR service test classes.</p>
 *
 * @doc.type class
 * @doc.purpose Shared test fixtures for PHR service tests
 * @doc.layer product
 * @doc.pattern TestFixture
 */
final class PhrTestInfrastructure {

    private PhrTestInfrastructure() {}

    /**
     * Creates a minimal {@link KernelContext} wired to the given DataCloud adapter.
     *
     * @param dataCloud the stub or real DataCloud adapter
     * @return a test-scoped KernelContext
     */
    static KernelContext createTestContext(DataCloudKernelAdapter dataCloud) {
        return createTestContext(dataCloud, Map.of());
    }

    static KernelContext createTestContext(
            DataCloudKernelAdapter dataCloud,
            Map<Class<?>, Object> dependencies) {
        return new KernelContext() {
            @SuppressWarnings("unchecked")
            @Override
            public <T> T getDependency(Class<T> type) {
                Object dependency = dependencies.get(type);
                if (dependency != null) {
                    return (T) dependency;
                }
                if (DataCloudKernelAdapter.class.isAssignableFrom(type)) return (T) dataCloud;
                return null;
            }

            @Override
            public <T> java.util.Optional<T> getOptionalDependency(Class<T> type) {
                return java.util.Optional.ofNullable(getDependency(type));
            }

            @Override
            public <T> boolean hasDependency(Class<T> type) {
                return getDependency(type) != null;
            }

            @Override
            public <T> T getDependency(String name, Class<T> type) {
                return getDependency(type);
            }

            @Override
            public <E> void registerEventHandler(Class<E> eventType,
                    com.ghatana.kernel.event.EventHandler<E> handler) {}

            @Override
            public <E> void unregisterEventHandler(Class<E> eventType,
                    com.ghatana.kernel.event.EventHandler<E> handler) {}

            @Override
            public <E> void publishEvent(E event) {}

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
                return io.activej.eventloop.Eventloop.create();
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
            public <T> java.util.Optional<T> getOptionalConfig(String key, Class<T> type) {
                return java.util.Optional.empty();
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
            public java.util.concurrent.Executor getExecutor(String executorName) {
                return Runnable::run;
            }

            @Override
            public <T> java.util.Optional<T> getCapability(String capabilityId) {
                return java.util.Optional.empty();
            }

            @Override
            public <T> void registerService(Class<T> type, T service) {}
        };
    }

    /**
     * Minimal DataCloudKernelAdapter that stores data in memory.
     *
     * <p>Supports read, write, delete, and queryData with metadata-based filtering.
     * Records are stored alongside their metadata so equality filters in queries
     * match the actual stored values (e.g. {@code patientId = :patientId}).</p>
     */
    static class StubDataCloudAdapter implements DataCloudKernelAdapter {

        private record StoredEntry(byte[] data, Map<String, String> metadata) {}

        final ConcurrentHashMap<String, StoredEntry> store = new ConcurrentHashMap<>();

        @Override
        public Promise<DataResult> readData(DataReadRequest request) {
            StoredEntry entry = store.get(storeKey(request.getDatasetId(), request.getRecordId()));
            if (entry == null) return Promise.of(null);
            return Promise.of(new DataResult(
                    request.getRecordId(), entry.data(), Map.of(), System.currentTimeMillis()));
        }

        @Override
        public Promise<Void> writeData(DataWriteRequest request) {
            store.put(storeKey(request.getDatasetId(), request.getRecordId()),
                    new StoredEntry(request.getData(), Map.copyOf(request.getMetadata())));
            return Promise.complete();
        }

        @Override
        public Promise<Void> deleteData(DataDeleteRequest request) {
            store.remove(storeKey(request.getDatasetId(), request.getRecordId()));
            return Promise.complete();
        }

        @Override
        public Promise<QueryResult> queryData(DataQueryRequest request) {
            String prefix = request.getDatasetId() + ":";
            Map<String, Object> params = request.getParameters();
            List<DataResult> results = store.entrySet().stream()
                    .filter(e -> e.getKey().startsWith(prefix))
                    .filter(e -> matchesParams(e.getValue().metadata(), params))
                    .map(e -> {
                        String recordId = e.getKey().substring(prefix.length());
                        return new DataResult(recordId, e.getValue().data(), Map.of(), System.currentTimeMillis());
                    })
                    .toList();
            return Promise.of(new QueryResult(results, results.size(), false));
        }

        /** Checks that all query parameters match the stored metadata values. */
        private static boolean matchesParams(Map<String, String> metadata, Map<String, Object> params) {
            for (Map.Entry<String, Object> param : params.entrySet()) {
                String stored = metadata.get(param.getKey());
                if (stored == null || !stored.equals(String.valueOf(param.getValue()))) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public Promise<Void> createSchema(SchemaCreateRequest request) {
            return Promise.complete();
        }

        @Override
        public Promise<SchemaInfo> getSchema(String datasetId) {
            return Promise.of(null);
        }

        @Override
        public Promise<List<DatasetInfo>> listDatasets() {
            return Promise.of(List.of());
        }

        @Override
        public Promise<TransactionHandle> beginTransaction() {
            return Promise.of(null);
        }

        @Override
        public Promise<Void> commitTransaction(TransactionHandle handle) {
            return Promise.complete();
        }

        @Override
        public Promise<Void> rollbackTransaction(TransactionHandle handle) {
            return Promise.complete();
        }

        @Override
        public Promise<DataStream> openStream(DataStreamRequest request) {
            return Promise.of(null);
        }

        private static String storeKey(String dataset, String recordId) {
            return dataset + ":" + recordId;
        }
    }
}
