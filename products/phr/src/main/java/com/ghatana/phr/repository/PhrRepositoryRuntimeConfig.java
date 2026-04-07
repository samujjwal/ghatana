package com.ghatana.phr.repository;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.platform.database.connection.DataSourceConfig;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * PHR repository runtime configuration.
 *
 * @doc.type class
 * @doc.purpose Resolves PHR auxiliary repository persistence settings from kernel config
 * @doc.layer product
 * @doc.pattern Configuration
 */
public final class PhrRepositoryRuntimeConfig {

    private static final String ENABLED_KEY = "phr.persistence.enabled";
    private static final String JDBC_URL_KEY = "phr.persistence.jdbc-url";
    private static final String USERNAME_KEY = "phr.persistence.username";
    private static final String PASSWORD_KEY = "phr.persistence.password";
    private static final String DRIVER_KEY = "phr.persistence.driver-class-name";
    private static final String MIN_IDLE_KEY = "phr.persistence.minimum-idle";
    private static final String MAX_POOL_KEY = "phr.persistence.maximum-pool-size";
    private static final String CONNECTION_TIMEOUT_KEY = "phr.persistence.connection-timeout-ms";
    private static final String IDLE_TIMEOUT_KEY = "phr.persistence.idle-timeout-ms";
    private static final String MAX_LIFETIME_KEY = "phr.persistence.max-lifetime-ms";
    private static final String POOL_NAME_KEY = "phr.persistence.pool-name";

    private final boolean persistenceEnabled;
    private final DataSourceConfig dataSourceConfig;

    private PhrRepositoryRuntimeConfig(boolean persistenceEnabled, DataSourceConfig dataSourceConfig) {
        this.persistenceEnabled = persistenceEnabled;
        this.dataSourceConfig = dataSourceConfig;
    }

    public static PhrRepositoryRuntimeConfig fromContext(KernelContext context) {
        Objects.requireNonNull(context, "context cannot be null");

        Optional<Boolean> explicitEnabled = context.getOptionalConfig(ENABLED_KEY, Boolean.class);
        Optional<String> jdbcUrl = context.getOptionalConfig(JDBC_URL_KEY, String.class)
            .filter(value -> !value.isBlank());

        boolean persistenceEnabled = explicitEnabled.orElseGet(jdbcUrl::isPresent);
        if (!persistenceEnabled) {
            return disabled();
        }

        String resolvedJdbcUrl = jdbcUrl.orElseThrow(() ->
            new IllegalStateException("PHR persistence is enabled but '" + JDBC_URL_KEY + "' is missing")
        );
        String username = context.getOptionalConfig(USERNAME_KEY, String.class)
            .filter(value -> !value.isBlank())
            .orElseThrow(() -> new IllegalStateException(
                "PHR persistence is enabled but '" + USERNAME_KEY + "' is missing"
            ));
        String password = context.getOptionalConfig(PASSWORD_KEY, String.class)
            .orElseThrow(() -> new IllegalStateException(
                "PHR persistence is enabled but '" + PASSWORD_KEY + "' is missing"
            ));

        DataSourceConfig dataSourceConfig = DataSourceConfig.builder()
            .jdbcUrl(resolvedJdbcUrl)
            .username(username)
            .password(password)
            .driverClassName(context.getOptionalConfig(DRIVER_KEY, String.class).orElse("org.postgresql.Driver"))
            .minimumIdle(context.getOptionalConfig(MIN_IDLE_KEY, Integer.class).orElse(5))
            .maximumPoolSize(context.getOptionalConfig(MAX_POOL_KEY, Integer.class).orElse(20))
            .connectionTimeout(Duration.ofMillis(context.getOptionalConfig(CONNECTION_TIMEOUT_KEY, Long.class).orElse(30_000L)))
            .idleTimeout(Duration.ofMillis(context.getOptionalConfig(IDLE_TIMEOUT_KEY, Long.class).orElse(600_000L)))
            .maxLifetime(Duration.ofMillis(context.getOptionalConfig(MAX_LIFETIME_KEY, Long.class).orElse(1_800_000L)))
            .poolName(context.getOptionalConfig(POOL_NAME_KEY, String.class).orElse("phr-repository-runtime"))
            .build();

        return new PhrRepositoryRuntimeConfig(true, dataSourceConfig);
    }

    public static PhrRepositoryRuntimeConfig disabled() {
        return new PhrRepositoryRuntimeConfig(false, null);
    }

    public boolean isPersistenceEnabled() {
        return persistenceEnabled;
    }

    public Optional<DataSourceConfig> getDataSourceConfig() {
        return Optional.ofNullable(dataSourceConfig);
    }
}