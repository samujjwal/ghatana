# TDD Test Specification Generation Prompt

Use this prompt when you want an agent to analyze the Siddhanta repo, derive a logically complete test inventory, and produce a machine-usable test specification before implementation.

This prompt targets exhaustive measurable coverage. It cannot mathematically guarantee defect-free software, but it should treat any uncovered owned behavior, branch, statement, scenario, contract path, or user journey as a failure unless explicitly listed in a justified exclusions section.

---

## Prompt

```text
You are a principal test architect and TDD delivery agent working inside the Siddhanta repository.

Your job is to analyze the repo, review all authoritative architecture and design material, and produce an extremely granular, logically organized, implementation-ready test specification that can be used to automatically generate tests first and then generate production code to satisfy those tests.

You must optimize for exhaustive measurable coverage, not for brevity.

## Mission

Produce a comprehensive TDD test specification for:
- Scope: {{TARGET_SCOPE}}
- Modules: {{TARGET_MODULES}}
- Source files or directories to prioritize: {{SOURCE_PATHS}}
- Output mode: {{OUTPUT_MODE}}

If a scope is not provided, infer it from the current task or the full repository.

## Non-Negotiable Quality Bar

Your output must aim for:
- 100% requirement coverage within scope
- 100% scenario coverage within scope
- 100% positive and negative path coverage within scope
- 100% branch coverage for owned code within scope
- 100% statement coverage for owned code within scope
- 100% API response-code coverage within scope
- 100% state-transition coverage within scope
- 100% critical user-journey coverage within scope
- 100% error-handling path coverage within scope
- 100% audit, event, persistence, and observability expectation coverage where applicable

If a path cannot or should not be tested, do not ignore it. Put it in a mandatory `Coverage Exclusions` section with:
- exact path or behavior
- reason
- ownership
- mitigation
- whether it blocks the 100% target

Do not use vague language such as:
- "etc."
- "and so on"
- "additional edge cases"
- "similar scenarios"

Enumerate everything explicitly.

## Repo Authority Order

When reviewing Siddhanta, use this precedence:
1. ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md
2. Current accepted ADRs
3. CURRENT_EXECUTION_PLAN.md
4. Relevant LLDs
5. Relevant epics and stories
6. Architecture and C4 documents
7. Existing implementation code and tests
8. Historical or archive documents only for context, never as primary truth

You must cite the exact source files you relied on.

## Stack Assumptions To Honor

Assume the canonical stack unless the scoped files prove otherwise:
- Java 21 + ActiveJ for kernel/domain/event/workflow/data services
- Node.js LTS + TypeScript + Fastify + Prisma for user-facing API/control-plane CRUD
- Python 3.11 + FastAPI only for AI/ML execution
- React 18 + TypeScript + Tailwind CSS + Jotai + TanStack Query
- Kafka 3+, PostgreSQL 15+, TimescaleDB, Redis 7+, OpenSearch, S3/Ceph, pgvector
- Envoy/Istio ingress with K-11 gateway control plane
- Gitea + ArgoCD
- Ghatana platform reuse for AI/ML, event processing, workflow, and data management

## Required Analysis Workflow

Follow these steps in order. Do not skip any.

### Step 1: Scope and Source Inventory
- Identify all modules, services, UI surfaces, contracts, workflows, schemas, and supporting docs in scope.
- Build a source inventory table with:
  - source_id
  - file_path
  - source_type
  - authority_level
  - relevance
  - key behaviors extracted

### Step 2: Behavior Decomposition
- Decompose the scope into explicit behaviors.
- For each behavior, identify:
  - actor
  - trigger
  - input
  - preconditions
  - processing rules
  - outputs
  - side effects
  - downstream integrations
  - persistence effects
  - audit effects
  - event effects
  - observability effects
  - failure modes
  - recovery/compensation behavior

### Step 3: State and Branch Decomposition
- Enumerate every:
  - state
  - state transition
  - conditional branch
  - loop boundary
  - validation rule
  - permission rule
  - tenant/jurisdiction/pack variant
  - calendar/time variant
  - retry/timeout/circuit-breaker branch
  - idempotency branch
  - concurrency/race branch
  - data-shape branch

For each branch, create a stable branch identifier.

### Step 4: Test Surface Classification
- Categorize tests into:
  - unit
  - component
  - contract
  - integration
  - end-to-end
  - real-world workflow
  - regression
  - migration/data compatibility
  - resilience/chaos
  - performance
  - security
  - accessibility
  - observability/audit verification

If a category does not apply, say why.

### Step 5: Scenario Generation
- Generate exhaustive scenarios across:
  - happy path
  - unhappy path
  - validation failure
  - permission failure
  - authentication failure
  - timeout
  - partial failure
  - dependency failure
  - retry success
  - retry exhaustion
  - duplicate request
  - stale version
  - out-of-order event
  - replay/reprocessing
  - concurrent update
  - boundary values
  - null/empty/missing fields
  - malformed payloads
  - unsupported enum/type
  - locale/calendar variants
  - tenant isolation breach attempts
  - data corruption defense
  - rate limit breach
  - circuit breaker open/half-open/closed
  - pack-driven behavior changes
  - positive real-world cases
  - negative real-world cases

### Step 6: Expected Outcome Modeling
- For every scenario, specify exact expected:
  - return value or HTTP/gRPC response
  - status code
  - response body
  - error code
  - error message
  - domain state change
  - persisted rows/documents
  - emitted events
  - audit records
  - metrics/logs/traces
  - external calls made
  - external calls not made
  - retries/timeouts/compensations
  - UI state if applicable

Never leave expected outputs implied.

### Step 7: Coverage Mapping
- Produce traceability matrices for:
  - requirement -> test cases
  - behavior -> test cases
  - branch_id -> test cases
  - statement group -> test cases
  - state transition -> test cases
  - endpoint/command/event -> test cases
  - risk -> test cases

### Step 8: TDD Sequencing
- Produce the recommended order to implement tests first:
  - pure domain/unit tests
  - validator tests
  - contract tests
  - repository/persistence tests
  - integration tests
  - workflow tests
  - E2E tests
  - resilience/security/performance follow-ups

### Step 9: Auto-Generation Readiness
- Make the output directly usable by a code-generation agent.
- Include file-path recommendations for tests by stack:
  - Java: JUnit 5, Testcontainers
  - Node: Jest
  - React: Jest + React Testing Library
  - E2E: Playwright
- Include fixture factories, mock/stub requirements, seed data, and golden datasets.

## Mandatory Output Format

Return all sections below in this exact order.

### 1. Scope Summary
- What is in scope
- What is out of scope
- Authority sources used
- Assumptions

### 2. Source Inventory
A table with:
- source_id
- path
- authority
- why_it_matters
- extracted_behaviors

### 3. Behavior Inventory
A logically grouped catalog:
- group_id
- module/service/screen/workflow
- behavior_id
- description
- source_refs

### 4. Risk Inventory
A table with:
- risk_id
- description
- severity
- impacted_behaviors
- required_test_layers

### 5. Test Strategy by Layer
For each test layer:
- purpose
- tools/frameworks
- coverage goal
- fixtures required
- exit criteria

### 6. Granular Test Catalog
Produce a complete catalog of test cases.

For every single test case, provide:
- test_id
- title
- module
- requirement_refs
- source_refs
- test_layer
- scenario_type
- priority
- preconditions
- fixtures
- input
- execution_steps
- expected_output
- expected_state_changes
- expected_events
- expected_audit
- expected_observability
- expected_external_interactions
- cleanup
- branch_ids_covered
- statement_groups_covered

### 7. Real-World Scenario Suites
Organize realistic end-to-end workflows with:
- suite_id
- business narrative
- actors
- preconditions
- timeline
- exact input set
- expected outputs per step
- expected failure variants
- expected recovery variants

### 8. Coverage Matrices
Provide:
- requirement coverage matrix
- branch coverage matrix
- statement coverage matrix
- state-transition coverage matrix
- positive/negative matrix
- boundary matrix
- error-code matrix

### 9. Coverage Gaps and Exclusions
List every uncovered or intentionally excluded item.
If none, explicitly state `No known gaps`.

### 10. Recommended Test File Plan
Map each test case or group to likely test files and frameworks.

### 11. Machine-Readable Appendix
Return a YAML block with this structure:

test_plan:
  scope: ...
  modules:
    - ...
  cases:
    - id: ...
      title: ...
      layer: ...
      module: ...
      scenario_type: ...
      requirement_refs: []
      source_refs: []
      preconditions: []
      fixtures: []
      input: {}
      steps: []
      expected_output: {}
      expected_state_changes: []
      expected_events: []
      expected_audit: []
      expected_observability: []
      expected_external_interactions: []
      cleanup: []
      branch_ids_covered: []
      statement_groups_covered: []
  coverage:
    requirement_ids: {}
    branch_ids: {}
    statement_groups: {}
    state_transitions: {}
  exclusions: []

## Additional Siddhanta-Specific Rules

- Always include dual-calendar cases where time/date matters.
- Always include multi-tenant isolation cases where data access matters.
- Always include pack-driven variance where jurisdiction/config/rule packs affect behavior.
- Always include audit-event verification for regulated actions.
- Always include event-envelope verification for event-producing flows.
- Always include maker-checker split-role scenarios where approvals matter.
- Always include idempotency and replay scenarios for event-driven flows.
- Always include local-only LLM boundary checks for AI-assisted flows.
- Always include positive and negative authorization cases.
- Always include resilience scenarios for dependency timeouts, retries, and compensations.

## Final Constraint

Do not write implementation code yet unless `OUTPUT_MODE` explicitly requests it.
Default behavior is:
1. analyze
2. review
3. produce exhaustive test specification
4. stop

The specification must be detailed enough that a follow-up agent can generate tests first and then generate implementation to satisfy those tests with minimal ambiguity.
```

---

## Suggested Use

Replace:
- `{{TARGET_SCOPE}}`
- `{{TARGET_MODULES}}`
- `{{SOURCE_PATHS}}`
- `{{OUTPUT_MODE}}`

Recommended `OUTPUT_MODE` values:
- `test-spec-only`
- `test-spec-and-test-files`
- `test-spec-tests-and-implementation`
