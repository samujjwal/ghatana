/**
 * Platform Kernel Settings
 *
 * Defines all submodules in the platform-kernel composite build.
 * Bridges to parent version catalog for consistent dependency management.
 */
rootProject.name = "platform-kernel"

include("kernel-core")
include("kernel-plugin")
include("kernel-persistence")
include("kernel-testing")
include("kernel-bom")

// =============================================================================
// Version Catalog Bridge
// =============================================================================
// Composite builds have isolated classloaders and cannot access the parent's
// libs.versions.toml by default. This bridge explicitly imports the parent
// catalog so kernel modules can use libs.* references.
// =============================================================================
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
