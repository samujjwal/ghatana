/**
 * Ghatana Monorepo - Minimal Root Build Configuration
 *
 * All module configuration is handled by convention plugins applied explicitly
 * in each module's build.gradle.kts file.
 */

import org.gradle.api.plugins.quality.Pmd
import org.gradle.api.plugins.quality.PmdExtension

plugins {
    `java-platform`
    `idea`
    alias(libs.plugins.cyclonedx)
}

group = "com.ghatana"
version = "2026.3.1-SNAPSHOT"

// Temporary: Full convention application during migration to build-logic
// MIGRATION STATUS: ~15 modules migrated to build-logic, ~165 remaining
// This subprojects block will be removed once all modules use build-logic conventions
subprojects {
    // Skip if no build file exists
    if (!buildFile.exists()) return@subprojects
    
    val buildContent = buildFile.readText()
    
    // Check if module uses new build-logic conventions
    val usesBuildLogic = buildContent.contains("id(\"java-module\")") ||
                        buildContent.contains("id(\"java-application\")") ||
                        buildContent.contains("id(\"protobuf-module\")")
    
    // If module doesn't use build-logic, apply full buildSrc conventions
    if (!usesBuildLogic) {
        // Check if it has Java sources
        fun hasJavaSources(): Boolean {
            return file("$projectDir/src/main/java").exists() ||
                   file("$projectDir/src/main/kotlin").exists() ||
                   file("$projectDir/src/test/java").exists() ||
                   file("$projectDir/src/test/kotlin").exists()
        }
        
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

    plugins.withId("pmd") {
        configure<PmdExtension> {
            toolVersion = "7.11.0"
            ruleSetFiles = files(rootProject.file("config/pmd/minimal-ruleset.xml"))
            ruleSets = emptyList()
            isIgnoreFailures = false
            isConsoleOutput = true
        }

        tasks.withType<Pmd>().configureEach {
            val rulesetFile = if (name.contains("Test", ignoreCase = true)) {
                rootProject.file("config/pmd/test-ruleset.xml")
            } else {
                rootProject.file("config/pmd/minimal-ruleset.xml")
            }
            val sourceDirectory = if (name.contains("Test", ignoreCase = true)) {
                "src/test/java"
            } else {
                "src/main/java"
            }
            ruleSetFiles = files(rulesetFile)
            ruleSets = emptyList()
            source = fileTree(sourceDirectory) {
                exclude("**/generated/**")
                exclude("**/build/generated/**")
                exclude("**/*Grpc.java")
                exclude("**/*Proto.java")
                exclude("**/*_Grpc*.java")
                exclude("**/grpc/**")
                exclude("**/proto/**")
            }
        }
    }
}

// IDE Configuration
idea {
    module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}

// SBOM Generation
tasks.withType<org.cyclonedx.gradle.CyclonedxAggregateTask>().configureEach {
    includeLicenseText = false
    includeBuildSystem = true
    jsonOutput.set(project.file("build/sbom/bom.json"))
    xmlOutput.unsetConvention()
}

// Build Health Task
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
