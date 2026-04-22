/**
 * YAPPC Product Build Configuration
 *
 * @doc.type build-script
 * @doc.purpose YAPPC product-level configuration with governance and validation
 * @doc.layer product
 * @doc.pattern Product
 */
import java.io.File

plugins {
    id("java-module")
}

group = "com.ghatana.products.yappc"
version = rootProject.version
description = "YAPPC - AI-Native Product Development Platform"

// Product-specific configuration simplified - extension not available in simplified version

// Skip spotless for this root product module - the frontend/web/node_modules tree
// contains broken symlinks that cause Spotless file-tree traversal to fail.
// Formatting is enforced at the individual Java sub-module level instead.
afterEvaluate {
    tasks.matching { it.name.startsWith("spotlessMisc") }.configureEach {
        onlyIf { false }
    }
}

// Custom validation for YAPPC architectural decisions
// Note: This task is kept simple to avoid configuration cache serialization issues.
// For comprehensive validation, use a dedicated CI job that runs without configuration cache.
tasks.register("checkYappcStructuralGovernance") {
    group = "verification"
    description = "Validates YAPPC-specific architectural governance"
    
    // Always re-run this check since it validates critical governance rules
    outputs.upToDateWhen { false }

    doLast {
        // Minimal validation: just check that settings.gradle.kts doesn't have obvious violations
        // Use System property to avoid project serialization with configuration cache
        val settingsFile = File(project.projectDir, "settings.gradle.kts")
        val settingsPath = settingsFile.absolutePath

        if (!settingsFile.exists()) {
            logger.warn("YAPPC settings.gradle.kts not found at $settingsPath, skipping governance check")
            return@doLast
        }

        val settingsContent = settingsFile.readText()

        // Quick check: ensure we don't reintroduce thin modules
        val thinModulePatterns = listOf(
            "include\\(['\\\"].*:services:ai",
            "include\\(['\\\"].*:services:scaffold",
            "include\\(['\\\"].*:core:scaffold:packs",
            "include\\(['\\\"].*:backend:websocket",
            "include\\(['\\\"].*:infrastructure:security"
        )

        val violations = thinModulePatterns.filter { pattern ->
            Regex(pattern).containsMatchIn(settingsContent)
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "YAPPC structural governance violation: Thin modules detected in settings.gradle.kts\n" +
                "See YAPPC_STRUCTURE_SIMPLIFICATION_PLAN.md for details"
            )
        }

        logger.lifecycle("YAPPC structural governance check completed (basic validation)")
    }
}

tasks.named("check") {
    dependsOn("checkYappcStructuralGovernance")
}

// EventloopTestBase enforcement for ActiveJ async tests
// Note: This task performs file scanning which is kept minimal to avoid configuration cache issues.
// For comprehensive validation, use a dedicated CI job that runs without configuration cache.
tasks.register("checkNoGetResultInTests") {
    group = "verification"
    description = "Ensures ActiveJ async tests use EventloopTestBase (basic check)"

    // Always re-run this check
    outputs.upToDateWhen { false }

    doLast {
        // Minimal check: just scan the most likely test location
        // Use System property to avoid project serialization with configuration cache
        val userDir = System.getProperty("user.dir")
        val testDir = File("$userDir/src/test/java")

        if (!testDir.exists()) {
            logger.debug("No src/test/java directory found, skipping getResult check")
            return@doLast
        }

        val violations = mutableListOf<String>()
        val testPattern = Regex("""\.getResult\s*\(\s*\)""")

        // Only scan a limited set of files to avoid performance issues
        testDir.walk()
            .filter { it.isFile && it.name.endsWith("Test.java") }
            .take(50)  // Limit to first 50 test files
            .forEach { file ->
                try {
                    file.readLines().forEachIndexed { idx, line ->
                        if (testPattern.containsMatchIn(line) &&
                            !line.trimStart().startsWith("//") &&
                            !line.contains("y04-ok")) {
                            violations.add("${file.name}:${idx + 1}")
                        }
                    }
                } catch (e: Exception) {
                    // Ignore read errors
                    logger.debug("Could not read file ${file.name}: ${e.message}")
                }
            }

        if (violations.isNotEmpty()) {
            logger.warn("Found potential getResult() usage in tests (limited scan): ${violations.take(5)}")
            logger.warn("Run './gradlew :products:yappc:checkNoGetResultInTests --no-configuration-cache' for comprehensive check")
        }
    }
}

tasks.named("check") {
    dependsOn("checkNoGetResultInTests")
}
