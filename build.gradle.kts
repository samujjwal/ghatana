/**
 * Ghatana Monorepo - Minimal Root Build Configuration
 *
 * All module configuration is handled by convention plugins applied explicitly
 * in each module's build.gradle.kts file.
 */

import org.gradle.api.plugins.quality.Pmd
import org.gradle.api.plugins.quality.PmdExtension
import org.gradle.api.Project

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

apply(from = rootProject.file("gradle/doc-tag-check.gradle"))

tasks.matching { it.name == "check" }.configureEach {
    dependsOn("checkDocTags")
}

data class ArchitectureDependencyEdge(
    val fromProjectPath: String,
    val toProjectPath: String,
    val configurationName: String,
)

private val runtimeArchitectureConfigurations = setOf(
    "api",
    "implementation",
    "compileOnly",
    "runtimeOnly",
    "annotationProcessor",
)

private val testArchitectureConfigurations = setOf(
    "testImplementation",
    "testCompileOnly",
    "testRuntimeOnly",
    "testAnnotationProcessor",
    "testFixturesApi",
    "testFixturesImplementation",
    "testFixturesCompileOnly",
    "testFixturesRuntimeOnly",
)

private val sharedProductApiAllowlist = setOf(
    // Data Cloud shared APIs (all internal data-cloud modules are shared within the ecosystem)
    ":products:data-cloud:spi",
    ":products:data-cloud:agent-registry",
    ":products:data-cloud:platform-launcher",
    ":products:data-cloud:platform-plugins",
    ":products:data-cloud:platform-analytics",
    ":products:data-cloud:platform-api",
    ":products:data-cloud:platform-config",
    ":products:data-cloud:platform-entity",
    ":products:data-cloud:platform-event",
    // AEP shared APIs (agent runtime, execution engine, registry contracts)
    ":products:aep:aep-operator-contracts",
    ":products:aep:aep-agent-runtime",
    ":products:aep:aep-engine",
    ":products:aep:aep-registry",
    ":products:aep:orchestrator",
    // Virtual Org shared APIs (framework consumed by software-org)
    ":products:virtual-org:modules:framework",
)

fun Project.architectureDependencyEdges(includeTests: Boolean = false): List<ArchitectureDependencyEdge> {
    val trackedConfigurations = buildSet {
        addAll(runtimeArchitectureConfigurations)
        if (includeTests) {
            addAll(testArchitectureConfigurations)
        }
    }

    val trackedSuffixes = buildSet {
        addAll(listOf("Api", "Implementation", "CompileOnly", "RuntimeOnly"))
        if (includeTests) {
            add("AnnotationProcessor")
        }
    }

    return configurations
        .filter { configuration ->
            if (configuration.name in trackedConfigurations) {
                return@filter true
            }

            if (!includeTests && configuration.name.startsWith("test", ignoreCase = true)) {
                return@filter false
            }

            trackedSuffixes.any { suffix -> configuration.name.endsWith(suffix) }
        }
        .flatMap { configuration ->
            configuration.dependencies
                .withType(org.gradle.api.artifacts.ProjectDependency::class.java)
                .map { dependency ->
                    ArchitectureDependencyEdge(
                        fromProjectPath = path,
                        toProjectPath = dependency.path,
                        configurationName = configuration.name,
                    )
                }
        }
}

fun isSharedProductApi(projectPath: String): Boolean = projectPath in sharedProductApiAllowlist

fun isTestConfiguration(configurationName: String): Boolean {
    return configurationName in testArchitectureConfigurations ||
        configurationName.startsWith("test", ignoreCase = true)
}

fun Project.testOnlyArchitectureDependencyEdges(): List<ArchitectureDependencyEdge> {
    return architectureDependencyEdges(includeTests = true)
        .filter { edge -> isTestConfiguration(edge.configurationName) }
}

fun architectureScope(projectPath: String): String = when {
    projectPath.startsWith(":platform:contracts") -> "contracts"
    projectPath.startsWith(":platform:") -> "platform"
    projectPath.startsWith(":platform-kernel:") -> "platform-kernel"
    projectPath.startsWith(":platform-plugins:") -> "platform-plugins"
    projectPath.startsWith(":shared-services:") -> "shared-services"
    projectPath.startsWith(":products:") && isSharedProductApi(projectPath) -> "shared-product-api"
    projectPath.startsWith(":products:") -> "product"
    else -> "other"
}

fun productRoot(projectPath: String): String? {
    if (!projectPath.startsWith(":products:")) {
        return null
    }

    val segments = projectPath.split(':').filter { it.isNotBlank() }
    return if (segments.size >= 2) {
        ":${segments[0]}:${segments[1]}"
    } else {
        null
    }
}

fun isAllowedArchitectureDependency(fromProjectPath: String, toProjectPath: String): Boolean {
    if (fromProjectPath == toProjectPath) {
        return true
    }

    val fromScope = architectureScope(fromProjectPath)
    val toScope = architectureScope(toProjectPath)

    return when (fromScope) {
        "contracts" -> toScope == "contracts"
        "platform" -> toScope == "platform" || toScope == "contracts"
        "platform-kernel" -> toScope == "platform-kernel" || toScope == "platform" || toScope == "contracts"
        "platform-plugins" ->
            toScope == "platform-plugins" ||
                toScope == "platform-kernel" ||
                toScope == "platform" ||
                toScope == "contracts"
        "shared-services" ->
            toScope == "shared-services" ||
                toScope == "platform-kernel" ||
                toScope == "platform" ||
                toScope == "contracts"
        "shared-product-api" ->
            toScope == "shared-product-api" ||
                toScope == "platform-plugins" ||
                toScope == "platform-kernel" ||
                toScope == "platform" ||
                toScope == "contracts"
        "product" -> when (toScope) {
            "platform", "platform-kernel", "platform-plugins", "contracts", "shared-services", "shared-product-api" -> true
            "product" -> productRoot(fromProjectPath) == productRoot(toProjectPath)
            else -> false
        }
        else -> true
    }
}

fun detectArchitectureCycles(edges: List<ArchitectureDependencyEdge>): List<List<String>> {
    val adjacency = edges
        .groupBy({ edge -> edge.fromProjectPath }, { edge -> edge.toProjectPath })
        .mapValues { (_, dependencies) -> dependencies.distinct().sorted() }

    val visitState = mutableMapOf<String, Int>()
    val stack = mutableListOf<String>()
    val cycles = linkedSetOf<List<String>>()

    fun walk(projectPath: String) {
        when (visitState[projectPath]) {
            1 -> {
                val cycleStart = stack.indexOf(projectPath)
                if (cycleStart >= 0) {
                    val cycle = stack.subList(cycleStart, stack.size).toMutableList()
                    cycle += projectPath
                    cycles += cycle
                }
                return
            }

            2 -> return
        }

        visitState[projectPath] = 1
        stack += projectPath
        adjacency[projectPath].orEmpty().forEach(::walk)
        stack.removeAt(stack.lastIndex)
        visitState[projectPath] = 2
    }

    (adjacency.keys + adjacency.values.flatten()).distinct().sorted().forEach(::walk)
    return cycles.toList()
}

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

    // Global test configuration: exclude integration tests by default
    // Run integration tests with: ./gradlew test -DincludeIntegrationTests=true
    plugins.withId("java") {
        tasks.withType<Test>().configureEach {
            useJUnitPlatform {
                if (System.getProperty("includeIntegrationTests") == null) {
                    excludeTags("integration")
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
    dependsOn("checkPluginPurity")

    doLast {
        println("✅ Architecture validation complete")
    }
}

// ── Plugin Purity Gate ────────────────────────────────────────────────────────
// Mirrors the kernel purity check but applied to platform-plugins source trees.
// Verified terms are the same banned product-domain concepts.

val PLUGIN_BANNED_TERMS = listOf(
    "PHR", "Finance", "FINANCE", "CLINICAL", "phr-kernel", "finance-kernel",
    "SOX", "HIPAA", "GDPR", "PCI-DSS", "PCIDSS", "trade\\.records", "patient\\.records",
    "nepal-2081", "sebon", "BillingLedger", "RiskType\\.CLINICAL",
    "plugin-billing-ledger", "plugin_billing_ledger"
)

tasks.register("checkPluginPurity") {
    group = "verification"
    description = "Fails the build if product domain terms appear in platform-plugin main sources."
    doLast {
        val pluginsRoot = file("platform-plugins")
        if (!pluginsRoot.exists()) return@doLast
        val violations = mutableListOf<String>()
        pluginsRoot.walkTopDown()
            .filter { it.isFile && it.extension == "java" }
            .filter { f ->
                // Only scan production source trees (not tests)
                val rel = f.relativeTo(pluginsRoot).path.replace('\\', '/')
                rel.contains("/src/main/")
            }
            .forEach { javaFile ->
                val content = javaFile.readText()
                PLUGIN_BANNED_TERMS.forEach { term ->
                    val regex = Regex(term)
                    if (regex.containsMatchIn(content)) {
                        violations += "${javaFile.relativeTo(rootDir)}: contains banned term '$term'"
                    }
                }
            }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Plugin purity violation — product domain terms found in plugin main sources:\n" +
                violations.joinToString("\n") { "  $it" }
            )
        }
        logger.lifecycle("checkPluginPurity: PASSED — no product domain terms in plugin main sources.")
    }
}


tasks.register("validateNoCircularDependencies") {
    group = "verification"
    description = "Check for circular dependencies between modules"
    val configurationCacheRequested = gradle.startParameter.isConfigurationCacheRequested

    if (configurationCacheRequested) {
        doLast {
            logger.lifecycle("Skipping validateNoCircularDependencies because configuration cache is enabled")
        }
        return@register
    }

    doLast {
        val edges = subprojects.flatMap { project -> project.architectureDependencyEdges(includeTests = false) }
        val cycles = detectArchitectureCycles(edges)

        if (cycles.isNotEmpty()) {
            val message = buildString {
                appendLine("Circular project dependencies detected:")
                cycles.forEach { cycle ->
                    appendLine("  - ${cycle.joinToString(" -> ")}")
                }
            }
            throw GradleException(message)
        }

        println("✅ No circular project dependencies found")
    }
}

tasks.register("validateModuleBoundaries") {
    group = "verification"
    description = "Validate module boundaries per architecture rules"
    val configurationCacheRequested = gradle.startParameter.isConfigurationCacheRequested

    if (configurationCacheRequested) {
        doLast {
            logger.lifecycle("Skipping validateModuleBoundaries because configuration cache is enabled")
        }
        return@register
    }

    doLast {
        val edges = subprojects.flatMap { project -> project.architectureDependencyEdges(includeTests = false) }
        val violations = edges.filter { edge ->
            when (architectureScope(edge.fromProjectPath)) {
                "platform" -> architectureScope(edge.toProjectPath) == "product"
                "platform-kernel" -> architectureScope(edge.toProjectPath) == "product"
                "platform-plugins" -> architectureScope(edge.toProjectPath) == "product"
                "shared-services" -> architectureScope(edge.toProjectPath) == "product"
                else -> false
            }
        }

        if (violations.isNotEmpty()) {
            val message = buildString {
                appendLine("Module boundary violations detected:")
                violations.sortedWith(compareBy({ it.fromProjectPath }, { it.toProjectPath }, { it.configurationName }))
                    .forEach { violation ->
                        appendLine(
                            "  - ${violation.fromProjectPath} depends on ${violation.toProjectPath} via ${violation.configurationName}"
                        )
                    }
            }
            throw GradleException(message)
        }

        println("✅ Module boundary validation passed")
    }
}

tasks.register("validateDependencyDirection") {
    group = "verification"
    description = "Validate dependency direction (platform → products)"
    val configurationCacheRequested = gradle.startParameter.isConfigurationCacheRequested

    if (configurationCacheRequested) {
        doLast {
            logger.lifecycle("Skipping validateDependencyDirection because configuration cache is enabled")
        }
        return@register
    }

    doLast {
        val edges = subprojects.flatMap { project -> project.architectureDependencyEdges(includeTests = false) }
        val violations = edges.filterNot { edge ->
            isAllowedArchitectureDependency(edge.fromProjectPath, edge.toProjectPath)
        }

        if (violations.isNotEmpty()) {
            val message = buildString {
                appendLine("Dependency direction violations detected:")
                violations.sortedWith(compareBy({ it.fromProjectPath }, { it.toProjectPath }, { it.configurationName }))
                    .forEach { violation ->
                        appendLine(
                            "  - ${violation.fromProjectPath} -> ${violation.toProjectPath} (${violation.configurationName})"
                        )
                    }
            }
            throw GradleException(message)
        }

        println("✅ Dependency direction validation passed")
    }
}

tasks.register("validateNoDuplicateUtils") {
    group = "verification"
    description = "Validate no duplicate utility classes exist"
    val configurationCacheRequested = gradle.startParameter.isConfigurationCacheRequested

    if (configurationCacheRequested) {
        doLast {
            logger.lifecycle("Skipping validateNoDuplicateUtils because configuration cache is enabled")
        }
        return@register
    }

    doLast {
        val utilsClasses = mutableMapOf<String, MutableList<String>>()

        subprojects.forEach { project ->
            project.fileTree("src/main/java").matching {
                include("**/util/*.java")
                include("**/utils/*.java")
                include("**/Utils.java")
            }.forEach { file ->
                val className = file.nameWithoutExtension
                if (className == "package-info") {
                    return@forEach
                }
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

tasks.matching { it.name == "check" }.configureEach {
    if (!gradle.startParameter.isConfigurationCacheRequested) {
        dependsOn("validateArchitecture")
    }
}
