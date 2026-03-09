rootProject.name = "audio-video"

// Detect build mode
val isStandaloneBuild = gradle.parent == null
val monorepoRoot = if (isStandaloneBuild) rootDir.parentFile.parentFile else rootDir.parentFile.parentFile

// Store context
extra["isStandaloneBuild"] = isStandaloneBuild
extra["monorepoRoot"] = monorepoRoot

// Helper function to include libs from monorepo
fun includeLib(name: String, subPath: String = "java") {
    val libDir = File(monorepoRoot, "libs/$subPath/$name")
    if (libDir.exists()) {
        include("libs:$name")
        project(":libs:$name").projectDir = libDir
    } else {
        logger.warn("Library not found: libs:$name at $libDir")
    }
}
// Include contracts (needed by domain-models and other libs)
val contractsDir = File(monorepoRoot, "contracts")
if (contractsDir.exists()) {
    include("contracts")
    project(":contracts").projectDir = contractsDir
    
    // Include contract modules
    listOf("proto", "pojos", "mappers", "json-schemas").forEach { name ->
        val contractDir = File(contractsDir, name)
        if (contractDir.exists()) {
            include("contracts:$name")
            project(":contracts:$name").projectDir = contractDir
        }
    }
}
// Include audio-video modules
fileTree("modules") {
    include("**/build.gradle.kts")
    include("**/build.gradle")
}.forEach { buildFile ->
    val projectDir = buildFile.parentFile
    val relativePath = projectDir.relativeTo(rootDir).path
    val projectName = relativePath.replace("/", ":")
    include(projectName)
    project(":$projectName").projectDir = projectDir
}

// Include audio-video libs
fileTree("libs/java") {
    include("**/build.gradle.kts")
    include("**/build.gradle")
}.forEach { buildFile ->
    val projectDir = buildFile.parentFile
    val relativePath = projectDir.relativeTo(rootDir).path
    val projectName = relativePath.replace("/", ":")
    include(projectName)
    project(":$projectName").projectDir = projectDir
}

// Include required shared libs from monorepo
// Note: Including comprehensive list to avoid cascading transitive dependency issues

// Core infrastructure
listOf(
    "activej-runtime",
    "activej-websocket",
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
    "common-utils",
    "types",
    "redis-cache",
    "state",
    "security",
    "governance",
    "config-runtime",
    "domain-models",
    "ai-integration",
    "event-cloud",
    "event-cloud-factory",
    "event-cloud-contract",
    "event-spi",
    "audit"
).forEach { includeLib(it) }

// Include testing libs from libs/java/testing/
listOf(
    "activej-test-utils",
    "test-utils",
    "test-fixtures",
    "test-data",
    "test-containers",
    "native-test-support"
).forEach { name ->
    val testLibDir = File(monorepoRoot, "libs/java/testing/$name")
    if (testLibDir.exists()) {
        include("libs:$name")
        project(":libs:$name").projectDir = testLibDir
    }
}

// Plugin management - use version catalog from root
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.google.protobuf") version "0.9.4"
    }
}

// Dependency resolution
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    
    // Version catalog auto-discovered from gradle/libs.versions.toml (via symlink)
}
