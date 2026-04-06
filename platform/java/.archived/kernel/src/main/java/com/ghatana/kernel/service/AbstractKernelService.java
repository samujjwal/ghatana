package com.ghatana.kernel.service;

import io.activej.promise.Promise;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @doc.type class
 * @doc.purpose Reusable lifecycle base for kernel-managed services
 * @doc.layer core
 * @doc.pattern Service
 */
public abstract class AbstractKernelService implements KernelLifecycleAware {

    private final String serviceName;
    private final AtomicBoolean running = new AtomicBoolean(false);

    protected AbstractKernelService(String serviceName) {
        this.serviceName = Objects.requireNonNull(serviceName, "serviceName cannot be null");
    }

    @Override
    public final Promise<Void> start() {
        if (!running.compareAndSet(false, true)) {
            return Promise.complete();
        }

        return onStart()
            .whenException(ignored -> running.set(false));
    }

    @Override
    public final Promise<Void> stop() {
        if (!running.compareAndSet(true, false)) {
            return Promise.complete();
        }

        return onStop();
    }

    @Override
    public boolean isHealthy() {
        return running.get() && isInternallyHealthy();
    }

    @Override
    public final String getName() {
        return serviceName;
    }

    protected final void requireRunning() {
        if (!running.get()) {
            throw new IllegalStateException("Service not running: " + serviceName);
        }
    }

    protected boolean isRunning() {
        return running.get();
    }

    protected boolean isInternallyHealthy() {
        return true;
    }

    protected Promise<Void> onStart() {
        return Promise.complete();
    }

    protected Promise<Void> onStop() {
        return Promise.complete();
    }
}
