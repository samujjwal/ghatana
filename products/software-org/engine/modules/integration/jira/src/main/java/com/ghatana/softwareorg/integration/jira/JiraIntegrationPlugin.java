package com.ghatana.softwareorg.integration.jira;

import com.ghatana.softwareorg.plugins.PluginMetadata;
import com.ghatana.softwareorg.plugins.SoftwareOrgPlugin;
import java.util.Map;
import java.util.Set;

/**
 * JIRA integration plugin for software-org.
 *
 * <p>
 * <b>Purpose</b><br>
 * Ingests JIRA webhooks for issues and epics, maps them to software-org backlog
 * events.
 *
 * <p>
 * <b>Capabilities</b><br>
 * - issue_created: JIRA issue creation → backlog - issue_updated: JIRA issue
 * updates → backlog tracking - epic_created: JIRA epic creation → release
 * planning
 *
 * @doc.type class
 * @doc.purpose JIRA issue tracker integration
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class JiraIntegrationPlugin implements SoftwareOrgPlugin {

    private static final Set<String> CAPABILITIES = Set.of("issue_created", "issue_updated", "epic_created");
    private static final PluginMetadata METADATA = new PluginMetadata(
            "jira-integration",
            "1.0.0",
            CAPABILITIES,
            "com.ghatana.softwareorg.integration.jira.JiraIntegrationPlugin"
    );

    @Override
    public Set<String> getCapabilities() {
        return CAPABILITIES;
    }

    @Override
    public void handleEvent(String eventType, Map<String, String> payload) throws Exception {
        switch (eventType) {
            case "issue_created" ->
                handleIssueCreated(payload);
            case "issue_updated" ->
                handleIssueUpdated(payload);
            case "epic_created" ->
                handleEpicCreated(payload);
            default ->
                throw new IllegalArgumentException("Unknown event type: " + eventType);
        }
    }

    @Override
    public boolean validateConfig(Map<String, String> config) {
        return config.containsKey("jira_url") && config.containsKey("jira_token");
    }

    @Override
    public PluginMetadata getMetadata() {
        return METADATA;
    }

    private void handleIssueCreated(Map<String, String> payload) {
        // Map JIRA issue to product backlog
    }

    private void handleIssueUpdated(Map<String, String> payload) {
        // Track JIRA issue status changes
    }

    private void handleEpicCreated(Map<String, String> payload) {
        // Map JIRA epic to release planning
    }
}
