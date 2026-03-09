package com.ghatana.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestResult

/**
 * Gradle plugin that implements a test failure tolerance policy.
 *
 * <p>When applied, test tasks will not immediately fail the build on test failures.
 * Instead, they collect results and only fail if the failure rate exceeds the
 * configured threshold (default: 5% of total executed test cases).
 *
 * <h3>Usage</h3>
 * Applied automatically in the root build.gradle.kts. The threshold can be
 * overridden per-project via Gradle property:
 * <pre>
 *   ./gradlew build                               # default 5% tolerance
 *   ./gradlew build -PtestFailureThreshold=0      # zero tolerance (strict)
 *   ./gradlew build -PtestFailureThreshold=10     # 10% tolerance
 * </pre>
 *
 * <p>When a test task reports failures but under the threshold, the build
 * continues with a WARNING. A summary of tolerated failures is printed at
 * the end of each test task.
 *
 * @doc.type plugin
 * @doc.purpose Build-failure tolerance for low test failure rates
 * @doc.layer build-infrastructure
 * @doc.pattern Convention Plugin
 */
class TestFailureTolerancePlugin implements Plugin<Project> {

    /** Default failure percentage threshold below which the build still succeeds. */
    static final double DEFAULT_THRESHOLD_PERCENT = 0.0

    @Override
    void apply(Project project) {
        double threshold = resolveThreshold(project)

        project.tasks.withType(Test).configureEach { Test testTask ->
            // Track counts per test task
            def counts = new TestCounts()

            testTask.ignoreFailures = true

            testTask.afterTest { TestDescriptor desc, TestResult result ->
                counts.total++
                switch (result.resultType) {
                    case TestResult.ResultType.FAILURE:
                        counts.failed++
                        counts.failedNames << "${desc.className}.${desc.name}"
                        break
                    case TestResult.ResultType.SKIPPED:
                        counts.skipped++
                        break
                    case TestResult.ResultType.SUCCESS:
                        counts.passed++
                        break
                }
            }

            testTask.doLast {
                if (counts.total == 0) return

                int executed = counts.passed + counts.failed
                if (executed == 0) return

                double failureRate = (counts.failed / (double) executed) * 100.0

                String summary = String.format(
                    "[%s] Test results: %d total, %d passed, %d failed, %d skipped (failure rate: %.1f%%, threshold: %.1f%%)",
                    testTask.path, counts.total, counts.passed, counts.failed, counts.skipped,
                    failureRate, threshold
                )

                if (counts.failed > 0 && failureRate >= threshold) {
                    // Above threshold — fail the build
                    String failedList = counts.failedNames.collect { "  - ${it}" }.join('\n')
                    throw new org.gradle.api.GradleException(
                        "${summary}\n" +
                        "Build FAILED: test failure rate (${String.format('%.1f', failureRate)}%) " +
                        "exceeds threshold (${String.format('%.1f', threshold)}%).\n" +
                        "Failed tests:\n${failedList}"
                    )
                } else if (counts.failed > 0) {
                    // Under threshold — warn but continue
                    project.logger.warn("WARNING: ${summary}")
                    String failedList = counts.failedNames.collect { "  - ${it}" }.join('\n')
                    project.logger.warn("Tolerated failures:\n${failedList}")
                } else {
                    project.logger.lifecycle(summary)
                }
            }
        }
    }

    private static double resolveThreshold(Project project) {
        if (project.hasProperty('testFailureThreshold')) {
            try {
                return Double.parseDouble(project.property('testFailureThreshold').toString())
            } catch (NumberFormatException ignored) {
                project.logger.warn("Invalid testFailureThreshold value, using default ${DEFAULT_THRESHOLD_PERCENT}%")
            }
        }
        return DEFAULT_THRESHOLD_PERCENT
    }

    /** Mutable counter bag for a single test task. */
    private static class TestCounts {
        int total = 0
        int passed = 0
        int failed = 0
        int skipped = 0
        List<String> failedNames = []
    }
}
