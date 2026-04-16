package com.ghatana.products.finance.launcher;

import com.ghatana.core.activej.promise.PromiseUtils;
import com.ghatana.kernel.config.HierarchicalKernelConfigResolver;
import com.ghatana.kernel.config.KernelConfigResolver;
import com.ghatana.kernel.context.DefaultKernelContext;
import com.ghatana.kernel.context.KernelTenantContext;
import com.ghatana.kernel.contracts.ContractRegistry;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.registry.KernelRegistryImpl;
import com.ghatana.products.finance.FinanceProductModule;
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
 * @doc.type class
 * @doc.purpose Standalone launcher for the Finance product module
 * @doc.layer launcher
 * @doc.pattern Main Entry Point
 */
public final class FinanceLauncher {

    private static final Logger log = LoggerFactory.getLogger(FinanceLauncher.class);
    private static final Duration START_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration STOP_TIMEOUT = Duration.ofSeconds(30);

    private FinanceLauncher() {
    }

    public static void main(String[] args) {
        FinanceLauncherConfig config = FinanceLauncherConfig.from(args);
        FinanceProductModule module = new FinanceProductModule();

        try {
            DefaultKernelContext context = createContext(config.environment());
            module.initialize(context);
            await("finance startup", module.start(), START_TIMEOUT);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> stopQuietly(module), "finance-shutdown"));

            log.info("Finance launcher started: environment={}", config.environment());
            Thread.currentThread().join();
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            stopQuietly(module);
            log.info("Finance launcher interrupted");
        } catch (Exception exception) {
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

    private static void await(String operation, Promise<Void> promise, Duration timeout) throws Exception {
        PromiseUtils.toCompletableFuture(promise).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        log.info("Completed {}", operation);
    }

    private record FinanceLauncherConfig(String environment) {

        private static FinanceLauncherConfig from(String[] args) {
            String environment = readOption(args, "--environment")
                .or(() -> readOption(args, "--env"))
                .or(() -> readValue("FINANCE_ENVIRONMENT"))
                .or(() -> readValue("GHATANA_ENVIRONMENT"))
                .orElse("local");
            return new FinanceLauncherConfig(environment);
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