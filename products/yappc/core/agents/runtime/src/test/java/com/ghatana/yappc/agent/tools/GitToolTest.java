package com.ghatana.yappc.agent.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GitTool}.
 *
 * <p>Tests operate on a real {@code git} process against a temporary repository
 * initialised with {@link TempDir}.  A minimal git identity is configured via
 * environment-safe commands so tests are portable across CI environments.
 *
 * <p>No {@code EventloopTestBase} is required — GitTool is purely synchronous.
 */
@DisplayName("GitTool")
class GitToolTest {

    @TempDir
    Path repoDir;

    /** Path of the temp repo as a String for easier use in assertions. */
    private String repo;

    @BeforeEach
    void initGitRepo() throws IOException, InterruptedException { // GH-90000
        repo = repoDir.toString(); // GH-90000

        // Initialise a bare-minimum git repo so every test starts clean
        runGit(repo, "git", "init", "-b", "main"); // GH-90000
        runGit(repo, "git", "config", "user.email", "test@ghatana.ai"); // GH-90000
        runGit(repo, "git", "config", "user.name", "Test User"); // GH-90000
    }

    // ──────────────────────────── status ────────────────────────────

    @Test
    @DisplayName("status returns SUCCESS on a clean repo with no staged changes")
    void statusSucceedsOnCleanRepo() { // GH-90000
        String result = GitTool.status(repo); // GH-90000

        assertThat(result).startsWith("SUCCESS:");
    }

    @Test
    @DisplayName("status shows untracked file after creating one")
    void statusShowsUntrackedFile() throws IOException { // GH-90000
        Files.writeString(repoDir.resolve("new.txt"), "new");

        String result = GitTool.status(repo); // GH-90000

        assertThat(result).startsWith("SUCCESS:");
        assertThat(result).contains("new.txt");
    }

    // ──────────────────────────── diff ────────────────────────────

    @Test
    @DisplayName("diff returns SUCCESS with empty diff on a clean repo")
    void diffSucceedsOnCleanRepo() { // GH-90000
        String result = GitTool.diff(repo); // GH-90000

        assertThat(result).startsWith("SUCCESS:");
    }

    @Test
    @DisplayName("diff shows modified content after changing a tracked file")
    void diffShowsModifiedContent() throws IOException, InterruptedException { // GH-90000
        // Stage and commit a file first
        Path file = repoDir.resolve("tracked.txt");
        Files.writeString(file, "original"); // GH-90000
        runGit(repo, "git", "add", "tracked.txt"); // GH-90000
        runGit(repo, "git", "commit", "-m", "init"); // GH-90000

        // Modify the tracked file (not staged) // GH-90000
        Files.writeString(file, "modified"); // GH-90000

        String result = GitTool.diff(repo); // GH-90000

        assertThat(result).startsWith("SUCCESS:");
        assertThat(result).contains("tracked.txt");
    }

    // ──────────────────────────── log ────────────────────────────

    @Test
    @DisplayName("log FAILS on a repo with no commits yet")
    void logFailsWithNoCommits() { // GH-90000
        String result = GitTool.log(repo, "5"); // GH-90000

        // git log returns exit code 128 when there are no commits
        assertThat(result).startsWith("FAILED");
    }

    @Test
    @DisplayName("log returns commit message after first commit")
    void logReturnsCommitAfterFirstCommit() throws IOException, InterruptedException { // GH-90000
        Files.writeString(repoDir.resolve("a.txt"), "a");
        runGit(repo, "git", "add", "."); // GH-90000
        runGit(repo, "git", "commit", "-m", "first commit"); // GH-90000

        String result = GitTool.log(repo, "1"); // GH-90000

        assertThat(result).startsWith("SUCCESS:");
        assertThat(result).contains("first commit");
    }

    // ──────────────────────────── commit ────────────────────────────

    @Test
    @DisplayName("commit FAILS when there is nothing to commit")
    void commitFailsWithNothingStaged() { // GH-90000
        String result = GitTool.commit(repo, "empty commit"); // GH-90000

        assertThat(result).startsWith("FAILED");
    }

    @Test
    @DisplayName("commit succeeds when a file is staged")
    void commitSucceedsWithStagedFile() throws IOException, InterruptedException { // GH-90000
        Files.writeString(repoDir.resolve("staged.txt"), "content");
        runGit(repo, "git", "add", "staged.txt"); // GH-90000

        String result = GitTool.commit(repo, "add staged file"); // GH-90000

        assertThat(result).startsWith("SUCCESS:");
    }

    // ──────────────────────────── branch ────────────────────────────

    @Test
    @DisplayName("branch returns SUCCESS and lists branches after initial commit")
    void branchListsAfterInitialCommit() throws IOException, InterruptedException { // GH-90000
        Files.writeString(repoDir.resolve("init.txt"), "init");
        runGit(repo, "git", "add", "."); // GH-90000
        runGit(repo, "git", "commit", "-m", "init"); // GH-90000

        String result = GitTool.branch(repo); // GH-90000

        assertThat(result).startsWith("SUCCESS:");
        assertThat(result).contains("main");
    }

    // ──────────────────────────── checkout ────────────────────────────

    @Test
    @DisplayName("checkout FAILS for a branch that does not exist")
    void checkoutFailsForNonExistentBranch() { // GH-90000
        String result = GitTool.checkout(repo, "no-such-branch"); // GH-90000

        assertThat(result).startsWith("FAILED");
    }

    @Test
    @DisplayName("checkout succeeds when switching to an existing branch")
    void checkoutSucceedsForExistingBranch() throws IOException, InterruptedException { // GH-90000
        // Create an initial commit so branches can be created
        Files.writeString(repoDir.resolve("base.txt"), "base");
        runGit(repo, "git", "add", "."); // GH-90000
        runGit(repo, "git", "commit", "-m", "base"); // GH-90000
        runGit(repo, "git", "checkout", "-b", "feature-x"); // GH-90000

        String result = GitTool.checkout(repo, "main"); // GH-90000

        assertThat(result).startsWith("SUCCESS:");
    }

    // ──────────────────────────── clone ────────────────────────────

    @Test
    @DisplayName("clone FAILS for an invalid/unreachable URL")
    void cloneFailsForInvalidUrl() { // GH-90000
        String invalidUrl = "https://invalid.ghatana.local/nonexistent/repo.git";
        String target = repoDir.resolve("cloned").toString();

        String result = GitTool.clone(invalidUrl, target); // GH-90000

        // git clone returns non-zero exit code for unreachable URLs
        assertThat(result).satisfiesAnyOf( // GH-90000
            r -> assertThat(r).startsWith("FAILED"),
            r -> assertThat(r).startsWith("ERROR")
        );
    }

    // ──────────────────────────── helpers ────────────────────────────

    private static void runGit(String workDir, String... cmd) throws IOException, InterruptedException { // GH-90000
        new ProcessBuilder(cmd) // GH-90000
            .directory(Path.of(workDir).toFile()) // GH-90000
            .redirectErrorStream(true) // GH-90000
            .start() // GH-90000
            .waitFor(); // GH-90000
    }
}
