# Specialized TDD Prompt: Phase 3 Trading MVP

Use this prompt together with [tdd_test_spec_generation_prompt_v1.md](tdd_test_spec_generation_prompt_v1.md).

Execution-plan position:
- Phase 3
- Dates: July 13, 2026 to September 18, 2026

Primary sources:
- [../CURRENT_EXECUTION_PLAN.md](../CURRENT_EXECUTION_PLAN.md)
- relevant LLDs for D-11, D-04, D-01, D-06, D-07, D-02, D-09, D-03, D-05
- [../ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md](../ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md)

Implementation order to honor:
1. D-11 Reference Data
2. D-04 Market Data
3. D-01 OMS
4. D-06 Risk Engine and D-07 Compliance
5. D-02 EMS
6. D-09 Post-Trade
7. D-03 PMS
8. D-05 Pricing Engine

Primary staging workflow to encode:
`Reference Data -> Market Data -> OMS -> Risk -> Compliance -> EMS -> Post-Trade -> Ledger -> PMS`

---

## Prompt Addendum

```text
Apply the base Siddhanta TDD prompt, then apply these Phase 3 overrides.

## Scope

Target scope:
- the full Trading MVP path
- module-by-module tests in execution order
- end-to-end staged trading flow

## Goal

Produce a test specification that can drive and verify the first staging-grade trading control path with no manual database intervention and with exact event, ledger, and post-trade consequences.

## Mandatory Organization

Your output must be organized in this order:
1. D-11 Reference Data
2. D-04 Market Data
3. D-01 OMS
4. D-06 Risk Engine
5. D-07 Compliance
6. D-02 EMS
7. D-09 Post-Trade
8. K-16 Ledger touchpoints
9. D-03 PMS
10. D-05 Pricing Engine
11. Cross-domain E2E workflow suites

## Mandatory Behavior Areas

You must explicitly cover:
- instrument and reference data validity
- market data ingestion, normalization, freshness, and stale-feed handling
- order placement, validation, modification, cancel, and fill flows
- pre-trade risk checks and blocking conditions
- compliance screening and deterministic hard blocks
- EMS routing, execution recording, and exchange adapter behavior
- settlement obligation creation
- ledger posting side effects
- holdings and P&L updates
- pricing and valuation consistency
- replay from K-05 to rebuild at least one dependent projection

## Mandatory Scenario Families

At minimum, enumerate:
- fully valid trade lifecycle
- invalid instrument
- stale market data
- market halt / circuit breaker condition
- insufficient margin
- holdings unavailable
- compliance hard block
- sanctions/compliance escalation
- exchange reject
- partial fill
- full fill
- cancel before route
- cancel after partial fill
- duplicate execution report
- out-of-order execution report
- post-trade partial failure
- ledger posting failure and compensation
- PMS projection lag and replay recovery
- pricing update after fill
- end-of-day valuation
- tenant isolation in shared market/reference surfaces
- positive and negative operator workflows tied to the trade lifecycle

## Phase Exit Criteria To Encode

Your tests must prove:
- staging order lifecycle executes without manual database intervention
- OMS critical path respects the `<=12ms e2e P99` design target in controlled benchmark runs
- replay from K-05 can rebuild at least one projection used by OMS or PMS
- failure cases route to DLQ or compensating saga paths instead of ad hoc recovery

## Coverage Additions

Add matrices for:
- trading-state transitions
- pre-trade decision outcomes
- execution outcome coverage
- settlement/ledger consequence coverage
- projection rebuild coverage

## TDD Sequencing

Drive tests in the exact implementation order above.
Do not generate downstream module tests that assume upstream data contracts before those upstream contracts are specified.
Conclude with real-world E2E trading suites and replay/recovery suites.
```
