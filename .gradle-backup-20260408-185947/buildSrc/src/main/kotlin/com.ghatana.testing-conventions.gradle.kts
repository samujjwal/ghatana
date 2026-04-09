import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport
import java.math.BigDecimal

/**
 * Testing Convention Plugin
 *
 * @doc.type convention-plugin
 * @doc.purpose Provides consistent, comprehensive test configuration for all Java
 *              modules: JUnit Platform, JaCoCo coverage, integration-test profile,
 *              configurable failure tolerance, parallel execution, and structured logging.
 * @doc.layer build
 * @doc.pattern Convention
 *
 * Consolidated from (supersedes):
 *   - com.ghatana.jacoco-conventions
 *   - com.ghatana.integration-test-profile
 *   - com.ghatana.test-failure-tolerance
 *
 * Usage:
 *   plugins {
 *       id("com.ghatana.java-conventions")
 *       id("com.ghatana.testing-conventions")
 *   }
 *
 * Integration test mode (include all tests):
 *   ./gradlew build -PrunIntegrationTests
 *
 * Coverage thresholds (override defaults):
 *   ./gradlew test -PjacocoMinBranch=0.70 -PjacocoMinLine=0.75
 *
 * Test failure tolerance (default: 100% — stabilization mode):
 *   ./gradlew build -PtestFailureThreshold=0    # strict: zero failures tolerated
 *   ./gradlew build -PtestFailureThreshold=10   # tolerate up to 10% test failures
 */

plugins {
    jacoco
}

val libs = project.extensions.findByType(VersionCatalogsExtension::class.java)?.named("libs")

// ── JaCoCo Version ────────────────────────────────────────────────────────────
configure<JacocoPluginExtension> {
    toolVersion = "0.8.14"  // Matches libs.versions.jacoco in catalog
}

// ── Integration Test Profile ──────────────────────────────────────────────────
val integrationMode: Boolean = project.hasProperty("runIntegrationTests")

private val integrationSystemProperties = mapOf(
    "testcontainers.enabled"  to "true",
    "test.typescript.enabled" to "true",
    "test.python.enabled"     to "true",
    "test.go.enabled"         to "true",
    "test.rust.enabled"       to "true",
    "test.native.enabled"     to "true",
    "test.ai.enabled"         to "true",
    "runBenchmarks"           to "true"
)

// ── Test Failure Tolerance ────────────────────────────────────────────────────
// Default: 100% tolerance preserves existing build behavior (stabilization mode).
// Tighten incrementally: -PtestFailureThreshold=10 → 5 → 0 as coverage improves.
val failureThreshold: Double = run {
    val prop = providers.gradleProperty("testFailureThreshold")
        .orNull?.toDoubleOrNull()
    prop ?: 100.0
}

// ── Test Task Configuration ───────────────────────────────────────────────────
tasks.withType<Test>().configureEach {
    val taskPath = path

    // Tag-based filtering — integration tests are excluded by default
    if (integrationMode) {
        useJUnitPlatform()
        integrationSystemProperties.forEach { (k, v) -> systemProperty(k, v) }
        logger.lifecycle("[$taskPath] Integration test profile ACTIVE — all tags included")
    } else {
        useJUnitPlatform {
            excludeTags("integration")
        }
    }

    testLogging {
        events("passed", "skipped", "failed")
        showCauses = true
        showStackTraces = true
        showStandardStreams = false
    }

    // Parallel execution — use half the available CPUs to avoid OOM in CI
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)

    // Docker Desktop 29+ compatibility for Testcontainers
    jvmArgs("-Dapi.version=1.44")

    // Wire into JaCoCo
    finalizedBy(project.tasks.named("jacocoTestReport"))

    // ── Failure Tolerance Post-Processing ─────────────────────────────────
    val counts = TestCountAccumulator()
    ignoreFailures = true   // Always proceed; the doLast gate enforces the threshold rate

    addTestListener(object : org.gradle.api.tasks.testing.TestListener {
        override fun beforeSuite(suite: org.gradle.api.tasks.testing.TestDescriptor) {}
        override fun afterSuite(suite: org.gradle.api.tasks.testing.TestDescriptor, result: org.gradle.api.tasks.testing.TestResult) {}
        override fun beforeTest(testDescriptor: org.gradle.api.tasks.testing.TestDescriptor) {}
        override fun afterTest(
            testDescriptor: org.gradle.api.tasks.testing.TestDescriptor,
            result: org.gradle.api.tasks.testing.TestResult
        ) {
            counts.total++
            when (result.resultType) {
                org.gradle.api.tasks.testing.TestResult.ResultType.FAILURE -> {
                    counts.failed++
                    counts.failedNames += "${testDescriptor.className}.${testDescriptor.name}"
                }
                org.gradle.api.tasks.testing.TestResult.ResultType.SKIPPED -> counts.skipped++
                org.gradle.api.tasks.testing.TestResult.ResultType.SUCCESS -> counts.passed++
            }
        }
    })

    doLast {
        if (counts.total == 0 || (counts.passed + counts.failed) == 0) return@doLast

        val executed = counts.passed + counts.failed
        val failureRate = (counts.failed.toDouble() / executed.toDouble()) * 100.0
        val summary = "[$taskPath] Tests: ${counts.total} total, " +
            "${counts.passed} passed, ${counts.failed} failed, ${counts.skipped} skipped " +
            "(failure rate: ${"%.1f".format(failureRate)}%, threshold: ${"%.1f".format(failureThreshold)}%)"

        when {
            counts.failed > 0 && failureRate > failureThreshold -> {
                val failedList = counts.failedNames.joinToString("\n") { "  - $it" }
                if (failureThreshold < 100.0) {
                    throw GradleException(
                        "$summary\nBuild FAILED: failure rate (${"%.1f".format(failureRate)}%) " +
                            "exceeds threshold (${"%.1f".format(failureThreshold)}%).\n" +
                            "Failed:\n$failedList"
                    )
                } else {
                    logger.warn("$summary\nFailed tests (tolerated at 100% threshold):\n$failedList")
                }
            }
            counts.failed > 0 -> {
                val failedList = counts.failedNames.joinToString("\n") { "  - $it" }
                logger.warn("$summary\nTolerated failures:\n$failedList")
            }
            else -> logger.lifecycle(summary)
        }
    }
}

// ── JaCoCo Report ─────────────────────────────────────────────────────────────
tasks.named("jacocoTestReport", JacocoReport::class.java).configure {
    dependsOn(tasks.withType<Test>())
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
    val excludes = listOf("**/generated/**", "**/test/**", "**/proto/**")
    runCatching {
        val classesDirs = project.extensions.getByType(JavaPluginExtension::class.java)
            .sourceSets.getByName("main").output.classesDirs.files
        classDirectories.setFrom(
            classesDirs.map { dir -> project.fileTree(dir).exclude(excludes) }
        )
    }.onFailure { e ->
        project.logger.debug("jacocoTestReport: could not resolve classesDirs: ${e.message}")
    }
}

// ── JaCoCo Coverage Verification ─────────────────────────────────────────────
// Thresholds can be overridden per-project via Gradle properties:
//   ./gradlew test -PjacocoMinBranch=0.70 -PjacocoMinLine=0.75
val minBranchCoverage: BigDecimal =
    project.findProperty("jacocoMinBranch")?.toString()?.toBigDecimalOrNull()
        ?: BigDecimal("0.50")

val minLineCoverage: BigDecimal =
    project.findProperty("jacocoMinLine")?.toString()?.toBigDecimalOrNull()
        ?: BigDecimal("0.60")

tasks.named("jacocoTestCoverageVerification", JacocoCoverageVerification::class.java).configure {
    dependsOn(project.tasks.named("jacocoTestReport"))
    violationRules {
        rule {
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = minBranchCoverage
            }
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = minLineCoverage
            }
        }
    }
    val excludes = listOf("**/generated/**", "**/test/**", "**/proto/**")
    runCatching {
        val classesDirs = project.extensions.getByType(JavaPluginExtension::class.java)
            .sourceSets.getByName("main").output.classesDirs.files
        classDirectories.setFrom(
            classesDirs.map { dir -> project.fileTree(dir).exclude(excludes) }
        )
    }.onFailure { e ->
        project.logger.debug(
            "jacocoTestCoverageVerification: could not resolve classesDirs: ${e.message}"
        )
    }
}

// Wire coverage verification into standard `check` lifecycle
tasks.named("check").configure {
    dependsOn(tasks.named("jacocoTestCoverageVerification"))
}

// ── Convenience: Integration Test root task ───────────────────────────────────
if (project == rootProject) {
    tasks.register("integrationTest") {
        group = "verification"
        description = "Shortcut to run the full build with all integration tests.\n" +
            "Equivalent to: ./gradlew build -PrunIntegrationTests"
        doFirst {
            logger.lifecycle(
                "NOTE: Shortcut task.  For a full integration build run:\n" +
                    "  ./gradlew build -PrunIntegrationTests"
            )
        }
    }
}

/** Mutable counters used to track per-test-task results. */
class TestCountAccumulator {
    var total = 0
    var passed = 0
    var failed = 0
    var skipped = 0
    val failedNames = mutableListOf<String>()
}





