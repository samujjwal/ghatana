package com.ghatana.kernel.module;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.platform.health.HealthStatus;
import com.ghatana.kernel.service.KernelLifecycleAware;
import io.activej.promise.Promise;
import io.activej.promise.Promises;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @doc.type class
 * @doc.purpose Shared lifecycle orchestration for kernel modules
 * @doc.layer core
 * @doc.pattern Service
 */
public abstract class AbstractKernelModule implements KernelModule {

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final List<KernelLifecycleAware> services = new ArrayList<>();
    private volatile KernelContext context;

    @Override
    public final void initialize(KernelContext context) {
        if (!initialized.compareAndSet(false, true)) {
            throw new IllegalStateException(getModuleId() + " already initialized");
        }

        this.context = context;
        validateDependencies(context);
        initializeConfiguration(context);
        registerEventHandlers(context);
        registerServices(services, context);
        registerModuleContract(context);
        afterInitialized(context);
    }

    @Override
    public final Promise<Void> start() {
        if (!initialized.get()) {
            return Promise.ofException(new IllegalStateException("Module not initialized"));
        }

        if (!started.compareAndSet(false, true)) {
            return Promise.complete();
        }

        return Promises.all(services.stream().map(KernelLifecycleAware::start).toList())
            .then(() -> onStarted(context))
            .whenException(ignored -> started.set(false));
    }

    @Override
    public final Promise<Void> stop() {
        if (!started.compareAndSet(true, false)) {
            return Promise.complete();
        }

        List<Promise<Void>> stopPromises = services.reversed().stream()
            .map(KernelLifecycleAware::stop)
            .toList();

        return Promises.all(stopPromises)
            .then(() -> onStopped(context));
    }

    @Override
    public final HealthStatus getHealthStatus() {
        if (!initialized.get()) {
            return HealthStatus.unhealthy("Module not initialized");
        }

        HealthStatus.Builder builder = HealthStatus.builder()
            .withStatus(HealthStatus.Status.HEALTHY)
            .withMessage(getHealthyMessage());

        boolean unhealthy = false;
        for (KernelLifecycleAware service : services) {
            boolean healthy = service.isHealthy();
            if (!healthy) {
                unhealthy = true;
            }
            builder.withCheck(
                service.getName(),
                healthy ? HealthStatus.Status.HEALTHY : HealthStatus.Status.UNHEALTHY,
                healthy ? "Service healthy" : "Service unhealthy",
                0
            );
        }

        if (unhealthy) {
            builder.withStatus(HealthStatus.Status.DEGRADED);
            builder.withMessage(getDegradedMessage());
        }

        enrichHealthStatus(builder, context);
        return builder.build();
    }

    protected final KernelContext getContext() {
        return context;
    }

    protected final List<KernelLifecycleAware> getServices() {
        return List.copyOf(services);
    }

    protected String getHealthyMessage() {
        return getModuleId() + " module operational";
    }

    protected String getDegradedMessage() {
        return getModuleId() + " module degraded";
    }

    protected void validateDependencies(KernelContext context) {
    }

    protected void initializeConfiguration(KernelContext context) {
    }

    protected void registerEventHandlers(KernelContext context) {
    }

    protected void registerModuleContract(KernelContext context) {
    }

    protected void afterInitialized(KernelContext context) {
    }

    protected Promise<Void> onStarted(KernelContext context) {
        return Promise.complete();
    }

    protected Promise<Void> onStopped(KernelContext context) {
        return Promise.complete();
    }

    protected void enrichHealthStatus(HealthStatus.Builder builder, KernelContext context) {
    }

    protected abstract void registerServices(List<KernelLifecycleAware> services, KernelContext context);
}
