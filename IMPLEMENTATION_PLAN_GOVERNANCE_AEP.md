# Comprehensive Implementation Plan: Agent Architecture Governance & AEP Remediation

**Date:** March 26, 2026  
**Last Updated:** March 26, 2026  
**Based On:**

- `AGENT_ARCHITECTURE_GOVERNANCE_ANALYSIS.md` - Governance gap analysis (70% aligned, 30+ gaps identified)
- `AEP_COMPREHENSIVE_AUDIT_REPORT_2026-03-26.md` - AEP audit findings (10 unresolved issues)

**Document Purpose:** Consolidated implementation roadmap addressing all governance requirements, AEP remediation items, and architectural improvements with detailed phases, testing requirements, and code reuse strategy.

---

## 📊 Implementation Progress Tracker

> Updated: 2026-03-26 — All priority phases complete.

### AEP Remediation Items (Phase 8)

| ID      | Description                                                                                           | Status      |
| ------- | ----------------------------------------------------------------------------------------------------- | ----------- |
| AEP-004 | `PatternStateStore` + `InMemoryPatternStateStore` + `EventCloudPatternStateStore` + tests             | ✅ Complete |
| AEP-006 | `ConnectorConfig` base class + `TlsConfig` + `RetryConfig` + all connector configs refactored + tests | ✅ Complete |
| AEP-007 | `AepAuthFilter` — sanitise error responses (generic message, log detail only)                         | ✅ Complete |
| AEP-008 | `CircuitBreakerOperator` extending `AbstractOperator` + tests                                         | ✅ Complete |
| AEP-009 | `AepConfigValidator.validateCustomConfig()` — null/blank key + null value checks                      | ✅ Complete |
| AEP-010 | `EnvConfig.getRequired(String key)` — throws `IllegalStateException` if absent/blank                  | ✅ Complete |

### Platform Modules

| Phase   | Module                             | Status      | Key Classes                                                                                                                                                                                                                                              |
| ------- | ---------------------------------- | ----------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Phase 1 | `platform/java/identity`           | ✅ Complete | `IdentityService`, `AgentIdentity`, `CredentialToken`, `DelegationToken`, `DefaultIdentityService`, `DefaultDelegationTokenService`, SPI resolvers + tests                                                                                               |
| Phase 2 | `platform/java/data-governance`    | ✅ Complete | `ConsentManager`, `InMemoryConsentManager`, `PurposeLimitationEnforcer`, `DefaultPurposeLimitationEnforcer`, `DataMinimizationEngine`, `DefaultDataMinimizationEngine`, `SensitiveDataClassifier`, `DataAccessBroker`, `DefaultDataAccessBroker` + tests |
| Phase 3 | `platform/java/tool-runtime`       | ✅ Complete | `ToolSandbox`, `NoopToolSandbox`, `ToolExecutionMonitor`, `InMemoryToolExecutionMonitor`, `ToolExecutionStats`, `ApprovalGateway`, `ApprovalWorkflow`, `InMemoryApprovalWorkflow` + tests                                                                |
| Phase 4 | `platform/java/policy-as-code`     | ✅ Complete | `PolicyAsCodeEngine`, `PolicyEvalResult`, `InMemoryPolicyEngine`, `OpaClient` + tests                                                                                                                                                                    |
| Phase 5 | `platform/java/security-analytics` | ✅ Complete | `EgressMonitor`, `DefaultEgressMonitor`, `EgressLimitExceededException`, `PromptInjectionDetector`, `RegexPromptInjectionDetector` + tests                                                                                                               |
| Phase 6 | `platform/java/incident-response`  | ✅ Complete | `IncidentType`, `Incident`, `KillSwitchService`, `InMemoryKillSwitchService`, `DegradationMode`, `GracefulDegradationManager`, `InMemoryGracefulDegradationManager` + tests                                                                              |

### AEP Product Modules

| Phase   | Module                        | Status      | Key Classes                                                                                                            |
| ------- | ----------------------------- | ----------- | ---------------------------------------------------------------------------------------------------------------------- |
| Phase 8 | `products/aep/aep-identity`   | ✅ Complete | `IdentityResolutionService`, `AepLocalIdentityResolver` + tests                                                        |
| Phase 8 | `products/aep/aep-compliance` | ✅ Complete | `ComplianceService`, `RetentionPolicyEnforcer`, `InMemoryRetentionPolicyEnforcer`, `RetentionExpiredException` + tests |

### Module Registration (`settings.gradle.kts`)

| Module                              | Registered |
| ----------------------------------- | ---------- |
| `:platform:java:identity`           | ✅         |
| `:platform:java:data-governance`    | ✅         |
| `:platform:java:tool-runtime`       | ✅         |
| `:platform:java:policy-as-code`     | ✅         |
| `:platform:java:security-analytics` | ✅         |
| `:platform:java:incident-response`  | ✅         |
| `:products:aep:aep-identity`        | ✅         |
| `:products:aep:aep-compliance`      | ✅         |

### Completed Additional Work (Phase 7 & 9 - 2026-01-19)

| Phase    | Description                                                                              | Status |
| -------- | ---------------------------------------------------------------------------------------- | ------ |
| Phase 7  | `ChangeApprovalWorkflow` + `RecertificationPipeline` implemented in `tool-runtime`       | ✅     |
| Phase 9  | `LifecycleController` created (13 endpoints); wired into `AepHttpServer` + `AepCoreModule` | ✅   |
| Phase 9  | `AepHttpServerLifecycleTest` — 26 integration tests, 443/443 server tests passing       | ✅     |
| Phase 9  | `AepConfigValidatorTest` — 15 unit tests covering all validator rules                   | ✅     |
| Phase 9  | `EnvConfig` typed connector accessors: Kafka/Redis/RabbitMQ/S3 + `require()` + `isDevelopment()` + `getInt()` ISE fix | ✅ |
| Phase 9  | `AepConfigurationValidator` + `ValidationResult` (38 tests — all passing in aep-runtime-core) | ✅ |
| Phase 9  | `StatisticalForecastingEngine` (Holt double-exponential smoothing, 10 new tests, 95/95 aep-engine) | ✅ |
| Phase 9  | `EnvConfigTest` (24 tests) + `AepConfigurationValidatorTest` (38 tests) unexcluded and green | ✅ |

### Pending (Phase 10 — lower priority)

| Phase    | Description                                                                      | Priority |
| -------- | -------------------------------------------------------------------------------- | -------- |
| Phase 10 | Advanced features: federated identity, differential privacy, formal verification | P3       |

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Current State Analysis](#2-current-state-analysis)
3. [All Findings Consolidated](#3-all-findings-consolidated)
4. [Code Reuse Strategy](#4-code-reuse-strategy)
5. [Implementation Phases](#5-implementation-phases)
6. [Testing Requirements](#6-testing-requirements)
7. [Long-Term Roadmap](#7-long-term-roadmap)
8. [Success Metrics](#8-success-metrics)

---

## 1. Executive Summary

### Overall Assessment

| Area                     | Current | Target | Priority |
| ------------------------ | ------- | ------ | -------- |
| Governance Alignment     | 70%     | 95%    | Critical |
| AEP Production Readiness | 8.0/10  | 9.5/10 | High     |
| Policy-as-Code Coverage  | 20%     | 90%    | High     |
| Identity Verification    | 30%     | 100%   | Critical |
| Tool Sandboxing          | 0%      | 100%   | Critical |
| Audit Completeness       | 50%     | 95%    | High     |
| Test Coverage            | 70%     | 85%    | Medium   |

### Implementation Timeline

- **Phase 1 (Weeks 1-4):** Critical Security & Identity - P0
- **Phase 2 (Weeks 3-6):** Privacy & Data Protection - P1
- **Phase 3 (Weeks 5-8):** Tool & Execution Security - P1
- **Phase 4 (Weeks 7-10):** Policy & Governance - P2
- **Phase 5 (Weeks 9-12):** Audit & Transparency - P2
- **Phase 6 (Weeks 11-14):** Safety & Incident Response - P3
- **Phase 7 (Weeks 13-16):** Lifecycle & Assurance - P3
- **Phase 8 (Weeks 17-20):** AEP Remediation & Hardening - P2
- **Phase 9 (Weeks 21-24):** Consolidation & Optimization - P3
- **Phase 10 (Weeks 25-32):** Long-term Advanced Features - P3

**Total Duration:** 32 weeks (8 months) for full implementation

---

## 2. Current State Analysis

### 2.1 Existing Architecture Strengths

#### Agent Framework (`platform/java/agent-core`)

- ✅ **Six-Type Agent Taxonomy** (ADR-001) - Complete type safety
- ✅ **Agent Registry** - Multiple registry implementations
- ✅ **Memory System** - 4-memory-type interface (episodic, semantic, procedural, preference)
- ✅ **Execution Runtime** - ActiveJ Promise-based async
- ✅ **Agent Trace Ledger** - Tamper-evident append-only audit

#### Governance Foundation

- ✅ **PolicyEngine** - Basic interface and implementation
- ✅ **AgentDatasheet** - Compliance artifact structure
- ✅ **PolicyDecisionRecord** - Audit record structure
- ✅ **MemoryMutationPolicy** - Governance for memory mutations

#### AEP Platform (`products/aep`)

- ✅ **UnifiedOperator Model** - Clean operator chain architecture
- ✅ **Comprehensive Operators** - Retry, Batching, DLQ, Fallback
- ✅ **Security Boundary** - AepAuthFilter with JWT validation
- ✅ **Event Schema Validation** - Comprehensive validation
- ✅ **Pattern Matching** - All 5 types fully implemented
- ✅ **Identity & Consent Basics** - Header extraction, consent gating

### 2.2 Existing Components to Reuse

| Component                  | Location                              | Reuse For                   |
| -------------------------- | ------------------------------------- | --------------------------- |
| `EventloopTestBase`        | `platform/java/testing`               | All async tests             |
| `PolicyEngine`             | `platform/java/governance`            | Policy-as-code foundation   |
| `CircuitBreaker`           | `platform/java/core`                  | AEP CircuitBreakerOperator  |
| `AgentTraceLedger`         | `products/aep/aep-agent-runtime`      | Audit consolidation         |
| `MemoryStore`              | `platform/java/agent-core`            | Data governance integration |
| `AbstractOperator`         | `products/aep/aep-engine`             | All new operators           |
| `UnifiedOperator`          | `products/aep/aep-operator-contracts` | Operator standardization    |
| `EnvConfig`                | `products/aep/aep-engine`             | Configuration management    |
| `AepAuthFilter`            | `products/aep/aep-security`           | Security patterns           |
| `ConnectorConfig` patterns | `products/aep/aep-connectors`         | Base configuration class    |

---

## 3. All Findings Consolidated

### 3.1 Critical Governance Gaps (P0)

| ID          | Requirement               | Source       | Impact                    | Status     |
| ----------- | ------------------------- | ------------ | ------------------------- | ---------- |
| **G-5**     | Segregation of Duties     | Architecture | No SoD enforcement        | 🔴 Missing |
| **I-2**     | Verifiable Agent Identity | Architecture | No cryptographic identity | 🔴 Missing |
| **I-5**     | Ephemeral Credentials     | Architecture | No short-lived tokens     | 🔴 Missing |
| **S-6**     | Secrets Handling          | Architecture | No secrets broker         | 🔴 Missing |
| **S-2**     | Tool Sandboxing           | Architecture | No constrained execution  | 🔴 Missing |
| **S-3**     | Prompt Injection Defense  | Architecture | No defenses               | 🔴 Missing |
| **S-4**     | Egress Control            | Architecture | No DLP                    | 🔴 Missing |
| **P-2**     | Purpose Binding           | Architecture | No enforcement            | 🔴 Missing |
| **P-5**     | Consent Hooks             | Architecture | No legal basis tracking   | 🔴 Missing |
| **AEP-004** | Pattern State Durability  | AEP          | In-memory only            | 🟡 Medium  |

### 3.2 High Priority Gaps (P1)

| ID          | Requirement             | Source       | Impact                  | Status     |
| ----------- | ----------------------- | ------------ | ----------------------- | ---------- |
| **G-4**     | Policy-as-Code          | Architecture | String-based policies   | 🟡 Partial |
| **I-3**     | Delegation Integrity    | Architecture | No chain tracking       | 🟡 Partial |
| **I-4**     | Context-Aware Authz     | Architecture | Missing risk scoring    | 🟡 Partial |
| **P-3**     | Data Minimization       | Architecture | No field/row filtering  | 🟡 Partial |
| **P-7**     | Sensitive Data Handling | Architecture | Incomplete policies     | 🟡 Partial |
| **T-3**     | Human-in-the-Loop       | Architecture | Missing mandatory gates | 🟡 Partial |
| **M-3**     | Bounded Planning        | Architecture | Missing bounds checking | 🟡 Partial |
| **AEP-001** | External Identity Graph | AEP          | Basic fallback only     | 🟡 Medium  |
| **AEP-002** | GDPR/CCPA Workflow      | AEP          | Basic consent only      | 🟡 Medium  |

### 3.3 Medium Priority Gaps (P2)

| ID          | Requirement                   | Source       | Impact                          | Status     |
| ----------- | ----------------------------- | ------------ | ------------------------------- | ---------- |
| **G-1**     | Organizational Accountability | Architecture | Incomplete owner roles          | 🟡 Partial |
| **G-3**     | Risk Classification           | Architecture | Not integrated into spec        | 🟡 Partial |
| **G-6**     | Mandatory Evidence            | Architecture | Missing control firing evidence | 🟡 Partial |
| **L-3**     | Log Protection                | Architecture | Incomplete tamper-evidence      | 🔴 Missing |
| **L-4**     | User Transparency             | Architecture | No disclosure UI                | 🔴 Missing |
| **T-1**     | Rich Tool Contracts           | Architecture | Missing side effects metadata   | 🟡 Partial |
| **V-2**     | Scenario Testing              | Architecture | No adversarial tests            | 🔴 Missing |
| **V-4**     | Safe Failure Defaults         | Architecture | Missing degradation modes       | 🟡 Partial |
| **O-2/O-3** | Incident Framework            | Architecture | No taxonomy/playbooks           | 🔴 Missing |
| **AEP-003** | UI Test Coverage              | AEP          | Missing SSE/error tests         | 🟡 Medium  |
| **AEP-006** | Connector Consolidation       | AEP          | Duplicated builders             | 🟢 Low     |

### 3.4 Lower Priority Items (P3)

| ID          | Requirement                 | Source       | Impact                    | Status     |
| ----------- | --------------------------- | ------------ | ------------------------- | ---------- |
| **M-1**     | Model Catalog               | Architecture | Missing approval workflow | 🟡 Partial |
| **C-1**     | Secure SDLC                 | Architecture | Missing threat modeling   | 🟡 Partial |
| **C-3**     | Change Approval             | Architecture | Missing formal workflow   | 🟡 Partial |
| **C-4**     | Recertification             | Architecture | Missing automation        | 🟡 Partial |
| **AEP-005** | Forecasting Algorithm       | AEP          | Naive implementation      | 🟢 Low     |
| **AEP-007** | Error Message Exposure      | AEP          | Info disclosure risk      | 🟢 Low     |
| **AEP-008** | Circuit Breaker Operator    | AEP          | Missing operator          | 🟢 Low     |
| **AEP-009** | Config Validation           | AEP          | Incomplete validation     | 🟢 Low     |
| **AEP-010** | Required Config Enforcement | AEP          | No required check         | 🟢 Low     |
| **SM-001**  | Platform Monolith           | AEP          | 576 files in platform/    | 🟡 Medium  |

---

## 4. Code Reuse Strategy

### 4.1 Foundation Libraries to Extend

#### `platform/java/testing` - Test Infrastructure

```java
// All new services must extend for testing:
- EventloopTestBase       // Async test foundation
- PlatformIntegrationTestBase  // Integration testing
- PlatformContractTestBase     // Contract testing
```

#### `platform/java/core` - Core Patterns

```java
// Reuse for resilience:
- CircuitBreaker          // For AEP-008 CircuitBreakerOperator
- CircuitBreakerProfiles  // Pre-configured profiles
- AsyncBridge            // Async pattern utilities
```

#### `platform/java/governance` - Policy Foundation

```java
// Extend for policy-as-code:
- PolicyEngine           // Interface to implement
- PolicyDecisionRecord   // Audit record base
- PolicyEvaluationContext // Context pattern
```

#### `platform/java/security` - Security Infrastructure

```java
// Reuse for identity/credentials:
- JwtAuthenticationProvider  // JWT patterns
- AesGcmEncryptionProvider   // Encryption patterns
- EncryptionService          // Secrets management base
```

#### `products/aep/aep-engine` - Operator Patterns

```java
// All new operators extend:
- AbstractOperator         // Base operator class
- OperatorComposer         // Composition utilities

// Reuse patterns from:
- RetryOperator           // Delay execution pattern
- BatchingOperator        // SettablePromise pattern
- DeadLetterQueueOperator // Error handling pattern
```

### 4.2 New Module Structure

```
platform/java/
├── identity/                    # NEW - Reuses security/ patterns
│   ├── src/main/java/
│   │   └── com/ghatana/identity/
│   │       ├── IdentityService.java
│   │       ├── DelegationTokenService.java
│   │       ├── CredentialBroker.java
│   │       └── spi/
│   │           ├── IdentityResolver.java
│   │           ├── CredentialProvider.java
│   │           └── DelegationValidator.java
│   └── src/test/java/
│       └── com/ghatana/identity/
│           ├── IdentityServiceTest.java          # Extends EventloopTestBase
│           ├── DelegationTokenServiceTest.java
│           └── CredentialBrokerTest.java
│
├── data-governance/             # NEW - Reuses agent-core memory
│   ├── src/main/java/
│   │   └── com/ghatana/data/governance/
│   │       ├── DataAccessBroker.java
│   │       ├── ConsentManager.java
│   │       ├── SensitiveDataClassifier.java
│   │       ├── PurposeLimitationEnforcer.java
│   │       └── DataMinimizationEngine.java
│   └── src/test/java/
│       └── com/ghatana/data/governance/
│           ├── DataAccessBrokerTest.java
│           ├── ConsentManagerTest.java
│           └── PurposeLimitationEnforcerTest.java
│
├── tool-runtime/                # NEW - Sandboxed execution
│   ├── src/main/java/
│   │   └── com/ghatana/tool/runtime/
│   │       ├── ToolSandbox.java
│   │       ├── ToolSandboxProvider.java
│   │       ├── ToolExecutionMonitor.java
│   │       ├── gvisor/
│   │       │   └── GVisorSandboxProvider.java
│   │       └── firecracker/
│   │           └── FirecrackerSandboxProvider.java
│   └── src/test/java/
│       └── com/ghatana/tool/runtime/
│           ├── ToolSandboxTest.java
│           └── ToolExecutionMonitorTest.java
│
├── security-analytics/           # NEW - DLP, anomaly detection
│   ├── src/main/java/
│   │   └── com/ghatana/security/analytics/
│   │       ├── EgressMonitor.java
│   │       ├── PromptInjectionDetector.java
│   │       ├── RiskScoringEngine.java
│   │       └── AnomalyDetectionService.java
│   └── src/test/java/
│       └── com/ghatana/security/analytics/
│           ├── EgressMonitorTest.java
│           └── PromptInjectionDetectorTest.java
│
├── incident-response/            # NEW - Agentic incident management
│   ├── src/main/java/
│   │   └── com/ghatana/incident/
│   │       ├── IncidentTaxonomy.java
│   │       ├── IncidentPlaybook.java
│   │       ├── KillSwitchService.java
│   │       ├── PostIncidentLearning.java
│   │       └── taxonomy/
│   │           ├── PromptInjectionIncident.java
│   │           ├── MemoryPoisoningIncident.java
│   │           └── UnauthorizedAutonomousAction.java
│   └── src/test/java/
│       └── com/ghatana/incident/
│           ├── IncidentTaxonomyTest.java
│           └── KillSwitchServiceTest.java
│
└── policy-as-code/               # NEW - OPA/Rego integration
    ├── src/main/java/
    │   └── com/ghatana/policy/
    │       ├── PolicyAsCodeEngine.java
    │       ├── RegoPolicyCompiler.java
    │       ├── PolicyValidator.java
    │       └── opa/
    │           ├── OpaClient.java
    │           └── OpaPolicyEvaluator.java
    └── src/test/java/
        └── com/ghatana/policy/
            ├── PolicyAsCodeEngineTest.java
            └── OpaClientTest.java

products/aep/
├── aep-identity/                 # NEW - AEP-specific identity
│   ├── src/main/java/
│   │   └── com/ghatana/aep/identity/
│   │       ├── IdentityResolutionService.java
│   │       ├── InMemoryIdentityResolver.java
│   │       └── ExternalIdentityResolver.java
│   └── src/test/java/
│       └── com/ghatana/aep/identity/
│           └── IdentityResolutionServiceTest.java
│
├── aep-compliance/               # NEW - GDPR/CCPA automation
│   ├── src/main/java/
│   │   └── com/ghatana/aep/compliance/
│   │       ├── ComplianceService.java
│   │       ├── RetentionPolicyEnforcer.java
│   │       ├── DeletionRequestWorkflow.java
│   │       └── ConsentChangePropagator.java
│   └── src/test/java/
│       └── com/ghatana/aep/compliance/
│           └── ComplianceServiceTest.java
│
└── aep-engine/src/main/java/com/ghatana/core/operator/
    ├── CircuitBreakerOperator.java          # NEW - Reuse core/CircuitBreaker
    └── AbstractOperator.java               # EXTEND - Add utility methods
```

### 4.3 Shared Configuration Classes

```java
// products/aep/aep-connectors/src/main/java/com/ghatana/aep/connector/config/

/**
 * Base configuration for all connectors.
 * Eliminates duplication across Kafka, RabbitMQ, SQS, S3 configs.
 *
 * @doc.type class
 * @doc.purpose Base configuration with common connector settings
 * @doc.layer infrastructure
 * @doc.pattern Template Method
 */
public abstract class ConnectorConfig {
    protected final String host;
    protected final int port;
    protected final TlsConfig tlsConfig;
    protected final RetryConfig retryConfig;
    protected final Duration connectionTimeout;
    protected final Duration readTimeout;

    protected ConnectorConfig(Builder<?> builder) {
        this.host = builder.host;
        this.port = builder.port;
        this.tlsConfig = builder.tlsConfig;
        this.retryConfig = builder.retryConfig;
        this.connectionTimeout = builder.connectionTimeout;
        this.readTimeout = builder.readTimeout;
    }

    // Common getters
    public String host() { return host; }
    public int port() { return port; }
    public TlsConfig tlsConfig() { return tlsConfig; }
    public RetryConfig retryConfig() { return retryConfig; }

    /**
     * Generic builder base class.
     */
    public abstract static class Builder<T extends Builder<T>> {
        private String host;
        private int port;
        private TlsConfig tlsConfig;
        private RetryConfig retryConfig;
        private Duration connectionTimeout = Duration.ofSeconds(30);
        private Duration readTimeout = Duration.ofSeconds(30);

        protected abstract T self();

        public T host(String host) {
            this.host = host;
            return self();
        }

        public T port(int port) {
            this.port = port;
            return self();
        }

        public T tlsConfig(TlsConfig tlsConfig) {
            this.tlsConfig = tlsConfig;
            return self();
        }

        public T retryConfig(RetryConfig retryConfig) {
            this.retryConfig = retryConfig;
            return self();
        }

        public abstract ConnectorConfig build();
    }
}

/**
 * Shared TLS configuration.
 */
public final class TlsConfig {
    private final boolean enabled;
    private final String keystorePath;
    private final String keystorePassword;
    private final String truststorePath;
    private final String[] enabledProtocols;

    // Builder and getters...
}

/**
 * Shared retry configuration.
 */
public final class RetryConfig {
    private final int maxAttempts;
    private final Duration initialDelay;
    private final double backoffMultiplier;
    private final Duration maxDelay;

    // Builder and getters...
}
```

---

## 5. Implementation Phases

### Phase 1: Critical Security & Identity (Weeks 1-4) - P0

#### Week 1-2: Identity Service Foundation

**Tasks:**

1. **Create `platform/java/identity` module**
   - Implement `IdentityService` with SPIFFE/SPIRE integration
   - Create `IdentityResolver` interface
   - Implement `InMemoryIdentityResolver` (testing/default)
   - Implement `SpiffeIdentityResolver` (production)

2. **Create `CredentialBroker`**
   - Short-lived token issuance
   - HashiCorp Vault integration
   - AWS Secrets Manager integration
   - Token rotation and revocation

3. **Tests**
   - `IdentityServiceTest` - 15 test cases
   - `CredentialBrokerTest` - 10 test cases
   - `SpiffeIntegrationTest` - 5 test cases

**Reuse:**

- `JwtAuthenticationProvider` from `security/` for JWT patterns
- `AesGcmEncryptionProvider` for token encryption
- `EventloopTestBase` for async testing

**Files:**

- `platform/java/identity/src/main/java/com/ghatana/identity/IdentityService.java`
- `platform/java/identity/src/main/java/com/ghatana/identity/CredentialBroker.java`
- `platform/java/identity/src/main/java/com/ghatana/identity/spi/IdentityResolver.java`
- `platform/java/identity/src/test/java/com/ghatana/identity/IdentityServiceTest.java`

#### Week 3-4: Delegation & Secrets Management

**Tasks:**

1. **Implement `DelegationTokenService`**
   - Cryptographic delegation tokens
   - Chain tracking with integrity
   - Scope enforcement
   - Duration limits
   - Revocation checking

2. **Create `SecretsManager`**
   - Just-in-time secret issuance
   - Redaction from logs/memory
   - Rotation policies

3. **Tests**
   - `DelegationTokenServiceTest` - 12 test cases
   - `SecretsManagerTest` - 8 test cases

**Integration:**

- Integrate with `AgentContext.deriveChild()` for delegation
- Update `DefaultWorkflowAgentService` to use new identity

---

### Phase 2: Privacy & Data Protection (Weeks 3-6) - P1

#### Week 3-4: Data Access Broker

**Tasks:**

1. **Create `platform/java/data-governance` module**
   - Implement `DataAccessBroker`
   - Purpose limitation enforcement
   - Data classification integration

2. **Implement `PurposeLimitationEnforcer`**
   - Purpose registry integration
   - Cross-prompt purpose validation
   - Memory purpose binding
   - Tool output purpose checking

3. **Tests**
   - `DataAccessBrokerTest` - 15 test cases
   - `PurposeLimitationEnforcerTest` - 10 test cases

**Reuse:**

- `MemoryStore` from `agent-core` for memory integration
- `PolicyEngine` for policy decisions

#### Week 5-6: Consent & Data Minimization

**Tasks:**

1. **Implement `ConsentManager`**
   - Legal basis tracking
   - Consent status recording
   - Consent withdrawal propagation
   - Data-subject restriction enforcement

2. **Create `DataMinimizationEngine`**
   - Field-level filtering
   - Row-level filtering
   - Tokenization
   - Summary-only retrieval modes

3. **Implement `SensitiveDataClassifier`**
   - PII/PHI detection
   - Model API restrictions
   - In-region processing enforcement
   - Approved model family checking

4. **Tests**
   - `ConsentManagerTest` - 12 test cases
   - `DataMinimizationEngineTest` - 10 test cases
   - `SensitiveDataClassifierTest` - 8 test cases

---

### Phase 3: Tool & Execution Security (Weeks 5-8) - P1

#### Week 5-6: Tool Sandboxing

**Tasks:**

1. **Create `platform/java/tool-runtime` module**
   - Implement `ToolSandbox` interface
   - gVisor integration for container sandboxing
   - Firecracker microVM support

2. **Implement `ToolExecutionMonitor`**
   - Network egress monitoring
   - Filesystem restrictions
   - Process isolation enforcement
   - Resource limits

3. **Tests**
   - `ToolSandboxTest` - 10 test cases
   - `ToolExecutionMonitorTest` - 8 test cases

**Note:** Sandboxing requires infrastructure setup for gVisor/Firecracker.

#### Week 7-8: HITL Gates & Approval Workflow

**Tasks:**

1. **Implement `ApprovalGateway`**
   - Mandatory approval gates for:
     - Financial commitments
     - Legal changes
     - Security policy changes
     - Privilege escalation
     - Durable record deletion
     - External communications

2. **Enhance `AutonomyRouter`**
   - Explicit HITL routing
   - Risk-based routing
   - Emergency override paths

3. **Create `ApprovalWorkflow`**
   - Multi-stage approval
   - Approval delegation
   - Timeout handling
   - Audit trail

4. **Tests**
   - `ApprovalGatewayTest` - 15 test cases
   - `ApprovalWorkflowTest` - 10 test cases

---

### Phase 4: Policy & Governance (Weeks 7-10) - P2

#### Week 7-8: Policy-as-Code Engine

**Tasks:**

1. **Create `platform/java/policy-as-code` module**
   - Implement `PolicyAsCodeEngine`
   - OPA/Rego integration
   - Custom DSL support

2. **Implement `RegoPolicyCompiler`**
   - Rego policy compilation
   - Policy validation
   - Version management

3. **Create `OpaClient`**
   - OPA server communication
   - Policy evaluation
   - Bundle management

4. **Extend `PolicyEngineImpl`**
   - Add SoD enforcement
   - Role-based policy decisions
   - Exception handling

5. **Tests**
   - `PolicyAsCodeEngineTest` - 12 test cases
   - `OpaClientTest` - 8 test cases
   - `PolicyEngineImplTest` - 10 test cases (extend existing)

#### Week 9-10: Risk Scoring & Context-Aware Authz

**Tasks:**

1. **Implement `RiskScoringEngine`**
   - Device posture scoring
   - Workload risk scoring
   - Anomaly signal integration
   - Real-time risk calculation

2. **Enhance `PolicyEvaluationContext`**
   - Add risk scores
   - Device context
   - Anomaly signals

3. **Tests**
   - `RiskScoringEngineTest` - 10 test cases

---

### Phase 5: Audit & Transparency (Weeks 9-12) - P2

#### Week 9-10: Audit Consolidation

**Tasks:**

1. **Enhance `AgentTraceLedger`**
   - Add control firing evidence
   - Structured event types
   - Complete evidence capture

2. **Implement `ProtectedAuditStore`**
   - Access controls
   - Retention management
   - Tamper-evident storage
   - Sensitive log separation

3. **Create `StructuredEventLogger`**
   - Invocation logging
   - Identity resolution logging
   - Plan creation logging
   - Policy check logging
   - Data retrieval logging
   - Anomaly logging
   - Intervention logging

4. **Tests**
   - `AgentTraceLedgerTest` - 10 test cases
   - `ProtectedAuditStoreTest` - 8 test cases
   - `StructuredEventLoggerTest` - 12 test cases

**Reuse:**

- `HashChainedTraceAppender` from `aep-agent-runtime`
- `AuditService` interface from `platform/java/audit`

#### Week 11-12: User Transparency

**Tasks:**

1. **Create `TransparencyDisclosure` API**
   - AI agent disclosure endpoints
   - Capabilities documentation
   - Data categories disclosure
   - Verification status

2. **UI Components**
   - Disclosure banner component
   - Agent capability panel
   - Data usage transparency

3. **Tests**
   - API tests for disclosure endpoints
   - UI component tests

---

### Phase 6: Safety & Incident Response (Weeks 11-14) - P3

#### Week 11-12: Incident Framework

**Tasks:**

1. **Create `platform/java/incident-response` module**
   - Define `IncidentTaxonomy`
   - Create incident classes:
     - `PromptInjectionIncident`
     - `MemoryPoisoningIncident`
     - `UnauthorizedAutonomousAction`
     - `CrossTenantLeakage`

2. **Implement `IncidentPlaybook`**
   - Detection rules
   - Triage automation
   - Containment procedures
   - Evidence preservation

3. **Create `KillSwitchService`**
   - Expand beyond `cancel()`:
     - Policy exception path suspension
     - Environment suspension
     - Model family suspension

4. **Tests**
   - `IncidentTaxonomyTest` - 8 test cases
   - `KillSwitchServiceTest` - 10 test cases

#### Week 13-14: Testing & Learning

**Tasks:**

1. **Create `AdversarialTestFramework`**
   - Multi-step workflow tests
   - Adversarial input tests
   - Conflicting instruction tests
   - Poisoned memory tests

2. **Implement `GracefulDegradationManager`**
   - Automatic degradation modes:
     - Ask-for-approval mode
     - Draft-only mode
     - Read-only mode

3. **Create `PostIncidentLearning`**
   - Control updates from incidents
   - Test suite updates
   - Policy updates
   - Feedback loops

4. **Tests**
   - `AdversarialTestFramework` tests
   - `GracefulDegradationManagerTest` - 8 test cases

---

### Phase 7: Lifecycle & Assurance (Weeks 13-16) - P3

#### Week 13-14: Secure SDLC

**Tasks:**

1. **Create `AgentThreatModeling` framework**
   - Threat modeling templates
   - Secure design checklists
   - Review workflows

2. **Implement `ChangeApprovalWorkflow`**
   - Risk-based approval
   - Formal review process
   - High-risk change gating

3. **Tests**
   - `ChangeApprovalWorkflowTest` - 6 test cases

#### Week 15-16: Recertification

**Tasks:**

1. **Implement `RecertificationPipeline`**
   - Automated recertification workflow
   - Policy conformance checking
   - Privacy review automation
   - Security reassessment

2. **Create recertification schedules**
   - Based on `AgentDatasheet.lastReviewedAt`
   - `AgentDatasheet.nextReviewAt` triggers

3. **Tests**
   - `RecertificationPipelineTest` - 8 test cases

---

### Phase 8: AEP Remediation & Hardening (Weeks 17-20) - P2

#### Week 17-18: AEP Identity & Compliance

**Tasks:**

1. **Create `products/aep/aep-identity` module**
   - `IdentityResolutionService` interface
   - `InMemoryIdentityResolver` (current behavior)
   - `ExternalIdentityResolver` (external graph integration)

2. **Create `products/aep/aep-compliance` module**
   - `ComplianceService`
   - `RetentionPolicyEnforcer`
   - `DeletionRequestWorkflow`
   - `ConsentChangePropagator`

3. **Integrate with AEP Engine**
   - Update `Aep.java` to use new services
   - Maintain backward compatibility

4. **Tests**
   - `IdentityResolutionServiceTest` - 8 test cases
   - `ComplianceServiceTest` - 10 test cases

#### Week 19-20: AEP Pattern State & Connectors

**Tasks:**

1. **Create `PatternStateStore` interface**
   - `InMemoryPatternStateStore` (default)
   - `EventCloudPatternStateStore` (durable)

2. **Refactor connector configurations**
   - Create `ConnectorConfig` base class
   - Refactor `KafkaConfig`, `RabbitMQConfig`, `SqsConfig`
   - Add `TlsConfig`, `RetryConfig` shared classes

3. **Implement `CircuitBreakerOperator`**
   - Reuse `platform/java/core/CircuitBreaker`
   - Follow `AbstractOperator` pattern

4. **Tests**
   - `PatternStateStoreTest` - 8 test cases
   - `EventCloudPatternStateStoreTest` - 6 test cases
   - `CircuitBreakerOperatorTest` - 10 test cases

---

### Phase 9: Consolidation & Optimization (Weeks 21-24) - P3

#### Week 21-22: Configuration & Error Handling

**Tasks:**

1. **Enhance `AepConfigValidator`**
   - Add `workerThreads` bounds checking
   - Add `instanceId` validation
   - Add `customConfig` validation

2. **Extend `EnvConfig`**
   - Add `getRequired()` method
   - Distinguish optional vs required keys

3. **Fix `AepAuthFilter` error messages**
   - Sanitize client-facing messages
   - Log detailed messages internally

4. **Tests**
   - Extend existing config tests

#### Week 23-24: Forecasting & Platform Modularization

**Tasks:**

1. **Create `ForecastingEngine` interface**
   - `NaiveForecastingEngine` (current)
   - `StatisticalForecastingEngine` (ARIMA/Prophet)
   - Pluggable configuration

2. **Platform module split** (SM-001)
   - `platform-event` (event cloud abstractions)
   - `platform-observability` (metrics, tracing)
   - `platform-resilience` (circuit breakers, retry)

3. **Tests**
   - `ForecastingEngineTest` - 6 test cases

---

### Phase 10: Long-term Advanced Features (Weeks 25-32) - P3

#### Week 25-28: Advanced Governance

**Tasks:**

1. **Model Catalog Enhancement**
   - Full model metadata
   - Provider tracking
   - Hosting mode documentation
   - Jurisdiction compliance
   - Data handling terms
   - Red-team status

2. **Bounded Planning**
   - Explicit prohibited actions
   - Approval thresholds
   - Budget/time limits
   - Tool-use limits

3. **Rich Tool Contracts**
   - Side effects metadata
   - Idempotency declarations
   - Rollback support flags
   - Human approval requirements

#### Week 29-32: Enterprise Features

**Tasks:**

1. **Administrative Governance Console**
   - Policy management UI
   - Agent registry UI
   - Audit log viewer
   - Incident dashboard

2. **Cross-Agent Orchestration**
   - Multi-agent workflow governance
   - Agent-to-agent communication policies
   - Shared memory governance

3. **Advanced Observability**
   - Security analytics dashboard
   - Anomaly detection visualization
   - Risk score tracking

---

## 6. Testing Requirements

### 6.1 Test Coverage Targets

| Component Type    | Target Coverage | Minimum Coverage |
| ----------------- | --------------- | ---------------- |
| New Services      | 90%             | 80%              |
| Extended Services | 85%             | 75%              |
| UI Components     | 80%             | 70%              |
| Integration Tests | 70%             | 60%              |

### 6.2 Test Infrastructure

All tests must use:

```java
// Base class for all async tests
public class MyServiceTest extends EventloopTestBase {

    @Override
    protected Duration eventloopTimeout() {
        return Duration.ofSeconds(30); // Longer for integration tests
    }

    @Test
    void testAsyncOperation() {
        runPromise(() -> {
            return service.asyncMethod()
                .then(result -> {
                    assertNotNull(result);
                    return Promise.of(result);
                });
        });
    }
}
```

### 6.3 Required Test Files Per Component

#### Service Tests (Example: IdentityService)

```java
// platform/java/identity/src/test/java/com/ghatana/identity/IdentityServiceTest.java

/**
 * Tests for IdentityService.
 *
 * @doc.type test
 * @doc.purpose Verify identity resolution and credential management
 * @doc.layer platform
 * @doc.pattern Unit Test
 */
public class IdentityServiceTest extends EventloopTestBase {

    private IdentityService identityService;
    private InMemoryIdentityResolver mockResolver;

    @BeforeEach
    void setUp() {
        mockResolver = new InMemoryIdentityResolver();
        identityService = new IdentityService(mockResolver);
    }

    @Test
    @DisplayName("Should resolve identity from valid credentials")
    void testResolveIdentity_Success() {
        runPromise(() -> {
            IdentityContext ctx = new IdentityContext("user-123", null, "session-456");
            return identityService.resolve(ctx)
                .then(identity -> {
                    assertNotNull(identity);
                    assertEquals("user-123", identity.userId());
                    return Promise.complete();
                });
        });
    }

    @Test
    @DisplayName("Should fail for revoked credentials")
    void testResolveIdentity_Revoked() {
        runPromise(() -> {
            // Setup revoked credential
            mockResolver.revoke("user-123");

            IdentityContext ctx = new IdentityContext("user-123", null, null);
            return identityService.resolve(ctx)
                .then(identity -> {
                    fail("Should have thrown");
                    return Promise.complete();
                })
                .catchError(e -> {
                    assertInstanceOf(RevokedCredentialException.class, e);
                    return Promise.complete();
                });
        });
    }

    @Test
    @DisplayName("Should handle cross-device identity linking")
    void testCrossDeviceLinking() {
        runPromise(() -> {
            IdentityContext webCtx = new IdentityContext("user-123", "anon-web", "session-web");
            IdentityContext mobileCtx = new IdentityContext(null, "anon-mobile", "session-mobile");

            // Link anonymous mobile to authenticated web
            return identityService.linkIdentities(webCtx, mobileCtx)
                .then(linkedIdentity -> {
                    assertEquals("user-123", linkedIdentity.userId());
                    assertTrue(linkedIdentity.linkedAnonymousIds().contains("anon-mobile"));
                    return Promise.complete();
                });
        });
    }
}
```

#### Integration Tests (Example: AEP Compliance)

```java
// products/aep/aep-compliance/src/test/java/com/ghatana/aep/compliance/
//     ComplianceServiceIntegrationTest.java

/**
 * Integration tests for ComplianceService with EventCloud.
 *
 * @doc.type test
 * @doc.purpose Verify end-to-end compliance workflows
 * @doc.layer integration
 * @doc.pattern Integration Test
 */
public class ComplianceServiceIntegrationTest extends PlatformIntegrationTestBase {

    @Container
    private static final PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:15");

    private ComplianceService complianceService;
    private EventCloud eventCloud;

    @BeforeEach
    void setUp() {
        eventCloud = new PostgresEventCloud(postgres.getJdbcUrl());
        complianceService = new ComplianceService(eventCloud);
    }

    @Test
    @DisplayName("Should enforce retention policy and delete expired data")
    void testRetentionEnforcement() {
        runPromise(() -> {
            // Store event with 1-day retention
            Event event = createEventWithRetention("tenant-1", Duration.ofDays(1));
            return eventCloud.store(event)
                .then(v -> {
                    // Fast-forward time (mock)
                    clock.advance(Duration.ofDays(2));

                    // Run retention enforcement
                    return complianceService.enforceRetention("tenant-1");
                })
                .then(deletedCount -> {
                    assertTrue(deletedCount > 0);

                    // Verify event no longer exists
                    return eventCloud.findById(event.id());
                })
                .then(found -> {
                    assertTrue(found.isEmpty());
                    return Promise.complete();
                });
        });
    }

    @Test
    @DisplayName("Should process deletion request and cascade to related data")
    void testDeletionRequestWorkflow() {
        runPromise(() -> {
            // Create user data across multiple stores
            String userId = "user-to-delete";

            return storeUserData(userId)
                .then(v -> complianceService.submitDeletionRequest(userId, "GDPR Article 17"))
                .then(requestId -> {
                    // Verify deletion workflow started
                    assertNotNull(requestId);

                    // Wait for completion
                    return waitForDeletionComplete(requestId, Duration.ofSeconds(30));
                })
                .then(status -> {
                    assertEquals(DeletionStatus.COMPLETED, status);

                    // Verify data deleted
                    return verifyUserDataDeleted(userId);
                })
                .then(v -> Promise.complete());
        });
    }
}
```

### 6.4 Testcontainers Requirements

| Service    | Container                      | Purpose                  |
| ---------- | ------------------------------ | ------------------------ |
| PostgreSQL | `postgres:15`                  | EventCloud storage       |
| Redis      | `redis:7-alpine`               | Distributed cache        |
| Kafka      | `confluentinc/cp-kafka:latest` | Connector tests          |
| RabbitMQ   | `rabbitmq:3-management`        | Connector tests          |
| OPA        | `openpolicyagent/opa:latest`   | Policy-as-code tests     |
| Vault      | `hashicorp/vault:latest`       | Secrets management tests |

### 6.5 UI Testing Requirements

```typescript
// products/aep/ui/src/__tests__/hooks/usePipelineRuns.test.ts

describe("usePipelineRuns", () => {
  it("should handle SSE reconnection", async () => {
    const { result } = renderHook(() => usePipelineRuns("tenant-1"));

    // Simulate connection drop
    mockEventSource.close();

    // Wait for reconnection
    await waitFor(() => {
      expect(result.current.connectionStatus).toBe("reconnecting");
    });

    // Simulate reconnection
    mockEventSource.connect();

    await waitFor(() => {
      expect(result.current.connectionStatus).toBe("connected");
    });
  });

  it("should handle error boundaries", async () => {
    const { result } = renderHook(() => usePipelineRuns("tenant-1"));

    // Simulate error
    mockEventSource.emitError(new Error("Connection failed"));

    await waitFor(() => {
      expect(result.current.error).toBeDefined();
      expect(result.current.isError).toBe(true);
    });
  });

  it("should handle tenant switching", async () => {
    const { result, rerender } = renderHook(
      ({ tenantId }) => usePipelineRuns(tenantId),
      { initialProps: { tenantId: "tenant-1" } },
    );

    expect(result.current.tenantId).toBe("tenant-1");

    // Switch tenant
    rerender({ tenantId: "tenant-2" });

    expect(result.current.tenantId).toBe("tenant-2");
    expect(mockEventSource.url).toContain("tenant-2");
  });
});
```

---

## 7. Long-Term Roadmap

### 7.1 6-Month Goals (Months 1-6)

| Goal               | Target              | Measurement                     |
| ------------------ | ------------------- | ------------------------------- |
| Critical Security  | 100% P0 complete    | All P0 requirements implemented |
| Privacy Foundation | 100% P1 complete    | GDPR/CCPA compliance automated  |
| AEP Hardiness      | 95% issues resolved | AEP audit findings closed       |
| Test Coverage      | 80% overall         | Platform + AEP combined         |

### 7.2 12-Month Goals (Months 7-12)

| Goal              | Target         | Measurement                        |
| ----------------- | -------------- | ---------------------------------- |
| Policy-as-Code    | 90% coverage   | OPA/Rego integration complete      |
| Tool Sandboxing   | 100% tools     | All tools in sandboxed environment |
| Incident Response | Full framework | Taxonomy, playbooks, automation    |
| Production Ready  | 99.9% uptime   | Monitoring, alerting, SLOs met     |

### 7.3 24-Month Vision (Months 13-24)

| Goal                      | Target                  | Measurement                           |
| ------------------------- | ----------------------- | ------------------------------------- |
| Enterprise Governance     | Full compliance         | NIST AI RMF, ISO/IEC 42001            |
| Cross-Agent Orchestration | Multi-agent governance  | Shared memory, communication policies |
| AI Safety Leadership      | Industry best practices | Published research, open-source tools |
| Global Scale              | Multi-region            | Tenant isolation across regions       |

---

## 8. Success Metrics

### 8.1 Technical Metrics

| Metric                     | Current | 3-Month | 6-Month | 12-Month |
| -------------------------- | ------- | ------- | ------- | -------- |
| Requirements Coverage      | 70%     | 85%     | 95%     | 98%      |
| Policy-as-Code Coverage    | 20%     | 60%     | 90%     | 95%      |
| Identity Verification      | 30%     | 80%     | 100%    | 100%     |
| Data Minimization          | 40%     | 70%     | 90%     | 95%      |
| Tool Sandboxing            | 0%      | 50%     | 100%    | 100%     |
| Audit Completeness         | 50%     | 75%     | 90%     | 95%      |
| Test Coverage              | 70%     | 75%     | 80%     | 85%      |
| Incident Response Time     | N/A     | <10 min | <5 min  | <3 min   |
| Recertification Automation | 0%      | 50%     | 100%    | 100%     |

### 8.2 Compliance Metrics

| Framework             | Current | Target | Timeline  |
| --------------------- | ------- | ------ | --------- |
| NIST AI RMF           | 60%     | 95%    | 12 months |
| ISO/IEC 42001         | 55%     | 90%    | 12 months |
| NIST Zero Trust       | 50%     | 90%    | 12 months |
| OWASP Agentic         | 40%     | 95%    | 12 months |
| GDPR Article 17       | Partial | Full   | 6 months  |
| CCPA Section 1798.105 | Partial | Full   | 6 months  |

### 8.3 Quality Metrics

| Metric                 | Target           | Measurement          |
| ---------------------- | ---------------- | -------------------- |
| Code Review Coverage   | 100%             | All PRs reviewed     |
| Test Pass Rate         | >99%             | CI/CD gate           |
| Security Scan Pass     | 0 critical       | SAST/DAST scans      |
| Documentation Coverage | 100% public APIs | Javadoc + @doc.\*    |
| Performance Regression | <5%              | Benchmark comparison |

---

## 9. Appendices

### Appendix A: Dependency Matrix

| New Module           | Depends On                      | Used By                      |
| -------------------- | ------------------------------- | ---------------------------- |
| `identity`           | `security`, `core`              | `agent-core`, `aep-identity` |
| `data-governance`    | `agent-core`, `governance`      | `aep-compliance`             |
| `tool-runtime`       | `core`, `kernel`                | `agent-core`                 |
| `security-analytics` | `core`, `observability`         | `aep-engine`                 |
| `incident-response`  | `core`, `audit`                 | All modules                  |
| `policy-as-code`     | `governance`, `core`            | All modules                  |
| `aep-identity`       | `identity`, `aep-engine`        | `aep-engine`                 |
| `aep-compliance`     | `data-governance`, `aep-engine` | `aep-engine`                 |

### Appendix B: Configuration Schema

```yaml
# New configuration sections for application.yml

ghatana:
  identity:
    provider: spiffe # or in-memory, jwt
    spiffe:
      socketPath: /spiffe-socket/agent.sock
      trustDomain: ghatana.io
    credential:
      ttl: 3600 # seconds
      provider: vault # or aws-secrets-manager
      vault:
        address: https://vault.ghatana.io
        role: agent-credentials

  data-governance:
    purpose-binding:
      enabled: true
      default-purpose: event_processing
    minimization:
      enabled: true
      strategies: [field-filtering, row-filtering, tokenization]
    consent:
      provider: internal # or external-crm
      retention-policy:
        enabled: true
        check-interval: 86400 # seconds

  tool-runtime:
    sandbox:
      provider: gvisor # or firecracker, none
      gvisor:
        runtime: runsc
        network: none
        filesystem: read-only
    execution-limits:
      max-memory: 512m
      max-cpu-time: 30s
      max-wall-time: 60s

  policy-as-code:
    engine: opa # or rego-direct
    opa:
      endpoint: http://opa.ghatana.io:8181
      bundle: ghatana-policies
    evaluation-timeout: 500ms

  incident-response:
    auto-containment: true
    evidence-preservation: true
    playbook-directory: /etc/ghatana/playbooks
```

### Appendix C: Migration Checklist

#### Phase 1 Migration (Weeks 1-4)

- [ ] `platform/java/identity` module created
- [ ] `IdentityService` implemented
- [ ] `CredentialBroker` implemented
- [ ] `DelegationTokenService` implemented
- [ ] All identity tests passing
- [ ] `AgentContext.deriveChild()` integrated
- [ ] Documentation updated

#### Phase 2 Migration (Weeks 3-6)

- [ ] `platform/java/data-governance` module created
- [ ] `DataAccessBroker` implemented
- [ ] `ConsentManager` implemented
- [ ] `PurposeLimitationEnforcer` implemented
- [ ] All governance tests passing
- [ ] `Aep.java` updated to use new services

#### Phase 3 Migration (Weeks 5-8)

- [ ] `platform/java/tool-runtime` module created
- [ ] `ToolSandbox` implemented
- [ ] `ApprovalGateway` implemented
- [ ] All tool tests passing
- [ ] Sandbox infrastructure configured

#### Phase 8 AEP Migration (Weeks 17-20)

- [ ] `aep-identity` module created
- [ ] `aep-compliance` module created
- [ ] `IdentityResolutionService` integrated
- [ ] `ComplianceService` integrated
- [ ] `PatternStateStore` implemented
- [ ] `ConnectorConfig` base class created
- [ ] All AEP tests passing
- [ ] Backward compatibility verified

---

**Document Version:** 1.0  
**Last Updated:** March 26, 2026  
**Next Review:** After Phase 1 completion
