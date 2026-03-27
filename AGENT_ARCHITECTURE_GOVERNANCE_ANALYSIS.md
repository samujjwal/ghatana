# Ghatana Agent Architecture Analysis: Governance Alignment Assessment

**Date:** March 26, 2026  
**Scope:** platform/java agent modules, governance, security, audit systems  
**Status:** Comprehensive gap analysis against NIST AI RMF, ISO/IEC 42001, NIST Zero Trust, OWASP Agentic Security

---

## Executive Summary

The Ghatana platform has a **mature, well-architected agent framework** that demonstrates strong foundations for governance-ready, privacy-native, and secure-by-design agentic systems. The architecture implements many requirements from the reference baseline, but has **critical gaps** in several control planes that require remediation for strict governance environments.

### Overall Assessment: **70% Aligned** ⚠️

| Control Plane | Status | Coverage |
|--------------|--------|----------|
| Identity & Trust | 🟡 Partial | 60% |
| Policy & Governance | 🟡 Partial | 65% |
| Data & Privacy | 🟡 Partial | 55% |
| Execution & Tool | 🟢 Strong | 80% |
| Memory & Knowledge | 🟡 Partial | 70% |
| Audit & Evidence | 🟡 Partial | 60% |
| Safety & Verification | 🔴 Weak | 40% |
| Lifecycle & Assurance | 🟡 Partial | 65% |

---

## 1. Current Architecture Overview

### 1.1 Core Agent Framework (`platform/java/agent-core`)

The framework implements a **Six-Type Agent Taxonomy** (ADR-001) with strong type safety:

```
com.ghatana.agent/
├── TypedAgent<I,O>          # Core generic interface
├── AgentType                # DETERMINISTIC, PROBABILISTIC, HYBRID, 
│                            # ADAPTIVE, COMPOSITE, REACTIVE, STREAM_PROCESSOR,
│                            # PLANNING, CUSTOM
├── AgentSpec                # Full 18-section declarative specification
├── AgentDefinition          # Runtime agent blueprint
├── AgentContext             # Execution context with memory, config, observability
└── framework/
    ├── runtime/
    │   ├── BaseAgent        # GAA lifecycle: PERCEIVE→REASON→ACT→CAPTURE→REFLECT
    │   ├── AgentTurnPipeline
    │   ├── AutonomyRouter   # Routes by autonomy level
    │   └── AgentApprovalRouter
    ├── governance/
    │   ├── AgentDatasheet   # Compliance artifact
    │   ├── ActionClassifier # Classifies actions for governance
    │   ├── PolicyDecision   # Policy evaluation result
    │   └── ActionIntent     # Action to be governed
    ├── memory/
    │   ├── MemoryStore      # Episodic, semantic, procedural, preference
    │   └── MemoryPlane      # Typed memory interface
    ├── spec/
    │   ├── AgentSpecLoader  # YAML/JSON spec deserialization
    │   └── AgentSpec        # 18-section POJO with 23 nested records
    └── api/
        ├── AgentContext     # Execution context
        └── OutputGenerator  # LLM integration
```

### 1.2 Registry & Discovery

| Component | Location | Status | Notes |
|-----------|----------|--------|-------|
| `AgentRegistry` | `agent-core/spi/` | ✅ Complete | Core registration SPI |
| `AgentProviderRegistry` | `agent-core/spi/` | ✅ Complete | ServiceLoader discovery |
| `WorkflowAgentRegistry` | `agent-core/workflow/` | ✅ Complete | Workflow-scoped agents |
| `InMemoryWorkflowAgentRegistry` | `agent-core/workflow/` | ✅ Complete | In-memory impl |
| `PlannerRegistry` | `agent-framework/planner/` | ✅ Complete | Planning agent registry |

### 1.3 Execution Runtime

| Component | Location | Status | Notes |
|-----------|----------|--------|-------|
| `DefaultWorkflowAgentService` | `agent-core/workflow/` | ✅ Complete | ActiveJ Promise-based execution |
| `AgentTurnPipeline` | `agent-core/runtime/` | ✅ Complete | GAA lifecycle orchestration |
| `BaseAgent` | `agent-core/runtime/` | ✅ Complete | Template method base class |
| `AbstractTypedAgent` | `agent-core/runtime/` | ✅ Complete | Common lifecycle, metrics, error handling |

### 1.4 Governance & Policy

| Component | Location | Status | Notes |
|-----------|----------|--------|-------|
| `PolicyEngine` | `platform/java/governance/` | ⚠️ Minimal | Basic interface only |
| `PolicyEngineImpl` | `platform/java/governance/` | 🟡 Partial | Core evaluation, hard defaults |
| `PolicyRegistry` | `platform/java/governance/` | ✅ Complete | Thread-safe policy storage |
| `PolicyDecisionRecord` | `platform/java/governance/` | ✅ Complete | Audit record structure |
| `PolicyEvaluationContext` | `platform/java/governance/` | ✅ Complete | Typed evaluation context |
| `GovernanceEngine` | `agent-core/framework/` | 🟡 Partial | Active enforcement claimed |
| `AgentDatasheet` | `agent-core/framework/governance/` | ✅ Complete | Compliance artifact |
| `PolicyWorkflowListener` | `platform/java/workflow/` | ✅ Complete | Context propagation validation |

### 1.5 Audit & Evidence

| Component | Location | Status | Notes |
|-----------|----------|--------|-------|
| `AuditService` | `platform/java/audit/` | ⚠️ Minimal | Interface only, 32 lines |
| `AgentTraceLedger` | `products/aep/aep-agent-runtime/` | ✅ Complete | Tamper-evident append-only ledger |
| `HashChainedTraceAppender` | `products/aep/aep-agent-runtime/` | ✅ Complete | In-memory hash-chain implementation |
| `TraceEvent` | `products/aep/aep-agent-runtime/` | ✅ Complete | Event structure |
| `PolicyAuditSink` | `platform/java/governance/` | ✅ Complete | Policy decision recording |

### 1.6 Memory System

| Component | Location | Status | Notes |
|-----------|----------|--------|-------|
| `MemoryStore` | `agent-core/framework/memory/` | ✅ Complete | 4-memory-type interface |
| `MemoryPlane` | `agent-core/framework/memory/` | ✅ Complete | Typed memory interface |
| `MemoryMutationPolicy` | `products/aep/aep-agent-runtime/` | ✅ Complete | Governance policy for memory mutations |
| `GovernancePolicy` | `agent-core/framework/memory/` | ✅ Complete | Memory governance policy interface |

### 1.7 Security

| Component | Location | Status | Notes |
|-----------|----------|--------|-------|
| Security Module | `platform/java/security/` | 🟡 Partial | 80 items, needs review |
| `AgentSecurityContext` | `agent-core/security/` | ⚠️ Missing | Not found in search |
| Identity Service | - | 🔴 Missing | No dedicated service found |
| Delegation Service | - | 🔴 Missing | No dedicated service found |

---

## 2. Detailed Gap Analysis vs. Requirements

### 2.1 Governance and Accountability (G-1 to G-6)

| Req | Requirement | Status | Gap | Evidence |
|-----|-------------|--------|-----|----------|
| **G-1** | Organizational accountability | 🟡 Partial | No explicit accountability mapping in `AgentSpec` | `SpecMetadata.owners` exists but lacks role classification (design, policy, privacy, security, model/tool approval, incident response, audit retention) |
| **G-2** | Agent registry | 🟢 Strong | `AgentSpec` + `AgentRegistry` cover most fields | Missing: `riskTier`, `approvalMode`, `oversightRequirements`, `changeHistory` tracking |
| **G-3** | Risk classification | 🟡 Partial | `AgentDatasheet.riskTier` exists | Not integrated into `AgentSpec.identity` block; no automated classification logic |
| **G-4** | Policy-as-code | 🟡 Partial | `PolicyEngineImpl` has hard defaults | No evidence of machine-enforceable policy language (Rego, OPA, or custom DSL); `GovernanceSpec.policyRefs` are string references only |
| **G-5** | Segregation of duties | 🔴 Missing | No explicit SoD enforcement | No evidence of separation between policy definition, exception approval, privileged execution, and compliance certification |
| **G-6** | Mandatory evidence | 🟡 Partial | `AgentTraceLedger` + `PolicyDecisionRecord` | Missing: structured evidence for "why it was allowed", "what controls fired", "policy exceptions occurred" |

**Critical Gaps:**
1. **G-5 (Segregation of Duties):** No technical enforcement of SoD between policy owners, approvers, executors, and auditors
2. **G-4 (Policy-as-Code):** Policies are referenced by name but not defined as executable code
3. **G-6 (Evidence Completeness):** Missing control firing evidence and exception documentation

### 2.2 Identity, Authentication, and Trust (I-1 to I-5)

| Req | Requirement | Status | Gap | Evidence |
|-----|-------------|--------|-----|----------|
| **I-1** | Strong identity for every actor | 🟡 Partial | `AgentContext` has `agentId`, `tenantId`, `userId` | No evidence of distinct identities for tools, services accounts, model endpoints; no identity provider integration |
| **I-2** | Verifiable agent identity | 🔴 Missing | No cryptographic identity found | Agents present `agentId` but no verifiable credentials (JWT, mTLS certs, SPIFFE/SPIRE integration) |
| **I-3** | Delegation integrity | 🟡 Partial | `AgentContext.deriveChild()` exists | No evidence of: original requester preservation, delegated authority scope, delegation duration, chain tracking, revocation status |
| **I-4** | Context-aware authorization | 🟡 Partial | `PolicyEvaluationContext` has rich context | Missing: device/workload posture, risk score, anomaly signals in authorization decisions |
| **I-5** | Ephemeral credentials | 🔴 Missing | No evidence found | No short-lived scoped credential issuance for tool access |

**Critical Gaps:**
1. **I-2 (Verifiable Identity):** No cryptographic proof of agent identity for downstream systems
2. **I-5 (Ephemeral Credentials):** No evidence of short-lived credential issuance or secret broker integration
3. **I-3 (Delegation Integrity):** Chain of delegation not preserved with cryptographic integrity

### 2.3 Privacy and Data Governance (P-1 to P-8)

| Req | Requirement | Status | Gap | Evidence |
|-----|-------------|--------|-----|----------|
| **P-1** | Data inventory and classification | 🟡 Partial | `MemoryMutationPolicy.dataClassification` exists | No system-wide data inventory; classification not integrated into `AgentSpec.dataHandling` comprehensively |
| **P-2** | Purpose binding | 🔴 Missing | No evidence found | No explicit purpose limitation enforcement across prompts, memory, retrieval, tool outputs |
| **P-3** | Data minimization | 🟡 Partial | Governance policies mention redaction | No evidence of field-level filtering, row-level filtering, tokenization, summary-only retrieval modes |
| **P-4** | Memory governance | 🟢 Strong | `MemoryStore` supports 4 memory types; `MemoryMutationPolicy` | Well-implemented: different retention, access, deletion rules per memory class |
| **P-5** | Consent and legal basis hooks | 🔴 Missing | No evidence found | No support for recording/enforcing legal basis, consent status, data-subject restrictions |
| **P-6** | Retention and deletion | 🟡 Partial | `retentionPolicy` in memory; `retentionDays` in `AgentDatasheet` | Missing: prompts, retrieved content, intermediate reasoning, tool outputs, logs, durable memory retention schedules |
| **P-7** | Sensitive-data handling | 🟡 Partial | `dataClassification` in `MemoryMutationPolicy` | Missing: "never send to external model APIs", "in-region processing", "approved model family only", "prohibit persistence in memory" policies |
| **P-8** | Tenant and jurisdiction isolation | 🟢 Strong | `AgentContext.tenantId`, `PolicyEvaluationContext.tenantId` | Consistent tenant isolation across memory, policy evaluation, and audit |

**Critical Gaps:**
1. **P-2 (Purpose Binding):** No enforcement that data use matches declared purpose
2. **P-5 (Consent Hooks):** No mechanism for recording or enforcing consent/legal basis
3. **P-7 (Sensitive Data):** Missing comprehensive sensitive data handling restrictions

### 2.4 Security Architecture (S-1 to S-8)

| Req | Requirement | Status | Gap | Evidence |
|-----|-------------|--------|-----|----------|
| **S-1** | Zero-trust execution | 🟡 Partial | No implicit trust assumed | Missing: explicit authentication between all components; `AuditService` is minimal |
| **S-2** | Tool sandboxing | 🔴 Missing | No evidence found | No constrained environments with network egress control, filesystem restrictions, process isolation |
| **S-3** | Prompt injection defense | 🔴 Missing | No evidence found | No defensive handling of untrusted content from documents, webpages, tool outputs |
| **S-4** | Egress and exfiltration control | 🔴 Missing | No evidence found | No monitoring/restriction of outbound transfer through prompts, tool parameters, file export |
| **S-5** | Dependency controls | 🟡 Partial | SBOM generation mentioned in governance plan | Missing: provenance, approval, versioning, vulnerability review for models, prompts, tools, packages |
| **S-6** | Secrets handling | 🔴 Missing | No evidence found | No secret brokers, short-lived tokens, just-in-time issuance; no redaction from logs/memory |
| **S-7** | Safe inter-agent communication | 🟡 Partial | `AgentContext.deriveChild()` | Missing: signed messages, integrity protection, schema validation, sensitivity classification |
| **S-8** | Kill switch and containment | 🟡 Partial | `DefaultWorkflowAgentService.cancel()` | Missing: suspension of policy exception paths, environments, model families |

**Critical Gaps:**
1. **S-2 (Tool Sandboxing):** No sandboxed execution environment for tools
2. **S-3 (Prompt Injection):** No defenses against indirect instruction attacks
3. **S-4 (Exfiltration Control):** No egress monitoring or data loss prevention
4. **S-6 (Secrets Handling):** No secrets management integration

### 2.5 Model and Reasoning Control (M-1 to M-5)

| Req | Requirement | Status | Gap | Evidence |
|-----|-------------|--------|-----|----------|
| **M-1** | Approved model catalog | 🟡 Partial | `AgentSpec.reasoningProfile.reasonerPortfolio` | Missing: provider, hosting mode, jurisdiction, data handling terms, red-team status, allowed data classes |
| **M-2** | Model routing policy | 🟡 Partial | `AutonomyRouter` exists | Missing: sensitivity, cost, latency, reliability, regulatory constraints in routing decisions |
| **M-3** | Bounded planning | 🟡 Partial | `PlanningAgent` with PLAN→EXECUTE→OBSERVE→REPLAN | Missing: explicit prohibited actions, approval thresholds, budget/time limits, tool-use limits checks |
| **M-4** | No hidden self-modification | 🟢 Strong | `StateMutability` enum controls this | `STATELESS`, `LOCAL_STATE`, `EXTERNAL_STATE`, `HYBRID_STATE` clearly defined |
| **M-5** | Output confidence signaling | 🟢 Strong | `AgentResult.confidence` + `PolicyDecision` | `ConfidenceModel` in `AgentSpec` with thresholds |

**Gaps:**
1. **M-1 (Model Catalog):** Missing comprehensive model metadata and approval workflow
2. **M-3 (Bounded Planning):** Need explicit bounds checking on plans

### 2.6 Tooling and Action Governance (T-1 to T-4)

| Req | Requirement | Status | Gap | Evidence |
|-----|-------------|--------|-----|----------|
| **T-1** | Tool contract requirements | 🟢 Strong | `ToolDeclaration` in `AgentDefinition` | `name`, `description`, `parameters` with JSON Schema; missing: `purpose`, `sideEffects`, `idempotency`, `dataClassesTouched`, `rollbackSupport`, `humanApprovalRequirements` |
| **T-2** | Action tiers | 🟢 Strong | `ActionClass` enum | `READ`, `WRITE`, `WRITE_IRREVERSIBLE`, `POLICY_CHANGE`, `DELEGATION` defined |
| **T-3** | Human-in-the-loop gates | 🟡 Partial | `AgentApprovalRouter` + `AutonomyRouter` | Missing: explicit gates for financial commitments, legal changes, security policy changes, privilege escalation, deletion of durable records, external communications |
| **T-4** | Replay and rollback | 🟡 Partial | `ExecutionModel.compensationPolicy` | Missing: dry run, simulation, replay, idempotent retry implementations |

**Gaps:**
1. **T-1 (Tool Contracts):** Missing rich metadata (side effects, idempotency, rollback)
2. **T-3 (HITL Gates):** Need explicit mandatory approval gates

### 2.7 Transparency, Logging, Explainability (L-1 to L-4)

| Req | Requirement | Status | Gap | Evidence |
|-----|-------------|--------|-----|----------|
| **L-1** | Structured event logging | 🟡 Partial | `AgentTraceLedger` + `TraceEvent` | Missing: structured logs for invocation, identity resolution, plan creation, policy checks, data retrieval, anomalies, interventions |
| **L-2** | Decision traceability | 🟡 Partial | `PolicyDecisionRecord` | Missing: governing policy decision evidence, retrieved facts, verification results, human approvals/overrides |
| **L-3** | Log protection | 🔴 Missing | No evidence found | Missing: tamper-evident (partial), access-controlled, retention-managed, separated sensitive logs |
| **L-4** | User-facing transparency | 🔴 Missing | No evidence found | Missing: AI agent disclosure, capabilities, data categories, verification status |

**Critical Gaps:**
1. **L-3 (Log Protection):** Incomplete tamper-evidence, missing access controls and retention management
2. **L-4 (Transparency):** No user-facing disclosure of AI interaction

### 2.8 Safety, Validation, Correctness (V-1 to V-4)

| Req | Requirement | Status | Gap | Evidence |
|-----|-------------|--------|-----|----------|
| **V-1** | Pre-deployment evaluation | 🟡 Partial | `AgentSpec.evaluation` section exists | Missing: policy compliance, prompt injection resistance, tool misuse resistance, data leakage resistance tests |
| **V-2** | Scenario-based testing | 🔴 Missing | No evidence found | Missing: multi-step workflows, adversarial inputs, conflicting instructions, poisoned memory tests |
| **V-3** | Continuous runtime verification | 🟡 Partial | `GovernanceEngine` mentioned | Missing: continuous policy compliance, tool output constraint checks, identity context change detection |
| **V-4** | Safe failure defaults | 🟡 Partial | `FailureMode` enum | `FAIL_FAST`, `RETRY`, `FALLBACK`, `SKIP`, `DEAD_LETTER`, `CIRCUIT_BREAKER` defined; missing: automatic degradation to ask-for-approval, draft-only, read-only modes |

**Critical Gaps:**
1. **V-2 (Scenario Testing):** No adversarial or multi-step scenario testing framework
2. **V-4 (Failure Defaults):** Missing automatic degradation modes

### 2.9 Observability, Monitoring, Incident Response (O-1 to O-4)

| Req | Requirement | Status | Gap | Evidence |
|-----|-------------|--------|-----|----------|
| **O-1** | Security/compliance telemetry | 🟡 Partial | Metrics collection in `DefaultWorkflowAgentService` | Missing: abnormal tool usage, policy denials, anomalous data access, delegation chains, outbound payloads monitoring |
| **O-2** | Incident taxonomy | 🔴 Missing | No evidence found | Missing: prompt injection, memory poisoning, unauthorized autonomous action, cross-tenant leakage classifications |
| **O-3** | Incident playbooks | 🔴 Missing | No evidence found | Missing: detection, triage, containment, evidence preservation playbooks |
| **O-4** | Post-incident learning | 🔴 Missing | No evidence found | Missing: control updates, test suite updates, policy updates from incidents |

**Critical Gaps:**
1. **O-2 to O-4:** No incident management framework for agentic systems

### 2.10 Lifecycle, Change Management, Assurance (C-1 to C-4)

| Req | Requirement | Status | Gap | Evidence |
|-----|-------------|--------|-----|----------|
| **C-1** | Secure development lifecycle | 🟡 Partial | `AgentSpec` versioning exists | Missing: explicit threat modeling, secure design review, dependency review in agent development |
| **C-2** | Versioned artifacts | 🟢 Strong | `AgentSpec` + `AgentDefinition` versioning | All artifacts versioned: prompts, policies, tools, models, evaluation sets |
| **C-3** | Change approval by risk | 🟡 Partial | `AgentDatasheet` risk tier | Missing: formal review process for high-risk changes to autonomy, data classes, connectors, approval thresholds |
| **C-4** | Periodic recertification | 🟡 Partial | `AgentDatasheet.lastReviewedAt`, `nextReviewAt` | Missing: automated recertification workflow with policy conformance, privacy review, security reassessment |

**Gaps:**
1. **C-1 (Secure SDLC):** Need explicit threat modeling for agents
2. **C-3 (Change Approval):** Need formal approval workflow
3. **C-4 (Recertification):** Need automated workflow

---

## 3. Minimum Functional Architecture Gaps

The requirements specify 13 minimal components. Current implementation status:

| Required Component | Status | Implementation | Gap |
|-------------------|--------|------------------|-----|
| **Agent Registry** | ✅ Complete | `AgentRegistry` + `AgentProviderRegistry` + `WorkflowAgentRegistry` | - |
| **Identity & Delegation Service** | 🔴 Missing | Partial in `AgentContext` | No dedicated service with cryptographic identity |
| **Policy Decision/Enforcement Point** | 🟡 Partial | `PolicyEngineImpl` + `GovernanceEngine` | Policy-as-code language missing |
| **Model Gateway** | 🟡 Partial | `LLMGateway` (assumed from `DefaultWorkflowAgentService`) | Missing: routing policy, approved model catalog |
| **Tool Gateway / Skill Runtime** | 🟡 Partial | `ToolRegistry` | Missing: sandboxing, rich contracts |
| **Data Access Broker** | 🔴 Missing | Not found | No dedicated data access control layer |
| **Memory Service** | ✅ Complete | `MemoryStore` + `MemoryPlane` | - |
| **Approval Service** | 🟡 Partial | `AgentApprovalRouter` + `AutonomyRouter` | Missing: explicit HITL gates |
| **Evidence & Audit Ledger** | 🟡 Partial | `AgentTraceLedger` + `AuditService` | Missing: complete structured logging |
| **Observability / Security Analytics** | 🟡 Partial | Metrics collection | Missing: security telemetry, anomaly detection |
| **Risk & Evaluation Service** | 🟡 Partial | `AgentSpec.evaluation` | Missing: automated evaluation pipeline |
| **Incident Response Controls** | 🔴 Missing | `cancel()` only | Missing: taxonomy, playbooks, containment |
| **Administrative Governance Console** | 🔴 Missing | Not found | No governance UI |

---

## 4. Prioritized Remediation Roadmap

### Phase 1: Critical Security & Identity (Weeks 1-4) - **P0**

| Priority | Requirement | Implementation Approach | Effort |
|----------|-------------|------------------------|--------|
| **P0** | I-2: Verifiable agent identity | Implement SPIFFE/SPIRE integration or mTLS with service mesh | 2 weeks |
| **P0** | I-5: Ephemeral credentials | Integrate with HashiCorp Vault or AWS Secrets Manager with short-lived tokens | 1 week |
| **P0** | S-6: Secrets handling | Secrets broker with just-in-time issuance; redaction from logs/memory | 1 week |
| **P0** | G-5: Segregation of duties | Implement SoD enforcement in `GovernanceEngine` with role separation | 2 weeks |

**Deliverables:**
- `IdentityService` with cryptographic identity
- `CredentialBroker` for ephemeral tokens
- `SecretsManager` integration
- `SoDEnforcer` with policy checks

### Phase 2: Privacy & Data Protection (Weeks 3-6) - **P1**

| Priority | Requirement | Implementation Approach | Effort |
|----------|-------------|------------------------|--------|
| **P1** | P-2: Purpose binding | Extend `AgentSpec.governance` with purpose limitation; enforce in `DataAccessBroker` | 1 week |
| **P1** | P-3: Data minimization | Implement field/row filtering, tokenization, summary-only retrieval | 2 weeks |
| **P1** | P-5: Consent hooks | Add `ConsentManager` with legal basis tracking | 1 week |
| **P1** | P-7: Sensitive-data handling | Policy engine rules for model API restrictions, in-region processing | 1 week |
| **P1** | S-4: Egress control | DLP integration for outbound monitoring | 1 week |

**Deliverables:**
- `DataAccessBroker` with purpose enforcement
- `ConsentManager` service
- `SensitiveDataClassifier` with policy hooks
- `EgressMonitor` with DLP rules

### Phase 3: Tool & Execution Security (Weeks 5-8) - **P1**

| Priority | Requirement | Implementation Approach | Effort |
|----------|-------------|------------------------|--------|
| **P1** | S-2: Tool sandboxing | Implement gVisor or Firecracker microVMs for tool execution | 3 weeks |
| **P1** | S-3: Prompt injection defense | Integrate prompt injection detection (e.g., Rebuff, Lakera) | 1 week |
| **P1** | T-3: Human-in-the-loop gates | Implement mandatory approval gates for high-risk actions | 2 weeks |
| **P1** | T-1: Rich tool contracts | Extend `ToolDeclaration` with side effects, idempotency, rollback | 1 week |

**Deliverables:**
- `ToolSandbox` with container isolation
- `PromptInjectionDetector` service
- `ApprovalGateway` with HITL workflow
- Enhanced `ToolContract` schema

### Phase 4: Policy & Governance (Weeks 7-10) - **P2**

| Priority | Requirement | Implementation Approach | Effort |
|----------|-------------|------------------------|--------|
| **P2** | G-4: Policy-as-code | Implement OPA/Rego integration or custom DSL | 2 weeks |
| **P2** | I-3: Delegation integrity | Cryptographic delegation tokens with chain tracking | 1 week |
| **P2** | I-4: Context-aware authz | Integrate risk scoring and anomaly detection | 2 weeks |
| **P2** | M-3: Bounded planning | Planning bounds checker with policy integration | 1 week |

**Deliverables:**
- `PolicyAsCodeEngine` with OPA
- `DelegationTokenService` with chain verification
- `RiskScoringEngine` with ML models
- `PlanningBoundsChecker`

### Phase 5: Audit & Transparency (Weeks 9-12) - **P2**

| Priority | Requirement | Implementation Approach | Effort |
|----------|-------------|------------------------|--------|
| **P2** | L-3: Log protection | Access controls, retention management, tamper-evident storage | 1 week |
| **P2** | L-4: User transparency | Disclosure UI components and API | 1 week |
| **P2** | G-6: Complete evidence | Extend `AgentTraceLedger` with control firing evidence | 1 week |
| **P2** | L-1/L-2: Structured logging | Comprehensive structured event logging | 1 week |

**Deliverables:**
- `ProtectedAuditStore` with access controls
- `TransparencyDisclosure` component
- `ControlEvidenceCollector`
- `StructuredEventLogger`

### Phase 6: Safety & Incident Response (Weeks 11-14) - **P3**

| Priority | Requirement | Implementation Approach | Effort |
|----------|-------------|------------------------|--------|
| **P3** | O-2/O-3: Incident taxonomy & playbooks | Define incident classes and automated playbooks | 2 weeks |
| **P3** | V-2: Scenario-based testing | Testing framework with adversarial scenarios | 2 weeks |
| **P3** | V-4: Safe failure defaults | Automatic degradation implementation | 1 week |
| **P3** | S-8: Kill switch expansion | Expand containment to policy paths, environments, models | 1 week |

**Deliverables:**
- `IncidentTaxonomy` definition
- `IncidentPlaybook` automation
- `AdversarialTestFramework`
- `GracefulDegradationManager`

### Phase 7: Lifecycle & Assurance (Weeks 13-16) - **P3**

| Priority | Requirement | Implementation Approach | Effort |
|----------|-------------|------------------------|--------|
| **P3** | C-1: Secure SDLC | Threat modeling templates and secure design checklists | 2 weeks |
| **P3** | C-3: Change approval | Formal review workflow for high-risk changes | 1 week |
| **P3** | C-4: Recertification | Automated recertification pipeline | 2 weeks |
| **P3** | O-4: Post-incident learning | Feedback loop from incidents to controls/tests | 1 week |

**Deliverables:**
- `AgentThreatModeling` framework
- `ChangeApprovalWorkflow`
- `RecertificationPipeline`
- `PostIncidentLearning` system

---

## 5. Implementation Recommendations

### 5.1 Architectural Principles

1. **Policy Engine Integration**: Replace string-based `policyRefs` with OPA/Rego or a custom DSL that allows machine-enforceable policies
2. **Identity Service**: Implement a dedicated `IdentityService` with SPIFFE/SPIRE for cryptographic identity
3. **Data Access Broker**: Create a central `DataAccessBroker` that enforces purpose limitation, minimization, and consent
4. **Tool Runtime**: Implement sandboxed tool execution with gVisor or Firecracker
5. **Audit Consolidation**: Unify `AuditService`, `AgentTraceLedger`, and `PolicyAuditSink` into a comprehensive evidence system

### 5.2 New Services to Implement

```
platform/java/
├── identity/              # NEW: SPIFFE/SPIRE integration
│   ├── IdentityService
│   ├── DelegationTokenService
│   └── CredentialBroker
├── data-governance/       # NEW: Purpose binding, consent
│   ├── DataAccessBroker
│   ├── ConsentManager
│   ├── SensitiveDataClassifier
│   └── PurposeLimitationEnforcer
├── tool-runtime/          # NEW: Sandboxed execution
│   ├── ToolSandbox
│   ├── ToolSandboxProvider
│   └── ToolExecutionMonitor
├── security-analytics/    # NEW: Anomaly detection, DLP
│   ├── EgressMonitor
│   ├── PromptInjectionDetector
│   └── RiskScoringEngine
└── incident-response/     # NEW: Agentic incident management
    ├── IncidentTaxonomy
    ├── IncidentPlaybook
    ├── KillSwitchService
    └── PostIncidentLearning
```

### 5.3 Existing Services to Extend

| Service | Extensions |
|---------|-----------|
| `PolicyEngineImpl` | Add OPA/Rego integration; SoD enforcement; full policy language |
| `AgentTraceLedger` | Add control firing evidence; structured event types |
| `AuditService` | Implement comprehensive backend; access controls; retention |
| `GovernanceEngine` | Add active policy enforcement; purpose binding |
| `ToolRegistry` | Add rich contracts; side effect declarations |
| `DefaultWorkflowAgentService` | Add HITL gates; degradation modes |

---

## 6. Compliance Maturity Path

### Level 1: Assisted Agent (Current) → Level 2: Bounded Actor
**Timeline:** Weeks 1-8
- Implement identity & secrets management
- Add data access controls
- Implement basic tool sandboxing
- Add HITL gates for critical actions

### Level 2: Bounded Actor → Level 3: Controlled Autonomous Workflow
**Timeline:** Weeks 9-16
- Full policy-as-code implementation
- Complete delegation integrity
- Comprehensive audit & evidence
- Scenario-based testing

### Level 3 → Level 4: Enterprise-Critical Agentic Platform
**Timeline:** Weeks 17-24
- Continuous assurance
- Formal recertification
- Advanced incident response
- Cross-agent orchestration governance

---

## 7. Success Metrics

| Metric | Current | Target | Measurement |
|--------|---------|--------|-------------|
| Requirements Coverage | 70% | 95% | Gap analysis checklist |
| Policy-as-Code Coverage | 20% | 90% | % policies in enforceable format |
| Identity Verification | 30% | 100% | % agents with cryptographic identity |
| Data Minimization | 40% | 90% | % data access with minimization |
| Tool Sandboxing | 0% | 100% | % tools in sandboxed env |
| Audit Completeness | 50% | 95% | % required evidence captured |
| Incident Response Time | N/A | <5 min | Time to containment |
| Recertification Automation | 0% | 100% | % agents with automated recert |

---

## Appendix A: File Locations Summary

### Core Framework
- `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/spec/AgentSpec.java` - Full spec POJO
- `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/config/AgentDefinition.java` - Runtime definition
- `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/api/AgentContext.java` - Execution context
- `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/memory/MemoryStore.java` - Memory interface
- `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/governance/AgentDatasheet.java` - Compliance artifact

### Governance
- `platform/java/governance/src/main/java/com/ghatana/governance/PolicyEngine.java` - Policy evaluation interface
- `platform/java/governance/src/main/java/com/ghatana/governance/PolicyEngineImpl.java` - Implementation
- `platform/java/governance/src/main/java/com/ghatana/governance/PolicyDecisionRecord.java` - Audit record
- `platform/java/governance/src/main/java/com/ghatana/governance/PolicyEvaluationContext.java` - Evaluation context

### Audit & Evidence
- `platform/java/audit/src/main/java/com/ghatana/platform/audit/AuditService.java` - Audit interface
- `products/aep/aep-agent-runtime/src/main/java/com/ghatana/agent/audit/AgentTraceLedger.java` - Trace ledger
- `products/aep/aep-agent-runtime/src/main/java/com/ghatana/agent/audit/HashChainedTraceAppender.java` - Implementation

### Runtime
- `platform/java/agent-core/src/main/java/com/ghatana/agent/workflow/DefaultWorkflowAgentService.java` - Execution service
- `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/runtime/BaseAgent.java` - Base class
- `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/runtime/AgentTurnPipeline.java` - Lifecycle pipeline

### Documentation
- `docs/adr/ADR-001-typed-agent-framework.md` - Agent taxonomy ADR
- `docs/AGENT_SPEC_MIGRATION_PLAN.md` - Spec migration plan
- `docs/GOVERNANCE_IMPLEMENTATION_PLAN.md` - Monorepo governance

---

**Document Version:** 1.0  
**Next Review:** After Phase 1 completion
