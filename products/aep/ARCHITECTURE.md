# Agentic Event Processor Architecture

**Status:** Target architecture with partially implemented foundations  
**Owner:** AEP maintainers  
**Boundary ADRs:** `docs/adr/ADR-aep-datacloud-eventcloud-boundaries.md`, `docs/adr/ADR-agent-capability-event-operator.md`, `docs/adr/ADR-adaptive-esp-foundation.md`

> **AEP is a formal, adaptive, agentic event processing platform. Its foundation is adaptive ESP: clean event/operator/time semantics, uncertainty-aware detection, predictive and recommended patterns, pattern exploration, extraction, learning, adaptation, and expert feedback. AEP modernizes this foundation with distributed stateful stream execution, probabilistic inference, online learning, neuro-symbolic reasoning, RAG-grounded agents, and governed tool use. In AEP, the agent is the root abstraction and event processing is exposed as an `EventOperatorCapability`; PatternSpec and PipelineSpec bind to capabilities alongside AND, OR, SEQ, NOT, WITHIN, TIMES, WINDOW, FILTER, TRANSFORM, and LEARNING operators. Data-Cloud remains the governed data/storage substrate; EventCloud and adaptive event semantics belong to AEP.**

## Product Vision and Scope

AEP owns adaptive event intelligence:

- EventCloud.
- Canonical event and stream semantics.
- PatternSpec and EPL.
- Operator catalog and runtime.
- Pattern detection.
- Uncertainty propagation.
- Predictive, recommended, and shadow pattern lifecycle.
- Pattern exploration, extraction, learning, and adaptation.
- Agent capability runtime.
- Pattern governance, review, promotion, rollback, and audit.

Data-Cloud owns governed data and storage substrate capabilities. AEP may use Data-Cloud storage plugins through stable SPI, but Data-Cloud must not expose EventCloud, CEP, PatternSpec, operator-runtime, or learning semantics.

## Foundational Model: Adaptive Event Intelligence

AEP is grounded in adaptive event stream processing. Its foundation is:

1. Canonical events and event streams.
2. Formal event operators and temporal semantics.
3. Pattern definitions through PatternSpec/EPL.
4. Uncertainty-aware detection.
5. Predictive and recommended pattern lifecycle.
6. Event pattern exploration, extraction, learning, and adaptation.
7. Expert, human, and agent feedback.
8. Governed promotion from recommended or shadow patterns to predictive or active patterns.

Modern advancements such as distributed stream processing, probabilistic inference, online learning, RAG, LLMs, and agents extend this model through typed capabilities.

## Architecture Layers

```text
Event ingress and normalization
CanonicalEvent envelope
EventCloud append, tail, replay, offsets, watermarks
PatternSpec/EPL compiler
Unified EventOperator runtime
EventOperatorCapability runtime
Uncertainty and time semantics
Pattern lifecycle and governance
Learning and adaptation services
Observability, audit, security, and tenant isolation
```

## Event Processing as Agent Capability

An agent is the root abstraction. Event processing is one capability an agent can expose through `EventOperatorCapability`, which implements the event-operator execution contract without making the agent itself inherit from an operator.

Event operator capability roles include:

- `AGENT_PREDICATE`
- `AGENT_ENRICH`
- `AGENT_EXTRACT`
- `AGENT_PATTERN_SYNTHESIS`
- `AGENT_EXPLANATION`
- `AGENT_REVIEW`
- `AGENT_ACTION`
- `AGENT_REFLECTION`

All event operator capabilities must declare input schema, output schema, model policy, tool policy, memory policy, replay policy, guardrails, observability, and uncertainty semantics.

AI and agent behavior is represented as typed capability bindings in the unified operator graph.

## Pattern Example

```yaml
apiVersion: aep.ghatana.io/v1
kind: PatternSpec
metadata:
  name: deploy-risk-sequence
  tenantId: tenant-a
semantics:
  timeMode: event_time
  uncertaintyPolicy:
    minPatternConfidence: 0.85
pattern:
  operator: SEQ
  within: PT30M
  operands:
    - event: deploy.started
    - operator: TIMES
      event: service.error_rate_elevated
      min: 3
      within: PT10M
    - operator: AGENT_PREDICATE
      capabilityRef: agents/sre-risk-assessor@1.0.0/capabilities/agent_predicate
      inputSchema: DeployRiskContext
      outputSchema: RiskDecision
      condition: "$output.riskScore >= 0.85"
emit:
  eventType: incident.risk_detected
  outputSchema: IncidentRiskDetected
lifecycle:
  state: SHADOW
governance:
  owner: sre-platform
  reviewPolicy: human_required_for_promotion
```

## Boundary Rules

```text
Data-Cloud must not depend on AEP.
Data-Cloud must not import PatternSpec, EPL, EventOperatorCapability runtime, or adaptive learning semantics.
AEP EventCloud SPI must not depend on Data-Cloud implementation classes.
AEP may use Data-Cloud storage plugins through stable SPI.
EventOperatorCapability must implement EventOperator and AgentCapability.
Side-effecting EventOperatorCapability usage must require tool policy, approval policy, idempotency, and audit.
```

## Current Reality

AEP has foundations in event processing, pipelines, operators, state, ingestion, runtime services, and learning concepts. Today, much of the implementation remains co-located under `products/data-cloud/planes/action/*` for migration compatibility. That temporary location is allowed while build/module boundaries stabilize, but it does not make Data-Cloud the owner of AEP semantics.

Readiness claims for AEP or the co-located Action Plane require executable proof. At minimum, the Action Plane modules must compile and test cleanly, AI governance behavioral proof must execute real tests with zero warnings, product release readiness must pass, and Data-Cloud/AEP boundary gates must prove non-action Data-Cloud planes do not import AEP internals.

The adaptive event intelligence architecture is not complete until the unified operator model, PatternSpec compiler, EventCloud SPI, pattern lifecycle, learning-to-recommendation loop, replay-safe agent execution, and CI/build gates are implemented and verified.
