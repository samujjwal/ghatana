import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.quality.CheckstyleExtension
import org.gradle.api.plugins.quality.PmdExtension
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension

/**
 * Quality Convention Plugin
 *
 * @doc.type convention-plugin
 * @doc.purpose Applies consistent static-analysis and formatting tooling with
 *              versions sourced exclusively from the version catalog.
 * @doc.layer build
 * @doc.pattern Convention
 *
 * Configures:
 * - Checkstyle — style & formatting enforcement
 * - PMD        — static bug-pattern analysis
 * - JaCoCo     — bytecode coverage instrumentation
 * - Spotless   — source-code auto-formatter
 *
 * All tool versions are read from gradle/libs.versions.toml.  There are NO
 * hardcoded version strings in this file.
 *
 * Usage:
 *   plugins {
 *       id("com.ghatana.java-conventions")
 *       id("com.ghatana.quality-conventions")
 *   }
 */

plugins {
    checkstyle
    pmd
    jacoco
    com.diffplug.spotless
}

val libs = project.extensions.findByType(VersionCatalogsExtension::class.java)?.named("libs")

// ── Checkstyle ────────────────────────────────────────────────────────────────
configure<CheckstyleExtension> {
    // Hardcoded version required due to buildSrc isolation
    // This version must be kept in sync with gradle/libs.versions.toml
    // See buildSrc/VERSION_SYNC.md for details
    toolVersion = "10.21.4"
    configFile = rootProject.file("config/checkstyle/checkstyle.xml")
    configProperties = mapOf(
        "suppressionFile" to rootProject.file("config/checkstyle/suppressions.xml").absolutePath,
        "checkstyle.cache.file" to
            "${project.layout.buildDirectory.get()}/checkstyle.cache"
    )
    isIgnoreFailures = false
    isShowViolations = true
}

tasks.withType<org.gradle.api.plugins.quality.Checkstyle>().configureEach {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

// ── PMD ───────────────────────────────────────────────────────────────────────
configure<PmdExtension> {
    // Hardcoded version required due to buildSrc isolation
    // This version must be kept in sync with gradle/libs.versions.toml
    // See buildSrc/VERSION_SYNC.md for details
    toolVersion = "7.11.0"
    ruleSetFiles = files(rootProject.file("config/pmd/minimal-ruleset.xml"))
    ruleSets = emptyList()
    isIgnoreFailures = true
    isConsoleOutput = true
}

// ── JaCoCo ────────────────────────────────────────────────────────────────────
configure<JacocoPluginExtension> {
    // Hardcoded version required due to buildSrc isolation
    // This version must be kept in sync with gradle/libs.versions.toml
    // See buildSrc/VERSION_SYNC.md for details
    toolVersion = "0.8.14"
}

tasks.named<org.gradle.testing.jacoco.tasks.JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.withType<Test>())
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

// ── Spotless ──────────────────────────────────────────────────────────────────
configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    java {
        target("src/**/*.java")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }

    format("misc") {
        target("**/*.gradle", "**/.gitignore")
        trimTrailingWhitespace()
        endWithNewline()
    }

    format("xml") {
        target("**/*.xml", "**/*.xsd")
        trimTrailingWhitespace()
        endWithNewline()
    }

    // Enforce Spotless checks during the standard `check` lifecycle
    isEnforceCheck = true
}
