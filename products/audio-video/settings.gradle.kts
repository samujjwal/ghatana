rootProject.name = "audio-video"

// Detect build mode
val isStandaloneBuild = gradle.parent == null
val monorepoRoot = if (isStandaloneBuild) rootDir.parentFile.parentFile else rootDir.parentFile.parentFile
val productProjectPrefix = "products:audio-video"

// Store context
extra["isStandaloneBuild"] = isStandaloneBuild
extra["monorepoRoot"] = monorepoRoot

fun includeProject(projectPath: String, projectDir: File) {
    include(projectPath)
    project(":$projectPath").projectDir = projectDir
}

include("products")
project(":products").projectDir = File(monorepoRoot, "products")
include(productProjectPrefix)
project(":$productProjectPrefix").projectDir = File(monorepoRoot, "products")
include("$productProjectPrefix:modules")
project(":$productProjectPrefix:modules").projectDir = File(rootDir, "modules")
include("$productProjectPrefix:modules:speech")
project(":$productProjectPrefix:modules:speech").projectDir = File(rootDir, "modules/speech")
include("$productProjectPrefix:modules:vision")
project(":$productProjectPrefix:modules:vision").projectDir = File(rootDir, "modules/vision")
include("$productProjectPrefix:modules:intelligence")
project(":$productProjectPrefix:modules:intelligence").projectDir = File(rootDir, "modules/intelligence")
include("$productProjectPrefix:libs")
project(":$productProjectPrefix:libs").projectDir = File(rootDir, "libs")

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

val platformContractsDir = File(monorepoRoot, "platform/contracts")
if (platformContractsDir.exists()) {
    include("platform")
    project(":platform").projectDir = File(monorepoRoot, "platform")
    include("platform:java")
    project(":platform:java").projectDir = File(monorepoRoot, "platform/java")
    includeProject("platform:contracts", platformContractsDir)
}

val sharedServicesDir = File(monorepoRoot, "shared-services")
if (sharedServicesDir.exists()) {
    include("shared-services")
    project(":shared-services").projectDir = sharedServicesDir

    val authGatewayDir = File(sharedServicesDir, "auth-gateway")
    if (authGatewayDir.exists()) {
        includeProject("shared-services:auth-gateway", authGatewayDir)
    }
}

// Include top-level platform/java modules used directly or transitively by audio-video.
fileTree(File(monorepoRoot, "platform/java")) {
    include("*/build.gradle.kts")
    include("*/build.gradle")
}.forEach { buildFile ->
    val projectDir = buildFile.parentFile
    val relativePath = projectDir.relativeTo(File(monorepoRoot, "platform/java")).path
    val projectName = "platform:java:${relativePath.replace("/", ":")}"
    includeProject(projectName, projectDir)
}

// Include audio-video modules
fileTree("modules") {
    include("**/build.gradle.kts")
    include("**/build.gradle")
}.forEach { buildFile ->
    val projectDir = buildFile.parentFile
    val relativePath = projectDir.relativeTo(rootDir).path
    val projectName = "$productProjectPrefix:${relativePath.replace("/", ":")}"
    includeProject(projectName, projectDir)
}

// Include audio-video libs (including nested java/ subdirectory)
fileTree("libs") {
    include("**/build.gradle.kts")
    include("**/build.gradle")
}.forEach { buildFile ->
    val projectDir = buildFile.parentFile
    val relativePath = projectDir.relativeTo(rootDir).path
    // Handle both libs/xxx and libs/java/xxx patterns
    val projectName = "$productProjectPrefix:${relativePath.replace("/", ":")}"
    includeProject(projectName, projectDir)
}

// Plugin management - use version catalog from root
pluginManagement {
    includeBuild(File(rootDir.parentFile.parentFile, "build-logic"))
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.google.protobuf").version("0.9.4")
    }
}

// Dependency resolution
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    
    // Version catalog auto-discovered from gradle/libs.versions.toml (via symlink)
}
