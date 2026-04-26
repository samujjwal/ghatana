# Data Cloud Full-Stack Remediation Progress

> Derived from `DATA_CLOUD_FULL_STACK_AUDIT_2026-04-25.md`
> Created: 2026-04-25

## P0 — Immediate (Critical)

| ID | Title | Status | Notes |
|----|-------|--------|-------|
| DC-AUD-001 | Fix readiness truthfulness — /ready fails for NOT_CONFIGURED critical deps | **DONE** | isCriticalSubsystemDown now checks NOT_CONFIGURED; deriveOverallStatus classifies NOT_CONFIGURED as DOWN; regression test added |
| DC-AUD-002 | Fix entity query contract — implement filter/search/sort/total end to end | **DONE** | EntityCrudHandler now parses offset/search/sort/filter; DefaultDataCloudClient passes them to QuerySpec; InMemoryEntityStore applies filters/sorts |
| DC-AUD-003 | Fix counts — return total not page size for entity/pipeline/alert lists | **DONE** | Entity query now returns total from store.count(), plus offset/limit/hasMore; count field retained for page size |
| DC-AUD-011 | Fix compliance summary — compare collections vs policies, expose unclassified | **DONE** | Added EntityStore.listCollections(); DataLifecycleHandler queries actual collections and computes unclassified; complianceStatus now PARTIAL when gaps exist |
| DC-AUD-013 | Fail-closed policy/audit in production | **PENDING** | DataCloudSecurityFilter skips policy/audit when null |
| DC-AUD-020 | Fix UI type-check to green | **PENDING** | pnpm --filter @data-cloud/ui type-check fails |

## P1 — Short-term (High)

| ID | Title | Status | Notes |
|----|-------|--------|-------|
| DC-AUD-004 | Pipeline list filter/search/sort implementation | **PENDING** | PipelineCheckpointHandler reads only limit |
| DC-AUD-005 | Workflow execution plugin gating | **PENDING** | Returns 503 without plugin; needs first-party engine or hard gating |
| DC-AUD-006 | Settings UI contract vs backend route mismatch | **PENDING** | UI expects /settings/keys/profile/preferences/notifications; backend only has /settings /settings/security |
| DC-AUD-007 | Settings persistence (in-memory -> persistent) | **PENDING** | SettingsHandler uses in-memory maps |
| DC-AUD-008 | AI operations client targets unrouted endpoints | **PENDING** | UI targets /ai/suggestions, /ai/correlations; backend has different routes |
| DC-AUD-010 | Fix temporal history — store full CDC payloads, replay from genesis | **PENDING** | Save event lacks data; history replays from current state |
| DC-AUD-012 | PII classification from name-convention to registry/schema scan | **PENDING** | derivePiiFields checks collection name only |
| DC-AUD-015 | HTTPDataCloudClient placeholder removal | **PENDING** | Returns null/empty/zero success values |
| DC-AUD-016 | Full-text search capability gating | **PENDING** | Returns 501 when OpenSearch absent; should gate UI |
| DC-AUD-017 | Runtime capability registry as universal truth | **PENDING** | Static route lifecycles vs runtime routes diverge |
| DC-AUD-019 | Batch mutation transactional guarantees | **PENDING** | Promise chains without rollback or per-item contract |
| DC-AUD-023 | Full CDC event envelopes for all mutations | **PENDING** | Event payloads too thin for replay/audit |

## P2 — Medium-term

| ID | Title | Status | Notes |
|----|-------|--------|-------|
| DC-AUD-009 | AI confidence heuristic labeling | **PENDING** | Alert confidence is deterministic constant |
| DC-AUD-014 | Default tenant fallback restrictions | **PENDING** | Non-strict mode falls back to "default" tenant |
| DC-AUD-018 | Alert totals, resolution closure, reason fields | **PENDING** | Count is page size; ack/resolve no reason body |
| DC-AUD-021 | Raise platform-launcher coverage gates | **PENDING** | Coverage minimums 0.26/0.20 with TODO |
| DC-AUD-022 | Mark API capabilities as live/partial/preview/unavailable | **PENDING** | Overview claims 85+ endpoints; many are optional/501/503 |
| DC-AUD-024 | Reduce in-memory fallbacks, add mode labels | **PENDING** | LOCAL uses in-memory; demo mode can masquerade |
| DC-AUD-025 | Generate UI/SDK clients from canonical OpenAPI | **PENDING** | Strategic requirement; UI clients hand-coded divergent routes |

## Surface-by-Surface Remediation

### Data Explorer / Collections / Entities
- [x] Implement canonical query contract with filter/search/sort/offset/limit/total/hasMore
- [x] Thread fields through DataCloudClient.Query, EntityStore.QuerySpec, OpenAPI, UI clients, mocks, tests
- [ ] Make collection registry first-class API or document dc_collections as system collection

### Entity History / Temporal Query
- [ ] Store complete CDC payloads or durable version snapshots
- [ ] Reconstruct from genesis forward, not current backward
- [ ] Add tests for create/update/delete over time, missing data, deleted entities, version ordering

### Pipelines / Workflows
- [ ] Pipeline list support filter/sort/search/page/total
- [ ] Add first-party execution engine or make execution unavailable until plugin installed
- [ ] Add execution lifecycle statuses, retries, cancellation, logs, progress, audit, notifications

### Query / SQL / Analytics
- [ ] Establish query broker contract: source capabilities, execution id, freshness, partial warnings, cost estimate, cancellation, trace id, explain plan

### Trust Center / Governance / Privacy
- [ ] Build real governance inventory from collections, schemas, policies, holds, audit events, redaction verification
- [ ] Fail closed or explicitly mark policy/audit as unavailable in production
- [ ] Add legal hold enforcement to purge and redact paths

### Alerts / Operations
- [ ] Alert total counts, cursor pagination
- [ ] Reason/comment fields, audit trail, action history, escalation status, incident lifecycle
- [ ] Model provenance for AI suggestions

### Settings / Admin / API Keys
- [ ] Implement persistent tenant-scoped settings, API key creation/revocation, profile, preferences, notification channels, RBAC, audit, secret one-time reveal
- [ ] Or remove/hide settings until supported

### Plugins / Capability Registry
- [ ] Runtime capability snapshots drive navigation, hub cards, global search, page tabs, CTA enablement, boundary copy
- [ ] Include source, mode, dependency, degraded reason, last probe, documentation link

### AI / ML / Context / Agents / Fabric
- [ ] Define AI operations substrate with suggestion, action, provenance, confidence band, input features, model/provider, review policy, apply/rollback, audit event
- [ ] Use behind Data, Pipelines, Trust, Alerts, Operations instead of separate AI dashboard

### Health / Observability / Readiness
- [ ] Split liveness from readiness from capability
- [ ] /live process-only; /ready fails for required missing/unknown/down/not-warmed dependencies
- [ ] /health/detail includes optional dependency status
- [ ] Add trace IDs to all error envelopes, make trace export status visible

### Security / Tenant Isolation / Policy
- [ ] Fail closed in production when policy/audit dependencies are missing
- [ ] Bind API keys to tenant, scopes, roles, expiry, rotation, audit
- [ ] Make default-tenant mode only test/local and visually bannered

### SDK / Developer Experience
- [ ] Delete or clearly mark HttpDataCloudClient unsupported, or implement real HTTP transport generated from canonical OpenAPI
- [ ] Never return success-shaped placeholders

## Quality Gates

- [ ] `pnpm --filter @data-cloud/ui type-check` passes
- [ ] `./gradlew :products:data-cloud:launcher:test` passes
- [ ] `./gradlew :products:data-cloud:platform-launcher:test` passes
- [ ] ESLint zero warnings
- [ ] Prettier formatting applied
- [ ] Test coverage minimum 80% for critical paths

---

## Implementation Log

### 2026-04-25 Session

- Created progress tracker.
- Starting P0 critical fixes:
  1. DC-AUD-001: HealthHandler readiness truthfulness
  2. DC-AUD-011: Compliance summary unclassified collections
  3. DC-AUD-002: Entity query search/filter/sort/total
  4. DC-AUD-020: UI type-check fixes

### 2026-04-25 Session (continued)

- Completed DC-AUD-001: HealthHandler.isCriticalSubsystemDown now checks NOT_CONFIGURED; deriveOverallStatus classifies NOT_CONFIGURED as DOWN; added regression test `readyReturns503WhenDatabaseProbeIsNotConfigured`.
- Completed DC-AUD-002 + DC-AUD-003: EntityCrudHandler.handleQueryEntities now parses offset/search/sort/filter, builds full DataCloudClient.Query, fetches total via EntityStore.count, and returns total/offset/limit/hasMore. DefaultDataCloudClient passes filters/sorts to QuerySpec. InMemoryEntityStore applies filters/sorts and computes total before pagination.
- Completed DC-AUD-011: Added EntityStore.listCollections() to SPI with implementations for InMemory, H2, and Postgres stores. DataLifecycleHandler.handleComplianceSummary now queries actual collections and computes collectionsTotal, collectionsClassified, collectionsUnclassified, and complianceStatus (NEEDS_CLASSIFICATION/PARTIAL/COMPLIANT).
- Updated tests: DataCloudHttpServerEntityTest (mocked entityStore.count), DataCloudHttpServerGovernanceTest (mocked listCollections), and adjusted EntityCrudHandler/EntityStore interfaces.
