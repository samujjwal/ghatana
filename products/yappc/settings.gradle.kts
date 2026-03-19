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
include(":backend:api")

// --- Backend Service Modules ---
include(":backend:persistence")
include(":backend:auth")
include(":backend:websocket")
include(":backend:deployment")

// --- Application Launcher ---
include(":launcher")

// --- Unified Service Modules ---
include(":services:platform")   // canonical combined module (was: domain + infrastructure)
include(":services:domain")     // backward-compat stub — delegates to :services:platform
include(":services:infrastructure") // backward-compat stub — delegates to :services:platform
include(":services:ai")
include(":services:lifecycle")
include(":services:scaffold")

// --- Core Domain Modules ---
include(":core:domain")
include(":core:domain:service")
include(":core:domain:task")

// --- Core: Scaffold Engine ---
include(":core:scaffold")
include(":core:scaffold:api")
include(":core:scaffold:api:http")
include(":core:scaffold:api:grpc")
include(":core:scaffold:core")
include(":core:scaffold:packs")
include(":core:scaffold:adapters")
include(":core:scaffold:cli")
include(":core:scaffold:schemas")

// --- Core: AI & Agents ---
include(":core:ai")
include(":core:agents")
include(":core:agents:runtime")
include(":core:agents:workflow")
include(":core:agents:specialists")

// --- Core: Refactorer ---
include(":core:refactorer:api")
include(":core:refactorer:engine")

// --- Core: Supplementary ---
include(":core:cli-tools")
include(":core:knowledge-graph")
include(":core:lifecycle")
include(":core:framework")
include(":core:framework:integration-test")
include(":core:spi")

// --- Infrastructure ---
include(":infrastructure:datacloud")
include(":infrastructure:security")

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
            "backend:api",
            "backend:persistence",
            "backend:auth",
            "backend:websocket",
            "backend:deployment",
            "launcher",
            "services:platform",       // canonical combined module
            "services:domain",         // backward-compat stub
            "services:infrastructure", // backward-compat stub
            "services:ai",
            "services:lifecycle",
            "services:scaffold",
            "core:domain",
            "core:domain:service",
            "core:domain:task",
            "core:scaffold",
            "core:scaffold:api",
            "core:scaffold:api:http",
            "core:scaffold:api:grpc",
            "core:scaffold:core",
            "core:scaffold:packs",
            "core:scaffold:adapters",
            "core:scaffold:cli",
            "core:scaffold:schemas",
            "core:ai",
            "core:agents",
            "core:agents:runtime",
            "core:agents:workflow",
            "core:agents:specialists",
            "core:refactorer:api",
            "core:refactorer:engine",
            "core:cli-tools",
            "core:knowledge-graph",
            "core:lifecycle",
            "core:framework",
            "core:framework:integration-test",
            "core:spi",
            "infrastructure:datacloud",
            "infrastructure:security",
            "libs:java:yappc-domain")

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
    val aepPlatform = File(aepRoot, "platform")
    if (aepRoot.exists() && aepPlatform.exists()) {
        include("products:aep")
        project(":products:aep").projectDir = aepRoot
        include("products:aep:platform")
        project(":products:aep:platform").projectDir = aepPlatform
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
