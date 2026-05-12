/**
 * Ghatana Monorepo Settings
 *
 * Hybrid approach: Explicit module includes with build-logic included build
 */

rootProject.name = "ghatana"

// =============================================================================
// Java Version Validation
// =============================================================================
val javaVersion = System.getProperty("java.version")
val javaMajorVersion = javaVersion?.split(".")?.firstOrNull()?.toIntOrNull() ?: 0
if (javaMajorVersion < 21) {
    throw GradleException("""
        ================ JAVA VERSION ERROR ================
        This project requires Java 21 or higher.
        Current Java version: ${javaVersion ?: "unknown"}
        JAVA_HOME: ${System.getenv("JAVA_HOME") ?: "not set"}
        ====================================================
    """.trimIndent())
}

// =============================================================================
// Plugin Management - Include build-logic for convention plugins
// =============================================================================
pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

// =============================================================================
// Dependency Resolution Management
// =============================================================================
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// =============================================================================
// Build Cache — local + optional remote
// =============================================================================
// Local cache is always active.  Remote cache is activated in CI by setting:
//   GRADLE_CACHE_URL   — HTTP endpoint of the Gradle Build Cache Node
//   GRADLE_CACHE_USER  — cache credentials username
//   GRADLE_CACHE_PASS  — cache credentials password
//
// Self-hosted option (free):
//   docker run -p 5071:5071 gradle/build-cache-node:latest
//
// GitHub Actions option (zero-infra):
//   Use actions/cache with gradle-build-action which manages the cache via the
//   GH Actions API automatically when GRADLE_CACHE_URL is not set.
buildCache {
    local {
        isEnabled = true
        isPush = true
    }
    remote<HttpBuildCache> {
        val cacheUrl = System.getenv("GRADLE_CACHE_URL")
        isEnabled = cacheUrl != null
        if (cacheUrl != null) {
            url = uri(cacheUrl)
            isPush = System.getenv("CI") != null
            isAllowUntrustedServer = false
            credentials {
                username = System.getenv("GRADLE_CACHE_USER") ?: ""
                password = System.getenv("GRADLE_CACHE_PASS") ?: ""
            }
        }
    }
}

// =============================================================================
// Platform Java Modules
// =============================================================================
include(":platform:java:core")
include(":platform:java:database")
include(":platform:java:http")
include(":platform:java:observability")
include(":platform:java:security")
include(":platform:java:testing")
include(":platform:java:workflow")
include(":platform:java:ai-integration")
include(":platform:java:governance")
include(":platform:java:agent-core")
include(":platform:java:domain")
include(":platform:java:config")
include(":platform:java:runtime")
include(":platform:java:audit")
include(":platform:java:cache")
include(":platform:java:policy-as-code")
include(":platform:java:platform-bom")
include(":platform:java:tool-runtime")
include(":platform:java:messaging")
include(":platform:java:data-governance")
include(":platform:java:identity")

// =============================================================================
// Platform Contracts
// =============================================================================
include(":platform:contracts")

// =============================================================================
// Platform Kernel Modules
// =============================================================================
include(":platform-kernel:kernel-core")
include(":platform-kernel:kernel-persistence")
include(":platform-kernel:kernel-plugin")
include(":platform-kernel:kernel-testing")
include(":platform-kernel:kernel-bom")

// =============================================================================
// Platform Plugins
// =============================================================================
include(":platform-plugins:plugin-audit-trail")
include(":platform-plugins:plugin-compliance")
include(":platform-plugins:plugin-consent")
include(":platform-plugins:core-observability")
include(":platform-plugins:plugin-fraud-detection")
include(":platform-plugins:plugin-human-approval")
include(":platform-plugins:plugin-ledger")
include(":platform-plugins:plugin-notification")
include(":platform-plugins:plugin-risk-management")

// =============================================================================
// Products (auto-generated from canonical-product-registry.json)
// =============================================================================
// Run: node scripts/generate-product-registry-artifacts.mjs to regenerate
apply(from = file("config/generated/settings-gradle-includes.kts"))

// =============================================================================
// PHR Domain Modules
// =============================================================================
include(":products:phr:domains:healthcare")

// =============================================================================
// Tutorputor Modules
// =============================================================================
include(":products:tutorputor:libs:content-studio-agents")

// =============================================================================
// Kernel Bridge Modules (not in canonical registry - cross-product adapters)
// =============================================================================
include(":products:data-cloud:planes:action:kernel-bridge")
include(":products:data-cloud:extensions:kernel-bridge")
include(":products:yappc:kernel-bridge")

// =============================================================================
// Shared Services
// =============================================================================
include(":shared-services:auth-gateway")
include(":shared-services:user-profile-service")
include(":shared-services:incident-service")  // Migrated from platform:java:incident-response per audit

// =============================================================================
// Integration Tests
// =============================================================================
include(":integration-tests:cross-service-workflow")
include(":integration-tests:phr-finance-integration")
