package com.ghatana.core.activej.promise;

import io.activej.promise.Promise;
import java.util.List;

/**
 * Minimal compatibility facade exposing commonly-used Promise helpers in a
 * stable, project-local place.
 *
 * This delegates to {@link PromiseUtils} which contains robust helpers already
 * present in this module.
 *
 * @doc.type class
 * @doc.purpose Minimal compatibility facade exposing commonly-used ActiveJ Promise helpers
 * @doc.layer platform
 * @doc.pattern Utility
 */
public final class PromiseCompat {

    private PromiseCompat() {
        // utility
    }

    @SafeVarargs
    public static <T> Promise<List<T>> all(Promise<T>... promises) {
        return PromiseUtils.all(promises);
    }

    public static <T> Promise<List<T>> all(java.util.List<Promise<T>> promises) {
        return PromiseUtils.all(promises);
    }

    /**
     * Compatibility helper that accepts heterogeneous promise types and returns
     * a Promise of Object list. Call sites that combine different typed
     * promises should use {@link #allMixed(Promise[])}.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Promise<java.util.List<Object>> allMixed(Promise<?>... promises) {
        return (Promise) PromiseUtils.all((Promise[]) promises);
    }
}
