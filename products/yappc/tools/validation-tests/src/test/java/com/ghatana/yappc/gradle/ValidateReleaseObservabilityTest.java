package com.ghatana.yappc.gradle;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gradle TestKit integration tests for the {@code validateReleaseObservability} task.
 *
 * @doc.type class
 * @doc.purpose Gradle TestKit tests for release observability gating
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("validateReleaseObservability Gradle task")
class ValidateReleaseObservabilityTest {

    @TempDir
    Path projectDir;

    private String validationScriptPath;

    @BeforeEach
    void setUp() throws IOException {
        validationScriptPath = System.getProperty("validationScriptPath");
        if (validationScriptPath == null || validationScriptPath.isBlank()) {
            throw new IllegalStateException("System property 'validationScriptPath' is not set.");
        }

        Files.writeString(projectDir.resolve("settings.gradle.kts"),
                "rootProject.name = \"validate-release-observability-test\"\n");
        String escaped = validationScriptPath.replace("\\", "\\\\");
        Files.writeString(projectDir.resolve("build.gradle.kts"),
                "apply(from = \"" + escaped + "\")\n");
    }

    @Test
    @DisplayName("passes when current observability release surfaces are present")
    void shouldPassForCurrentObservabilityReleaseSurfaces() throws IOException {
        writeFile("prometheus.yappc.yml", """
                scrape_configs:
                  - job_name: \"yappc-backend\"
                    metrics_path: \"/metrics\"
                """);
        writeFile("deployment/monitoring/alerts/yappc.yml", """
                groups:
                  - name: yappc.ai
                    rules:
                      - alert: YappcAiProviderLatencyHigh
                        expr: rate(yappc_ai_llm_latency_seconds_sum[5m]) / rate(yappc_ai_llm_latency_seconds_count[5m]) > 5
                      - alert: YappcAiFallbackRateHigh
                        expr: rate(yappc_ai_fallback_total[5m]) > 0
                      - alert: YappcAiInferenceFailureRateHigh
                        expr: rate(yappc_ai_inference_failed_total[5m]) > 0
                """);
        writeFile("docs/RELEASE_READINESS_CHECKLIST.md", """
                # Checklist
                - AI observability sign-off
                - release evidence bundle
                """);
        writeFile("docs/operations/ONCALL_RUNBOOK.md", """
                # Runbook
                Check /health/readiness and /metrics during rollback.
                """);
        writeFile("docs/LLM_OBSERVABILITY.md", """
                # Observability
                Propagate X-Correlation-ID.
                Metrics:
                - yappc.ai.llm.latency.seconds
                - yappc.ai.fallback.total
                - yappc.ai.inference.failed
                """);

        BuildResult result = gradleRunner()
                .withArguments("validateReleaseObservability")
                .build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":validateReleaseObservability").getOutcome());
        assertTrue(result.getOutput().contains("Release observability validation PASSED"));
    }

    @Test
    @DisplayName("fails when Prometheus config does not scrape the canonical metrics endpoint")
    void shouldFailWhenPrometheusUsesWrongMetricsPath() throws IOException {
        writeFile("prometheus.yappc.yml", """
                scrape_configs:
                  - job_name: \"yappc-backend\"
                    metrics_path: \"/actuator/prometheus\"
                """);
        writeFile("deployment/monitoring/alerts/yappc.yml", """
                groups:
                  - name: yappc.ai
                    rules:
                      - alert: YappcAiProviderLatencyHigh
                        expr: rate(yappc_ai_llm_latency_seconds_sum[5m]) / rate(yappc_ai_llm_latency_seconds_count[5m]) > 5
                      - alert: YappcAiFallbackRateHigh
                        expr: rate(yappc_ai_fallback_total[5m]) > 0
                      - alert: YappcAiInferenceFailureRateHigh
                        expr: rate(yappc_ai_inference_failed_total[5m]) > 0
                """);
        writeFile("docs/RELEASE_READINESS_CHECKLIST.md", """
                # Checklist
                - AI observability sign-off
                - release evidence bundle
                """);
        writeFile("docs/operations/ONCALL_RUNBOOK.md", """
                # Runbook
                Check /health/readiness and /metrics during rollback.
                """);
        writeFile("docs/LLM_OBSERVABILITY.md", """
                # Observability
                Propagate X-Correlation-ID.
                Metrics:
                - yappc.ai.llm.latency.seconds
                - yappc.ai.fallback.total
                - yappc.ai.inference.failed
                """);

        BuildResult result = gradleRunner()
                .withArguments("validateReleaseObservability")
                .buildAndFail();

        assertTrue(result.getOutput().contains("must scrape the canonical /metrics endpoint"));
        assertEquals(TaskOutcome.FAILED, result.task(":validateReleaseObservability").getOutcome());
    }

    private void writeFile(String relativePath, String content) throws IOException {
        Path target = projectDir.resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content.stripIndent());
    }

    private GradleRunner gradleRunner() {
        return GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .forwardOutput();
    }
}