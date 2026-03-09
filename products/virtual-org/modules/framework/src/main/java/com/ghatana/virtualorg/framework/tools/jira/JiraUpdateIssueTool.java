package com.ghatana.virtualorg.framework.tools.jira;

import com.ghatana.virtualorg.framework.tools.AgentTool;
import com.ghatana.virtualorg.framework.tools.ToolContext;
import com.ghatana.virtualorg.framework.tools.ToolInput;
import com.ghatana.virtualorg.framework.tools.ToolResult;
import io.activej.promise.Promise;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Tool for updating Jira issues.
 *
 * <p>
 * <b>Purpose</b><br>
 * Allows agents to update existing Jira issues, including: - Changing status
 * (transitioning) - Updating fields (summary, description) - Adding comments -
 * Assigning to users
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * AgentTool tool = new JiraUpdateIssueTool(jiraClient);
 *
 * ToolInput input = ToolInput.builder()
 *     .put("issue_key", "PROJ-123")
 *     .put("status", "In Progress")
 *     .put("comment", "Starting work on this issue")
 *     .build();
 *
 * ToolResult result = tool.execute(input, context).getResult();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Update Jira issues
 * @doc.layer product
 * @doc.pattern Command
 */
public class JiraUpdateIssueTool implements AgentTool {

    private static final String TOOL_NAME = "jira.update_issue";

    private final JiraClient jiraClient;

    public JiraUpdateIssueTool(JiraClient jiraClient) {
        this.jiraClient = Objects.requireNonNull(jiraClient, "jiraClient required");
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "Update an existing Jira issue. "
                + "Can change status, add comments, update fields, or assign the issue.";
    }

    @Override
    public Map<String, Object> getSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "issue_key", Map.of(
                                "type", "string",
                                "description", "Issue key (e.g., 'PROJ-123')"
                        ),
                        "status", Map.of(
                                "type", "string",
                                "description", "New status to transition to (e.g., 'In Progress', 'Done')"
                        ),
                        "comment", Map.of(
                                "type", "string",
                                "description", "Comment to add to the issue"
                        ),
                        "summary", Map.of(
                                "type", "string",
                                "description", "New summary/title for the issue"
                        ),
                        "assignee", Map.of(
                                "type", "string",
                                "description", "Account ID of user to assign to"
                        )
                ),
                "required", List.of("issue_key")
        );
    }

    @Override
    public Set<String> getRequiredPermissions() {
        return Set.of("jira.write", "jira.issues.edit");
    }

    @Override
    public Promise<ToolResult> execute(ToolInput input, ToolContext context) {
        try {
            String issueKey = input.getString("issue_key");
            String status = input.getString("status", null);
            String comment = input.getString("comment", null);
            String summary = input.getString("summary", null);
            String assignee = input.getString("assignee", null);

            // Build a chain of operations
            Promise<Void> chain = Promise.complete();

            // Update fields if provided
            Map<String, Object> fieldsToUpdate = new HashMap<>();
            if (summary != null && !summary.isEmpty()) {
                fieldsToUpdate.put("summary", summary);
            }

            if (!fieldsToUpdate.isEmpty()) {
                chain = chain.then(v -> jiraClient.updateIssue(issueKey, fieldsToUpdate));
            }

            // Transition status if provided
            if (status != null && !status.isEmpty()) {
                chain = chain.then(v -> jiraClient.transitionIssue(issueKey, status));
            }

            // Add comment if provided
            if (comment != null && !comment.isEmpty()) {
                chain = chain.then(v -> jiraClient.addComment(issueKey, comment).map(c -> null));
            }

            // Assign if provided
            if (assignee != null && !assignee.isEmpty()) {
                chain = chain.then(v -> jiraClient.assignIssue(issueKey, assignee));
            }

            return chain
                    .then(v -> jiraClient.getIssue(issueKey))
                    .map(issue -> ToolResult.success(Map.of(
                    "key", issue.key(),
                    "summary", issue.summary(),
                    "status", issue.status() != null ? issue.status() : "Unknown",
                    "updated", true
            )))
                    .mapException(e -> new RuntimeException(
                    "Failed to update Jira issue: " + e.getMessage(), e));
        } catch (IllegalArgumentException e) {
            return Promise.of(ToolResult.failure("Invalid input: " + e.getMessage()));
        }
    }

    @Override
    public List<String> validate(ToolInput input) {
        List<String> errors = new java.util.ArrayList<>();

        if (!input.has("issue_key")) {
            errors.add("issue_key is required");
        } else {
            String key = input.getString("issue_key", "");
            if (!key.matches("[A-Z]+-\\d+")) {
                errors.add("issue_key must be in format 'PROJ-123'");
            }
        }

        // At least one update field should be provided
        boolean hasUpdate = input.has("status") || input.has("comment")
                || input.has("summary") || input.has("assignee");
        if (!hasUpdate) {
            errors.add("At least one of status, comment, summary, or assignee must be provided");
        }

        return errors;
    }
}
