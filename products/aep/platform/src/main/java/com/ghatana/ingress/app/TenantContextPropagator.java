package com.ghatana.ingress.app;

import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * Helpers to run code with the current TenantContext preserved.
 * ActiveJ generally runs on a single eventloop thread, so this is mostly a no-op
 * but is helpful for readability and future-proofing if we multi-thread.
 */
public final class TenantContextPropagator {
    private TenantContextPropagator() { }

    public static <T> T callWithTenant(Supplier<T> supplier) {
        return supplier.get();
    }

    public static void runWithTenant(Runnable runnable) {
        runnable.run();
    }

    public static <T> T call(Callable<T> c) throws Exception {
        return c.call();
    }
}
