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

package com.ghatana.yappc.cli.commands;

import com.ghatana.yappc.core.rca.*;
import io.activej.eventloop.Eventloop;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Day 27: CLI command for AI-powered Root Cause Analysis */
@Command(
        name = "explain-failure",
        description =
                "Analyze build failures and provide AI-powered explanations and fix suggestions")
/**
 * ExplainFailureCommand component within the YAPPC platform.
 *
 * @doc.type class
 * @doc.purpose ExplainFailureCommand component within the YAPPC platform.
 * @doc.layer platform
 * @doc.pattern Component
 */
public class ExplainFailureCommand implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(ExplainFailureCommand.class);

    @Parameters(index = "0", description = "Path to build log file to analyze")
    private String logFilePath;

    @Option(
            names = {"-t", "--tool"},
            description =
                    "Build tool type (gradle, nx, pnpm, cargo). Auto-detect if not specified.")
    private String buildTool;

    @Option(
            names = {"-p", "--project-type"},
            description = "Project type for enhanced analysis context")
    private String projectType;

    @Option(
            names = {"-v", "--verbose"},
            description = "Show detailed analysis information")
    private boolean verbose;

    @Option(
            names = {"-o", "--output"},
            description = "Output format: text (default), json, markdown")
    private String outputFormat = "text";

    private AIRCAService rcaService;
    private BuildLogNormalizerService normalizerService;

    public ExplainFailureCommand() {
        this.rcaService = new MockAIRCAService();
        this.normalizerService = new BuildLogNormalizerService();
    }

    @Override
    public Integer call() throws Exception {
        try {
            // Read the build log file
            Path logPath = Paths.get(logFilePath);
            if (!Files.exists(logPath)) {
                log.error("Error: Build log file not found: {}", logFilePath);
                return 1;
            }

            String logContent = Files.readString(logPath);

            // Determine build tool if not specified
            NormalizedBuildLog.BuildTool detectedTool =
                    buildTool != null
                            ? parseBuildTool(buildTool)
                            : normalizerService.detectBuildTool(logContent);

            if (detectedTool == null) {
                log.error("Error: Could not determine build tool. Please specify with -t option.");
                return 1;
            }

            // Normalize the build log
            NormalizedBuildLog normalizedLog =
                    normalizerService.normalize(logContent, detectedTool);
            if (normalizedLog == null) {
                log.error("Error: Failed to normalize build log for tool: {}", detectedTool);
                return 1;
            }

            // Create analysis context
            RCAContext context = createAnalysisContext();

            // Perform RCA
            if (!rcaService.isAvailable()) {
                log.error("Error: AI RCA service is not available");
                return 1;
            }

            AtomicReference<RCAResult> ref = new AtomicReference<>();
            Eventloop eventloop = Eventloop.create();
            eventloop.post(() -> rcaService.analyzeFailure(normalizedLog, context).whenResult(ref::set));
            eventloop.run();
            RCAResult rcaResult = ref.get();

            // Output results
            outputResults(rcaResult);

            return 0;

        } catch (Exception e) {
            log.error("Error analyzing build failure: {}", e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
            return 1;
        }
    }

    private NormalizedBuildLog.BuildTool parseBuildTool(String tool) {
        return switch (tool.toLowerCase()) {
            case "gradle" -> NormalizedBuildLog.BuildTool.GRADLE;
            case "nx" -> NormalizedBuildLog.BuildTool.NX;
            case "pnpm" -> NormalizedBuildLog.BuildTool.PNPM;
            case "cargo" -> NormalizedBuildLog.BuildTool.CARGO;
            default -> null;
        };
    }

    private RCAContext createAnalysisContext() {
        RCAContext.Builder builder = RCAContext.builder();

        if (projectType != null) {
            builder.projectType(projectType);
        }

        // Could add more context here like environment variables,
        // recent changes, etc. when those features are available

        return builder.build();
    }

    private void outputResults(RCAResult result) {
        switch (outputFormat.toLowerCase()) {
            case "json" -> outputJson(result);
            case "markdown" -> outputMarkdown(result);
            default -> outputText(result);
        }
    }

    private void outputText(RCAResult result) {
        log.info("🔍 Build Failure Analysis");
        log.info("═".repeat(50));
        log.info("");;

        log.info("📊 Analysis Summary:");
        log.info("  • Analysis ID: {}", result.getAnalysisId());
        log.info("  • Timestamp: {}", result.getTimestamp());
        log.info("  • Root Cause: {}", result.getRootCause());
        log.info("  • Confidence: {}", String.format("%.1f%%", result.getConfidence() * 100));
        log.info("");;

        log.info("📋 Build Status:");
        log.info("  • Tool: {}", result.getBuildLog().getTool());
        log.info("  • Status: {}", result.getBuildLog().getStatus());
        log.info("  • Errors: {}", result.getBuildLog().getErrors().size());
        log.info("  • Warnings: {}", result.getBuildLog().getWarnings().size());
        log.info("");;

        log.info("💡 Explanation:");
        log.info("{}", result.getExplanation());
        log.info("");;

        if (!result.getFixSuggestions().isEmpty()) {
            log.info("🔧 Fix Suggestions:");
            for (int i = 0; i < result.getFixSuggestions().size(); i++) {
                RCAResult.FixSuggestion suggestion = result.getFixSuggestions().get(i);
                log.info("");;
                log.info("  {}. {} [{}]", (i + 1), suggestion.getTitle(), suggestion.getPriority());
                log.info("     {}", suggestion.getDescription());

                if (!suggestion.getCommands().isEmpty()) {
                    log.info("     Commands:");
                    for (String command : suggestion.getCommands()) {
                        log.info("       $ {}", command);
                    }
                }

                log.info("     Effort: {}", suggestion.getEstimatedEffort());
            }
        }

        if (verbose && result.getMetadata() != null && !result.getMetadata().isEmpty()) {
            log.info("");
            log.info("\ud83d\udcc8 Analysis Metadata:");
            result.getMetadata().forEach((key, value) ->
                log.info("  \u2022 {}: {}", key, value));
        }
    }

    private void outputJson(RCAResult result) {
        // For now, output a simplified JSON representation
        log.info("{");
        log.info("  \"analysisId\": \"{}\",", result.getAnalysisId());
        log.info("  \"timestamp\": \"{}\",", result.getTimestamp());
        log.info("  \"rootCause\": \"{}\",", result.getRootCause());
        log.info("  \"confidence\": {},", result.getConfidence());
        log.info("  \"explanation\": \"{}\",", result.getExplanation().replace("\"", "\\\""));
        log.info("  \"fixSuggestionsCount\": {}", result.getFixSuggestions().size());
        log.info("}");
    }

    private void outputMarkdown(RCAResult result) {
        log.info("# 🔍 Build Failure Analysis");
        log.info("");;
        log.info("## 📊 Summary");
        log.info("- **Analysis ID**: {}", result.getAnalysisId());
        log.info("- **Root Cause**: {}", result.getRootCause());
        log.info("- **Confidence**: {}", String.format("%.1f%%", result.getConfidence() * 100));
        log.info("");;

        log.info("## 💡 Explanation");
        log.info("{}", result.getExplanation());
        log.info("");;

        if (!result.getFixSuggestions().isEmpty()) {
            log.info("## 🔧 Fix Suggestions");
            for (int i = 0; i < result.getFixSuggestions().size(); i++) {
                RCAResult.FixSuggestion suggestion = result.getFixSuggestions().get(i);
                log.info("### {}. {}", (i + 1), suggestion.getTitle());
                log.info("**Priority**: {} | **Effort**: {}", suggestion.getPriority(), suggestion.getEstimatedEffort());
                log.info("");;
                log.info("{}", suggestion.getDescription());

                if (!suggestion.getCommands().isEmpty()) {
                    log.info("");;
                    log.info("**Commands**:");
                    for (String command : suggestion.getCommands()) {
                        log.info("```bash");
                        log.info("{}", command);
                        log.info("```");
                    }
                }
                log.info("");;
            }
        }
    }
}
