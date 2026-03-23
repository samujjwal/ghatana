# Architecture Review Report — Project Siddhanta
## Comprehensive Cross-Document Analysis

**Reviewer**: Architecture Review Board  
**Date**: March 6, 2026  
**Documents Reviewed**: 6 (Master Index + 5 Specification Parts)  
**Total Lines Analyzed**: ~8,735  
**Issues Found**: 67  
**Critical**: 8 | **High**: 19 | **Medium**: 24 | **Low**: 16

**Historical Note (March 8, 2026):** This report is a point-in-time review snapshot. Some findings documented here were later resolved in subsequent hardening passes; use the current architecture, epic, and LLD documents as the canonical baseline.

---

## Table of Contents

1. [Document Summaries](#1-document-summaries)
2. [Issue Catalogue](#2-issue-catalogue)
   - [2.1 Consistency Issues](#21-consistency-issues)
   - [2.2 Expressiveness Gaps](#22-expressiveness-gaps)
   - [2.3 Extensibility Concerns](#23-extensibility-concerns)
   - [2.4 Scalability Risks](#24-scalability-risks)
   - [2.5 Real-World Problem Coverage](#25-real-world-problem-coverage)
   - [2.6 Future-Proofing Gaps](#26-future-proofing-gaps)
   - [2.7 Platform / Content-Pack Model](#27-platform--content-pack-model)
3. [Missing Topics](#3-missing-topics)
4. [Summary Matrix](#4-summary-matrix)
5. [Recommended Remediation Priority](#5-recommended-remediation-priority)

---

## 1. Document Summaries

### 1.1 ARCHITECTURE_AND_DESIGN_SPECIFICATION.md (Master Index — 730 lines)
Master navigation document. Covers: key architectural decisions (10 ADRs), technology stack summary, implementation roadmap (4 phases, 14 months), future-proofing roadmap (near/medium/long-term), KPIs, compliance/regulatory coverage (Nepal primary, India secondary, MiFID II/Dodd-Frank/FATF international), security framework (7-layer defense-in-depth), DR/BCP, testing strategy, monitoring/alerting, glossary, and epic coverage map (42 epics across 8 layers). Declares document version **2.1**.

### 1.2 ARCHITECTURE_SPEC_PART_1_SECTIONS_1-3.md (1,403 lines)
**Section 1 (Executive Summary)**: Project vision as jurisdiction-neutral capital markets OS. Nepal first instantiation. 10 strategic objectives. Core capabilities across 5 areas. Technology stack table. 14 architectural principles (7 non-negotiable invariants + 7 design principles). Key SLAs/metrics.  
**Section 2 (Layered Architecture)**: 7-layer architecture (Presentation → API Gateway → Application Services → Domain Services → Integration & Messaging → Data Persistence → Infrastructure). Detailed component specs, code examples (K8s deployment, SQL schemas, TypeScript domain models). 8 bounded contexts defined.  
**Section 3 (Event-Driven & CQRS)**: Event sourcing implementation with PostgreSQL event store. CQRS read/write separation with projections. Snapshot strategy (every 100 events). Avro schema registry with Confluent. Event upcasting for versioning. Idempotent event handling.  
Document version **2.0**.

### 1.3 ARCHITECTURE_SPEC_PART_1_SECTIONS_4-5.md (1,723 lines)
**Section 4 (Configuration Resolution)**: Dual hierarchy — Infrastructure precedence (6 levels) and Business configuration (5 levels per LLD K-02: GLOBAL→JURISDICTION→TENANT→USER→SESSION). Vault integration for secrets. Feature flag service with rollout % and targeting. Config versioning and rollback. K8s ConfigMap watcher.  
**Section 5 (Plugin Runtime)**: T1/T2/T3 content pack taxonomy — the cornerstone of jurisdiction-neutral operation. Plugin manifest spec (`plugin.json`), Plugin SDK interfaces, example validator plugin. Plugin loader with dependency resolution. Sandbox using Node.js VM. Plugin lifecycle (INSTALLED→ENABLED→RUNNING→DISABLED→UNINSTALLED). Plugin registry in PostgreSQL. Inter-plugin event bus.  
Document version **2.0**.

### 1.4 ARCHITECTURE_SPEC_PART_2_SECTIONS_6-8.md (1,728 lines)
**Section 6 (AI Governance)**: 8 AI use cases. MLflow model registry. Training pipeline (sklearn/xgboost/neural). Feast feature store. FastAPI inference service with SHAP explainability. KS-test drift detection. Governance policies with risk levels and approval workflows.  
**Section 7 (Data Architecture)**: Polyglot persistence (PostgreSQL, TimescaleDB, MongoDB, Redis, Elasticsearch, Neo4j optional, S3+Athena). Full SQL schemas with dual-calendar (BS) columns and tenant RLS. TimescaleDB hypertables with continuous aggregates. MongoDB schemas for corporate actions and regulatory filings. Redis cache patterns. S3 data lake with Airflow ETL.  
**Section 8 (Deployment)**: Multi-cloud K8s (EKS/AKS/GKE). EKS cluster config with managed node groups. Full order-service deployment YAML with HPA. Istio virtual service + destination rule with canary weights. GitLab CI pipeline (build→test→security-scan→deploy-staging→integration-test→deploy-production).  
Document version **2.0**.

### 1.5 ARCHITECTURE_SPEC_PART_2_SECTIONS_9-10.md (1,578 lines)
**Section 9 (Security)**: 7-layer security stack. OAuth 2.0 + JWT auth with HS256. RBAC with role inheritance and wildcard patterns. TOTP-based MFA with backup codes. AES-256-GCM field-level encryption. Istio mTLS (STRICT mode). Express rate limiting (global + API + order-specific). Input validation (Joi). Security monitor with regex-based threat detection. Audit logging with dual-calendar timestamps and Elasticsearch.  
**Section 10 (Observability)**: Three pillars — Metrics (Prometheus with custom business metrics), Traces (OpenTelemetry + Jaeger), Logs (Winston + ELK). Prometheus scrape config and alert rules. Grafana dashboard JSON. Alertmanager config with PagerDuty and Slack routing.  
Document version **2.0**.

### 1.6 ARCHITECTURE_SPEC_PART_3_SECTIONS_11-15.md (1,573 lines)
**Section 11 (Performance Optimization)**: Revised performance targets (distinct from master index). Multi-level caching (L1 in-memory / L2 Redis / L3 DB). Database optimization (covering indexes, partial indexes, materialized views, connection pooling). Bull job queues for async processing. NGINX load balancing. K8s HPA with custom metrics.  
**Section 12 (Compliance & Regulatory)**: Jurisdiction-configurable compliance monitoring. In-code compliance engine with rule registration. Automated regulatory reporting service. GDPR implementation (right to access, right to erasure with 10-year retention check).  
**Section 13 (Future-Safe Validation)**: API versioning (header or URL path). Backward compatibility adapter pattern. Zero-downtime DB migrations (Knex.js). Advanced feature flags with date ranges and dependencies. Dependabot for automated dependency updates.  
**Section 14 (Requirements Traceability Matrix)**: Requirement-to-Epic-to-Component-to-Implementation-to-Test mapping (18 requirements traced). Automated coverage tracker.  
**Section 15 (Risks & Mitigation)**: 15 risks across Technical (5), Business (5), Operational (5). Risk scoring system (probability × impact). Incident management with post-mortem workflow.  
Document version **2.0**.

---

## 2. Issue Catalogue

### 2.1 Consistency Issues

| # | ID | Severity | Location | Issue | Detail |
|---|-----|----------|----------|-------|--------|
| 1 | **CON-01** | **CRITICAL** | Master Index L247 vs Part 3 §11.2 L32 | **Order latency SLA contradicts across documents** | Master Index claims "Order Latency (p99): < 1ms". Part 1 §1.6 repeats "< 1ms (p99)". But Part 3 §11.2 specifies "≤ 2ms internal / ≤ 12ms e2e (see LLD D-01)". The Prometheus alert rule in Part 2 §10 alerts at >12ms e2e, confirming the Part 3 figure. The <1ms headline claim is physically implausible for an end-to-end path through API Gateway → validation → risk check → Kafka publish → exchange. **Recommendation**: Adopt the Part 3 / LLD D-01 values (≤2ms internal, ≤12ms e2e) as canonical and correct the Master Index and Part 1. |
| 2 | **CON-02** | **CRITICAL** | Master Index L252 vs Part 3 §11.2 L40 | **Availability SLA contradicts: 99.99% vs 99.999%** | Master Index says "System Availability: 99.99%". Part 3 §11.2 says "99.999%" (5.26 min downtime/year). Part 3 §14.2 traceability matrix also cites "99.999%". These differ by 10× in allowed downtime (52.6 min/year vs 5.26 min/year). **Recommendation**: Choose one. 99.999% requires multi-region active-active; 99.99% is achievable with single-region multi-AZ. Align all documents to the chosen target. |
| 3 | **CON-03** | **HIGH** | Master Index L251 vs Part 3 §11.2 L38 | **Throughput SLA mismatch: 100K+ vs 50K sustained/100K burst** | Master Index says "Throughput: 100,000+ orders/sec" (implying sustained). Part 3 §11.2 and §14.2 say "50,000 sustained / 100K burst". Using the burst figure as the headline is misleading. **Recommendation**: Clearly state "50,000 sustained / 100,000 burst" everywhere. |
| 4 | **CON-04** | **MEDIUM** | All documents | **Document version inconsistency** | Master Index is **v2.1** (March 5, 2026). The other 5 files are all **v2.0** (same date). If v2.1 includes post-v2.0 changes, the child documents are out of sync. **Recommendation**: Bump child documents to v2.1 or document the delta. |
| 5 | **CON-05** | **MEDIUM** | Part 1 §2, L371 | **Exchange adapter naming violates jurisdiction-neutral principle** | Section 2.2.5 says "Custom adapters for NSE, BSE, MCX, NCDEX" with no mention of NEPSE, despite Nepal being the first instantiation. This contradicts the §1.1 principle. **Recommendation**: Change to "Custom adapters (NEPSE primary; additional exchanges via T3 packs)". |
| 6 | **CON-06** | **MEDIUM** | Master Index L270 vs Part 3 §13.3 | **Database migration tooling conflict** | Master Index §Architecture Guardrails says "Liquibase/Flyway with zero-downtime schema evolution". Part 3 §13.3 implements migrations with **Knex.js**. These are different tools with different capabilities. **Recommendation**: Pick one (Flyway is more enterprise-grade for a regulated platform) and align. |
| 7 | **CON-07** | **MEDIUM** | Part 2 §9.3, L~115 | **JWT algorithm uses HS256 (symmetric) — unsuitable for microservices** | The `generateAccessToken()` implementation uses `algorithm: 'HS256'`. In a microservices architecture, the signing key would need to be shared with every verifying service, violating least-privilege. **Recommendation**: Use RS256 or ES256 (asymmetric) so only Auth Service holds the private key, and all services verify with public key. |
| 8 | **CON-08** | **LOW** | Part 2 §10.3 vs Master Index §KPIs | **Metric name hardcodes INR currency** | The Prometheus histogram is named `order_value_inr` (Part 2, Observability section). For a jurisdiction-neutral platform, metric names should not embed currency. **Recommendation**: Use `order_value_base_currency` with a `currency` label. |
| 9 | **CON-09** | **LOW** | Part 1 §3.5 vs Part 2 §7.3 | **Event schema format conflict: Avro vs JSON** | Section 3.5 defines Avro schemas with Confluent Schema Registry. Section 3.2's code examples and all code samples throughout use JSON serialization (`JSON.stringify(event.payload)`). The event store stores `JSONB`. No Avro serialization is implemented. **Recommendation**: Decide whether events are Avro-serialized (for schema registry compatibility) or JSON (simpler). If Avro, update all code examples. |
| 10 | **CON-10** | **LOW** | Part 3 §12.2 vs Part 1 §1.5 | **Security epic numbering mismatch in traceability** | Section 14.2 references "EPIC-D-08-Security" for MFA and encryption, but the actual epic list names it "EPIC-D-08-Surveillance" (for trade surveillance). D-08 is Surveillance, not Security. Security (IAM) is K-01. **Recommendation**: Fix the traceability matrix entry. |

---

### 2.2 Expressiveness Gaps

| # | ID | Severity | Location | Issue | Detail |
|---|-----|----------|----------|-------|--------|
| 11 | **EXP-01** | **CRITICAL** | All documents | **Multi-tenancy model never formally defined** | Tenant isolation is mentioned via RLS and `tenant_id` columns, but: (a) What constitutes a "tenant"? A broker? An institution? A jurisdiction? (b) How are tenants provisioned and decommissioned? (c) How do cross-tenant queries work (e.g., regulator querying across all brokers)? (d) What is the tenant boundary for event sourcing, Kafka topics, Redis namespaces, and S3 buckets? (e) Can a single Siddhanta deployment host competing brokers with data isolation? **Recommendation**: Add a dedicated §2.x "Multi-Tenancy Architecture" section defining the tenancy model, isolation boundaries per data store, and cross-tenant access patterns. |
| 12 | **EXP-02** | **CRITICAL** | Part 1 §3 / Master Index §ADR-10 | **Distributed Transaction Coordinator (K-17) has zero implementation detail** | K-17 is classified as ARB P0-01 (highest priority finding), yet no architecture document describes its design. The saga pattern is mentioned in passing in §2.2.3 (order placement compensation) but: (a) No saga orchestration framework is specified, (b) No compensating transaction patterns for settlement, ledger, reconciliation, (c) No state machine definition for saga steps, (d) No idempotency guarantees for compensation, (e) Temporal.io is mentioned once in §2.2.3 but never integrated into K-17. **Recommendation**: Add a dedicated sub-section or promote to a full section covering DTC saga patterns, compensating actions, and integration with K-19 (DLQ). |
| 13 | **EXP-03** | **HIGH** | Master Index §ADR-10 / All docs | **DLQ Management & Event Replay (K-19) has zero implementation detail** | K-19 is ARB P0-04 priority, yet no document describes: (a) DLQ topic naming and routing, (b) Max retry policy before DLQ, (c) Event replay UI/API, (d) Poison-pill detection, (e) DLQ monitoring and alerting, (f) Replay idempotency guarantees. **Recommendation**: Add DLQ architecture section. |
| 14 | **EXP-04** | **HIGH** | Part 1 §3.2 | **Event store capacity, partitioning, and archival unspecified** | At 100K orders/sec burst, the `event_store` table could accumulate billions of rows. Unlike the `orders` table (partitioned by date), the event store has no partitioning strategy, no archival plan, and no compaction mechanism. The 10-year retention requirement means multi-terabyte event store. **Recommendation**: Specify event store partitioning (by aggregate type + date range), archive-to-S3 strategy, and event compaction/snapshoting approach for long-term storage. |
| 15 | **EXP-05** | **HIGH** | Part 1 §5.1 | **Content Pack certification pipeline undefined** | The lifecycle mentions "Pack Certification (EPIC-P-01) → Marketplace → Maker-Checker Deployment → Hot-Swap" but never defines what certification entails. Questions: (a) What static analysis is performed on T2/T3 packs? (b) Are integration tests required? (c) Is there a regression test suite? (d) Who certifies — automated pipeline or human reviewers? (e) What is the SLA for certification? **Recommendation**: Define the certification pipeline with gates and acceptance criteria. |
| 16 | **EXP-06** | **HIGH** | Master Index §ADR-8 | **Air-gap deployment architecture missing** | Air-gapped on-premise deployment is declared as a first-class requirement, but no document describes: (a) Container image distribution without internet, (b) Content pack installation without marketplace connectivity, (c) Observability stack in air-gapped mode (no external PagerDuty, Slack), (d) Certificate management without external CA, (e) Time synchronization without NTP access. **Recommendation**: Add an air-gap deployment addendum to Section 8 (Deployment). |
| 17 | **EXP-07** | **MEDIUM** | Part 1 §1.3 | **Market halt / circuit breaker handling unarchitected** | "Market halt detection and circuit-breaker handling" is listed as a capability but never detailed. How does the system: (a) Detect exchange-level circuit breakers? (b) Handle in-flight orders during a halt? (c) Queue or reject new orders? (d) Resume after halt? (e) Handle multi-exchange halts differently? **Recommendation**: Add market halt handling to Section 3 or create a sub-section in the Integration layer. |
| 18 | **EXP-08** | **MEDIUM** | All documents | **Settlement architecture severely underspecified** | Post-trade settlement (D-09) is foundational for capital markets but has minimal coverage. No detail on: (a) Settlement instruction generation, (b) Netting engine, (c) Fail management, (d) Settlement obligation tracking, (e) DVP (Delivery vs Payment) mechanism, (f) T+2 → T+1 → T+0 migration path. **Recommendation**: Settlement deserves its own architecture section given the regulatory significance. |
| 19 | **EXP-09** | **MEDIUM** | Part 1 §3.7 | **Idempotency store TTL conflicts with regulatory retention** | The `RedisProcessedEventsStore` uses a 7-day TTL for processed event IDs. But event replay (K-19) could replay events older than 7 days, causing duplicate processing. The 10-year regulatory retention means events could be replayed years later. **Recommendation**: Use a persistent idempotency store (PostgreSQL) for critical financial events, not Redis with TTL. |
| 20 | **EXP-10** | **MEDIUM** | Part 2 §9.4 | **MFA backup code generation uses `Math.random()` — not cryptographically secure** | The `generateRandomCode` method uses `Math.random()` and string indexing, which is not a CSPRNG. For a financial platform, backup codes must be generated with `crypto.randomBytes()`. **Recommendation**: Replace with `crypto.randomBytes()` or `crypto.getRandomValues()`. |
| 21 | **EXP-11** | **LOW** | Part 1 §2.2.4 | **Bounded contexts list incomplete** | 8 bounded contexts are listed, but the epic inventory shows 14 domain modules (D-01 through D-14). Missing contexts: Pricing (D-05), Surveillance (D-08), Post-Trade (D-09), Regulatory Reporting (D-10), Reference Data (D-11), Corporate Actions is listed but D-12 (Corporate Actions) and D-13 (Client Money Reconciliation) and D-14 (Sanctions Screening) are not mapped to contexts. **Recommendation**: Complete the bounded context mapping for all 14 domain modules. |
| 22 | **EXP-12** | **LOW** | Part 2 §6.1 | **AI governance references "SEBI AI guidelines" and "EU AI Act" — jurisdiction-specific** | The AI Governance overview hardcodes specific regulatory references. For a jurisdiction-neutral platform, AI governance regulatory requirements should come from T2 Rule Packs. **Recommendation**: Reframe as "applicable AI regulatory frameworks (loaded via jurisdiction packs)". |

---

### 2.3 Extensibility Concerns

| # | ID | Severity | Location | Issue | Detail |
|---|-----|----------|----------|-------|--------|
| 23 | **EXT-01** | **CRITICAL** | Part 3 §12.4, L~750 | **Regulatory report formatting hardcodes SEBI/NSE in core code** | `formatReport()` contains: `if (regulator === 'SEBI') { return this.formatForSEBI(...) }`. This directly violates Architecture Principle §1 "Zero Hardcoding of Jurisdiction Logic". Report formatting should be driven by T2 Rule Packs or template packs. **Recommendation**: Refactor to load format templates from T1/T2 packs via K-02 Configuration Engine. |
| 24 | **EXT-02** | **HIGH** | Part 3 §12.4, L~808 | **Report scheduler hardcodes SEBI/NSE report schedules** | `ReportScheduler.scheduleReports()` contains hardcoded `await reportingService.generateReport('DAILY_TRADES', 'SEBI', period)` and `'NSE'`. Scheduling should be driven by jurisdiction-specific T1 configuration. **Recommendation**: Load report scheduling configuration from T1 Config Packs. |
| 25 | **EXT-03** | **HIGH** | Part 1 §5.5 example, L~1140 | **Plugin example hardcodes NSE/BSE market hours** | The `isMarketOpen()` method in the example plugin contains `'NSE': { open: 9.25, close: 15.30 }, 'BSE': { open: 9.25, close: 15.30 }`. Even in an example, this teaches the wrong pattern. **Recommendation**: Show loading market hours from a T1 Config Pack or K-02 Configuration Engine. |
| 26 | **EXT-04** | **HIGH** | Part 1 §5.7 | **T3 Plugin sandbox uses Node.js VM — not a security boundary** | The `PluginSandbox` class uses `vm2` (or built-in VM module) for T3 executable packs. Node.js VM is explicitly documented as "not a security mechanism" (Node.js docs). For T3 packs executing arbitrary signed code from third parties, this is insufficient. **Recommendation**: T3 packs should run in process-isolated containers (gVisor/Kata) or at minimum use V8 isolates (e.g., workerd, isolated-vm). Keep VM only for T2 evaluation. |
| 27 | **EXT-05** | **MEDIUM** | Part 2 §7.3 corporate_actions collection | **MongoDB corporate actions schema has no extensibility for future action types** | The `ca_type` enum is hardcoded to `["DIVIDEND", "BONUS", "RIGHTS", "SPLIT", "MERGER", "BUYBACK"]`. Future corporate action types (e.g., TENDER_OFFER, SPIN_OFF, CONSOLIDATION, WARRANT_CONVERSION) would require schema migration. **Recommendation**: Use a string type for `ca_type` with validation at the application layer, or define extensible enums via T1 Config Packs. |
| 28 | **EXT-06** | **MEDIUM** | Part 1 §2.2.6 | **Database schemas lack extension points for jurisdiction-specific columns** | The core tables (orders, trades, positions) have fixed column sets. Different jurisdictions may require additional fields (e.g., India requires PAN on every order; Nepal may require DMAT account number). No `metadata JSONB` or extension column is provided. **Recommendation**: Add a `jurisdiction_metadata JSONB` column to core domain tables, populated by T1/T2 packs. |
| 29 | **EXT-07** | **LOW** | Part 1 §5.3 | **Plugin type registry is fixed — no custom plugin types** | Plugin types are enumerated (order validators, execution strategies, etc.) with no mechanism for defining new plugin types via packs. A jurisdiction might need a plugin type that doesn't exist (e.g., "demat_reconciler", "depository_connector"). **Recommendation**: Allow T3 packs to register new plugin hook points during certification. |

---

### 2.4 Scalability Risks

| # | ID | Severity | Location | Issue | Detail |
|---|-----|----------|----------|-------|--------|
| 30 | **SCA-01** | **CRITICAL** | Part 2 §7.5, Part 3 §11.3 | **Redis `KEYS` command used in production paths — blocks cluster** | Multiple code examples use `await this.redis.keys(pattern)` (CacheManager.invalidate, PluginEventBus). `KEYS` is O(N) and blocks the Redis event loop. At scale with millions of cache entries, this causes multi-second latency spikes. **Recommendation**: Replace all `KEYS` calls with `SCAN` cursor-based iteration. |
| 31 | **SCA-02** | **HIGH** | Part 1 §3.2 | **Event store is a single unpartitioned PostgreSQL table** | The `event_store` table grows without bounds. At 100K orders/sec burst × multiple events per order, this table could grow by 500K+ rows/sec. No partitioning strategy (unlike `orders` which IS partitioned). Sequential append is already a write bottleneck. **Recommendation**: Partition `event_store` by `created_at` (monthly or weekly). Consider event store separation by aggregate type. |
| 32 | **SCA-03** | **HIGH** | Part 3 §11.3, L~50 | **L1 in-memory cache (Map) has no eviction, TTL, or size limit** | The `CachingStrategy` L1 cache is `private l1Cache: Map<string, any>`. A bare Map grows without bound, causing OOM crashes under sustained traffic. **Recommendation**: Use an LRU cache with configurable max size and TTL (e.g., `lru-cache` npm package). |
| 33 | **SCA-04** | **HIGH** | Part 1 §2.2.5, Part 2 §8.3 | **Kafka partition counts insufficient for target throughput** | Topic config shows 12 partitions for `trading.order.placed` and 24 for `market-data.tick`. At 100K orders/sec burst, each partition would need to handle ~8,333 msgs/sec (for 12 partitions). While feasible, this leaves no headroom and limits consumer parallelism. Market data at 24 partitions with potentially thousands of instruments is also tight. **Recommendation**: Provision at minimum 36-48 partitions for order topics and 64+ for market data. Add partition scaling plan. |
| 34 | **SCA-05** | **MEDIUM** | Part 2 §10.3 | **Prometheus cardinality explosion from unbounded `path` label** | HTTP metrics use `labelNames: ['method', 'path', 'status']` where `path` is `req.route?.path || req.path`. If `req.route` is undefined (e.g., 404s), raw paths (including UUIDs) leak into labels, causing cardinality explosion. **Recommendation**: Always use normalized route patterns; never use raw request paths as metric labels. |
| 35 | **SCA-06** | **MEDIUM** | Part 2 §7.4 | **MongoDB has no sharding strategy** | Corporate actions and regulatory filings are stored in MongoDB, but no sharding key or strategy is defined. With multi-jurisdiction deployment and 10-year retention, these collections will grow significantly. **Recommendation**: Define sharding strategy (e.g., hash-based on `instrument_id` for corporate actions, range-based on `filing_date` for regulatory filings). |
| 36 | **SCA-07** | **MEDIUM** | All documents | **Single Kafka cluster — no geo-redundancy** | No mention of multi-cluster Kafka (e.g., MirrorMaker 2) for cross-region replication. If the primary region's Kafka fails, there's no event streaming. **Recommendation**: Add multi-cluster Kafka replication for the DR region. |
| 37 | **SCA-08** | **LOW** | Part 1 §3.2 | **Snapshot frequency (every 100 events) may be insufficient** | For high-velocity aggregates (e.g., a market data instrument receiving thousands of updates/sec), replaying 100 events on every read is expensive. **Recommendation**: Make snapshot frequency configurable per aggregate type. High-frequency aggregates should snapshot more often. |
| 38 | **SCA-09** | **LOW** | Part 2 §8.3 | **EKS node group has only 12 max nodes for trading** | `maxSize: 12` for `c5.2xlarge` (8 vCPU, 16 GiB each) = 96 vCPU max for trading services. At 100K orders/sec, this may be insufficient. **Recommendation**: Increase `maxSize` or provide sizing calculations that justify the limit. |

---

### 2.5 Real-World Problem Coverage

| # | ID | Severity | Location | Issue | Detail |
|---|-----|----------|----------|-------|--------|
| 39 | **RWP-01** | **CRITICAL** | All documents | **No reconciliation architecture** | D-13 (Client Money Reconciliation) exists as an epic, but no architecture section covers: (a) Daily trade-vs-exchange reconciliation, (b) Cash reconciliation with bank/payment gateway, (c) Depository position reconciliation, (d) Break detection and resolution workflow, (e) Regulatory reconciliation reporting (SEBON/NRB mandate). This is a Day 1 operational necessity for any brokerage. **Recommendation**: Add a dedicated reconciliation architecture section. |
| 40 | **RWP-02** | **HIGH** | All documents | **No depository integration architecture** | Securities settlement requires depository connectivity (CDS&C in Nepal, NSDL/CDSL in India). No architecture for: (a) DEMAT account management, (b) Settlement instruction submission, (c) Delivery/receipt confirmation, (d) Pledge/unpledge for margin. **Recommendation**: Make depository adapter a T3 plugin with a well-defined SPI (Service Provider Interface). |
| 41 | **RWP-03** | **HIGH** | All documents | **No payment/banking integration architecture** | Client fund management (deposit, withdrawal, margin top-up) requires bank integration. No architecture for: (a) Real-time payment confirmation, (b) Bank statement reconciliation, (c) RTGS/NEFT/SWIFT integration, (d) Nepal-specific NRB payment rails. **Recommendation**: Add payment gateway architecture as a T3 plug-in point. |
| 42 | **RWP-04** | **HIGH** | Part 2 §8, Master Index §DR | **Disaster recovery testing is absent** | DR objectives are defined (RTO <15 min, RPO <1 sec), multi-region is specified, but no architecture for: (a) Automated failover testing, (b) Data consistency verification after failover, (c) Failback procedures, (d) DR drill scheduling and reporting. The T-02 (Chaos Engineering) epic partially covers this, but no architecture supports it. **Recommendation**: Add DR testing architecture and integrate with chaos engineering framework. |
| 43 | **RWP-05** | **MEDIUM** | All documents | **No batch/EOD processing architecture** | Capital markets require end-of-day processing: (a) EOD position valuation (mark-to-market), (b) Margin recomputation, (c) NAV calculation for fund portfolios, (d) Statement generation, (e) Interest computation, (f) Settlement obligation netting. None of this is architectured. **Recommendation**: Add an EOD batch processing section to the architecture. |
| 44 | **RWP-06** | **MEDIUM** | All documents | **No network architecture** | No documentation of: (a) VPC/VNET topology, (b) CIDR planning, (c) Security groups / network policies, (d) DNS architecture, (e) Load balancer hierarchy, (f) Exchange connectivity (co-location, leased lines, VPN). **Recommendation**: Add network architecture diagram and specification. |
| 45 | **RWP-07** | **MEDIUM** | All documents | **No data residency enforcement architecture** | Despite mentioning data residency as a requirement, there's no mechanism to ensure: (a) Nepal data stays in Nepal infrastructure, (b) Tenant data doesn't cross jurisdiction boundaries, (c) Regulatory data access restrictions by geography. **Recommendation**: Add data residency enforcement architecture (label-based data classification + policy enforcement at storage and network layers). |
| 46 | **RWP-08** | **MEDIUM** | Part 2 §9.3 | **No session management for trading terminals** | Desktop trading terminals (mentioned in §2.2.1) need: (a) Persistent WebSocket connections, (b) Session failover, (c) Multi-device session management, (d) Session revocation. The JWT approach doesn't cover long-lived terminal sessions. **Recommendation**: Add trading terminal session management architecture. |
| 47 | **RWP-09** | **LOW** | All documents | **No client onboarding detail** | W-02 (Client Onboarding & KYC) is referenced but no architecture for: document verification, video KYC, eKYC/DigiLocker (India), DMAT account opening integration, risk profiling questionnaire, or PEP/sanctions screening during onboarding. **Recommendation**: Define onboarding workflow architecture with integration points. |
| 48 | **RWP-10** | **LOW** | All documents | **No operational runbook architecture** | O-01 (Operator Workflows) epic exists but no architecture for: runbook format, automated remediation, escalation paths, or operational knowledge base. **Recommendation**: Define runbook-as-code architecture with integration to observability stack. |

---

### 2.6 Future-Proofing Gaps

| # | ID | Severity | Location | Issue | Detail |
|---|-----|----------|----------|-------|--------|
| 49 | **FUT-01** | **HIGH** | Master Index §Future-Proofing, Part 2 §9.3 | **Post-quantum cryptography has no migration path** | PQC is on the roadmap, but the current architecture: (a) Uses HS256 for JWTs (should be RS256 minimum; PQC = CRYSTALS-Dilithium), (b) Uses AES-256-GCM (quantum-safe for symmetric, but key exchange is vulnerable), (c) Uses Ed25519 for plugin signing (not PQC-safe), (d) No crypto-agility abstraction layer exists. **Recommendation**: Introduce a cryptographic abstraction layer now so algorithms can be swapped without code changes. |
| 50 | **FUT-02** | **HIGH** | Master Index §Future-Proofing | **Digital asset / tokenized security support has no architectural hooks** | The `instruments` table has a fixed `instrument_type` field with no provision for tokenized assets. No: (a) Fractional ownership model, (b) On-chain settlement patterns, (c) Wallet/custody management, (d) Smart contract interaction. **Recommendation**: Add `instrument_metadata JSONB` to the instruments table and define an asset-class-agnostic position model. |
| 51 | **FUT-03** | **MEDIUM** | Master Index §Future-Proofing | **CBDC integration has no ledger abstraction** | K-16 (Ledger Framework) is referenced but not detailed in the architecture. A CBDC integration requires: (a) Multi-currency ledger, (b) Token vs account-based models, (c) Central bank API integration, (d) Programmable money rules. None of this is architectured. **Recommendation**: Ensure K-16 LLD includes CBDC-ready ledger abstractions. |
| 52 | **FUT-04** | **MEDIUM** | Master Index §Future-Proofing | **T+0 settlement requires fundamentally different architecture** | T+0 means atomic DVP (Delivery vs Payment) with real-time cash confirmation. The current architecture assumes asynchronous settlement (event-driven, T+2). Real-time gross settlement needs: (a) Synchronous DVP mechanism, (b) Real-time liquidity management, (c) Pre-funding or collateral management. None specified. **Recommendation**: Add T+0 settlement architecture as an addendum to Section 3 (Event-Driven architecture), showing both async (T+2/T+1) and sync (T+0) patterns. |
| 53 | **FUT-05** | **MEDIUM** | Master Index §Future-Proofing | **ESG has no data model or screening pipeline** | ESG is listed as medium-term but no: (a) ESG score data model, (b) ESG data provider integration, (c) Green bond classification, (d) Carbon footprint tracking, (e) ESG screening in order validation, (f) ESG regulatory reporting framework. **Recommendation**: Add ESG data model extension points to D-11 (Reference Data) and D-03 (PMS). |
| 54 | **FUT-06** | **LOW** | Master Index §Future-Proofing | **Cross-border settlement has no FX or ISO 20022 architecture** | No: (a) FX conversion engine, (b) Correspondent banking integration, (c) SWIFT/ISO 20022 message handling, (d) Multi-CBDC bridge architecture. **Recommendation**: Future-architecture note is sufficient for now, but define interface contracts early. |
| 55 | **FUT-07** | **LOW** | All documents | **No GraphQL schema evolution strategy** | Apollo GraphQL Federation is specified, but no strategy for: (a) Schema evolution/deprecation, (b) Breaking change policy, (c) Federation gateway versioning. **Recommendation**: Add GraphQL-specific versioning policy alongside REST API versioning in §13.2. |

---

### 2.7 Platform / Content-Pack Model Issues

| # | ID | Severity | Location | Issue | Detail |
|---|-----|----------|----------|-------|--------|
| 56 | **PCM-01** | **CRITICAL** | Part 3 §12.4, Part 1 §5.5, Part 2 §10.3 | **Jurisdiction-specific logic leaks into core in at least 5 places** | Despite the "Zero Hardcoding" invariant, the following code in the architecture spec embeds jurisdiction-specific logic in core: (1) `formatReport()` with SEBI/NSE branches [Part 3 §12.4], (2) `ReportScheduler` scheduling SEBI/NSE reports [Part 3 §12.4], (3) `isMarketOpen()` with NSE/BSE hours [Part 1 §5.5], (4) `order_value_inr` metric name [Part 2 §10.3], (5) Exchange adapter list "NSE, BSE, MCX, NCDEX" without NEPSE [Part 1 §2.2.5]. **Recommendation**: Systematically audit all code examples for jurisdiction references and refactor to use T1/T2 configurations. |
| 57 | **PCM-02** | **HIGH** | All documents | **T2 Rule Pack (OPA/Rego) integration architecture completely missing** | OPA/Rego is the specified technology for T2 Rule Packs, but no document describes: (a) How OPA is deployed (sidecar, daemon, library), (b) How policies are distributed to OPA instances, (c) How OPA decisions are logged for audit, (d) How T2 packs are compiled and loaded into OPA, (e) How OPA integrates with the K-03 Rules Engine, (f) Performance impact of OPA evaluation in the hot path. **Recommendation**: Add a dedicated OPA integration section to the Plugin Runtime Architecture (§5). |
| 58 | **PCM-03** | **HIGH** | Part 1 §5 | **T1 Config Pack loading mechanism undefined** | T1 Config Packs (tax tables, calendars, thresholds) are referenced throughout, but no architecture describes: (a) How T1 packs are physically structured (YAML files? JSON? Protobuf?), (b) How they're loaded into K-02 Configuration Engine, (c) How they interact with the 5-level config hierarchy (do they populate the JURISDICTION level?), (d) How they're versioned and updated in production, (e) How conflicts between T1 pack versions are resolved. **Recommendation**: Define T1 pack format specification and loading mechanism in §4 or §5. |
| 59 | **PCM-04** | **MEDIUM** | Part 1 §5 | **No dependency resolution between content packs** | A T2 Rule Pack may depend on specific T1 Config Pack values (e.g., a margin rule depends on margin rate thresholds). A T3 Executable Pack may depend on both T1 and T2 packs. No dependency graph, version compatibility matrix, or conflict resolution mechanism is defined. **Recommendation**: Add pack dependency specification to the plugin manifest format (§5.4) and add dependency resolution to the lifecycle manager (§5.8). |
| 60 | **PCM-05** | **MEDIUM** | Part 1 §5.8 | **Content pack rollback architecture missing** | Hot-swap is described for deploying new packs, but: (a) What if a new T2 rule pack causes false compliance failures? (b) How is a pack rolled back to the previous version? (c) Is state preserved during rollback? (d) Are events processed during the faulty-pack window replayed? **Recommendation**: Add rollback mechanism with pack version pinning and replay capability. |
| 61 | **PCM-06** | **MEDIUM** | Master Index §ADR-1 | **Pack marketplace architecture missing** | P-01 references "Pack Certification & Marketplace" but no architecture for: (a) Pack discovery and search, (b) Pack ratings and trust model, (c) Compatibility matrix (which platform version supports which pack version), (d) Marketplace API, (e) Offline/air-gap marketplace mirror. **Recommendation**: Add marketplace architecture — at minimum define the data model and API. |
| 62 | **PCM-07** | **LOW** | Part 1 §5.2 | **T1 signing is "Optional" — creates supply chain risk** | T1 Config Packs have "Optional" signing. A corrupted or tampered T1 pack (e.g., modified tax rate, wrong circuit breaker threshold) could have severe financial impact. **Recommendation**: Make T1 signing mandatory in production (can remain optional in development). |

---

## 3. Missing Topics

The following topics are absent from all 6 architecture documents and represent significant gaps:

| # | ID | Severity | Missing Topic | Impact |
|---|-----|----------|---------------|--------|
| 63 | **MIS-01** | **HIGH** | **API Specification (OpenAPI/Swagger, GraphQL SDL, gRPC Proto)** | No concrete API contracts exist despite "API-First" being a declared principle. Development teams cannot begin implementation without API specs. |
| 64 | **MIS-02** | **HIGH** | **WebSocket Architecture for Real-Time Data** | WebSocket is mentioned 5+ times as the real-time mechanism but never architectured. No: connection lifecycle, authentication, subscription model, backpressure, or reconnection strategy. |
| 65 | **MIS-03** | **MEDIUM** | **Data Classification Scheme** | No PII/restricted/confidential/public data classification. Critical for GDPR compliance, audit, and access control. |
| 66 | **MIS-04** | **MEDIUM** | **Internationalization (i18n) and Localization** | Multi-jurisdiction implies multi-language. No i18n architecture for UI, reports, notifications, or error messages. Nepal requires Nepali; India requires Hindi + English. |
| 67 | **MIS-05** | **LOW** | **Inter-Service SLA Contracts** | No documented SLAs between internal services (e.g., what latency/availability does K-05 Event Bus promise to D-01 OMS?). Without these, end-to-end SLAs cannot be composed or reasoned about. |

---

## 4. Summary Matrix

| Category | CRITICAL | HIGH | MEDIUM | LOW | Total |
|----------|----------|------|--------|-----|-------|
| **Consistency** | 2 | 1 | 4 | 3 | 10 |
| **Expressiveness** | 2 | 4 | 4 | 2 | 12 |
| **Extensibility** | 1 | 3 | 2 | 1 | 7 |
| **Scalability** | 1 | 3 | 3 | 2 | 9 |
| **Real-World** | 1 | 3 | 4 | 2 | 10 |
| **Future-Proofing** | 0 | 2 | 3 | 2 | 7 |
| **Platform Model** | 1 | 2 | 3 | 1 | 7 |
| **Missing Topics** | 0 | 2 | 2 | 1 | 5 |
| **TOTAL** | **8** | **20** | **25** | **14** | **67** |

### Distribution by Document

| Document | Issues Referencing |
|----------|-------------------|
| Master Index (All) | 28 (referenced by cross-doc issues) |
| Part 1 §1-3 | 16 |
| Part 1 §4-5 | 14 |
| Part 2 §6-8 | 12 |
| Part 2 §9-10 | 10 |
| Part 3 §11-15 | 15 |

---

## 5. Recommended Remediation Priority

### Immediate (Before Implementation Starts)

| Priority | Issues | Action |
|----------|--------|--------|
| **P0** | CON-01, CON-02, CON-03 | **Fix SLA contradictions** — Establish single-source-of-truth SLA table; propagate to all 6 documents. |
| **P0** | EXP-02 | **Architect K-17 (DTC)** — This is an ARB P0-01 finding with zero architectural detail. Add saga patterns, state machines, compensating actions. |
| **P0** | PCM-01, EXT-01 | **Purge jurisdiction-specific code from architecture** — Audit all 6 documents for SEBI/NSE/BSE/INR hardcoding; replace with generic patterns using T1/T2/T3 packs. |
| **P0** | RWP-01 | **Add reconciliation architecture** — No brokerage can go live without daily reconciliation. |

### Before Phase 2 (Core Domain Subsystems)

| Priority | Issues | Action |
|----------|--------|--------|
| **P1** | EXP-01 | **Define multi-tenancy model** — Tenant boundaries across all data stores. |
| **P1** | EXP-03, EXP-04 | **Architect K-19 (DLQ)** and **Event store scaling** — Both are ARB findings. |
| **P1** | PCM-02, PCM-03 | **Define OPA integration** and **T1 pack loading** — T2/T3 packs are deployment vehicles for Phase 2 Nepal-specific rules. |
| **P1** | SCA-01, SCA-02 | **Fix Redis KEYS** and **partition event store** — Production reliability prerequisite. |
| **P1** | CON-07 | **Switch JWT to RS256/ES256** — Security prerequisite for multi-service verification. |
| **P1** | EXT-04 | **Upgrade T3 sandbox** to process-level isolation — Security prerequisite for third-party code. |
| **P1** | MIS-01, MIS-02 | **Produce API specs** and **WebSocket architecture** — Developer prerequisites. |

### Before Phase 3 (Advanced Workflows)

| Priority | Issues | Action |
|----------|--------|--------|
| **P2** | RWP-02, RWP-03 | **Depository and payment integration** architecture. |
| **P2** | EXP-08 | **Settlement architecture** — Required before D-09 implementation. |
| **P2** | FUT-01 | **Crypto-agility abstraction** — Foundation for PQC migration. |
| **P2** | RWP-05, RWP-06 | **EOD processing** and **network architecture**. |
| **P2** | PCM-04, PCM-05 | **Pack dependency resolution** and **rollback**. |

### Before Production (Phase 4)

| Priority | Issues | Action |
|----------|--------|--------|
| **P3** | EXP-06 | **Air-gap deployment architecture**. |
| **P3** | RWP-04 | **DR testing automation**. |
| **P3** | FUT-02, FUT-03, FUT-04 | **Digital asset, CBDC, and T+0 hooks** — Not needed for MVP but architecture hooks must exist. |
| **P3** | MIS-03, MIS-04 | **Data classification** and **i18n**. |
| **P3** | PCM-06 | **Pack marketplace** architecture. |

---

**Report generated**: March 6, 2026  
**Next review**: After P0 remediation complete  
**Estimated P0 remediation effort**: 2-3 architecture team weeks
