import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

/**
 * Unified Testing Convention Plugin
 *
 * @doc.type convention-plugin
 * @doc.purpose Provides comprehensive, configurable test setup for all Java modules:
 *              JUnit Platform, JaCoCo coverage, integration-test profile,
 *              parallel execution, Docker compatibility, and structured logging.
 * @doc.layer build
 * @doc.pattern Convention
 *
 * Usage:
 *   plugins {
 *       id("com.ghatana.java-conventions")
 *       id("com.ghatana.testing-conventions")
 *   }
 *
 * Configuration:
 *   testing {
 *       coverage.enabled.set(true)
 *       coverage.threshold.set(0.80)
 *       integration.enabled.set(false)
 *       parallel.forks.set("auto")
 *       docker.compat.set(true)
 *       logging.showStandardStreams.set(false)
 *   }
 */

plugins {
    jacoco
}

// Extension for configuring testing behavior
interface TestingConventionExtension {
    val coverage: CoverageExtension
    val integration: IntegrationExtension
    val parallel: ParallelExtension
    val docker: DockerExtension
    val logging: LoggingExtension
}

interface CoverageExtension {
    val enabled: Property<Boolean>
    val threshold: Property<Double>
    val excludes: ListProperty<String>
}

interface IntegrationExtension {
    val enabled: Property<Boolean>
    val systemProperties: MapProperty<String, String>
}

interface ParallelExtension {
    val forks: Property<String> // "auto", "disabled", or number
    val maxForks: Property<Int>
}

interface DockerExtension {
    val compat: Property<Boolean>
    val apiVersion: Property<String>
}

interface LoggingExtension {
    val showStandardStreams: Property<Boolean>
    val events: ListProperty<String>
}

// Create the extension
val extension = project.extensions.create<TestingConventionExtension>("testing")

// Configure defaults
extension.coverage.enabled.convention(true)
extension.coverage.threshold.convention(0.80)
extension.coverage.excludes.convention(listOf(
    "**/*\$Builder.class",
    "**/*\$*_Factory.class",
    "**/package-info.class",
    "**/generated/**",
    "**/test/**",
    "**/*Test.class",
    "**/*Tests.class"
))

extension.integration.enabled.convention(false)
extension.integration.systemProperties.convention(mapOf(
    "testcontainers.enabled" to "true",
    "test.typescript.enabled" to "true",
    "test.python.enabled" to "true",
    "test.go.enabled" to "true",
    "test.ruby.enabled" to "true"
))

extension.parallel.forks.convention("auto")
extension.parallel.maxForks.convention(Runtime.getRuntime().availableProcessors() / 2)

extension.docker.compat.convention(true)
extension.docker.apiVersion.convention("1.44")

extension.logging.showStandardStreams.convention(false)
extension.logging.events.convention(listOf("passed", "skipped", "failed"))

// JaCoCo Configuration
configure<JacocoPluginExtension> {
    toolVersion = "0.8.14"  // Matches libs.versions.jacoco in catalog
}

// Test Configuration
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    
    // Integration test mode
    if (extension.integration.enabled.get()) {
        extension.integration.systemProperties.get().forEach { (key, value) ->
            systemProperty(key, value)
        }
        logger.lifecycle("[$path] Integration test profile ACTIVE")
    } else {
        useJUnitPlatform {
            excludeTags("integration")
        }
    }

    // Logging configuration
    testLogging {
        events(extension.logging.events.get())
        showCauses = true
        showStackTraces = true
        showStandardStreams = extension.logging.showStandardStreams.get()
    }

    // Parallel execution
    val forks = when (extension.parallel.forks.get()) {
        "auto" -> extension.parallel.maxForks.get().coerceAtLeast(1)
        "disabled" -> 1
        else -> extension.parallel.forks.get().toIntOrNull() ?: 1
    }
    maxParallelForks = forks

    // Docker compatibility
    if (extension.docker.compat.get()) {
        jvmArgs("-Dapi.version=${extension.docker.apiVersion.get()}")
    }

    // Wire into JaCoCo if coverage is enabled
    if (extension.coverage.enabled.get()) {
        finalizedBy(project.tasks.named("jacocoTestReport"))
    }
}

// JaCoCo Test Report (only if coverage is enabled)
if (extension.coverage.enabled.get()) {
    tasks.named<JacocoReport>("jacocoTestReport") {
        dependsOn(tasks.withType<Test>())
        
        reports {
            xml.required.set(true)
            html.required.set(true)
            csv.required.set(false)
        }
        
        classDirectories.setFrom(
            fileTree(layout.buildDirectory.dir("classes/java/main")) {
                exclude(extension.coverage.excludes.get())
            }
        )
    }

    // JaCoCo Coverage Verification
    tasks.withType<JacocoCoverageVerification>().configureEach {
        dependsOn(tasks.named<JacocoReport>("jacocoTestReport"))
        
        violationRules {
            rule {
                limit {
                    counter = "INSTRUCTION"
                    value = "COVEREDRATIO"
                    minimum = extension.coverage.threshold.get().toBigDecimal()
                }
            }
        }
        
        classDirectories.setFrom(
            fileTree(layout.buildDirectory.dir("classes/java/main")) {
                exclude(extension.coverage.excludes.get())
            }
        )
    }

    // Wire JaCoCo tasks into check lifecycle
    tasks.named("check") {
        dependsOn(tasks.named("jacocoTestReport"))
        dependsOn(tasks.named("jacocoTestCoverageVerification"))
    }
}
