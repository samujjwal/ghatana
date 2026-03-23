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
include(":platform:java:testing")
include(":platform:java:runtime")
include(":platform:java:config")
include(":platform:java:workflow")
include(":platform:java:plugin")
include(":platform:java:event-cloud")
include(":platform:java:ai-integration")  // Merged: registry + observability + feature-store + experimental consolidated
include(":platform:java:governance")
include(":platform:java:security")
include(":platform:java:agent-core")
include(":platform:java:agent-runtime")   // Merged: agent-memory + agent-learning + agent-dispatch + agent-resilience
include(":platform:java:agent-registry")
// schema-registry merged into platform:java:domain (SCHM-1, 2026)
include(":platform:java:connectors")
include(":platform:java:audit")
include(":platform:java:kernel")
include(":platform:java:kernel-capabilities")  // Merged: authentication + config + event-store + audit + resilience + observability + secrets

// =============================================================================
// Product: AEP — Autonomous Event Processing
// =============================================================================

include(":products:aep:contracts")
include(":products:aep:aep-operator-contracts")
include(":products:aep:aep-central-runtime")
include(":products:aep:aep-engine")
include(":products:aep:aep-registry")
include(":products:aep:aep-analytics")
include(":products:aep:aep-security")
include(":products:aep:aep-connectors")
// aep-agent merged into aep-registry on 2026-03-22 (boundary audit P0)
include(":products:aep:aep-api")
include(":products:aep:aep-scaling")
include(":products:aep:orchestrator")
include(":products:aep:server")

// =============================================================================
// Product: Data-Cloud — Multi-tenant Metadata Management
// =============================================================================

include(":products:data-cloud:spi")
include(":products:data-cloud:platform")
include(":products:data-cloud:launcher")
include(":products:data-cloud:sdk")
include(":products:data-cloud:agent-registry")
include(":products:data-cloud:feature-store-ingest")  // migrated from shared-services per ADR-013

// =============================================================================
// Product: YAPPC — Yet Another Platform Creator
// =============================================================================

include(":products:yappc")
include(":products:yappc:platform")
include(":products:yappc:services")
include(":products:yappc:services:platform")
// services:ai removed — absorbed into services:lifecycle
include(":products:yappc:services:lifecycle")
// services:scaffold removed — absorbed into services:lifecycle
include(":products:yappc:backend:api")
include(":products:yappc:backend:persistence")
include(":products:yappc:backend:auth")
include(":products:yappc:backend:deployment")
// backend:websocket removed — sources merged into backend:api
include(":products:yappc:core:domain")
include(":products:yappc:core:scaffold")
include(":products:yappc:core:scaffold:api")
include(":products:yappc:core:scaffold:core")
// core:scaffold:packs removed — absorbed into core:scaffold:core
include(":products:yappc:core:ai")
include(":products:yappc:core:agents")
include(":products:yappc:core:agents:runtime")
include(":products:yappc:core:agents:workflow")
include(":products:yappc:core:agents:specialists")
include(":products:yappc:core:spi")
include(":products:yappc:core:cli-tools")
include(":products:yappc:core:knowledge-graph")
include(":products:yappc:core:lifecycle")
include(":products:yappc:core:framework")
include(":products:yappc:core:framework:integration-test")
include(":products:yappc:core:refactorer:api")
include(":products:yappc:core:refactorer:engine")
include(":products:yappc:infrastructure:datacloud")
// infrastructure:security removed — absorbed into backend:auth
include(":products:yappc:libs:java:yappc-domain")
// launcher removed — superseded by backend:api application launcher

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

//include(":products:tutorputor:apps:content-explorer")
//project(":products:tutorputor:apps:content-explorer").projectDir = File(
//    rootDir,
//   "products/tutorputor/apps/tutorputor-explorer"
//)
include(":products:tutorputor:libs:content-studio-agents")
//include(":products:tutorputor:services:tutorputor-ai-agents")
include(":products:tutorputor:services:tutorputor-content-generation")

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
include(":products:software-org:libs:java:departments")
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
// Product: PHR — Personal Health Records with Nepal Privacy Act 2075 compliance
// =============================================================================

include(":products:phr")

// =============================================================================
// Product: Finance — Financial Operating System with Regulatory Compliance
// Migrated from app-platform with kernel vision compliance
// =============================================================================

include(":products:finance:platform-sdk")
include(":products:finance:operator-workflows")
include(":products:finance:regulator-portal")
include(":products:finance:rules-engine")
include(":products:finance:data-governance")
include(":products:finance:ledger-framework")
include(":products:finance:calendar-service")
include(":products:finance:incident-management")

// Finance Domain Modules - Core (OMS, EMS, PMS, Risk, Compliance, Rules)
include(":products:finance:domains:oms")
include(":products:finance:domains:ems")
include(":products:finance:domains:pms")
include(":products:finance:domains:risk")
include(":products:finance:domains:compliance")
include(":products:finance:domains:rules")

// Finance Domain Modules - Phase 2 Migration (9 new domains)
include(":products:finance:domains:corporate-actions")
include(":products:finance:domains:market-data")
include(":products:finance:domains:post-trade")
include(":products:finance:domains:pricing")
include(":products:finance:domains:reconciliation")
include(":products:finance:domains:reference-data")
include(":products:finance:domains:regulatory-reporting")
include(":products:finance:domains:sanctions")
include(":products:finance:domains:surveillance")

// Finance Domain Pack Extensions
include(":products:finance:extensions")

// Finance Integration Testing (migrated from app-platform)
include(":products:finance:integration-testing")

// Finance Client Onboarding with AML (migrated from app-platform)
include(":products:finance:client-onboarding")

// =============================================================================
// Shared Services — Cross-product microservices
// =============================================================================

// include(":shared-services:ai-inference-service")   // ARCHIVED: build not stabilised; see shared-services/ai-inference-service/STATUS.md
// include(":shared-services:ai-registry")             // consolidated into platform:java:ai-integration per ADR-013
include(":shared-services:auth-gateway")                // absorbs auth-service
// include(":shared-services:feature-store-ingest")     // migrated to products:data-cloud per ADR-013
// include(":shared-services:auth-service")             // consolidated into auth-gateway per ADR-013
include(":shared-services:user-profile-service")
