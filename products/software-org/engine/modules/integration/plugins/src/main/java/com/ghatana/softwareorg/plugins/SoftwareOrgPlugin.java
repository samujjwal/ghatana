package com.ghatana.softwareorg.plugins;

import java.util.Map;
import java.util.Set;

/**
 * SPI interface that all software-org plugins must implement.
 *
 * <p>
 * <b>Purpose</b><br>
 * Defines contract for external system integrations (GitHub, JIRA, CI/CD, CRM).
 * Plugins must declare capabilities and handle external events.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * public class GithubPlugin implements SoftwareOrgPlugin {
 *   @Override
 *   public Set<String> getCapabilities() {
 *     return Set.of("push", "pull_request", "check_run");
 *   }
 *
 *   @Override
 *   public void handleEvent(String eventType, Map<String, String> payload) {
 *     // Map GitHub event to software-org event
 *   }
 * }
 * }</pre>
 *
 * @doc.type interface
 * @doc.purpose Plugin SPI for external integrations
 * @doc.layer product
 * @doc.pattern Port
 */
public interface SoftwareOrgPlugin {

    /**
     * Returns capabilities this plugin provides.
     *
     * @return set of capability names (e.g., "push", "pull_request")
     */
    Set<String> getCapabilities();

    /**
     * Handles incoming events from external system.
     *
     * @param eventType external system event type
     * @param payload event payload as key-value map
     * @throws Exception if processing fails
     */
    void handleEvent(String eventType, Map<String, String> payload) throws Exception;

    /**
     * Validates plugin configuration.
     *
     * @param config plugin configuration
     * @return true if valid, false otherwise
     */
    boolean validateConfig(Map<String, String> config);

    /**
     * Returns plugin metadata.
     *
     * @return metadata describing this plugin
     */
    PluginMetadata getMetadata();
}
