package com.ghatana.products.yappc.domain.task;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * SPI for loading task definitions from various sources (YAML, plugins, DB, API).
 *
 * @doc.type interface
 * @doc.purpose Task definition provider SPI
 * @doc.layer product
 * @doc.pattern Strategy, Provider
 */
public interface TaskDefinitionProvider {
    
    /**
     * Provider name for logging/debugging.
     *
     * @return Provider name
     */
    @NotNull
    String getName();

    /**
     * Loads task definitions from this provider.
     *
     * @return Promise of task definitions
     */
    @NotNull
    Promise<List<TaskDefinition>> loadTasks();

    /**
     * Provider priority (higher = loaded first, can override lower priority).
     *
     * @return Priority value (default 100)
     */
    default int getPriority() {
        return 100;
    }

    /**
     * Whether this provider supports hot-reload.
     *
     * @return true if hot-reload is supported
     */
    default boolean supportsHotReload() {
        return false;
    }
}
