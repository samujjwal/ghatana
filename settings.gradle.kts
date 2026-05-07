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
// Platform Kernel
// =============================================================================
include(":platform-kernel:kernel-core")
include(":platform-kernel:kernel-plugin")
include(":platform-kernel:kernel-persistence")
include(":platform-kernel:kernel-testing")
include(":platform-kernel:kernel-bom")

// =============================================================================
// Platform Plugins
// =============================================================================
include(":platform-plugins:plugin-audit-trail")
include(":platform-plugins:plugin-human-approval")
include(":platform-plugins:plugin-ledger")
include(":platform-plugins:plugin-compliance")
include(":platform-plugins:plugin-consent")
include(":platform-plugins:plugin-fraud-detection")
include(":platform-plugins:plugin-risk-management")
include(":platform-plugins:plugin-notification")
include(":platform-plugins:core-observability")

// =============================================================================
// Platform Java
// =============================================================================
include(":platform:contracts")
include(":platform:java:core")
include(":platform:java:domain")
include(":platform:java:database")
include(":platform:java:http")
include(":platform:java:observability")
include(":platform:java:platform-bom")
include(":platform:java:testing")
include(":platform:java:runtime")
include(":platform:java:config")
include(":platform:java:workflow")
include(":platform:java:ai-integration")
include(":platform:java:governance")
include(":platform:java:security")
include(":platform:java:agent-core")
include(":platform:java:messaging")   // Unified messaging module
include(":platform:java:audit")
include(":platform:java:integration-tests")  // Cross-module integration tests
// Audio-Video commons libraries
include(":products:audio-video:libs:java:common")
include(":products:audio-video:libs:common")
include(":platform:java:identity")
include(":platform:java:data-governance")
include(":platform:java:tool-runtime")
include(":platform:java:policy-as-code")
include(":platform:java:ds-cli")
include(":platform:java:cache")              // Distributed caching infrastructure
// platform:java:incident-response DEPRECATED: Migrated to shared-services:incident-service per Phase 3.2

// =============================================================================
// Product: Digital Marketing Operating System (DMOS)
// =============================================================================
include(":products:digital-marketing:dm-core-contracts")
include(":products:digital-marketing:dm-domain-packs")
include(":products:digital-marketing:dm-kernel-bridge")
include(":products:digital-marketing:dm-domain")
include(":products:digital-marketing:dm-application")
include(":products:digital-marketing:dm-infra")
include(":products:digital-marketing:dm-persistence")
include(":products:digital-marketing:dm-connector-google-ads")
include(":products:digital-marketing:dm-api")
include(":products:digital-marketing:dm-integration-tests")

// =============================================================================
// Product: Data-Cloud
// =============================================================================
include(":products:data-cloud:planes:shared-spi")
include(":products:data-cloud:planes:data:entity")
include(":products:data-cloud:planes:event:core")
include(":products:data-cloud:planes:operations:config")
include(":products:data-cloud:planes:intelligence:analytics")
include(":products:data-cloud:planes:governance:core")
include(":products:data-cloud:delivery:runtime-composition")
include(":products:data-cloud:extensions:plugins")
include(":products:data-cloud:delivery:api")
include(":products:data-cloud:delivery:launcher")
include(":products:data-cloud:delivery:sdk")
include(":products:data-cloud:contracts")
include(":products:data-cloud:extensions:agent-registry")
include(":products:data-cloud:extensions:agent-catalog")
include(":products:data-cloud:delivery:api-contract-tests")
include(":products:data-cloud:planes:intelligence:feature-ingest")
include(":products:data-cloud:planes:event:store")
include(":products:data-cloud:integration-tests")
include(":products:data-cloud:planes:action")
include(":products:data-cloud:planes:action:operator-contracts")
include(":products:data-cloud:planes:action:central-runtime")
include(":products:data-cloud:planes:action:engine")
include(":products:data-cloud:planes:action:registry")
include(":products:data-cloud:planes:action:analytics")
include(":products:data-cloud:planes:action:security")
include(":products:data-cloud:planes:action:event-bridge")
include(":products:data-cloud:planes:action:agent-runtime")
include(":products:data-cloud:planes:action:api")
include(":products:data-cloud:planes:action:scaling")
include(":products:data-cloud:planes:action:observability")
include(":products:data-cloud:planes:action:orchestrator")
include(":products:data-cloud:planes:action:server")
include(":products:data-cloud:planes:action:identity")
include(":products:data-cloud:planes:action:compliance")

// =============================================================================
// Product: YAPPC
// =============================================================================
include(":products:yappc")
include(":products:yappc:platform")
include(":products:yappc:services")
// SIMP-Y8: services-platform and services-lifecycle merged into yappc-services (removed)
include(":products:yappc:core:yappc-domain-impl")
include(":products:yappc:core:yappc-infrastructure")
include(":products:yappc:core:yappc-services")
include(":products:yappc:core:yappc-api")
include(":products:yappc:core:yappc-shared")
include(":products:yappc:core:scaffold")
include(":products:yappc:core:scaffold:api")
include(":products:yappc:core:scaffold:core")
include(":products:yappc:core:scaffold:templates")
include(":products:yappc:core:scaffold:engine")
include(":products:yappc:core:scaffold:generators")
include(":products:yappc:core:ai")
include(":products:yappc:core:agents")
include(":products:yappc:core:agents:runtime")
include(":products:yappc:core:agents:workflow")
include(":products:yappc:core:agents:common")
include(":products:yappc:core:agents:code-specialists")
include(":products:yappc:core:agents:delivery-specialists")
include(":products:yappc:core:agents:testing-specialists")
include(":products:yappc:core:agents:architecture-specialists")
include(":products:yappc:core:cli-tools")
include(":products:yappc:core:knowledge-graph")
include(":products:yappc:core:refactorer:api")
include(":products:yappc:core:refactorer:engine")
include(":products:yappc:infrastructure:datacloud")
include(":products:yappc:libs:java:yappc-domain")
include(":products:yappc:integration")

// =============================================================================
// Product: Flashit
// =============================================================================
include(":products:flashit")

// =============================================================================
// Product: Audio-Video
// =============================================================================
include(":products:audio-video")
include(":products:audio-video:modules:infrastructure:persistence")
include(":products:audio-video:modules:infrastructure:security")
include(":products:audio-video:modules:infrastructure:cache")
include(":products:audio-video:modules:infrastructure:messaging")
include(":products:audio-video:modules:integration-tests")
include(":products:audio-video:modules:intelligence:multimodal-service")
include(":products:audio-video:modules:speech:stt-service")
include(":products:audio-video:modules:speech:tts-service")
include(":products:audio-video:modules:vision:vision-service")

// =============================================================================
// Product: DCMaar
// =============================================================================
include(":products:dcmaar:libs:java:ai-platform-adapters-guardian")
include(":products:dcmaar:libs:java:guardian-threat-service")

// =============================================================================
// Product: Tutorputor
// =============================================================================
include(":products:tutorputor:services:tutorputor-content-generation")
include(":products:tutorputor:libs:content-studio-agents")

// =============================================================================
// Product: Aura
// =============================================================================
include(":products:aura")
include(":products:aura:foundation")
include(":products:aura:domain:profile")
include(":products:aura:domain:catalog")
include(":products:aura:domain:recommendation")
include(":products:aura:domain:explainability")
include(":products:aura:domain:community")
include(":products:aura:agents:intelligence-agent")
include(":products:aura:agents:task-agent")
include(":products:aura:platform:api")
include(":products:aura:platform:config")
include(":products:aura:integration:aep")
include(":products:aura:integration:knowledge-graph")

// =============================================================================
// Product: Software-Org
// =============================================================================
include(":products:software-org")
include(":products:software-org:engine:boot")
include(":products:software-org:engine:modules:domain-model")
include(":products:software-org:engine:modules:integration")
include(":products:software-org:engine:modules:integration:plugins")
include(":products:software-org:engine:modules:integration:ci")
include(":products:software-org:engine:modules:integration:jira")
include(":products:software-org:engine:modules:integration:github")
include(":products:software-org:libs:java:departments")
include(":products:software-org:launcher")

// =============================================================================
// Product: Virtual-Org
// =============================================================================
include(":products:virtual-org")
include(":products:virtual-org:contracts:proto")
include(":products:virtual-org:engine:service")
include(":products:virtual-org:modules:agent")
include(":products:virtual-org:modules:operator-adapter")
include(":products:virtual-org:modules:framework")
include(":products:virtual-org:modules:integration")
include(":products:virtual-org:modules:workflow")
include(":products:virtual-org:launcher")

// =============================================================================
// Product: Security-Gateway
// =============================================================================
include(":products:security-gateway:platform:java")
include(":products:security-gateway:launcher")

// =============================================================================
// Product: PHR
// =============================================================================
include(":products:phr")
include(":products:phr:launcher")

// =============================================================================
// Product: Finance
// =============================================================================
include(":products:finance")
include(":products:finance:launcher")
include(":products:finance:platform-sdk")
include(":products:finance:operator-workflows")
include(":products:finance:regulator-portal")
include(":products:finance:rules-engine")
include(":products:finance:data-governance")
include(":products:finance:ledger-framework")
include(":products:finance:calendar-service")
include(":products:finance:incident-management")
include(":products:finance:domains:oms")
include(":products:finance:domains:ems")
include(":products:finance:domains:pms")
include(":products:finance:domains:risk")
include(":products:finance:domains:compliance")
include(":products:finance:domains:rules")
include(":products:finance:domains:corporate-actions")
include(":products:finance:domains:integration")
include(":products:finance:domains:market-data-core")
include(":products:finance:domains:market-data")
include(":products:finance:domains:post-trade")
include(":products:finance:domains:pricing")
include(":products:finance:domains:reconciliation")
include(":products:finance:domains:reference-data")
include(":products:finance:domains:regulatory-reporting")
include(":products:finance:domains:sanctions")
include(":products:finance:domains:surveillance")
include(":products:finance:extensions")
include(":products:finance:integration-testing")
include(":products:finance:client-onboarding")

// =============================================================================
// Platform Shared Services (Kernel Bridges)
// =============================================================================
// Kernel bridge modules (product-specific adapters - moved to products/)
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
