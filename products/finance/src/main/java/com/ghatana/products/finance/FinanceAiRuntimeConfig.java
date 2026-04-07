package com.ghatana.products.finance;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.platform.database.connection.DataSourceConfig;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Finance AI runtime configuration.
 *
 * @doc.type class
 * @doc.purpose Resolves finance AI runtime persistence and connection-pool settings from kernel config
 * @doc.layer product
 * @doc.pattern Configuration
 */
public final class FinanceAiRuntimeConfig {

    private static final String ENABLED_KEY = "finance.ai.persistence.enabled";
    private static final String JDBC_URL_KEY = "finance.ai.database.jdbc-url";
    private static final String USERNAME_KEY = "finance.ai.database.username";
    private static final String PASSWORD_KEY = "finance.ai.database.password";
    private static final String DRIVER_KEY = "finance.ai.database.driver-class-name";
    private static final String MIN_IDLE_KEY = "finance.ai.database.minimum-idle";
    private static final String MAX_POOL_KEY = "finance.ai.database.maximum-pool-size";
    private static final String CONNECTION_TIMEOUT_KEY = "finance.ai.database.connection-timeout-ms";
    private static final String IDLE_TIMEOUT_KEY = "finance.ai.database.idle-timeout-ms";
    private static final String MAX_LIFETIME_KEY = "finance.ai.database.max-lifetime-ms";
    private static final String POOL_NAME_KEY = "finance.ai.database.pool-name";

    private final boolean persistenceEnabled;
    private final DataSourceConfig dataSourceConfig;

    private FinanceAiRuntimeConfig(boolean persistenceEnabled, DataSourceConfig dataSourceConfig) {
        this.persistenceEnabled = persistenceEnabled;
        this.dataSourceConfig = dataSourceConfig;
    }

    public static FinanceAiRuntimeConfig fromContext(KernelContext context) {
        Objects.requireNonNull(context, "context cannot be null");

        Optional<Boolean> explicitEnabled = context.getOptionalConfig(ENABLED_KEY, Boolean.class);
        Optional<String> jdbcUrl = context.getOptionalConfig(JDBC_URL_KEY, String.class)
            .filter(value -> !value.isBlank());

        boolean persistenceEnabled = explicitEnabled.orElseGet(jdbcUrl::isPresent);
        if (!persistenceEnabled) {
            return disabled();
        }

        String resolvedJdbcUrl = jdbcUrl.orElseThrow(() ->
            new IllegalStateException("Finance AI persistence is enabled but '" + JDBC_URL_KEY + "' is missing")
        );
        String username = context.getOptionalConfig(USERNAME_KEY, String.class)
            .filter(value -> !value.isBlank())
            .orElseThrow(() -> new IllegalStateException(
                "Finance AI persistence is enabled but '" + USERNAME_KEY + "' is missing"
            ));
        String password = context.getOptionalConfig(PASSWORD_KEY, String.class)
            .orElseThrow(() -> new IllegalStateException(
                "Finance AI persistence is enabled but '" + PASSWORD_KEY + "' is missing"
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
            .poolName(context.getOptionalConfig(POOL_NAME_KEY, String.class).orElse("finance-ai-runtime"))
            .build();

        return new FinanceAiRuntimeConfig(true, dataSourceConfig);
    }

    public static FinanceAiRuntimeConfig disabled() {
        return new FinanceAiRuntimeConfig(false, null);
    }

    public boolean isPersistenceEnabled() {
        return persistenceEnabled;
    }

    public Optional<DataSourceConfig> getDataSourceConfig() {
        return Optional.ofNullable(dataSourceConfig);
    }
}