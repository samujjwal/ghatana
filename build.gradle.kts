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

// MIGRATION STATUS: ~15 modules migrated to build-logic, ~165 remaining.
//
// Convention application uses buildFile.readText() to detect which plugins a module
// declares. While this performs file I/O at configuration time and is not ideal for
// the configuration cache, it is correct and the performance impact is negligible
// (~50 ms total for 175 modules) compared to the alternative (hard-failing the build
// if double-configuration occurs).
//
// A future optimization: once all modules are migrated to build-logic conventions,
// this entire subprojects {} block can be removed.
subprojects {
    // Skip if no build file exists (empty directory placeholders in settings).
    if (!buildFile.exists()) return@subprojects

    val buildContent = buildFile.readText()

    val onBuildLogic = buildContent.contains("id(\"java-module\")") ||
                       buildContent.contains("id(\"java-application\")") ||
                       buildContent.contains("id(\"protobuf-module\")")

    if (!onBuildLogic) {
        // Only auto-apply buildSrc conventions when the module has Java/Kotlin sources
        // AND has not yet been migrated to build-logic.
        val hasSources = file("$projectDir/src/main/java").exists() ||
                         file("$projectDir/src/main/kotlin").exists() ||
                         file("$projectDir/src/test/java").exists() ||
                         file("$projectDir/src/test/kotlin").exists()

        if (hasSources) {
            apply(plugin = "java-library")
            apply(plugin = "idea")
            apply(plugin = "com.ghatana.java-conventions")
            apply(plugin = "com.ghatana.testing-conventions")
            apply(plugin = "com.ghatana.quality-conventions")
            apply(plugin = "com.ghatana.lombok-conventions")

            group = rootProject.group
            version = rootProject.version

            // Sources JAR generated only for publishing; skip on normal builds
            // to save ~15% per-module build time.
            // Enable with: ./gradlew build -PwithSourcesJar=true
            if (findProperty("withSourcesJar")?.toString()?.toBoolean() == true) {
                configure<JavaPluginExtension> {
                    withJavadocJar()
                    withSourcesJar()
                }
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
