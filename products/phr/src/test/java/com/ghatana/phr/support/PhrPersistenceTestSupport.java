package com.ghatana.phr.support;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.context.KernelTenantContext;
import com.ghatana.phr.repository.PhrRepositoryRuntimeConfig;
import com.ghatana.platform.database.connection.ConnectionPool;
import com.ghatana.platform.database.connection.DataSourceConfig;
import org.junit.jupiter.api.Assumptions;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;

/**
 * Shared PostgreSQL persistence test support for PHR repository tests.
 *
 * @doc.type class
 * @doc.purpose Starts PostgreSQL test infrastructure and creates pool-backed PHR runtime configs
 * @doc.layer product
 * @doc.pattern TestFixture
 */
public final class PhrPersistenceTestSupport {

    private PhrPersistenceTestSupport() {
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
            .withDatabaseName("phr_repository_test")
            .withUsername("ghatana")
            .withPassword("password");
        postgres.start();
        return postgres;
    }

    public static ConnectionPool createConnectionPool(PostgreSQLContainer<?> postgres, String poolName) {
        return ConnectionPool.create(DataSourceConfig.builder()
            .jdbcUrl(postgres.getJdbcUrl())
            .username(postgres.getUsername())
            .password(postgres.getPassword())
            .driverClassName("org.postgresql.Driver")
            .minimumIdle(1)
            .maximumPoolSize(4)
            .connectionTimeout(Duration.ofSeconds(30))
            .idleTimeout(Duration.ofMinutes(1))
            .maxLifetime(Duration.ofMinutes(2))
            .poolName(poolName)
            .build());
    }

    public static PhrRepositoryRuntimeConfig createRuntimeConfig(PostgreSQLContainer<?> postgres, String poolName) {
        return PhrRepositoryRuntimeConfig.fromContext(new RuntimeConfigKernelContext(
            Map.of(
                "phr.persistence.enabled", true,
                "phr.persistence.jdbc-url", postgres.getJdbcUrl(),
                "phr.persistence.username", postgres.getUsername(),
                "phr.persistence.password", postgres.getPassword(),
                "phr.persistence.pool-name", poolName,
                "phr.persistence.minimum-idle", 1,
                "phr.persistence.maximum-pool-size", 4,
                "phr.persistence.connection-timeout-ms", 30_000L,
                "phr.persistence.idle-timeout-ms", 60_000L,
                "phr.persistence.max-lifetime-ms", 120_000L
            )
        ));
    }

    public static void closeConnectionPool(ConnectionPool connectionPool) {
        if (connectionPool != null) {
            connectionPool.close();
        }
    }

    public static final class RuntimeConfigKernelContext implements KernelContext {

        private final Map<String, Object> configValues;

        public RuntimeConfigKernelContext(Map<String, Object> configValues) {
            this.configValues = configValues;
        }

        @Override public <T> T getDependency(Class<T> type) { throw new IllegalStateException("Dependency not found: " + type.getName()); }
        @Override public <T> Optional<T> getOptionalDependency(Class<T> type) { return Optional.empty(); }
        @Override public <T> boolean hasDependency(Class<T> type) { return false; }
        @Override public <T> T getDependency(String name, Class<T> type) { throw new IllegalStateException("Dependency not found: " + name); }
        @Override public <E> void registerEventHandler(Class<E> eventType, com.ghatana.kernel.event.EventHandler<E> handler) {}
        @Override public <E> void unregisterEventHandler(Class<E> eventType, com.ghatana.kernel.event.EventHandler<E> handler) {}
        @Override public <E> void publishEvent(E event) {}
        @Override public KernelTenantContext getTenantContext() { return new KernelTenantContext("test", KernelTenantContext.TenantType.SYSTEM, Map.of(), Set.of(), null, null); }
        @Override public KernelTenantContext getTenantContext(String tenantId) { return getTenantContext(); }
        @Override public io.activej.eventloop.Eventloop getEventloop() { return io.activej.eventloop.Eventloop.create(); }
        @Override public Set<com.ghatana.kernel.descriptor.KernelCapability> getAvailableCapabilities() { return Set.of(); }
        @Override public boolean hasCapability(com.ghatana.kernel.descriptor.KernelCapability capability) { return false; }
        @Override public <T> T getConfig(String key, Class<T> type) { return getOptionalConfig(key, type).orElseThrow(() -> new IllegalArgumentException("Config not found: " + key)); }
        @Override public <T> Optional<T> getOptionalConfig(String key, Class<T> type) {
            Object value = configValues.get(key);
            return type.isInstance(value) ? Optional.of(type.cast(value)) : Optional.empty();
        }
        @Override public String getKernelVersion() { return "1.0.0"; }
        @Override public String getEnvironment() { return "test"; }
        @Override public java.util.concurrent.Executor getExecutor(String executorName) { return ForkJoinPool.commonPool(); }
        @Override public <T> Optional<T> getCapability(String capabilityId) { return Optional.empty(); }
        @Override public <T> void registerService(Class<T> type, T service) {}
    }
}