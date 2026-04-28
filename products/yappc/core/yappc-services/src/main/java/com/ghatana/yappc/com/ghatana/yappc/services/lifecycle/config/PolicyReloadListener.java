/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Lifecycle Service
 */
package com.ghatana.yappc.services.lifecycle.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.StandardWatchEventKinds;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Hot-reloads policy YAML definitions whenever files under {@code policies/} change.
 *
 * <p>Policy definitions are low-risk to hot-reload because they are stateless at the
 * evaluation layer. The caller provides a reload callback (e.g., a lambda that delegates
 * to a PolicyStore or PolicyEngine extension) so this listener stays decoupled from
 * any specific engine implementation.
 *
 * <p>Files matching the pattern {@code policies/**&#47;*.yaml} or {@code policies/**&#47;*.yml}
 * are eligible for hot reload.
 *
 * @doc.type class
 * @doc.purpose Hot-reloads policy YAML definitions on file change
 * @doc.layer product
 * @doc.pattern Observer, Strategy
 */
public class PolicyReloadListener implements ConfigReloadListener {

    private static final Logger log = LoggerFactory.getLogger(PolicyReloadListener.class);

    /** Callback invoked with the changed file's bytes when a policy file changes. */
    private final Consumer<ConfigChangeEvent> reloadCallback;

    /**
     * Creates a listener that invokes the given callback on every policy file change.
     *
     * @param reloadCallback handler for the raw change event; receives the full event;
     *                       must be thread-safe
     */
    public PolicyReloadListener(Consumer<ConfigChangeEvent> reloadCallback) {
        this.reloadCallback = Objects.requireNonNull(reloadCallback, "reloadCallback");
    }

    @Override
    public boolean accepts(String relativePath) {
        return relativePath.startsWith("policies/")
                && (relativePath.endsWith(".yaml") || relativePath.endsWith(".yml"));
    }

    @Override
    public void onConfigChanged(ConfigChangeEvent event) {
        if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
            log.warn("PolicyReloadListener: policy file deleted '{}' — "
                    + "removing policies from deleted files is not yet supported; restart required.",
                    event.relativePath());
            return;
        }

        if (!Files.isReadable(event.absolutePath())) {
            log.warn("PolicyReloadListener: '{}' is not readable, skipping reload", event.absolutePath());
            return;
        }

        log.info("PolicyReloadListener: detected change in '{}', triggering reload", event.relativePath());
        reloadCallback.accept(event);
        log.info("PolicyReloadListener: reload completed for '{}'", event.relativePath());
    }
}
