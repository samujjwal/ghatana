import org.gradle.api.GradleException
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult

/**
 * Implements a configurable test failure tolerance policy.
 *
 * Usage:
 *   ./gradlew build                               # default 0% tolerance (strict)
 *   ./gradlew build -PtestFailureThreshold=0      # zero tolerance (strict)
 *   ./gradlew build -PtestFailureThreshold=10     # 10% tolerance
 */

tasks.withType<Test>().configureEach {
    val counts = TestCounts()
    val threshold = providers.gradleProperty("testFailureThreshold")
        .map {
            runCatching { it.toDouble() }.getOrElse {
                logger.warn("Invalid testFailureThreshold value, using default 0.0%")
                0.0
            }
        }
        .orElse(0.0)

    ignoreFailures = true

    addTestListener(object : TestListener {
        override fun beforeSuite(suite: TestDescriptor) {}
        override fun afterSuite(suite: TestDescriptor, result: TestResult) {}
        override fun beforeTest(testDescriptor: TestDescriptor) {}

        override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {
            counts.total++
            when (result.resultType) {
                TestResult.ResultType.FAILURE -> {
                    counts.failed++
                    counts.failedNames += "${testDescriptor.className}.${testDescriptor.name}"
                }
                TestResult.ResultType.SKIPPED -> counts.skipped++
                TestResult.ResultType.SUCCESS -> counts.passed++
            }
        }
    })

    val taskPath = path
    doLast {
        val failureThreshold = threshold.get()

        if (counts.total == 0) return@doLast
        val executed = counts.passed + counts.failed
        if (executed == 0) return@doLast

        val failureRate = (counts.failed.toDouble() / executed.toDouble()) * 100.0
        val summary = "[$taskPath] Test results: ${counts.total} total, " +
            "${counts.passed} passed, ${counts.failed} failed, ${counts.skipped} skipped " +
            "(failure rate: ${"%.1f".format(failureRate)}%, threshold: ${"%.1f".format(failureThreshold)}%)"

        when {
            counts.failed > 0 && failureRate >= failureThreshold -> {
                val failedList = counts.failedNames.joinToString("\n") { "  - $it" }
                throw GradleException(
                    "$summary\nBuild FAILED: test failure rate (${"%.1f".format(failureRate)}%) " +
                        "exceeds threshold (${"%.1f".format(failureThreshold)}%).\nFailed tests:\n$failedList"
                )
            }
            counts.failed > 0 -> {
                logger.warn("WARNING: $summary")
                val failedList = counts.failedNames.joinToString("\n") { "  - $it" }
                logger.warn("Tolerated failures:\n$failedList")
            }
            else -> logger.lifecycle(summary)
        }
    }
}

/** Mutable counter bag used per test task. */
class TestCounts {
    var total = 0
    var passed = 0
    var failed = 0
    var skipped = 0
    val failedNames = mutableListOf<String>()
}
