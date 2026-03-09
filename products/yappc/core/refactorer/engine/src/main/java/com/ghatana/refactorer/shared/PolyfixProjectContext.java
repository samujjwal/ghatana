/* Licensed under Apache-2.0 */
package com.ghatana.refactorer.shared;

import com.ghatana.refactorer.shared.service.LanguageService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.stream.Stream;
import org.apache.logging.log4j.Logger;

/**

 * @doc.type class

 * @doc.purpose Handles polyfix project context operations

 * @doc.layer core

 * @doc.pattern ValueObject

 */

public class PolyfixProjectContext {
    private final Path root;
    private final PolyfixConfig config;
    private final List<LanguageService> languages;
    private final ExecutorService exec;
    private final Logger log;

    /**
     * Arbitrary metadata bag for sharing cross-cutting context (e.g., flags like eslint.fix,
     * prettier.enabled, or module-specific helpers). This avoids tight coupling between modules.
     */
    private final Map<String, Object> meta;

    public PolyfixProjectContext(
            Path root,
            PolyfixConfig config,
            List<LanguageService> languages,
            ExecutorService exec,
            Logger log) {
        this.root = root;
        this.config = config;
        this.languages = languages;
        this.exec = exec;
        this.log = log;
        this.meta = new ConcurrentHashMap<>();
    }

    public Path root() {
        return root;
    }

    public Path getProjectRoot() {
        return root;
    }

    /**
     * Returns the working directory for the project. This is an alias for getProjectRoot() for
     * compatibility.
     */
    public Path workingDir() {
        return root;
    }

    public PolyfixConfig config() {
        return config;
    }

    public List<LanguageService> languages() {
        return languages;
    }

    public ExecutorService exec() {
        return exec;
    }

    public Logger log() {
        return log;
    }

    /**
 * Metadata map for optional features and shared objects. Thread-safe. */
    public Map<String, Object> meta() {
        return meta;
    }

    public int getMaxPasses() {
        return config != null && config.budgets() != null ? config.budgets().maxPasses() : 3;
    }

    public boolean isDryRun() {
        // Default to false if not specified
        return false;
    }

    public Set<Path> getSourceFiles() {
        if (root == null) {
            return Set.of();
        }

        try (Stream<Path> files = Files.walk(root)) {
            LinkedHashSet<Path> sourceFiles = new LinkedHashSet<>();
            files.filter(Files::isRegularFile)
                    .forEach(
                            file -> {
                                if (languages.stream()
                                        .anyMatch(lang -> supportsSafely(lang, file))) {
                                    sourceFiles.add(file);
                                }
                            });
            return sourceFiles;
        } catch (IOException e) {
            if (log != null) {
                log.warn("Failed to enumerate source files", e);
            }
            return Set.of();
        }
    }

    public List<Rule> getActiveRules() {
        return List.of();
    }

    private boolean supportsSafely(LanguageService lang, Path file) {
        try {
            return lang.supports(file);
        } catch (Exception ex) {
            if (log != null) {
                log.warn(
                        "Language service {} threw while checking support for {}",
                        lang.id(),
                        file,
                        ex);
            }
            return false;
        }
    }
}
