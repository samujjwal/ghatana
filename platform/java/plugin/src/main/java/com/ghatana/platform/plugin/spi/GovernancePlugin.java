package com.ghatana.platform.plugin.spi;

import com.ghatana.platform.governance.Governance;
import com.ghatana.platform.plugin.Plugin;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

/**
 * SPI for Governance Plugins.
 * Handles policy enforcement and validation.
 *
 * @doc.type interface
 * @doc.purpose Governance policy enforcement
 * @doc.layer core
 */
public interface GovernancePlugin extends Plugin {

    /**
     * Validates if an action is allowed by the governance policy.
     *
     * @param policy The governance policy
     * @param subject The subject attempting the action (e.g., user, service)
     * @param action The action being attempted (e.g., "read", "write")
     * @param resource The resource being accessed
     * @return A Promise resolving to true if allowed, false otherwise
     */
    @NotNull
    Promise<Boolean> isAllowed(@NotNull Governance policy, @NotNull String subject, @NotNull String action, @NotNull String resource);

    /**
     * Validates data against classification rules.
     *
     * @param policy The governance policy
     * @param data The data to validate
     * @return A Promise resolving to true if valid
     */
    @NotNull
    Promise<Boolean> validateData(@NotNull Governance policy, @NotNull Object data);
}
