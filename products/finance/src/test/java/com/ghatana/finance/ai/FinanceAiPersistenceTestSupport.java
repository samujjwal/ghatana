package com.ghatana.finance.ai;

import com.ghatana.products.finance.FinanceAiRuntimeConfig;
import com.ghatana.products.finance.FinanceTransactionRuntimeConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Assumptions;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.util.Map;

public final class FinanceAiPersistenceTestSupport {

    private FinanceAiPersistenceTestSupport() {
    }

    public static PostgreSQLContainer<?> startPostgres() {
        boolean dockerAvailable;
        try {
            dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable exception) {
            dockerAvailable = false;
        }

        Assumptions.assumeTrue(dockerAvailable, () -> "Skipping persistence test because Docker is unavailable");

        PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("finance_ai_test")
            .withUsername("ghatana")
            .withPassword("password");
        postgres.start();
        return postgres;
    }

    public static DataSource createDataSource(PostgreSQLContainer<?> postgres, String poolName) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(postgres.getJdbcUrl());
        config.setUsername(postgres.getUsername());
        config.setPassword(postgres.getPassword());
        config.setMaximumPoolSize(4);
        config.setMinimumIdle(1);
        config.setAutoCommit(false);
        config.setPoolName(poolName);
        return new HikariDataSource(config);
    }

    public static FinanceAiRuntimeConfig createRuntimeConfig(PostgreSQLContainer<?> postgres, String poolName) {
        return FinanceAiRuntimeConfig.fromContext(new RuntimeConfigKernelContext(
            Map.of(
                "finance.ai.persistence.enabled", true,
                "finance.ai.database.jdbc-url", postgres.getJdbcUrl(),
                "finance.ai.database.username", postgres.getUsername(),
                "finance.ai.database.password", postgres.getPassword(),
                "finance.ai.database.pool-name", poolName,
                "finance.ai.database.minimum-idle", 1,
                "finance.ai.database.maximum-pool-size", 4,
                "finance.ai.database.connection-timeout-ms", 30_000L,
                "finance.ai.database.idle-timeout-ms", 60_000L,
                "finance.ai.database.max-lifetime-ms", 120_000L
            )
        ));
    }

    public static FinanceTransactionRuntimeConfig createTransactionRuntimeConfig(
            PostgreSQLContainer<?> postgres,
            String poolName) {
        return createTransactionRuntimeConfig(postgres, poolName, 24L, 120, true);
    }

    public static FinanceTransactionRuntimeConfig createTransactionRuntimeConfig(
            PostgreSQLContainer<?> postgres,
            String poolName,
            long ttlHours,
            int maxRequestsPerMinute,
            boolean sharedRateLimitEnabled) {
        return FinanceTransactionRuntimeConfig.fromContext(new RuntimeConfigKernelContext(
            Map.ofEntries(
                Map.entry("finance.transaction.idempotency.persistence.enabled", true),
                Map.entry("finance.transaction.idempotency.database.jdbc-url", postgres.getJdbcUrl()),
                Map.entry("finance.transaction.idempotency.database.username", postgres.getUsername()),
                Map.entry("finance.transaction.idempotency.database.password", postgres.getPassword()),
                Map.entry("finance.transaction.idempotency.database.pool-name", poolName),
                Map.entry("finance.transaction.idempotency.database.minimum-idle", 1),
                Map.entry("finance.transaction.idempotency.database.maximum-pool-size", 4),
                Map.entry("finance.transaction.idempotency.database.connection-timeout-ms", 30_000L),
                Map.entry("finance.transaction.idempotency.database.idle-timeout-ms", 60_000L),
                Map.entry("finance.transaction.idempotency.database.max-lifetime-ms", 120_000L),
                Map.entry("finance.transaction.idempotency.ttl-hours", ttlHours),
                Map.entry("finance.transaction.rate-limit.shared.enabled", sharedRateLimitEnabled),
                Map.entry("finance.transaction.rate-limit.max-requests-per-minute", maxRequestsPerMinute),
                Map.entry("finance.transaction.rate-limit.window-seconds", 60L)
            )
        ));
    }

    public static void closeDataSource(DataSource dataSource) {
        if (dataSource instanceof HikariDataSource hikariDataSource) {
            hikariDataSource.close();
        }
    }

    public static final class RuntimeConfigKernelContext implements com.ghatana.kernel.context.KernelContext {

        private final Map<String, Object> configValues;

        public RuntimeConfigKernelContext(Map<String, Object> configValues) {
            this.configValues = configValues;
        }

        @Override public <T> T getDependency(Class<T> type) { throw new IllegalStateException("Dependency not found: " + type.getName()); }
        @Override public <T> java.util.Optional<T> getOptionalDependency(Class<T> type) { return java.util.Optional.empty(); }
        @Override public <T> boolean hasDependency(Class<T> type) { return false; }
        @Override public <T> T getDependency(String name, Class<T> type) { throw new IllegalStateException("Dependency not found: " + name); }
        @Override public <E> void registerEventHandler(Class<E> eventType, com.ghatana.kernel.event.EventHandler<E> handler) {}
        @Override public <E> void unregisterEventHandler(Class<E> eventType, com.ghatana.kernel.event.EventHandler<E> handler) {}
        @Override public <E> void publishEvent(E event) {}
        @Override public com.ghatana.kernel.context.KernelTenantContext getTenantContext() { return new com.ghatana.kernel.context.KernelTenantContext("test", com.ghatana.kernel.context.KernelTenantContext.TenantType.SYSTEM, Map.of(), java.util.Set.of(), null, null); }
        @Override public com.ghatana.kernel.context.KernelTenantContext getTenantContext(String tenantId) { return getTenantContext(); }
        @Override public io.activej.eventloop.Eventloop getEventloop() { return io.activej.eventloop.Eventloop.create(); }
        @Override public java.util.Set<com.ghatana.kernel.descriptor.KernelCapability> getAvailableCapabilities() { return java.util.Set.of(); }
        @Override public boolean hasCapability(com.ghatana.kernel.descriptor.KernelCapability capability) { return false; }
        @Override public <T> T getConfig(String key, Class<T> type) { return getOptionalConfig(key, type).orElseThrow(() -> new IllegalArgumentException("Config not found: " + key)); }
        @Override public <T> java.util.Optional<T> getOptionalConfig(String key, Class<T> type) {
            Object value = configValues.get(key);
            return type.isInstance(value) ? java.util.Optional.of(type.cast(value)) : java.util.Optional.empty();
        }
        @Override public String getKernelVersion() { return "1.0.0"; }
        @Override public String getEnvironment() { return "test"; }
        @Override public java.util.concurrent.Executor getExecutor(String executorName) { return java.util.concurrent.ForkJoinPool.commonPool(); }
        @Override public <T> java.util.Optional<T> getCapability(String capabilityId) { return java.util.Optional.empty(); }
        @Override public <T> void registerService(Class<T> type, T service) {}
    }
}