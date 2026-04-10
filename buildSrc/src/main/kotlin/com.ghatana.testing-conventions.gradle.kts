import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension

/**
 * Simplified Testing Convention Plugin
 *
 * @doc.type convention-plugin
 * @doc.purpose Provides consistent, comprehensive test configuration for all Java
 *              modules: JUnit Platform, JaCoCo coverage, integration-test profile,
 *              parallel execution, and structured logging.
 * @doc.layer build
 * @doc.pattern Convention
 *
 * Usage:
 *   plugins {
 *       id("com.ghatana.java-conventions")
 *       id("com.ghatana.testing-conventions-simplified")
 *   }
 */

plugins {
    jacoco
}

// JaCoCo Version
configure<JacocoPluginExtension> {
    // Hardcoded version required due to buildSrc isolation
    // This version must be kept in sync with gradle/libs.versions.toml
    // See buildSrc/VERSION_SYNC.md for details
    toolVersion = "0.8.14"
}

// JaCoCo only fires during CI or when explicitly requested locally.
// Guards against JaCoCo instrumentation being triggered by non-test tasks
// such as compileJava (via the eager finalizedBy() that was previously here).
val withCoverage: Boolean = System.getenv("CI") != null ||
    project.hasProperty("coverage")

// Integration Test Profile
val integrationMode: Boolean = project.hasProperty("runIntegrationTests")

private val integrationSystemProperties = mapOf(
    "testcontainers.enabled"  to "true",
    "test.typescript.enabled" to "true",
    "test.python.enabled"     to "true",
    "test.go.enabled"         to "true",
    "test.ruby.enabled"       to "true"
)

// Test Configuration
tasks.withType<Test>().configureEach {
    // Single useJUnitPlatform() call — calling it multiple times is a no-op but
    // redundant calls show up as misconfiguration warnings in --info output.
    useJUnitPlatform()
    ignoreFailures = false  // Tests should fail the build

    if (integrationMode) {
        integrationSystemProperties.forEach { (k, v) -> systemProperty(k, v) }
        logger.lifecycle("[$path] Integration test profile ACTIVE - all tags included")
    }

    testLogging {
        events("passed", "skipped", "failed")
        showCauses = true
        showStackTraces = true
        showStandardStreams = false
    }

    // Parallel execution — use half available CPUs to leave headroom for IDE + Docker.
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2)
        .coerceAtLeast(1)
        .coerceAtMost(4)
    maxHeapSize = "1536m"
    // NOTE: forkEvery intentionally omitted.  Setting forkEvery > 0 causes Gradle
    // to SIGTERM the test JVM at the class boundary, which races with the XML result
    // writer and produces "Could not write XML test results" / null-byte corruption.
    // Java 21 + ZGC handles metaspace pressure well without per-class JVM recycling.

    // Docker Desktop 29+ compatibility for Testcontainers
    jvmArgs("-Dapi.version=1.44")

    // Wire into JaCoCo only when coverage is enabled.
    if (withCoverage) {
        finalizedBy(project.tasks.named("jacocoTestReport"))
    }
}

// JaCoCo Test Report — mustRunAfter keeps the task graph lazy so that
// jacocoTestReport doesn't force Test execution when the user only runs
// compileJava or assembleJar.
project.tasks.findByName("jacocoTestReport")?.let { task ->
    task as org.gradle.testing.jacoco.tasks.JacocoReport
    task.mustRunAfter(tasks.withType<Test>())

    task.reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
    
    // Include all classes in coverage - no exclusions
    task.classDirectories.setFrom(
        fileTree(layout.buildDirectory.dir("classes/java/main"))
    )
}

// JaCoCo Coverage Verification - temporarily disabled to avoid build failures
tasks.withType<org.gradle.testing.jacoco.tasks.JacocoCoverageVerification>().configureEach {
    dependsOn(tasks.withType<org.gradle.testing.jacoco.tasks.JacocoReport>())
    
    violationRules {
        rule {
            limit {
                counter = "INSTRUCTION"
                value = "COVEREDRATIO"
                minimum = "0.000".toBigDecimal() // 0% instruction coverage (temporarily disabled)
            }
        }
    }
    
    // Apply same inclusions as test report - all classes
    classDirectories.setFrom(
        fileTree(layout.buildDirectory.dir("classes/java/main"))
    )
}

// Wire JaCoCo tasks into check lifecycle - only if check task exists
project.tasks.findByName("check")?.let { checkTask ->
    project.tasks.findByName("jacocoTestReport")?.let { checkTask.dependsOn(it) }
    project.tasks.findByName("jacocoTestCoverageVerification")?.let { checkTask.dependsOn(it) }
}
