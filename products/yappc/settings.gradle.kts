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

// --- Core: Reusable Service Modules (Phase 2: moved from services/ to separate deployables from libraries) ---
include(":core:services-platform")   // was: services:platform — reusable platform service layer
include(":core:services-lifecycle")  // was: services:lifecycle — reusable lifecycle orchestration library

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
include(":core:spi")
// NOTE: core:lifecycle removed — absorbed into core:yappc-services
// NOTE: core:framework removed — absorbed into core:yappc-infrastructure
// NOTE: core:domain removed — absorbed into core:yappc-domain
// NOTE: core:agents:specialists removed — sources distributed to specialist modules

// --- Infrastructure ---
include(":infrastructure:datacloud")
// NOTE: infrastructure:security removed — sources consolidated

// --- YAPPC Shared Libraries ---
include(":libs:java:yappc-domain")

// --- Examples (plugin SDK reference implementations) ---
include(":examples:sample-build-generator-plugin")

// --- Gradle build tooling & validation tests ---
include(":tools:validation-tests")

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

    include("products:yappc")
    val yappcAliasRoot = File(rootDir, "build/gradle-alias/products-yappc").apply { mkdirs() }
    project(":products:yappc").projectDir = yappcAliasRoot

    val yappcAliasModules =
        listOf(
            "platform",
            "services",
            // backend modules removed (2026-03-23)
            // services:platform/lifecycle moved to core (Phase 2: separate deployables from reusables)
            "core:services-platform",
            "core:services-lifecycle",
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
            "core:spi",
            // core:lifecycle removed — absorbed into core:yappc-services
            // core:framework removed — absorbed into core:yappc-infrastructure
            // core:domain removed — absorbed into core:yappc-domain-impl (renamed from core:yappc-domain, Phase 3)
            "infrastructure:datacloud",
            // infrastructure:security removed — sources consolidated
            "libs:java:yappc-domain",
            // Core yappc-* modules (Phase 3: yappc-domain renamed to yappc-domain-impl)
            "core:yappc-domain-impl",
            "core:yappc-services",
            "core:yappc-infrastructure",
            "core:yappc-agents",
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
    val dataCloudPlatform = File(dataCloudRoot, "platform")
    val dataCloudSpi = File(dataCloudRoot, "spi")
    if (dataCloudRoot.exists() && dataCloudPlatform.exists()) {
        include("products:data-cloud")
        project(":products:data-cloud").projectDir = dataCloudRoot
        include("products:data-cloud:platform")
        project(":products:data-cloud:platform").projectDir = dataCloudPlatform
        if (dataCloudSpi.exists()) {
            include("products:data-cloud:spi")
            project(":products:data-cloud:spi").projectDir = dataCloudSpi
        }
    }

    val aepRoot = File(monorepoRoot, "products/aep")
    val aepContracts = File(aepRoot, "aep-operator-contracts")
    val aepEngine = File(aepRoot, "aep-engine")
    val aepCentralRuntime = File(aepRoot, "aep-central-runtime")
    val aepOrchestrator = File(aepRoot, "orchestrator")
    if (aepRoot.exists() && aepContracts.exists()) {
        include("products:aep")
        project(":products:aep").projectDir = aepRoot
        include("products:aep:aep-operator-contracts")
        project(":products:aep:aep-operator-contracts").projectDir = aepContracts
        if (aepEngine.exists()) {
            include("products:aep:aep-engine")
            project(":products:aep:aep-engine").projectDir = aepEngine
        }
        if (aepCentralRuntime.exists()) {
            include("products:aep:aep-central-runtime")
            project(":products:aep:aep-central-runtime").projectDir = aepCentralRuntime
        }
        if (aepOrchestrator.exists()) {
            include("products:aep:orchestrator")
            project(":products:aep:orchestrator").projectDir = aepOrchestrator
        }
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
        includeBuild(File(standaloneMonorepoRoot, "buildSrc")) {
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
