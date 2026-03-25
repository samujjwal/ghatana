package com.ghatana.yappc.core.doctor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Doctor runner for checking environment and tooling requirements. Week 1, Day 3 deliverable:
 * doctor checks (JDK, Docker, Buf/protoc, Node/pnpm, Rust).
 *
 * @doc.type class
 * @doc.purpose Doctor runner for checking environment and tooling requirements. Week 1, Day 3 deliverable:
 * @doc.layer platform
 * @doc.pattern Runner
 */
public class DoctorRunner {

    private static final Logger log = LoggerFactory.getLogger(DoctorRunner.class);

    private final ObjectMapper objectMapper = JsonUtils.getDefaultMapper();

    /**
     * Run all predefined tool checks for YAPPC environment.
     *
     * @return List of check results
     */
    public List<ToolCheckResult> runAllChecks() {
        List<ToolCheck> checks =
                Arrays.asList(
                        new ToolCheck("java", Arrays.asList("java", "--version")),
                        new ToolCheck("docker", Arrays.asList("docker", "--version")),
                        new ToolCheck("buf", Arrays.asList("buf", "--version")),
                        new ToolCheck("node", Arrays.asList("node", "--version")),
                        new ToolCheck("pnpm", Arrays.asList("pnpm", "--version")),
                        new ToolCheck("cargo", Arrays.asList("cargo", "--version")));
        return runChecks(checks);
    }

    /**
 * Output check results to console in human-readable format. */
    public void outputConsole(List<ToolCheckResult> results) {
        log.info("\n🔧 Tool Status:");
        for (ToolCheckResult result : results) {
            String status = result.available() ? "✅" : "❌";
            log.info("  {} {}: {}", status, result.check().name(), result.available() ? "OK" : "MISSING/ERROR");
            if (!result.available()) {
                log.info("     Error: {}", result.output());
            }
        }
    }

    /**
 * Output check results in JSON format for machine consumption. */
    public void outputJson(List<ToolCheckResult> results) {
        try {
            log.info("{}", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(results));
        } catch (Exception e) {
            log.error("Error serializing results to JSON: {}", e.getMessage());
        }
    }

    public static List<ToolCheckResult> runChecks(List<ToolCheck> checks) {
        List<ToolCheckResult> results = new ArrayList<>();
        for (ToolCheck check : checks) {
            ProcessBuilder builder = new ProcessBuilder(check.command());
            builder.redirectErrorStream(true);
            try {
                Process process = builder.start();
                String output;
                try (BufferedReader reader =
                        new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    output =
                            reader.lines()
                                    .limit(20)
                                    .reduce((a, b) -> a + System.lineSeparator() + b)
                                    .orElse("(no output)");
                }
                int exitCode = process.waitFor();
                results.add(new ToolCheckResult(check, exitCode == 0, output));
            } catch (IOException | InterruptedException ex) {
                String message =
                        ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                results.add(new ToolCheckResult(check, false, message));
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        return results;
    }
}
