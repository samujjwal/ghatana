/**
 * YAPPC Product Settings
 *
 * Enables standalone build: cd products/yappc && ../../gradlew build
 * Also works within monorepo: ./gradlew :products:yappc:build
 */
rootProject.name = "yappc"

// ============================================================================
// Build Context Detection
// ============================================================================
val isStandaloneBuild = gradle.parent == null
val monorepoRoot = if (isStandaloneBuild) rootDir.parentFile.parentFile else rootDir.parentFile.parentFile
val productDir = rootDir

extra["isStandaloneBuild"] = isStandaloneBuild
extra["monorepoRoot"] = monorepoRoot
extra["productName"] = "yappc"

logger.lifecycle("┌─────────────────────────────────────────────────────────────")
logger.lifecycle("│ Product: YAPPC — AI-Native Product Development Platform")
logger.lifecycle("│ Mode: ${if (isStandaloneBuild) "STANDALONE" else "MONOREPO"}")
logger.lifecycle("│ Product Dir: $productDir")
logger.lifecycle("│ Monorepo Root: $monorepoRoot")
logger.lifecycle("└─────────────────────────────────────────────────────────────")

// ============================================================================
// Helper Functions
// ============================================================================
fun includeLib(name: String, subPath: String = "java") {
    val libDir = File(monorepoRoot, "libs/$subPath/$name")
    if (libDir.exists()) {
        include("libs:$name")
        project(":libs:$name").projectDir = libDir
    }
}

fun includePlatformLib(name: String) {
    val libDir = File(monorepoRoot, "platform/java/$name")
    if (libDir.exists()) {
        include("platform:java:$name")
        project(":platform:java:$name").projectDir = libDir
    }
}

fun includeDirectModules(parentProjectPath: String, rootDir: File) {
    val normalizedParentProjectPath =
        if (parentProjectPath.startsWith(":")) parentProjectPath else ":$parentProjectPath"

    rootDir
        .listFiles()
        .orEmpty()
        .filter { child ->
            child.isDirectory &&
                (File(child, "build.gradle.kts").exists() || File(child, "build.gradle").exists())
        }
        .sortedBy { it.name }
        .forEach { child ->
            val projectPath = "$normalizedParentProjectPath:${child.name}"
            include(projectPath)
            project(projectPath).projectDir = child
        }
}

fun includeNamedModules(parentProjectPath: String, rootDir: File, moduleNames: List<String>) {
    val normalizedParentProjectPath =
        if (parentProjectPath.startsWith(":")) parentProjectPath else ":$parentProjectPath"

    moduleNames
        .distinct()
        .sorted()
        .forEach { moduleName ->
            val moduleDir = File(rootDir, moduleName)
            if (moduleDir.exists() &&
                (File(moduleDir, "build.gradle.kts").exists() || File(moduleDir, "build.gradle").exists())) {
                val projectPath = "$normalizedParentProjectPath:$moduleName"
                include(projectPath)
                project(projectPath).projectDir = moduleDir
            }
        }
}

// ============================================================================
// YAPPC Product Modules
// ============================================================================

// --- Platform Layer ---
include(":platform")

// --- Application Entry Points ---
include(":services")
// backend modules removed (2026-03-23) — functionality consolidated into core modules

// --- Backend Service Modules ---
// backend modules removed (2026-03-23) — functionality consolidated into core modules
// NOTE: backend:websocket, backend:api, backend:persistence, backend:auth removed

// --- Core: Reusable Service Modules (SIMP-Y8 complete: consolidated into :core:yappc-services) ---

// --- Core: Scaffold Engine ---
include(":core:scaffold")
include(":core:scaffold:api")
include(":core:scaffold:core") // aggregator re-exporting templates + engine + generators
include(":core:scaffold:templates")
include(":core:scaffold:engine")
include(":core:scaffold:generators")
// NOTE: core:scaffold:packs removed — sources merged into core:scaffold:core

// --- Core: AI & Agents ---
include(":core:ai")
include(":core:agents")
include(":core:agents:runtime")
include(":core:agents:workflow")
include(":core:agents:common")
include(":core:agents:code-specialists")
include(":core:agents:delivery-specialists")
include(":core:agents:architecture-specialists")
include(":core:agents:testing-specialists")
// NOTE: core:agents:specialists removed — sources distributed to code/delivery/architecture/testing-specialists

// --- Core: Refactorer ---
include(":core:refactorer:api")
include(":core:refactorer:engine")

// --- Core: Supplementary ---
include(":core:cli-tools")
include(":core:knowledge-graph")
// NOTE: core:lifecycle removed — absorbed into core:yappc-services
// NOTE: core:framework removed — absorbed into core:yappc-infrastructure
// NOTE: core:domain removed — absorbed into core:yappc-domain
// NOTE: core:agents:specialists removed — sources distributed to specialist modules

// --- Core: Domain (Phase 2.1: yappc-domain-impl - api split deferred) ---
// NOTE: yappc-domain-api creation deferred - api/impl split to be done later
// NOTE: core:yappc-agents removed — consolidated into core:agents
include(":core:yappc-domain-impl")
include(":core:yappc-services")
include(":core:yappc-infrastructure")
// NOTE: core:yappc-api removed — duplicate controllers consolidated into yappc-domain-impl
include(":core:yappc-shared")

// --- Infrastructure ---
include(":infrastructure:datacloud")
// NOTE: infrastructure:security removed — sources consolidated

// --- YAPPC Shared Libraries ---
include(":libs:java:yappc-domain")

// --- Examples (plugin SDK reference implementations) ---
include(":examples:sample-build-generator-plugin")

// --- Gradle build tooling & validation tests ---
val validationTestsDir = File(rootDir, "tools/validation-tests")
if (validationTestsDir.exists()) {
    include(":tools:validation-tests")
}

// ============================================================================
// Monorepo Shared Libraries (Standalone build only)
// ============================================================================
if (isStandaloneBuild) {
    logger.lifecycle("Including shared platform libraries for standalone build...")

    val monorepoPlatformJava = File(monorepoRoot, "platform/java")
    if (monorepoPlatformJava.exists()) {
        include("platform:java")
        project(":platform:java").projectDir = monorepoPlatformJava
    }

    val monorepoPlatformContracts = File(monorepoRoot, "platform/contracts")
    if (monorepoPlatformContracts.exists()) {
        include("platform:contracts")
        project(":platform:contracts").projectDir = monorepoPlatformContracts
    }

    val platformKernelRoot = File(monorepoRoot, "platform-kernel")
    if (platformKernelRoot.exists()) {
        include("platform-kernel")
        project(":platform-kernel").projectDir = platformKernelRoot
        includeNamedModules(
            "platform-kernel",
            platformKernelRoot,
            listOf("kernel-core", "kernel-plugin")
        )
    }

    include("products:yappc")
    val yappcAliasRoot = File(rootDir, "build/gradle-alias/products-yappc").apply { mkdirs() }
    project(":products:yappc").projectDir = yappcAliasRoot

    val yappcAliasModules =
        listOf(
            "platform",
            "services",
            // backend modules removed (2026-03-23)
            // services:platform and services:lifecycle removed (SIMP-Y8 complete — consolidated into yappc-services)
            // services:ai, services:scaffold removed — merged into services:lifecycle
            "core:scaffold",
            "core:scaffold:api",
            "core:scaffold:core",
            "core:scaffold:templates",
            "core:scaffold:engine",
            "core:scaffold:generators",
            // core:scaffold:packs removed — merged into core:scaffold:core
            "core:ai",
            "core:agents",
            "core:agents:runtime",
            "core:agents:workflow",
            "core:agents:common",
            "core:agents:code-specialists",
            "core:agents:delivery-specialists",
            "core:agents:architecture-specialists",
            "core:agents:testing-specialists",
            // core:agents:specialists removed — distributed to specialist modules
            "core:refactorer:api",
            "core:refactorer:engine",
            "core:cli-tools",
            "core:knowledge-graph",
            // core:lifecycle removed — absorbed into core:yappc-services
            // core:framework removed — absorbed into core:yappc-infrastructure
            // core:domain removed — absorbed into core:yappc-domain-impl (renamed from core:yappc-domain, Phase 3)
            "infrastructure:datacloud",
            // infrastructure:security removed — sources consolidated
            "libs:java:yappc-domain",
            // Core yappc-* modules (Phase 2.1: yappc-domain-impl - api split deferred)
            // NOTE: yappc-domain-api creation deferred - api/impl split to be done later
            "core:yappc-domain-impl",
            "core:yappc-services",
            "core:yappc-infrastructure",
            // core:yappc-agents removed — consolidated into core:agents
            "core:yappc-api",
            "core:yappc-shared")

    yappcAliasModules.forEach { modulePath ->
        val pathParts = modulePath.split(":")
        if (pathParts.size > 1) {
            for (i in 1 until pathParts.size) {
                File(yappcAliasRoot, pathParts.take(i).joinToString("/")).mkdirs()
            }
        }
        include("products:yappc:$modulePath")
        project(":products:yappc:$modulePath").projectDir = project(":$modulePath").projectDir
    }

    // Core platform libraries
    monorepoPlatformJava
        .listFiles()
        .orEmpty()
        .filter { it.isDirectory }
        .map { it.name }
        .sorted()
        .forEach { includePlatformLib(it) }

    val aiIntegrationDir = File(monorepoRoot, "platform/java/ai-integration")
    listOf("feature-store", "observability", "registry").forEach { moduleName ->
        val moduleDir = File(aiIntegrationDir, moduleName)
        if (moduleDir.exists()) {
            include("platform:java:ai-integration:$moduleName")
            project(":platform:java:ai-integration:$moduleName").projectDir = moduleDir
        }
    }

    // Peer product dependencies
    val productsRoot = File(monorepoRoot, "products")
    if (productsRoot.exists()) {
        include("products")
        project(":products").projectDir = productsRoot
    }

    val dataCloudRoot = File(monorepoRoot, "products/data-cloud")
    if (dataCloudRoot.exists()) {
        include("products:data-cloud")
        project(":products:data-cloud").projectDir = dataCloudRoot
        includeNamedModules(
            "products:data-cloud",
            dataCloudRoot,
            listOf(
                "agent-registry",
                "platform-analytics",
                "platform-api",
                "platform-config",
                "platform-entity",
                "platform-event",
                "platform-launcher",
                "platform-plugins",
                "spi"
            )
        )
    }

    val aepRoot = File(monorepoRoot, "products/aep")
    if (aepRoot.exists()) {
        include("products:aep")
        project(":products:aep").projectDir = aepRoot
        includeNamedModules(
            "products:aep",
            aepRoot,
            listOf(
                "aep-agent-runtime",
                "aep-analytics",
                "aep-engine",
                "aep-operator-contracts",
                "aep-registry",
                "aep-security",
                "orchestrator"
            )
        )
    }

    val sharedServicesRoot = File(monorepoRoot, "shared-services")
    val authGatewayRoot = File(sharedServicesRoot, "auth-gateway")
    if (sharedServicesRoot.exists()) {
        include("shared-services")
        project(":shared-services").projectDir = sharedServicesRoot
        if (authGatewayRoot.exists()) {
            include("shared-services:auth-gateway")
            project(":shared-services:auth-gateway").projectDir = authGatewayRoot
        }
    }
}

// ============================================================================
// Plugin Management & Dependency Resolution
// ============================================================================
pluginManagement {
    if (gradle.parent == null) {
        val standaloneMonorepoRoot = rootDir.parentFile.parentFile
        includeBuild(File(standaloneMonorepoRoot, "build-logic")) {
            name = "ghatana-build-logic"
        }
    }
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }

    if (gradle.parent == null) {
        versionCatalogs {
            create("libs") {
                from(files(File(monorepoRoot, "gradle/libs.versions.toml")))
            }
        }
    }
}

logger.lifecycle("YAPPC build configuration complete!")
