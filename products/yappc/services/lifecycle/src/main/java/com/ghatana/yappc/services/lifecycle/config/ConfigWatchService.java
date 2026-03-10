/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Lifecycle Service
 */
package com.ghatana.yappc.services.lifecycle.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.nio.file.StandardWatchEventKinds.*;

/**
 * Watches the {@code yappc.config.dir} directory tree for file changes and triggers
 * hot reload for supported configuration families.
 *
 * <h2>Reload Contract</h2>
 * <ul>
 *   <li><b>PolicyDefinitions</b> — hot reload (low risk, stateless evaluation)</li>
 *   <li><b>AgentDefinitions</b> — hot add / update (no downtime)</li>
 *   <li><b>WorkflowDefinitions</b> — hot add for new templates only; running
 *       workflow instances are never affected</li>
 *   <li><b>StageSpec / TransitionSpec</b> — NOT hot-reloadable; logs a warning
 *       and ignores the change (requires restart)</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * ConfigWatchService watcher = new ConfigWatchService(configDir, listeners);
 * watcher.start();
 * // ... service runs ...
 * watcher.close();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Monitors the config directory and triggers hot reload on file changes
 * @doc.layer product
 * @doc.pattern Service
 */
public class ConfigWatchService implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(ConfigWatchService.class);

    /** Maximum poll interval for the WatchService before re-registering. */
    private static final long POLL_TIMEOUT_MS = 5_000;

    /**
     * File path patterns that must NOT be hot-reloaded — a change only logs a warning.
     * These paths are relative to the config dir.
     */
    private static final Set<String> NO_HOT_RELOAD_PATTERNS = Set.of(
            "lifecycle/transitions.yaml",
            "lifecycle/stages.yaml");

    private final Path configDir;
    private final List<ConfigReloadListener> listeners;
    private final ExecutorService watchThread;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private WatchService watchService;

    /**
     * Creates a {@code ConfigWatchService} that monitors the given directory.
     *
     * @param configDir the root config directory to monitor (must exist)
     * @param listeners ordered list of reload listeners to notify on file changes
     * @throws IllegalArgumentException if {@code configDir} does not exist or is not a directory
     */
    public ConfigWatchService(Path configDir, List<ConfigReloadListener> listeners) {
        Objects.requireNonNull(configDir, "configDir");
        Objects.requireNonNull(listeners, "listeners");
        if (!Files.isDirectory(configDir)) {
            throw new IllegalArgumentException(
                    "Config directory does not exist or is not a directory: " + configDir);
        }
        this.configDir   = configDir;
        this.listeners   = List.copyOf(listeners);
        this.watchThread = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "config-watch-service");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Starts the file-watching background thread.
     *
     * <p>Idempotent — subsequent calls are no-ops.
     */
    public void start() {
        if (!running.compareAndSet(false, true)) {
            log.debug("ConfigWatchService already running, ignoring start()");
            return;
        }
        try {
            watchService = FileSystems.getDefault().newWatchService();
            registerRecursive(configDir);
        } catch (IOException e) {
            running.set(false);
            throw new IllegalStateException("Failed to initialize ConfigWatchService: " + e.getMessage(), e);
        }
        watchThread.submit(this::watchLoop);
        log.info("ConfigWatchService started — watching '{}'", configDir);
    }

    /**
     * Stops the watch service and releases all resources.
     */
    @Override
    public void close() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        try {
            if (watchService != null) {
                watchService.close();
            }
        } catch (IOException e) {
            log.warn("ConfigWatchService: error closing WatchService", e);
        }
        watchThread.shutdownNow();
        try {
            if (!watchThread.awaitTermination(2, TimeUnit.SECONDS)) {
                log.warn("ConfigWatchService: watch thread did not terminate within 2s");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        log.info("ConfigWatchService stopped");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal
    // ─────────────────────────────────────────────────────────────────────────

    private void watchLoop() {
        log.debug("ConfigWatchService watch loop started");
        while (running.get()) {
            WatchKey key;
            try {
                key = watchService.poll(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (ClosedWatchServiceException cwe) {
                break; // close() was called
            }
            if (key == null) {
                continue;
            }

            Path watchedDir = (Path) key.watchable();
            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                if (kind == OVERFLOW) {
                    log.warn("ConfigWatchService: WatchService overflow — some events may have been lost");
                    continue;
                }

                @SuppressWarnings("unchecked")
                WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                Path changedFile = watchedDir.resolve(pathEvent.context());

                if (Files.isDirectory(changedFile) && kind == ENTRY_CREATE) {
                    // Recursively register newly created sub-directories
                    try {
                        registerRecursive(changedFile);
                    } catch (IOException e) {
                        log.warn("ConfigWatchService: failed to register new directory '{}'", changedFile, e);
                    }
                    continue;
                }

                // Skip events on directories (e.g., ENTRY_MODIFY fired when files inside changed)
                if (Files.isDirectory(changedFile)) {
                    continue;
                }

                handleFileChange(changedFile, kind);
            }

            boolean valid = key.reset();
            if (!valid) {
                log.warn("ConfigWatchService: watch key invalidated for '{}' — directory may have been deleted",
                        watchedDir);
            }
        }
        log.debug("ConfigWatchService watch loop exited");
    }

    private void handleFileChange(Path changedFile, WatchEvent.Kind<?> kind) {
        String relativePath = configDir.relativize(changedFile).toString().replace('\\', '/');
        log.debug("ConfigWatchService: detected {} on '{}'", kind.name(), relativePath);

        // Check if this file requires a restart — log warning, do NOT reload
        if (requiresRestart(relativePath)) {
            log.warn(
                "ConfigWatchService: change detected in '{}' but this config family is NOT " +
                "hot-reloadable. A service restart is required for changes to take effect.",
                relativePath);
            return;
        }

        ConfigChangeEvent changeEvent = new ConfigChangeEvent(changedFile, relativePath, kind);
        for (ConfigReloadListener listener : listeners) {
            if (listener.accepts(relativePath)) {
                try {
                    listener.onConfigChanged(changeEvent);
                } catch (Exception e) {
                    log.error("ConfigWatchService: listener '{}' threw while processing change in '{}'",
                            listener.getClass().getSimpleName(), relativePath, e);
                }
            }
        }
    }

    private boolean requiresRestart(String relativePath) {
        return NO_HOT_RELOAD_PATTERNS.stream().anyMatch(relativePath::endsWith);
    }

    private void registerRecursive(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, java.nio.file.attribute.BasicFileAttributes attrs)
                    throws IOException {
                dir.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
