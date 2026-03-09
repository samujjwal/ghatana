/**
 * Tutorputor Product Settings
 * 
 * Enables standalone build: cd products/tutorputor && ../../gradlew build
 */
rootProject.name = "tutorputor"

// ============================================================================
// Build Context Detection
// ============================================================================
val isStandaloneBuild = gradle.parent == null
val monorepoRoot = if (isStandaloneBuild) rootDir.parentFile.parentFile else rootDir.parentFile.parentFile
val productDir = rootDir

extra["isStandaloneBuild"] = isStandaloneBuild
extra["monorepoRoot"] = monorepoRoot
extra["productName"] = "tutorputor"

logger.lifecycle("┌─────────────────────────────────────────────────────────────")
logger.lifecycle("│ Product: Tutorputor")
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
    "ai-integration",
    "event-cloud",
    "event-cloud-factory",
    "event-cloud-contract",
    "event-spi",
    "audit"
).forEach { includeLib(it) }

// Nested libraries
includeNestedLib("auth-platform", listOf("core", "jwt", "oauth"))
includeNestedLib("ai-platform", listOf("registry", "feature-store", "observability"))

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

// ============================================================================
// Plugin Management & Dependency Resolution
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
    // Version catalog auto-discovered from gradle/libs.versions.toml (via symlink)
}

logger.lifecycle("Tutorputor build configuration complete!")
