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

/**
 * Tool for creating Jira issues.
 *
 * <p>
 * <b>Purpose</b><br>
 * Allows agents to create Jira issues for tracking work. Supports bugs,
 * stories, tasks, and other issue types.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * AgentTool tool = new JiraCreateIssueTool(jiraClient);
 *
 * ToolInput input = ToolInput.builder()
 *     .put("project", "PROJ")
 *     .put("issue_type", "Bug")
 *     .put("summary", "Login button broken")
 *     .put("description", "Users cannot click the login button")
 *     .build();
 *
 * ToolResult result = tool.execute(input, context).getResult();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Create Jira issues
 * @doc.layer product
 * @doc.pattern Command
 */
public class JiraCreateIssueTool implements AgentTool {

    private static final String TOOL_NAME = "jira.create_issue";

    private final JiraClient jiraClient;

    public JiraCreateIssueTool(JiraClient jiraClient) {
        this.jiraClient = Objects.requireNonNull(jiraClient, "jiraClient required");
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "Create a new Jira issue. "
                + "Use this to track bugs, features, tasks, and other work items.";
    }

    @Override
    public Map<String, Object> getSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "project", Map.of(
                                "type", "string",
                                "description", "Project key (e.g., 'PROJ', 'ENG')"
                        ),
                        "issue_type", Map.of(
                                "type", "string",
                                "enum", List.of("Bug", "Story", "Task", "Epic", "Sub-task"),
                                "description", "Type of issue to create"
                        ),
                        "summary", Map.of(
                                "type", "string",
                                "description", "Brief summary/title of the issue"
                        ),
                        "description", Map.of(
                                "type", "string",
                                "description", "Detailed description of the issue"
                        ),
                        "priority", Map.of(
                                "type", "string",
                                "enum", List.of("Highest", "High", "Medium", "Low", "Lowest"),
                                "description", "Issue priority",
                                "default", "Medium"
                        )
                ),
                "required", List.of("project", "issue_type", "summary")
        );
    }

    @Override
    public Set<String> getRequiredPermissions() {
        return Set.of("jira.write", "jira.issues.create");
    }

    @Override
    public Promise<ToolResult> execute(ToolInput input, ToolContext context) {
        try {
            String project = input.getString("project");
            String issueType = input.getString("issue_type");
            String summary = input.getString("summary");
            String description = input.getString("description", "");

            return jiraClient.createIssue(project, issueType, summary, description)
                    .map(issue -> ToolResult.success(Map.of(
                    "id", issue.id(),
                    "key", issue.key(),
                    "summary", issue.summary(),
                    "status", issue.status() != null ? issue.status() : "Open",
                    "issue_type", issue.issueType(),
                    "url", buildIssueUrl(issue.key())
            )))
                    .mapException(e -> new RuntimeException(
                    "Failed to create Jira issue: " + e.getMessage(), e));
        } catch (IllegalArgumentException e) {
            return Promise.of(ToolResult.failure("Invalid input: " + e.getMessage()));
        }
    }

    @Override
    public List<String> validate(ToolInput input) {
        List<String> errors = new java.util.ArrayList<>();

        if (!input.has("project")) {
            errors.add("project is required");
        }

        if (!input.has("issue_type")) {
            errors.add("issue_type is required");
        }

        if (!input.has("summary")) {
            errors.add("summary is required");
        } else {
            String summary = input.getString("summary", "");
            if (summary.length() > 255) {
                errors.add("summary must be 255 characters or less");
            }
        }

        return errors;
    }

    private String buildIssueUrl(String key) {
        // URL would be constructed from base URL in real implementation
        return "https://jira.atlassian.net/browse/" + key;
    }
}
