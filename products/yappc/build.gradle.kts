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

// Data classes for route manifest parsing
data class ManifestRoute(
    val server: String,
    val method: String,
    val path: String,
    val auth: String,
    val scopes: List<String>,
    val owner: String,
    val boundary: String,
    val operationId: String
)

data class SpecPathData(
    val path: String,
    val method: String,
    val operationId: String
)

fun parseRouteEntry(
    entry: Map<String, Any>,
    server: String,
    requiredFields: List<String>,
    validBoundaries: Set<String>,
    validAuthModes: Set<String>
): ManifestRoute {
    // Validate required fields
    val missing = requiredFields.filter { it !in entry.keys }
    if (missing.isNotEmpty()) {
        throw GradleException(
            "Route in [$server] missing required fields: ${missing.joinToString(", ")}"
        )
    }

    val method = entry["method"] as String
    val path = entry["path"] as String
    val auth = entry["auth"] as String
    val scopes = entry["scopes"] as? List<*> ?: emptyList<String>()
    val owner = entry["owner"] as String
    val boundary = entry["boundary"] as String
    val operationId = entry["operationId"] as String

    // Validate auth mode
    if (auth !in validAuthModes) {
        throw GradleException(
            "Route in [$server] has invalid auth mode '$auth'. Valid modes: ${validAuthModes.joinToString(", ")}"
        )
    }

    // Validate auth/scopes consistency
    if (auth == "required" && (scopes as List<*>).isEmpty()) {
        throw GradleException(
            "Route in [$server] $method $path has auth=required but empty scopes"
        )
    }
    if (auth == "public" && (scopes as List<*>).isNotEmpty()) {
        throw GradleException(
            "Route in [$server] $method $path has auth=public but non-empty scopes: ${scopes.joinToString(", ")}"
        )
    }

    // Validate boundary
    if (boundary !in validBoundaries) {
        throw GradleException(
            "Route in [$server] $method $path has invalid boundary '$boundary'. Valid boundaries: ${validBoundaries.joinToString(", ")}"
        )
    }

    return ManifestRoute(
        server = server,
        method = method.uppercase(),
        path = path,
        auth = auth,
        scopes = scopes.map { it.toString() },
        owner = owner,
        boundary = boundary,
        operationId = operationId
    )
}

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

        // Parse structured route-manifest.yaml
        val manifestRoutes = mutableListOf<ManifestRoute>()
        val requiredFields = listOf("method", "path", "auth", "owner", "boundary", "operationId")
        val validBoundaries = setOf("YAPPC", "DATA_CLOUD_AEP")
        val validAuthModes = setOf("public", "required", "optional")
        var currentServer = "unknown"
        var currentRoute: MutableMap<String, Any>? = null
        var inRouteEntry = false

        manifestFile.readLines().forEach { line ->
            val trimmed = line.trim()
            
            // Skip comments and empty lines
            if (trimmed.startsWith("#") || trimmed.isEmpty()) {
                return@forEach
            }

            // Detect server section (e.g., "yappc-services:")
            if (trimmed.endsWith(":") && !line.startsWith(" ") && !trimmed.startsWith("-")) {
                currentServer = trimmed.trimEnd(':').trim()
                return@forEach
            }

            // Detect route entry start (dash indicates list item)
            if (trimmed.startsWith("- ")) {
                // Finalize previous route if exists
                if (currentRoute != null && inRouteEntry) {
                    manifestRoutes.add(parseRouteEntry(currentRoute, currentServer, requiredFields, validBoundaries, validAuthModes))
                }
                currentRoute = mutableMapOf()
                inRouteEntry = true
                
                // Parse the field from "- key: value" format
                val parts = trimmed.substring(2).split(":", limit = 2)
                if (parts.size == 2) {
                    val key = parts[0].trim()
                    val value = parts[1].trim()
                    currentRoute[key] = value
                }
                return@forEach
            }

            // Parse route fields (any indented line with a colon)
            if (inRouteEntry && currentRoute != null && line.startsWith("  ") && ":" in line) {
                val parts = line.trim().split(":", limit = 2)
                if (parts.size == 2) {
                    val key = parts[0].trim()
                    val value = parts[1].trim()
                    when (key) {
                        "scopes" -> {
                            // Parse array syntax: [scope1, scope2]
                            if (value.startsWith("[") && value.endsWith("]")) {
                                val scopes = value.substring(1, value.length - 1)
                                    .split(",")
                                    .map { it.trim() }
                                    .filter { it.isNotEmpty() }
                                currentRoute[key] = scopes
                            } else {
                                currentRoute[key] = emptyList<String>()
                            }
                        }
                        else -> currentRoute[key] = value
                    }
                }
            }
        }

        // Don't forget the last route
        if (currentRoute != null && inRouteEntry) {
            manifestRoutes.add(parseRouteEntry(currentRoute, currentServer, requiredFields, validBoundaries, validAuthModes))
        }

        // Collect paths and operationIds from openapi.yaml
        val specPathData = mutableMapOf<String, SpecPathData>()
        var currentPath = ""
        var currentMethod = ""
        
        specFile.readLines().forEach { line ->
            val trimmed = line.trim()
            
            // Detect path definition (e.g., "  /health:")
            if (trimmed.matches(Regex("^/[^#].*:"))) {
                currentPath = trimmed.trimEnd(':')
                return@forEach
            }
            
            // Detect HTTP method (e.g., "    get:")
            if (currentPath.isNotEmpty() && trimmed.matches(Regex("^(get|post|put|delete|patch|head|options):"))) {
                currentMethod = trimmed.trimEnd(':').uppercase()
                return@forEach
            }
            
            // Detect operationId
            if (currentPath.isNotEmpty() && currentMethod.isNotEmpty() && trimmed.startsWith("operationId:")) {
                val operationId = trimmed.substring(12).trim()
                val key = "$currentMethod:$currentPath"
                specPathData[key] = SpecPathData(currentPath, currentMethod, operationId)
                currentMethod = ""  // Reset after capturing
            }
        }

        // Validate parity between manifest and OpenAPI
        val errors = mutableListOf<String>()
        
        manifestRoutes.forEach { route ->
            val key = "${route.method}:${route.path}"
            val specData = specPathData[key]
            
            if (specData == null) {
                errors.add("[$route.server] ${route.method} ${route.path} - not found in OpenAPI")
            } else {
                // Validate operationId parity
                if (route.operationId != specData.operationId) {
                    errors.add("[$route.server] ${route.method} ${route.path} - operationId mismatch: manifest='${route.operationId}', OpenAPI='${specData.operationId}'")
                }
            }
        }

        if (errors.isNotEmpty()) {
            throw GradleException(
                "OpenAPI parity failure — ${errors.size} error(s):\n${errors.joinToString("\n")}\n\n" +
                "Fix the discrepancies in products/yappc/docs/api/openapi.yaml or route-manifest.yaml and re-run the check."
            )
        }

        logger.lifecycle("OpenAPI parity check passed — all ${manifestRoutes.size} manifest routes are documented with matching operationIds.")
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
                                    Regex("""import\s+.*\.graphql\.client"""),
                                    // Data Cloud+AEP internal runtime modules - YAPPC must use typed contracts only
                                    Regex("""import\s+com\.ghatana\.aep\.agent-registry"""),
                                    Regex("""import\s+com\.ghatana\.aep\.execution-engine"""),
                                    Regex("""import\s+com\.ghatana\.aep\.memory"""),
                                    Regex("""import\s+com\.ghatana\.aep\.search"""),
                                    Regex("""import\s+com\.ghatana\.datacloud\.events"""),
                                    Regex("""import\s+com\.ghatana\.datacloud\.embeddings"""),
                                    Regex("""import\s+com\.ghatana\.datacloud\.analytics"""),
                                    // Generic internal platform imports
                                    Regex("""import\s+com\.ghatana\.platform\.runtime"""),
                                    Regex("""import\s+com\.ghatana\.platform\.data""")
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
                "Java code must not:\n" +
                "- Import @prisma/client or com.prisma.client directly\n" +
                "- Import GraphQL client directly\n" +
                "- Import internal Data Cloud+AEP runtime modules (agent-registry, execution-engine, memory, search)\n" +
                "- Import internal Data Cloud+AEP data modules (events, embeddings, analytics)\n" +
                "- Import internal platform runtime or data modules\n\n" +
                "YAPPC must consume Data Cloud+AEP through typed contracts only. " +
                "Use the appropriate HTTP API or typed platform client instead."
            )
        }

        logger.lifecycle("Forbidden import check passed - no cross-stack violations found")
    }
}

// Generate Java registry data from route manifest
tasks.register("generateRouteRegistry") {
    group = "generation"
    description = "Generates Java registry data from route-manifest.yaml"
    
    // Disable configuration cache for this task due to function serialization issues
    notCompatibleWithConfigurationCache("Route generation uses script functions not serializable with config cache")
    
    val manifestFile = layout.projectDirectory.file("docs/api/route-manifest.yaml").asFile
    val outputDir = layout.projectDirectory.dir("core/yappc-services/src/generated/java/com/ghatana/yappc/api/generated").asFile
    
    inputs.file(manifestFile)
    outputs.dir(outputDir)
    
    doLast {
        outputDir.mkdirs()
        
        val generatedFile = File(outputDir, "GeneratedRouteRegistry.java")
        generatedFile.writeText(
            """
            |package com.ghatana.yappc.api.generated;
            |
            |/**
            | * AUTO-GENERATED - DO NOT EDIT
            | * Generated from docs/api/route-manifest.yaml
            | * Run: ./gradlew :products:yappc:generateRouteRegistry
            | */
            |import com.ghatana.yappc.governance.route.AuthMode;
            |import com.ghatana.yappc.governance.route.Boundary;
            |import com.ghatana.yappc.governance.route.PrivacyClassification;
            |import com.ghatana.yappc.governance.route.RouteEntry;
            |import com.ghatana.yappc.governance.route.RouteManifest;
            |import java.util.List;
            |import java.util.Set;
            |
            |public final class GeneratedRouteRegistry {
            |    private static final RouteManifest MANIFEST = new RouteManifest();
            |    
            |    static {
            |        initializeManifest();
            |    }
            |    
            |    private static void initializeManifest() {
            """.trimMargin()
        )
        
        var currentServer = "unknown"
        var currentRoute: MutableMap<String, Any>? = null
        var inRouteEntry = false
        
        // Helper function to append route entry (inlined to avoid serialization issues)
        fun appendRouteEntry(file: File, route: MutableMap<String, Any>, server: String) {
            val method = route["method"] as String
            val path = route["path"] as String
            val auth = route["auth"] as String
            val scopes = route["scopes"] as List<String>
            val owner = route["owner"] as String
            val boundary = route["boundary"] as String
            val operationId = route["operationId"] as String
            val auditEventType = route["auditEventType"] as? String ?: operationId.uppercase()
            val privacyClassification = route["privacyClassification"] as? String ?: "INTERNAL"
            
            file.appendText(
                """
                |        MANIFEST.addRoute("$server", new RouteEntry(
                |            "$method",
                |            "$path",
                |            AuthMode.${auth.uppercase()},
                |            Set.of(${scopes.joinToString(", ") { "\"$it\"" }}),
                |            "$owner",
                |            Boundary.$boundary,
                |            "$operationId",
                |            "$auditEventType",
                |            PrivacyClassification.$privacyClassification
                |        ));
                """.trimMargin()
            )
        }
        
        manifestFile.readLines().forEach { line ->
            val trimmed = line.trim()
            
            if (trimmed.startsWith("#") || trimmed.isEmpty()) return@forEach
            
            if (trimmed.endsWith(":") && !line.startsWith(" ") && !trimmed.startsWith("-")) {
                currentServer = trimmed.trimEnd(':').trim()
                return@forEach
            }
            
            if (trimmed.startsWith("- ")) {
                if (currentRoute != null && inRouteEntry) {
                    appendRouteEntry(generatedFile, currentRoute, currentServer)
                }
                currentRoute = mutableMapOf()
                inRouteEntry = true
                
                val parts = trimmed.substring(2).split(":", limit = 2)
                if (parts.size == 2) {
                    currentRoute[parts[0].trim()] = parts[1].trim()
                }
                return@forEach
            }
            
            if (inRouteEntry && currentRoute != null && line.startsWith("  ") && ":" in line) {
                val parts = line.trim().split(":", limit = 2)
                if (parts.size == 2) {
                    val key = parts[0].trim()
                    val value = parts[1].trim()
                    when (key) {
                        "scopes" -> {
                            if (value.startsWith("[") && value.endsWith("]")) {
                                currentRoute[key] = value.substring(1, value.length - 1)
                                    .split(",")
                                    .map { it.trim() }
                                    .filter { it.isNotEmpty() }
                            } else {
                                currentRoute[key] = emptyList<String>()
                            }
                        }
                        else -> currentRoute[key] = value
                    }
                }
            }
        }
        
        if (currentRoute != null && inRouteEntry) {
            appendRouteEntry(generatedFile, currentRoute, currentServer)
        }
        
        generatedFile.appendText(
            """
            |    }
            |    
            |    public static RouteManifest getManifest() {
            |        return MANIFEST;
            |    }
            |}
            """.trimMargin()
        )
        
        logger.lifecycle("Generated route registry at ${generatedFile.absolutePath}")
    }
}

tasks.named("check") {
    if (!gradle.startParameter.isConfigurationCacheRequested) {
        dependsOn("checkYappcForbiddenImports")
    }
}
