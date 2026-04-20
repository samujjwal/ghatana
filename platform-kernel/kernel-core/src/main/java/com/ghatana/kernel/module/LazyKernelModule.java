package com.ghatana.kernel.module;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.platform.health.HealthStatus;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Wraps a {@link KernelModule} to defer its {@code initialize(KernelContext)} call
 * until the first time the module is actually needed.
 *
 * <p>Use this together with the {@link com.ghatana.kernel.annotation.Lazy} annotation,
 * or construct it programmatically via {@link #wrap(KernelModule)}. The wrapped module's
 * identity ({@code getModuleId()}, {@code getVersion()}, capabilities, and dependencies)
 * is always available immediately — only heavy initialization work is deferred.</p>
 *
 * <p><b>Thread safety</b><br>
 * Initialization is guaranteed to happen at most once. The wrapper uses a
 * double-checked pattern backed by {@link AtomicBoolean} so multiple concurrent
 * callers do not double-initialize the delegate.</p>
 *
 * <p><b>Example — programmatic wrapping</b></p>
 * <pre>{@code
 * KernelModule lazy = LazyKernelModule.wrap(new HeavyAnalyticsModule());
 * kernel.registerModule(lazy);
 * }</pre>
 *
 * <p><b>Example — annotation-driven</b></p>
 * <pre>{@code
 * @Lazy
 * public class HeavyAnalyticsModule extends AbstractKernelModule { ... }
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Deferred-initialization wrapper for kernel modules
 * @doc.layer core
 * @doc.pattern Lazy Loading, Proxy
 */
public final class LazyKernelModule implements KernelModule {

    private static final Logger LOG = LoggerFactory.getLogger(LazyKernelModule.class);

    private final KernelModule delegate;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private volatile KernelContext pendingContext;

    private LazyKernelModule(KernelModule delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate cannot be null");
    }

    /**
     * Wraps {@code module} in a lazy wrapper.
     *
     * @param module the module to wrap; must not be {@code null}
     * @return a {@link LazyKernelModule} backed by {@code module}
     */
    public static LazyKernelModule wrap(KernelModule module) {
        return new LazyKernelModule(module);
    }

    // ==================== KernelModule identity — always available ====================

    @Override
    public String getModuleId() {
        return delegate.getModuleId();
    }

    @Override
    public String getVersion() {
        return delegate.getVersion();
    }

    @Override
    public Set<KernelCapability> getCapabilities() {
        return delegate.getCapabilities();
    }

    @Override
    public Set<KernelDependency> getDependencies() {
        return delegate.getDependencies();
    }

    // ==================== Lazy initialization ====================

    /**
     * Stores the context for deferred use. The delegate's {@code initialize()} is
     * NOT called here; it will be called just before the first {@link #start()}.
     *
     * @param context the kernel context
     */
    @Override
    public void initialize(KernelContext context) {
        this.pendingContext = Objects.requireNonNull(context, "context cannot be null");
        LOG.debug("LazyKernelModule[{}]: initialization deferred", delegate.getModuleId());
    }

    /**
     * Triggers deferred initialization on first call, then delegates {@code start()}.
     *
     * @return Promise completing when the delegate has started
     */
    @Override
    public Promise<Void> start() {
        ensureInitialized();
        return delegate.start();
    }

    @Override
    public Promise<Void> stop() {
        if (!initialized.get()) {
            // Never actually started — nothing to stop
            return Promise.complete();
        }
        return delegate.stop();
    }

    @Override
    public HealthStatus getHealthStatus() {
        if (!initialized.get()) {
            return HealthStatus.unhealthy("Module not yet initialized (lazy)");
        }
        return delegate.getHealthStatus();
    }

    // ==================== Internal ====================

    /**
     * Ensures the delegate is initialized exactly once.
     */
    private void ensureInitialized() {
        if (initialized.get()) {
            return;
        }
        synchronized (this) {
            if (initialized.get()) {
                return;
            }
            KernelContext ctx = pendingContext;
            if (ctx == null) {
                throw new IllegalStateException(
                    "LazyKernelModule[" + delegate.getModuleId() + "] was not given a KernelContext. "
                    + "Ensure initialize(KernelContext) is called before start().");
            }
            LOG.info("LazyKernelModule[{}]: triggering deferred initialization", delegate.getModuleId());
            delegate.initialize(ctx);
            initialized.set(true);
        }
    }

    /**
     * Returns {@code true} if the delegate has been initialized.
     *
     * @return initialization status
     */
    public boolean isInitialized() {
        return initialized.get();
    }

    /**
     * Returns the underlying delegate module (for testing / introspection).
     *
     * @return the wrapped {@link KernelModule}
     */
    public KernelModule getDelegate() {
        return delegate;
    }
}
