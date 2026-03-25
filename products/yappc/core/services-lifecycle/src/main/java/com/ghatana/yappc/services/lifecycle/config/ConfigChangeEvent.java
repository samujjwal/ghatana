/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Lifecycle Service
 */
package com.ghatana.yappc.services.lifecycle.config;

import java.nio.file.Path;
import java.nio.file.WatchEvent;

/**
 * Immutable event object describing a file-system change inside the config directory.
 *
 * <p>Delivered to all matching {@link ConfigReloadListener}s by {@link ConfigWatchService}.
 *
 * @doc.type record
 * @doc.purpose Value object for a config file-system change event
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ConfigChangeEvent(
        /** Absolute path of the changed file. */
        Path absolutePath,
        /**
         * Path relative to the config directory root, with forward-slash separators
         * (e.g., {@code "agents/definitions/planner.yaml"}).
         */
        String relativePath,
        /** WatchEvent kind: ENTRY_CREATE, ENTRY_MODIFY, or ENTRY_DELETE. */
        WatchEvent.Kind<?> kind) {
}
