# Specialized TDD Prompt: Phase 4 and Phase 5 Operational Hardening

Use this prompt together with [tdd_test_spec_generation_prompt_v1.md](tdd_test_spec_generation_prompt_v1.md).

Execution-plan position:
- Phase 4: September 21, 2026 to November 13, 2026
- Phase 5: November 16, 2026 to December 24, 2026

Primary sources:
- [../CURRENT_EXECUTION_PLAN.md](../CURRENT_EXECUTION_PLAN.md)
- relevant LLDs for D-10, D-12, D-13, D-14, W-01, W-02, O-01, P-01, R-01, R-02, T-01, T-02
- [../ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md](../ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md)

---

## Prompt Addendum

```text
Apply the base Siddhanta TDD prompt, then apply these Phase 4 and Phase 5 overrides.

## Scope

Target scope:
- regulatory and operational scale-out
- certification and evidence paths
- integration, chaos, DR, security, and launch-readiness hardening

## Goal

Produce a test specification that drives Siddhanta from trading MVP to regulator-credible, evidence-backed launch readiness.

## Mandatory Organization

Your output must be organized in this order:
1. D-10 Regulatory Reporting
2. D-12 Corporate Actions
3. D-13 Client Money Reconciliation
4. D-14 Sanctions Screening
5. W-01 Workflow Orchestration
6. W-02 Client Onboarding
7. O-01 Operator Workflows
8. P-01 Pack Certification
9. R-01 Regulator Portal
10. R-02 Incident Notification
11. T-01 Integration Testing
12. T-02 Chaos Engineering
13. Performance qualification
14. Security validation
15. DR rehearsal
16. Launch readiness evidence suites

## Mandatory Behavior Areas

You must explicitly cover:
- regulator evidence generation and export
- corporate actions entitlements and exception handling
- client money segregation and reconciliation
- sanctions and adverse-media screening
- onboarding workflows and approvals
- operator runbooks and escalation paths
- pack certification validation and rejection reasons
- regulator-facing access controls and evidence views
- incident clustering, escalation, and notifications
- full integration regression
- chaos/failure injection across critical dependencies
- disaster recovery failover, restore, and data integrity
- security regression, authz regression, and audit completeness
- launch checklist evidence generation

## Mandatory Scenario Families

At minimum, enumerate:
- successful regulator report generation and submission
- invalid report payloads
- missed filing deadline detection
- corporate action straight-through and exception paths
- reconciliation exact match vs break vs critical break
- sanctions true positive, false positive, override, and escalation
- onboarding auto-approve, manual review, reject, and refresh
- pack certification pass/fail with exact reasons
- regulator portal allowed and forbidden access patterns
- incident notification on valid critical event
- missing evidence on incident path
- full end-to-end integration regression
- broker outage
- consumer lag
- stale calendar config
- saga timeout
- database failover
- object storage outage
- gateway partial outage
- DR restore with replay verification
- security attack simulation categories relevant to scope
- production-readiness checklist pass/fail conditions

## Phase Exit Criteria To Encode

Your tests must prove:
- regulator evidence export is demonstrable from staging data
- client money and sanctions controls are integrated, not standalone
- pack certification validates T1/T2/T3 artifacts before staging install
- major operational incidents have runbook-backed response paths
- end-to-end automated regression covers the full trading path
- chaos scenarios include broker outage, consumer lag, stale calendar config, and saga timeout
- DR rehearsal confirms documented recovery path
- launch checklist is evidence-backed, not presentation-backed

## Coverage Additions

Add matrices for:
- regulatory evidence coverage
- operational incident coverage
- runbook coverage
- chaos-fault coverage
- DR-step coverage
- launch-readiness checklist coverage

## TDD Sequencing

Order the tests first as:
1. deterministic business/control flows
2. regulator/export/reporting flows
3. workflow and operator flows
4. certification and portal flows
5. full integration suites
6. chaos and resilience suites
7. DR and security suites
8. launch-readiness evidence suites
```
