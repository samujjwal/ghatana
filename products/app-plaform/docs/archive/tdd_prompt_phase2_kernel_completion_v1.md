# Specialized TDD Prompt: Phase 2 Kernel Completion

Use this prompt together with [tdd_test_spec_generation_prompt_v1.md](tdd_test_spec_generation_prompt_v1.md).

Execution-plan position:
- Phase 2
- Dates: May 18, 2026 to July 10, 2026

Primary sources:
- [../CURRENT_EXECUTION_PLAN.md](../CURRENT_EXECUTION_PLAN.md)
- relevant LLDs for K-01, K-03, K-04, K-06, K-08, K-09, K-10, K-11, K-12, K-13, K-14, K-16, K-17, K-18, K-19
- [../ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md](../ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md)

Execution sequence to honor:
1. Security/control plane: K-01, K-14
2. Policy/extensibility: K-03, K-04
3. Operational core: K-06, K-18, K-19
4. Financial/core state: K-16, K-17
5. Governance/control-plane surfaces: K-08, K-09, K-10, K-11, K-13, PU-004
6. K-12 Platform SDK continues across the phase

---

## Prompt Addendum

```text
Apply the base Siddhanta TDD prompt, then apply these Phase 2 overrides.

## Scope

Target scope:
- all Phase 2 kernel completion modules
- cross-module contracts and readiness gates
- staging-grade integration guarantees required before domain implementation

## Goal

Produce a test specification that proves Phase 2 clears all kernel readiness gates and that domain teams can consume the kernel through stable, secure, observable, auditable interfaces.

## Mandatory Organization

Your output must be organized in this order:
1. K-01 IAM
2. K-14 Secrets Management
3. K-03 Rules Engine
4. K-04 Plugin Runtime
5. K-06 Observability
6. K-18 Resilience Patterns
7. K-19 DLQ Management
8. K-16 Ledger Framework
9. K-17 Distributed Transaction Coordinator
10. K-08 Data Governance
11. K-09 AI Governance
12. K-10 Deployment Abstraction
13. K-11 API Gateway
14. K-13 Admin Portal
15. PU-004 Platform Manifest
16. K-12 Platform SDK
17. Cross-module readiness suites

## Mandatory Cross-Module Themes

You must explicitly cover:
- authentication, authorization, and split-role maker-checker paths
- policy evaluation against T1/T2/T3-driven behavior
- plugin loading, isolation, signature verification, and rollback
- metrics, traces, logs, and alerting guarantees
- timeouts, retries, circuit breakers, bulkheads, and fallback behavior
- DLQ routing, replay, and poison-message handling
- ledger posting correctness and immutability
- saga orchestration and compensation
- tenant isolation and data-governance policy enforcement
- AI model/prompt governance and local-only LLM boundaries
- deployment promotion, rollback, and environment abstraction
- gateway ingress validation, rate limiting, and telemetry injection
- admin portal RBAC, dual-calendar UI behavior, and maker-checker flows
- SDK contract stability and compatibility across Java/TypeScript consumers

## Mandatory Scenario Families

At minimum, generate suites for:
- all happy paths per module
- all validation and authorization failures per module
- all dependency-down scenarios for each critical integration
- replay/retry/rollback paths
- config/policy/plugin changes applied live
- split-role approval enforcement
- tenant isolation breach attempts
- audit and observability verification for every regulated write path
- degraded-mode behavior for partial platform outages
- compatibility tests between modules in execution-sequence order

## Phase Exit Criteria To Encode

Your tests must prove:
- domain teams can consume all kernel services through stable interfaces
- T1 and T2 packs load safely in staging
- secrets, auth, audit, and observability are wired into one staging environment
- DLQ/replay and saga compensation are tested before domain rollout

## Coverage Additions

Add matrices for:
- module dependency coverage
- readiness gate coverage
- cross-module contract coverage
- platform-control coverage

## TDD Sequencing

Drive tests in the exact execution sequence above.
Within each module, apply the base TDD flow.
Do not schedule downstream module tests that depend on an upstream readiness gate before that upstream module's contracts and failure modes are specified.
```
