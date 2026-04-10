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
include(":platform-plugins:plugin-billing-ledger")
include(":platform-plugins:plugin-compliance")
include(":platform-plugins:plugin-consent")
include(":platform-plugins:plugin-fraud-detection")
include(":platform-plugins:plugin-risk-management")

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
include(":platform:java:agent-memory")
include(":platform:java:connectors")
include(":platform:java:audit")
include(":platform:java:audio-video")
include(":platform:java:distributed-cache")
include(":platform:java:identity")
include(":platform:java:data-governance")
include(":platform:java:tool-runtime")
include(":platform:java:policy-as-code")
include(":platform:java:security-analytics")
include(":platform:java:incident-response")

// =============================================================================
// Product: AEP
// =============================================================================
include(":products:aep:contracts")
include(":products:aep:aep-operator-contracts")
include(":products:aep:aep-central-runtime")
include(":products:aep:aep-engine")
include(":products:aep:aep-runtime-core")
include(":products:aep:aep-registry")
include(":products:aep:aep-analytics")
include(":products:aep:aep-security")
include(":products:aep:aep-connectors")
include(":products:aep:aep-event-cloud")
include(":products:aep:aep-agent-runtime")
include(":products:aep:aep-api")
include(":products:aep:aep-scaling")
include(":products:aep:orchestrator")
include(":products:aep:server")
include(":products:aep:aep-identity")
include(":products:aep:aep-compliance")

// =============================================================================
// Product: Data-Cloud
// =============================================================================
include(":products:data-cloud:spi")
include(":products:data-cloud:platform-entity")
include(":products:data-cloud:platform-event")
include(":products:data-cloud:platform-config")
include(":products:data-cloud:platform-analytics")
include(":products:data-cloud:platform-launcher")
include(":products:data-cloud:platform-client")
include(":products:data-cloud:platform-plugins")
include(":products:data-cloud:platform-api")
include(":products:data-cloud:launcher")
include(":products:data-cloud:sdk")
include(":products:data-cloud:agent-registry")
include(":products:data-cloud:feature-store-ingest")

// =============================================================================
// Product: YAPPC
// =============================================================================
include(":products:yappc")
include(":products:yappc:platform")
include(":products:yappc:services")
include(":products:yappc:core:services-platform")
include(":products:yappc:core:services-lifecycle")
include(":products:yappc:core:yappc-agents")
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

// =============================================================================
// Product: Flashit
// =============================================================================
include(":products:flashit")

// =============================================================================
// Product: Audio-Video
// =============================================================================
include(":products:audio-video")
include(":products:audio-video:libs:common")
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

// =============================================================================
// Product: PHR
// =============================================================================
include(":products:phr")

// =============================================================================
// Product: Finance
// =============================================================================
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
// Shared Services
// =============================================================================
include(":shared-services:auth-gateway")
include(":shared-services:user-profile-service")

// =============================================================================
// Integration Tests
// =============================================================================
include(":integration-tests:phr-finance-integration")
