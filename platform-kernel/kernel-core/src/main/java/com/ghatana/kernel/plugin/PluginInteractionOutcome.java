package com.ghatana.kernel.plugin;

import java.util.List;

/**
 * Outcome of a plugin interaction handling.
 *
 * <p>This class mirrors {@link com.ghatana.kernel.interaction.ProductInteractionOutcome}
 * but for plugin interactions. It represents the result of processing a plugin interaction.</p>
 *
 * @doc.type class
 * @doc.purpose Outcome wrapper for plugin interaction handling results
 * @doc.layer kernel
 * @doc.pattern Value Object
 */
public record PluginInteractionOutcome<T>(
    String interactionId,
    PluginInteractionStatus status,
    String reasonCode,
    List<String> evidenceRefs,
    T response
) {
    public PluginInteractionOutcome {
        if (interactionId == null || interactionId.isBlank()) {
            throw new IllegalArgumentException("interactionId must not be blank");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (evidenceRefs == null) {
            evidenceRefs = List.of();
        }
    }

    /**
     * Creates a successful outcome.
     */
    public static <T> PluginInteractionOutcome<T> succeeded(
            String interactionId,
            List<String> evidenceRefs,
            T response) {
        return new PluginInteractionOutcome<>(
            interactionId,
            PluginInteractionStatus.SUCCEEDED,
            null,
            evidenceRefs,
            response
        );
    }

    /**
     * Creates a failed outcome.
     */
    public static <T> PluginInteractionOutcome<T> failed(
            String interactionId,
            PluginInteractionStatus status,
            String reasonCode,
            List<String> evidenceRefs) {
        return new PluginInteractionOutcome<>(
            interactionId,
            status,
            reasonCode,
            evidenceRefs,
            null
        );
    }
}
