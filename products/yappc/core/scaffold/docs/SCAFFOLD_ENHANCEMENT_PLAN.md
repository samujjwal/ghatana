# YAPPC Scaffold Enhancement & Plugin Framework Plan

Goal: deliver a flexible, extensible, plugin-first scaffold system that covers frontend, backend, middleware, mobile/desktop, libraries, API-only services, and polyglot compositions. Each phase is implementation-ready with owners and outputs.

---

## Phase A — Foundation (Plugin SPI + Core Build Providers)
**Anchor to current codebase/patterns**
- Keep PackEngine/TemplateEngine as the core render path; Plugin SPI wraps/extends these without changing existing APIs.
- Reuse YappcApi surface + HTTP/gRPC servers for plugin management; add minimal endpoints/stubs rather than new services.
- Extend existing BuildSystemType/PolyglotBuildOrchestrator and Go/Cargo/Make patterns for new providers.

**Objectives**
- Establish plugin SPI and lifecycle.
- Add missing build providers for Python and .NET; improve CMake/C++.
- Extend polyglot orchestrator to new build systems.

**Work Items**
1) Plugin SPI (core/plugin/)
   - Define interfaces: `PackDiscoveryPlugin`, `TemplateHelperPlugin`, `FeaturePackPlugin`, `BuildSystemPlugin`, `PostProcessorPlugin`, `AnalyzerPlugin`, `TelemetryPlugin`.
   - Plugin metadata: id, version, capabilities, supported languages/build systems, required config/env, stability level.
   - Lifecycle: load → init → advertise capabilities → healthcheck → unload; classloader/module isolation; timeouts and allow/deny write boundaries.
   - Event hooks: before/after render, before/after apply, validation, analysis, post-process.
   - Config: yappc config gains `plugins.enabled[]`, `plugins.paths[]`, `plugins.registry`, `sandbox` flags.

2) Plugin management surfaces
   - CLI: `yappc plugins list/install/enable/disable/info`.
   - HTTP/gRPC: list, enable/disable, capabilities, run analyzer/post-processor.
   - API: expose plugin registry/loader via `YappcApi.plugins()`.

3) BuildSystemProviders
   - Python (UV/Poetry): generate pyproject.toml, lock optional; commands for lint/test/build; validate spec; suggest upgrades (pip/uv index).
   - .NET SDK: generate .csproj/.sln, minimal API template hooks; lint/test via analyzers/dotnet test; validate; suggest upgrades (NuGet feed).
   - CMake/C++: flesh out Make/CMake support with config gen, validate, analyze, improvements.
   - Extend BuildSystemType enum and serialization; wire into orchestrator.

4) PolyglotBuildOrchestrator
   - Add targets for uv/poetry, dotnet, cmake, and ensure pnpm/turbo/gradle/cargo/go remain.
   - Emit per-service targets + top-level aggregate, ordered by dependency graph.

5) Quality gates
   - Golden tests for new build providers (render + validate).
   - Integration: create → validate → analyze for Python/.NET/C++.

**Deliverables**
- Plugin SPI + loader + config
- CLI/HTTP/gRPC plugin management
- BuildSystemProviders: Python, .NET, improved CMake
- Orchestrator updated for new systems
- Tests + docs for SPI usage

---

## Phase B — Language & Framework Coverage
**Anchor to current codebase/patterns**
- Follow existing pack.json schema and template layout used by go-service-chi, rust-service-axum-cargo, ts-node-fastify, java-service-spring-gradle.
- Keep feature packs (db/auth/obs) structure and variable conventions; add new language renders behind the same feature names.
- Reuse CLI/API flows (create/add/update/validate) for new packs.

**Objectives**
- Add backend variants and fill library/CLI gaps.
- Ensure API-only/middleware options exist.

**Work Items**
1) Backend packs
   - Go Gin service pack.
   - Rust Actix service pack.
   - Python FastAPI service pack (uv/poetry).
   - .NET Minimal API pack.
   - Kotlin/Java (Spring Boot/Quarkus) optional variant if desired.

2) Library/CLI packs
   - Python library pack.
   - Rust CLI pack (clap).
   - Go CLI pack (cobra).
   - .NET class library pack.
   - C++ library pack (CMake).

3) Middleware/API-only
   - TS/Node middleware pack (Nest/Express).
   - Go API gateway pack (chi/gin) with auth/rate-limit hooks.
   - Java gateway (Spring Cloud) optional.
   - OpenAPI-first pack: contract-first stubs + codegen hooks.

4) Template quality
   - Include lint/test/format config per language (ruff/mypy/pytest; golangci-lint; clippy/fmt; eslint/prettier; dotnet analyzers; clang-tidy/format).

**Deliverables**
- New pack directories with pack.json + templates
- Per-language README usage
- Validation coverage in pack tests

---

## Phase C — Frontend, Desktop, Mobile
**Anchor to current codebase/patterns**
- Mirror current React/Vite and Tauri/React pack structure (pack.json + templates + README.hbs).
- Keep pnpm/turbo workspace conventions already used in existing TS packs.
- Reuse feature pack hooks (auth/db/obs) when applicable to frontend/middleware additions.

**Objectives**
- Broaden UI coverage and platform reach.

**Work Items**
1) Frontend packs
   - Vue + Vite pack.
   - Remix/Next.js full-stack-ready variant.
   - SvelteKit pack.
   - UI library pack (component library baseline with Storybook optional).

2) Desktop/Mobile
   - Tauri + Vue flavor (alongside React).
   - React Native bare workflow pack with native module stubs (iOS/Android).
   - (Stretch) Flutter or KMM pack.

3) DX
   - Include testing presets (Vitest/Cypress/Playwright), linting, theming tokens.

**Deliverables**
- Pack additions with templates and docs
- E2E render tests for each new UI pack

---

## Phase D — Infra, Ops, Security
**Anchor to current codebase/patterns**
- Extend base pack docker/ci layout instead of inventing new structure.
- Use existing observability feature-pack conventions; add language variants rather than new feature names.
- Keep SBOM/audit hooks as post-processors compatible with current CLI/HTTP/gRPC surfaces.

**Objectives**
- Ship operational scaffolding for all stacks.

**Work Items**
1) Infra packs
   - Docker + docker-compose templates per language.
   - K8s manifests and Helm chart starter.
   - Terraform baseline for common services (db/cache/message broker) with environment overlays.

2) CI/CD packs
   - GitHub Actions and GitLab CI presets per language/build system.
   - Matrix examples for polyglot repos.

3) Security & supply chain
   - SBOM generation hooks (Syft/npm sbom/cargo-about/dotnet sbom/uv export).
   - Audit hooks (pip-audit/osv-scanner/npm audit/go vulncheck/cargo audit/dotnet list package –vulnerable).
   - Container scan stub.

4) Observability & ops
   - Logging/metrics/tracing defaults per language (tie into existing feature-observability).
   - Health/readiness/liveness endpoints defaults across packs.

**Deliverables**
- Infra/CI/security pack templates
- Docs for enabling scans and SBOM
- Validation checks in CI examples

---

## Phase E — Compositions & Microservices
**Anchor to current codebase/patterns**
- Build on MultiRepoWorkspaceSpec and PolyglotBuildOrchestrator already in docs/plan; emit the same Makefile/Turbo targets style.
- Keep composition metadata compatible with pack.json (composition flag + composedPacks array).
- Reuse gateway/middleware pack patterns for route wiring rather than bespoke logic.

**Objectives**
- Turn single packs into cohesive multi-service solutions.

**Work Items**
1) Composition packs
   - Microservices composition: gateway + N backends + shared UI + infra.
   - Full-stack Next.js composition (SSR + API).
   - Contract-first composition (OpenAPI/GraphQL SDL wired to services).

2) Orchestration wiring
   - Generate shared env (.env/.env.example), docker-compose, make/turbo targets for all services.
   - Route wiring for gateway (path → service), CORS defaults, local TLS optional.

3) Data/infra options
   - Optional feature add-ons (DB/Auth/Observability/Messaging) as toggles at composition creation.

**Deliverables**
- Composition pack metadata + templates
- Orchestrator targets + docs

---

## Phase F — Plugin Ecosystem & Registry
**Anchor to current codebase/patterns**
- Keep registry/client minimal and JVM-first to align with existing YappcApi runtime; allow filesystem + simple HTTP registry.
- Reuse plugin metadata schema defined in Phase A; avoid new config surfaces beyond existing YAPPC config/CLI/HTTP/gRPC.
- Security/verification should integrate with current pack validation flow.

**Objectives**
- Operationalize plugins beyond local filesystem.

**Work Items**
1) Plugin registry
   - Simple file/HTTP registry spec with signed metadata (id, version, checksum, capabilities).
   - CLI/HTTP: install from registry, verify signature/checksum.

2) Sandbox/hardening
   - Enforce allow/deny file roots; timeouts; optional “dry-run-only” mode.
   - Telemetry opt-in with redaction; plugin-level consent flags.

3) Samples
   - Reference plugins: custom template helpers, custom analyzer, custom post-processor (formatter), custom build system adapter.

**Deliverables**
- Registry format + client
- Security hardening
- Sample plugins + docs

---

## Cross-Cutting Items
- Documentation: Update USER_GUIDE, PACK_AUTHORING_GUIDE; add “Writing Plugins” and “Adding a Build System” guides; per-pack READMEs.
- Testing: Golden renders for every pack; E2E flows (create → add feature → update → validate) per language; plugin SPI unit tests; orchestrator integration tests.
- Backward compatibility: versioned pack schema; shims for legacy packs; deprecation warnings not breakage.
- Performance: cache pack metadata; parallel rendering; measure create-time and file counts; optional incremental update.
- Security: Signed packs/plugins; version pinning; Renovate/Dependabot configs generated where relevant.

---

## Ownership & Sequencing (suggested)
- Phase A: Core platform team (plugin SPI, build providers, orchestrator).
- Phase B/C: Language specialists (TS/Go/Rust/Python/.NET/C++/Frontend/Mobile).
- Phase D: DevOps/Security.
- Phase E: Platform + solution architects.
- Phase F: Platform + security.

---

## Ready-to-Implement Checklists
- Plugin SPI interfaces coded, loader wired, config keys exposed.
- New BuildSystemTypes added; providers registered; orchestrator updated.
- New packs have pack.json, templates, defaults, lint/test configs, README.
- Tests: golden templates, validation, E2E per language.
- CI examples added; security/audit hooks present; SBOM enabled.
- Docs updated for packs, plugins, build systems, compositions.
