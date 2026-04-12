/*
 * Copyright (c) 2025 Ghatana Platform Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ghatana.platform.dscli.commands;

import com.ghatana.platform.dscli.model.ValidationIssue;
import com.ghatana.platform.dscli.service.TokenFileLoader;
import com.ghatana.platform.dscli.service.TokenValidator;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Validates DTCG token files for structural correctness, type conformance,
 * and alias reference integrity.
 *
 * <p>Usage: {@code ds validate ./tokens}
 *
 * @doc.type class
 * @doc.purpose Implements the 'validate' CLI command for token/contract validation.
 * @doc.layer platform
 * @doc.pattern Command
 */
@Command(
        name = "validate",
        description = "Validate DTCG token files: structure, types, alias references",
        mixinStandardHelpOptions = true)
public final class ValidateCommand implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(ValidateCommand.class);

    @Parameters(index = "0", description = "Directory containing token files to validate", defaultValue = ".")
    private Path tokensDir;

    @Option(
            names = {"--strict"},
            description = "Treat warnings as errors")
    private boolean strict;

    @Option(
            names = {"--quiet"},
            description = "Only print errors, suppress warnings and info")
    private boolean quiet;

    @Override
    public Integer call() throws Exception {
        final var loader = new TokenFileLoader();
        final var validator = new TokenValidator();

        log.info("Validating tokens in: {}", tokensDir.toAbsolutePath());
        final List<TokenFileLoader.LoadedTokenFile> files = loader.loadDirectory(tokensDir);

        if (files.isEmpty()) {
            log.warn("No token files found in {}", tokensDir);
            return 1;
        }

        final List<ValidationIssue> allIssues = new ArrayList<>();

        for (final var loaded : files) {
            final List<ValidationIssue> issues = validator.validate(loaded.tokenFile());
            for (final var issue : issues) {
                allIssues.add(issue);
                if (!quiet || issue.isError()) {
                    printIssue(loaded.path(), issue);
                }
            }
        }

        final long errors = allIssues.stream().filter(ValidationIssue::isError).count();
        final long warnings = allIssues.stream()
                .filter(i -> i.severity() == ValidationIssue.Severity.WARNING).count();

        log.info("\n{} error(s), {} warning(s) across {} file(s)",
                errors, warnings, files.size());

        if (errors > 0) {
            log.error("❌ Validation failed");
            return 1;
        }

        if (strict && warnings > 0) {
            log.error("❌ Validation failed (--strict: warnings treated as errors)");
            return 1;
        }

        log.info("✅ Validation passed");
        return 0;
    }

    private void printIssue(final Path file, final ValidationIssue issue) {
        final String prefix = switch (issue.severity()) {
            case ERROR -> "❌ ERROR";
            case WARNING -> "⚠ WARN ";
            case INFO -> "ℹ INFO ";
        };
        log.info("  {} [{}] {} at {}:{}", prefix, issue.code(), issue.message(),
                file.getFileName(), issue.path());
    }
}
