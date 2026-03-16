/**
 * Ghatana Monorepo Settings
 *
 * Every module on disk is explicitly included — NO conditional includes,
 * NO file/test/module exclusions.  If a directory has a build.gradle.kts
 * it MUST be listed here (except Android/React-Native modules that require
 * the Android SDK, which are in a separate composite build).
 *
 * Design Principles:
 * - DETERMINISTIC: Same code → same build result, always
 * - EXPLICIT: All modules declared, no conditional magic
 * - COMPLETE: Every module compiles and tests green
 */
rootProject.name = "ghatana"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// =============================================================================
// Platform — Contracts
// =============================================================================

include(":platform:contracts")

// =============================================================================
// Platform — Java modules (shared infrastructure used by multiple products)
// =============================================================================

include(":platform:java:core")
include(":platform:java:domain")
include(":platform:java:database")
include(":platform:java:http")
include(":platform:java:observability")
include(":platform:java:observability-http")
include(":platform:java:observability-clickhouse")
include(":platform:java:testing")
include(":platform:java:runtime")
include(":platform:java:config")
include(":platform:java:workflow")
include(":platform:java:plugin")
include(":platform:java:event-cloud")
include(":platform:java:ai-integration")
include(":platform:java:ai-integration:registry")
include(":platform:java:ai-integration:observability")
include(":platform:java:ai-integration:feature-store")
include(":platform:java:ai-api")
include(":platform:java:ai-experimental")
include(":platform:java:governance")
include(":platform:java:security")
include(":platform:java:agent-framework")
include(":platform:java:agent-memory")
include(":platform:java:agent-learning")
include(":platform:java:agent-resilience")
include(":platform:java:agent-registry")
include(":platform:java:agent-dispatch")
include(":platform:java:yaml-template")
include(":platform:java:schema-registry")
include(":platform:java:connectors")
include(":platform:java:ingestion")
include(":platform:java:audit")

// =============================================================================
// Product: AEP — Autonomous Event Processing
// =============================================================================

include(":products:aep:platform")
include(":products:aep:launcher")

// =============================================================================
// Product: Data-Cloud — Multi-tenant Metadata Management
// =============================================================================

include(":products:data-cloud:spi")
include(":products:data-cloud:platform")
include(":products:data-cloud:launcher")
include(":products:data-cloud:sdk")
include(":products:data-cloud:agent-registry")

// =============================================================================
// Product: YAPPC — Yet Another Platform Creator
// =============================================================================

include(":products:yappc")
include(":products:yappc:platform")
include(":products:yappc:services")
include(":products:yappc:services:domain")
include(":products:yappc:services:infrastructure")
include(":products:yappc:services:ai")
include(":products:yappc:services:lifecycle")
include(":products:yappc:services:scaffold")
include(":products:yappc:backend:api")
include(":products:yappc:backend:persistence")
include(":products:yappc:backend:auth")
include(":products:yappc:backend:deployment")
include(":products:yappc:backend:websocket")
include(":products:yappc:core:domain")
include(":products:yappc:core:domain:service")
include(":products:yappc:core:domain:task")
include(":products:yappc:core:scaffold")
include(":products:yappc:core:scaffold:api")
include(":products:yappc:core:scaffold:api:http")
include(":products:yappc:core:scaffold:api:grpc")
include(":products:yappc:core:scaffold:adapters")
include(":products:yappc:core:scaffold:cli")
include(":products:yappc:core:scaffold:schemas")
include(":products:yappc:core:scaffold:core")
include(":products:yappc:core:scaffold:packs")
include(":products:yappc:core:ai")
include(":products:yappc:core:agents")
include(":products:yappc:core:spi")
include(":products:yappc:core:cli-tools")
include(":products:yappc:core:knowledge-graph")
include(":products:yappc:core:lifecycle")
include(":products:yappc:core:framework")
include(":products:yappc:core:framework:integration-test")
include(":products:yappc:core:refactorer:api")
include(":products:yappc:core:refactorer:engine")
include(":products:yappc:infrastructure:datacloud")
include(":products:yappc:libs:java:yappc-domain")
include(":products:yappc:launcher")

// =============================================================================
// Product: Flashit — Context Capture Platform
// =============================================================================

include(":products:flashit")
// flashit:platform removed — empty module (zero sources, only re-exported platform:java:core)
// flashit:backend:agent (39 files) excluded from root build by design — uses composite build only

// =============================================================================
// Product: Audio-Video — Speech, Vision, Intelligence
// =============================================================================

include(":products:audio-video")
include(":products:audio-video:libs:common")
include(":products:audio-video:modules:intelligence:multimodal-service")
include(":products:audio-video:modules:speech:stt-service")
include(":products:audio-video:modules:speech:tts-service")
include(":products:audio-video:modules:vision:vision-service")

// =============================================================================
// Product: DCMAAR — AI Platform Guardian
// =============================================================================

include(":products:dcmaar:libs:java:ai-platform-adapters-guardian")
include(":products:dcmaar:libs:java:guardian-threat-service")

// =============================================================================
// Product: Tutorputor — AI Tutoring Platform
// =============================================================================

include(":products:tutorputor:apps:content-explorer")
include(":products:tutorputor:libs:content-studio-agents")
include(":products:tutorputor:services:tutorputor-ai-agents")
include(":products:tutorputor:services:tutorputor-content-studio-grpc")

// =============================================================================
// Product: Software-Org — Software Organization Simulation
// =============================================================================

include(":products:software-org")
include(":products:software-org:engine:boot")
include(":products:software-org:engine:modules:domain-model")
include(":products:software-org:engine:modules:integration")
include(":products:software-org:engine:modules:integration:plugins")
include(":products:software-org:engine:modules:integration:ci")
include(":products:software-org:engine:modules:integration:jira")
include(":products:software-org:engine:modules:integration:github")
include(":products:software-org:libs:java:departments:engineering")
include(":products:software-org:libs:java:departments:qa")
include(":products:software-org:libs:java:departments:devops")
include(":products:software-org:libs:java:departments:support")
include(":products:software-org:libs:java:departments:product")
include(":products:software-org:libs:java:departments:sales")
include(":products:software-org:libs:java:departments:finance")
include(":products:software-org:libs:java:departments:hr")
include(":products:software-org:libs:java:departments:compliance")
include(":products:software-org:libs:java:departments:marketing")
include(":products:software-org:launcher")

// =============================================================================
// Product: Virtual-Org — Virtual Organization Framework
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
// Product: Security-Gateway — OAuth 2.1/OIDC + RBAC/ABAC
// =============================================================================

include(":products:security-gateway:platform:java")

// =============================================================================
// Product: App-Platform — Multi-Domain Financial Operating System (Siddhanta)
// Sprint 1: K-05 Event Store, K-07 Audit Trail, K-15 Calendar Service, K-02 Config Engine
// =============================================================================

include(":products:app-platform:kernel:event-store")
include(":products:app-platform:kernel:audit-trail")
include(":products:app-platform:kernel:calendar-service")
include(":products:app-platform:kernel:config-engine")
include(":products:app-platform:kernel:ledger-framework")
include(":products:app-platform:kernel:secrets-management")
include(":products:app-platform:kernel:iam")
include(":products:app-platform:kernel:observability-sdk")
include(":products:app-platform:kernel:api-gateway")
include(":products:app-platform:kernel:resilience-patterns")
include(":products:app-platform:kernel:rules-engine")
include(":products:app-platform:service-template")

// =============================================================================
// Shared Services — Cross-product microservices
// =============================================================================

include(":shared-services:ai-inference-service")
include(":shared-services:ai-registry")
include(":shared-services:auth-gateway")
include(":shared-services:feature-store-ingest")
include(":shared-services:auth-service")
include(":shared-services:user-profile-service")
