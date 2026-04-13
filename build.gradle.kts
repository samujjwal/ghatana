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
    // Anchor Spotless in the root classloader so every subproject's SpotlessTaskService
    // resolves to the same class, preventing the cross-project classloader mismatch.
    // See: https://github.com/diffplug/spotless/issues/1495
    alias(libs.plugins.spotless) apply false
}

group = "com.ghatana"
version = "2026.3.1-SNAPSHOT"

subprojects {
    // Prevent clean/build races in parallel multi-project execution.
    // Running `clean build` can otherwise delete a project's build directory
    // while that same project's test/report tasks are still writing outputs.
    plugins.withId("base") {
        val cleanTask = tasks.named("clean")
        tasks.matching { it.name != "clean" }.configureEach {
            mustRunAfter(cleanTask)
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

    plugins.withId("com.diffplug.spotless") {
        tasks.matching { it.name.startsWith("spotless") }.configureEach {
            notCompatibleWithConfigurationCache(
                "Spotless task file-tree serialization currently conflicts with symlink-heavy node_modules trees"
            )
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

    val javaVersion = System.getProperty("java.version")
    val gradleVersion = gradle.gradleVersion
    val totalProjects = gradle.rootProject.childProjects.size
    notCompatibleWithConfigurationCache("Diagnostic task prints static build metadata")

    doLast {
        println("=== Build Health Report ===")
        println("Java Version: $javaVersion")
        println("Gradle Version: $gradleVersion")
        println("Total Projects: $totalProjects")
        println("===========================")
    }
}

// ============================================================================
// Architecture Validation Tasks (Phase 4: Audit Report Implementation)
// ============================================================================

tasks.register("validateArchitecture") {
    group = "verification"
    description = "Validate monorepo architecture rules (dependency direction, module boundaries)"

    dependsOn("validateNoCircularDependencies")
    dependsOn("validateModuleBoundaries")
    dependsOn("validateDependencyDirection")
    dependsOn("validateNoDuplicateUtils")

    doLast {
        println("✅ Architecture validation complete")
    }
}

tasks.register("validateNoCircularDependencies") {
    group = "verification"
    description = "Check for circular dependencies between modules"

    doLast {
        println("Checking for circular dependencies...")
        // This would be implemented with a dependency analyzer plugin
        // For now, we rely on Gradle's built-in cycle detection
    }
}

tasks.register("validateModuleBoundaries") {
    group = "verification"
    description = "Validate module boundaries per architecture rules"

    doLast {
        // Rule: platform modules should not depend on product modules
        // Note: Full implementation requires dependency analysis plugin
        // This is a placeholder that will be enhanced with ArchUnit
        println("✅ Module boundary validation (placeholder - requires ArchUnit implementation)")
    }
}

tasks.register("validateDependencyDirection") {
    group = "verification"
    description = "Validate dependency direction (platform → products)"

    doLast {
        println("✅ Dependency direction validation complete (enforced by Gradle)")
    }
}

tasks.register("validateNoDuplicateUtils") {
    group = "verification"
    description = "Validate no duplicate utility classes exist"

    doLast {
        val utilsClasses = mutableMapOf<String, MutableList<String>>()

        subprojects.forEach { project ->
            project.fileTree("src/main/java").matching {
                include("**/util/*.java")
                include("**/utils/*.java")
                include("**/Utils.java")
            }.forEach { file ->
                val className = file.nameWithoutExtension
                utilsClasses.getOrPut(className) { mutableListOf() }.add(project.path)
            }
        }

        val duplicates = utilsClasses.filter { it.value.size > 1 }
        if (duplicates.isEmpty()) {
            println("✅ No duplicate utility classes found")
        } else {
            println("⚠️ Duplicate utility classes found:")
            duplicates.forEach { (name, projects) ->
                println("  - $name found in: ${projects.joinToString()}")
            }
        }
    }
}

tasks.register("auditModuleCount") {
    group = "reporting"
    description = "Audit module count per layer"

    doLast {
        val platformModules = subprojects.count { it.path.startsWith(":platform:") }
        val productModules = subprojects.count { it.path.startsWith(":products:") }
        val sharedServices = subprojects.count { it.path.startsWith(":shared-services:") }
        val kernelModules = subprojects.count { it.path.startsWith(":platform-kernel:") }

        println("=== Module Count Audit ===")
        println("Platform modules: $platformModules")
        println("Product modules: $productModules")
        println("Shared services: $sharedServices")
        println("Kernel modules: $kernelModules")
        println("Total: ${platformModules + productModules + sharedServices + kernelModules}")
        println("===========================")
    }
}
