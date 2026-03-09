package com.ghatana.refactorer.diagnostics.python;

import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.refactorer.shared.process.ProcessResult;
import com.ghatana.refactorer.shared.process.ProcessRunner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.*;
import java.util.Objects;
import io.activej.promise.Promise;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs black for Python code formatting.
 *
 * <p>
 * Example usage:
 *
 * <pre>{@code
 * BlackRunner runner = new BlackRunner(context);
 * boolean success = runner.run().join();
 * }</pre>
 * 
 * @doc.type runner
 * @doc.language python
 * @doc.tool black
 
 * @doc.purpose Handles black runner operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public class BlackRunner {
    private static final Logger logger = LoggerFactory.getLogger(BlackRunner.class);

    private final PolyfixProjectContext context;
    private final ProcessRunner processRunner;
    private List<String> includePatterns = List.of(".");
    private List<String> ignorePatterns = List.of("**/.venv/**", "**/venv/**", "**/node_modules/**");
    private boolean checkOnly = false;
    private int lineLength = 88; // Black's default

    public BlackRunner(PolyfixProjectContext context) {
        this(context, new ProcessRunner(context));
    }

    BlackRunner(PolyfixProjectContext context, ProcessRunner processRunner) {
        this.context = Objects.requireNonNull(context, "context");
        this.processRunner = Objects.requireNonNull(processRunner, "processRunner");
    }

    public BlackRunner withCheckOnly(boolean checkOnly) {
        this.checkOnly = checkOnly;
        return this;
    }

    public BlackRunner withIncludePatterns(List<String> includePatterns) {
        this.includePatterns = includePatterns != null ? includePatterns : List.of(".");
        return this;
    }

    public BlackRunner withIgnorePatterns(List<String> ignorePatterns) {
        this.ignorePatterns = ignorePatterns != null ? ignorePatterns : List.of();
        return this;
    }

    public BlackRunner withLineLength(int lineLength) {
        this.lineLength = lineLength;
        return this;
    }

    /**
     * Runs black formatting on the project.
     * 
     * @return Promise resolving to true if formatting succeeded
     * @doc.type method
     * @doc.promise ActiveJ Promise for async Black formatting
     */
    public Promise<Boolean> run() {
        return Promise.ofBlocking(context.exec(), () -> {
            try {
                Path projectRoot = context.root();
                List<String> command = buildBlackCommand();

                logger.debug("Running black: {}", String.join(" ", command));
                ProcessResult result = processRunner.execute(
                        command.get(0),
                        command.subList(1, command.size()),
                        projectRoot,
                        true);

                if (result.exitCode() != 0) {
                    logger.error("Black failed: {}", result.error());
                    return false;
                }

                return true;
            } catch (Exception e) {
                logger.error("Error running black", e);
                return false;
            }
        });
    }

    /**
     * Returns true if the 'black' formatter is available on PATH.
     */
    public boolean isAvailable() {
        try {
            ProcessResult check = processRunner.execute("black", List.of("--version"), context.root(), true);
            return check.exitCode() == 0;
        } catch (Exception e) {
            logger.debug("Black availability check failed", e);
            return false;
        }
    }

    private List<String> buildBlackCommand() {
        List<String> command = new ArrayList<>();
        command.add("black");

        if (checkOnly) {
            command.add("--check");
        }

        command.add("--quiet");
        command.add("--line-length");
        command.add(String.valueOf(lineLength));

        // Add include patterns
        includePatterns.forEach(command::add);

        // Add exclude patterns
        if (!ignorePatterns.isEmpty()) {
            command.add("--exclude");
            command.add(String.join("|", ignorePatterns));
        }

        return command;
    }

    /**
     * Get a list of Python files that would be processed by Black.
     */
    public List<Path> findPythonFiles() throws Exception {
        Path projectRoot = context.getProjectRoot();
        List<Path> files = new ArrayList<>();

        for (String pattern : includePatterns) {
            if (isGlobPattern(pattern)) {
                PathMatcher matcher = projectRoot
                        .getFileSystem()
                        .getPathMatcher("glob:" + normalizeGlob(pattern));
                try (Stream<Path> stream = Files.walk(projectRoot)) {
                    stream.filter(Files::isRegularFile)
                            .filter(p -> p.toString().endsWith(".py"))
                            .filter(p -> matcher.matches(projectRoot.relativize(p)))
                            .filter(p -> !isIgnored(relativize(projectRoot, p)))
                            .forEach(files::add);
                }
            } else {
                Path path = projectRoot.resolve(pattern).normalize();

                if (Files.isDirectory(path)) {
                    try (Stream<Path> stream = Files.walk(path)) {
                        stream.filter(Files::isRegularFile)
                                .filter(p -> p.toString().endsWith(".py"))
                                .filter(p -> !isIgnored(relativize(projectRoot, p)))
                                .forEach(files::add);
                    }
                } else if (Files.isRegularFile(path)
                        && path.toString().endsWith(".py")
                        && !isIgnored(relativize(projectRoot, path))) {
                    files.add(path);
                }
            }
        }

        return files;
    }

    private static boolean isGlobPattern(String pattern) {
        return pattern.contains("*") || pattern.contains("?") || pattern.contains("[");
    }

    private static String normalizeGlob(String pattern) {
        if (pattern.startsWith("./")) {
            return pattern.substring(2);
        }
        return pattern;
    }

    private static String relativize(Path root, Path file) {
        return root.relativize(file).toString();
    }

    private boolean isIgnored(String relativePath) {
        return ignorePatterns.stream()
                .anyMatch(
                        pattern -> {
                            // Simple glob matching - for production, use a proper glob library
                            String regex = pattern.replace(".", "\\.")
                                    .replace("**", ".*")
                                    .replace("*", "[^/]*");
                            return relativePath.matches(regex);
                        });
    }
}
