/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.config.watcher;

import com.ghatana.platform.config.ConfigSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Watches configuration files for changes and reloads them.
 *
 * @doc.type class
 * @doc.purpose Watches configuration sources for changes and triggers reload
 * @doc.layer platform
 * @doc.pattern Service
 */
public class ConfigReloadWatcher implements AutoCloseable {
    
    private static final Logger log = LoggerFactory.getLogger(ConfigReloadWatcher.class);
    
    private final ScheduledExecutorService executor;
    private final long checkIntervalMs;
    
    /**
     * Creates a new config reload watcher with default 30-second check interval.
     */
    public ConfigReloadWatcher() {
        this(30000);
    }
    
    /**
     * Creates a new config reload watcher.
     *
     * @param checkIntervalMs the interval between checks in milliseconds
     */
    public ConfigReloadWatcher(long checkIntervalMs) {
        this.checkIntervalMs = checkIntervalMs;
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "config-watcher");
            t.setDaemon(true);
            return t;
        });
    }
    
    /**
     * Watches a configuration file for changes.
     *
     * @param filePath the path to the configuration file
     * @param onReload the callback to invoke when file changes
     */
    public void watchFile(String filePath, Consumer<String> onReload) {
        File file = new File(filePath);
        if (!file.exists()) {
            log.warn("Cannot watch non-existent file: {}", filePath);
            return;
        }
        
        final long[] lastModified = { file.lastModified() };
        
        executor.scheduleAtFixedRate(() -> {
            try {
                long currentModified = file.lastModified();
                if (currentModified > lastModified[0]) {
                    lastModified[0] = currentModified;
                    log.info("Configuration file changed: {}", filePath);
                    onReload.accept(filePath);
                }
            } catch (Exception e) {
                log.error("Error checking file for changes: {}", filePath, e);
            }
        }, checkIntervalMs, checkIntervalMs, TimeUnit.MILLISECONDS);
        
        log.info("Started watching configuration file: {}", filePath);
    }
    
    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("Config reload watcher stopped");
    }
}
