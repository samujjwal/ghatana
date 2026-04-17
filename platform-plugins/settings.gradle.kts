/**
 * Platform Plugins Settings
 *
 * Defines all submodules in the platform-plugins composite build.
 * Bridges to parent version catalog for consistent dependency management.
 */
rootProject.name = "platform-plugins"

includeBuild("../platform-kernel")

include("plugin-billing-ledger")
include("plugin-fraud-detection")
include("plugin-compliance")
include("plugin-consent")
include("plugin-risk-management")
include("plugin-audit-trail")
include("plugin-human-approval")

// =============================================================================
// Version Catalog Bridge
// =============================================================================
// Composite builds have isolated classloaders and cannot access the parent's
// libs.versions.toml by default. This bridge explicitly imports the parent
// catalog so plugin modules can use libs.* references.
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
