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
 * Tool for merging GitHub pull requests.
 *
 * <p>
 * <b>Purpose</b><br>
 * Allows agents to merge approved pull requests. Supports merge, squash, and
 * rebase strategies.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * AgentTool tool = new GitHubMergePRTool(gitHubClient);
 *
 * ToolInput input = ToolInput.builder()
 *     .put("repository", "owner/repo")
 *     .put("pr_number", 123)
 *     .put("merge_method", "squash")
 *     .build();
 *
 * ToolResult result = tool.execute(input, context).getResult();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Merge GitHub pull requests
 * @doc.layer product
 * @doc.pattern Command
 */
public class GitHubMergePRTool implements AgentTool {

    private static final String TOOL_NAME = "github.merge_pr";

    private final GitHubClient gitHubClient;

    public GitHubMergePRTool(GitHubClient gitHubClient) {
        this.gitHubClient = Objects.requireNonNull(gitHubClient, "gitHubClient required");
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "Merge a GitHub pull request. "
                + "Only works if the PR is approved and all checks pass.";
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
                        "merge_method", Map.of(
                                "type", "string",
                                "enum", List.of("merge", "squash", "rebase"),
                                "description", "How to merge the PR",
                                "default", "squash"
                        )
                ),
                "required", List.of("repository", "pr_number")
        );
    }

    @Override
    public Set<String> getRequiredPermissions() {
        return Set.of("github.write", "github.pr.merge");
    }

    @Override
    public Promise<ToolResult> execute(ToolInput input, ToolContext context) {
        try {
            String repository = input.getString("repository");
            int prNumber = input.getInt("pr_number");
            String methodStr = input.getString("merge_method", "squash").toUpperCase();

            GitHubClient.MergeMethod method = GitHubClient.MergeMethod.valueOf(methodStr);

            return gitHubClient.mergePullRequest(repository, prNumber, method)
                    .map(v -> ToolResult.success(Map.of(
                    "repository", repository,
                    "pr_number", prNumber,
                    "merge_method", methodStr.toLowerCase(),
                    "status", "merged"
            )))
                    .mapException(e -> new RuntimeException(
                    "Failed to merge PR: " + e.getMessage(), e));
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

        if (input.has("merge_method")) {
            String method = input.getString("merge_method", "").toUpperCase();
            try {
                GitHubClient.MergeMethod.valueOf(method);
            } catch (IllegalArgumentException e) {
                errors.add("merge_method must be one of: merge, squash, rebase");
            }
        }

        return errors;
    }
}
