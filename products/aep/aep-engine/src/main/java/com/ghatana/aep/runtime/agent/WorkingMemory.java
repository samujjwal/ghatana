package com.ghatana.agent.memory.model.working;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Bounded, ephemeral working memory for in-run agent state.
 * Working memory holds transient data needed during a single agent execution
 * and is not persisted across sessions.
 *
 * <p>Thread-safe. Supports LRU eviction with configurable capacity.
 *
 * @doc.type interface
 * @doc.purpose Bounded in-run working memory
 * @doc.layer agent-memory
 */
public interface WorkingMemory {

    /**
     * Stores a value in working memory.
     *
     * @param key   Key identifier
     * @param value Value to store
     */
    void put(@NotNull String key, @NotNull Object value);

    /**
     * Retrieves a value from working memory.
     *
     * @param key Key identifier
     * @return Value or null if not present
     */
    @Nullable
    Object get(@NotNull String key);

    /**
     * Retrieves a typed value from working memory.
     *
     * @param key  Key identifier
     * @param type Expected type
     * @param <T>  Value type
     * @return Typed value or null if not present or wrong type
     */
    @Nullable
    <T> T get(@NotNull String key, @NotNull Class<T> type);

    /**
     * Removes a value from working memory.
     *
     * @param key Key identifier
     * @return Previous value or null
     */
    @Nullable
    Object remove(@NotNull String key);

    /**
     * Returns all entries as an unmodifiable snapshot.
     *
     * @return All key-value pairs
     */
    @NotNull
    Map<String, Object> getAll();

    /** Current number of entries. */
    int size();

    /** Maximum number of entries allowed. */
    int capacity();

    /** Clears all entries. */
    void clear();

    /**
     * Creates an immutable snapshot of the current state.
     *
     * @return Snapshot map
     */
    @NotNull
    Map<String, Object> snapshot();

    /**
     * Registers a callback invoked when items are evicted.
     *
     * @param callback Receives evicted key and value
     */
    void onEviction(@NotNull BiConsumer<String, Object> callback);
}
