package com.ghatana.virtualorg.framework.tools.jira;

import com.ghatana.virtualorg.framework.tools.AgentTool;
import com.ghatana.virtualorg.framework.tools.ToolContext;
import com.ghatana.virtualorg.framework.tools.ToolInput;
import com.ghatana.virtualorg.framework.tools.ToolResult;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tool for searching Jira issues.
 *
 * <p>
 * <b>Purpose</b><br>
 * Allows agents to search for Jira issues using JQL (Jira Query Language).
 * Useful for finding related issues, checking status, or gathering context.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * AgentTool tool = new JiraSearchTool(jiraClient);
 *
 * ToolInput input = ToolInput.builder()
 *     .put("jql", "project = PROJ AND status = 'In Progress'")
 *     .put("max_results", 10)
 *     .build();
 *
 * ToolResult result = tool.execute(input, context).getResult();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Search Jira issues via JQL
 * @doc.layer product
 * @doc.pattern Command
 */
public class JiraSearchTool implements AgentTool {

    private static final String TOOL_NAME = "jira.search";
    private static final int DEFAULT_MAX_RESULTS = 20;
    private static final int MAX_ALLOWED_RESULTS = 100;

    private final JiraClient jiraClient;

    public JiraSearchTool(JiraClient jiraClient) {
        this.jiraClient = Objects.requireNonNull(jiraClient, "jiraClient required");
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "Search for Jira issues using JQL (Jira Query Language). "
                + "Examples: 'project = PROJ', 'assignee = currentUser()', "
                + "'status = \"In Progress\" AND priority = High'";
    }

    @Override
    public Map<String, Object> getSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "jql", Map.of(
                                "type", "string",
                                "description", "JQL query string"
                        ),
                        "project", Map.of(
                                "type", "string",
                                "description", "Filter by project key (shorthand for 'project = X')"
                        ),
                        "status", Map.of(
                                "type", "string",
                                "description", "Filter by status (shorthand for 'status = X')"
                        ),
                        "assignee", Map.of(
                                "type", "string",
                                "description", "Filter by assignee"
                        ),
                        "max_results", Map.of(
                                "type", "integer",
                                "description", "Maximum number of results (default: 20, max: 100)",
                                "default", DEFAULT_MAX_RESULTS
                        )
                ),
                "required", List.of()
        );
    }

    @Override
    public Set<String> getRequiredPermissions() {
        return Set.of("jira.read", "jira.search");
    }

    @Override
    public Promise<ToolResult> execute(ToolInput input, ToolContext context) {
        try {
            String jql = buildJql(input);
            int maxResults = Math.min(
                    input.getInt("max_results", DEFAULT_MAX_RESULTS),
                    MAX_ALLOWED_RESULTS
            );

            return jiraClient.searchIssues(jql, maxResults)
                    .map(issues -> {
                        List<Map<String, Object>> issueList = issues.stream()
                                .map(issue -> Map.<String, Object>of(
                                "key", issue.key(),
                                "summary", issue.summary(),
                                "status", issue.status() != null ? issue.status() : "Unknown",
                                "type", issue.issueType() != null ? issue.issueType() : "Unknown",
                                "priority", issue.priority() != null ? issue.priority() : "Unknown"
                        ))
                                .collect(Collectors.toList());

                        return ToolResult.success(Map.of(
                                "total", issueList.size(),
                                "jql", jql,
                                "issues", issueList
                        ));
                    })
                    .mapException(e -> new RuntimeException(
                    "Failed to search Jira: " + e.getMessage(), e));
        } catch (IllegalArgumentException e) {
            return Promise.of(ToolResult.failure("Invalid input: " + e.getMessage()));
        }
    }

    private String buildJql(ToolInput input) {
        // If explicit JQL provided, use it
        if (input.has("jql")) {
            return input.getString("jql");
        }

        // Otherwise, build from shorthand parameters
        List<String> clauses = new java.util.ArrayList<>();

        if (input.has("project")) {
            clauses.add("project = \"" + input.getString("project") + "\"");
        }

        if (input.has("status")) {
            clauses.add("status = \"" + input.getString("status") + "\"");
        }

        if (input.has("assignee")) {
            clauses.add("assignee = \"" + input.getString("assignee") + "\"");
        }

        if (clauses.isEmpty()) {
            // Default: get recent issues
            return "ORDER BY created DESC";
        }

        return String.join(" AND ", clauses) + " ORDER BY created DESC";
    }

    @Override
    public List<String> validate(ToolInput input) {
        List<String> errors = new java.util.ArrayList<>();

        // Either JQL or at least one filter should be provided
        boolean hasFilter = input.has("jql") || input.has("project")
                || input.has("status") || input.has("assignee");

        if (!hasFilter) {
            // Not an error - will return recent issues
        }

        if (input.has("max_results")) {
            int maxResults = input.getInt("max_results", DEFAULT_MAX_RESULTS);
            if (maxResults < 1 || maxResults > MAX_ALLOWED_RESULTS) {
                errors.add("max_results must be between 1 and " + MAX_ALLOWED_RESULTS);
            }
        }

        return errors;
    }
}
