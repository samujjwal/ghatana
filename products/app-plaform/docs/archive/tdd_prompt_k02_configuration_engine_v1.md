# Specialized TDD Prompt: K-02 Configuration Engine

Use this prompt together with [tdd_test_spec_generation_prompt_v1.md](tdd_test_spec_generation_prompt_v1.md).

Execution-plan position:
- Phase 1
- Build order position: 4

Primary sources:
- [../CURRENT_EXECUTION_PLAN.md](../CURRENT_EXECUTION_PLAN.md)
- [../LLD_K02_CONFIGURATION_ENGINE.md](../LLD_K02_CONFIGURATION_ENGINE.md)
- [../ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md](../ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md)

---

## Prompt Addendum

```text
Apply the base Siddhanta TDD prompt, then apply these K-02-specific overrides.

## Scope

Target scope:
- config schema registration
- config pack submission and approval
- hierarchical resolution
- effective-date activation
- hot reload
- immutable config history
- T1 pack loading

Default source paths:
- LLD_K02_CONFIGURATION_ENGINE.md
- CURRENT_EXECUTION_PLAN.md
- relevant stories for maker-checker, T1 packs, and hot reload

## Goal

Produce a test specification that can drive implementation of a deterministic configuration engine with schema validation, maker-checker approval, temporal resolution, and hot-reload safety.

## Mandatory Behavior Areas

You must explicitly decompose and test:
- schema registration
- schema compatibility checks
- pack payload validation
- hierarchy resolution order
- null/absent pack handling at each level
- effective date activation
- BS and Gregorian effective date behavior
- maker-checker workflow behavior
- approval and rejection paths
- immutable history
- rollback behavior
- watch/stream config change notifications
- cache TTL and invalidation
- T1 pack loader behavior
- tenant/jurisdiction/operator/user/account overrides
- deterministic resolution_path output
- K-05 hot reload event emission
- K-07 audit side effects
- K-15 effective date interpretation

## Mandatory Scenario Families

You must enumerate, at minimum:
- valid schema registration
- incompatible schema registration
- valid pack submission
- invalid payload against schema
- maker submits pack
- checker approves pack
- checker rejects pack
- maker attempts self-approval
- resolve only global config
- resolve jurisdiction override
- resolve tenant override
- resolve user override
- missing lower-level override falls back correctly
- conflicting overrides resolved deterministically
- as_of_date before activation
- as_of_date on activation instant
- as_of_date after activation
- rollback to prior active pack
- watch receives activation event
- watch receives deprecation event
- cache hit
- cache invalidated after activation
- T1 pack load success
- T1 pack signature or integrity failure if applicable
- tenant isolation in config resolution

## Expected Outputs To Force

For every test case, specify exact expected:
- resolved_config object
- resolution_path entries
- effective_as_of value
- pack/workflow statuses
- emitted hot-reload events
- audit records
- cache behavior
- error codes for invalid schema/payload/authorization/approval state

## Coverage Additions

In addition to the base matrices, include:
- hierarchy-level matrix
- temporal-resolution matrix
- maker-checker matrix
- schema-compatibility matrix
- T1 pack activation matrix

## TDD Sequencing

Order the tests first as:
1. schema validator and compatibility tests
2. hierarchy resolution pure-function tests
3. effective-date tests
4. pack submission/approval tests
5. immutable history and rollback tests
6. watch/hot-reload tests
7. T1 pack loader tests
8. cache and concurrency tests
```
