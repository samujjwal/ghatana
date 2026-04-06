# System Architecture Overview - Finance Product

## 1. System Context

**Observed in code** - The Finance product is a comprehensive financial services platform implementing trading, risk management, compliance, and AI governance. It uses a two-level composition pattern with kernel-level services and domain-level business logic.

### Context Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                        External Systems                          │
│  (Market Data Feeds, Risk Models, Regulatory Systems)           │
└───────────────────────────────┬─────────────────────────────────┘
                                │
┌───────────────────────────────▼─────────────────────────────────┐
│                     Finance Product                              │
│                                                                  │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                    BFF Layer                             │  │
│  │           (FinanceBFF - API composition)                │  │
│  └─────────────────────────┬─────────────────────────────────┘  │
│                            │                                     │
│  ┌─────────────────────────▼─────────────────────────────────┐  │
│  │              Finance Product Module                        │  │
│  │   ┌──────────────┐            ┌───────────────────────┐  │  │
│  │   │ FinanceKernel│            │  14 Domain Modules    │  │  │
│  │   │   Module     │◄──────────►│  • OMS, EMS, PMS     │  │  │
│  │   │ (6 services) │            │  • Risk, Compliance  │  │  │
│  │   └──────────────┘            │  • Market Data, etc. │  │  │
│  │                               └───────────────────────┘  │  │
│  │   ┌──────────────────────────────────────────────────┐  │  │
│  │   │         AI Governance Layer                       │  │  │
│  │   │  • Model Approval Workflow                       │  │  │
│  │   │  • Fraud Detection Agent                         │  │  │
│  │   │  • Autonomy Manager                              │  │  │
│  │   │  • Performance Tracking                          │  │  │
│  │   └──────────────────────────────────────────────────┘  │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                  │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │            Finance Product Shell                         │  │
│  │         (Product lifecycle management)                  │  │
│  └───────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────┘
                                │
                                │ Kernel Platform Services
┌───────────────────────────────▼─────────────────────────────────┐
│                     Kernel Platform                            │
│  (Lifecycle, Registry, Capabilities, Context, DataCloud)       │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2. Primary Components

**Observed in source structure**:

### Kernel Level (`src/main/java/com/ghatana/finance/kernel/`)

| Component | Responsibility | Key Files |
|-----------|---------------|-----------|
| **FinanceKernelModule** | Cross-cutting service composition | `FinanceKernelModule.java` |
| **Capabilities** | Finance feature declarations | `FinanceCapabilities.java` |
| **Order Management** | Trade order processing | `service/OrderManagementService.java` |
| **Risk Management** | Real-time risk assessment | `service/RiskManagementService.java` |
| **Compliance** | Regulatory compliance | `service/ComplianceService.java` |
| **Portfolio Management** | Portfolio tracking | `service/PortfolioManagementService.java` |
| **Ledger Management** | Double-entry bookkeeping | `service/LedgerManagementService.java` |
| **Market Data** | Market data processing | `service/MarketDataService.java` |

### Domain Level (`domains/`)

| Domain | Purpose | Key Module |
|--------|---------|------------|
| **OMS** | Order Management System | `OmsDomainModule.java` |
| **EMS** | Execution Management System | `EmsDomainModule.java` |
| **PMS** | Portfolio Management System | `PmsDomainModule.java` |
| **Risk** | Risk Management | `RiskDomainModule.java` |
| **Compliance** | Regulatory Compliance | `ComplianceDomainModule.java` |
| **Market Data** | Market data ingestion | `MarketDataDomainModule.java` |
| **Post-Trade** | Post-trade processing | `PostTradeDomainModule.java` |
| **Pricing** | Valuation and pricing | `PricingDomainModule.java` |
| **Reconciliation** | Trade reconciliation | `ReconciliationDomainModule.java` |
| **Reference Data** | Reference data management | `ReferenceDataDomainModule.java` |
| **Regulatory Reporting** | MiFID II, EMIR, SFTR | `RegulatoryReportingDomainModule.java` |
| **Sanctions** | Sanctions screening | `SanctionsDomainModule.java` |
| **Surveillance** | Trade surveillance | `SurveillanceDomainModule.java` |
| **Corporate Actions** | Corporate actions processing | `CorporateActionsDomainModule.java` |
| **Rules** | Finance rules engine | `FinanceRulesDomain.java` |

### AI Governance (`src/main/java/com/ghatana/finance/ai/`)

| Component | Responsibility | Key Files |
|-----------|---------------|-----------|
| **Model Governance** | Model approval workflow | `FinanceModelGovernanceImpl.java` |
| **Autonomy Manager** | Human-in-the-loop | `FinanceAutonomyManagerImpl.java` |
| **Agent Orchestrator** | AI agent coordination | `FinanceAgentOrchestratorImpl.java` |
| **Fraud Detection** | Fraud detection agent | `agents/FraudDetectionAgent.java` |
| **Risk Assessment** | Risk assessment agent | `agents/RiskAssessmentAgent.java` |
| **Model Repository** | Model metadata storage | `ModelRepository.java` |
| **Performance Tracking** | Model performance | `ModelPerformanceRepository.java` |

### Contracts (`src/main/java/com/ghatana/finance/contracts/`)

| Component | Responsibility | Key Files |
|-----------|---------------|-----------|
| **Contract Definitions** | API/Schema contracts | `FinanceContracts.java` |
| **Validation Runner** | CI/CD validation | `ContractValidationRunner.java` |

---

## 3. Data Flow

### AI Governance Decision Flow

```
┌──────────────┐
│  Transaction │
│   Request    │
└──────┬───────┘
       │
       ▼
┌─────────────────────────────────────────────────────────┐
│  1. TRANSACTION SERVICE                                 │
│     - Receives transaction request                      │
│     - Calls AgentOrchestrator                         │
└─────────────────────────────────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────────────────────────┐
│  2. AGENT ORCHESTRATION                                 │
│     - Coordinates FraudDetectionAgent                 │
│     - Validates model approval via ModelGovernance    │
│     - Records performance metrics                     │
└─────────────────────────────────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────────────────────────┐
│  3. MODEL GOVERNANCE                                    │
│     - Checks if model is approved                       │
│     - Validates SOX compliance                          │
│     - Throws ModelNotApprovedException if not approved  │
└─────────────────────────────────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────────────────────────┐
│  4. FRAUD DETECTION                                     │
│     - Analyzes transaction patterns                   │
│     - Calculates fraud score (0-1)                     │
│     - Provides feature importance (explainability)     │
└─────────────────────────────────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────────────────────────┐
│  5. AUTONOMY MANAGEMENT                                 │
│     - High-value check (>$100k)                       │
│     - Fraud score > 0.8 triggers human review         │
│     - Routes to human review queue if needed          │
└─────────────────────────────────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────────────────────────┐
│  6. DECISION LOGGING                                    │
│     - Records autonomous decision                     │
│     - Includes explanation and audit trail              │
│     - SOX-compliant retention (7 years)                │
└─────────────────────────────────────────────────────────┘
       │
       ▼
┌──────────────┐
│   Decision   │
│ (Approve/    │
│  Reject/     │
│  Review)     │
└──────────────┘
```

**Evidence**: `@/home/samujjwal/Developments/ghatana/products/finance/FINANCE_KERNEL_INTEGRATION_SUMMARY.md:159-195`

---

## 4. Trust Boundaries

**Observed in AI governance and security**:

| Boundary | Mechanism | Evidence |
|----------|-----------|----------|
| **Model Approval** | ModelNotApprovedException | `FinanceModelGovernanceImpl` validates before execution |
| **Human-in-the-Loop** | AutonomyManager routing | `FinanceAutonomyManagerImpl` - transactions >$100k require review |
| **Fraud Scoring** | Threshold-based (0.8) | `FraudDetectionAgent` - scores above threshold trigger review |
| **Decision Audit** | Immutable logging | All autonomous decisions logged with explanation |
| **SOX Compliance** | Certification check | Models must have SOX certification |

---

## 5. Failure Boundaries

**Observed in implementation**:

| Failure Scenario | Handling Strategy | Evidence |
|------------------|-------------------|----------|
| **Unapproved Model** | Exception with rejection | `ModelNotApprovedException.java` |
| **High Fraud Score** | Human review queue | AutonomyManager conditional routing |
| **High-Value Transaction** | Mandatory human review | >$100k threshold in AutonomyManager |
| **AI Agent Error** | Graceful degradation | Agent error handling patterns |
| **Performance Degradation** | Alert + potential failover | <95% accuracy triggers alert |
| **Contract Violation** | CI/CD gate failure | `ContractValidationRunner` exit codes |

---

## 6. Deployment Boundaries

**Inferred from architecture**:

| Component | Deployment Unit | Scaling Strategy |
|-----------|-----------------|------------------|
| **Finance Services** | Java application | Horizontal with load balancing |
| **AI Model Inference** | In-process / External | Depends on model complexity |
| **Distributed Cache** | Redis/Hazelcast | Clustered for multi-node consistency |
| **Decision Audit Log** | Persistent storage | Write-optimized, 7-year retention |
| **Market Data** | Streaming infrastructure | High-throughput, low-latency |

---

## 7. External Integrations

**Observed in capabilities and dependencies**:

| Integration | Status | Interface |
|-------------|--------|-----------|
| **Market Data Feed** | Required | `market-data-feed` external service |
| **Risk Models** | Required | `risk-models` external service |
| **Kernel Platform** | ✅ Complete | `FinanceKernelModule` |
| **PHR Billing** | ✅ Complete | `platform:java:billing` shared contracts |
| **Regulatory Systems** | Inferred | MiFID II, EMIR, SFTR reporting |

---

## 8. Runtime Relationships

### Domain Module Relationships

```
FinanceProductModule (Composition Root)
    ├── FinanceKernelModule (Cross-cutting)
    │   ├── OrderManagementService
    │   ├── RiskManagementService
    │   ├── ComplianceService
    │   ├── PortfolioManagementService
    │   ├── LedgerManagementService
    │   └── MarketDataService
    │
    ├── Domain Modules (Business Logic)
    │   ├── OMS (Order Management)
    │   ├── EMS (Execution) ──► OMS
    │   ├── PMS (Portfolio) ──► Market Data, Risk
    │   ├── Risk (Risk Mgmt) ──► Market Data
    │   ├── Compliance ──► All domains
    │   ├── Market Data (feeds) ──► All trading domains
    │   ├── Post-Trade ──► OMS, EMS
    │   ├── Pricing ──► Market Data
    │   ├── Reconciliation ──► Post-Trade
    │   ├── Reference Data ──► All domains
    │   ├── Regulatory Reporting ──► All domains
    │   ├── Sanctions ──► OMS, EMS
    │   ├── Surveillance ──► OMS, EMS
    │   └── Corporate Actions ──► PMS
    │
    ├── AI Governance
    │   ├── ModelGovernanceService
    │   ├── AgentOrchestrator
    │   ├── AutonomyManager
    │   └── FraudDetectionAgent
    │
    ├── Contracts
    │   └── ContractValidationRunner
    │
    ├── Product Shell
    │   └── FinanceProductShell
    │
    └── BFF
        └── FinanceBFF
```

---

## 9. Architecture Patterns

**Observed throughout codebase**:

| Pattern | Implementation | Evidence |
|---------|----------------|----------|
| **Composition Root** | `FinanceProductModule` | Central composition |
| **Two-Level Composition** | Kernel + Domain modules | Architecture specification |
| **Module Pattern** | `KernelModule` implementations | Lifecycle management |
| **Service Pattern** | Business services in each domain | Service layering |
| **Agent Pattern** | `FraudDetectionAgent` | AI governance |
| **Repository Pattern** | `ModelRepository`, etc. | Data access |
| **Contract Pattern** | `FinanceContracts` | API governance |
| **BFF Pattern** | `FinanceBFF` | Frontend API composition |
| **Circuit Breaker** | Inferred | Resilience patterns |
| **Rate Limiting** | Inferred | API protection |

---

## 10. Technology Stack

**Observed in build.gradle.kts**:

| Layer | Technology | Purpose |
|-------|------------|---------|
| **Language** | Java 21 | Core implementation |
| **Async Framework** | ActiveJ (Promise, Inject, ServiceGraph) | Async operations and DI |
| **AI Framework** | LangChain4j 0.34.0 | AI agent development |
| **OpenAI** | OpenAI Java 0.12.0 | Model inference |
| **Kernel Platform** | platform:java:kernel | Module lifecycle |
| **Database** | PostgreSQL | Persistent storage |
| **Connection Pool** | HikariCP | Database connections |
| **Observability** | Micrometer | Metrics collection |
| **JSON Processing** | Jackson | Data serialization |
| **Testing** | JUnit 5 + AssertJ + Mockito | Testing framework |

---

## 11. Performance Characteristics

**Observed targets in documentation**:

| Metric | Target | Evidence |
|--------|--------|----------|
| Model Approval Check | < 5ms | `FINANCE_KERNEL_INTEGRATION_SUMMARY.md:229` |
| Fraud Detection | < 100ms | Same |
| Contract Validation | < 50ms | Same |
| Agent Orchestration | < 150ms | Same |
| Trade Processing | Microsecond | `FinanceCapabilities.TRADE_PROCESSING` |
| Throughput | 100K TPS | Same |

---

## 12. Architectural Weaknesses

**Identified through code inspection**:

| Weakness | Impact | Recommendation |
|----------|--------|----------------|
| **No frontend implementation** | High - user interface gap | Develop React/Vue frontend |
| **Production load unknown** | High - capacity planning risk | Load testing campaign |
| **AI bias testing missing** | High - fairness risk | Add bias detection tests |
| **Cross-domain integration tests limited** | Medium - edge case risk | Expand integration coverage |
| **Staging not deployed** | Medium - validation gap | Deploy to staging |

---

## 13. Evidence Reference

**Primary Sources**:
- `@/home/samujjwal/Developments/ghatana/products/finance/src/main/java/com/ghatana/products/finance/FinanceProductModule.java`
- `@/home/samujjwal/Developments/ghatana/products/finance/src/main/java/com/ghatana/finance/kernel/FinanceKernelModule.java`
- `@/home/samujjwal/Developments/ghatana/products/finance/src/main/java/com/ghatana/finance/kernel/FinanceCapabilities.java`
- `@/home/samujjwal/Developments/ghatana/products/finance/src/main/java/com/ghatana/finance/ai/FinanceModelGovernanceImpl.java`
- `@/home/samujjwal/Developments/ghatana/products/finance/src/main/java/com/ghatana/finance/ai/agents/FraudDetectionAgent.java`
- `@/home/samujjwal/Developments/ghatana/products/finance/domains/oms/src/main/java/com/ghatana/products/finance/domains/oms/OmsDomainModule.java`

**Documentation**:
- `@/home/samujjwal/Developments/ghatana/products/finance/FINANCE_KERNEL_INTEGRATION_SUMMARY.md`
- `@/home/samujjwal/Developments/ghatana/products/finance/OWNER.md`

---

*Status: Architecture documented from source code evidence with identified gaps and recommendations.*
