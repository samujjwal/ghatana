Perform a strict, evidence-based deep audit of the **current codebase** for the **target product** and its **directly or transitively dependent libraries/modules only**.

Do **not** audit the entire repository by default.
Do **not** use previous audit reports as a primary input.
Do **not** spend time on unrelated products, unrelated packages, dormant modules, or repo-wide content unless they are actually required by the target product’s runtime, build, test, deploy, contracts, or critical workflows.

Goal: determine whether the current product and the libraries it depends on are:
- feature-complete for intended scope and use cases
- correct end to end
- production-hardened
- secure and privacy-conscious
- observable and operationally transparent
- performant and scalable
- simple in UX and architecture
- AI/ML-native, with AI/ML as a first-class, embedded, pervasive capability
- designed to minimize customer effort and require manual intervention only when truly necessary

AI/ML must not be a visible gimmick or a separate “feature.” It must be embedded into workflows to solve real-world problems, reduce customer work, and improve outcomes. Customers should only be involved when confidence, governance, policy, approval, ambiguity, or exception handling requires it.

Your job is to find the truth in the **current implementation** and its **actual dependency surface**.

---

# Scope rules

## 1. Audit target scope only
Audit:
- the target product/application
- the libraries/modules/packages/services it directly depends on
- transitive dependencies only when they materially affect the product’s behavior, correctness, performance, security, UX, automation, or operations

Do not audit:
- unrelated apps/products in the repo
- unrelated shared libraries not used by the target product
- experimental, dead, archived, or dormant modules unless referenced by the target product
- generic repo-wide issues that do not affect this product

## 2. Build the dependency surface first
Before deep review, identify the product’s actual dependency surface:
- runtime dependencies
- build dependencies
- test dependencies relevant to proving product correctness
- shared libraries used by product flows
- APIs/services/contracts the product depends on
- schemas/migrations/storage paths the product uses
- observability/security/automation components used by the product

Use that dependency graph to define audit boundaries.

## 3. Current codebase only
Base the audit on:
- current code
- current configs
- current scripts
- current tests
- current docs used by the product
- current routes/pages/components
- current APIs/contracts/schemas/migrations
- current deployment/runtime/observability/security artifacts

Do not rely on previous audit reports to define truth.
If previous audits exist, they may be used only as optional context after the current-code audit is complete, never as the baseline source of findings.

---

# Inputs

Use only what is relevant to the target product:
- product code
- dependent libraries/modules
- configs, scripts, build/deploy files
- tests for product and dependent modules
- routes/pages/components
- APIs/contracts/schemas/migrations used by the product
- architecture/design/docs relevant to the product
- observability/monitoring/security/privacy artifacts relevant to the product
- performance/load artifacts relevant to the product

If scope is ambiguous, infer it from imports, build graphs, package/module references, runtime wiring, API usage, and dependency declarations.

---

# Core rules

- Use evidence only.
- Do not trust claims without implementation proof.
- Do not trust implementation without end-to-end proof.
- Focus on current code behavior, not historical intent.
- Prefer root-cause analysis over symptom commentary.
- Do not reward complexity if a simpler system would perform better or be easier to operate.
- Do not reward AI/ML claims unless AI/ML is actually embedded into workflows and reduces customer effort.
- Do not reward automation that is opaque, ungovernable, or hard to observe.
- Do not reward polished UI over weak product, operational, or technical foundations.

---

# Mission A: Establish product boundary and dependency map

First determine:
- what the target product is
- what user-facing capabilities it owns
- which modules/libraries/services it actually depends on
- which dependencies are critical to runtime correctness
- which dependencies are critical to performance, security, privacy, o11y, automation, and AI/ML behavior

Produce a scoped dependency map:
- product modules
- direct dependent libraries
- important transitive dependencies
- external integrations/contracts
- supporting operational modules

Use this map to constrain the rest of the audit.

---

# Mission B: Validate feature completeness in current implementation

Determine whether the current product is truly complete for its intended scope, users, use cases, and workflows.

Validate:
- target users/personas/operators/admins
- core and secondary use cases
- expected workflows
- feature claims actually represented in current code/docs
- supporting backend/domain/data/contracts
- validation/auth/authz
- observability and auditability
- operational readiness
- edge cases and exception handling
- manual fallback behavior when automation cannot proceed safely

For each major feature/use case, determine:
- intended user and problem being solved
- expected workflow and outcome
- implementation evidence in current code
- missing pieces
- correctness status
- UX completeness
- automation completeness
- visibility/o11y completeness
- production credibility

Classify each as:
- complete and credible
- complete but fragile
- partial
- incorrect
- misleadingly complete
- non-production-grade
- missing but required

Explicitly detect:
- claimed features not fully implemented
- shallow workflows that stop before real value delivery
- backend capabilities without usable UX
- UX capabilities without real backend truth
- incomplete exception/recovery paths
- missing admin/operator/governance surfaces

---

# Mission C: Validate correctness deeply

Verify correctness in the current codebase and dependent modules.

Check:
- business rules
- computations
- state transitions
- workflow sequencing
- forms and validation
- API request/response handling
- persistence effects
- role/permission behavior
- retries/error handling
- loading/empty/error/success states
- search/filter/sort/pagination
- import/export/sync/reporting flows
- integration flows
- concurrency and stale-state behavior
- idempotency/retry correctness where relevant
- correctness of automated and manual paths
- correctness of AI/ML-assisted outcomes and fallbacks

For each critical flow:
- expected behavior
- actual behavior
- edge cases
- failure modes
- proof quality
- severity if wrong
- release confidence

---

# Mission D: Audit AI/ML as a first-class, pervasive capability

Determine whether AI/ML is embedded as a first-class citizen in the target product and its dependent modules where it creates real value.

AI/ML must be:
- implicit, not noisy
- pervasive where justified
- embedded into workflows, not bolted on
- used to reduce customer work
- measurable and observable
- safe, governable, and fallback-aware
- paired with deterministic methods where appropriate
- operationally visible even if mostly invisible to the user

Validate whether AI/ML is meaningfully used for:
- intent/context inference
- ranking/prioritization
- recommendation
- anomaly/error/duplicate/conflict detection
- validation assistance
- classification/tagging/routing
- summarization/extraction where useful
- forecasting/optimization/planning
- workflow adaptation
- quality improvement
- automation of repetitive or low-value steps
- reducing decision burden and manual input

For each AI/ML-relevant area, determine:
- what real problem it solves
- whether AI/ML is justified vs deterministic alternatives
- whether it is embedded into workflow instead of exposed as a gimmick
- whether it reduces manual effort
- whether outputs are monitored, explainable enough, and governable
- whether confidence/fallback/human-escalation paths exist
- whether the system remains trustworthy when AI/ML is wrong or unavailable

Classify AI/ML usage as:
- meaningful and embedded
- useful but shallow
- absent where needed
- misapplied
- gimmicky
- unsafe due to weak controls
- too visible to user for the value delivered

---

# Mission E: Minimize manual intervention

Audit whether the current product solves as much as possible automatically before asking the customer to act.

For major workflows, determine whether the system:
- infers or pre-fills what it reasonably can
- automates routine steps
- detects and resolves simple issues automatically
- asks for only necessary input
- avoids repetitive questions
- avoids making the user orchestrate internal system steps
- escalates only when confidence, policy, approval, governance, or ambiguity requires it
- provides safe defaults, suggestions, and next actions
- preserves trust while reducing clicks, steps, and decisions

Find places where:
- the product asks users to do work the system should do
- AI/ML should help but does not
- deterministic automation should exist before AI/ML
- users are forced to reconcile gaps manually
- internal intelligence exists but user workflows remain clumsy
- manual review is required too often without good reason

---

# Mission F: Full visibility of actions and operations

Determine whether both automation and manual actions have full operational visibility within the target product and its dependent modules.

Validate whether the system provides clear, inspectable visibility into:
- what actions were taken
- who/what took them
- when they happened
- why they happened
- what inputs/context were used
- what outputs/effects occurred
- whether the action was manual, automated, deterministic, or AI/ML-assisted
- what confidence/risk/status applied
- whether approval/review/escalation occurred
- whether rollback/retry/reconciliation is possible where needed

Audit visibility for:
- user actions
- system actions
- background jobs
- automated workflow steps
- AI/ML-driven decisions or suggestions
- integration calls
- security-sensitive operations
- data changes
- failures and retries
- approvals and overrides

Find gaps where automation exists but is opaque, untraceable, unauditable, or difficult to govern.

---

# Mission G: Audit observability and o11y

Determine whether the target product and its dependency surface have first-class observability for correctness, performance, failures, automation, AI/ML behavior, and operations.

Validate:
- logs
- metrics
- traces
- correlation IDs / request tracing
- job/workflow visibility
- audit trails
- alerting surfaces
- dashboards
- failure classification
- bottleneck visibility
- dependency health visibility
- user-impact visibility
- automation success/failure visibility
- AI/ML inference/evaluation visibility where relevant

Check whether observability answers:
- what happened
- where it happened
- why it failed
- who is affected
- whether it is recurring
- whether automation is helping or harming
- whether AI/ML is improving outcomes or degrading trust
- whether operators can diagnose issues quickly

Find:
- blind spots
- noisy useless telemetry
- missing key metrics
- poor tracing across boundaries
- weak audit trails
- missing visibility for automation or AI/ML decisions
- operational surfaces too shallow for real support

---

# Mission H: Audit security and privacy

Determine whether the target product and its dependent modules are secure and privacy-conscious.

Validate:
- authentication
- authorization
- least-privilege design
- secret handling
- config safety
- boundary validation
- tenant/user isolation if relevant
- input/output sanitization
- sensitive data handling
- logging/privacy hygiene
- encryption assumptions if relevant
- third-party/integration trust boundaries
- auditability of privileged actions
- approval/review paths for sensitive automation
- AI/ML data exposure risks
- privacy-preserving defaults
- data minimization practices where relevant

Find:
- weak auth/authz
- overexposure of sensitive data
- unsafe defaults
- privacy leaks
- logs/traces capturing sensitive content improperly
- weak trust boundaries
- insecure integrations
- automation acting without enough policy/governance
- AI/ML flows that leak or misuse sensitive information

---

# Mission I: Audit performance and efficiency

Determine whether the target product and its dependent modules are performant in design and implementation.

Validate:
- latency on critical paths
- throughput assumptions
- memory behavior
- CPU-heavy logic
- I/O efficiency
- database/query efficiency
- cache strategy
- batching/streaming where relevant
- async/concurrency handling
- render/recompute efficiency
- excessive network chatter
- hot-path complexity
- backpressure handling if relevant
- efficiency of automation/AI/ML pipelines

Search for:
- N+1 patterns
- repeated computation
- redundant queries/calls
- unnecessary serialization/deserialization
- blocking operations in wrong layers
- bad pagination/search/filter design
- unnecessary rerenders or state churn
- poor cache invalidation
- resource leaks
- inefficient file/object/media handling
- wasteful inference or repeated model calls

For each issue, determine:
- where it exists
- why it is inefficient
- impact under realistic and scaled load
- whether locally fixable or structural

---

# Mission J: Audit scalability and long-term architecture fitness

Determine whether the target product and its dependencies can scale technically and operationally while remaining understandable and maintainable.

Validate:
- load growth assumptions
- user/workflow/data growth handling
- persistence behavior under scale
- automation/job handling under scale
- AI/ML workload scaling assumptions
- observability usefulness under scale
- deployment/runtime topology realism
- failure-mode amplification under scale
- multi-tenant/multi-region/high-throughput evolution if relevant

Find:
- central bottlenecks
- points of contention
- coupling that blocks scale
- architecture choices that will break under growth
- overengineered scaling mechanisms with low present value
- underbuilt platform areas that will become blockers

Classify scalability concerns as:
- acceptable now
- near-term concern
- structural blocker
- future risk worth tracking
- overengineering

---

# Mission K: Audit simplicity in architecture and UX

Determine whether the target product is simple in the right way.

Simplicity means:
- fewer moving parts where possible
- understandable workflows
- low accidental complexity
- predictable behavior
- low operator burden
- low developer burden
- low customer burden
- AI/ML reducing effort rather than creating extra interaction

Find:
- unnecessary complexity
- duplicate patterns
- too many tools/frameworks for same job
- hard-to-follow flows
- too many config paths
- unclear ownership
- hidden control flow
- architecture/documentation mismatch
- user-facing workflows that expose internal system mess

Verify whether:
- the simplest viable design was chosen
- complexity was added only when justified
- the product reduces customer effort instead of shifting burden outward

---

# Mission L: Audit reliability, hardening, and production readiness

Validate:
- failure handling
- recovery behavior
- retries and idempotency
- concurrency/race safety
- data consistency and integrity
- config/env safety
- migration/release safety
- graceful degradation
- fallback behavior for AI/ML and integrations
- resilience under partial failure
- operational recovery procedures if present

Find:
- silent corruption risk
- fragile retries
- missing validation
- stale state issues
- hidden dependency failures
- partial failure blind spots
- weak fallback logic
- optimistic UI not backed by durable truth
- production paths still relying on mocks/stubs/placeholders

---

# Mission M: Judge proof quality

Do not count tests; judge whether they prove truth for the target product and its dependent modules.

Look for evidence via:
- unit tests
- integration tests
- API/service E2E tests
- browser E2E tests
- contract tests
- migration tests
- perf/load/stress tests
- failure-mode/chaos tests if present
- security/privacy tests if present
- observability dashboards/alerts if present
- AI/ML evaluation artifacts if present

For each critical area determine:
- expected proof
- current proof
- proof quality
- gaps in proof
- whether release confidence is justified

Flag all cases where:
- feature completeness is claimed without proof
- performance/scalability is asserted without evidence
- AI/ML value is claimed without evaluation or operational evidence
- tests validate implementation trivia instead of system truth
- critical flows lack end-to-end proof
- automation correctness lacks validation
- privacy/security assurances lack evidence

---

# Mission N: Identify gaps we must fill

Group gaps across:
- feature completeness
- correctness
- AI/ML embedding
- manual-effort reduction
- action visibility
- o11y
- security/privacy
- performance/efficiency
- scalability
- simplicity
- hardening/reliability
- testing/proof
- platform/operational excellence

For each gap include:
- what is missing or weak
- why it matters
- urgency
- impact on trust/performance/scale/customer effort
- whether foundational or incremental
- recommended direction

---

# Mission O: Define frontrunner technical and product positioning

Recommend how the target product can become a frontrunner through:
- superior correctness and trust
- higher feature completeness with lower complexity
- pervasive but implicit AI/ML
- lower customer effort
- stronger automation with clear governance
- full operational visibility
- stronger observability and debuggability
- better security/privacy posture
- better performance efficiency
- simpler scalable architecture
- faster time-to-value
- fewer moving parts with higher capability

For each proposed differentiator:
- what real problem it solves
- why it matters
- why current market solutions do not solve it well
- why it is technically credible
- feasibility
- defensibility
- effect on customer effort
- effect on trust/operations/scale
- whether it is commodity, meaningful differentiator, disruptive differentiator, or moat candidate

---

# Output

Return one compact structured report:

## 1. Executive verdict
- Scope audited
- Feature completeness: Strong / Moderate / Weak
- Technical excellence: Strong / Moderate / Weak
- Production readiness: Ready / Not Ready / Critically Not Ready
- Correctness confidence: High / Medium / Low
- AI/ML-native quality: Strong / Moderate / Weak
- Customer-effort reduction: Strong / Moderate / Weak
- Action/operation visibility: Strong / Moderate / Weak
- Observability quality: Strong / Moderate / Weak
- Security/privacy posture: Strong / Moderate / Weak
- Performance quality: Strong / Moderate / Weak
- Scalability readiness: Strong / Moderate / Weak
- Simplicity quality: Strong / Moderate / Weak
- Top 10–15 critical findings

## 2. Scope and dependency map
- target product boundary
- direct dependent libraries/modules
- critical transitive dependencies
- external integrations/contracts
- excluded out-of-scope areas

## 3. Feature completeness matrix
For each major feature/use case:
- feature/use case
- intended user/problem
- implementation evidence
- missing pieces
- correctness
- automation completeness
- visibility/o11y
- production credibility
- verdict

## 4. AI/ML-native assessment
For each AI/ML-relevant area:
- problem being solved
- current implementation
- whether AI/ML is justified
- whether AI/ML is embedded and pervasive
- whether it reduces manual effort
- fallback/governance/evaluation quality
- visibility/o11y quality
- verdict

## 5. Manual-effort reduction findings
- where system should do more automatically
- where user burden is too high
- where deterministic automation should exist
- where AI/ML should assist
- where manual review is overused

## 6. Action visibility / operations findings
- missing auditability
- opaque automation
- poor traceability
- weak governance/override surfaces
- missing visibility into background/system actions

## 7. Observability findings
- logging/metrics/tracing gaps
- dashboard/alert gaps
- automation visibility gaps
- AI/ML visibility gaps
- operator blind spots

## 8. Security/privacy findings
- auth/authz issues
- data exposure/privacy risks
- secret/config issues
- trust-boundary weaknesses
- unsafe automation/AI handling

## 9. Performance and efficiency findings
For each issue:
- location
- issue
- why inefficient
- scale impact
- severity
- recommended fix

## 10. Scalability findings
For each concern:
- area
- scalability risk
- current evidence
- time horizon
- severity
- recommended direction

## 11. Correctness and hardening findings
- workflow/area
- issue/risk
- severity
- evidence
- required fix

## 12. Simplicity findings
- unnecessary complexity
- duplicate patterns
- too many moving parts
- simplification opportunities

## 13. Proof gaps
- capability/claim
- expected proof
- current proof
- missing proof
- confidence
- tests/evaluations/benchmarks needed

## 14. Strategic gaps to fill
- what must improve for market leadership
- what must be simplified or removed
- what platform capabilities should be strengthened
- what hidden risks block trust, adoption, or scale

## 15. Prioritized execution plan
- Phase 0: correctness/security/privacy/hardening blockers
- Phase 1: feature completeness + proof + o11y + visibility
- Phase 2: performance/scalability + simplification + operational excellence
- Phase 3: pervasive AI/ML + autonomous value + differentiated leadership

For each item:
- problem
- why it matters
- fix direction
- expected impact
- priority

---

# Style

- Be direct, critical, and concrete.
- Use bullets, not essays.
- Reference real files/modules/routes/services/tests where possible.
- Distinguish clearly:
  - complete vs partial vs misleadingly complete
  - correct vs plausible
  - automated vs manual vs AI/ML-assisted
  - visible/governable vs opaque
  - secure/private vs risky
  - observable vs blind
  - performant/scalable vs merely claimed
  - simple vs underbuilt vs overbuilt
  - AI/ML embedded vs gimmicky

Final goal: determine whether the **current target product and its actual dependency surface** are feature-complete, correct, secure, observable, performant, scalable, simple, and truly AI/ML-native, while minimizing customer effort and preserving full visibility and governance over both manual and automated operations.