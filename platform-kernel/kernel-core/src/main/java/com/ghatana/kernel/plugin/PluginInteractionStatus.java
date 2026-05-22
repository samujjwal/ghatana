package com.ghatana.kernel.plugin;

/**
 * Status of a plugin interaction.
 *
 * <p>This enum mirrors {@link com.ghatana.kernel.interaction.ProductInteractionStatus}
 * but for plugin interactions. It represents the outcome status of plugin interaction processing.</p>
 *
 * @doc.type enum
 * @doc.purpose Status enumeration for plugin interaction outcomes
 * @doc.layer kernel
 * @doc.pattern Enumeration
 */
public enum PluginInteractionStatus {
    /**
     * Interaction succeeded.
     */
    SUCCEEDED,
    
    /**
     * Interaction was blocked by policy.
     */
    BLOCKED,
    
    /**
     * Interaction was denied by policy.
     */
    DENIED,
    
    /**
     * Interaction failed due to an error.
     */
    FAILED
}
