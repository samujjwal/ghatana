package com.ghatana.products.finance;

import com.ghatana.finance.service.JdbcTransactionProcessingIdempotencyStore;
import com.ghatana.finance.service.JdbcSharedRateLimiter;
import com.ghatana.finance.service.TransactionIdempotencyStore;
import com.ghatana.finance.service.TransactionProcessingIdempotencyStore;
import com.ghatana.finance.service.TransactionService;
import com.ghatana.kernel.ai.AgentOrchestrator;
import com.ghatana.kernel.ai.AutonomyManager;
import com.ghatana.kernel.service.KernelLifecycleAware;
import com.ghatana.platform.database.connection.ConnectionPool;
import com.ghatana.platform.security.ratelimit.DefaultRateLimiter;
import com.ghatana.platform.security.ratelimit.RateLimiter;
import com.ghatana.platform.security.ratelimit.RateLimiterConfig;
import io.activej.promise.Promise;
import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @doc.type class
 * @doc.purpose Owns Finance transaction-processing service lifecycle and optional durable idempotency storage
 * @doc.layer product
 * @doc.pattern Service
 */
public final class FinanceTransactionRuntimeService implements KernelLifecycleAware {

    private static final Logger log = LoggerFactory.getLogger(FinanceTransactionRuntimeService.class);
    private final FinanceTransactionRuntimeConfig config;
    private final AgentOrchestrator orchestrator;
    private final AutonomyManager autonomyManager;
    private final Clock clock;
    private final AtomicBoolean started = new AtomicBoolean(false);

    private ConnectionPool connectionPool;
    private TransactionService transactionService;
    private RateLimiter rateLimiter;

    public FinanceTransactionRuntimeService(
            FinanceTransactionRuntimeConfig config,
            AgentOrchestrator orchestrator,
            AutonomyManager autonomyManager) {
        this(config, orchestrator, autonomyManager, Clock.systemUTC());
    }

    FinanceTransactionRuntimeService(
            FinanceTransactionRuntimeConfig config,
            AgentOrchestrator orchestrator,
            AutonomyManager autonomyManager,
            Clock clock) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.orchestrator = Objects.requireNonNull(orchestrator, "orchestrator cannot be null");
        this.autonomyManager = Objects.requireNonNull(autonomyManager, "autonomyManager cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    @Override
    public Promise<Void> start() {
        if (!started.compareAndSet(false, true)) {
            return Promise.complete();
        }

        log.info("Starting Finance transaction runtime (persistent={})", config.isPersistenceEnabled());
        try {
            TransactionIdempotencyStore idempotencyStore;
            if (config.isPersistenceEnabled()) {
                connectionPool = ConnectionPool.create(config.getDataSourceConfig().orElseThrow());
                idempotencyStore = new JdbcTransactionProcessingIdempotencyStore(
                    connectionPool.getDataSource(),
                    config.getIdempotencyTtl(),
                    clock
                );
            } else {
                idempotencyStore = new TransactionProcessingIdempotencyStore(config.getIdempotencyTtl(), clock);
            }

            rateLimiter = createRateLimiter();

            transactionService = new TransactionService(
                orchestrator,
                autonomyManager,
                clock,
                rateLimiter,
                idempotencyStore
            );
            return Promise.complete();
        } catch (RuntimeException exception) {
            started.set(false);
            closePool();
            throw exception;
        }
    }

    @Override
    public Promise<Void> stop() {
        if (!started.compareAndSet(true, false)) {
            return Promise.complete();
        }

        log.info("Stopping Finance transaction runtime");
        transactionService = null;
        rateLimiter = null;
        closePool();
        return Promise.complete();
    }

    @Override
    public boolean isHealthy() {
        return started.get() && transactionService != null && (connectionPool == null || connectionPool.isRunning());
    }

    @Override
    public String getName() {
        return "finance-transaction-runtime";
    }

    public boolean isPersistenceEnabled() {
        return config.isPersistenceEnabled();
    }

    public boolean isSharedRateLimitingEnabled() {
        return config.isSharedRateLimitEnabled();
    }

    public TransactionService getTransactionService() {
        if (transactionService == null) {
            throw new IllegalStateException("Finance transaction runtime is not started");
        }
        return transactionService;
    }

    private RateLimiter createRateLimiter() {
        if (config.isSharedRateLimitEnabled()) {
            if (connectionPool == null) {
                throw new IllegalStateException("Shared Finance rate limiting requires transaction persistence");
            }
            return new JdbcSharedRateLimiter(
                connectionPool.getDataSource(),
                config.getMaxRequestsPerMinute(),
                config.getRateLimitWindow(),
                clock
            );
        }
        return DefaultRateLimiter.create(
            RateLimiterConfig.builder()
                .maxRequestsPerMinute(config.getMaxRequestsPerMinute())
                .burstSize(config.getMaxRequestsPerMinute())
                .windowDuration(config.getRateLimitWindow())
                .build()
        );
    }

    private void closePool() {
        if (connectionPool != null) {
            connectionPool.close();
            connectionPool = null;
        }
    }
}
