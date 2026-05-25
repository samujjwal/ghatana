/**
 * Virtual-Org Product Settings
 *
 * Enables standalone build: cd products/virtual-org && ../../gradlew build
 */
rootProject.name = "virtual-org"

// ============================================================================
// Build Context Detection
// ============================================================================
val isStandaloneBuild = gradle.parent == null
val monorepoRoot = if (isStandaloneBuild) rootDir.parentFile.parentFile else rootDir.parentFile.parentFile
val productDir = rootDir

extra["isStandaloneBuild"] = isStandaloneBuild
extra["monorepoRoot"] = monorepoRoot
extra["productName"] = "virtual-org"

logger.lifecycle("┌─────────────────────────────────────────────────────────────")
logger.lifecycle("│ Product: Virtual-Org")
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

fun includeMonorepoProject(path: String, relativePath: String) {
    val projectDir = File(monorepoRoot, relativePath)
    if (projectDir.exists()) {
        if (findProject(path) == null) {
            include(path)
        }
        project(path).projectDir = projectDir
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
    "event-runtime",
    "event-spi",
    "audit",
    "agent-core",
    "workflow-api",
    "operator",
    "operator-catalog"
).forEach { includeLib(it) }

// Nested libraries
includeNestedLib("auth-platform", listOf("core", "jwt", "oauth"))
includeNestedLib("ai-platform", listOf("registry", "feature-store", "observability", "serving", "gateway"))

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
// Include Platform and Product Projects
// ============================================================================
logger.lifecycle("Including platform and product projects...")

listOf(
    ":platform" to "platform",
    ":platform:java" to "platform/java",
    ":platform-kernel" to "platform-kernel",
    ":products" to "products",
    ":products:data-cloud" to "products/data-cloud",
    ":products:data-cloud:delivery" to "products/data-cloud/delivery",
    ":products:data-cloud:extensions" to "products/data-cloud/extensions",
    ":products:data-cloud:planes" to "products/data-cloud/planes",
    ":products:data-cloud:planes:action" to "products/data-cloud/planes/action",
    ":platform:contracts" to "platform/contracts",
    ":platform:java:core" to "platform/java/core",
    ":platform:java:database" to "platform/java/database",
    ":platform:java:http" to "platform/java/http",
    ":platform:java:observability" to "platform/java/observability",
    ":platform:java:security" to "platform/java/security",
    ":platform:java:testing" to "platform/java/testing",
    ":platform:java:workflow" to "platform/java/workflow",
    ":platform:java:ai-integration" to "platform/java/ai-integration",
    ":platform:java:domain" to "platform/java/domain",
    ":platform:java:config" to "platform/java/config",
    ":platform:java:runtime" to "platform/java/runtime",
    ":platform:java:audit" to "platform/java/audit",
    ":platform:java:governance" to "platform/java/governance",
    ":platform:java:policy-as-code" to "platform/java/policy-as-code",
    ":platform:java:agent-core" to "platform/java/agent-core",
    ":platform-kernel:kernel-core" to "platform-kernel/kernel-core",
    ":platform-kernel:kernel-plugin" to "platform-kernel/kernel-plugin",
    ":products:data-cloud:planes:action:agent-runtime" to "products/data-cloud/planes/action/agent-runtime",
    ":products:data-cloud:planes:action:operator-contracts" to "products/data-cloud/planes/action/operator-contracts"
).forEach { (path, relativePath) -> includeMonorepoProject(path, relativePath) }

File(monorepoRoot, "platform/java")
    .listFiles()
    .orEmpty()
    .filter { it.isDirectory && File(it, "build.gradle.kts").exists() }
    .sortedBy { it.name }
    .forEach { dir ->
        includeMonorepoProject(":platform:java:${dir.name}", dir.relativeTo(monorepoRoot).path)
    }

File(monorepoRoot, "platform-kernel")
    .listFiles()
    .orEmpty()
    .filter { it.isDirectory && File(it, "build.gradle.kts").exists() }
    .sortedBy { it.name }
    .forEach { dir ->
        includeMonorepoProject(":platform-kernel:${dir.name}", dir.relativeTo(monorepoRoot).path)
    }

File(monorepoRoot, "products/data-cloud/planes/action")
    .listFiles()
    .orEmpty()
    .filter { it.isDirectory && File(it, "build.gradle.kts").exists() }
    .sortedBy { it.name }
    .forEach { dir ->
        includeMonorepoProject(":products:data-cloud:planes:action:${dir.name}", dir.relativeTo(monorepoRoot).path)
    }

File(monorepoRoot, "products/data-cloud/planes")
    .walkTopDown()
    .filter { it.isDirectory && File(it, "build.gradle.kts").exists() }
    .sortedBy { it.name }
    .forEach { dir ->
        includeMonorepoProject(
            ":${dir.relativeTo(monorepoRoot).path.replace(File.separatorChar, ':')}",
            dir.relativeTo(monorepoRoot).path
        )
    }

File(monorepoRoot, "products/data-cloud/extensions")
    .listFiles()
    .orEmpty()
    .filter { it.isDirectory && File(it, "build.gradle.kts").exists() }
    .sortedBy { it.name }
    .forEach { dir ->
        includeMonorepoProject(":products:data-cloud:extensions:${dir.name}", dir.relativeTo(monorepoRoot).path)
    }

File(monorepoRoot, "products/data-cloud/delivery")
    .listFiles()
    .orEmpty()
    .filter { it.isDirectory && File(it, "build.gradle.kts").exists() }
    .sortedBy { it.name }
    .forEach { dir ->
        includeMonorepoProject(":products:data-cloud:delivery:${dir.name}", dir.relativeTo(monorepoRoot).path)
    }

val sharedServicesDir = File(monorepoRoot, "shared-services")
if (sharedServicesDir.exists()) {
    includeMonorepoProject(":shared-services", "shared-services")
    includeMonorepoProject(":shared-services:auth-gateway", "shared-services/auth-gateway")
    includeMonorepoProject(":shared-services:incident-service", "shared-services/incident-service")
}

listOf(
    ":modules:agent" to "modules/agent",
    ":modules:framework" to "modules/framework",
    ":modules:workflow" to "modules/workflow",
    ":modules:operator-adapter" to "modules/operator-adapter",
    ":modules:integration" to "modules/integration",
    ":engine:service" to "engine/service",
    ":launcher" to "launcher",
    ":contracts:proto" to "contracts/proto"
).forEach { (path, relativePath) ->
    val projectDir = File(productDir, relativePath)
    if (projectDir.exists()) {
        include(path)
        project(path).projectDir = projectDir
    }
}

val virtualOrgAliasRoot = File(productDir, "build/gradle-alias/products-virtual-org")
    .apply { mkdirs() }
include(":products:virtual-org")
project(":products:virtual-org").projectDir = virtualOrgAliasRoot
include(":products:virtual-org:modules")
project(":products:virtual-org:modules").projectDir = File(virtualOrgAliasRoot, "modules").apply { mkdirs() }
include(":products:virtual-org:engine")
project(":products:virtual-org:engine").projectDir = File(virtualOrgAliasRoot, "engine").apply { mkdirs() }

listOf(
    ":products:virtual-org:modules:agent" to ":modules:agent",
    ":products:virtual-org:modules:framework" to ":modules:framework",
    ":products:virtual-org:modules:workflow" to ":modules:workflow",
    ":products:virtual-org:modules:operator-adapter" to ":modules:operator-adapter",
    ":products:virtual-org:modules:integration" to ":modules:integration",
    ":products:virtual-org:engine:service" to ":engine:service",
    ":products:virtual-org:launcher" to ":launcher"
).forEach { (aliasPath, targetPath) ->
    val targetProject = project(targetPath)
    include(aliasPath)
    project(aliasPath).projectDir = targetProject.projectDir
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
    // The product-local gradle symlink exposes the shared version catalog.
}

logger.lifecycle("Virtual-Org build configuration complete!")
