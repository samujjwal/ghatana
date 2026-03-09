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
 * Tool for creating GitHub pull requests.
 *
 * <p>
 * <b>Purpose</b><br>
 * Allows agents to create pull requests on GitHub repositories. Used by
 * engineering agents to propose code changes.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * AgentTool tool = new GitHubCreatePRTool(gitHubClient);
 *
 * ToolInput input = ToolInput.builder()
 *     .put("repository", "owner/repo")
 *     .put("head", "feature-branch")
 *     .put("base", "main")
 *     .put("title", "Add new feature")
 *     .put("description", "This PR adds...")
 *     .build();
 *
 * ToolResult result = tool.execute(input, context).getResult();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Create GitHub pull requests
 * @doc.layer product
 * @doc.pattern Command
 */
public class GitHubCreatePRTool implements AgentTool {

    private static final String TOOL_NAME = "github.create_pr";

    private final GitHubClient gitHubClient;

    public GitHubCreatePRTool(GitHubClient gitHubClient) {
        this.gitHubClient = Objects.requireNonNull(gitHubClient, "gitHubClient required");
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "Create a pull request on a GitHub repository. "
                + "Use this to propose code changes from one branch to another.";
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
                        "head", Map.of(
                                "type", "string",
                                "description", "The branch containing your changes"
                        ),
                        "base", Map.of(
                                "type", "string",
                                "description", "The branch you want to merge into (e.g., 'main')"
                        ),
                        "title", Map.of(
                                "type", "string",
                                "description", "Title of the pull request"
                        ),
                        "description", Map.of(
                                "type", "string",
                                "description", "Description/body of the pull request"
                        ),
                        "draft", Map.of(
                                "type", "boolean",
                                "description", "Whether to create as a draft PR",
                                "default", false
                        )
                ),
                "required", List.of("repository", "head", "base", "title")
        );
    }

    @Override
    public Set<String> getRequiredPermissions() {
        return Set.of("github.write", "github.pr.create");
    }

    @Override
    public Promise<ToolResult> execute(ToolInput input, ToolContext context) {
        try {
            String repository = input.getString("repository");
            String head = input.getString("head");
            String base = input.getString("base");
            String title = input.getString("title");
            String description = input.getString("description", "");

            return gitHubClient.createPullRequest(repository, head, base, title, description)
                    .map(pr -> ToolResult.success(Map.of(
                    "number", pr.number(),
                    "title", pr.title(),
                    "url", pr.url(),
                    "state", pr.state(),
                    "head_branch", pr.headBranch(),
                    "base_branch", pr.baseBranch()
            )))
                    .mapException(e -> new RuntimeException(
                    "Failed to create PR: " + e.getMessage(), e));
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

        if (!input.has("head")) {
            errors.add("head branch is required");
        }

        if (!input.has("base")) {
            errors.add("base branch is required");
        }

        if (!input.has("title")) {
            errors.add("title is required");
        }

        return errors;
    }
}
