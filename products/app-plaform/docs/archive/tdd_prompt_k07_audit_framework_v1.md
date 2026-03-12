# Specialized TDD Prompt: K-07 Audit Framework

Use this prompt together with [tdd_test_spec_generation_prompt_v1.md](tdd_test_spec_generation_prompt_v1.md).

Execution-plan position:
- Phase 1
- Build order position: 3

Primary sources:
- [../CURRENT_EXECUTION_PLAN.md](../CURRENT_EXECUTION_PLAN.md)
- [../LLD_K07_AUDIT_FRAMEWORK.md](../LLD_K07_AUDIT_FRAMEWORK.md)
- [../ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md](../ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md)

---

## Prompt Addendum

```text
Apply the base Siddhanta TDD prompt, then apply these K-07-specific overrides.

## Scope

Target scope:
- immutable audit append
- cryptographic chaining
- standardized audit schema
- search
- export
- verification
- maker-checker linkage

Default source paths:
- LLD_K07_AUDIT_FRAMEWORK.md
- CURRENT_EXECUTION_PLAN.md
- relevant epics and stories for audit, maker-checker, and evidence export

## Goal

Produce a test specification that can drive implementation of a tamper-evident audit framework with exact chain verification behavior, strict immutability, evidence export, and regulated search semantics.

## Mandatory Behavior Areas

You must explicitly decompose and test:
- audit event schema validation
- required actor/resource/details/outcome fields
- dual-calendar timestamps
- append-only persistence
- sequence numbering
- previous_hash and current_hash computation
- chain verification full range and partial range
- search filtering
- pagination and cursors
- export request validation
- export job lifecycle
- maker-checker linkage fields
- retention policy enforcement hooks
- tenant isolation
- trace_id propagation
- tamper detection behavior
- failure behavior when hash chain breaks
- integration boundary to K-05 publication if applicable

## Mandatory Scenario Families

You must enumerate, at minimum:
- valid audit append
- missing required actor field
- missing resource id
- invalid timestamp pair
- append sequence continuity
- append under concurrent load
- search with exact filters
- search with broad filters
- search pagination next page
- export accepted
- export invalid format
- export empty result set
- verify full chain valid
- verify subrange valid
- verify chain with tampered row
- verify chain with deleted row attempt
- duplicate audit submission handling
- tenant A cannot search tenant B
- maker cannot self-approve when linked workflow requires split roles
- retention boundary behavior
- evidence export includes requested payloads
- evidence export excludes unauthorized tenant data

## Expected Outputs To Force

For every test case, specify exact expected:
- stored audit row
- sequence number
- hash values or hash comparison expectation
- verification result structure
- search result count and cursor behavior
- export status transitions
- audit-related events and metrics
- explicit immutability outcome on update/delete attempts

## Coverage Additions

In addition to the base matrices, include:
- hash-chain coverage matrix
- search-filter matrix
- export-state matrix
- immutability defense matrix

## TDD Sequencing

Order the tests first as:
1. audit schema validator tests
2. hash calculation tests
3. append persistence tests
4. verification tests
5. search tests
6. export tests
7. concurrency and tamper tests
8. integration tests with eventing and maker-checker surfaces
```
