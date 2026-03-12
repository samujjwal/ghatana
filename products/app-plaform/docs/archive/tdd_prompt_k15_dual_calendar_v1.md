# Specialized TDD Prompt: K-15 Dual-Calendar

Use this prompt together with [tdd_test_spec_generation_prompt_v1.md](tdd_test_spec_generation_prompt_v1.md).

Execution-plan position:
- Phase 1
- Build order position: 2

Primary sources:
- [../CURRENT_EXECUTION_PLAN.md](../CURRENT_EXECUTION_PLAN.md)
- [../LLD_K15_DUAL_CALENDAR.md](../LLD_K15_DUAL_CALENDAR.md)
- [../ADR-004_DUAL_CALENDAR_SYSTEM.md](../ADR-004_DUAL_CALENDAR_SYSTEM.md)
- [../ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md](../ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md)

---

## Prompt Addendum

```text
Apply the base Siddhanta TDD prompt, then apply these K-15-specific overrides.

## Scope

Target scope:
- BS <-> Gregorian conversion
- DualDate generation
- holiday calendars
- business-day computation
- settlement-date helper
- fiscal-year resolution
- bulk conversion

Default source paths:
- LLD_K15_DUAL_CALENDAR.md
- ADR-004_DUAL_CALENDAR_SYSTEM.md
- CURRENT_EXECUTION_PLAN.md

## Goal

Produce a test specification that can drive exact lookup-table-based dual-calendar implementation with deterministic business-day and settlement calculations across jurisdiction-aware holiday sets.

## Mandatory Behavior Areas

You must explicitly decompose and test:
- AD to BS conversion
- BS to AD conversion
- lookup-table bounds
- invalid/out-of-range date handling
- leap-year and boundary-day handling
- DualDate generation from timestamp
- BS full timestamp formatting
- fiscal year label calculation
- quarter boundaries
- holiday lookup by jurisdiction
- business day vs non-business day evaluation
- settlement date T+n logic
- skipped non-business days
- batch conversion consistency
- performance-sensitive hot-path conversion behavior
- calendar extension handling for non-default calendar types
- K-02-driven holiday/fiscal configuration updates

## Mandatory Scenario Families

You must enumerate, at minimum:
- valid single conversion AD->BS
- valid single conversion BS->AD
- round-trip conversion accuracy
- first supported date
- last supported date
- one day below lower bound
- one day above upper bound
- invalid date format
- invalid calendar type
- leap-day handling
- month boundary handling
- year boundary handling
- fiscal year boundary day
- holiday business-day false
- normal weekday business-day true
- weekend variance by jurisdiction
- settlement date with no skipped days
- settlement date across weekend
- settlement date across holiday chain
- batch conversion mixed valid and invalid inputs
- config change updates holiday outcomes
- concurrent requests produce stable answers
- repeated conversion returns identical results

## Expected Outputs To Force

For every test case, specify exact expected:
- input date
- output date
- day of week
- fiscal year label
- holiday flag
- business day flag
- skipped dates list
- settlement target date
- error code/message for invalid values
- cache/memory lookup expectations where relevant

## Coverage Additions

In addition to the base matrices, include:
- conversion-table boundary matrix
- fiscal-year matrix
- holiday/business-day matrix
- settlement T+n matrix
- round-trip consistency matrix

## TDD Sequencing

Order the tests first as:
1. table lookup unit tests
2. format/validation tests
3. round-trip conversion tests
4. fiscal-year and quarter tests
5. holiday/business-day tests
6. settlement-date tests
7. batch API tests
8. performance tests
```
