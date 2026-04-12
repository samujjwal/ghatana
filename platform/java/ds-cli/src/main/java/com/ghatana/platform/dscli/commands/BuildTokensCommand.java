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

import com.ghatana.platform.dscli.service.TokenBuilder;
import com.ghatana.platform.dscli.service.TokenBuilder.BuildResult;
import com.ghatana.platform.dscli.service.TokenBuilder.OutputFormat;
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
 * Builds DTCG-compliant output (CSS, JSON, TS) from token source files.
 *
 * <p>Usage: {@code ds build-tokens ./tokens --format CSS --out ./dist/tokens.css}
 *
 * @doc.type class
 * @doc.purpose Implements the 'build-tokens' CLI command for DTCG output generation.
 * @doc.layer platform
 * @doc.pattern Command
 */
@Command(
        name = "build-tokens",
        description = "Build DTCG-compliant CSS, JSON, or TypeScript token outputs",
        mixinStandardHelpOptions = true)
public final class BuildTokensCommand implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(BuildTokensCommand.class);

    @Parameters(index = "0", description = "Directory containing .json/.yaml token files", defaultValue = ".")
    private Path tokensDir;

    @Option(
            names = {"-f", "--format"},
            description = "Output format: CSS, JSON, or TS (default: CSS)",
            defaultValue = "CSS")
    private OutputFormat format;

    @Option(
            names = {"-o", "--out"},
            description = "Output file path (default: ./dist/tokens.<ext>)")
    private Path outputFile;

    @Option(
            names = {"--theme"},
            description = "CSS scope selector (default: :root)",
            defaultValue = ":root")
    private String theme;

    @Option(
            names = {"--dry-run"},
            description = "Print output without writing to disk")
    private boolean dryRun;

    @Override
    public Integer call() throws Exception {
        final var loader = new TokenFileLoader();
        final var builder = new TokenBuilder();

        log.info("Loading tokens from: {}", tokensDir.toAbsolutePath());
        final List<TokenFileLoader.LoadedTokenFile> files = loader.loadDirectory(tokensDir);

        if (files.isEmpty()) {
            log.warn("No token files found in {}", tokensDir);
            return 1;
        }

        log.info("Found {} token file(s)", files.size());
        final BuildResult result = builder.build(files, format, theme);

        if (!result.warnings().isEmpty()) {
            result.warnings().forEach(w -> log.warn("  ⚠ {}", w));
        }

        log.info("Built {} token(s)", result.tokenCount());

        if (dryRun) {
            log.info("\n--- Preview ({}) ---\n{}", format, result.content());
            return 0;
        }

        final Path out = resolveOutputPath();
        builder.writeTo(result, out);
        log.info("✅ Written to {}", out.toAbsolutePath());
        return 0;
    }

    private Path resolveOutputPath() {
        if (outputFile != null) return outputFile;
        final String ext = switch (format) {
            case CSS -> "css";
            case JSON -> "json";
            case TS -> "ts";
        };
        return tokensDir.resolve("dist/tokens." + ext);
    }
}
