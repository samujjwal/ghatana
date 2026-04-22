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
@DisplayName("GitTool [GH-90000]")
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
    @DisplayName("status returns SUCCESS on a clean repo with no staged changes [GH-90000]")
    void statusSucceedsOnCleanRepo() { // GH-90000
        String result = GitTool.status(repo); // GH-90000

        assertThat(result).startsWith("SUCCESS: [GH-90000]");
    }

    @Test
    @DisplayName("status shows untracked file after creating one [GH-90000]")
    void statusShowsUntrackedFile() throws IOException { // GH-90000
        Files.writeString(repoDir.resolve("new.txt [GH-90000]"), "new");

        String result = GitTool.status(repo); // GH-90000

        assertThat(result).startsWith("SUCCESS: [GH-90000]");
        assertThat(result).contains("new.txt [GH-90000]");
    }

    // ──────────────────────────── diff ────────────────────────────

    @Test
    @DisplayName("diff returns SUCCESS with empty diff on a clean repo [GH-90000]")
    void diffSucceedsOnCleanRepo() { // GH-90000
        String result = GitTool.diff(repo); // GH-90000

        assertThat(result).startsWith("SUCCESS: [GH-90000]");
    }

    @Test
    @DisplayName("diff shows modified content after changing a tracked file [GH-90000]")
    void diffShowsModifiedContent() throws IOException, InterruptedException { // GH-90000
        // Stage and commit a file first
        Path file = repoDir.resolve("tracked.txt [GH-90000]");
        Files.writeString(file, "original"); // GH-90000
        runGit(repo, "git", "add", "tracked.txt"); // GH-90000
        runGit(repo, "git", "commit", "-m", "init"); // GH-90000

        // Modify the tracked file (not staged) // GH-90000
        Files.writeString(file, "modified"); // GH-90000

        String result = GitTool.diff(repo); // GH-90000

        assertThat(result).startsWith("SUCCESS: [GH-90000]");
        assertThat(result).contains("tracked.txt [GH-90000]");
    }

    // ──────────────────────────── log ────────────────────────────

    @Test
    @DisplayName("log FAILS on a repo with no commits yet [GH-90000]")
    void logFailsWithNoCommits() { // GH-90000
        String result = GitTool.log(repo, "5"); // GH-90000

        // git log returns exit code 128 when there are no commits
        assertThat(result).startsWith("FAILED [GH-90000]");
    }

    @Test
    @DisplayName("log returns commit message after first commit [GH-90000]")
    void logReturnsCommitAfterFirstCommit() throws IOException, InterruptedException { // GH-90000
        Files.writeString(repoDir.resolve("a.txt [GH-90000]"), "a");
        runGit(repo, "git", "add", "."); // GH-90000
        runGit(repo, "git", "commit", "-m", "first commit"); // GH-90000

        String result = GitTool.log(repo, "1"); // GH-90000

        assertThat(result).startsWith("SUCCESS: [GH-90000]");
        assertThat(result).contains("first commit [GH-90000]");
    }

    // ──────────────────────────── commit ────────────────────────────

    @Test
    @DisplayName("commit FAILS when there is nothing to commit [GH-90000]")
    void commitFailsWithNothingStaged() { // GH-90000
        String result = GitTool.commit(repo, "empty commit"); // GH-90000

        assertThat(result).startsWith("FAILED [GH-90000]");
    }

    @Test
    @DisplayName("commit succeeds when a file is staged [GH-90000]")
    void commitSucceedsWithStagedFile() throws IOException, InterruptedException { // GH-90000
        Files.writeString(repoDir.resolve("staged.txt [GH-90000]"), "content");
        runGit(repo, "git", "add", "staged.txt"); // GH-90000

        String result = GitTool.commit(repo, "add staged file"); // GH-90000

        assertThat(result).startsWith("SUCCESS: [GH-90000]");
    }

    // ──────────────────────────── branch ────────────────────────────

    @Test
    @DisplayName("branch returns SUCCESS and lists branches after initial commit [GH-90000]")
    void branchListsAfterInitialCommit() throws IOException, InterruptedException { // GH-90000
        Files.writeString(repoDir.resolve("init.txt [GH-90000]"), "init");
        runGit(repo, "git", "add", "."); // GH-90000
        runGit(repo, "git", "commit", "-m", "init"); // GH-90000

        String result = GitTool.branch(repo); // GH-90000

        assertThat(result).startsWith("SUCCESS: [GH-90000]");
        assertThat(result).contains("main [GH-90000]");
    }

    // ──────────────────────────── checkout ────────────────────────────

    @Test
    @DisplayName("checkout FAILS for a branch that does not exist [GH-90000]")
    void checkoutFailsForNonExistentBranch() { // GH-90000
        String result = GitTool.checkout(repo, "no-such-branch"); // GH-90000

        assertThat(result).startsWith("FAILED [GH-90000]");
    }

    @Test
    @DisplayName("checkout succeeds when switching to an existing branch [GH-90000]")
    void checkoutSucceedsForExistingBranch() throws IOException, InterruptedException { // GH-90000
        // Create an initial commit so branches can be created
        Files.writeString(repoDir.resolve("base.txt [GH-90000]"), "base");
        runGit(repo, "git", "add", "."); // GH-90000
        runGit(repo, "git", "commit", "-m", "base"); // GH-90000
        runGit(repo, "git", "checkout", "-b", "feature-x"); // GH-90000

        String result = GitTool.checkout(repo, "main"); // GH-90000

        assertThat(result).startsWith("SUCCESS: [GH-90000]");
    }

    // ──────────────────────────── clone ────────────────────────────

    @Test
    @DisplayName("clone FAILS for an invalid/unreachable URL [GH-90000]")
    void cloneFailsForInvalidUrl() { // GH-90000
        String invalidUrl = "https://invalid.ghatana.local/nonexistent/repo.git";
        String target = repoDir.resolve("cloned [GH-90000]").toString();

        String result = GitTool.clone(invalidUrl, target); // GH-90000

        // git clone returns non-zero exit code for unreachable URLs
        assertThat(result).satisfiesAnyOf( // GH-90000
            r -> assertThat(r).startsWith("FAILED [GH-90000]"),
            r -> assertThat(r).startsWith("ERROR [GH-90000]")
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
