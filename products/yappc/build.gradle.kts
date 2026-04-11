/**
 * YAPPC Product Build Configuration
 *
 * @doc.type build-script
 * @doc.purpose YAPPC product-level configuration with governance and validation
 * @doc.layer product
 * @doc.pattern Product
 */
plugins {
    id("java-library")
    id("com.ghatana.testing-conventions")
}

group = "com.ghatana.products.yappc"
version = rootProject.version
description = "YAPPC - AI-Native Product Development Platform"

// Product-specific configuration simplified - extension not available in simplified version

// Custom validation for YAPPC architectural decisions
tasks.register("checkYappcStructuralGovernance") {
    group = "verification"
    description = "Validates YAPPC-specific architectural governance"
    
    // Disable configuration cache for this custom task
    notCompatibleWithConfigurationCache("Custom validation task that reads project files")

    doLast {
        val settingsFile = file("settings.gradle.kts")
        val settingsText = settingsFile.readText()
        val violations = mutableListOf<String>()

        // RULE 1: No thin module re-introduction
        val bannedModules = listOf(
            ":services:ai",
            ":services:scaffold",
            ":core:scaffold:packs",
            ":backend:websocket",
            ":infrastructure:security",
            ":launcher"
        )

        val violationsList = bannedModules.filter { mod ->
            settingsText.lines().any { line ->
                !line.trimStart().startsWith("//") &&
                (line.contains("include(\"$mod\")") || line.contains("include(':$mod')"))
            }
        }

        if (violationsList.isNotEmpty()) {
            violations.addAll(violationsList.map { "Thin module re-introduction: $it" })
        }

        // RULE 2: Core domain must be yappc-domain-impl
        val yapcDomainReintroduced = settingsText.lines().any { line ->
            !line.trimStart().startsWith("//") &&
            (line.contains("include(\":core:yappc-domain\")") ||
             line.contains("\"products:yappc:core:yappc-domain\""))
        }
        if (yapcDomainReintroduced) {
            violations.add("Use ':core:yappc-domain-impl' instead of ':core:yappc-domain'")
        }

        // RULE 3: No deprecated packages in frontend/libs/
        if (file("frontend/libs/theme").exists()) {
            violations.add("Deprecated packages must live in frontend/compat/, not frontend/libs/")
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "YAPPC structural governance violations:\n" +
                violations.joinToString("\n") { "  - $it" } + "\n" +
                "See YAPPC_STRUCTURE_SIMPLIFICATION_PLAN.md"
            )
        }

        logger.lifecycle("YAPPC structural governance: all checks passed")
    }
}

tasks.named("check") {
    dependsOn("checkYappcStructuralGovernance")
}

// EventloopTestBase enforcement for ActiveJ async tests
tasks.register("checkNoGetResultInTests") {
    group = "verification"
    description = "Ensures ActiveJ async tests use EventloopTestBase"

    // Disable configuration cache for this custom task
    notCompatibleWithConfigurationCache("Custom validation task that scans test files")

    doLast {
        val testPattern = Regex("""\.getResult\(\)""")
        val violations = mutableListOf<String>()
        val projectRoot = projectDir

        // Only scan src/test directories, not entire project tree
        val testDirs = listOf(
            file("src/test/java"),
            file("core/src/test/java"),
            file("services/src/test/java"),
            file("agents/src/test/java"),
            file("platform/src/test/java")
        ).filter { it.exists() }

        testDirs.forEach { testDir ->
            testDir.walkTopDown()
                .filter { it.isFile && it.extension == "java" &&
                    (it.name.endsWith("Test.java") || it.name.endsWith("IT.java")) }
                .forEach { file ->
                    file.readLines().forEachIndexed { idx, line ->
                        if (testPattern.containsMatchIn(line) && !line.trimStart().startsWith("//") &&
                            !line.contains("y04-ok")) {
                            violations.add("${file.relativeTo(projectRoot)}:${idx + 1}: ${line.trim()}")
                        }
                    }
                }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "ActiveJ async test violations found:\n" +
                violations.joinToString("\n") { "  - $it" } + "\n" +
                "All async tests must extend EventloopTestBase and use runPromise()"
            )
        }
    }
}

tasks.named("check") {
    dependsOn("checkNoGetResultInTests")
}
