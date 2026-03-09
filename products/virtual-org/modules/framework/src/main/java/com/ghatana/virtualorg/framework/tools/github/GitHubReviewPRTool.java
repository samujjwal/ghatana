package com.ghatana.virtualorg.framework.tools.github;

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
 * Tool for reviewing GitHub pull requests.
 *
 * <p>
 * <b>Purpose</b><br>
 * Allows agents to submit reviews on pull requests. Supports approve, request
 * changes, and comment actions.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * AgentTool tool = new GitHubReviewPRTool(gitHubClient);
 *
 * ToolInput input = ToolInput.builder()
 *     .put("repository", "owner/repo")
 *     .put("pr_number", 123)
 *     .put("event", "APPROVE")
 *     .put("comment", "Looks good to me!")
 *     .build();
 *
 * ToolResult result = tool.execute(input, context).getResult();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Review GitHub pull requests
 * @doc.layer product
 * @doc.pattern Command
 */
public class GitHubReviewPRTool implements AgentTool {

    private static final String TOOL_NAME = "github.review_pr";

    private final GitHubClient gitHubClient;

    public GitHubReviewPRTool(GitHubClient gitHubClient) {
        this.gitHubClient = Objects.requireNonNull(gitHubClient, "gitHubClient required");
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "Submit a review on a GitHub pull request. "
                + "Can approve, request changes, or just comment.";
    }

    @Override
    public Map<String, Object> getSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "repository", Map.of(
                                "type", "string",
                                "description", "Repository in 'owner/repo' format"
                        ),
                        "pr_number", Map.of(
                                "type", "integer",
                                "description", "The pull request number"
                        ),
                        "event", Map.of(
                                "type", "string",
                                "enum", List.of("APPROVE", "REQUEST_CHANGES", "COMMENT"),
                                "description", "The review action to take"
                        ),
                        "comment", Map.of(
                                "type", "string",
                                "description", "Review comment to include"
                        )
                ),
                "required", List.of("repository", "pr_number", "event")
        );
    }

    @Override
    public Set<String> getRequiredPermissions() {
        return Set.of("github.write", "github.pr.review");
    }

    @Override
    public Promise<ToolResult> execute(ToolInput input, ToolContext context) {
        try {
            String repository = input.getString("repository");
            int prNumber = input.getInt("pr_number");
            String eventStr = input.getString("event");
            String comment = input.getString("comment", "");

            GitHubClient.ReviewEvent event = GitHubClient.ReviewEvent.valueOf(eventStr);

            return gitHubClient.reviewPullRequest(repository, prNumber, event, comment)
                    .map(v -> ToolResult.success(Map.of(
                    "repository", repository,
                    "pr_number", prNumber,
                    "event", eventStr,
                    "status", "review_submitted"
            )))
                    .mapException(e -> new RuntimeException(
                    "Failed to review PR: " + e.getMessage(), e));
        } catch (IllegalArgumentException e) {
            return Promise.of(ToolResult.failure("Invalid input: " + e.getMessage()));
        }
    }

    @Override
    public List<String> validate(ToolInput input) {
        List<String> errors = new java.util.ArrayList<>();

        if (!input.has("repository")) {
            errors.add("repository is required");
        } else {
            String repo = input.getString("repository", "");
            if (!repo.contains("/")) {
                errors.add("repository must be in 'owner/repo' format");
            }
        }

        if (!input.has("pr_number")) {
            errors.add("pr_number is required");
        }

        if (!input.has("event")) {
            errors.add("event is required");
        } else {
            String event = input.getString("event", "");
            try {
                GitHubClient.ReviewEvent.valueOf(event);
            } catch (IllegalArgumentException e) {
                errors.add("event must be one of: APPROVE, REQUEST_CHANGES, COMMENT");
            }
        }

        return errors;
    }
}
