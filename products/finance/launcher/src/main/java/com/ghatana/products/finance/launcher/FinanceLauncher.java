package com.ghatana.products.finance.launcher;

import com.ghatana.core.activej.http.HttpServerBinding;
import com.ghatana.core.activej.http.HttpServerBindingFactory;
import com.ghatana.core.activej.promise.PromiseUtils;
import com.ghatana.kernel.config.HierarchicalKernelConfigResolver;
import com.ghatana.kernel.config.KernelConfigResolver;
import com.ghatana.kernel.context.DefaultKernelContext;
import com.ghatana.kernel.context.KernelTenantContext;
import com.ghatana.kernel.contracts.ContractRegistry;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.registry.KernelRegistryImpl;
import com.ghatana.products.finance.FinanceProductModule;
import com.ghatana.products.finance.http.FinanceHttpServer;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Standalone launcher for the Finance product module.
 *
 * <h2>HTTP Server Configuration</h2>
 * <ul>
 *   <li>Default port: 8081 (configurable via {@code FINANCE_HTTP_PORT} environment variable or {@code --port} argument)</li>
 *   <li>Health probe: {@code GET /health} - returns 200 when service is healthy</li>
 *   <li>Readiness probe: {@code GET /ready} - returns 200 when service is ready to accept traffic</li>
 * </ul>
 *
 * <h2>API Endpoints</h2>
 * <ul>
 *   <li>POST {@code /ledger/postings} - Post ledger transaction</li>
 *   <li>GET {@code /ledger/postings/:entryId} - Get ledger posting status</li>
 *   <li>POST {@code /transactions} - Submit and process transaction</li>
 *   <li>GET {@code /transactions/:id} - Retrieve transaction by ID</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Standalone launcher for the Finance product module
 * @doc.layer launcher
 * @doc.pattern Main Entry Point
 */
public final class FinanceLauncher {

    private static final Logger log = LoggerFactory.getLogger(FinanceLauncher.class);
    private static final Duration START_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration STOP_TIMEOUT = Duration.ofSeconds(30);
    private static final int DEFAULT_HTTP_PORT = 8081;

    private FinanceLauncher() {
    }

    public static void main(String[] args) {
        FinanceLauncherConfig config = FinanceLauncherConfig.from(args);
        FinanceProductModule module = new FinanceProductModule();
        HttpServerBinding httpBinding = null;

        try {
            DefaultKernelContext context = createContext(config.environment());
            module.initialize(context);
            await("finance startup", module.start(), START_TIMEOUT);

            // Create and start HTTP server binding with explicit port configuration
            Eventloop eventloop = context.getDependency(Eventloop.class);
            FinanceHttpServer financeHttpServer = context.getDependency(FinanceHttpServer.class);
            httpBinding = new HttpServerBindingFactory()
                    .withServiceName("finance")
                    .withPort(config.httpPort())
                    .withHost(config.httpHost())
                    .build(eventloop, financeHttpServer.getServlet());
            await("finance http binding", httpBinding.start(), START_TIMEOUT);

            HttpServerBinding bindingForShutdown = httpBinding;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                stopQuietly(bindingForShutdown);
                stopQuietly(module);
            }, "finance-shutdown"));

            log.info("Finance launcher started: environment={}, http={}:{}", 
                config.environment(), httpBinding.getHost(), httpBinding.getPort());
            Thread.currentThread().join();
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            stopQuietly(httpBinding);
            stopQuietly(module);
            log.info("Finance launcher interrupted");
        } catch (Exception exception) {
            stopQuietly(httpBinding);
            stopQuietly(module);
            log.error("Finance launcher failed", exception);
            System.exit(1);
        }
    }

    private static DefaultKernelContext createContext(String environment) {
        KernelRegistryImpl registry = new KernelRegistryImpl();
        registerBaselineCapabilities(registry);

        HierarchicalKernelConfigResolver configResolver = new HierarchicalKernelConfigResolver();
        configResolver.addConfigProvider(new EnvironmentConfigProvider());

        Eventloop eventloop = Eventloop.create();
        DefaultKernelContext context = new DefaultKernelContext(registry, configResolver, eventloop, "1.0.0", environment);
        context.registerDependency(KernelConfigResolver.class, configResolver);
        context.registerDependency(ContractRegistry.class, new ContractRegistry());
        return context;
    }

    private static void registerBaselineCapabilities(KernelRegistryImpl registry) {
        registry.registerCapability(KernelCapability.Core.USER_AUTHENTICATION);
        registry.registerCapability(KernelCapability.Core.CONFIG_MANAGEMENT);
        registry.registerCapability(KernelCapability.Core.EVENT_PROCESSING);
        registry.registerCapability(KernelCapability.Core.OBSERVABILITY_FRAMEWORK);
        registry.registerCapability(KernelCapability.Core.RESILIENCE_PATTERNS);
        registry.registerCapability(KernelCapability.Core.DATA_STORAGE);
    }

    private static void stopQuietly(FinanceProductModule module) {
        try {
            await("finance shutdown", module.stop(), STOP_TIMEOUT);
        } catch (Exception exception) {
            log.warn("Finance shutdown encountered an error", exception);
        }
    }

    private static void stopQuietly(HttpServerBinding binding) {
        if (binding == null) {
            return;
        }
        try {
            await("finance http shutdown", binding.stop(), STOP_TIMEOUT);
        } catch (Exception exception) {
            log.warn("Finance HTTP server shutdown encountered an error", exception);
        }
    }

    private static void await(String operation, Promise<Void> promise, Duration timeout) throws Exception {
        PromiseUtils.toCompletableFuture(promise).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        log.info("Completed {}", operation);
    }

    private record FinanceLauncherConfig(String environment, int httpPort, String httpHost) {

        private static FinanceLauncherConfig from(String[] args) {
            String environment = readOption(args, "--environment")
                .or(() -> readOption(args, "--env"))
                .or(() -> readValue("FINANCE_ENVIRONMENT"))
                .or(() -> readValue("GHATANA_ENVIRONMENT"))
                .orElse("local");
            
            int port = readOption(args, "--port")
                .or(() -> readValue("FINANCE_HTTP_PORT"))
                .map(Integer::parseInt)
                .orElse(DEFAULT_HTTP_PORT);
            
            String host = readOption(args, "--host")
                .or(() -> readValue("FINANCE_HTTP_HOST"))
                .orElse("0.0.0.0");
            
            return new FinanceLauncherConfig(environment, port, host);
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