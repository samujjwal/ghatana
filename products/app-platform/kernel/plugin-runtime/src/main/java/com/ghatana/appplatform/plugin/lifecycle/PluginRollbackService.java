/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.plugin.lifecycle;

import com.ghatana.appplatform.plugin.domain.PluginRegistration;
import com.ghatana.appplatform.plugin.domain.PluginStatus;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;

/**
 * Rolls back a failed plugin upgrade to the previously stable version (STORY-K04-013).
 *
 * <p>Rollback is the inverse hot-swap: it re-activates the previous registration
 * and marks the failed candidate as {@link PluginStatus#FAILED}. The caller supplies
 * a {@code trafficRestorer} callback that switches request routing back to the
 * previous version.
 *
 * @doc.type  class
 * @doc.purpose Rolls back a failed plugin upgrade to the previous stable version (K04-013)
 * @doc.layer kernel
 * @doc.pattern Service
 */
public final class PluginRollbackService {

    private static final Logger log = LoggerFactory.getLogger(PluginRollbackService.class);

    private final Executor executor;

    public PluginRollbackService(Executor executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    /**
     * Rolls back to {@code previous} after a failed upgrade to {@code failed}.
     *
     * @param previous         the previously stable plugin registration to restore
     * @param failed           the failed candidate registration to retire
     * @param trafficRestorer  callback that restores traffic routing to {@code previous};
     *                         returns {@code true} on success
     * @return promise resolving to a {@link RollbackResult}
     */
    public Promise<RollbackResult> rollback(PluginRegistration previous,
                                             PluginRegistration failed,
                                             BiFunction<PluginRegistration, PluginRegistration, Boolean> trafficRestorer) {
        Objects.requireNonNull(previous,        "previous");
        Objects.requireNonNull(failed,          "failed");
        Objects.requireNonNull(trafficRestorer, "trafficRestorer");

        return Promise.ofBlocking(executor, () -> {
            log.warn("Rolling back plugin={} from failed={} to previous={}",
                    previous.pluginName(), failed.version(), previous.version());

            boolean restored;
            try {
                restored = trafficRestorer.apply(failed, previous);
            } catch (Exception e) {
                log.error("Rollback traffic switch threw exception for plugin={}: {}",
                        previous.pluginName(), e.getMessage());
                restored = false;
            }

            if (!restored) {
                String reason = "Traffic restorer returned false during rollback of plugin=" + previous.pluginName();
                log.error(reason);
                return new RollbackResult(false, previous, failed.withStatus(PluginStatus.FAILED), reason);
            }

            PluginRegistration restoredPrevious = previous.withStatus(PluginStatus.ACTIVE);
            PluginRegistration markedFailed     = failed.withStatus(PluginStatus.FAILED);

            log.info("Rollback complete: plugin={} restored to version={}",
                    previous.pluginName(), previous.version());
            return new RollbackResult(true, restoredPrevious, markedFailed, null);
        });
    }

    // ── Domain types ──────────────────────────────────────────────────────────

    public record RollbackResult(
            boolean success,
            PluginRegistration restoredRegistration,
            PluginRegistration failedRegistration,
            String failureReason
    ) {}
}
