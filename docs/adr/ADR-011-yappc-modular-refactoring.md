# ADR-011: YAPPC Modular Service Architecture Refactoring

**Status:** Accepted  
**Date:** 2026-02-21  
**Decision Makers:** Architecture Team  
**Phase:** 2.0 — YAPPC Restructuring  

## Context

YAPPC (Yet Another Project Planning Canvas) had grown organically into a monolithic structure with several architectural issues:

- **40+ Gradle build files** with inconsistent configuration and no standalone build support
- **34 frontend libraries** with significant overlap and duplication
- **Custom `core/framework` module** (55+ classes) duplicating platform capabilities (auth, audit, encryption, observability)
- **Single monolithic service** handling all concerns (AI, lifecycle, scaffolding, domain logic, infrastructure)
- **No ActiveJ DI wiring** — services lacked proper dependency injection despite being an ActiveJ codebase

This was creating developer friction, slowing builds, and introducing divergence from platform standards.

## Decision

Execute a 5-phase restructuring of YAPPC to align with platform architecture standards:

### Phase 1: Build System Standardization
- Create standalone `settings.gradle.kts` following Flashit/Tutorputor patterns
- Standardize all build files to ActiveJ (eliminate any Spring Boot remnants)
- Add `Makefile` targets for standalone builds

### Phase 2: Directory Structure & Backend Bridge
- Create 6 focused service modules: `api`, `ai`, `lifecycle`, `scaffold`, `domain`, `infrastructure`
- Bridge legacy 80+ endpoints via `LegacyRouteRegistrar → ProductionModule` pattern
- Consolidate frontend libraries from 34 → 20 canonical + 13 deprecated with re-export proxies

### Phase 3: Framework Consolidation & Deprecation
- Deprecate entire `core/framework` module (14 packages, 55+ classes)
- Migrate reusable classes to platform:
  - `ActiveJPatterns` → `platform:java:core:async` (retry, circuit breaker, timeout, parallel)
  - `AIFallbackService` → `core:ai:resilience` (product-specific AI resilience)
  - Audit reporting VOs → `platform:java:audit:reporting`
  - `KeyManagementService` → `platform:java:security:encryption`
- Create `PlatformPluginBridge` + `UnifiedPluginBootstrap` for legacy plugin compatibility
- Security/auth VOs deprecated in favor of platform `Credentials` / `AuthenticationProvider`

### Phase 4: Frontend Simplification
- Remove ~49 redundant files (compiled outputs, debug artifacts, empty directories)
- Consolidate overlapping libraries via proxy re-export pattern

### Phase 5: Service Layer Unification
- 4 ActiveJ launchers extending `UnifiedApplicationLauncher`
- 5 DI modules (`AiServiceModule`, `LifecycleServiceModule`, `ScaffoldServiceModule`, `InfrastructureServiceModule`, `DomainServiceModule`)
- 2 facades, integration test suite (6/6 tests)
- `CorsMiddleware` + `GlobalExceptionHandler` on entry point

## Rationale

| Concern | Before | After | Why |
|:---|:---|:---|:---|
| Framework duplication | 55+ classes in `core/framework` reimplementing platform | All deprecated, 7 migrated to platform/core | Golden Rule #1 "Reuse First" |
| Service coupling | Single entry point | 6 focused services with DI | Separation of concerns, independent scaling |
| Build isolation | No standalone build | `settings.gradle.kts` with parent detection | Developer velocity, CI parallelism |
| Frontend bloat | 34 libraries | 20 canonical + 13 deprecated | Reduced bundle size, clearer dependency graph |
| Auth/Security | Custom `AuthenticationRequest/Result` | Platform `Credentials` + `AuthenticationProvider` | Standards compliance, no duplicate abstractions |
| Async patterns | Framework-specific `ActiveJPatterns` with `Reactor` dep | Platform `ActiveJPatterns` with `volatile` + `System.currentTimeMillis()` | Broader reusability, no reactor coupling |

## Consequences

### Positive
- **100 deprecation warnings** replace silent duplication — migration path is explicit
- **6.9s full build** (11 modules, `--parallel`) — well under 5-minute target
- **214 tests passing** (198 platform:core + 16 YAPPC) — zero regressions
- **Re-export proxies** preserve backward compatibility during gradual migration
- **Platform enriched** with `ActiveJPatterns`, audit reporting, `KeyManagementService`

### Negative
- **100 deprecation warnings** create noise until consumers migrate (expected ~3 months)
- **Re-export proxies** add a thin indirection layer for 13 frontend libraries
- **Import path migration** deferred — requires coordinated effort across teams

### Risks
- Legacy plugins may depend on deprecated framework classes — bridged via `PlatformPluginBridge`
- Frontend proxy re-exports may mask circular dependencies — monitored via build

## References

- [ADR-004: ActiveJ Framework](ADR-004-activej-framework.md) — foundational async/DI decision
- [YAPPC Refactoring Plan v2.3](../../products/yappc/YAPPC_REFACTORING_IMPLEMENTATION_PLAN.md) — full implementation details
- Golden Rules: `copilot-instructions.md` §1
