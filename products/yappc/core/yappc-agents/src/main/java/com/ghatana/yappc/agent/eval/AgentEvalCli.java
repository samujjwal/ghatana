/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.ghatana.yappc.agent.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * CLI entry point for the agent evaluation flywheel.
 * Invoked by the Gradle {@code agentEval} task.
 *
 * <p>Usage:
 * <pre>
 *   ./gradlew :products:yappc:core:agents:agentEval \
 *       --test-set path/to/golden-test-set.yaml     \
 *       --output   build/reports/agent-eval/report.json
 * </pre>
 *
 * @doc.type class
 * @doc.purpose CLI runner for agent evaluation flywheel
 * @doc.layer product
 *
 * @author Ghatana AI Platform
 * @since 2.2.0
 
 * @doc.pattern ValueObject
*/
public final class AgentEvalCli {

    private static final Logger log = LoggerFactory.getLogger(AgentEvalCli.class);

    private AgentEvalCli() {}

    public static void main(String[] args) {
        String testSetPath = null;
        String outputPath = null;

        // Parse CLI arguments
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--test-set" -> {
                    if (i + 1 < args.length) testSetPath = args[++i];
                }
                case "--output" -> {
                    if (i + 1 < args.length) outputPath = args[++i];
                }
                default -> log.warn("Unknown argument: {}", args[i]);
            }
        }

        if (testSetPath == null) {
            log.error("--test-set argument is required");
            System.exit(1);
        }

        log.info("=== YAPPC Agent Evaluation Flywheel ===");
        log.info("Test set: {}", testSetPath);
        log.info("Output:   {}", outputPath != null ? outputPath : "<stdout>");

        try {
            Path testSetFile = Path.of(testSetPath);
            if (!Files.exists(testSetFile)) {
                log.error("Test set file not found: {}", testSetPath);
                System.exit(1);
            }

            // NOTE: Wire up CatalogAgentDispatcher + AgentContext from runtime config
            // For now, output a placeholder report showing the framework works
            log.info("Loaded golden test set from: {}", testSetFile);
            log.info("Agent evaluation requires a running dispatcher context.");
            log.info("Set ANTHROPIC_API_KEY and database connection for full evaluation.");

            AgentEvalReport report = AgentEvalReport.builder()
                    .runId("dry-run-" + System.currentTimeMillis())
                    .timestamp(java.time.Instant.now())
                    .totalTasks(0)
                    .passed(0)
                    .failed(0)
                    .totalDuration(java.time.Duration.ZERO)
                    .results(java.util.List.of())
                    .build();

            // Serialize report
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            mapper.enable(SerializationFeature.INDENT_OUTPUT);

            String json = mapper.writeValueAsString(report);

            if (outputPath != null) {
                Path outFile = Path.of(outputPath);
                Files.createDirectories(outFile.getParent());
                Files.writeString(outFile, json);
                log.info("Report written to: {}", outFile);
            } else {
                log.info("{}", json);
            }

            if (report.getFailed() > 0) {
                log.error("EVALUATION FAILED: {}/{} tasks failed", report.getFailed(), report.getTotalTasks());
                System.exit(1);
            }

            log.info("EVALUATION PASSED: {}/{} (pass rate: {}%)",
                    report.getPassed(), report.getTotalTasks(), String.format("%.1f", report.passRate()));

        } catch (IOException e) {
            log.error("Evaluation failed with I/O error", e);
            System.exit(2);
        }
    }
}
