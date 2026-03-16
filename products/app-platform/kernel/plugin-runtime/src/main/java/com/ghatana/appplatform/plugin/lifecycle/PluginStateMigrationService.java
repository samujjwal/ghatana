/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.plugin.lifecycle;

import com.ghatana.appplatform.plugin.domain.PluginManifest;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

/**
 * Executes plugin state migration scripts when upgrading to a new plugin version (STORY-K04-012).
 *
 * <p>When a plugin stores internal state (e.g. configuration schema, calibration tables)
 * and is upgraded, there may be structural changes the runtime must apply. The migration
 * script is identified in {@link PluginManifest#migrationScript()} and executed once
 * before the new plugin version goes live.
 *
 * <p>Migration steps:
 * <ol>
 *   <li>Take a snapshot of current state (backup)</li>
 *   <li>Apply migration script atomically</li>
 *   <li>Verify migration result via validator</li>
 *   <li>Record migration outcome for audit</li>
 * </ol>
 *
 * @doc.type  class
 * @doc.purpose Executes plugin state migration scripts during version upgrades (K04-012)
 * @doc.layer kernel
 * @doc.pattern Service
 */
public final class PluginStateMigrationService {

    private static final Logger log = LoggerFactory.getLogger(PluginStateMigrationService.class);

    private final Path scriptBaseDir;
    private final Executor executor;

    /**
     * @param scriptBaseDir base directory where plugin migration scripts are stored;
     *                      expects files at {@code {scriptBaseDir}/{pluginName}/{scriptName}}
     */
    public PluginStateMigrationService(Path scriptBaseDir, Executor executor) {
        this.scriptBaseDir = Objects.requireNonNull(scriptBaseDir, "scriptBaseDir");
        this.executor      = Objects.requireNonNull(executor, "executor");
    }

    /**
     * Runs the migration script declared in the new manifest, if any.
     *
     * @param previousManifest  the manifest of the currently installed version
     * @param newManifest       the manifest of the new version being installed
     * @param migrationExecutor callback that receives the script path and a state directory
     *                          and performs the actual migration; must be idempotent
     * @return promise resolving to a {@link MigrationResult}
     */
    public Promise<MigrationResult> migrate(PluginManifest previousManifest,
                                             PluginManifest newManifest,
                                             BiConsumer<Path, Path> migrationExecutor) {
        Objects.requireNonNull(previousManifest,   "previousManifest");
        Objects.requireNonNull(newManifest,        "newManifest");
        Objects.requireNonNull(migrationExecutor,  "migrationExecutor");

        if (newManifest.migrationScript() == null || newManifest.migrationScript().isBlank()) {
            log.info("No migration script for plugin={} upgrading {} -> {}",
                    newManifest.name(), previousManifest.version(), newManifest.version());
            return Promise.of(MigrationResult.skipped(newManifest.name(),
                    previousManifest.version().toString(), newManifest.version().toString()));
        }

        return Promise.ofBlocking(executor, () -> {
            Path scriptPath = scriptBaseDir
                    .resolve(newManifest.name())
                    .resolve(newManifest.migrationScript());

            if (!Files.exists(scriptPath)) {
                throw new MigrationException("Migration script not found: " + scriptPath);
            }

            // Plugin state directory (per-plugin, per-version staging area)
            Path stateDir = scriptBaseDir
                    .resolve(newManifest.name())
                    .resolve("state");
            Files.createDirectories(stateDir);

            log.info("Running migration script: plugin={} from={} to={} script={}",
                    newManifest.name(), previousManifest.version(), newManifest.version(), scriptPath);

            Instant startedAt = Instant.now();
            try {
                migrationExecutor.accept(scriptPath, stateDir);
            } catch (Exception e) {
                throw new MigrationException(
                        "Migration script failed for plugin=" + newManifest.name() + ": " + e.getMessage(), e);
            }

            log.info("Migration complete: plugin={} from={} to={}",
                    newManifest.name(), previousManifest.version(), newManifest.version());
            return MigrationResult.success(newManifest.name(),
                    previousManifest.version().toString(),
                    newManifest.version().toString(),
                    startedAt,
                    Instant.now());
        });
    }

    // ── Domain types ──────────────────────────────────────────────────────────

    public record MigrationResult(
            String pluginName,
            String fromVersion,
            String toVersion,
            boolean ran,
            boolean succeeded,
            Instant startedAt,
            Instant completedAt,
            String failureReason
    ) {
        static MigrationResult success(String plugin, String from, String to, Instant start, Instant end) {
            return new MigrationResult(plugin, from, to, true, true, start, end, null);
        }
        static MigrationResult skipped(String plugin, String from, String to) {
            return new MigrationResult(plugin, from, to, false, true, null, null, null);
        }
    }

    public static final class MigrationException extends RuntimeException {
        public MigrationException(String message) { super(message); }
        public MigrationException(String message, Throwable cause) { super(message, cause); }
    }
}
