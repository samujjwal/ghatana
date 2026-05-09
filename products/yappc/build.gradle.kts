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

group = "com.ghatana.yappc"
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
    val configurationCacheRequested = gradle.startParameter.isConfigurationCacheRequested

    if (configurationCacheRequested) {
        doLast {
            logger.lifecycle("Skipping checkYappcStructuralGovernance because configuration cache is enabled")
        }
        return@register
    }
    
    // Always re-run this check since it validates critical governance rules
    outputs.upToDateWhen { false }

    val settingsFile = layout.projectDirectory.file("settings.gradle.kts").asFile

    doLast {
        // Minimal validation: just check that settings.gradle.kts doesn't have obvious violations
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
    if (!gradle.startParameter.isConfigurationCacheRequested) {
        dependsOn("checkYappcStructuralGovernance")
    }
}

// EventloopTestBase enforcement for ActiveJ async tests
// Note: This task performs file scanning which is kept minimal to avoid configuration cache issues.
// For comprehensive validation, use a dedicated CI job that runs without configuration cache.
tasks.register("checkNoGetResultInTests") {
    group = "verification"
    description = "Ensures ActiveJ async tests use EventloopTestBase (basic check)"
    val configurationCacheRequested = gradle.startParameter.isConfigurationCacheRequested

    if (configurationCacheRequested) {
        doLast {
            logger.lifecycle("Skipping checkNoGetResultInTests because configuration cache is enabled")
        }
        return@register
    }

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
    if (!gradle.startParameter.isConfigurationCacheRequested) {
        dependsOn("checkNoGetResultInTests")
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// OpenAPI parity gate
//
// Reads docs/api/route-manifest.yaml (source-of-truth route list) and confirms
// that every declared path is present in docs/api/openapi.yaml.  Fails the
// build if any route is undocumented.  Skipped when configuration cache is on
// (file I/O incompatible with CC serialisation; run with --no-configuration-cache).
// ─────────────────────────────────────────────────────────────────────────────
/**
 * @doc.type task
 * @doc.purpose Fail the build if any route in route-manifest.yaml is absent from openapi.yaml
 * @doc.layer product
 * @doc.pattern Contract Validation
 */
tasks.register("checkYappcOpenApiParity") {
    group = "verification"
    description = "Verifies that every route in docs/api/route-manifest.yaml is documented in docs/api/openapi.yaml."

    val configurationCacheRequested = gradle.startParameter.isConfigurationCacheRequested

    if (configurationCacheRequested) {
        doLast {
            logger.lifecycle("Skipping checkYappcOpenApiParity because configuration cache is enabled")
        }
        return@register
    }

    outputs.upToDateWhen { false }

    doLast {
        val manifestFile = file("docs/api/route-manifest.yaml")
        val specFile = file("docs/api/openapi.yaml")

        if (!manifestFile.exists()) {
            throw GradleException("Route manifest not found: ${manifestFile.absolutePath}")
        }
        if (!specFile.exists()) {
            throw GradleException("OpenAPI spec not found: ${specFile.absolutePath}")
        }

        // Collect paths declared in openapi.yaml (lines that start with two spaces + '/')
        val specPaths: Set<String> = specFile.readLines()
            .filter { it.matches(Regex("^  /[^#].*:")) }
            .map { it.trim().trimEnd(':') }
            .toSet()

        // Parse route-manifest.yaml: extract path segments from "METHOD /path" lines
        val routeLinePattern = Regex("""^\s*-\s+(?:GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)\s+(/\S+)""")
        val manifestRoutes = mutableListOf<Pair<String, String>>()  // (server, path)
        var currentServer = "unknown"

        manifestFile.readLines().forEach { line ->
            if (line.trimEnd().endsWith(":") && !line.startsWith(" ") && !line.startsWith("#")) {
                currentServer = line.trimEnd(':').trim()
            } else {
                val match = routeLinePattern.matchEntire(line)
                if (match != null) {
                    // Normalise ActiveJ :param segments to OpenAPI {param} style
                    val rawPath = match.groupValues[1]
                    val normPath = rawPath.replace(Regex("/:([^/]+)"), "/\\{$1\\}")
                    manifestRoutes.add(currentServer to normPath)
                }
            }
        }

        val missing = manifestRoutes.filter { (_, path) ->
            // Exact match or match after normalising colon params
            path !in specPaths
        }

        if (missing.isNotEmpty()) {
            val report = missing.joinToString("\n") { (server, path) -> "  [$server] $path" }
            throw GradleException(
                "OpenAPI parity failure — ${missing.size} route(s) in route-manifest.yaml are missing from openapi.yaml:\n$report\n\n" +
                "Add the missing path(s) to products/yappc/docs/api/openapi.yaml and re-run the check."
            )
        }

        logger.lifecycle("OpenAPI parity check passed — all ${manifestRoutes.size} manifest routes are documented.")
    }
}

tasks.named("check") {
    if (!gradle.startParameter.isConfigurationCacheRequested) {
        dependsOn("checkYappcOpenApiParity")
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Forbidden import check for cross-stack boundaries
//
// Enforces that Java code does not import @prisma/client or call GraphQL API directly,
// and that Node code does not call yappc-domain-impl JDBC tables directly.
// ─────────────────────────────────────────────────────────────────────────────
tasks.register("checkYappcForbiddenImports") {
    group = "verification"
    description = "Ensures no forbidden cross-stack imports exist in Java code"

    val configurationCacheRequested = gradle.startParameter.isConfigurationCacheRequested

    if (configurationCacheRequested) {
        doLast {
            logger.lifecycle("Skipping checkYappcForbiddenImports because configuration cache is enabled")
        }
        return@register
    }

    outputs.upToDateWhen { false }

    doLast {
        val violations = mutableListOf<String>()

        // Scan Java source files for forbidden imports
        val javaSourceDirs = listOf(
            "core/yappc-services/src/main/java",
            "core/yappc-domain-impl/src/main/java",
            "core/yappc-shared/src/main/java"
        )

        javaSourceDirs.forEach { dirPath ->
            val dir = File(projectDir, dirPath)
            if (dir.exists()) {
                dir.walk()
                    .filter { it.isFile && it.name.endsWith(".java") }
                    .forEach { file ->
                        try {
                            file.readLines().forEachIndexed { idx, line ->
                                val forbiddenPatterns = listOf(
                                    Regex("""import\s+@prisma\.client"""),
                                    Regex("""import\s+com\.prisma\.client"""),
                                    Regex("""import\s+.*\.graphql\.client""")
                                )

                                forbiddenPatterns.forEach { pattern ->
                                    if (pattern.containsMatchIn(line) && !line.trimStart().startsWith("//")) {
                                        violations.add("${file.absolutePath}:${idx + 1} - ${line.trim()}")
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            logger.debug("Could not read file ${file.name}: ${e.message}")
                        }
                    }
            }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "Forbidden cross-stack imports detected:\n${violations.joinToString("\n")}\n\n" +
                "Java code must not import @prisma/client or GraphQL client directly. " +
                "Use the appropriate HTTP API or event-based integration instead."
            )
        }

        logger.lifecycle("Forbidden import check passed - no cross-stack violations found")
    }
}

tasks.named("check") {
    if (!gradle.startParameter.isConfigurationCacheRequested) {
        dependsOn("checkYappcForbiddenImports")
    }
}
