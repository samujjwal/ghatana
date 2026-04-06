package com.ghatana.kernel.extension;

import com.ghatana.kernel.context.KernelContext;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @doc.type class
 * @doc.purpose Shared lifecycle state management for kernel extensions
 * @doc.layer core
 * @doc.pattern Service
 */
public abstract class AbstractKernelExtension implements KernelExtension {

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean started = new AtomicBoolean(false);
    private volatile KernelContext context;

    @Override
    public final void onModuleInitialized(KernelContext context) {
        if (!initialized.compareAndSet(false, true)) {
            return;
        }
        this.context = context;
        onInitialize(context);
    }

    @Override
    public final void onModuleStarted(KernelContext context) {
        started.set(true);
        onStart(context);
    }

    @Override
    public final void onModuleStopped(KernelContext context) {
        started.set(false);
        onStop(context);
    }

    protected final KernelContext getContext() {
        return context;
    }

    protected final boolean isStarted() {
        return started.get();
    }

    protected final void requireStarted() {
        if (!started.get()) {
            throw new IllegalStateException("Extension not started: " + getExtensionId());
        }
    }

    protected void onInitialize(KernelContext context) {
    }

    protected void onStart(KernelContext context) {
    }

    protected void onStop(KernelContext context) {
    }
}
