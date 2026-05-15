package com.ghatana.integration.phrfinance;

import com.ghatana.kernel.adapter.datacloud.*;
import com.ghatana.kernel.bridge.port.BridgeContext;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class PhrFinanceTestFixtures {

    private PhrFinanceTestFixtures() {
    }

    static com.ghatana.kernel.context.KernelTenantContext createTestTenantContext() {
        com.ghatana.kernel.context.KernelTenantContext.SecurityContext securityContext = 
            new com.ghatana.kernel.context.KernelTenantContext.SecurityContext() {
                @Override
                public String getUserId() {
                    return "test-principal-123";
                }

                @Override
                public java.util.Set<String> getRoles() {
                    return java.util.Set.of("TEST_USER");
                }

                @Override
                public java.util.Set<String> getPermissions() {
                    return java.util.Set.of("READ", "WRITE");
                }

                @Override
                public boolean isAuthenticated() {
                    return true;
                }

                @Override
                public boolean hasRole(String role) {
                    return getRoles().contains(role);
                }

                @Override
                public boolean hasPermission(String permission) {
                    return getPermissions().contains(permission);
                }
            };

        return new com.ghatana.kernel.context.KernelTenantContext(
            "test-tenant",
            com.ghatana.kernel.context.KernelTenantContext.TenantType.STANDARD,
            Map.of("product", "phr-finance-test"),
            Set.of("billing", "ledger"),
            securityContext,
            Runnable::run
        );
    }

    static KernelContext createTestContext(DataCloudKernelAdapter dataCloud) {
        com.ghatana.kernel.context.KernelTenantContext tenantContext = createTestTenantContext();
        
        return new KernelContext() {
            @SuppressWarnings("unchecked")
            @Override
            public <T> T getDependency(Class<T> type) {
                if (DataCloudKernelAdapter.class.isAssignableFrom(type)) {
                    return (T) dataCloud;
                }
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
                                                 com.ghatana.kernel.event.EventHandler<E> handler) {
            }

            @Override
            public <E> void unregisterEventHandler(Class<E> eventType,
                                                   com.ghatana.kernel.event.EventHandler<E> handler) {
            }

            @Override
            public <E> void publishEvent(E event) {
            }

            @Override
            public com.ghatana.kernel.context.KernelTenantContext getTenantContext() {
                return tenantContext;
            }

            @Override
            public com.ghatana.kernel.context.KernelTenantContext getTenantContext(String tenantId) {
                return tenantContext;
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
            public <T> void registerService(Class<T> type, T service) {
            }
        };
    }

    static final class StubDataCloudAdapter implements DataCloudKernelAdapter {

        private record StoredEntry(byte[] data, Map<String, String> metadata) {
        }

        private final ConcurrentHashMap<String, StoredEntry> store = new ConcurrentHashMap<>();

        @Override
        public Promise<DataResult> readData(DataReadRequest request) {
            StoredEntry entry = store.get(storeKey(request.getDatasetId(), request.getRecordId()));
            if (entry == null) {
                return Promise.of(null);
            }
            return Promise.of(new DataResult(
                request.getRecordId(), entry.data(), Map.of(), System.currentTimeMillis()
            ));
        }

        @Override
        public Promise<Void> writeData(DataWriteRequest request) {
            store.put(
                storeKey(request.getDatasetId(), request.getRecordId()),
                new StoredEntry(request.getData(), Map.copyOf(request.getMetadata()))
            );
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
                .filter(entry -> entry.getKey().startsWith(prefix))
                .filter(entry -> matchesParams(entry.getValue().metadata(), params))
                .map(entry -> {
                    String recordId = entry.getKey().substring(prefix.length());
                    return new DataResult(recordId, entry.getValue().data(), Map.of(), System.currentTimeMillis());
                })
                .toList();
            return Promise.of(new QueryResult(results, results.size(), false));
        }

        private static boolean matchesParams(Map<String, String> metadata, Map<String, Object> params) {
            for (Map.Entry<String, Object> parameter : params.entrySet()) {
                String stored = metadata.get(parameter.getKey());
                if (stored == null || !stored.equals(String.valueOf(parameter.getValue()))) {
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
        public Promise<SchemaInfo> getSchema(BridgeContext context, String datasetId) {
            return Promise.of(null);
        }

        @Override
        public Promise<List<DatasetInfo>> listDatasets(BridgeContext context) {
            return Promise.of(List.of());
        }

        @Override
        public Promise<TransactionHandle> beginTransaction(BridgeContext context) {
            return Promise.of(null);
        }

        @Override
        public Promise<Void> commitTransaction(BridgeContext context, TransactionHandle handle) {
            return Promise.complete();
        }

        @Override
        public Promise<Void> rollbackTransaction(BridgeContext context, TransactionHandle handle) {
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
