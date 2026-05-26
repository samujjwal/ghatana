# Production Readiness Actionable Tickets

This map converts the production-readiness audit, product release readiness evidence, and 47-dimension scorecard into executable work. Every row names an owner, validation, evidence surface, and failure classification so gaps cannot pass as vague posture.

## Failure Classifications

- code failure
- product failure
- test failure
- environment blocked
- dependency blocked
- configuration blocked
- policy blocked
- interaction blocked
- release blocked
- evidence blocked

## PRD Ticket Map

| Ticket | Severity | Owner | Current gap | Required implementation | Validation | Evidence | Failure classification |
|---|---|---|---|---|---|---|---|
| PRD-001 | P0 | Product teams | DMOS and PHR workflows are partially proven. | Complete end-to-end pilot workflows with real APIs, UI routes, persistence, permissions, and test evidence. | `pnpm check:product-feature-completeness`; product E2E suites | product scorecards; workflow matrices | product failure |
| PRD-002 | P0 | PHR + i18n | PHR had missing i18n coverage. | Localize PHR web chrome, validation, routes, and high-value pages with locale formatting and pseudo-locale tests. | `pnpm check:i18n-conformance`; PHR i18n tests | PHR locale files; i18n tests | product failure |
| PRD-003 | P0 | Studio | Lifecycle UX is still too CLI-heavy. | Expose launcher, explain, recover, run history, and release readiness views in Studio. | `pnpm check:studio-lifecycle-control-plane`; Studio E2E | Studio lifecycle route and tests | code failure |
| PRD-004 | P1 | Kernel toolchains | Rust adapter lacked product fixture proof. | Maintain a real Rust fixture product with source, tests, lifecycle manifest, and adapter validation. | `pnpm check:rust-adapter-conformance`; `pnpm check:polyglot-product-fixture` | Rust fixture Cargo tests | dependency blocked |
| PRD-005 | P1 | Kernel toolchains | Python adapter lacked product fixture proof. | Maintain a real Python fixture product with source, tests, lifecycle manifest, and adapter validation. | `pnpm check:python-adapter-conformance`; `pnpm check:polyglot-product-fixture` | Python fixture pytest proof | dependency blocked |
| PRD-006 | P0 | Kernel + products | Product interaction broker needed real product adoption. | Keep DMOS campaign activation and PHR consent flows broker-mediated, policy-gated, and evidence-backed. | `pnpm check:product-interaction-broker`; `pnpm check:cross-product-interaction-flows` | broker tests; PHR/DMOS workflow tests | interaction blocked |
| PRD-007 | P1 | Kernel/Data Cloud | Event broker durability needed replay and DLQ proof. | Use durable interaction event provider with replay, delivery, deletion, and DLQ behavior. | `pnpm check:interaction-durable-event-provider` | durable provider tests | interaction blocked |
| PRD-008 | P0 | Kernel/Data Cloud | Evidence files needed durable retention policy. | Preserve evidence via validated APIs, no-secret checks, retention/deletion policy, and audit gate packs. | `pnpm check:evidence-retention-policy` | retention docs; lifecycle evidence API | evidence blocked |
| PRD-009 | P1 | Kernel release | Release logic was too product-specific. | Use product-neutral release profiles mapped from lifecycle profiles. | `pnpm check:production-deployment-provenance`; release tests | `config/product-release-profiles.json` | release blocked |
| PRD-010 | P0 | Kernel release/deployment | Rollback proof was partial. | Require rollback manifests, previous artifact selection, and post-rollback verification. | `pnpm check:release-rollback-drill` | rollback tests; pilot evidence packs | release blocked |
| PRD-011 | P1 | Kernel artifacts | SBOM policy existed in one workflow. | Enforce SBOM policy by artifact type through product-neutral release profiles and manifest validation. | kernel-release tests; `pnpm check:production-deployment-provenance` | release manifest validation | policy blocked |
| PRD-012 | P0 | Platform/Studio | Runtime telemetry was not operator-visible enough. | Keep lifecycle, interaction, release, and runtime truth evidence queryable in Studio surfaces. | Studio lifecycle and observability gates | Studio lifecycle page; runtime truth snapshots | evidence blocked |
| PRD-013 | P0 | Security/Kernel | Runtime security enforcement needed fail-closed proof. | Enforce policy, tenant, consent, purpose, and data-minimization checks in broker/release/product workflows. | broker, security, and production-readiness gates | policy tests; interaction evidence | policy blocked |
| PRD-014 | P0 | Kernel release | Product release command needed real manifest behavior. | Implement `pnpm kernel release create` and `pnpm kernel release manifest` with evidence/profile/SBOM validation. | `pnpm check:product-release-readiness`; kernel release tests | release manifests under `.kernel/out` | release blocked |
| PRD-015 | P1 | Kernel lifecycle | Affected execution needed safe scope proof. | Preserve affected-surface tests and product registry dependency proof. | `pnpm check:affected-surface-execution`; registry checks | affected-product evidence | configuration blocked |
| PRD-016 | P1 | Scripts/Studio | Scorecards needed actionability. | Keep DMOS and PHR scorecards with blocker severity, owner module, and ticket suggestions. | `pnpm check:product-feature-completeness` | `.kernel/evidence/product-scorecards` | evidence blocked |
| PRD-017 | P1 | Docs/Kernel | Docs needed executable evidence mapping. | Tie claims to evidence through doc truth, implementation ticket, and readiness gates. | `pnpm check:doc-truth`; `pnpm check:kernel-implementation-plan-coverage` | this ticket map; doc truth reports | evidence blocked |
| PRD-018 | P1 | AI/platform | AI governance needed runtime approval evidence. | Link model/tool usage to approvals, policy, audit, and release evidence where AI workflows are active. | AI governance gates; production readiness | governance evidence | policy blocked |

## 47-Dimension Coverage Map

| Dimension | Ticket | Severity | Owner | Current gap | Required implementation | Validation | Evidence | Failure classification |
|---|---|---|---|---|---|---|---|---|
| DIM-001 Vision alignment | PRD-017 | P1 | Docs/Kernel | Execution claims can drift from the vision. | Keep claims tied to evidence-backed implementation tickets. | `pnpm check:doc-truth` | tracker and ticket map | evidence blocked |
| DIM-002 Product coherence | PRD-009 | P1 | Kernel release | Product families need consistent release handling. | Use product-neutral release profiles for every product shape. | `pnpm check:production-deployment-provenance` | release profiles | release blocked |
| DIM-003 Feature completeness | PRD-001 | P0 | Product teams | Pilot workflows still need deeper proof. | Complete DMOS and PHR workflow matrices with runtime evidence. | `pnpm check:product-feature-completeness` | scorecards | product failure |
| DIM-004 End-to-end workflow completeness | PRD-001 | P0 | Product teams | Atomic workflow runtime proof is incomplete. | Add route/API/UI/persistence workflow evidence per pilot journey. | product E2E gates | workflow tests | test failure |
| DIM-005 Runtime correctness | PRD-013 | P0 | Security/Kernel | Failure-injection depth is uneven. | Keep fail-closed runtime checks in brokers and release paths. | production readiness gates | failure tests | code failure |
| DIM-006 Domain correctness | PRD-001 | P0 | Product teams | Domain golden tests need product depth. | Tie each pilot workflow to domain tests and evidence. | product workflow tests | domain tests | product failure |
| DIM-007 Data model correctness | PRD-008 | P0 | Kernel/Data Cloud | Durable lifecycle proof needs retention. | Validate data/evidence retention and deletion policy. | `pnpm check:evidence-retention-policy` | retention policy | evidence blocked |
| DIM-008 Contract correctness | PRD-006 | P0 | Kernel + products | Typed schemas need runtime compatibility proof. | Preserve broker contracts and product contract tests. | interaction contract gates | broker contracts | interaction blocked |
| DIM-009 Route/API correctness | PRD-001 | P0 | Product teams | Release execution must confirm route behavior. | Link pilot routes to APIs and tests. | product E2E suites | route tests | product failure |
| DIM-010 UI/API/runtime coherence | PRD-003 | P0 | Studio | Browser/runtime parity needs stronger proof. | Keep Studio and product pages wired to real API contracts. | Studio/product UI tests | route tests | test failure |
| DIM-011 Runtime Truth maturity | PRD-012 | P0 | Platform/Studio | Product-family parity is incomplete. | Surface runtime truth snapshots and evidence in Studio. | runtime truth checks | runtime snapshots | evidence blocked |
| DIM-012 Security | PRD-013 | P0 | Security/Kernel | Runtime abuse proof needs depth. | Enforce authz/policy failures in product interactions and releases. | security and broker gates | policy tests | policy blocked |
| DIM-013 Privacy | PRD-013 | P0 | Security/Kernel | Data-subject and redaction proof need depth. | Enforce consent, purpose, and no-secret evidence checks. | evidence and broker gates | consent tests | policy blocked |
| DIM-014 Tenant isolation | PRD-013 | P0 | Security/Kernel | Cross-product runtime proof needs depth. | Preserve tenant-aware broker and product policy checks. | cross-product flow gates | interaction evidence | interaction blocked |
| DIM-015 Authorization/RBAC/ABAC/scope | PRD-013 | P0 | Security/Kernel | Full matrix E2E is incomplete. | Keep fail-closed role/scope checks on broker and release workflows. | security gates | policy tests | policy blocked |
| DIM-016 Governance/policy/compliance | PRD-018 | P1 | AI/platform | Runtime policy evidence needs depth. | Link governance decisions to approvals and audit evidence. | AI governance gates | governance evidence | policy blocked |
| DIM-017 Audit durability/evidence quality | PRD-008 | P0 | Kernel/Data Cloud | Atomic audit proof needs durability. | Store evidence through validated APIs and retention policies. | `pnpm check:evidence-retention-policy` | audit evidence API | evidence blocked |
| DIM-018 Event correctness | PRD-007 | P1 | Kernel/Data Cloud | Replay and DLQ proof need durability. | Maintain durable event provider behavior. | `pnpm check:interaction-durable-event-provider` | provider tests | interaction blocked |
| DIM-019 Action Plane / automation correctness | PRD-012 | P0 | Platform/Studio | Action lifecycle proof needs visibility. | Surface lifecycle run history and recovery. | lifecycle run gates | run history | evidence blocked |
| DIM-020 Implicit AI/ML maturity | PRD-018 | P1 | AI/platform | AI governance behavior needs depth. | Require approval/evidence links for AI-enabled flows. | governance tests | governance evidence | policy blocked |
| DIM-021 HITL / override control | PRD-018 | P1 | AI/platform | Takeover E2E needs proof. | Track approval and override flows in evidence. | governance tests | approval evidence | policy blocked |
| DIM-022 Observability | PRD-012 | P0 | Platform/Studio | SLO dashboards need release artifacts. | Keep runtime telemetry linked to lifecycle/release evidence. | observability gates | telemetry snapshots | evidence blocked |
| DIM-023 Reliability/resilience | PRD-010 | P0 | Kernel release/deployment | Failure injection needs rollback proof. | Validate rollback manifest and post-rollback checks. | `pnpm check:release-rollback-drill` | rollback evidence | release blocked |
| DIM-024 Error/degraded mode | PRD-003 | P0 | Studio | Operator degraded UX needs proof. | Expose explain/recover/status states in Studio. | Studio lifecycle tests | Studio route tests | code failure |
| DIM-025 Idempotency/retry/replay/rollback | PRD-007 | P1 | Kernel/Data Cloud | Replay and rollback proof need depth. | Preserve event replay, DLQ, and rollback manifest gates. | interaction and rollback gates | event and rollback tests | interaction blocked |
| DIM-026 Performance | PRD-012 | P0 | Platform/Studio | Product SLO thresholds need visibility. | Keep interaction and lifecycle performance gates executable. | `pnpm check:interaction-performance` | performance tests | test failure |
| DIM-027 Scalability | PRD-015 | P1 | Kernel lifecycle | Multi-tenant scale proof is incomplete. | Use affected-surface execution and dependency graph checks. | affected execution gates | affected evidence | configuration blocked |
| DIM-028 Extensibility/plugin model | PRD-006 | P0 | Kernel + products | Plugin lifecycle proof needs runtime checks. | Preserve broker semantics and cycle-safe interaction gates. | plugin interaction gates | broker tests | interaction blocked |
| DIM-029 Shared-library reuse | PRD-017 | P1 | Docs/Kernel | Over-sharing controls need governance. | Keep architecture boundaries and docs truth tied to evidence. | architecture gates | boundary evidence | code failure |
| DIM-030 Dependency hygiene | PRD-011 | P1 | Kernel artifacts | SBOM rollout must cover product families. | Enforce SBOM profile policy by artifact type. | release tests | SBOM checks | dependency blocked |
| DIM-031 Architecture boundaries | PRD-017 | P1 | Docs/Kernel | Runtime architecture conformance needs depth. | Preserve product/platform boundary gates. | `pnpm check:architecture-boundaries` | boundary reports | code failure |
| DIM-032 Simplicity/maintainability | PRD-003 | P0 | Studio | Complexity reduction needs UX proof. | Make Studio the lifecycle/release control plane. | Studio lifecycle tests | Studio evidence | code failure |
| DIM-033 UI/UX simplicity/consistency | PRD-003 | P0 | Studio | Route-level UX proof is uneven. | Keep lifecycle and release readiness views consistent. | Studio E2E | Studio route tests | test failure |
| DIM-034 Accessibility | PRD-003 | P0 | Studio | Behavioral assertions need depth. | Keep Studio/product a11y routes covered. | a11y tests | a11y reports | test failure |
| DIM-035 i18n/l10n readiness | PRD-002 | P0 | PHR + i18n | Missing-key proof was incomplete. | Maintain PHR locales, formatting, and pseudo-locale tests. | `pnpm check:i18n-conformance` | locale files | product failure |
| DIM-036 Testing depth | PRD-001 | P0 | Product teams | Failure injection depth needs expansion. | Tie workflows to unit, integration, and E2E gates. | product workflow tests | test reports | test failure |
| DIM-037 Test quality / no test theater | PRD-016 | P1 | Scripts/Studio | Presence checks need behavioral anchors. | Require scorecards to name blockers, owners, tickets, and validation. | `pnpm check:product-feature-completeness` | scorecards | evidence blocked |
| DIM-038 CI gate strength | PRD-014 | P0 | Kernel release | Release evidence must execute. | Make product release readiness a CI command and workflow step. | `pnpm check:product-release-readiness` | release readiness evidence | release blocked |
| DIM-039 Release readiness | PRD-009 | P1 | Kernel release | Product-family release parity needs proof. | Validate product-neutral release profiles and workflow dry run. | release readiness gate | release profiles | release blocked |
| DIM-040 Deployment/ops readiness | PRD-014 | P0 | Kernel release | Runbook execution needs proof. | Link release create/manifest to lifecycle evidence and artifacts. | product release gate | release manifests | release blocked |
| DIM-041 Backup/restore/DR | PRD-010 | P0 | Kernel release/deployment | Product-family DR scope needs proof. | Require previous artifact selection and rollback verification. | rollback gate | rollback manifests | release blocked |
| DIM-042 Config/secrets management | PRD-013 | P0 | Security/Kernel | Secret rotation proof needs depth. | Keep no-secret evidence validation and release policy checks. | production readiness | secret checks | configuration blocked |
| DIM-043 Documentation truthfulness | PRD-017 | P1 | Docs/Kernel | Release-summary truth sync needs evidence. | Map every production claim to executable gates and evidence. | doc truth and implementation coverage | ticket map | evidence blocked |
| DIM-044 Migration/deprecation hygiene | PRD-017 | P1 | Docs/Kernel | Sunset enforcement needs compatibility proof. | Preserve deprecated package/import gates. | deprecation gates | deprecation reports | code failure |
| DIM-045 Cost/operational efficiency | PRD-015 | P1 | Kernel lifecycle | Cost budgets are not mature. | Use affected execution and cache-safe scope to avoid unnecessary work. | affected-surface tests | affected evidence | configuration blocked |
| DIM-046 Overall production readiness | PRD-001 | P0 | Product teams | Runtime failure proof is incomplete. | Complete pilot workflows and production gates without target-state claims lacking evidence. | phase8 and product gates | readiness evidence | product failure |
| DIM-047 Overall world-class maturity | PRD-003 | P0 | Studio | Simplicity and behavior proof need depth. | Keep Studio, lifecycle, release, interaction, and evidence surfaces operator-ready. | world-class readiness gate | Studio and release evidence | evidence blocked |
