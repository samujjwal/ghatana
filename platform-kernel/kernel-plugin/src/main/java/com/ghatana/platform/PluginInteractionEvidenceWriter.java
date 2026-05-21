package com.ghatana.platform.plugin;

import org.jetbrains.annotations.NotNull;

/**
 * Evidence persistence port for brokered plugin interactions.
 *
 * @doc.type interface
 * @doc.purpose Persist plugin interaction audit records to durable evidence stores
 * @doc.layer core
 * @doc.pattern Port
 */
@FunctionalInterface
public interface PluginInteractionEvidenceWriter {

    void write(@NotNull PluginInteractionAuditRecord record);

    static PluginInteractionEvidenceWriter noop() {
        return record -> {
        };
    }
}
