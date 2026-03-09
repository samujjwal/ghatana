/**
 * Flashit Product Settings
 * 
 * Enables standalone build: cd products/flashit && ../../gradlew build
 */

// ============================================================================
// Plugin Management & Dependency Resolution (must be FIRST)
// ============================================================================
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    // Version catalog auto-discovered from gradle/libs.versions.toml
}

rootProject.name = "flashit"

// ============================================================================
// Build Context Detection
// ============================================================================
val isStandaloneBuild = gradle.parent == null
val monorepoRoot = if (isStandaloneBuild) rootDir.parentFile.parentFile else rootDir.parentFile.parentFile
val productDir = rootDir

extra["isStandaloneBuild"] = isStandaloneBuild
extra["monorepoRoot"] = monorepoRoot
extra["productName"] = "flashit"

logger.lifecycle("┌─────────────────────────────────────────────────────────────")
logger.lifecycle("│ Product: Flashit")
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

fun includeNestedLib(parent: String, children: List<String>, subPath: String = "java") {
    val parentDir = File(monorepoRoot, "libs/$subPath/$parent")
    if (parentDir.exists()) {
        include("libs:$parent")
        project(":libs:$parent").projectDir = parentDir
        
        children.forEach { child ->
            val childDir = File(parentDir, child)
            if (childDir.exists()) {
                include("libs:$parent:$child")
                project(":libs:$parent:$child").projectDir = childDir
            }
        }
    }
}

// ============================================================================
// Include Flashit Modules
// ============================================================================
val agentDir = File(productDir, "backend/agent")
if (agentDir.exists()) {
    include(":agent")
    project(":agent").projectDir = agentDir
}

// ============================================================================
// Include Platform Libraries (from platform/)
// ============================================================================
val platformDir = File(monorepoRoot, "platform")
val platformJavaDir = File(platformDir, "java")

// Register intermediate container projects
include(":platform")
project(":platform").projectDir = platformDir
include(":platform:java")
project(":platform:java").projectDir = platformJavaDir

// Platform contracts (leaf)
val contractsPlatformDir = File(platformDir, "contracts")
if (contractsPlatformDir.exists()) {
    include(":platform:contracts")
    project(":platform:contracts").projectDir = contractsPlatformDir
}

// Platform Java modules required by the agent (governance + its transitive deps)
listOf(
    "core",
    "runtime",
    "config",
    "observability",
    "http",
    "testing",
    "governance"
).forEach { name ->
    val dir = File(platformJavaDir, name)
    if (dir.exists()) {
        include(":platform:java:$name")
        project(":platform:java:$name").projectDir = dir
    }
}

// ============================================================================
// Include Shared Libraries
// ============================================================================
logger.lifecycle("Including shared libraries...")

val libsDir = File(monorepoRoot, "libs/java")
if (libsDir.exists()) {
    include("libs")
    project(":libs").projectDir = File(monorepoRoot, "libs")
}

// Core infrastructure
listOf(
    "activej-runtime",
    "http-server",
    "http-client",
    "observability",
    "observability-http",
    "observability-clickhouse",
    "database",
    "validation",
    "validation-api",
    "validation-common",
    "validation-spi",
    "types",
    "common-utils",
    "domain-models",
    "redis-cache",
    "security",
    "governance",
    "state",
    "config-runtime",
    "event-cloud",
    "event-cloud-factory",
    "event-cloud-contract",
    "event-spi",
    "audit"
).forEach { includeLib(it) }

// Nested libraries
includeNestedLib("auth-platform", listOf("core", "jwt", "oauth"))

// Testing libraries
listOf(
    "activej-test-utils",
    "test-utils",
    "test-fixtures",
    "test-data",
    "test-containers"
).forEach { name ->
    val testLibDir = File(monorepoRoot, "libs/java/testing/$name")
    if (testLibDir.exists()) {
        include("libs:$name")
        project(":libs:$name").projectDir = testLibDir
    }
}

// ============================================================================
// Include Contracts
// ============================================================================
logger.lifecycle("Including contracts...")

val contractsDir = File(monorepoRoot, "contracts")
if (contractsDir.exists()) {
    include("contracts")
    project(":contracts").projectDir = contractsDir
    
    listOf("proto", "pojos", "mappers", "json-schemas").forEach { name ->
        val contractDir = File(contractsDir, name)
        if (contractDir.exists()) {
            include("contracts:$name")
            project(":contracts:$name").projectDir = contractDir
        }
    }
}

logger.lifecycle("Flashit build configuration complete!")
