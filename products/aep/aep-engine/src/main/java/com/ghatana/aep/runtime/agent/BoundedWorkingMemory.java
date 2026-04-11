package com.ghatana.agent.memory.model.working;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiConsumer;

/**
 * LRU-bounded working memory implementation.
 * Thread-safe via {@link ReadWriteLock}. Eviction events are
 * emitted via registered callbacks.
 *
 * @doc.type class
 * @doc.purpose Bounded, thread-safe working memory
 * @doc.layer agent-memory
 */
public class BoundedWorkingMemory implements WorkingMemory {

    private final WorkingMemoryConfig config;
    private final LinkedHashMap<String, Object> store;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final ConcurrentHashMap<String, BiConsumer<String, Object>> evictionCallbacks =
            new ConcurrentHashMap<>();

    public BoundedWorkingMemory(@NotNull WorkingMemoryConfig config) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.store = new LinkedHashMap<>(config.getMaxEntries(), 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Object> eldest) {
                if (size() > config.getMaxEntries()) {
                    notifyEviction(eldest.getKey(), eldest.getValue());
                    return true;
                }
                return false;
            }
        };
    }

    /**
     * Creates a BoundedWorkingMemory with default configuration.
     */
    public BoundedWorkingMemory() {
        this(WorkingMemoryConfig.builder().build());
    }

    @Override
    public void put(@NotNull String key, @NotNull Object value) {
        lock.writeLock().lock();
        try {
            store.put(key, value);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    @Nullable
    public Object get(@NotNull String key) {
        lock.readLock().lock();
        try {
            return store.get(key);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public <T> T get(@NotNull String key, @NotNull Class<T> type) {
        Object value = get(key);
        if (value == null) {
            return null;
        }
        if (type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    @Override
    @Nullable
    public Object remove(@NotNull String key) {
        lock.writeLock().lock();
        try {
            return store.remove(key);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    @NotNull
    public Map<String, Object> getAll() {
        lock.readLock().lock();
        try {
            return Collections.unmodifiableMap(new LinkedHashMap<>(store));
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public int size() {
        lock.readLock().lock();
        try {
            return store.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public int capacity() {
        return config.getMaxEntries();
    }

    @Override
    public void clear() {
        lock.writeLock().lock();
        try {
            store.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    @NotNull
    public Map<String, Object> snapshot() {
        lock.readLock().lock();
        try {
            return Map.copyOf(store);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void onEviction(@NotNull BiConsumer<String, Object> callback) {
        evictionCallbacks.put(
                String.valueOf(System.identityHashCode(callback)),
                callback
        );
    }

    private void notifyEviction(String key, Object value) {
        evictionCallbacks.values().forEach(cb -> {
            try {
                cb.accept(key, value);
            } catch (Exception e) {
                // Eviction callbacks must not throw
            }
        });
    }
}
