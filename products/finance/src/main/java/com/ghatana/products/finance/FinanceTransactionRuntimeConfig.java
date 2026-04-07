package com.ghatana.products.finance;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.platform.database.connection.DataSourceConfig;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * @doc.type class
 * @doc.purpose Resolves Finance transaction-runtime persistence settings from kernel config with AI-runtime fallback
 * @doc.layer product
 * @doc.pattern Configuration
 */
public final class FinanceTransactionRuntimeConfig {

    private static final String ENABLED_KEY = "finance.transaction.idempotency.persistence.enabled";
    private static final String JDBC_URL_KEY = "finance.transaction.idempotency.database.jdbc-url";
    private static final String USERNAME_KEY = "finance.transaction.idempotency.database.username";
    private static final String PASSWORD_KEY = "finance.transaction.idempotency.database.password";
    private static final String DRIVER_KEY = "finance.transaction.idempotency.database.driver-class-name";
    private static final String MIN_IDLE_KEY = "finance.transaction.idempotency.database.minimum-idle";
    private static final String MAX_POOL_KEY = "finance.transaction.idempotency.database.maximum-pool-size";
    private static final String CONNECTION_TIMEOUT_KEY = "finance.transaction.idempotency.database.connection-timeout-ms";
    private static final String IDLE_TIMEOUT_KEY = "finance.transaction.idempotency.database.idle-timeout-ms";
    private static final String MAX_LIFETIME_KEY = "finance.transaction.idempotency.database.max-lifetime-ms";
    private static final String POOL_NAME_KEY = "finance.transaction.idempotency.database.pool-name";
    private static final String TTL_HOURS_KEY = "finance.transaction.idempotency.ttl-hours";
    private static final String SHARED_RATE_LIMIT_ENABLED_KEY = "finance.transaction.rate-limit.shared.enabled";
    private static final String MAX_REQUESTS_PER_MINUTE_KEY = "finance.transaction.rate-limit.max-requests-per-minute";
    private static final String WINDOW_SECONDS_KEY = "finance.transaction.rate-limit.window-seconds";

    private static final String AI_JDBC_URL_KEY = "finance.ai.database.jdbc-url";
    private static final String AI_USERNAME_KEY = "finance.ai.database.username";
    private static final String AI_PASSWORD_KEY = "finance.ai.database.password";
    private static final String AI_DRIVER_KEY = "finance.ai.database.driver-class-name";
    private static final String AI_MIN_IDLE_KEY = "finance.ai.database.minimum-idle";
    private static final String AI_MAX_POOL_KEY = "finance.ai.database.maximum-pool-size";
    private static final String AI_CONNECTION_TIMEOUT_KEY = "finance.ai.database.connection-timeout-ms";
    private static final String AI_IDLE_TIMEOUT_KEY = "finance.ai.database.idle-timeout-ms";
    private static final String AI_MAX_LIFETIME_KEY = "finance.ai.database.max-lifetime-ms";
    private static final String AI_POOL_NAME_KEY = "finance.ai.database.pool-name";

    private final boolean persistenceEnabled;
    private final Duration idempotencyTtl;
    private final boolean sharedRateLimitEnabled;
    private final int maxRequestsPerMinute;
    private final Duration rateLimitWindow;
    private final DataSourceConfig dataSourceConfig;

    private FinanceTransactionRuntimeConfig(
            boolean persistenceEnabled,
            Duration idempotencyTtl,
            boolean sharedRateLimitEnabled,
            int maxRequestsPerMinute,
            Duration rateLimitWindow,
            DataSourceConfig dataSourceConfig) {
        this.persistenceEnabled = persistenceEnabled;
        this.idempotencyTtl = Objects.requireNonNull(idempotencyTtl, "idempotencyTtl cannot be null");
        this.sharedRateLimitEnabled = sharedRateLimitEnabled;
        this.maxRequestsPerMinute = maxRequestsPerMinute;
        this.rateLimitWindow = Objects.requireNonNull(rateLimitWindow, "rateLimitWindow cannot be null");
        this.dataSourceConfig = dataSourceConfig;
    }

    public static FinanceTransactionRuntimeConfig fromContext(KernelContext context) {
        Objects.requireNonNull(context, "context cannot be null");

        Optional<String> directJdbcUrl = configuredString(context, JDBC_URL_KEY);
        Optional<String> fallbackAiJdbcUrl = configuredString(context, AI_JDBC_URL_KEY);
        boolean persistenceEnabled = context.getOptionalConfig(ENABLED_KEY, Boolean.class)
            .orElseGet(() -> directJdbcUrl.isPresent() || fallbackAiJdbcUrl.isPresent());
        Duration ttl = Duration.ofHours(context.getOptionalConfig(TTL_HOURS_KEY, Long.class).orElse(24L));
        int maxRequestsPerMinute = context.getOptionalConfig(MAX_REQUESTS_PER_MINUTE_KEY, Integer.class).orElse(120);
        Duration rateLimitWindow = Duration.ofSeconds(context.getOptionalConfig(WINDOW_SECONDS_KEY, Long.class).orElse(60L));
        boolean sharedRateLimitEnabled = context.getOptionalConfig(SHARED_RATE_LIMIT_ENABLED_KEY, Boolean.class)
            .orElse(persistenceEnabled);

        if (!persistenceEnabled) {
            return disabled(ttl, maxRequestsPerMinute, rateLimitWindow);
        }

        String jdbcUrl = directJdbcUrl.or(() -> fallbackAiJdbcUrl).orElseThrow(() ->
            new IllegalStateException(
                "Finance transaction idempotency persistence is enabled but no JDBC URL is configured"
            )
        );
        String username = configuredString(context, USERNAME_KEY)
            .or(() -> configuredString(context, AI_USERNAME_KEY))
            .orElseThrow(() -> new IllegalStateException(
                "Finance transaction idempotency persistence is enabled but no username is configured"
            ));
        String password = context.getOptionalConfig(PASSWORD_KEY, String.class)
            .or(() -> context.getOptionalConfig(AI_PASSWORD_KEY, String.class))
            .orElseThrow(() -> new IllegalStateException(
                "Finance transaction idempotency persistence is enabled but no password is configured"
            ));

        DataSourceConfig dataSourceConfig = DataSourceConfig.builder()
            .jdbcUrl(jdbcUrl)
            .username(username)
            .password(password)
            .driverClassName(configuredString(context, DRIVER_KEY)
                .or(() -> configuredString(context, AI_DRIVER_KEY))
                .orElse("org.postgresql.Driver"))
            .minimumIdle(context.getOptionalConfig(MIN_IDLE_KEY, Integer.class)
                .or(() -> context.getOptionalConfig(AI_MIN_IDLE_KEY, Integer.class))
                .orElse(1))
            .maximumPoolSize(context.getOptionalConfig(MAX_POOL_KEY, Integer.class)
                .or(() -> context.getOptionalConfig(AI_MAX_POOL_KEY, Integer.class))
                .orElse(4))
            .connectionTimeout(Duration.ofMillis(context.getOptionalConfig(CONNECTION_TIMEOUT_KEY, Long.class)
                .or(() -> context.getOptionalConfig(AI_CONNECTION_TIMEOUT_KEY, Long.class))
                .orElse(30_000L)))
            .idleTimeout(Duration.ofMillis(context.getOptionalConfig(IDLE_TIMEOUT_KEY, Long.class)
                .or(() -> context.getOptionalConfig(AI_IDLE_TIMEOUT_KEY, Long.class))
                .orElse(60_000L)))
            .maxLifetime(Duration.ofMillis(context.getOptionalConfig(MAX_LIFETIME_KEY, Long.class)
                .or(() -> context.getOptionalConfig(AI_MAX_LIFETIME_KEY, Long.class))
                .orElse(120_000L)))
            .poolName(configuredString(context, POOL_NAME_KEY)
                .or(() -> configuredString(context, AI_POOL_NAME_KEY))
                .map(name -> name + "-transactions")
                .orElse("finance-transaction-runtime"))
            .build();

        return new FinanceTransactionRuntimeConfig(
            true,
            ttl,
            sharedRateLimitEnabled,
            maxRequestsPerMinute,
            rateLimitWindow,
            dataSourceConfig
        );
    }

    public static FinanceTransactionRuntimeConfig disabled(Duration ttl) {
        return disabled(ttl, 120, Duration.ofSeconds(60));
    }

    public static FinanceTransactionRuntimeConfig disabled(
            Duration ttl,
            int maxRequestsPerMinute,
            Duration rateLimitWindow) {
        return new FinanceTransactionRuntimeConfig(false, ttl, false, maxRequestsPerMinute, rateLimitWindow, null);
    }

    public boolean isPersistenceEnabled() {
        return persistenceEnabled;
    }

    public Duration getIdempotencyTtl() {
        return idempotencyTtl;
    }

    public boolean isSharedRateLimitEnabled() {
        return sharedRateLimitEnabled;
    }

    public int getMaxRequestsPerMinute() {
        return maxRequestsPerMinute;
    }

    public Duration getRateLimitWindow() {
        return rateLimitWindow;
    }

    public Optional<DataSourceConfig> getDataSourceConfig() {
        return Optional.ofNullable(dataSourceConfig);
    }

    private static Optional<String> configuredString(KernelContext context, String key) {
        return context.getOptionalConfig(key, String.class).filter(value -> !value.isBlank());
    }
}