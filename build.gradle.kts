/**
 * Ghatana Monorepo - Root Build Configuration
 *
 * Legacy convention application for gradual migration to build-logic.
 */

plugins {
    `java-platform`
    `idea`
    alias(libs.plugins.cyclonedx)
}

group = "com.ghatana"
version = "2026.3.1-SNAPSHOT"

// =============================================================================
// Legacy Convention Application (for gradual migration)
// =============================================================================
subprojects {
    // Helper function to check if project has Java sources
    fun hasJavaSources(): Boolean {
        return file("$projectDir/src/main/java").exists() ||
               file("$projectDir/src/main/kotlin").exists() ||
               file("$projectDir/src/test/java").exists() ||
               file("$projectDir/src/test/kotlin").exists()
    }

    // Apply standard conventions to all Java projects
    if (hasJavaSources()) {
        apply(plugin = "java-library")
        apply(plugin = "idea")
        apply(plugin = "com.ghatana.java-conventions")
        apply(plugin = "com.ghatana.testing-conventions")
        apply(plugin = "com.ghatana.quality-conventions")
        apply(plugin = "com.ghatana.lombok-conventions")

        group = rootProject.group
        version = rootProject.version

        configure<JavaPluginExtension> {
            withJavadocJar()
            withSourcesJar()
        }
    }
}

// =============================================================================
// IDE Configuration
// =============================================================================
idea {
    module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}

// =============================================================================
// SBOM Generation
// =============================================================================
tasks.withType<org.cyclonedx.gradle.CyclonedxAggregateTask>().configureEach {
    includeLicenseText = false
    includeBuildSystem = true
    jsonOutput.set(project.file("build/sbom/bom.json"))
    xmlOutput.unsetConvention()
}

// =============================================================================
// Build Health Task
// =============================================================================
tasks.register("buildHealth") {
    group = "verification"
    description = "Quick build health diagnostics"

    doLast {
        println("=== Build Health Report ===")
        println("Java Version: ${System.getProperty("java.version")}")
        println("Gradle Version: ${project.gradle.gradleVersion}")
        println("Total Projects: ${gradle.rootProject.childProjects.size}")
        println("===========================")
    }
}
