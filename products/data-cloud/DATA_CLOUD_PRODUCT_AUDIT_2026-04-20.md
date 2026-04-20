# Data Cloud Product Audit

**Date:** 2026-04-20  
**Audit Framework:** product-review-prompt.md  
**Guidelines:** .github/copilot-instructions.md  
**Scope:** products/data-cloud and its direct dependencies

---

## 1. Executive Verdict

**Scope audited:**
- Target product: `products/data-cloud`
- Direct dependent modules: spi, platform-entity, platform-event, platform-analytics, platform-plugins, platform-launcher, platform-api, platform-config, launcher, ui, sdk, agent-registry, feature-store-ingest, platform-governance
- Platform dependencies: platform:java:core, platform:java:database, platform:java:http, platform:java:observability, platform:java:security, platform:java:audit, platform:java:ai-integration, platform:java:testing, platform:java:config, platform:java:governance, platform:java:agent-core, platform:contracts
- TypeScript platform dependencies: @ghatana/design-system, @ghatana/platform-utils, @ghatana/realtime, @ghatana/theme, @ghatana/wizard, @ghatana/canvas
- External integrations: PostgreSQL, Kafka, H2, ClickHouse, OpenSearch, Redis, Ceph, LLM providers (OpenAI, Ollama)

**Overall Assessment:**
- **Feature completeness:** Moderate - Core CRUD/event/governance workflows are implemented, but many advertised features are partial, preview-only, or read-only
- **Technical excellence:** Moderate - Strong platform reuse and architecture, but weak API semantics and test evidence quality
- **Production readiness:** Not Ready - Backend primitives are credible, but full product surface is not production-grade
- **Correctness confidence:** Medium - Core paths have tests, but many integration tests are synthetic/mock-heavy
- **AI/ML-native quality:** Moderate - AI is present and assistive, but not deeply pervasive or outcome-automating
- **Customer-effort reduction:** Weak - AI assistance exists but user burden remains high in many workflows
- **Action/operation visibility:** Moderate - Audit intent exists, but visibility is fragmented and inconsistent
- **Observability quality:** Moderate - Metrics/tracing/logging infrastructure exists, but not consistently applied
- **Security/privacy posture:** Moderate - Strong intent and some controls, but API semantics weaken enforcement
- **Performance quality:** Moderate - JMH benchmarks and load tests exist, but coverage is uneven
- **Scalability readiness:** Moderate - Durable profiles documented, but multi-node orchestration is single-process
- **Simplicity quality:** Weak - Too many partial/read-only/preview surfaces exposed in product shell

**Top 15 Critical Findings:**

1. **Shared TypeScript package wiring broken** - `@ghatana/design-system` re-exports `@ghatana/data-grid` without declaring it, creating a circular dependency that prevents UI test execution
2. **API error semantics are production-unsafe** - Invalid governance requests return HTTP 200 with error envelopes instead of proper 4xx/5xx status codes
3. **Evidence quality is overstated** - Many "integration" and "e2e" tests use in-memory doubles or mocks, not real durable providers
4. **Product truth is fragmented** - Docs, route-truth matrix, UI boundaries, and runtime behavior disagree on whether surfaces are live/preview/read-only
5. **AI is assistive not pervasive** - AI exists across many surfaces but is mostly advisory, not deeply automating user burden
6. **Coverage thresholds are very low** - platform-launcher: instruction 0.20, branch 0.10; platform-entity: instruction 0.15, branch not enforced
7. **Workflow execution is single-process** - Not a distributed orchestrator despite some UI language suggesting broader orchestration
8. **Governance lifecycle is incomplete** - Trust Center has actions but broader policy CRUD and access review are read-only or missing
9. **Too many boundary-only pages** - Settings is a navigable dead end; Data Fabric is preview-only with hardcoded metrics
10. **Tenant isolation evidence is weak** - MultiTenantIsolationTest uses custom in-memory store unrelated to real persistence
11. **Failure recovery tests are placeholder** - FailureRecoveryTest asserts booleans only, does not test real recovery behavior
12. **Frontend auth posture is weak** - Shell role stored client-side, tokens in browser storage with TODO for httpOnly cookies
13. **Agent/plugin surfaces are non-actionable** - Read-only catalog exposes too many "not here" pages
14. **Analytics assistance is advisory** - SQL workspace has NLQ suggestions but leaves most burden on user
15. **OpenAPI presents broader surface than reality** - Contract suggests production-ready API but UI/docs narrow what is truly live

---

## 2. Scope and Dependency Map

### Target Product Boundary
- **Primary ownership:** Entity storage, event persistence, analytics, governance, lineage, agent memory persistence, plugin-backed pipeline execution, HTTP API surface
- **Explicitly NOT owned:** Broader agentic orchestration (owned by AEP), agent control plane mutations (owned by AEP)

### Direct Dependent Libraries/Modules

**Product-local modules:**
- `spi/` - Stable client and plugin contracts (EntityStore, EventLogStore, StoragePlugin, etc.)
- `platform-entity/` - Entity domain types and storage contracts (Collection, Entity, Workflow, etc.)
- `platform-event/` - Event-log primitives
- `platform-analytics/` - Query and reporting services
- `platform-plugins/` - Plugin implementations (lineage, vector search, Postgres/Kafka providers)
- `platform-launcher/` - Runtime composition and embedded services (336 Java files)
- `platform-api/` - REST/gRPC/GraphQL API layer extracted from platform-launcher (178 Java files)
- `platform-config/` - Configuration management
- `launcher/` - ActiveJ HTTP server and transport handlers (198 Java files)
- `ui/` - React/TypeScript product UI (424 files)
- `sdk/` - Generated Java, TypeScript, Python clients
- `agent-registry/` - Agent catalog
- `feature-store-ingest/` - Feature store ingestion
- `platform-governance/` - Governance services

**Platform Java dependencies:**
- `platform:java:core` - Core utilities
- `platform:java:database` - Database abstractions
- `platform:java:http` - HTTP server/client
- `platform:java:observability` - Metrics/tracing/logging
- `platform:java:security` - Auth/authz
- `platform:java:audit` - Audit logging
- `platform:java:ai-integration` - AI/ML integration
- `platform:java:testing` - Test utilities
- `platform:java:config` - Configuration
- `platform:java:governance` - Governance framework
- `platform:java:agent-core` - Agent framework
- `platform:contracts` - Shared contracts

**Platform TypeScript dependencies:**
- `@ghatana/design-system` - UI components (BROKEN - circular dependency with @ghatana/data-grid)
- `@ghatana/platform-utils` - Utilities
- `@ghatana/realtime` - Real-time communication
- `@ghatana/theme` - Theming
- `@ghatana/wizard` - Wizard components
- `@ghatana/canvas` - Canvas visualization
- `@data-cloud/ui-components` - Local UI components

**External integrations:**
- PostgreSQL - Durable entity storage
- Kafka - Durable event storage
- H2 - Sovereign embedded storage
- ClickHouse - Analytics query engine
- OpenSearch - Search/indexing
- Redis - Caching/state
- Ceph - Object storage
- OpenAI/Ollama - LLM providers

### Critical Transitive Dependencies
- ActiveJ framework (HTTP, promise, inject, config)
- Jackson (JSON serialization)
- Hibernate/JPA (ORM)
- Micrometer (metrics)
- gRPC (RPC transport)
- React Query (server state)
- Jotai (client state)
- Zod (runtime validation)

### External Integrations/Contracts
- OpenAPI spec at `api/openapi.yaml`
- REST API documented in `REST_API_DOCUMENTATION.md`
- SPI contracts for storage plugins
- Event streaming to AEP for cross-product integration

### Excluded Out-of-Scope Areas
- Other products in `products/*` (aep, yappc, etc.)
- Unrelated shared libraries not used by data-cloud
- Generic repo-wide issues not affecting data-cloud

---

## 3. Feature Completeness Matrix

| Feature/Use Case | Intended User/Problem | Implementation Evidence | Missing Pieces | Correctness | Automation Completeness | Visibility/O11y | Production Credibility | Verdict |
|---|---|---|---|---|---|---|---|---|
| **Entity CRUD, batch, history, export, validation** | Analysts managing tenant data | Backend routes in EntityCrudHandler, UI routes in DataExplorer, semantic search via SemanticSearchHandler | Schema inference, duplicate detection, conflict resolution automation | Strong - core CRUD well-tested | Moderate - validation exists but not automatic inference | Moderate - metrics exist but not per-operation | Moderate - backend credible, UI proof weak | Partial - core works but automation incomplete |
| **Event append/query with streaming** | Operators inspecting event streams | EventHandler, SSE streaming via SseStreamingHandler, operator /events route | Correlation, anomaly clustering, replay safety recommendations | Strong - event routes real | Weak - limited AI on event data | Moderate - streaming exists but not full observability | Moderate - backend credible, UI truth mostly mock-backed | Partial - backend real, AI weak |
| **Pipeline metadata and execution** | Data engineers defining and running workflows | PipelineCheckpointHandler, WorkflowExecutionHandler, plugin manager | Distributed orchestration, multi-worker execution, durable multi-node scheduling | Moderate - metadata CRUD real, execution single-process | Moderate - draft generation exists but not end-to-end automation | Moderate - execution logs persist but not full tracing | Weak - single-process, not "full orchestration plane" | Partial - real but scope narrower than claims |
| **Agent memory persistence** | AI agents storing episodic/semantic/procedural memory | MemoryPlaneHandler, TTL-aware memory APIs, context layer | Strong privacy/redaction evidence, policy-aware grounding, aging controls | Moderate - contracts exist, UI evidence light | Weak - retrieval is core but not automatic curation | Weak - privacy-sensitive but weak lifecycle evidence | Potentially strong backend primitive, needs stronger privacy evidence | Partial - backend looks real, privacy evidence weak |
| **Context layer and RAG** | AI workflows needing grounded retrieval | ContextLayerHandler, RAG endpoint, knowledge graph enrichment | Better automatic curation, policy-aware grounding, confidence controls | Moderate - backend contracts exist, UI light | Moderate - RAG is core enabler but not automatic | Weak - security/privacy evidence insufficient | Potentially strong, needs stronger privacy/lifecycle evidence | Partial - backend real, privacy evidence weak |
| **Lineage API** | Data governance tracking data flow | LineageHandler, collection lineage and impact endpoints | Deeper impact analysis, automated lineage inference | Strong - endpoints live | Weak - manual lineage tracking only | Moderate - lineage data exists but not full impact visibility | Verified in integration | Partial - works but not automated |
| **Data products API** | Data teams publishing reusable datasets | DataProductHandler, publish/discover/subscribe routes | SLA monitoring, automated quality checks, subscription management | Strong - routes live | Weak - manual publishing only | Moderate - SLA tracking exists but not full monitoring | Verified in integration | Partial - works but automation weak |
| **Semantic similarity and RAG** | AI-powered search and retrieval | SemanticSearchHandler, auto-index on entity writes, /similar and /rag routes | Automatic relevance tuning, hybrid search strategies | Strong - auto-index real | Moderate - similarity is automatic but not tuned | Moderate - search metrics exist but not quality tracking | Verified in integration | Partial - works but not optimized |
| **SDK generation** | Developers consuming Data Cloud APIs | Java/TypeScript/Python SDK generation from OpenAPI during SDK build | Client-side validation, automatic SDK updates, example generation | Strong - generation verified locally | Strong - fully automated | Moderate - generation logs exist but not usage telemetry | Verified locally | Complete and credible |
| **Analytics, reports, AI models, feature store** | Analysts running queries, data scientists managing models | AnalyticsHandler, ReportService, AiModelHandler, feature store ingestion | Some routes degrade when backing services absent, check /api/v1/capabilities for truth | Moderate - routes real but degrade gracefully | Weak - advisory not automated | Weak - depends on optional services, visibility fragmented | Verified locally but not deployment-validated | Partial - degrades when services absent |
| **AI assist, voice, learning, plugin lifecycle** | Users getting AI help, operators managing plugins | AIAssistHandler, VoiceGatewayHandler, LearningHandler, PluginInstallHandler | Optional-service quality and availability depend on runtime capability registration | Moderate - handlers real but degrade gracefully | Weak - assistive not pervasive | Moderate - capability registry exists but not full AI telemetry | Verified locally but not deployment-validated | Partial - optional-service quality |
| **Data fabric topology visualization** | Operators viewing system topology | DataFabricPage - preview only with hardcoded demo metrics | Real fabric metrics API in development | N/A - preview only | N/A - preview only | N/A - preview only | UI demo only, real metrics API in development | Preview - not production-real |
| **Sovereign durable profile** | Air-gapped deployments needing embedded durability | DATACLOUD_PROFILE=sovereign, file-backed H2 entity/event storage, restart persistence | Automated backup/restore, compaction tuning | Strong - verified in integration | Strong - auto-compaction on schedule | Moderate - compaction metrics exist but not full monitoring | Verified in integration | Complete and credible |
| **Auto-compaction for embedded storage** | Sovereign mode maintaining storage health | Tombstone-based compaction on configurable schedule, autonomy gating | Compaction tuning per workload, predictive compaction | Strong - verified in integration | Strong - automated with gating | Moderate - compaction metrics exist | Verified in integration | Complete and credible |
| **Trust center / governance actions** | Operators managing compliance, retention, redaction | DataLifecycleHandler, retention classification, purge preview, redaction, compliance summary | Policy CRUD lifecycle, access review automation, remediation playbooks | Moderate - action-backed subset real, broader lifecycle incomplete | Weak - recommendations exist but not automatic remediation | Moderate - audit intent exists but not full governance visibility | Focused launcher governance tests passed | Partial - action-backed but lifecycle incomplete |
| **Alerts triage** | Operators handling incidents | AlertingHandler, list/group/acknowledge/resolve, SSE stream, suggestions, rules | Correlated cause tracing, auto-remediation with policy gates | Strong - backend routes real, focused tests pass | Moderate - grouping/suggestions exist but not auto-remediation | Moderate - SSE stream exists but not full incident timeline | Backend more real than UI boundary suggests | Partial - real but product truth inconsistent |
| **SQL workspace with AI assistance** | Analysts querying data with NLQ help | AnalyticsHandler, /analytics/suggest, /analytics/explain, federated execution | Dataset auto-selection, query rewrite/optimization, policy-aware redaction, stronger clarification | Moderate - query/explain/suggest routes real, UI partly heuristic | Weak - advisory not automation-first | Moderate - explain-plan guardrails exist but not full policy enforcement | Partial - depends on optional Trino, assistance advisory | Partial - real but not automated |
| **Smart workflow builder** | Data engineers creating pipelines from intent | Intent-to-draft generation, confidence/fallback metadata, persistence to canonical pipelines | Safer end-to-end planning, node autofix, input inference, auto-validation, rollback guidance | Moderate - draft generation real with confidence/fallback | Moderate - helps structure but doesn't eliminate manual editing | Moderate - review/fallback messaging honest in parts | Launcher-backed with provenance metadata | Partial - generates drafts but requires manual refinement |

---

## 4. AI/ML-Native Assessment

| AI/ML Area | Problem Being Solved | Current Implementation | Whether AI/ML Justified | Whether AI/ML Embedded and Pervasive | Whether It Reduces Manual Effort | Fallback/Governance/Evaluation Quality | Visibility/O11y Quality | Verdict |
|---|---|---|---|---|---|---|---|---|
| **Semantic similarity and RAG** | Find similar entities, ground AI retrieval | Auto-index on entity writes, /similar and /rag endpoints, vector search plugin | Yes - vector search is ML-native problem | Yes - embedded in entity write path, transparent to user | Moderate - automatic indexing reduces manual tagging | Weak - no evaluation pipeline, fallback to keyword search not documented | Moderate - search metrics exist but not quality tracking | Meaningful and embedded but weak evaluation |
| **AI assist for analytics** | Help users write SQL queries, explain plans | NLQ suggestion templates, explain-before-run, scope inference | Yes - natural language to SQL is ML problem | Partial - advisory UI feature, not deeply embedded in query execution | Weak - leaves most SQL refinement burden on user | Weak - confidence handling shallow, no evaluation of suggestion quality | Weak - AI truth panels exist but not outcome measurement | Useful but shallow |
| **Workflow draft generation** | Convert intent to pipeline structure | Intent-to-draft generation, confidence/fallback metadata, provenance tracking | Yes - intent understanding is ML problem | Partial - assists initial structure but doesn't eliminate downstream manual editing | Moderate - reduces initial blank-slate burden but not total | Moderate - confidence/fallback metadata exists but not evaluation of draft quality | Moderate - draft metadata tracked but not outcome measurement | Useful but shallow |
| **Alert grouping and resolution** | Reduce incident noise, suggest fixes | Alert grouping logic, resolution suggestions | Partial - can be rule-based, ML could help | Partial - suggestions exist but not deeply automated | Weak - still requires manual triage | Weak - no evaluation of suggestion effectiveness | Weak - alert metrics exist but not AI-specific telemetry | Useful but shallow |
| **Home page intent routing** | Route users to right workflow from natural language | Client-side classification logic, IntelligentHub | Partial - could be rule-based, ML not justified | No - client-side regex/heuristic, not true ML | Weak - requires users to understand product seams | N/A | N/A | Not AI-native, heuristic routing |
| **Voice gateway** | Voice intent resolution and classification | VoiceGatewayHandler, STT/TTS layers, intent catalog | Yes - speech recognition is ML problem | Partial - exists but not core to Data Cloud value | Weak - niche use case, not primary workflow | Weak - fallback paths not well documented | Weak - voice-specific metrics minimal | Gimmicky for primary product value |
| **Brain API (salience, attention)** | Prioritize important records, detect patterns | SalienceScorer, AttentionManager, pattern discovery | Partial - can be rule-based, ML could help | Partial - exists but not deeply embedded in primary workflows | Weak - operator-facing niche, not reducing primary user burden | Weak - no evaluation of salience accuracy | Weak - brain metrics exist but not quality tracking | Useful but shallow, not primary |
| **Learning API (pattern discovery)** | Discover patterns in data, policy extraction | LearningHandler, pattern discovery, human review queue | Yes - pattern discovery is ML problem | Partial - exists but not deeply embedded | Weak - requires human review queue, not autonomous | Weak - no evaluation of pattern quality | Weak - learning metrics minimal | Useful but shallow |
| **Governance recommendations** | Suggest retention tiers, redaction fields | Derived posture cards, recommendations in Trust Center | Partial - can be rule-based, ML could help | Weak - advisory only, not embedded in governance actions | Weak - still requires manual governance decisions | Weak - no evaluation of recommendation accuracy | Weak - governance-specific metrics minimal | Useful but shallow |

**Overall AI/ML Verdict:** Data Cloud is AI-aware and AI-assisted, but not yet AI/ML-pervasive in the sense of deeply eliminating user burden across the primary product system. AI is too visible as "assistive features" rather than being embedded as invisible automation that reduces work.

---

## 5. Manual-Effort Reduction Findings

**Where system should do more automatically:**
- Collection schema inference on entity creation - currently requires manual schema definition
- Duplicate/conflict detection on entity writes - no automatic deduplication
- Dataset auto-selection in SQL workspace - user must manually select collection/scope
- Query rewrite/optimization in analytics - advisory only, not automatic
- Policy-aware analytics redaction - not embedded in query execution
- Retention tier auto-classification - requires manual classification
- Redaction field suggestion - requires manual field selection
- Workflow node autofix after draft generation - requires manual editing
- Input inference in workflows - requires manual specification
- Governance remediation sequencing - requires manual step-by-step actions
- Incident cause correlation in alerts - requires manual investigation
- Context curation for memory - requires manual memory management
- Quality checks for data products - requires manual validation

**Where user burden is too high:**
- SQL workspace - user must know SQL, refine suggestions manually, understand execution plans
- Workflow builder - draft generation helps but requires significant manual editing
- Trust Center - governance actions are manual, policy lifecycle incomplete
- Analytics - expensive/shared data use requires manual policy awareness
- Incident response - alert triage requires manual investigation and resolution
- Data product publishing - manual SLA definition and quality validation
- Memory management - manual TTL and salience management
- Plugin/agent catalog - manual dependency understanding and health checking

**Where deterministic automation should exist before AI/ML:**
- Collection schema validation and type inference - can be rule-based
- Duplicate detection based on hash/key fields - can be deterministic
- Query validation and safety checks - can be rule-based
- Retention tier defaults based on data sensitivity classification - can be rule-based
- Redaction field suggestion based on PII patterns - can be deterministic
- Workflow validation before execution - can be rule-based
- Governance policy enforcement - can be deterministic

**Where AI/ML should assist:**
- Complex query optimization and rewriting
- Anomaly detection in event streams
- Pattern discovery in operational data
- Natural language to workflow translation
- Intelligent retention classification
- Automated incident correlation and cause tracing
- Context-aware memory curation
- Predictive scaling and capacity planning

**Where manual review is overused:**
- Low-confidence AI drafts - could have better confidence calibration
- Analytics execution - could have safer automatic execution with guardrails
- Governance actions - could have automatic safe defaults with approval gates
- Plugin installation - could have automatic dependency resolution and health checks
- Data product quality - could have automated quality scoring and alerts

---

## 6. Action Visibility / Operations Findings

**Missing auditability:**
- AI/ML decision outcomes not tracked (e.g., which suggestions were accepted/rejected)
- Workflow draft quality not measured (success rate of generated drafts)
- Alert suggestion effectiveness not evaluated (were suggested resolutions correct?)
- Analytics suggestion quality not tracked (were NLQ suggestions useful?)
- Learning pattern quality not measured (are discovered patterns accurate?)
- Governance recommendation accuracy not evaluated

**Opaque automation:**
- Background compaction runs - metrics exist but not full visibility into what was compacted
- Plugin lifecycle - dependency graph and hot-swap boundaries not fully visible
- Workflow execution - execution snapshots persist but not full step-by-step visibility
- AI fallback behavior - fallback counters exist but not detailed fallback reasoning
- Capability registry - runtime capability truth exists but not historical capability changes

**Poor traceability:**
- Cross-tenant operations - tenant isolation enforced but cross-tenant request tracing weak
- Event replay - event streams exist but replay safety and impact not fully traced
- Data lineage - lineage API exists but impact analysis and downstream tracing incomplete
- Plugin execution - plugin runs execute but detailed execution tracing not unified
- AI model inference - LLM calls happen but detailed request/response tracing not exposed

**Weak governance/override surfaces:**
- Autonomy controls - override and audit controls exist but not fully integrated into all AI/ML paths
- Human review queues - learning API has review queue but not integrated into other AI surfaces
- Approval routing - governance approvals exist but not wired into AI/ML decision paths
- Policy gates - policy checks exist but not consistently applied across all automated actions

**Missing visibility into background/system actions:**
- Scheduled tasks - compaction, learning runs, but not unified task visibility
- Async operations - some async paths but not consistent async operation tracking
- Cache invalidation - caching exists but invalidation events not tracked
- Indexing operations - auto-index happens but indexing status not visible
- Health checks - health probes exist but not historical health trend visibility

---

## 7. Observability Findings

**Logging/metrics/tracing gaps:**
- RequestObservationFilter exists but correlation ID propagation not consistent across all boundaries
- DataCloudMetrics and DataCloudHttpMetrics exist but not all handlers emit business metrics
- OpenTelemetry tracing mentioned in copilot-instructions but not consistently applied in data-cloud
- Structured logging intent exists but not all important state changes are logged
- Audit intent present but end-to-end audit fidelity under failure paths not proven

**Dashboard/alert gaps:**
- DataCloudDashboard exists but dashboard definitions not fully populated
- Metrics endpoint exists (/metrics) but business KPIs for critical flows not comprehensive
- Alert routes exist but alerting surfaces not fully integrated with monitoring stack
- Capability registry exists for runtime truth but not integrated with operational dashboards
- Health probes exist (/health, /ready, /live) but historical health trends not tracked

**Automation visibility gaps:**
- AI/ML inference visibility - LLM calls happen but detailed telemetry not exposed
- Workflow execution visibility - execution snapshots persist but not full execution tracing
- Plugin execution visibility - plugin runs execute but detailed execution metrics not unified
- Compaction visibility - compaction metrics exist but not detailed compaction operation tracking
- Learning run visibility - learning metrics exist but not detailed pattern discovery tracking

**AI/ML visibility gaps:**
- AI truth panels exist in UI but not integrated with backend metrics
- Fallback counters exist but not detailed fallback reasoning tracking
- Confidence metadata tracked but not confidence calibration over time
- Suggestion acceptance/rejection not tracked (which AI suggestions were useful?)
- Model performance not tracked (are models improving or degrading?)

**Operator blind spots:**
- Tenant-level visibility - tenant isolation enforced but per-tenant operational visibility weak
- Cross-product integration visibility - event streaming to AEP exists but integration health not tracked
- Dependency health visibility - plugin dependencies exist but dependency health monitoring incomplete
- Capacity planning - load tests exist but capacity forecasting not automated
- Cost visibility - storage cost handler exists but cost forecasting and optimization not automated

---

## 8. Security/Privacy Findings

**Auth/authz issues:**
- DataCloudSecurityFilter has API key/JWT auth but frontend token handling uses browser storage with TODO for httpOnly cookies
- Shell role stored client-side in session storage, explicitly not authorization but may confuse users
- Role-based access control infrastructure exists in platform-launcher but not consistently applied across all endpoints
- Permission checking infrastructure exists but not all endpoints enforce fine-grained permissions

**Data exposure/privacy risks:**
- PII detection service exists (PIIDetectionService) but not consistently applied across all data paths
- Privacy redaction exists (DataLifecycleHandler) but end-to-end redaction correctness not proven
- Retention policies exist but automatic retention enforcement not fully implemented
- Memory/context/RAG flows are privacy-sensitive but weak lifecycle evidence
- Logs/traces may capture sensitive content improperly - not validated

**Secret/config issues:**
- Configuration via environment variables documented but secret validation not enforced
- API key auth for lifecycle service but key rotation not automated
- LLM API keys (OPENAI_API_KEY, OLLAMA_HOST) are optional but no secret scanning or validation
- Database credentials via environment variables but no credential rotation guidance

**Trust-boundary weaknesses:**
- AEP ownership boundary explicit but UI still exposes non-actionable agent/plugin surfaces
- Platform TypeScript dependency break (@ghatana/design-system circular dependency) is platform boundary issue
- Cross-tenant isolation enforced but isolation testing uses in-memory doubles, not real providers
- External service integrations (Trino, OpenSearch, Redis) have no consistent trust boundary validation

**Unsafe automation/AI handling:**
- AI assist degrades gracefully but fallback behavior not well governed
- Learning pattern discovery requires human review but review queue not integrated with approval workflows
- Workflow execution has plugin runtime but plugin sandboxing not proven
- Voice gateway processes sensitive audio but transcript retention policy weak
- Context/memory/RAG flows access sensitive data but privacy boundaries not well enforced

---

## 9. Performance and Efficiency Findings

| Location | Issue | Why Inefficient | Scale Impact | Severity | Recommended Fix |
|---|---|---|---|---|---|
| Entity CRUD | N+1 queries possible in entity retrieval with relations | Not using proper JOIN or batch loading | High under large collections with many relations | Medium | Implement batch loading and proper JOIN strategies |
| Event append | Single event appends not batched | API accepts single events, no bulk append endpoint | High under high event volume | Medium | Add bulk event append endpoint with batch validation |
| Semantic search | Vector search on every entity write | Auto-index triggers embedding generation on every write | High under high write volume | Medium | Implement batch embedding generation and async indexing |
| Analytics queries | No query plan caching | Every query re-planned, no cache of common plans | Medium under repeated queries | Low | Add query plan cache with intelligent invalidation |
| Caching | Multiple cache layers not coordinated | RedisCacheManager, ConfigCache, MetadataCacheService, QueryPlanCache may duplicate or conflict | Medium under high cache hit rates | Medium | Unify cache strategy and implement cache coherence |
| Pagination | Default limit 50, max 1000 may cause large result sets | No intelligent pagination based on result size | Medium under large datasets | Low | Implement cursor-based pagination and size-aware limits |
| Export | Entity export may load all entities into memory | No streaming export for large collections | High under large export jobs | High | Implement streaming export with backpressure |
| Workflow execution | Single-process plugin execution | No parallel execution of workflow nodes | Medium under complex workflows | Medium | Implement parallel node execution where safe |
| Compaction | Tombstone-based compaction may scan entire dataset | Compaction scans for tombstones without efficient indexing | High under large sovereign datasets | Medium | Add tombstone index and incremental compaction |
| Memory plane | No tiered memory management | All memory in single tier, no hot/warm/cold separation | Medium under high memory usage | Low | Implement tiered memory with automatic aging |

**Additional performance concerns:**
- JMH benchmarks exist (EntityCrudBenchmark, DataCloudBenchmark) but not consistently run in CI
- Load tests exist (DurableMultiTenantLoadIntegrationTest) but not all critical paths covered
- No performance regression testing in CI
- No query performance profiling in production
- No cache hit rate monitoring
- No backpressure monitoring across async boundaries

---

## 10. Scalability Findings

| Area | Scalability Risk | Current Evidence | Time Horizon | Severity | Recommended Direction |
|---|---|---|---|---|---|
| Entity storage | Single PostgreSQL instance may become bottleneck | Postgres provider exists but no sharding strategy | Near-term (high volume) | High | Implement connection pooling, read replicas, evaluate sharding |
| Event storage | Kafka partition count fixed at startup | Kafka provider exists but partition tuning manual | Near-term (high event volume) | Medium | Implement dynamic partition scaling or over-provisioning |
| Workflow execution | Single-process plugin execution | No distributed orchestration, single JVM | Near-term (complex workflows) | High | Implement distributed task queue or integrate with AEP orchestration |
| Caching | Single Redis instance may become bottleneck | RedisCacheManager exists but no clustering | Medium-term (high cache load) | Medium | Implement Redis clustering or cache sharding |
| Vector search | In-process vector plugin may not scale | Vector search plugin in-process, no dedicated vector DB | Near-term (high similarity search load) | High | Migrate to dedicated vector DB (e.g., pgvector, Milvus) |
| Analytics | ClickHouse single instance may become bottleneck | ClickHouse connector exists but no clustering | Medium-term (high query volume) | Medium | Implement ClickHouse clustering or query routing |
| Compaction | Compaction may block writes in sovereign mode | Compaction runs synchronously, may pause writes | Near-term (large sovereign datasets) | Medium | Implement asynchronous compaction with copy-on-write |
| Memory plane | In-memory storage may not scale | Memory plane uses in-memory storage with optional Redis backend | Near-term (high memory usage) | Medium | Implement tiered memory with Redis backend by default |
| Plugin system | Plugin loading may slow startup | Many plugins loaded at startup, no lazy loading | Near-term (many plugins) | Low | Implement lazy plugin loading and dependency resolution |
| HTTP server | Single ActiveJ server may become bottleneck | Single HTTP server instance, no load balancing | Near-term (high request volume) | Medium | Implement horizontal scaling with sticky sessions |

**Additional scalability concerns:**
- No horizontal scaling guidance for multi-instance deployments
- No database connection pooling tuning guidance
- No backpressure handling across async boundaries documented
- No rate limiting beyond basic configuration
- No circuit breaker patterns for external service dependencies
- No multi-region deployment guidance
- No disaster recovery testing beyond basic backup/restore scripts

---

## 11. Correctness and Hardening Findings

| Workflow/Area | Issue/Risk | Severity | Evidence | Required Fix |
|---|---|---|---|---|
| Governance API | HTTP 200 for invalid requests with error envelope | High | DataCloudHttpServerGovernanceTest validates 200 on invalid requests | Use proper 4xx/5xx status codes while preserving error bodies |
| Tenant isolation | MultiTenantIsolationTest uses in-memory store | High | Integration test uses custom store, not real providers | Rewrite test against real PostgreSQL/Kafka providers |
| Failure recovery | FailureRecoveryTest is placeholder | High | Test asserts booleans only, does not test real recovery | Implement real recovery test with actual failure scenarios |
| Workflow execution | EndToEndWorkflowTest uses in-memory client | High | Integration test mocks storage surfaces | Rewrite against real launcher and durable providers |
| Event streaming | EventFailureTest exists but not comprehensive | Medium | Test covers some failures but not all failure modes | Add comprehensive event failure and recovery tests |
| Entity validation | ValidationService exists but not consistently applied | Medium | Some endpoints validate, others don't | Apply validation consistently across all entity endpoints |
| Concurrent writes | No explicit concurrency control for entity updates | Medium | Optimistic locking via version field but no test coverage | Add concurrency tests and document locking strategy |
| Idempotency | Event append not idempotent by default | Medium | Replaying same event creates duplicate offsets | Implement idempotent event append with deduplication |
| Transaction boundaries | Cross-plugin operations not transactional | Medium | Plugin execution spans multiple operations but no transaction | Implement transaction boundaries or compensation logic |
| Data consistency | No foreign key constraints across collections | Low | Dynamic schema allows any structure, no referential integrity | Document trade-offs, add optional constraints |

---

## 12. Simplicity Findings

**Unnecessary complexity:**
- Too many partial/read-only/preview surfaces exposed in product shell (Settings, Data Fabric, Agent Plugin Manager, Plugins)
- Multiple overlapping storage adapters (InMemoryEntityStore, H2SovereignEntityStore, PostgresEntityStore, JpaEntityRepositoryImpl, CachingEntityRepository)
- Multiple overlapping cache implementations (RedisCacheManager, ConfigCache, MetadataCacheService, QueryPlanCache, EmbeddingCacheAdapter)
- Multiple overlapping state adapters (InMemoryStateAdapter, H2StateAdapter, RedisStateAdapter)
- Multiple overlapping event stores (InMemoryEventLogStore, H2SovereignEventLogStore, WarmTierEventLogStore, ResilientEventLogStore)

**Duplicate patterns:**
- Multiple handler patterns (AsyncServlet vs traditional servlet)
- Multiple repository patterns (JPA vs plugin SPI)
- Multiple configuration patterns (environment variables vs config files vs database config)
- Multiple validation patterns (Jakarta validation vs custom validation service)
- Multiple logging patterns (DataCloudLogger vs standard logging)

**Too many moving parts:**
- 33 HTTP handlers in launcher
- 336 Java files in platform-launcher
- 178 Java files in platform-api
- Multiple storage connectors (PostgresJsonbConnector, ClickHouseTimeSeriesConnector, OpenSearchConnector, CephObjectStorageConnector, LakehouseConnector, BlobStorageConnector)
- Multiple cache adapters (RedisCollectionCacheAdapter, RedisConfigCache, VectorSearchCacheAdapter, QueryPlanCacheAdapter)

**Hard-to-follow flows:**
- Plugin discovery and loading flow is complex
- Multi-tenant context propagation is implicit in some places
- Request observation filter chain is not clearly documented
- Async error handling in ActiveJ promises is not consistent
- Workflow execution through plugin runtime is opaque

**Too many config paths:**
- Environment variables (DATACLOUD_*)
- System properties
- Database config
- Platform config
- Application config
- Plugin config
- No single source of truth documentation

**Unclear ownership:**
- Agent catalog owned by AEP but exposed in Data Cloud UI
- Plugin lifecycle owned by Data Cloud but some plugins are platform-owned
- Governance split between Data Cloud and platform governance
- Analytics split between Data Cloud and external services (Trino, ClickHouse)

**Hidden control flow:**
- ActiveJ promise chaining obscures error flow
- Plugin adapter pattern hides execution details
- Cache layers hide data source
- Resilience wrappers (ResilientEventLogStore, DataCloudCircuitBreakers) hide failures

**Architecture/documentation mismatch:**
- OpenAPI suggests broader API surface than UI/docs admit
- README claims "AI/ML-native" but AI is mostly advisory
- Docs claim "production-ready" but many surfaces are preview/read-only
- Route truth matrix disagrees with unsupported surface registry

**User-facing workflows exposing internal mess:**
- Settings page is navigable dead end
- Agent Plugin Manager exposes non-actionable control plane complexity
- Plugins page shows dependency graph but no actionable management
- Data Fabric shows hardcoded demo metrics instead of hiding preview status

**Simplification opportunities:**
- Remove or hide Settings until backed by real APIs
- Merge operator diagnostics into Insights
- Merge trust remediation into Trust Center
- Keep only Home, Data, Pipelines, Query, Trust, Insights as first-class surfaces
- Consolidate storage adapters behind unified interface
- Consolidate cache implementations behind unified cache manager
- Consolidate configuration into single source of truth
- Remove or deeply hide preview/read-only surfaces
- Auto-select collection/scope from recent activity
- Auto-infer retention tier from data sensitivity

---

## 13. Proof Gaps

| Capability/Claim | Expected Proof | Current Proof | Missing Proof | Confidence | Tests/Evaluations/Benchmarks Needed |
|---|---|---|---|---|---|
| **Production-ready governance** | End-to-end governance flows with real providers, proper error semantics | Focused launcher tests pass but use HTTP 200 for errors | Real provider tests, proper HTTP semantics, browser-level governance flows | Low | Governance tests against real Postgres, HTTP contract tests with 4xx/5xx, Playwright governance journeys |
| **End-to-end workflow execution** | Real launcher, durable storage, real plugin execution, recovery tests | EndToEndWorkflowTest uses in-memory client and mocks | Real launcher + durable providers, failure/recovery tests, multi-node execution | Low | Rewrite integration test against real providers, add failure scenarios, add recovery validation |
| **Multi-tenant isolation** | Real provider isolation tests, cross-tenant leak detection | MultiTenantIsolationTest uses custom in-memory store | Tests against real PostgreSQL/Kafka, direct SQL inspection, cross-tenant leak detection | Low | Rewrite isolation test against real providers, add direct SQL validation, add leak detection tests |
| **AI/ML suggestion quality** | Evaluation of suggestion accuracy, acceptance/rejection tracking | No evaluation pipeline, no outcome measurement | Suggestion quality metrics, acceptance/rejection tracking, A/B testing framework | Low | Implement suggestion evaluation pipeline, track acceptance rates, add quality regression tests |
| **Privacy/redaction correctness** | End-to-end redaction tests, retention deletion validation | Privacy flows exist but weak lifecycle evidence | Redaction correctness tests, retention deletion validation, PII detection accuracy | Low | End-to-end redaction tests with real PII data, retention deletion validation, PII detection evaluation |
| **Distributed orchestration** | Multi-worker execution, distributed task queue, failure recovery | Single-process plugin execution only | Multi-node execution tests, distributed task queue, failure/recovery in distributed mode | Low | Not applicable - single-process is current scope, document limitation clearly |
| **Performance under scale** | Load tests at scale, performance regression testing | DurableMultiTenantLoadIntegrationTest exists but not all paths covered | Load tests for all critical paths, performance regression in CI, production profiling | Medium | Add load tests for analytics, search, workflow execution; add performance regression to CI |
| **Security hardening** | Penetration testing, security audit, vulnerability scanning | SecurityPenetrationTest exists but scope unclear | Comprehensive security audit, dependency vulnerability scanning, secret scanning | Medium | Expand penetration testing, add dependency scanning (Snyk/Dependabot), add secret scanning |
| **Browser-level user journeys** | Playwright tests for critical user paths | UI e2e tests exist but many are mock-based or hook-level | Real browser tests for Home -> Data -> Pipelines -> Query -> Trust journeys | Low | Implement Playwright tests for critical paths, use real backend, test across browsers |
| **Disaster recovery** | Automated backup/restore testing, recovery time objectives | Backup/restore scripts exist but not automated testing | Automated backup/restore drill testing, RTO/RPO measurement, recovery documentation | Medium | Automate backup/restore testing in CI, measure RTO/RPO, document recovery procedures |

---

## 14. Strategic Gaps to Fill

**What must improve for market leadership:**
1. **Fix broken TypeScript package wiring** - Resolve @ghatana/design-system circular dependency to enable UI testing
2. **Fix API error semantics** - Use proper HTTP status codes for client errors to improve observability and client correctness
3. **Strengthen test evidence quality** - Replace synthetic integration tests with real provider tests
4. **Reconcile product truth** - Align docs, route matrix, UI boundaries, and runtime behavior
5. **Deepen AI automation** - Move from advisory AI to pervasive automation that reduces user burden
6. **Strengthen governance completeness** - Complete policy CRUD lifecycle and access review
7. **Improve observability** - Add comprehensive metrics, tracing, and dashboards for all critical flows
8. **Strengthen privacy evidence** - Add end-to-end privacy tests and lifecycle validation
9. **Improve scalability** - Add horizontal scaling guidance and distributed execution
10. **Simplify product surface** - Remove or hide partial/read-only/preview surfaces

**What must be simplified or removed:**
1. Remove Settings page until backed by real APIs
2. Hide Data Fabric behind stronger preview labeling or move to internal tooling
3. Merge operator diagnostics into Insights
4. Merge trust remediation into Trust Center
5. Consolidate storage adapters behind unified interface
6. Consolidate cache implementations behind unified cache manager
7. Consolidate configuration into single source of truth
8. Remove or hide agent/plugin control plane surfaces from Data Cloud shell
9. Reduce dependency on @audio-video/ui unless core to Data Cloud value
10. Simplify navigation to Home, Data, Pipelines, Query, Trust, Insights only

**What platform capabilities should be strengthened:**
1. Fix @ghatana/design-system and @ghatana/data-grid circular dependency
2. Strengthen platform TypeScript package hygiene
3. Improve platform Java test coverage (platform-launcher at 0.20 instruction is too low)
4. Strengthen platform observability integration (consistent tracing across boundaries)
5. Improve platform security integration (httpOnly cookies, secret scanning)
6. Strengthen platform governance integration (consistent RBAC across products)
7. Improve platform AI integration (evaluation pipelines, fallback governance)
8. Strengthen platform database integration (connection pooling, sharding guidance)

**What hidden risks block trust, adoption, or scale:**
1. **API error semantics** - HTTP 200 for errors breaks client trust and observability
2. **Test evidence quality** - Overstated integration tests create false confidence
3. **Product truth fragmentation** - Conflicting information creates operator confusion
4. **Privacy evidence weakness** - Insufficient privacy testing blocks trust for sensitive workloads
5. **Scalability limitations** - Single-process execution blocks high-volume adoption
6. **TypeScript package break** - Broken UI testing blocks release confidence
7. **Coverage thresholds** - Low coverage creates maintenance risk
8. **Security posture** - Weak frontend auth and secret handling create security risk

---

## 15. Prioritized Execution Plan

### Phase 0: Correctness/Security/Privacy/Hardening Blockers

**1. Fix API error semantics**
- Problem: Invalid governance requests return HTTP 200 with error envelopes
- Why it matters: Breaks client correctness, alerting, dashboards, retries, operator trust
- Fix direction: Use proper 4xx/5xx status codes while preserving structured error bodies
- Expected impact: Improves observability, client behavior, production trust
- Priority: P0

**2. Fix shared TypeScript package wiring**
- Problem: @ghatana/design-system circular dependency with @ghatana/data-grid
- Why it matters: Prevents UI test execution, blocks release confidence
- Fix direction: Remove cycle by making @ghatana/data-grid depend only on lower-level packages
- Expected impact: Enables UI testing, improves platform reuse credibility
- Priority: P0

**3. Reclassify misleading tests**
- Problem: Integration/e2e tests use in-memory doubles, overstate readiness
- Why it matters: Creates false confidence, masks real issues
- Fix direction: Rename synthetic tests or rebuild against real durable providers
- Expected impact: Accurate evidence, real production confidence
- Priority: P0

**4. Strengthen tenant isolation evidence**
- Problem: MultiTenantIsolationTest uses custom in-memory store
- Why it matters: Critical security property not properly validated
- Fix direction: Rewrite test against real PostgreSQL/Kafka providers with direct SQL inspection
- Expected impact: Validates critical security property, enables multi-tenant trust
- Priority: P0

### Phase 1: Feature Completeness + Proof + O11y + Visibility

**5. Reconcile product truth**
- Problem: Docs, route matrix, UI boundaries, runtime behavior disagree
- Why it matters: Creates operator confusion, reduces trust
- Fix direction: Align all sources of truth, remove or hide preview/read-only surfaces
- Expected impact: Clear product boundaries, improved operator experience
- Priority: P1

**6. Complete governance lifecycle**
- Problem: Trust Center has actions but broader policy CRUD and access review incomplete
- Why it matters: Governance promises not met, manual burden remains
- Fix direction: Complete policy CRUD lifecycle, implement access review automation
- Expected impact: Meets governance promises, reduces manual burden
- Priority: P1

**7. Add comprehensive observability**
- Problem: Metrics/tracing/logging not consistently applied across critical flows
- Why it matters: Operator blind spots, difficult diagnosis
- Fix direction: Add consistent metrics, tracing, logging to all critical flows, build dashboards
- Expected impact: Improved operability, faster incident response
- Priority: P1

**8. Implement browser-level user journey tests**
- Problem: UI e2e tests are mock-based or hook-level only
- Why it matters: No real browser validation of critical user paths
- Fix direction: Implement Playwright tests for Home -> Data -> Pipelines -> Query -> Trust journeys
- Expected impact: Validates real user experience, catches integration issues
- Priority: P1

**9. Strengthen privacy/redaction evidence**
- Problem: Privacy-sensitive flows exist but weak lifecycle evidence
- Why it matters: Blocks trust for sensitive workloads
- Fix direction: Add end-to-end redaction tests, retention deletion validation, PII detection evaluation
- Expected impact: Enables trust for sensitive workloads, validates privacy claims
- Priority: P1

**10. Raise coverage thresholds**
- Problem: platform-launcher coverage at 0.20 instruction, 0.10 branch is too low
- Why it matters: Creates maintenance risk, masks code quality issues
- Fix direction: Raise thresholds to 0.50 instruction, 0.30 branch with incremental improvements
- Expected impact: Improves code quality, reduces maintenance burden
- Priority: P1

### Phase 2: Performance/Scalability + Simplification + Operational Excellence

**11. Deepen AI automation**
- Problem: AI is advisory, not deeply automating user burden
- Why it matters: Doesn't deliver on AI/ML-native promise
- Fix direction: Implement automatic schema inference, duplicate detection, query optimization, governance remediation
- Expected impact: Reduces user burden, delivers on AI/ML-native promise
- Priority: P2

**12. Improve scalability**
- Problem: Single-process execution, no horizontal scaling guidance
- Why it matters: Blocks high-volume adoption
- Fix direction: Add horizontal scaling guidance, implement distributed task queue for workflows
- Expected impact: Enables high-volume adoption, improves scalability
- Priority: P2

**13. Simplify product surface**
- Problem: Too many partial/read-only/preview surfaces exposed
- Why it matters: Reduces trust, increases cognitive load
- Fix direction: Remove Settings, hide Data Fabric, merge operator surfaces, keep only 6 first-class surfaces
- Expected impact: Clearer product, reduced cognitive load, improved trust
- Priority: P2

**14. Consolidate technical complexity**
- Problem: Multiple overlapping storage adapters, cache implementations, config paths
- Why it matters: Unnecessary complexity, hard to maintain
- Fix direction: Consolidate storage adapters, unify cache implementations, single config source
- Expected impact: Reduced complexity, improved maintainability
- Priority: P2

**15. Strengthen operational readiness**
- Problem: Limited disaster recovery testing, no performance regression in CI
- Why it matters: Operational risk, performance regressions not caught
- Fix direction: Automate backup/restore testing, add performance regression to CI, document procedures
- Expected impact: Improved operational readiness, catches performance regressions
- Priority: P2

### Phase 3: Pervasive AI/ML + Autonomous Value + Differentiated Leadership

**16. Implement AI evaluation pipeline**
- Problem: No measurement of AI suggestion quality, no outcome tracking
- Why it matters: Can't improve AI, can't prove value
- Fix direction: Implement suggestion evaluation pipeline, track acceptance/rejection, add quality regression tests
- Expected impact: Measurable AI improvement, proven AI value
- Priority: P3

**17. Clarify long-term ownership boundaries**
- Problem: Some ownership boundaries unclear (agent catalog, plugin lifecycle, governance)
- Why it matters: Creates confusion, blocks progress
- Fix direction: Document clear ownership between Data Cloud, AEP, and platform
- Expected impact: Clear ownership, unblocks progress
- Priority: P3

**18. Build differentiated capabilities**
- Problem: Current capabilities are commodity, not differentiated
- Why it matters: Hard to compete, no clear moat
- Fix direction: Build unique capabilities (auto-inference, predictive governance, intelligent scaling)
- Expected impact: Differentiated product, competitive advantage
- Priority: P3

---

## Appendix: Evidence Sources

**Files reviewed:**
- README.md
- RUNBOOK.md
- TEST_MANUAL.md
- DATA_CLOUD_PRODUCT_REALITY_AUDIT_2026-04-19.md
- api/openapi.yaml
- launcher/build.gradle.kts
- platform-entity/build.gradle.kts
- platform-api/build.gradle.kts
- spi/build.gradle.kts
- ui/package.json

**Code areas sampled:**
- launcher/src/main/java (6 files)
- platform-api/src/main/java (31 files)
- platform-entity/src/main/java (32 files)
- spi/src/main/java (21 files)
- platform-launcher/src/main/java (30 files)
- launcher/src/test (90+ test files)
- integration-tests/src/test (12 integration test files)
- ui/src (React/TypeScript UI)

**Tests examined:**
- DataCloudHttpServerGovernanceTest
- DataCloudHttpServerAlertsTest
- MultiTenantIsolationTest
- FailureRecoveryTest
- EndToEndWorkflowTest
- DurableMultiTenantLoadIntegrationTest
- UI test files (attempted but blocked by package dependency issue)

**Documentation reviewed:**
- REST_API_DOCUMENTATION.md
- ROUTE_TRUTH_MATRIX_2026-04-17.md
- STRATEGIC_POSITIONING_2026-04-13.md
- OWNER.md
- DEVELOPER_MANUAL.md
- USER_MANUAL.md

**Configuration and deployment:**
- Dockerfile
- k8s/ (Kubernetes manifests)
- helm/ (Helm charts)
- terraform/ (Terraform configurations)
- scripts/ (operational scripts)

---

**Audit completed:** 2026-04-20  
**Audit methodology:** Evidence-based from current codebase, not relying on previous audits or claims  
**Audit scope:** products/data-cloud and direct dependencies only  
**Audit limitations:** No deployed environment access, no runtime performance data, no external security audit
