package com.ghatana.refactorer.shared.util;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Minimal glob helper for matching files under a root. 
 * @doc.type class
 * @doc.purpose Handles glob operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public final class Glob {
    private Glob() {}

    /**
     * Find files under root matching the provided glob pattern (e.g., "**{@literal /}*.json").
     * Directories are skipped in the result.
     */
    public static List<Path> find(Path root, String pattern) throws IOException {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(pattern, "pattern");
        final FileSystem fs = FileSystems.getDefault();
        final var matcher = fs.getPathMatcher("glob:" + pattern);
        final List<Path> results = new ArrayList<>();

        try (var stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> matcher.matches(root.relativize(p)))
                    .forEach(results::add);
        }
        return results;
    }

    /**
 * Returns a predicate usable in streams to test a path against a glob. */
    public static Predicate<Path> predicate(Path root, String pattern) {
        final FileSystem fs = FileSystems.getDefault();
        final var matcher = fs.getPathMatcher("glob:" + pattern);
        return p -> matcher.matches(root.relativize(p));
    }
}
