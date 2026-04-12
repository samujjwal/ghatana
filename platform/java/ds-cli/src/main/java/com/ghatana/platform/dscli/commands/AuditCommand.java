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
import com.ghatana.platform.dscli.service.TokenAuditor;
import com.ghatana.platform.dscli.service.TokenFileLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Audits design system token sets for governance compliance: duplicate detection,
 * a11y coverage, and naming convention enforcement.
 *
 * <p>Usage: {@code ds audit ./tokens}
 *
 * @doc.type class
 * @doc.purpose Implements the 'audit' CLI command for token governance checks.
 * @doc.layer platform
 * @doc.pattern Command
 */
@Command(
        name = "audit",
        description = "Audit token sets for duplicates, a11y coverage, and naming conventions",
        mixinStandardHelpOptions = true)
public final class AuditCommand implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(AuditCommand.class);

    @Parameters(index = "0", description = "Directory containing token files to audit", defaultValue = ".")
    private Path tokensDir;

    @Option(
            names = {"--fail-on-warning"},
            description = "Exit with non-zero code if any warnings are found")
    private boolean failOnWarning;

    @Option(
            names = {"--skip-a11y"},
            description = "Skip accessibility coverage checks")
    private boolean skipA11y;

    @Option(
            names = {"--skip-duplicates"},
            description = "Skip duplicate value detection")
    private boolean skipDuplicates;

    @Override
    public Integer call() throws Exception {
        final var loader = new TokenFileLoader();
        final var auditor = new TokenAuditor();

        log.info("Auditing tokens in: {}", tokensDir.toAbsolutePath());
        final List<TokenFileLoader.LoadedTokenFile> files = loader.loadDirectory(tokensDir);

        if (files.isEmpty()) {
            log.warn("No token files found in {}", tokensDir);
            return 1;
        }

        List<ValidationIssue> issues = auditor.audit(files);

        // Apply skip filters
        if (skipA11y) {
            issues = issues.stream()
                    .filter(i -> !i.code().equals("MISSING_A11Y_PAIRS"))
                    .toList();
        }
        if (skipDuplicates) {
            issues = issues.stream()
                    .filter(i -> !i.code().equals("DUPLICATE_VALUE"))
                    .toList();
        }

        printReport(issues, files.size());

        final long errors = issues.stream().filter(ValidationIssue::isError).count();
        final long warnings = issues.stream()
                .filter(i -> i.severity() == ValidationIssue.Severity.WARNING).count();

        if (errors > 0) {
            log.error("❌ Audit failed ({} error(s))", errors);
            return 1;
        }
        if (failOnWarning && warnings > 0) {
            log.error("❌ Audit failed (--fail-on-warning: {} warning(s))", warnings);
            return 1;
        }

        log.info("✅ Audit passed ({} warning(s))", warnings);
        return 0;
    }

    private void printReport(final List<ValidationIssue> issues, final int fileCount) {
        log.info("\n=== Audit Report ({} file(s)) ===", fileCount);

        if (issues.isEmpty()) {
            log.info("  No issues found.");
            return;
        }

        for (final var issue : issues) {
            final String prefix = switch (issue.severity()) {
                case ERROR -> "❌";
                case WARNING -> "⚠";
                case INFO -> "ℹ";
            };
            log.info("  {} [{}] {} ({})", prefix, issue.code(), issue.message(), issue.path());
        }

        final long errors = issues.stream().filter(ValidationIssue::isError).count();
        final long warnings = issues.stream()
                .filter(i -> i.severity() == ValidationIssue.Severity.WARNING).count();
        log.info("\nTotal: {} error(s), {} warning(s)", errors, warnings);
    }
}
