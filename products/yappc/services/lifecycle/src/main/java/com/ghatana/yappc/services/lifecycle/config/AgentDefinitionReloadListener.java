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
 * Hot-reloads agent definition YAML files whenever files under {@code agents/} change.
 *
 * <p>Agent definitions support hot add and update — a new or updated YAML file is
 * loaded into the agent registry without requiring a service restart. Running agent
 * instances are NOT affected; only new dispatch requests use the updated definition.
 *
 * <p>Workflow definitions ({@code workflows/}) are also handled here with the same
 * semantics: new templates are added, but running workflow instances are never modified.
 *
 * @doc.type class
 * @doc.purpose Hot-adds and hot-updates agent/workflow YAML definitions on file change
 * @doc.layer product
 * @doc.pattern Observer, Strategy
 */
public class AgentDefinitionReloadListener implements ConfigReloadListener {

    private static final Logger log = LoggerFactory.getLogger(AgentDefinitionReloadListener.class);

    /** Callback invoked with the full change event when an agent/workflow file changes. */
    private final Consumer<ConfigChangeEvent> reloadCallback;

    /**
     * Creates a listener that invokes the given callback for agent and workflow definition changes.
     *
     * @param reloadCallback handler for reload events; must be thread-safe
     */
    public AgentDefinitionReloadListener(Consumer<ConfigChangeEvent> reloadCallback) {
        this.reloadCallback = Objects.requireNonNull(reloadCallback, "reloadCallback");
    }

    @Override
    public boolean accepts(String relativePath) {
        boolean isAgentFile    = relativePath.startsWith("agents/")
                && (relativePath.endsWith(".yaml") || relativePath.endsWith(".yml"));
        boolean isWorkflowFile = relativePath.startsWith("workflows/")
                && (relativePath.endsWith(".yaml") || relativePath.endsWith(".yml"));
        return isAgentFile || isWorkflowFile;
    }

    @Override
    public void onConfigChanged(ConfigChangeEvent event) {
        if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
            log.warn("AgentDefinitionReloadListener: definition file deleted '{}' — "
                    + "un-registering agents from deleted files is not yet supported; "
                    + "the agent will remain in the registry until the next restart.",
                    event.relativePath());
            return;
        }

        if (!Files.isReadable(event.absolutePath())) {
            log.warn("AgentDefinitionReloadListener: '{}' is not readable, skipping reload",
                    event.absolutePath());
            return;
        }

        log.info("AgentDefinitionReloadListener: detected change in '{}', triggering reload",
                event.relativePath());
        reloadCallback.accept(event);
        log.info("AgentDefinitionReloadListener: reload completed for '{}'", event.relativePath());
    }
}
