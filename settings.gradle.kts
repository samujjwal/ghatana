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
include(":platform:java:ai-integration")  // Merged: registry + observability + feature-store + experimental consolidated
include(":platform:java:governance")
include(":platform:java:security")
include(":platform:java:agent-core")
// agent-runtime RELOCATED to products:aep:aep-agent-runtime (Sprint 4, 2026-03-25 — see audit report Phase 5)
// agent-registry DELETED (merged into agent-core in Sprint 1, 2026-03-24 — see audit report Phase 1)
// schema-registry merged into platform:java:domain (SCHM-1, 2026)
include(":platform:java:connectors")
include(":platform:java:audit")
include(":platform:java:kernel")
include(":platform:java:kernel-persistence")    // Durable adapters: PostgresAuditTrailPersistence, RedisKernelConfigResolver, JdbcModuleRegistry
include(":platform:java:audio-video")   // STT, TTS, Vision engine library (com.ghatana.media.*)
// kernel-capabilities DELETED (zero consumers, capabilities merged into kernel/observability — Sprint 5, 2026-03-25)
include(":platform:java:distributed-cache")    // KRQ-05: Generic Redis-backed distributed cache abstraction
include(":platform:java:identity")             // Phase 1 — Identity brokering, delegation tokens, credential management
include(":platform:java:data-governance")       // Phase 2 — Consent, PII classification, purpose-limitation, data minimization
include(":platform:java:tool-runtime")          // Phase 3 — Tool sandboxing, execution monitoring, approval gates
include(":platform:java:policy-as-code")        // Phase 4 — OPA integration, policy-as-code engine, risk scoring
include(":platform:java:security-analytics")    // Phase 5 — Egress monitoring, prompt-injection detection
include(":platform:java:incident-response")     // Phase 6 — Kill-switch, taxonomy, graceful degradation
include(":platform:java:billing")               // Shared billing contracts: BillingTransaction, LedgerPostingService, HealthcareBillingExtension

// =============================================================================
// Product: AEP — Autonomous Event Processing
// =============================================================================

include(":products:aep:contracts")
include(":products:aep:aep-operator-contracts")
include(":products:aep:aep-central-runtime")
include(":products:aep:aep-engine")
include(":products:aep:aep-runtime-core")    // Backward-compat facade; also hosts shared test infrastructure
include(":products:aep:aep-registry")
include(":products:aep:aep-analytics")
include(":products:aep:aep-security")
include(":products:aep:aep-connectors")
include(":products:aep:aep-event-cloud")     // Data-Cloud bridge plugin for AEP event processing
include(":products:aep:aep-agent-runtime")  // Advanced agent runtime: memory, dispatch, learning, resilience (relocated from platform:java:agent-runtime)
// aep-agent merged into aep-registry on 2026-03-22 (boundary audit P0)
include(":products:aep:aep-api")
include(":products:aep:aep-scaling")
include(":products:aep:orchestrator")
include(":products:aep:server")
include(":products:aep:aep-identity")           // Phase 8 — AEP-specific identity resolution and external identity bridging
include(":products:aep:aep-compliance")         // Phase 8 — AEP compliance: retention, consent propagation, deletion workflows

// =============================================================================
// Product: Data-Cloud — Multi-tenant Metadata Management
// =============================================================================

include(":products:data-cloud:spi")
include(":products:data-cloud:platform-entity")
include(":products:data-cloud:platform-event")
include(":products:data-cloud:platform-config")
include(":products:data-cloud:platform-analytics")
include(":products:data-cloud:platform-launcher")
// FINDING-DC-H2: platform-launcher split (Phase 1 - module scaffolding)
// Phase 2 will physically move sources from platform-launcher into these modules.
include(":products:data-cloud:platform-client")   // Client SDK (no infra deps)
include(":products:data-cloud:platform-plugins")  // Storage plugin implementations
include(":products:data-cloud:platform-api")      // REST / gRPC / GraphQL controllers
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
// services:platform moved to core (Phase 2: separate deployables from reusables)
include(":products:yappc:core:services-platform")
// services:ai removed — absorbed into services:lifecycle
// services:lifecycle moved to core (Phase 2: separate deployables from reusables)
include(":products:yappc:core:services-lifecycle")
// services:scaffold removed — absorbed into services:lifecycle
// backend modules removed (2026-03-23) — functionality consolidated into core modules
// =============================================================================
// YAPPC Core — Consolidated Modules (2026-03-23)
// =============================================================================
include(":products:yappc:core:yappc-agents")
// core:yappc-domain renamed to yappc-domain-impl (Phase 3: clarify internal-only role)
include(":products:yappc:core:yappc-domain-impl")
include(":products:yappc:core:yappc-infrastructure")
include(":products:yappc:core:yappc-services")
include(":products:yappc:core:yappc-api")
include(":products:yappc:core:yappc-shared")

// =============================================================================
// YAPPC Core — Legacy Modules (to be removed after migration validation)
// =============================================================================
// core:domain removed (2026-03-24) — absorbed into core:yappc-domain
include(":products:yappc:core:scaffold")
include(":products:yappc:core:scaffold:api")
include(":products:yappc:core:scaffold:core")
include(":products:yappc:core:scaffold:templates")
include(":products:yappc:core:scaffold:engine")
include(":products:yappc:core:scaffold:generators")
// core:scaffold:packs removed — absorbed into core:scaffold:core
include(":products:yappc:core:ai")
include(":products:yappc:core:agents")
include(":products:yappc:core:agents:runtime")
include(":products:yappc:core:agents:workflow")
include(":products:yappc:core:agents:common")
include(":products:yappc:core:agents:code-specialists")
include(":products:yappc:core:agents:delivery-specialists")
include(":products:yappc:core:agents:architecture-specialists")
include(":products:yappc:core:agents:testing-specialists")
include(":products:yappc:core:spi")
include(":products:yappc:core:cli-tools")
include(":products:yappc:core:knowledge-graph")
// core:lifecycle removed (2026-03-24) — absorbed into core:yappc-services
// core:framework removed (2026-03-24) — absorbed into core:yappc-infrastructure
// core:framework:integration-test removed (2026-03-24) — moved to yappc-infrastructure
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
// Cross-Product Integration Tests
// =============================================================================

include(":integration-tests:phr-finance-integration")

// =============================================================================
// Shared Services — Cross-product microservices
// =============================================================================

// include(":shared-services:ai-inference-service")   // ARCHIVED: build not stabilised; see shared-services/ai-inference-service/STATUS.md
// include(":shared-services:ai-registry")             // consolidated into platform:java:ai-integration per ADR-013
include(":shared-services:auth-gateway")                // absorbs auth-service
// include(":shared-services:feature-store-ingest")     // migrated to products:data-cloud per ADR-013
// include(":shared-services:auth-service")             // consolidated into auth-gateway per ADR-013
include(":shared-services:user-profile-service")
