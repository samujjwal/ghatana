/**
 * Unified Product Build Configuration
 * 
 * This script provides a reusable pattern for products to:
 * 1. Build independently (cd products/foo && ../../gradlew build)
 * 2. Build from monorepo root (./gradlew :products:foo:build)
 * 3. Share common libs and contracts consistently
 * 
 * Usage in product settings.gradle.kts:
 *   rootProject.name = "my-product"
 *   apply(from = "../../gradle/product-build.gradle.kts")
 *   
 *   // Optional: customize what libs/contracts to include
 *   extra["includeLibs"] = listOf("observability", "http-server", "database")
 *   extra["includeContracts"] = listOf("proto", "pojos")
 *   
 *   // Auto-configure
 *   configureProductBuild()
 */

// Detect build context
val isStandaloneBuild = !gradle.parent?.rootProject?.name.equals("ghatana")
val rootDir = if (isStandaloneBuild) settings.rootDir.parentFile.parentFile else settings.rootDir
val productDir = settings.rootDir
val productName = settings.rootProject.name

// Store context for build scripts
settings.extra["isStandaloneBuild"] = isStandaloneBuild
settings.extra["monorepoRoot"] = rootDir
settings.extra["productDir"] = productDir
settings.extra["productName"] = productName

logger.lifecycle("┌─────────────────────────────────────────────────────────────")
logger.lifecycle("│ Product Build: $productName")
logger.lifecycle("│ Mode: ${if (isStandaloneBuild) "STANDALONE" else "MONOREPO"}")
logger.lifecycle("│ Product Dir: $productDir")
logger.lifecycle("│ Monorepo Root: $rootDir")
logger.lifecycle("└─────────────────────────────────────────────────────────────")

// ============================================================================
// Plugin Management (must be first)
// ============================================================================
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

// ============================================================================
// Dependency Resolution (version catalog)
// ============================================================================
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("${rootDir}/gradle/libs.versions.toml"))
        }
    }
}

// ============================================================================
// Helper Functions
// ============================================================================

/**
 * Discovers and includes all submodules within a directory
 */
fun Settings.discoverModules(
    baseDir: File,
    prefix: String = "",
    excludePatterns: List<String> = listOf("/build/", "/.", "/_", "/node_modules/", "/target/")
): List<String> {
    val included = mutableListOf<String>()
    
    baseDir.walkTopDown()
        .filter { dir ->
            dir.isDirectory &&
            dir != baseDir &&
            (dir.resolve("build.gradle.kts").exists() || dir.resolve("build.gradle").exists()) &&
            excludePatterns.none { pattern -> dir.path.contains(pattern) } &&
            dir.name != "buildSrc"
        }
        .forEach { dir ->
            val relativePath = baseDir.toPath().relativize(dir.toPath()).toString()
            val projectPath = if (prefix.isEmpty()) {
                relativePath.replace(File.separatorChar, ':')
            } else {
                "$prefix:${relativePath.replace(File.separatorChar, ':')}"
            }
            
            include(projectPath)
            project(":$projectPath").projectDir = dir
            included.add(projectPath)
        }
    
    return included
}

/**
 * Includes a shared library from libs/java/
 */
fun Settings.includeLib(name: String, subPath: String = "java") {
    val libDir = File(rootDir, "libs/$subPath/$name")
    if (libDir.exists()) {
        include("libs:$name")
        project(":libs:$name").projectDir = libDir
        logger.lifecycle("  ✓ libs:$name")
    } else {
        logger.warn("  ✗ libs:$name (not found at $libDir)")
    }
}

/**
 * Includes nested library modules (e.g., auth-platform/core)
 */
fun Settings.includeNestedLib(parent: String, children: List<String>, subPath: String = "java") {
    val parentDir = File(rootDir, "libs/$subPath/$parent")
    if (parentDir.exists()) {
        include("libs:$parent")
        project(":libs:$parent").projectDir = parentDir
        logger.lifecycle("  ✓ libs:$parent")
        
        children.forEach { child ->
            val childDir = File(parentDir, child)
            if (childDir.exists() && (childDir.resolve("build.gradle.kts").exists() || childDir.resolve("build.gradle").exists())) {
                include("libs:$parent:$child")
                project(":libs:$parent:$child").projectDir = childDir
                logger.lifecycle("    ✓ libs:$parent:$child")
            }
        }
    }
}

/**
 * Includes a contract module
 */
fun Settings.includeContract(name: String) {
    val contractDir = File(rootDir, "contracts/$name")
    if (contractDir.exists()) {
        include("contracts:$name")
        project(":contracts:$name").projectDir = contractDir
        logger.lifecycle("  ✓ contracts:$name")
    } else {
        logger.warn("  ✗ contracts:$name (not found at $contractDir)")
    }
}

/**
 * Main configuration function - call this from product settings
 */
fun Settings.configureProductBuild(
    libs: List<String> = defaultLibs(),
    nestedLibs: Map<String, List<String>> = defaultNestedLibs(),
    contracts: List<String> = defaultContracts(),
    testingLibs: List<String> = defaultTestingLibs(),
    excludeModulePatterns: List<String> = emptyList()
) {
    logger.lifecycle("Configuring product modules...")
    
    // 1. Discover product's own modules
    val productModules = discoverModules(
        productDir,
        excludePatterns = listOf("/build/", "/.", "/_", "/node_modules/", "/target/") + excludeModulePatterns
    )
    logger.lifecycle("  Found ${productModules.size} product modules")
    
    // 2. Include shared libraries
    logger.lifecycle("Including shared libraries...")
    libs.forEach { includeLib(it) }
    
    // 3. Include nested libraries
    nestedLibs.forEach { (parent, children) ->
        includeNestedLib(parent, children)
    }
    
    // 4. Include testing libraries
    logger.lifecycle("Including testing libraries...")
    testingLibs.forEach { name ->
        val testLibDir = File(rootDir, "libs/java/testing/$name")
        if (testLibDir.exists()) {
            include("libs:$name")
            project(":libs:$name").projectDir = testLibDir
            logger.lifecycle("  ✓ libs:$name (testing)")
        }
    }
    
    // 5. Include contracts
    logger.lifecycle("Including contracts...")
    // Parent contracts module
    val contractsDir = File(rootDir, "contracts")
    if (contractsDir.exists()) {
        include("contracts")
        project(":contracts").projectDir = contractsDir
    }
    contracts.forEach { includeContract(it) }
}

// ============================================================================
// Default Configurations (can be overridden per product)
// ============================================================================

fun defaultLibs(): List<String> = listOf(
    "activej-runtime",
    "http-server",
    "http-client",
    "observability",
    "observability-http",
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
    "ai-integration"
)

fun defaultNestedLibs(): Map<String, List<String>> = mapOf(
    "auth-platform" to listOf("core", "jwt", "oauth"),
    "ai-platform" to listOf("registry", "feature-store", "observability", "serving", "gateway")
)

fun defaultTestingLibs(): List<String> = listOf(
    "test-utils",
    "activej-test-utils",
    "test-containers",
    "test-fixtures",
    "test-data"
)

fun defaultContracts(): List<String> = listOf(
    "proto",
    "pojos",
    "mappers"
)

// Export functions to settings scope
settings.extra["discoverModules"] = ::discoverModules
settings.extra["includeLib"] = ::includeLib
settings.extra["includeNestedLib"] = ::includeNestedLib
settings.extra["includeContract"] = ::includeContract
settings.extra["configureProductBuild"] = ::configureProductBuild
