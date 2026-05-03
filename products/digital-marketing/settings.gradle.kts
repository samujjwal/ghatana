/**
 * Digital Marketing Operating System (DMOS) — Product Settings
 *
 * Enables standalone build: cd products/digital-marketing && ../../gradlew build
 * Also works within monorepo: ./gradlew :products:digital-marketing:build
 *
 * In standalone mode this settings file:
 *   1. Registers DMOS modules under short paths (:dm-*)
 *   2. Wires platform/kernel/plugin dependencies from the monorepo root
 *   3. Creates alias entries (:products:digital-marketing:dm-*) so that
 *      build.gradle.kts project() references using full monorepo paths resolve
 *
 * The alias pattern mirrors products/yappc/settings.gradle.kts.
 */

// ============================================================================
// Plugin Management & Dependency Resolution (must be FIRST)
// ============================================================================
pluginManagement {
	if (gradle.parent == null) {
		// In standalone mode wire the shared build-logic conventions so that
		// id("java-module"), id("java-application") etc. resolve correctly.
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
		// Point at the shared version catalog so libs.bundles.* references resolve.
		versionCatalogs {
			create("libs") {
				from(files(File(rootDir.parentFile.parentFile, "gradle/libs.versions.toml")))
			}
		}
	}
}

rootProject.name = "digital-marketing"

// ============================================================================
// Build Context Detection
// ============================================================================
// products/digital-marketing is exactly 2 directory levels below the monorepo root
val isStandaloneBuild = gradle.parent == null
val monorepoRoot = rootDir.parentFile.parentFile
val productDir = rootDir

extra["isStandaloneBuild"] = isStandaloneBuild
extra["monorepoRoot"] = monorepoRoot
extra["productName"] = "digital-marketing"

logger.lifecycle("┌─────────────────────────────────────────────────────────────")
logger.lifecycle("│ Product: Digital Marketing Operating System (DMOS)")
logger.lifecycle("│ Mode: ${if (isStandaloneBuild) "STANDALONE" else "MONOREPO"}")
logger.lifecycle("│ Product Dir: $productDir")
logger.lifecycle("│ Monorepo Root: $monorepoRoot")
logger.lifecycle("└─────────────────────────────────────────────────────────────")

// ============================================================================
// DMOS Product Modules (always registered under short :dm-* paths)
// ============================================================================
val dmosModules = listOf(
	"dm-core-contracts",
	"dm-domain-packs",
	"dm-kernel-bridge",
	"dm-domain",
	"dm-application",
	"dm-infra",
	"dm-persistence",
	"dm-connector-google-ads",
	"dm-api",
	"dm-integration-tests"
)

dmosModules.forEach { name ->
	val dir = File(productDir, name)
	if (dir.exists()) {
		include(":$name")
		project(":$name").projectDir = dir
	}
}

// ============================================================================
// Standalone-only: Platform / Kernel / Plugin wiring + monorepo-path aliases
// ============================================================================
if (isStandaloneBuild) {
	// ── Platform containers ────────────────────────────────────────────────
	include(":platform")
	project(":platform").projectDir = File(monorepoRoot, "platform")

	include(":platform:java")
	project(":platform:java").projectDir = File(monorepoRoot, "platform/java")

	val platformContractsDir = File(monorepoRoot, "platform/contracts")
	if (platformContractsDir.exists()) {
		include(":platform:contracts")
		project(":platform:contracts").projectDir = platformContractsDir
	}

	include(":platform-kernel")
	project(":platform-kernel").projectDir = File(monorepoRoot, "platform-kernel")

	include(":platform-plugins")
	project(":platform-plugins").projectDir = File(monorepoRoot, "platform-plugins")

	// ── Platform Java modules used by DMOS ─────────────────────────────────
	// ── All platform/java modules (include everything to satisfy transitive deps) ─
	File(monorepoRoot, "platform/java")
		.listFiles()
		.orEmpty()
		.filter { it.isDirectory }
		.sortedBy { it.name }
		.forEach { dir ->
			include(":platform:java:${dir.name}")
			project(":platform:java:${dir.name}").projectDir = dir
		}

	// ── Kernel modules ─────────────────────────────────────────────────────
	// ── All kernel modules (scan to satisfy transitive kernel-bom references) ──
	File(monorepoRoot, "platform-kernel")
		.listFiles()
		.orEmpty()
		.filter { it.isDirectory && File(it, "build.gradle.kts").exists() }
		.sortedBy { it.name }
		.forEach { dir ->
			include(":platform-kernel:${dir.name}")
			project(":platform-kernel:${dir.name}").projectDir = dir
		}

	// ── Plugin modules used by DMOS ────────────────────────────────────────
	// ── All platform-plugin modules (scan to satisfy transitive references) ──
		// ── Shared services (needed by platform/java transitive test deps) ────────
		val sharedServicesDir = File(monorepoRoot, "shared-services")
		if (sharedServicesDir.exists()) {
			include(":shared-services")
			project(":shared-services").projectDir = sharedServicesDir
			sharedServicesDir
				.listFiles()
				.orEmpty()
				.filter { it.isDirectory && File(it, "build.gradle.kts").exists() }
				.sortedBy { it.name }
				.forEach { dir ->
					include(":shared-services:${dir.name}")
					project(":shared-services:${dir.name}").projectDir = dir
				}
		}

		// ── All platform-plugin modules (scan to satisfy transitive references) ──
	File(monorepoRoot, "platform-plugins")
		.listFiles()
		.orEmpty()
		.filter { it.isDirectory && File(it, "build.gradle.kts").exists() }
		.sortedBy { it.name }
		.forEach { dir ->
			include(":platform-plugins:${dir.name}")
			project(":platform-plugins:${dir.name}").projectDir = dir
		}

	// ── Alias container ────────────────────────────────────────────────────
	// build.gradle.kts files use project(":products:digital-marketing:dm-*")
	// These aliases map those full monorepo paths to the same project dirs as
	// the short :dm-* entries above, so both forms resolve correctly.
	include(":products")
	project(":products").projectDir = File(monorepoRoot, "products")

	// The alias container needs a directory distinct from productDir (which is
	// already claimed by the root project ":"). An empty generated dir is used,
	// mirroring the products/yappc/settings.gradle.kts approach.
	val aliasRoot = File(rootDir, "build/gradle-alias/products-digital-marketing")
		.apply { mkdirs() }
	include(":products:digital-marketing")
	project(":products:digital-marketing").projectDir = aliasRoot

	dmosModules.forEach { name ->
		if (File(productDir, name).exists()) {
			include(":products:digital-marketing:$name")
			project(":products:digital-marketing:$name").projectDir =
				project(":$name").projectDir
		}
	}
}
