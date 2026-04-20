package com.ghatana.kernel.util;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Thread-safe lazy initializer for deferred object creation.
 *
 * @param <T> target type
 * @doc.type class
 * @doc.purpose Lazy dependency initialization helper
 * @doc.layer core
 * @doc.pattern Lazy Initialization
 */
public final class LazyInitializer<T> {

    private final Supplier<T> supplier;
    private volatile T value;

    public LazyInitializer(Supplier<T> supplier) {
        this.supplier = Objects.requireNonNull(supplier, "supplier cannot be null");
    }

    public T get() {
        T current = value;
        if (current != null) {
            return current;
        }

        synchronized (this) {
            if (value == null) {
                value = Objects.requireNonNull(supplier.get(), "lazy supplier returned null");
            }
            return value;
        }
    }

    public boolean isInitialized() {
        return value != null;
    }
}
