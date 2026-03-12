# LOW-LEVEL DESIGN (LLD) INDEX

## Project Siddhanta - Implementation-Ready Design Documents

**Version**: 3.1.1  
**Last Updated**: 2026-03-10  
**Status**: Complete — All 33 LLDs Authored (Full Architecture Coverage)  
**Owner**: Platform Architecture Team

---

## 1. OVERVIEW

This index provides navigation to all Low-Level Design (LLD) documents for Project Siddhanta's kernel modules and domain subsystems. Each LLD follows a standardized 10-section template ensuring implementation-ready specifications with concrete APIs, data models, algorithms, and test plans.

### 1.1 Document Structure

All LLDs follow this mandatory structure:

1. **Module Overview** - Purpose, responsibilities, invariants, dependencies
2. **Public APIs & Contracts** - REST, gRPC, SDK interfaces with example payloads
3. **Data Model** - Event schemas, storage tables, indexes
4. **Control Flow** - Sequence diagrams (textual), interaction flows
5. **Algorithms & Policies** - Core algorithms, business rules, decision logic
6. **NFR Budgets** - Latency, throughput, resource limits
7. **Security Design** - Authentication, authorization, encryption, isolation
8. **Observability & Audit** - Metrics, logs, traces, alerts
9. **Extensibility & Evolution** - Extension points, versioning, backward compatibility
10. **Test Plan** - Unit, integration, security, chaos tests

### 1.2 Design Principles

- **Generic Core Purity**: No jurisdiction-specific logic in kernel modules
- **Event Sourcing**: All state changes captured as immutable events
- **CQRS**: Command/query separation for scalability
- **Dual Calendar**: Bikram Sambat + Gregorian timestamps
- **Maker-Checker**: Approval workflows for critical operations
- **AI Integration**: Hooks for AI governance and explainability
- **Zero Trust**: Assume breach, verify everything

---

## 2. KERNEL MODULES (K-XX)

### K-01 Identity & Access Management (IAM)

**File**: `lld/LLD_K01_IAM.md`

**Purpose**: Multi-tenant authentication, authorization, session management, and KYC/AML integration.

**Key Features**:

- OAuth 2.0 + JWT authentication with MFA (TOTP/WebAuthn)
- RBAC with fine-grained permissions (resource + action level)
- Multi-tenant session management with tenant isolation
- KYC/AML status verification integration
- National ID verification via pluggable adapters (T3)
- SSO federation (SAML, OIDC)
- DID/wallet-based authentication (digital assets)

**Dependencies**: K-02 (configuration), K-05 (auth events), K-07 (audit), K-15 (dual-calendar)

**Extension Points**:

- National ID verification adapters (T3 — jurisdiction-specific)
- Custom authentication providers (T3)
- KYC/AML rule packs (T2)

**Status**: ✅ LLD authored

---

### K-02 Configuration Engine

**File**: `lld/LLD_K02_CONFIGURATION_ENGINE.md`

**Purpose**: Hierarchical configuration management with hot-reload, schema validation, and multi-level overrides.

**Key Features**:

- Schema registration and validation
- 6-level hierarchy: GLOBAL → JURISDICTION → OPERATOR → TENANT → ACCOUNT → USER
- Hot-reload without service restart
- Dual-calendar effective dates
- Canary rollout for configuration changes
- Air-gap support with cryptographic signing
- Metadata asset governance for process templates, task schemas, value catalogs, and routing policies

**Dependencies**: None (foundational module)

**Extension Points**:

- Custom schema validators
- Configuration transformers
- Rollout strategies
- Metadata asset providers and scoped overlay policies

---

### K-03 Policy/Rules Engine

**File**: `lld/LLD_K03_RULES_ENGINE.md`

**Purpose**: Declarative policy evaluation using OPA/Rego for compliance and business rules.

**Key Features**:

- Sandboxed Rego execution (T2 isolation)
- Hot-reload of rule packs
- Audit trail of all policy decisions
- Jurisdiction-specific rule routing
- Maker-checker for rule deployment
- < 10ms P99 evaluation latency

**Dependencies**: K-02 (rule pack storage), K-05 (hot-reload events), K-07 (audit)

**Extension Points**:

- Custom Rego functions
- Policy versioning strategies
- Fallback policies

---

### K-04 Plugin Runtime & SDK

**File**: `lld/LLD_K04_PLUGIN_RUNTIME.md`

**Purpose**: Secure, versioned extension mechanism for third-party and internal plugins.

**Key Features**:

- Cryptographic signature verification (Ed25519)
- Tier-based isolation (T1=process, T2=sandbox, T3=network)
- Capability-based access control
- Version compatibility enforcement
- Hot-swap without downtime
- Graceful degradation on plugin failure

**Dependencies**: K-02 (plugin config), K-05 (lifecycle events), K-07 (audit)

**Extension Points**:

- Custom plugin tiers
- Plugin lifecycle hooks
- Capability registration

---

### K-05 Event Bus, Event Store & Workflow Orchestration

**File**: `lld/LLD_K05_EVENT_BUS.md`

**Purpose**: Append-only event storage, reliable delivery, and saga-based workflow orchestration.

**Key Features**:

- Immutable event store (PostgreSQL + Kafka)
- At-least-once delivery guarantees
- Idempotency enforcement
- Saga orchestration with automatic compensation
- Event replay for projections
- Schema evolution with backward compatibility

**Dependencies**: K-02 (schema registry), K-07 (audit)

**Extension Points**:

- Custom event handlers
- Saga definition DSL
- Projection builders

---

### K-07 Audit Framework

**File**: `lld/LLD_K07_AUDIT_FRAMEWORK.md`

**Purpose**: Immutable, cryptographically-chained audit logging for regulatory compliance.

**Key Features**:

- Hash chain for tamper detection (SHA-256)
- Standardized audit event schema
- Evidence export (CSV, JSON, PDF)
- Retention policy enforcement
- Maker-checker linkage
- Immutability enforced at database level

**Dependencies**: K-02 (retention policies), K-05 (audit events)

**Extension Points**:

- Custom audit actions
- Event enrichers
- Compliance report templates

---

### K-09 AI Governance

**File**: `lld/LLD_K09_AI_GOVERNANCE.md`

**Purpose**: Model registry, prompt versioning, explainability, and HITL controls for AI/ML.

**Key Features**:

- Model registry with versioning
- SHAP/LIME explainability
- Human-in-the-loop (HITL) override mechanism
- Model drift detection (PSI)
- Instant rollback to previous versions
- A/B testing framework

**Dependencies**: K-02 (model config), K-05 (AI decision events), K-07 (audit)

**Extension Points**:

- Custom explainability methods
- Fairness metrics
- Model optimization strategies

---

### K-06 Observability Stack

**File**: `lld/LLD_K06_OBSERVABILITY.md`

**Purpose**: Metrics collection, distributed tracing, centralized logging, alerting, and SLO management.

**Key Features**:

- Prometheus metrics collection with custom dimensions
- Jaeger/OpenTelemetry distributed tracing
- ELK Stack centralized logging
- Grafana dashboards with alert rules
- SLO/SLI management with error budget tracking
- Trace context propagation across all services
- Meta-observability for monitoring the monitors

**Dependencies**: K-02 (alert thresholds, dashboard config)

**Extension Points**:

- Custom metric collectors
- Dashboard templates (T1 config pack)
- Alert routing rules (T2 rule pack)
- Custom trace exporters

**Status**: ✅ LLD authored

---

### K-15 Dual-Calendar Service

**File**: `lld/LLD_K15_DUAL_CALENDAR.md`

**Purpose**: Bikram Sambat ↔ Gregorian calendar conversion, dual-date generation, and fiscal year calculation.

**Key Features**:

- `DualDate(gregorian, bs)` generation for every timestamp
- BS ↔ AD conversion with JDN algorithm + 100-year lookup table
- Fiscal year boundaries per jurisdiction (T1 config)
- Business day calculation with holiday calendars (T1 config)
- Timezone-aware conversions
- Settlement date T+n calculation with weekend awareness
- CBDC/T+0 support

**Dependencies**: K-02 (holiday calendars, fiscal year config)

**Extension Points**:

- Additional calendar systems (T1 config — e.g., Islamic, Thai)
- Custom holiday calendars per jurisdiction (T1)
- Fiscal year boundary rules (T1)

**Status**: ✅ LLD authored

---

### K-16 Ledger Framework

**File**: `lld/LLD_K16_LEDGER_FRAMEWORK.md`

**Purpose**: Immutable, double-entry ledger primitive for all financial tracking across domain modules.

**Key Features**:

- Double-entry bookkeeping with balance enforcement
- Append-only, event-sourced ledger entries (REVOKE UPDATE/DELETE)
- Temporal balance queries with pre-computed snapshots
- Multi-currency and multi-asset (BTC, ETH, NPR_CBDC) support
- Built-in reconciliation engine with break aging/escalation
- Nightly balance verification with integrity alerts

**Dependencies**: K-02 (chart of accounts, precision rules), K-05 (events), K-15 (dual-calendar), K-17 (DTC for cross-module postings)

**Extension Points**:

- Chart of accounts structure (T1 config pack)
- Reconciliation format adapters (T3 — CDSC, CCIL, bank-specific)
- Rounding rules per currency (T1)
- Tax calculation hooks (T2)

**Status**: ✅ LLD authored

---

### K-17 Distributed Transaction Coordinator

**File**: `lld/LLD_K17_DISTRIBUTED_TRANSACTION_COORDINATOR.md`

**Purpose**: Outbox pattern, version vectors for cross-stream causal ordering, and compensation orchestration.

**Key Features**:

- Transactional outbox for guaranteed event publishing
- Version vectors for cross-aggregate causal ordering
- Compensation orchestration with retry and manual resolution
- Saga state machine with timeout and dead-letter handling
- Idempotent command processing
- Synchronous mode for T+0/CBDC settlement

**Dependencies**: K-05 (event delivery), K-06 (observability), K-07 (audit), K-16 (ledger), K-19 (DLQ)

**Extension Points**:

- Custom saga definitions (T1)
- Compensation strategies
- Conflict resolution plugins (T3)

**Status**: ✅ LLD authored [ARB P0-01 resolved]

---

### K-18 Resilience Patterns Library

**File**: `lld/LLD_K18_RESILIENCE_PATTERNS.md`

**Purpose**: Shared circuit breakers, bulkheads, retry policies, and timeout management.

**Key Features**:

- Circuit breaker with configurable thresholds (half-open probing)
- Bulkhead isolation (thread pool and semaphore)
- Retry policies with exponential backoff and jitter
- Timeout management with cascading timeout budgets
- Pre-defined resilience profiles (CRITICAL_PATH, BEST_EFFORT, COMPLIANCE_SENSITIVE, REAL_TIME_DATA, SETTLEMENT)
- Decorator/annotation-based integration

**Dependencies**: K-02 (configuration), K-05 (events), K-06 (metrics)

**Extension Points**:

- Custom resilience profiles (T1)
- Custom failure classifiers (T3)
- Adaptive resilience (future ML-driven)

**Status**: ✅ LLD authored [ARB P0-02 resolved]

---

### K-19 DLQ Management & Event Replay

**File**: `lld/LLD_K19_DLQ_MANAGEMENT.md`

**Purpose**: Dead-letter queue monitoring, root cause analysis tooling, and safe event replay.

**Key Features**:

- DLQ dashboard with threshold alerts
- Root cause analysis (RCA) requirement before replay
- Safe replay with idempotency verification and dry-run
- Poison pill detection and quarantine
- Bulk replay with rate limiting
- Auto-retry rules for transient failures

**Dependencies**: K-05 (event bus), K-06 (observability), K-07 (audit), K-15 (dual-calendar)

**Extension Points**:

- Custom auto-retry rules (T1)
- Custom DLQ handlers (T3)
- AI-assisted RCA (future)

**Status**: ✅ LLD authored [ARB P0-04 resolved]

---

### K-08 Data Governance

**File**: `lld/LLD_K08_DATA_GOVERNANCE.md`

**Purpose**: Data lineage tracking, residency enforcement, retention lifecycle, encryption abstraction, and GDPR data subject rights.

**Key Features**:

- Data lineage graph with field-level tracking
- Data residency enforcement per jurisdiction
- Retention lifecycle management with automated purge
- Encryption abstraction (AES-256-GCM / ChaCha20)
- GDPR data subject rights (access, erasure, portability)
- Data classification (PUBLIC, INTERNAL, CONFIDENTIAL, RESTRICTED)
- PII detection and masking

**Dependencies**: K-01 (identity), K-02 (config), K-05 (events), K-06 (observability), K-07 (audit), K-14 (encryption keys)

**Extension Points**:

- Custom data classifiers (T2)
- Jurisdiction-specific residency rules (T1)
- Custom retention policies (T1)

**Status**: ✅ LLD authored

---

### K-10 Deployment Abstraction Layer

**File**: `lld/LLD_K10_DEPLOYMENT_ABSTRACTION.md`

**Purpose**: Unified deployment across SaaS, dedicated, on-prem, hybrid, and air-gap topologies with feature flags and Ed25519 bundle signing.

**Key Features**:

- 5 deployment topology support (SaaS, dedicated, on-prem, hybrid, air-gap)
- Feature flag management with gradual rollout
- Ed25519 signed deployment bundles for air-gap
- Hybrid sync with conflict resolution
- Blue-green and canary deployment strategies
- Rollback with automatic health verification

**Dependencies**: K-02 (config), K-04 (plugin runtime), K-06 (observability), K-07 (audit)

**Extension Points**:

- Custom deployment strategies (T1)
- Cloud provider adapters (T3 — AWS/Azure/GCP)
- Custom health checks

**Status**: ✅ LLD authored

---

### K-11 API Gateway

**File**: `lld/LLD_K11_API_GATEWAY.md`

**Purpose**: Single entry point with JWT/mTLS auth, rate limiting, dynamic routing, jurisdiction-aware routing, OpenAPI schema validation, and WAF.

**Key Features**:

- JWT + mTLS authentication
- Token bucket rate limiting with tenant-aware quotas
- Dynamic routing with service discovery
- Jurisdiction-aware routing (tenant → jurisdiction → service)
- OpenAPI schema validation (request/response)
- WAF integration (OWASP Top 10)
- Request/response transformation

**Dependencies**: K-01 (auth), K-02 (config), K-05 (events), K-06 (observability), K-07 (audit)

**Extension Points**:

- Custom rate limiting strategies (T1)
- Custom middleware plugins (T3)
- Route transformers

**Status**: ✅ LLD authored

---

### K-12 Platform SDK

**File**: `lld/LLD_K12_PLATFORM_SDK.md`

**Purpose**: Multi-language SDK (Go, Java, Python, TypeScript, C#) with protocol abstraction, error translation, middleware hooks, and auto-generation.

**Key Features**:

- Multi-language support (Go, Java, Python, TypeScript, C#)
- Protocol abstraction (REST, gRPC, WebSocket)
- Automatic error translation with standard error codes
- Middleware hooks (auth, retry, logging)
- Auto-generation from protobuf and OpenAPI specs
- Connection pooling and circuit breaker integration

**Dependencies**: All platform services, K-01 (auth), K-18 (resilience)

**Extension Points**:

- Custom language targets
- Custom middleware
- Protocol adapters

**Status**: ✅ LLD authored

---

### K-13 Admin Portal

**File**: `lld/LLD_K13_ADMIN_PORTAL.md`

**Purpose**: Micro-frontend admin console (React 18) with RBAC views, maker-checker UI, dual-calendar date pickers, audit log viewer, and AI insights dashboard.

**Key Features**:

- Micro-frontend architecture (React 18 + Module Federation)
- RBAC-aware UI rendering
- Maker-checker approval workflows
- Dual-calendar date pickers (BS + Gregorian)
- Audit log viewer with advanced filtering
- AI insights dashboard with explainability
- Real-time operational dashboards
- Schema-driven forms and workflow-task rendering with value-catalog-backed controls

**Dependencies**: K-01 (IAM), K-02 (config), K-04 (plugin runtime), K-06 (observability), K-07 (audit), K-09 (AI), K-10 (deployment), K-15 (dual-calendar)

**Extension Points**:

- Custom admin micro-frontends (T3)
- Dashboard widget plugins
- Theme customization (T1)

**Status**: ✅ LLD authored

---

### K-14 Secrets Management

**File**: `lld/LLD_K14_SECRETS_MANAGEMENT.md`

**Purpose**: Multi-provider vault abstraction (Vault/AWS/Azure/GCP), auto-rotation, certificate lifecycle, break-glass access with MFA, and HSM integration.

**Key Features**:

- Multi-provider vault abstraction (HashiCorp Vault, AWS SM, Azure KV, GCP SM)
- Automatic secret rotation with zero-downtime
- Certificate lifecycle management (issuance, renewal, revocation)
- Break-glass access with MFA + mandatory review
- HSM integration for key custody
- Secret versioning and rollback

**Dependencies**: K-01 (IAM), K-02 (config), K-07 (audit), K-08 (data governance)

**Extension Points**:

- Custom vault providers (T3)
- Custom rotation strategies (T1)
- Certificate authority integrations

**Status**: ✅ LLD authored

---

## 3. DOMAIN SUBSYSTEMS (D-XX)

### D-01 Order Management System (OMS)

**File**: `lld/LLD_D01_OMS.md`

**Purpose**: Order lifecycle management, pre-trade validation, routing, and position updates.

**Key Features**:

- Order state machine (9 states)
- Pre-trade validation via K-03
- Maker-checker for large orders
- Position calculation with weighted average cost
- AI-powered order suggestions via K-09
- Partial fill handling

**Dependencies**: K-02 (order limits), K-03 (validation), K-05 (events), K-07 (audit), K-09 (AI)

**Extension Points**:

- Custom order validators (jurisdiction-specific)
- Custom order types (e.g., ICEBERG)
- AI optimization hooks (e.g., VWAP)

**Status**: ✅ LLD authored

---

### D-02 Execution Management System (EMS)

**File**: `lld/LLD_D02_EMS.md`

**Purpose**: Smart order routing, execution algorithms (VWAP/TWAP), T3 exchange adapters, TCA capture, and circuit breaker handling.

**Key Features**:

- Smart Order Router (SOR) with venue scoring
- Execution algorithms: VWAP, TWAP, Implementation Shortfall
- T3 exchange adapter framework (FIX 4.4/5.0, proprietary APIs)
- Transaction Cost Analysis (TCA) capture
- Circuit breaker detection and order suspension
- Partial fill aggregation

**Dependencies**: D-01 (orders), D-04 (market data), K-02, K-03, K-04, K-05, K-07, K-15, K-18

**Extension Points**:

- T3 exchange adapters (new venues)
- Custom execution algorithms (T2)
- TCA model plugins

**Status**: ✅ LLD authored

---

### D-03 Portfolio Management System (PMS)

**File**: `lld/LLD_D03_PMS.md`

**Purpose**: Portfolio construction, NAV calculation, rebalancing with drift detection, and position tracking via K-16 ledger.

**Key Features**:

- Portfolio construction with constraint optimization
- NAV calculation (time-weighted, money-weighted returns)
- Drift detection and automatic rebalancing triggers
- Position tracking via K-16 Ledger Framework
- Multi-asset, multi-currency portfolio support
- What-if scenario analysis

**Dependencies**: K-02, K-05, K-07, K-15, K-16, D-05 (pricing)

**Extension Points**:

- Custom portfolio models (T2)
- Rebalancing strategy plugins (T2)
- Performance attribution models

**Status**: ✅ LLD authored

---

### D-04 Market Data Service

**File**: `lld/LLD_D04_MARKET_DATA.md`

**Purpose**: Feed normalization from multiple sources, L1/L2/L3 order book distribution, circuit breaker detection, and historical data storage.

**Key Features**:

- Feed normalization from exchange/vendor sources
- L1 (best bid/ask), L2 (depth), L3 (full order book) distribution
- Circuit breaker detection and MarketHaltEvent emission
- Feed arbitration with primary/secondary failover
- Historical tick storage (TimescaleDB)
- Dual-calendar OHLC aggregation
- WebSocket streaming for real-time subscribers

**Dependencies**: K-02, K-04, K-05, K-07, K-15

**Extension Points**:

- T3 feed adapters for new exchanges/vendors
- T1 instrument mapping configs
- Custom OHLC intervals

**Status**: ✅ LLD authored

---

### D-05 Pricing Engine

**File**: `lld/LLD_D05_PRICING_ENGINE.md`

**Purpose**: Instrument valuation, yield curve construction, mark-to-market (MTM), and T3 model execution for complex pricing.

**Key Features**:

- Real-time and EOD instrument pricing (equity, fixed income, derivatives)
- Yield curve bootstrapping (Nelson-Siegel, cubic spline)
- T3 pricing model plugins for jurisdiction-specific instruments
- Mark-to-market (MTM) batch calculation
- Price challenge workflow with maker-checker
- Dual-calendar settlement date adjustment

**Dependencies**: D-04 (market data), K-02, K-03, K-04, K-05, K-07, K-09, K-15

**Extension Points**:

- T3 pricing model plugins
- T2 jurisdiction-specific valuation rules
- Custom curve types and interpolation methods

**Status**: ✅ LLD authored

---

### D-06 Risk Engine

**File**: `lld/LLD_D06_RISK_ENGINE.md`

**Purpose**: Pre-trade risk checks, real-time exposure monitoring, margin calculation, margin calls, and forced liquidation.

**Key Features**:

- Pre-trade risk validation via K-03 pipeline (<5ms)
- VaR calculation (parametric, historical, Monte Carlo)
- Margin calculation (initial, variation, maintenance)
- Margin call generation and escalation workflow
- Forced liquidation trigger via D-02 EMS
- Stress testing and scenario analysis

**Dependencies**: D-03, D-05, K-02, K-03, K-05, K-07, K-15, K-16, K-18

**Extension Points**:

- T2 jurisdiction-specific margin rules
- T3 custom risk model plugins
- Stress test scenario packs

**Status**: ✅ LLD authored

---

### D-07 Compliance Engine

**File**: `lld/LLD_D07_COMPLIANCE_ENGINE.md`

**Purpose**: Regulatory gatekeeper enforcing trading rules, lock-in periods, KYC/AML, and jurisdiction-specific compliance.

**Key Features**:

- Pre-trade compliance validation (lock-in, insider trading)
- KYC/AML onboarding and ongoing due diligence
- Lock-in period enforcement (BS calendar-aware)
- Beneficial ownership tracking with threshold alerting
- Restricted list management
- Compliance attestation workflows

**Dependencies**: K-01, K-02, K-03, K-05, K-07, K-15, K-18, D-14

**Extension Points**:

- T2 jurisdiction-specific compliance rules via K-03
- T3 regulatory portal adapters
- Custom KYC workflows

**Status**: ✅ LLD authored

---

### D-08 Market Surveillance

**File**: `lld/LLD_D08_SURVEILLANCE.md`

**Purpose**: Market abuse detection (wash trades, spoofing, front-running), AI anomaly detection, and case management.

**Key Features**:

- Wash trade detection (self-dealing across related accounts)
- Spoofing/layering detection (order book manipulation)
- Front-running detection (staff vs client timing)
- AI/ML anomaly detection via K-09 governed models
- Alert severity classification and case management
- Evidence collection with SHA-512 immutability

**Dependencies**: K-02, K-03, K-05, K-06, K-07, K-09, K-15, K-18

**Extension Points**:

- T2 jurisdiction-specific detection rules
- T3 exchange surveillance data adapters
- Custom ML models via K-09

**Status**: ✅ LLD authored

---

### D-09 Post-Trade Processing

**File**: `lld/LLD_D09_POST_TRADE.md`

**Purpose**: Trade confirmation, netting, settlement lifecycle (T+n), clearing integration, and ledger posting via K-16.

**Key Features**:

- Trade confirmation generation and distribution
- Bilateral and multilateral netting
- Settlement lifecycle (T+0 to T+n via K-15 dual-calendar)
- Settlement instruction generation for CSD/depository
- Ledger posting (K-16 double-entry)
- Settlement failure management and buy-in procedures

**Dependencies**: D-01, K-02, K-03, K-05, K-07, K-15, K-16, K-17

**Extension Points**:

- T3 CSD/depository adapters
- T2 jurisdiction-specific netting rules
- Custom settlement cycle configs

**Status**: ✅ LLD authored

---

### D-10 Regulatory Reporting

**File**: `lld/LLD_D10_REGULATORY_REPORTING.md`

**Purpose**: Automated report generation, multi-format rendering (PDF/CSV/XBRL), regulatory portal submission, and ACK tracking.

**Key Features**:

- Report definition management (templates, schedules)
- Multi-format rendering: PDF, CSV, XLSX, XBRL, JSON
- T3 regulatory portal adapter submission
- Submission tracking and ACK/NACK handling
- Report versioning and amendment workflows
- Regulatory deadline tracking (BS calendar)

**Dependencies**: K-02, K-04, K-05, K-07, K-15, K-16

**Extension Points**:

- T3 regulatory portal adapters per jurisdiction
- Custom report templates
- T2 jurisdiction-specific data transformation rules

**Status**: ✅ LLD authored

---

### D-11 Reference Data Service

**File**: `lld/LLD_D11_REFERENCE_DATA.md`

**Purpose**: Golden source for instruments (ISIN), legal entities, benchmarks, and versioned master data.

**Key Features**:

- Instrument master data management (ISIN, symbol, lots)
- Legal entity management (issuers, brokers, custodians)
- Benchmark/index definitions and constituents
- Versioned master data with temporal validity
- T3 data provider adapters for external feeds
- Point-in-time historical queries

**Dependencies**: K-02, K-05, K-07, K-15

**Extension Points**:

- T3 data provider adapters for new vendors
- T2 jurisdiction-specific instrument types
- Custom validation rules

**Status**: ✅ LLD authored

---

### D-12 Corporate Actions

**File**: `lld/LLD_D12_CORPORATE_ACTIONS.md`

**Purpose**: Bonus shares, dividends, rights issues, stock splits — entitlement calculation, tax withholding, and ledger posting.

**Key Features**:

- Corporate action lifecycle management
- Entitlement calculation (bonus, dividend, rights, splits)
- T2 Rule Packs for jurisdiction-specific entitlement logic
- Ex-date and record date enforcement (K-15 BS calendar)
- Tax withholding calculation and deduction
- Ledger posting to K-16 (cash and securities)
- Election management (rights issues)

**Dependencies**: D-03, D-11, K-02, K-03, K-05, K-07, K-15, K-16

**Extension Points**:

- T2 jurisdiction-specific tax and entitlement rules via K-03
- T3 CSD adapters for reconciliation
- Custom fractional handling policies

**Status**: ✅ LLD authored

---

### D-13 Client Money Reconciliation

**File**: `lld/LLD_D13_CLIENT_MONEY_RECONCILIATION.md`

**Purpose**: Daily reconciliation of client money obligations against actual holdings with break detection.

**Key Features**:

- Daily automated reconciliation workflow
- Break detection with severity classification (INFO/WARNING/CRITICAL)
- Aging tier escalation (3/5/10 business days)
- Segregation verification (client funds ≥ obligations)
- Regulatory evidence generation (PDF + JSON)
- Maker-checker break resolution workflow
- Multi-source: bank, CDSC, custodian, internal ledger

**Dependencies**: K-05 (events), K-06 (observability), K-07 (audit), K-15 (dual-calendar), K-16 (ledger), K-18 (resilience), D-09 (post-trade), R-02 (escalation)

**Status**: ✅ LLD authored [ARB P1-11 resolved]

---

### D-14 Sanctions Screening

**File**: `lld/LLD_D14_SANCTIONS_SCREENING.md`

**Purpose**: Real-time screening against sanctions lists with fuzzy matching and review workflow.

**Key Features**:

- Real-time screening at order placement (P99 < 50ms)
- Fuzzy matching (Levenshtein, Jaro-Winkler, Soundex, Double Metaphone)
- Match review workflow with maker-checker
- Air-gap support with Ed25519-signed offline list bundles
- Screening decision audit trail
- Batch re-screening for all active clients
- Whitelist cache for cleared entities

**Dependencies**: K-01 (IAM), K-02 (config), K-05 (events), K-07 (audit), K-15 (dual-calendar), K-18 (resilience), D-01 (OMS), D-07 (compliance)

**Status**: ✅ LLD authored [ARB P1-13 resolved]

---

## 4. CROSS-CUTTING CONCERNS

### 4.1 Dual Calendar Support

All modules support **Bikram Sambat (BS)** and **Gregorian** calendars:

```json
{
  "timestamp_bs": "2081-11-17",
  "timestamp_gregorian": "2025-03-02T10:30:00Z"
}
```

**Conversion Library**: `nepali-date-converter` (npm)

### 4.2 Maker-Checker Workflow

Critical operations require dual approval:

1. **Maker** initiates action (status: PENDING_APPROVAL)
2. **Checker** reviews and approves (checker ≠ maker)
3. Action executed (status: APPROVED → ACTIVE)

**Modules with Maker-Checker**:

- K-02: Configuration changes
- K-03: Rule pack deployment
- D-01: Large orders (> threshold)

### 4.3 Event Sourcing Pattern

All state changes are event-sourced:

```
Command → Validation → Event Published → State Updated → Projections Rebuilt
```

**Event Schema**:

```json
{
  "event_id": "uuid",
  "event_type": "OrderPlaced",
  "event_version": "1.0.0",
  "aggregate_id": "order_123",
  "aggregate_type": "Order",
  "sequence_number": 1,
  "data": {...},
  "metadata": {...},
  "timestamp_bs": "2081-11-17",
  "timestamp_gregorian": "2025-03-02T10:30:00Z"
}
```

### 4.4 Security Model

**Authentication**: OAuth 2.0 + JWT  
**Authorization**: RBAC with fine-grained permissions  
**Encryption**: TLS 1.3 (in-transit), AES-256 (at-rest)  
**Tenant Isolation**: Row-level security (PostgreSQL)  
**Zero Trust**: mTLS between services

---

## 5. NFR SUMMARY

### 5.1 Latency Budgets (P99)

| Module | Operation                 | P99 Latency | Timeout | Notes                                                             |
| ------ | ------------------------- | ----------- | ------- | ----------------------------------------------------------------- |
| K-02   | get()                     | 5ms         | 100ms   | Hot-reload config reads                                           |
| K-03   | evaluate()                | 10ms        | 100ms   | Single rule evaluation; pre-trade pipeline orchestrates D-06+D-07 |
| K-04   | invoke()                  | 50ms        | 5000ms  | T3 plugin IPC overhead; T1/T2 < 2ms                               |
| K-05   | publish()                 | 2ms         | 100ms   | Critical path (OMS, Ledger) — must match epic NFR                 |
| K-07   | log()                     | 3ms         | 100ms   | Async batched; synchronous path < 10ms                            |
| K-09   | predict()                 | 500ms       | 5000ms  | ML inference; non-blocking for trading path                       |
| D-01   | processOrder()            | 2ms         | 500ms   | OMS internal only; excludes pre-trade K-03 pipeline               |
| D-01   | placeOrder() (end-to-end) | 12ms        | 1000ms  | Includes OMS + K-03 pre-trade (D-06 risk + D-07 compliance)       |
| D-07   | compliance eval           | 5ms         | 100ms   | Additive to D-01; invoked via K-03 pipeline                       |

### 5.2 Throughput Targets

| Module | Operation    | Target TPS | Peak TPS |
| ------ | ------------ | ---------- | -------- |
| K-02   | get()        | 100,000    | 200,000  |
| K-03   | evaluate()   | 50,000     | 100,000  |
| K-05   | publish()    | 100,000    | 200,000  |
| D-01   | placeOrder() | 50,000     | 100,000  |

### 5.3 Availability

- **Uptime SLA**: 99.999% (trading hours — critical path: K-05, K-16, D-01), 99.99% (off-hours)
- **RTO**: < 5 minutes
- **RPO**: 0 (no data loss for financial transactions)

---

## 6. TECHNOLOGY STACK

### 6.1 Languages & Frameworks

- **Backend**: Java 21 + ActiveJ for kernel/domain/event/workflow/data, Node.js LTS + TypeScript + Fastify + Prisma for user API/control plane, Python 3.11 + FastAPI for AI/ML execution only
- **API**: REST + OpenAPI externally, gRPC + protobuf internally
- **Frontend**: React 18, TailwindCSS, Jotai, TanStack Query

### 6.2 Data Stores

- **Primary DB**: PostgreSQL 15+ (event store, audit logs)
- **Time-Series**: TimescaleDB
- **Cache**: Redis 7+ (configuration, sessions)
- **Search**: Elasticsearch
- **Message Broker**: Kafka 3+ via Ghatana Event Cloud / AEP alignment
- **Object Storage**: S3 / MinIO (artifacts, exports)
- **Vector Search**: pgvector
- **Shared Data Abstractions**: Ghatana Data Cloud platform + SPI

### 6.3 Infrastructure

- **Container**: Docker, Kubernetes
- **Service Mesh**: Istio (mTLS, observability)
- **Observability**: Prometheus, Grafana, Jaeger
- **CI/CD**: GitHub Actions, ArgoCD

### 6.4 Platform Reuse

- **AI/ML**: Ghatana `ai-registry`, `ai-inference-service`, `feature-store-ingest`, `platform/java/ai-integration`
- **Event Processing**: Ghatana `products/aep/platform`, `platform/java/event-cloud`
- **Workflow**: Ghatana `platform/java/workflow`
- **Data Management**: Ghatana `products/data-cloud/platform`, `products/data-cloud/spi`

---

## 7. IMPLEMENTATION ROADMAP

### Phase 1: Kernel Foundation (Weeks 1-8)

- ✅ K-02 Configuration Engine
- ✅ K-05 Event Bus & Event Store
- ✅ K-07 Audit Framework

### Phase 2: Policy & Extensions (Weeks 9-12)

- ✅ K-03 Policy/Rules Engine
- ✅ K-04 Plugin Runtime & SDK

### Phase 3: AI & Intelligence (Weeks 13-16)

- ✅ K-09 AI Governance

### Phase 4: Security, Data & Infrastructure (Weeks 17-20)

- ✅ K-08 Data Governance
- ✅ K-10 Deployment Abstraction Layer
- ✅ K-11 API Gateway
- ✅ K-12 Platform SDK
- ✅ K-13 Admin Portal
- ✅ K-14 Secrets Management

### Phase 5: Core Domain Subsystems (Weeks 21-28)

- ✅ D-01 Order Management System
- ✅ D-02 Execution Management System
- ✅ D-03 Portfolio Management System
- ✅ D-04 Market Data Service
- ✅ D-05 Pricing Engine
- ✅ D-11 Reference Data Service

### Phase 6: Risk, Compliance & Operations (Weeks 29-36)

- ✅ D-06 Risk Engine
- ✅ D-07 Compliance Engine
- ✅ D-08 Market Surveillance
- ✅ D-09 Post-Trade Processing
- ✅ D-10 Regulatory Reporting
- ✅ D-12 Corporate Actions

### Phase 7: Integration & Testing (Weeks 37-44)

- ⏳ End-to-end integration tests
- ⏳ Performance testing
- ⏳ Security audit
- ⏳ UAT with pilot customers

---

## 8. VALIDATION & ASSUMPTIONS

### 8.1 Key Assumptions

1. **Event ordering** only required per aggregate (not global)
2. **Saga compensation** retries with exponential backoff; manual intervention after max retries (see K-17)
3. **Plugin artifacts** < 50MB
4. **Model artifacts** < 1GB
5. **Audit logs** archived after 90 days (10-year retention — SEBON/SEBI cross-jurisdiction safety margin)
6. **HITL overrides** < 1% of AI decisions
7. **Maker-checker** threshold configurable per jurisdiction

### 8.2 Validation Questions

1. Are there long-running workflows (> 5s)?
2. Should there be a global event ordering mechanism?
3. Are there large ML models (> 1GB)?
4. What is expected HITL override rate?
5. Are there GDPR right-to-erasure requirements?
6. Should drift detection be real-time vs. periodic?

---

## 9. GLOSSARY

| Term          | Definition                                                                                                 |
| ------------- | ---------------------------------------------------------------------------------------------------------- |
| **Aggregate** | Domain object with unique identity (e.g., Order, Position)                                                 |
| **BS**        | Bikram Sambat (Nepali calendar)                                                                            |
| **CQRS**      | Command Query Responsibility Segregation                                                                   |
| **HITL**      | Human-in-the-Loop                                                                                          |
| **NFR**       | Non-Functional Requirement                                                                                 |
| **OPA**       | Open Policy Agent                                                                                          |
| **PSI**       | Population Stability Index (drift metric)                                                                  |
| **Rego**      | Policy language for OPA                                                                                    |
| **Saga**      | Distributed transaction pattern with compensation                                                          |
| **SHAP**      | SHapley Additive exPlanations (ML explainability)                                                          |
| **T1/T2/T3**  | Plugin taxonomy tiers: T1=Config (data-only), T2=Rules (declarative OPA/Rego), T3=Executable (signed code) |

---

## 10. DOCUMENT STATISTICS

| Metric                    | Value                        |
| ------------------------- | ---------------------------- |
| Total LLDs (authored)     | 33                           |
| Total LLDs (pending)      | 0                            |
| Kernel Modules Indexed    | 19 (all authored)            |
| Domain Subsystems Indexed | 14 (all authored)            |
| ADRs                      | 11 (ADR-001 through ADR-011) |
| Total Pages               | ~900                         |
| API Endpoints             | 200+                         |
| Event Types               | 120+                         |
| Storage Tables            | 110+                         |
| Test Cases                | 500+                         |

---

## 11. REFERENCES

- **Architecture Specification**: `docs/All_In_One_Capital_Markets_Platform_Specification.md`
- **Epic Files**: `epics/EPIC-*.md`
- **C4 Diagrams**: `c4/C4_*.md`

---

## 12. CHANGE LOG

| Version | Date       | Author        | Changes                                                                                                                                                                                                                                                                                                                                                                                                                    |
| ------- | ---------- | ------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1.0.0   | 2025-03-02 | Platform Team | Initial release of all kernel + OMS LLDs                                                                                                                                                                                                                                                                                                                                                                                   |
| 2.0.0   | 2026-03-03 | Platform Team | Post-ARB remediation: added K-17/K-18/K-19, D-13/D-14 index entries (LLDs pending), updated statistics, fixed stale assumptions                                                                                                                                                                                                                                                                                            |
| 2.1.0   | 2026-03-05 | Platform Team | Hardening pass: added K-01/K-06/K-15/K-16 index entries, fixed NFR budget mismatches (K-05 publish 50ms→2ms, D-01 placeOrder 300ms→12ms, availability 99.95→99.999%), corrected T1/T2/T3 glossary definition, aligned data retention to 10yr, added latency budget decomposition notes                                                                                                                                     |
| 2.2.0   | 2026-03-05 | Platform Team | All 9 pending LLDs authored: K-01 IAM, K-06 Observability, K-15 Dual-Calendar, K-16 Ledger Framework, K-17 DTC, K-18 Resilience, K-19 DLQ, D-13 Client Money Recon, D-14 Sanctions Screening. Updated all file links, statistics, and navigation. ARB P0-01/P0-02/P0-04/P1-11/P1-13 resolved.                                                                                                                              |
| 3.0.0   | 2026-03-08 | Platform Team | Full architecture coverage: 17 new LLDs (K-08 Data Governance, K-10 Deployment, K-11 API Gateway, K-12 SDK, K-13 Admin Portal, K-14 Secrets, D-02 EMS, D-03 PMS, D-04 Market Data, D-05 Pricing, D-06 Risk, D-07 Compliance, D-08 Surveillance, D-09 Post-Trade, D-10 Reg Reporting, D-11 Reference Data, D-12 Corporate Actions). 7 new ADRs (004–010). Total: 33 LLDs, 11 ADRs. Updated roadmap, navigation, statistics. |
| 3.1.0   | 2026-03-09 | Platform Team | Added ADR-011 stack standardization baseline, aligned LLD index stack summary to Java 21 + ActiveJ / Fastify / React + Jotai + TanStack Query, and referenced Ghatana AI, event, workflow, and data platform products.                                                                                                                                                                                                     |
| 3.1.1   | 2026-03-10 | Platform Team | Refreshed current summaries: corrected K-02 hierarchy to 6 levels, added metadata-asset governance to K-02 summary, added schema-driven and value-catalog-backed rendering to K-13 summary, and synchronized header version/date with the current documentation baseline.                                                                                                                                                  |

---

## 13. NAVIGATION GUIDE

### By Concern

**Configuration & Policy**:

- K-02 Configuration Engine
- K-03 Policy/Rules Engine

**Identity & Security**:

- K-01 Identity & Access Management
- K-08 Data Governance
- K-11 API Gateway
- K-14 Secrets Management

**Extensibility**:

- K-04 Plugin Runtime & SDK
- K-12 Platform SDK

**Event & Workflow**:

- K-05 Event Bus & Workflow Orchestration

**Governance & Observability**:

- K-06 Observability Stack
- K-07 Audit Framework
- K-09 AI Governance

**Resilience & Transactions**:

- K-17 Distributed Transaction Coordinator
- K-18 Resilience Patterns Library
- K-19 DLQ Management & Event Replay

**Infrastructure & Operations**:

- K-10 Deployment Abstraction Layer
- K-13 Admin Portal
- K-15 Dual-Calendar Service
- K-16 Ledger Framework

**Trading & Execution**:

- D-01 Order Management System
- D-02 Execution Management System
- D-04 Market Data Service

**Portfolio & Pricing**:

- D-03 Portfolio Management System
- D-05 Pricing Engine

**Risk & Compliance**:

- D-06 Risk Engine
- D-07 Compliance Engine
- D-08 Market Surveillance
- D-14 Sanctions Screening

**Post-Trade & Operations**:

- D-09 Post-Trade Processing
- D-10 Regulatory Reporting
- D-12 Corporate Actions
- D-13 Client Money Reconciliation

**Reference Data**:

- D-11 Reference Data Service

### By Dependency Order

1. K-02 Configuration Engine (no dependencies)
2. K-07 Audit Framework (K-02)
3. K-05 Event Bus (K-02, K-07)
4. K-01 IAM (K-02, K-05, K-07)
5. K-03 Rules Engine (K-02, K-05, K-07)
6. K-04 Plugin Runtime (K-02, K-05, K-07)
7. K-06 Observability (K-02)
8. K-14 Secrets Management (K-01, K-02, K-07)
9. K-08 Data Governance (K-01, K-02, K-05, K-06, K-07, K-14)
10. K-09 AI Governance (K-02, K-05, K-07)
11. K-15 Dual-Calendar (K-02)
12. K-16 Ledger Framework (K-02, K-05, K-15, K-17)
13. K-17 DTC (K-05, K-06, K-07, K-16, K-19)
14. K-18 Resilience (K-02, K-05, K-06)
15. K-19 DLQ Management (K-05, K-06, K-07, K-15)
16. K-10 Deployment Abstraction (K-02, K-04, K-06, K-07)
17. K-11 API Gateway (K-01, K-02, K-05, K-06, K-07)
18. K-12 Platform SDK (all services, K-01, K-18)
19. K-13 Admin Portal (K-01, K-02, K-04, K-06, K-07, K-09, K-10, K-15)
20. D-11 Reference Data (K-02, K-05, K-07, K-15)
21. D-04 Market Data (K-02, K-04, K-05, K-07, K-15)
22. D-05 Pricing Engine (D-04, K-02, K-03, K-04, K-05, K-07, K-09, K-15)
23. D-01 OMS (K-02, K-03, K-05, K-07, K-09)
24. D-02 EMS (D-01, D-04, K-02, K-03, K-04, K-05, K-07, K-15, K-18)
25. D-03 PMS (K-02, K-05, K-07, K-15, K-16, D-05)
26. D-06 Risk (D-03, D-05, K-02, K-03, K-05, K-07, K-15, K-16, K-18)
27. D-07 Compliance (K-01, K-02, K-03, K-05, K-07, K-15, K-18, D-14)
28. D-08 Surveillance (K-02, K-03, K-05, K-06, K-07, K-09, K-15, K-18)
29. D-09 Post-Trade (D-01, K-02, K-03, K-05, K-07, K-15, K-16, K-17)
30. D-10 Regulatory Reporting (K-02, K-04, K-05, K-07, K-15, K-16)
31. D-12 Corporate Actions (D-03, D-11, K-02, K-03, K-05, K-07, K-15, K-16)
32. D-13 Client Money Reconciliation (K-05, K-06, K-07, K-15, K-16, K-18)
33. D-14 Sanctions Screening (K-01, K-02, K-05, K-07, K-15, K-18)

---

**END OF LLD INDEX**

For questions or clarifications, contact: platform-architecture@siddhanta.io
