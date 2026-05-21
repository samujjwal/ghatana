package com.ghatana.platform.plugin;

import org.jetbrains.annotations.NotNull;

/**
 * Typed topic contract for plugin event publication.
 *
 * @param <Event> event payload type
 *
 * @doc.type interface
 * @doc.purpose Versioned topic contract for plugin pub/sub
 * @doc.layer core
 * @doc.pattern Contract
 */
public interface PluginTopicContract<Event> {
    @NotNull
    String topic();

    @NotNull
    String contractId();

    @NotNull
    default String schemaVersion() {
        return "1.0.0";
    }

    @NotNull
    Class<Event> eventType();

    @NotNull
    default PluginInteractionPolicy policy() {
        return PluginInteractionPolicy.allowAll();
    }
}
