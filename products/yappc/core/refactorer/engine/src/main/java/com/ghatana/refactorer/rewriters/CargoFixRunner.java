package com.ghatana.refactorer.rewriters;

import com.ghatana.refactorer.shared.util.ProcessExec;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Wrapper for running `cargo fix` with appropriate flags.
 *
 * <p>This class provides a safe way to run `cargo fix` with appropriate flags to handle various
 * scenarios like dirty workspaces and staged changes.
 
 * @doc.type class
 * @doc.purpose Handles cargo fix runner operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public final class CargoFixRunner {

    /**
     * Runs `cargo fix` in the specified directory.
     *
     * @param cwd The working directory where `cargo fix` should be run
     * @param timeoutMillis The maximum time to wait for the command to complete, in milliseconds
     * @param allowDirty Whether to allow running in a dirty workspace
     * @return The result of the command execution
     */
    public ProcessExec.Result run(Path cwd, long timeoutMillis, boolean allowDirty) {
        Objects.requireNonNull(cwd, "cwd");

        // Build the command with appropriate flags
        List<String> cmd = new ArrayList<>();
        cmd.add("cargo");
        cmd.add("fix");

        // Add flags based on parameters
        if (allowDirty) {
            cmd.add("--allow-dirty");
            cmd.add("--allow-staged");
        }

        // Add additional flags for better behavior in CI and interactive use
        cmd.add("--message-format=json"); // JSON output for better parsing
        cmd.add("--all-targets"); // Fix all targets
        cmd.add("--all-features"); // Include all features
        cmd.add("--allow-no-vcs"); // Allow running without a VCS
        cmd.add("--edition-idioms"); // Apply idiomatic fixes for the current edition

        return ProcessExec.run(cwd, Duration.ofMillis(timeoutMillis), cmd, Map.of());
    }
}
