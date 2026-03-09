package com.ghatana.softwareorg.integration.github;

import com.ghatana.softwareorg.plugins.PluginMetadata;
import com.ghatana.softwareorg.plugins.SoftwareOrgPlugin;
import java.util.Map;
import java.util.Set;

/**
 * GitHub integration plugin for software-org.
 *
 * <p>
 * <b>Purpose</b><br>
 * Ingests GitHub webhook events (push, pull request, check run) and publishes
 * them to software-org event streams.
 *
 * <p>
 * <b>Capabilities</b><br>
 * - push: Repository push events → engineering department - pull_request: PR
 * opened/closed/reviewed → engineering department - check_run: Build status →
 * devops department
 *
 * @doc.type class
 * @doc.purpose GitHub webhook integration plugin
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class GithubIntegrationPlugin implements SoftwareOrgPlugin {

    private static final Set<String> CAPABILITIES = Set.of("push", "pull_request", "check_run");
    private static final PluginMetadata METADATA = new PluginMetadata(
            "github-integration",
            "1.0.0",
            CAPABILITIES,
            "com.ghatana.softwareorg.integration.github.GithubIntegrationPlugin"
    );

    @Override
    public Set<String> getCapabilities() {
        return CAPABILITIES;
    }

    @Override
    public void handleEvent(String eventType, Map<String, String> payload) throws Exception {
        switch (eventType) {
            case "push" ->
                handlePush(payload);
            case "pull_request" ->
                handlePullRequest(payload);
            case "check_run" ->
                handleCheckRun(payload);
            default ->
                throw new IllegalArgumentException("Unknown event type: " + eventType);
        }
    }

    @Override
    public boolean validateConfig(Map<String, String> config) {
        return config.containsKey("webhook_url") && config.containsKey("github_token");
    }

    @Override
    public PluginMetadata getMetadata() {
        return METADATA;
    }

    private void handlePush(Map<String, String> payload) {
        // Map GitHub push event to engineering department event
        String repo = payload.get("repository");
        String branch = payload.get("branch");
        String commits = payload.get("commits");
        // Publish to engineering event stream
    }

    private void handlePullRequest(Map<String, String> payload) {
        // Map GitHub PR event to engineering department event
        String action = payload.get("action");
        String prNumber = payload.get("pr_number");
        String title = payload.get("title");
        // Publish to engineering event stream
    }

    private void handleCheckRun(Map<String, String> payload) {
        // Map GitHub check run event to devops department event
        String status = payload.get("status");
        String conclusion = payload.get("conclusion");
        String checkName = payload.get("check_name");
        // Publish to devops event stream
    }
}
