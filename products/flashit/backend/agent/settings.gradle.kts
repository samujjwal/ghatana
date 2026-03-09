/**
 * FlashIt Agent — Standalone Settings
 *
 * Supports two modes:
 *   1. Standalone: cd backend/agent && ../../gradlew build
 *   2. Composite: included from products/flashit/settings.gradle.kts
 */
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "flashit-agent"

// ============================================================================
// Resolve monorepo root relative to this file
// products/flashit/backend/agent → 4 levels up
// ============================================================================
val monorepoRoot = rootDir.parentFile.parentFile.parentFile.parentFile

// ============================================================================
// Version Catalog — point at the shared catalog
// ============================================================================
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files(File(monorepoRoot, "gradle/libs.versions.toml")))
        }
    }
}
