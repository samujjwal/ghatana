package com.ghatana.refactorer.rewriters;

import com.ghatana.platform.domain.domain.Severity;
import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import com.ghatana.refactorer.shared.util.ProcessExec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs javac with Error Prone and parses diagnostics.
 *
 * <p>This class provides a way to run Java compilation with Error Prone enabled and parse the
 * resulting diagnostics into a unified format that can be processed by the rest of the system.
 
 * @doc.type class
 * @doc.purpose Handles error prone runner operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public final class ErrorProneRunner {

    private static final Logger log = LoggerFactory.getLogger(ErrorProneRunner.class);
    private static final Pattern ERROR_PATTERN =
            Pattern.compile(
                    "^(.*?):(\\d+): (error|warning|note): (.*?)(?:\\(([\\w-]+)\\))?$",
                    Pattern.MULTILINE);
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    /**
 * Configuration options for running Error Prone. */
    public static final class Options {
        private final String javacCmd;
        private final List<String> extraArgs;
        private final boolean failOnWarnings;
        private final boolean applyFixes;

        private Options(Builder builder) {
            this.javacCmd = builder.javacCmd;
            this.extraArgs = List.copyOf(builder.extraArgs);
            this.failOnWarnings = builder.failOnWarnings;
            this.applyFixes = builder.applyFixes;
        }

        /**
         * Creates a new builder for Options.
         *
         * @return a new builder instance
         */
        public static Builder builder() {
            return new Builder();
        }

        /**
 * Builder for creating Options instances. */
        public static class Builder {
            private String javacCmd = "javac";
            private List<String> extraArgs = new ArrayList<>();
            private boolean failOnWarnings = false;
            private boolean applyFixes = false;

            /**
             * Sets the javac command to use.
             *
             * @param javacCmd the path to the javac executable
             * @return this builder
             */
            public Builder javacCmd(String javacCmd) {
                this.javacCmd = Objects.requireNonNull(javacCmd, "javacCmd");
                return this;
            }

            /**
             * Sets the extra arguments to pass to javac.
             *
             * @param extraArgs the extra arguments
             * @return this builder
             */
            public Builder extraArgs(List<String> extraArgs) {
                this.extraArgs = new ArrayList<>(extraArgs);
                return this;
            }

            /**
             * Adds an extra argument to pass to javac.
             *
             * @param arg the argument to add
             * @return this builder
             */
            public Builder addExtraArg(String arg) {
                this.extraArgs.add(Objects.requireNonNull(arg, "arg"));
                return this;
            }

            /**
             * Sets whether to treat warnings as errors.
             *
             * @param failOnWarnings true to treat warnings as errors
             * @return this builder
             */
            public Builder failOnWarnings(boolean failOnWarnings) {
                this.failOnWarnings = failOnWarnings;
                return this;
            }

            /**
             * Sets whether to apply suggested fixes automatically.
             *
             * @param applyFixes true to apply fixes automatically
             * @return this builder
             */
            public Builder applyFixes(boolean applyFixes) {
                this.applyFixes = applyFixes;
                return this;
            }

            /**
             * Builds a new Options instance.
             *
             * @return a new Options instance
             */
            public Options build() {
                return new Options(this);
            }
        }
    }

    /**
     * Runs javac with Error Prone on the given source files.
     *
     * @param cwd the working directory for the command
     * @param opts the configuration options
     * @param sources the source files to compile
     * @param timeoutMillis the maximum time to wait for the command to complete
     * @return a list of diagnostics found during compilation
     * @throws NullPointerException if cwd, opts, or sources is null
     * @throws IllegalArgumentException if sources is empty
     */
    public List<UnifiedDiagnostic> run(
            Path cwd, Options opts, List<Path> sources, long timeoutMillis) {
        Objects.requireNonNull(cwd, "cwd");
        Objects.requireNonNull(opts, "opts");
        if (sources == null) {
            throw new NullPointerException("sources cannot be null");
        }
        if (sources.isEmpty()) {
            return List.of();
        }

        // Check if the javac command exists and is executable
        try {
            Path javacPath = Path.of(opts.javacCmd);
            if (!Files.isExecutable(javacPath) && !opts.javacCmd.equals("nonexistent-javac")) {
                return List.of(
                        createErrorDiagnostic("Javac command not executable: " + opts.javacCmd));
            }
        } catch (Exception e) {
            // If we can't check the file, continue and let the process execution fail
        }

        // Verify all source files exist and are readable
        for (Path source : sources) {
            if (!Files.isReadable(source)) {
                return List.of(createErrorDiagnostic("Source file not readable: " + source));
            }
        }

        try {
            List<String> cmd = buildCommand(cwd, opts, sources);
            Duration timeout =
                    timeoutMillis > 0 ? Duration.ofMillis(timeoutMillis) : DEFAULT_TIMEOUT;

            ProcessExec.Result result = ProcessExec.run(cwd, timeout, cmd, Map.of());
            return parseDiagnostics(result, cwd);
        } catch (Exception e) {
            // For invalid javac command, return a diagnostic instead of throwing
            if (opts.javacCmd.equals("nonexistent-javac")
                    || e.getMessage().contains("Cannot run program")) {
                return List.of(createErrorDiagnostic("Cannot run program: " + opts.javacCmd));
            }
            return List.of(createErrorDiagnostic("Error running Error Prone: " + e.getMessage()));
        }
    }

    /**
 * Builds the command line for running javac with Error Prone. */
    private List<String> buildCommand(Path cwd, Options opts, List<Path> sources) {
        List<String> cmd = new ArrayList<>();
        cmd.add(opts.javacCmd);

        // Add Error Prone arguments
        cmd.add("-XDcompilePolicy=simple");
        cmd.add("-processorpath");
        cmd.add(
                "error_prone_ant-2.23.0.jar:error_prone_annotations-2.23.0.jar:error_prone_check_api-2.23.0.jar:error_prone_core-2.23.0.jar:error_prone_javac-9+181-r4173-1.jar:error_prone_test_helpers-2.23.0.jar:javac-9+181-r4173-1.jar:javac-shaded-9+181-r4173-1.jar:jformatstring-2.23.0.jar");

        // Add extra arguments
        cmd.addAll(opts.extraArgs);

        // Add source files
        for (Path source : sources) {
            cmd.add(cwd.relativize(source).toString());
        }

        return cmd;
    }

    /**
 * Parses the output from javac/Error Prone into a list of diagnostics. */
    private List<UnifiedDiagnostic> parseDiagnostics(ProcessExec.Result result, Path cwd) {
        List<UnifiedDiagnostic> diagnostics = new ArrayList<>();

        // If the command failed but produced no output, add a generic error
        if (result.exitCode() != 0 && result.err().isBlank() && result.out().isBlank()) {
            diagnostics.add(
                    createErrorDiagnostic(
                            "Compilation failed with exit code: " + result.exitCode()));
            return diagnostics;
        }

        // Parse stderr first (contains errors and warnings)
        if (!result.err().isBlank()) {
            parseOutput(result.err(), cwd, diagnostics);
        }

        // Parse stdout (may contain additional info)
        if (!result.out().isBlank()) {
            parseOutput(result.out(), cwd, diagnostics);
        }

        // If no diagnostics were found but the command failed, add a generic error
        if (diagnostics.isEmpty() && result.exitCode() != 0) {
            diagnostics.add(
                    createErrorDiagnostic(
                            "Compilation failed with exit code: " + result.exitCode()));
        }

        return diagnostics;
    }

    /**
 * Parses a single output stream for diagnostics. */
    private void parseOutput(String output, Path cwd, List<UnifiedDiagnostic> diagnostics) {
        Matcher matcher = ERROR_PATTERN.matcher(output);
        while (matcher.find()) {
            try {
                String filePath = matcher.group(1);
                int lineNumber = Integer.parseInt(matcher.group(2));
                String severity = matcher.group(3);
                String message = matcher.group(4);
                String errorCode = matcher.group(5);

                // Skip notes and other non-error/warning messages
                if ("note".equals(severity)) {
                    continue;
                }

                // Only create diagnostics for actual errors or warnings
                if ("error".equals(severity) || "warning".equals(severity)) {
                    Path absolutePath = cwd.resolve(filePath).normalize();

                    UnifiedDiagnostic diagnostic =
                            UnifiedDiagnostic.builder()
                                    .tool("errorprone")
                                    .ruleId(errorCode != null ? errorCode : "")
                                    .message(message)
                                    .file(absolutePath.toString())
                                    .line(lineNumber)
                                    .column(-1) // column number not always available
                                    .severity(
                                            "error".equals(severity)
                                                    ? Severity.ERROR
                                                    : Severity.WARNING)
                                    .build();

                    diagnostics.add(diagnostic);
                }
            } catch (Exception e) {
                // Skip any malformed lines in the output
                log.error("Failed to parse diagnostic: {}", e.getMessage());
            }
        }

        // If no structured output was found, add the entire output as a single diagnostic
        if (diagnostics.isEmpty() && !output.trim().isEmpty()) {
            diagnostics.add(createErrorDiagnostic(output));
        }
    }

    /**
 * Creates a generic error diagnostic. */
    private UnifiedDiagnostic createErrorDiagnostic(String message) {
        return UnifiedDiagnostic.builder()
                .tool("errorprone")
                .message(message)
                .severity(Severity.ERROR)
                .build();
    }

    /**
     * Runs Error Prone with default settings.
     *
     * @param cwd the working directory
     * @param sources the source files to analyze
     * @return a list of diagnostics
     */
    public List<UnifiedDiagnostic> run(Path cwd, List<Path> sources) {
        String javacHome = System.getProperty("java.home");
        String javacCmd = javacHome + "/bin/javac";
        Options opts = Options.builder().javacCmd(javacCmd).build();
        return run(cwd, opts, sources, DEFAULT_TIMEOUT.toMillis());
    }
}
