# Specialized TDD Prompt: Phase 0 Bootstrap

Use this prompt together with [tdd_test_spec_generation_prompt_v1.md](tdd_test_spec_generation_prompt_v1.md).

Execution-plan position:
- Phase 0
- Dates: March 9, 2026 to March 20, 2026
- Objective: convert the repository from document-only to implementation-ready workspace

Primary sources:
- [../CURRENT_EXECUTION_PLAN.md](../CURRENT_EXECUTION_PLAN.md)
- [../ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md](../ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md)

---

## Prompt Addendum

```text
Apply the base Siddhanta TDD prompt, then apply these Phase 0 bootstrap overrides.

## Scope

Target scope:
- repo bootstrap
- monorepo structure
- service templates
- shared contracts workspace
- local runtime stack
- CI validation pipeline
- engineering standards enforcement

Default source paths:
- CURRENT_EXECUTION_PLAN.md
- ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md
- README.md
- any new `services/`, `packages/`, `apps/`, `infra/`, `schemas/`, `packs/`, `tools/` scaffolds
- CI workflow files
- schema and contract packages

## Goal

Produce a test specification that proves the Phase 0 workspace is implementation-ready and blocks invalid scaffolding, invalid contracts, invalid packaging, invalid runtime startup, and schema-breaking changes.

## Mandatory Behavior Areas

You must explicitly decompose and test:
- repo directory creation and layout validity
- service template generation
- generated service naming conventions
- standard build/test tasks per stack
- shared event-envelope contract package generation
- shared dual-date contract package generation
- protobuf/OpenAPI workspace integrity
- local runtime boot sequence
- dependency health checks for PostgreSQL, Redis, Kafka, object storage, OpenTelemetry collector
- CI workflow validation and fail-fast behavior
- schema-breaking change detection
- artifact packaging
- branch/version strategy enforcement
- documented bootstrap command behavior

## Mandatory Scenario Families

You must enumerate, at minimum:
- valid repo bootstrap from empty scaffold
- repeated bootstrap/idempotent scaffold generation
- partial scaffold already exists
- invalid folder placement
- invalid service template name
- disallowed stack usage in templates
- missing required contract fields
- backward-compatible schema change
- backward-incompatible schema change
- protobuf compilation success/failure
- OpenAPI validation success/failure
- runtime starts cleanly
- runtime starts with one dependency down
- runtime starts with wrong credentials
- runtime starts with port conflict
- CI passes on valid change
- CI fails on lint/test/contract/schema/security-scan conditions
- branch naming valid/invalid
- container build success/failure
- bootstrap docs accurate/inaccurate

## Expected Outputs To Force

For every test case, specify exact expected:
- generated files and directories
- generated build files
- generated test files
- generated contract artifacts
- command exit codes
- console output fragments
- CI statuses
- produced container/image artifacts
- health endpoint outputs
- contract validation errors
- schema compatibility errors

## Coverage Additions

In addition to the base matrices, include:
- scaffold artifact coverage matrix
- command coverage matrix
- CI gate coverage matrix
- contract-toolchain coverage matrix

## Recommended Test File Planning

Plan tests across:
- scaffold generator tests
- contract package tests
- local runtime smoke/integration tests
- CI workflow validation tests
- developer-experience smoke tests

## TDD Sequencing

Order the tests first as:
1. directory and template shape tests
2. contract package shape tests
3. schema compatibility tests
4. local runtime smoke tests
5. CI gate tests
6. failure-mode bootstrap tests

Do not jump to Phase 1 modules in this prompt unless Phase 0 scaffolding guarantees are already defined.
```
