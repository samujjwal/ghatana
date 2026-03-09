package com.ghatana.virtualorg.tool.impl;

import com.ghatana.virtualorg.tool.Tool;
import com.ghatana.virtualorg.tool.ToolResult;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Git operations tool using JGit library.
 *
 * <p><b>Purpose</b><br>
 * Adapter implementing {@link Tool} for Git version control operations.
 * Wraps JGit library with ActiveJ Promise-based async execution.
 *
 * <p><b>Architecture Role</b><br>
 * Tool adapter wrapping JGit (Git Java implementation). Provides:
 * - Repository status and branch management
 * - Commit creation and history
 * - Remote operations (push, pull)
 * - Async execution via ActiveJ Eventloop
 *
 * <p><b>Supported Operations</b><br>
 * - **status**: Get repository status (modified, added, deleted files)
 * - **branch**: Create/switch branches
 * - **commit**: Commit staged changes
 * - **push**: Push commits to remote
 * - **pull**: Pull changes from remote
 * - **log**: Show commit history
 *
 * <p><b>Parameters</b><br>
 * - operation (required): Git operation to perform
 * - repoPath (required): Path to git repository
 * - branchName (optional): Branch name for branch operations
 * - message (optional): Commit message
 * - remote (optional): Remote name (default: origin)
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * GitTool git = new GitTool(eventloop, 60);
 * 
 * // Get repository status
 * ToolResult status = git.execute(Map.of(
 *     "operation", "status",
 *     "repoPath", "/path/to/repo"
 * )).getResult();
 * 
 * // Commit changes
 * ToolResult commit = git.execute(Map.of(
 *     "operation", "commit",
 *     "repoPath", "/path/to/repo",
 *     "message", "feat: add new feature"
 * )).getResult();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Git operations tool adapter using JGit
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class GitTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(GitTool.class);

    private final Eventloop eventloop;
    private final String id;
    private final int timeoutSeconds;
    private volatile boolean enabled;

    public GitTool(@NotNull Eventloop eventloop, int timeoutSeconds) {
        this.eventloop = eventloop;
        this.id = "git-tool";
        this.timeoutSeconds = timeoutSeconds;
        this.enabled = true;
    }

    @Override
    @NotNull
    public String getId() {
        return id;
    }

    @Override
    @NotNull
    public String getName() {
        return "git";
    }

    @Override
    @NotNull
    public String getDescription() {
        return "Performs Git version control operations (status, branch, commit, push, pull, log)";
    }

    @Override
    @NotNull
    public String getParameterSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "operation": {
                      "type": "string",
                      "enum": ["status", "branch", "commit", "push", "pull", "log"],
                      "description": "Git operation to perform"
                    },
                    "repoPath": {
                      "type": "string",
                      "description": "Path to git repository"
                    },
                    "branchName": {
                      "type": "string",
                      "description": "Branch name for branch operations"
                    },
                    "message": {
                      "type": "string",
                      "description": "Commit message"
                    },
                    "remote": {
                      "type": "string",
                      "default": "origin",
                      "description": "Remote name"
                    }
                  },
                  "required": ["operation", "repoPath"]
                }
                """;
    }

    @Override
    @NotNull
    public Promise<ToolResult> execute(@NotNull Map<String, String> arguments) {
        Instant start = Instant.now();

        return Promise.ofBlocking(eventloop, () -> {
            try {
                String operation = arguments.get("operation");
                String repoPath = arguments.get("repoPath");

                if (operation == null || repoPath == null) {
                    throw new IllegalArgumentException("operation and repoPath are required");
                }

                File repoDir = new File(repoPath);
                if (!repoDir.exists() || !new File(repoDir, ".git").exists()) {
                    throw new IllegalArgumentException("Invalid git repository: " + repoPath);
                }

                String result = switch (operation.toLowerCase()) {
                    case "status" -> doStatus(repoDir);
                    case "branch" -> doBranch(repoDir, arguments.get("branchName"));
                    case "commit" -> doCommit(repoDir, arguments.get("message"));
                    case "push" -> doPush(repoDir, arguments.getOrDefault("remote", "origin"));
                    case "pull" -> doPull(repoDir, arguments.getOrDefault("remote", "origin"));
                    case "log" -> doLog(repoDir, 10);
                    default -> throw new IllegalArgumentException("Unknown operation: " + operation);
                };

                Duration duration = Duration.between(start, Instant.now());
                log.debug("Git operation completed: op={}, duration={}ms", operation, duration.toMillis());

                return ToolResult.success(result, duration);

            } catch (Exception e) {
                Duration duration = Duration.between(start, Instant.now());
                log.error("Git operation failed", e);
                return ToolResult.failure("Git operation failed: " + e.getMessage(), duration);
            }
        });
    }

    @Override
    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    // =============================
    // Git operations
    // =============================

    private String doStatus(File repoDir) throws Exception {
        try (Git git = Git.open(repoDir)) {
            Status status = git.status().call();

            StringBuilder result = new StringBuilder();
            result.append("Branch: ").append(git.getRepository().getBranch()).append("\n\n");

            if (status.hasUncommittedChanges() || !status.getUntracked().isEmpty()) {
                if (!status.getModified().isEmpty()) {
                    result.append("Modified files:\n");
                    status.getModified().forEach(f -> result.append("  M ").append(f).append("\n"));
                }

                if (!status.getAdded().isEmpty()) {
                    result.append("Added files:\n");
                    status.getAdded().forEach(f -> result.append("  A ").append(f).append("\n"));
                }

                if (!status.getUntracked().isEmpty()) {
                    result.append("Untracked files:\n");
                    status.getUntracked().forEach(f -> result.append("  ? ").append(f).append("\n"));
                }
            } else {
                result.append("Working tree clean");
            }

            return result.toString();
        }
    }

    private String doBranch(File repoDir, String branchName) throws Exception {
        if (branchName == null || branchName.isEmpty()) {
            // List branches
            try (Git git = Git.open(repoDir)) {
                StringBuilder result = new StringBuilder("Branches:\n");
                for (Ref ref : git.branchList().call()) {
                    String name = ref.getName().replace("refs/heads/", "");
                    result.append("  ").append(name).append("\n");
                }
                return result.toString();
            }
        } else {
            // Create/checkout branch
            try (Git git = Git.open(repoDir)) {
                git.checkout()
                        .setCreateBranch(true)
                        .setName(branchName)
                        .call();

                return "Created and checked out branch: " + branchName;
            }
        }
    }

    private String doCommit(File repoDir, String message) throws Exception {
        if (message == null || message.isEmpty()) {
            throw new IllegalArgumentException("Commit message is required");
        }

        try (Git git = Git.open(repoDir)) {
            // Add all changes
            git.add().addFilepattern(".").call();

            // Commit
            RevCommit commit = git.commit()
                    .setMessage(message)
                    .call();

            return String.format("Committed: %s (%s)",
                    commit.getShortMessage(),
                    commit.getName().substring(0, 7));
        }
    }

    private String doPush(File repoDir, String remote) throws Exception {
        try (Git git = Git.open(repoDir)) {
            git.push()
                    .setRemote(remote)
                    .call();

            return "Pushed to " + remote;
        }
    }

    private String doPull(File repoDir, String remote) throws Exception {
        try (Git git = Git.open(repoDir)) {
            git.pull()
                    .setRemote(remote)
                    .call();

            return "Pulled from " + remote;
        }
    }

    private String doLog(File repoDir, int maxCount) throws Exception {
        try (Git git = Git.open(repoDir)) {
            Iterable<RevCommit> commits = git.log()
                    .setMaxCount(maxCount)
                    .call();

            StringBuilder result = new StringBuilder("Recent commits:\n\n");

            for (RevCommit commit : commits) {
                result.append(commit.getName().substring(0, 7))
                        .append(" - ")
                        .append(commit.getShortMessage())
                        .append(" (")
                        .append(commit.getAuthorIdent().getName())
                        .append(")\n");
            }

            return result.toString();
        }
    }
}
