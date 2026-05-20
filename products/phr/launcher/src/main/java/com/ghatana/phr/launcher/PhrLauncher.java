package com.ghatana.phr.launcher;

import com.ghatana.core.activej.http.HttpServerBinding;
import com.ghatana.core.activej.http.HttpServerBindingFactory;
import com.ghatana.core.activej.promise.PromiseUtils;
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
import com.ghatana.kernel.config.HierarchicalKernelConfigResolver;
import com.ghatana.kernel.config.KernelConfigResolver;
import com.ghatana.kernel.context.DefaultKernelContext;
import com.ghatana.kernel.context.KernelTenantContext;
import com.ghatana.kernel.registry.KernelRegistry;
import com.ghatana.kernel.contracts.ContractRegistry;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.policy.BoundaryPolicyRuntimeGuards;
import com.ghatana.kernel.registry.KernelRegistryImpl;
import com.ghatana.phr.api.PhrHttpServer;
import com.ghatana.phr.kernel.PhrKernelModule;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Standalone launcher for the PHR kernel module.
 *
 * <h2>HTTP Server Configuration</h2>
 * <ul>
 *   <li>Default port: 8080 (configurable via {@code PHR_HTTP_PORT} environment variable or {@code --port} argument)</li>
 *   <li>Health probe: {@code GET /health} - returns 200 when service is healthy</li>
 *   <li>Readiness probe: {@code GET /ready} - returns 200 when service is ready to accept traffic</li>
 * </ul>
 *
 * <h2>API Endpoints</h2>
 * <ul>
 *   <li>POST {@code /phr/billing/encounters} - Create billing encounter</li>
 *   <li>POST {@code /phr/billing/encounters/:encounterId/close} - Close billing encounter</li>
 *   <li>POST {@code /fhir/:resourceType} - Create FHIR resource</li>
 *   <li>GET {@code /fhir/:resourceType/:id} - Get FHIR resource by ID</li>
 *   <li>GET {@code /fhir/:resourceType} - Search FHIR resources</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Standalone launcher for the PHR kernel module
 * @doc.layer launcher
 * @doc.pattern Main Entry Point
 */
public final class PhrLauncher {

    private static final Logger log = LoggerFactory.getLogger(PhrLauncher.class);
    private static final Duration START_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration STOP_TIMEOUT = Duration.ofSeconds(30);
    private static final int DEFAULT_HTTP_PORT = 8080;

    private PhrLauncher() {
    }

    public static void main(String[] args) {
        PhrLauncherConfig config = PhrLauncherConfig.from(args);
        PhrKernelModule module = new PhrKernelModule();
        HttpServerBinding httpBinding = null;

        try {
            DefaultKernelContext context = createContext(config.environment());
            module.initialize(context);
            assertRuntimeDependencies(context);
            await("phr startup", module.start(), START_TIMEOUT);

            // Create and start HTTP server binding with explicit port configuration
            Eventloop eventloop = context.getDependency(Eventloop.class);
            PhrHttpServer phrHttpServer = context.getDependency(PhrHttpServer.class);
            httpBinding = new HttpServerBindingFactory()
                    .withServiceName("phr")
                    .withPort(config.httpPort())
                    .withHost(config.httpHost())
                    .build(eventloop, phrHttpServer.getServlet());
            await("phr http binding", httpBinding.start(), START_TIMEOUT);

            HttpServerBinding bindingForShutdown = httpBinding;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                stopQuietly(bindingForShutdown);
                stopQuietly(module);
            }, "phr-shutdown"));

            log.info("PHR launcher started: environment={}, http={}:{}", 
                config.environment(), httpBinding.getHost(), httpBinding.getPort());
            Thread.currentThread().join();
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            stopQuietly(httpBinding);
            stopQuietly(module);
            log.info("PHR launcher interrupted");
        } catch (Exception exception) {
            stopQuietly(httpBinding);
            stopQuietly(module);
            log.error("PHR launcher failed", exception);
            System.exit(1);
        }
    }

    private static DefaultKernelContext createContext(String environment) {
        KernelRegistryImpl registry = new KernelRegistryImpl();
        registerBaselineCapabilities(registry);

        HierarchicalKernelConfigResolver configResolver = new HierarchicalKernelConfigResolver();
        configResolver.addConfigProvider(new EnvironmentConfigProvider());

        Eventloop eventloop = Eventloop.create();
        KernelTenantContext localTenant = createLocalTenantContext(ForkJoinPool.commonPool());
        DefaultKernelContext context = new LocalLauncherKernelContext(
            registry,
            configResolver,
            eventloop,
            "1.0.0",
            environment,
            localTenant
        );
        context.registerTenantContext(localTenant.getTenantId(), localTenant);
        context.registerDependency(KernelConfigResolver.class, configResolver);
        context.registerDependency(ContractRegistry.class, new ContractRegistry());
        context.registerDependency(Eventloop.class, eventloop);
        context.registerDependency(DataCloudKernelAdapter.class, new LocalDataCloudKernelAdapter());
        return context;
    }

    static void assertRuntimeDependencies(DefaultKernelContext context) {
        BoundaryPolicyRuntimeGuards.assertStoreAllowed(context, "products/phr/launcher");
    }

    private static void registerBaselineCapabilities(KernelRegistryImpl registry) {
        registry.registerCapability(KernelCapability.Core.USER_AUTHENTICATION);
        registry.registerCapability(KernelCapability.Core.DATA_STORAGE);
        registry.registerCapability(KernelCapability.Core.API_FRAMEWORK);
        registry.registerCapability(KernelCapability.Core.WORKFLOW_ENGINE);
    }

    private static void stopQuietly(PhrKernelModule module) {
        try {
            await("phr shutdown", module.stop(), STOP_TIMEOUT);
        } catch (Exception exception) {
            log.warn("PHR shutdown encountered an error", exception);
        }
    }

    private static void stopQuietly(HttpServerBinding binding) {
        if (binding == null) {
            return;
        }
        try {
            await("phr http shutdown", binding.stop(), STOP_TIMEOUT);
        } catch (Exception exception) {
            log.warn("PHR HTTP server shutdown encountered an error", exception);
        }
    }

    private static void await(String operation, Promise<Void> promise, Duration timeout) throws Exception {
        PromiseUtils.toCompletableFuture(promise).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        log.info("Completed {}", operation);
    }

    private static KernelTenantContext createLocalTenantContext(Executor executor) {
        return new KernelTenantContext(
            "phr-local",
            KernelTenantContext.TenantType.STANDARD,
            Map.of("product", "phr"),
            Set.of("phr.local.lifecycle"),
            new LocalSecurityContext(),
            executor
        );
    }

    private static final class LocalLauncherKernelContext extends DefaultKernelContext {

        private final KernelTenantContext localTenant;

        private LocalLauncherKernelContext(
                KernelRegistry registry,
                KernelConfigResolver configResolver,
                Eventloop eventloop,
                String kernelVersion,
                String environment,
                KernelTenantContext localTenant) {
            super(registry, configResolver, eventloop, kernelVersion, environment);
            this.localTenant = localTenant;
        }

        @Override
        public KernelTenantContext getTenantContext() {
            return localTenant;
        }
    }

    private static final class LocalSecurityContext implements KernelTenantContext.SecurityContext {

        @Override
        public String getUserId() {
            return "phr-local-launcher";
        }

        @Override
        public Set<String> getRoles() {
            return Set.of("system", "phr-local");
        }

        @Override
        public Set<String> getPermissions() {
            return Set.of("phr:read", "phr:write", "phr:admin");
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
    }

    private record StoredData(byte[] data, Map<String, String> metadata) {
    }

    private static final class LocalDataCloudKernelAdapter implements DataCloudKernelAdapter {

        private final ConcurrentHashMap<String, StoredData> records = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, SchemaInfo> schemas = new ConcurrentHashMap<>();

        @Override
        public Promise<DataResult> readData(DataReadRequest request) {
            StoredData stored = records.get(storeKey(request.getDatasetId(), request.getRecordId()));
            if (stored == null) {
                return Promise.of((DataResult) null);
            }
            return Promise.of(new DataResult(
                request.getRecordId(),
                stored.data(),
                stored.metadata(),
                System.currentTimeMillis()
            ));
        }

        @Override
        public Promise<Void> writeData(DataWriteRequest request) {
            Map<String, String> metadata = new HashMap<>(request.getMetadata());
            metadata.putIfAbsent("tenantId", "local");
            metadata.putIfAbsent("principalId", "phr-local-launcher");
            records.put(
                storeKey(request.getDatasetId(), request.getRecordId()),
                new StoredData(request.getData(), Map.copyOf(metadata))
            );
            return Promise.complete();
        }

        @Override
        public Promise<Void> deleteData(DataDeleteRequest request) {
            records.remove(storeKey(request.getDatasetId(), request.getRecordId()));
            return Promise.complete();
        }

        @Override
        public Promise<QueryResult> queryData(DataQueryRequest request) {
            String prefix = request.getDatasetId() + ":";
            Map<String, Object> parameters = request.getParameters();
            List<DataResult> results = records.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(prefix))
                .filter(entry -> metadataMatches(entry.getValue().metadata(), parameters))
                .map(entry -> {
                    String recordId = entry.getKey().substring(prefix.length());
                    StoredData stored = entry.getValue();
                    return new DataResult(recordId, stored.data(), stored.metadata(), System.currentTimeMillis());
                })
                .toList();
            return Promise.of(new QueryResult(results, results.size(), false));
        }

        @Override
        public Promise<Void> createSchema(SchemaCreateRequest request) {
            long now = System.currentTimeMillis();
            schemas.put(request.getDatasetId(), new SchemaInfo(request.getDatasetId(), request.getSchema(), now, now));
            return Promise.complete();
        }

        @Override
        public Promise<SchemaInfo> getSchema(BridgeContext context, String datasetId) {
            return Promise.of(schemas.get(datasetId));
        }

        @Override
        public Promise<List<DatasetInfo>> listDatasets(BridgeContext context) {
            List<DatasetInfo> datasets = schemas.values().stream()
                .map(schema -> new DatasetInfo(
                    schema.getDatasetId(),
                    schema.getDatasetId(),
                    "Local PHR dataset",
                    countRecords(schema.getDatasetId()),
                    schema.getCreatedAt()
                ))
                .toList();
            return Promise.of(datasets);
        }

        @Override
        public Promise<TransactionHandle> beginTransaction(BridgeContext context) {
            return Promise.of((TransactionHandle) null);
        }

        @Override
        public Promise<Void> commitTransaction(BridgeContext context, TransactionHandle transaction) {
            return Promise.complete();
        }

        @Override
        public Promise<Void> rollbackTransaction(BridgeContext context, TransactionHandle transaction) {
            return Promise.complete();
        }

        @Override
        public Promise<DataStream> openStream(DataStreamRequest request) {
            return Promise.of((DataStream) null);
        }

        private long countRecords(String datasetId) {
            String prefix = datasetId + ":";
            return records.keySet().stream().filter(key -> key.startsWith(prefix)).count();
        }

        private static boolean metadataMatches(Map<String, String> metadata, Map<String, Object> parameters) {
            for (Map.Entry<String, Object> parameter : parameters.entrySet()) {
                String storedValue = metadata.get(parameter.getKey());
                if (storedValue == null || !storedValue.equals(String.valueOf(parameter.getValue()))) {
                    return false;
                }
            }
            return true;
        }

        private static String storeKey(String datasetId, String recordId) {
            return datasetId + ":" + recordId;
        }
    }

    private record PhrLauncherConfig(String environment, int httpPort, String httpHost) {

        private static PhrLauncherConfig from(String[] args) {
            String environment = readOption(args, "--environment")
                .or(() -> readOption(args, "--env"))
                .or(() -> readValue("PHR_ENVIRONMENT"))
                .or(() -> readValue("GHATANA_ENVIRONMENT"))
                .orElse("local");
            
            int port = readOption(args, "--port")
                .or(() -> readValue("PHR_HTTP_PORT"))
                .map(Integer::parseInt)
                .orElse(DEFAULT_HTTP_PORT);
            
            String host = readOption(args, "--host")
                .or(() -> readValue("PHR_HTTP_HOST"))
                .orElse("0.0.0.0");
            
            return new PhrLauncherConfig(environment, port, host);
        }

        private static Optional<String> readOption(String[] args, String option) {
            for (int index = 0; index < args.length - 1; index++) {
                if (option.equals(args[index])) {
                    String value = args[index + 1];
                    if (!value.isBlank()) {
                        return Optional.of(value);
                    }
                }
            }
            return Optional.empty();
        }

        private static Optional<String> readValue(String key) {
            return EnvironmentConfigProvider.readValue(key);
        }
    }

    private static final class EnvironmentConfigProvider implements KernelConfigResolver.ConfigProvider {

        @Override
        public <T> Optional<T> get(String key, Class<T> type, KernelTenantContext context) {
            return readValue(key).flatMap(value -> coerce(value, type));
        }

        @Override
        public int getPriority() {
            return 100;
        }

        @Override
        public String getName() {
            return "environment";
        }

        private static Optional<String> readValue(String key) {
            String property = System.getProperty(key);
            if (property != null && !property.isBlank()) {
                return Optional.of(property);
            }

            String envKey = key.toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_');
            String environment = System.getenv(envKey);
            if (environment != null && !environment.isBlank()) {
                return Optional.of(environment);
            }

            return Optional.empty();
        }

        private static <T> Optional<T> coerce(String value, Class<T> type) {
            Object converted;

            if (type == String.class) {
                converted = value;
            } else if (type == Boolean.class) {
                converted = Boolean.parseBoolean(value);
            } else if (type == Integer.class) {
                converted = Integer.parseInt(value);
            } else if (type == Long.class) {
                converted = Long.parseLong(value);
            } else if (type == Double.class) {
                converted = Double.parseDouble(value);
            } else if (type == Duration.class) {
                converted = Duration.parse(value);
            } else {
                return Optional.empty();
            }

            return Optional.of(type.cast(converted));
        }
    }
}
