package com.ghatana.platform.core.service;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Abstract template implementation of {@link BaseService} that handles idempotent
 * lifecycle state guarding, structured logging, and hook-based extension.
 *
 * <h2>Purpose</h2>
 * Eliminates repetitive {@code AtomicBoolean initialized/started/running} boilerplate
 * found in product services. Subclasses override the protected lifecycle hooks
 * rather than re-implementing guard logic.
 *
 * <h2>Lifecycle</h2>
 * <pre>
 * initialize() → guard check → onInitialize() → mark initialized
 * shutdown()   → guard check → onShutdown()   → mark shut down
 * </pre>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * public class MyService extends AbstractBaseService {
 *
 *     private DatabasePool pool;
 *
 *     {@literal @}Override
 *     protected Promise<Void> onInitialize() {
 *         return Promise.ofBlocking(executor, () -> {
 *             pool = DatabasePool.create(config);
 *         });
 *     }
 *
 *     {@literal @}Override
 *     protected Promise<Void> onShutdown() {
 *         return Promise.ofBlocking(executor, () -> pool.close());
 *     }
 *
 *     {@literal @}Override
 *     public ServiceHealth getHealthStatus() {
 *         return pool != null && pool.isReady()
 *             ? ServiceHealth.healthy("pool ready")
 *             : ServiceHealth.unhealthy("pool not ready", Map.of());
 *     }
 * }
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Abstract lifecycle template for platform services — eliminates AtomicBoolean guard boilerplate
 * @doc.layer core
 * @doc.pattern Template Method, LifecycleManagement
 */
public abstract class AbstractBaseService implements BaseService {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean shutDown    = new AtomicBoolean(false);

    // ── Public lifecycle API (guarded) ─────────────────────────────────────────

    /**
     * Idempotent: calls {@link #onInitialize()} only on the first invocation.
     * Subsequent calls return a completed promise without invoking {@code onInitialize()}.
     */
    @Override
    public final Promise<Void> initialize() {
        if (!initialized.compareAndSet(false, true)) {
            log.debug("[{}] initialize() skipped — already initialized", serviceName());
            return Promise.complete();
        }
        log.info("[{}] Initializing", serviceName());
        return onInitialize()
                .whenComplete((v, e) -> {
                    if (e != null) {
                        initialized.set(false); // allow retry on failure
                        log.error("[{}] Initialization failed: {}", serviceName(), e.getMessage(), e);
                    } else {
                        log.info("[{}] Initialized successfully", serviceName());
                    }
                });
    }

    /**
     * Idempotent: calls {@link #onShutdown()} only once. Subsequent calls return immediately.
     */
    @Override
    public final Promise<Void> shutdown() {
        if (!shutDown.compareAndSet(false, true)) {
            log.debug("[{}] shutdown() skipped — already shut down", serviceName());
            return Promise.complete();
        }
        log.info("[{}] Shutting down", serviceName());
        return onShutdown()
                .whenComplete((v, e) -> {
                    if (e != null) {
                        log.error("[{}] Shutdown error: {}", serviceName(), e.getMessage(), e);
                    } else {
                        log.info("[{}] Shut down successfully", serviceName());
                    }
                });
    }

    // ── Template hooks (override in subclasses) ────────────────────────────────

    /**
     * Called once during {@link #initialize()} after the guard passes.
     * Override to allocate resources, connect to backends, warm caches, etc.
     *
     * @return promise that resolves when initialization is complete
     */
    protected Promise<Void> onInitialize() {
        return Promise.complete();
    }

    /**
     * Called once during {@link #shutdown()} after the guard passes.
     * Override to release resources, close connections, flush pending work, etc.
     *
     * @return promise that resolves when shutdown is complete
     */
    protected Promise<Void> onShutdown() {
        return Promise.complete();
    }

    // ── State accessors ────────────────────────────────────────────────────────

    /** Returns {@code true} if {@link #initialize()} has completed successfully. */
    protected boolean isInitialized() {
        return initialized.get();
    }

    /** Returns {@code true} if {@link #shutdown()} has been called. */
    protected boolean isShutDown() {
        return shutDown.get();
    }

    // ── Metadata ───────────────────────────────────────────────────────────────

    /**
     * Returns the display name for log messages. Defaults to the simple class name.
     * Override to provide a more meaningful name.
     */
    protected String serviceName() {
        return getClass().getSimpleName();
    }

    @Override
    public Map<String, Object> getServiceInfo() {
        return Map.of(
            "serviceClass", getClass().getSimpleName(),
            "status",       getHealthStatus().status().name(),
            "initialized",  initialized.get(),
            "shutDown",     shutDown.get()
        );
    }
}
