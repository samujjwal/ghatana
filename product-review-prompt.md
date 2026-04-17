Perform a **strict, evidence-based full audit** of the repository, tests, product docs, configs, architecture, and overall product direction.

This is **not a re-audit**. Do not optimize for validating previous findings.  
Optimize for discovering the truth of the current system from first principles.

Your job is to determine:

1. what vision the product is actually pursuing
2. what customer problems and pain points it is trying to solve
3. whether those problems are real, important, current, and future-relevant
4. whether the current system actually solves them end to end
5. whether code, tests, docs, configs, architecture, and UX are aligned with that vision
6. where the product is incomplete, fragile, incorrect, inefficient, misleading, or non-production-grade
7. what gaps must be filled to harden the product
8. how the product should evolve to become more competitive, more correct, and more differentiated

Your task is not to praise the system. Your task is to find truth, gaps, risks, and the highest-value path forward.

---

# Inputs

Use all available evidence:
- repository code
- tests
- configs
- scripts
- product docs
- vision docs
- architecture docs
- README files
- schemas / contracts / migrations
- routes / pages / components
- services / modules / domain logic
- deployment/runtime files
- issue/task/docs if present
- accessible competitor / market context if available to the agent

If browsing is available, use it for market, competitor, problem, and solution exploration.
If browsing is not available, still perform the exploration using internal context and clearly mark assumptions.

---

# Core rules

- Use evidence only.
- Do not trust claims without implementation proof.
- Do not trust implementation without end-to-end proof.
- Do not trust docs unless they align with code and tests.
- Do not reward partial implementation, happy-path demos, placeholder behavior, or polished UI over weak logic.
- Prefer root-cause analysis over symptom description.
- Prefer concrete flows, customer pains, and proof over generic product language.
- Be strict, direct, and precise.

---

# Mission A: Determine vision, scope, and intended product truth

Read product docs, architecture docs, vision docs, README files, and key code structure to determine:

- what product this actually is
- what space/category it is operating in
- who the intended users/customers are
- what the product claims to do
- what workflows it claims to support
- what outcomes it promises
- what technical and product scope it appears to target
- what future direction is implied by the docs and architecture

Then determine whether the current implementation aligns with that vision.

Explicitly identify:
- clear vision areas
- vague or contradictory vision areas
- overscoped areas
- underspecified areas
- missing product definition
- mismatches between docs and implementation
- where the product is building features without strong problem clarity

---

# Mission B: Problem and pain-point exploration

Deeply explore the problems and pain points this product is trying to solve.

Identify:
- primary customer problems
- secondary operational/technical problems
- current customer pain points
- emerging/future pain points
- problems caused by scale, complexity, governance, trust, AI adoption, workflow fragmentation, data sprawl, or operational burden
- whether these pains are urgent enough to drive adoption and sustained usage

For each major pain point:
- who experiences it
- why it matters
- how severe it is
- how often it occurs
- what the customer is trying to accomplish
- why existing approaches are insufficient
- whether this product addresses the root pain or just symptoms
- whether the repo/product actually implements a credible solution

Classify each pain point as:
- strongly relevant and well-targeted
- relevant but weakly addressed
- relevant but not implemented well
- vaguely targeted
- low-value / not compelling
- future-relevant but currently unsupported

---

# Mission C: Market and competitor exploration

Explore the relevant market deeply.

Compare against:
- direct competitors
- indirect competitors
- adjacent substitutes
- incumbent/manual/status-quo approaches
- AI-native/newer entrants
- relevant open-source alternatives

Analyze:
- what problems competitors solve well
- what they solve poorly
- where customers still suffer
- what has become table stakes
- what is still truly differentiating
- where the current product is behind
- where the current product has credible opportunity
- what market whitespace still exists
- what pains remain underserved
- what adoption friction exists in this category
- what trust, security, governance, UX, correctness, or operational gaps competitors leave unresolved

Do not stop at feature comparison. Also examine:
- workflow depth
- trust/correctness
- operational reality
- usability and cognitive load
- proof of value
- extensibility and maintainability
- switching friction
- long-term defensibility

---

# Mission D: Solution exploration

Determine whether the current solution approach is the right one.

Evaluate:
- whether the product architecture matches the problem shape
- whether workflows solve the actual user need
- whether the implementation is overcomplicated or underpowered
- whether there are better solution patterns available
- whether the product is solving root problems or superficial tasks
- whether automation/AI is useful or gimmicky
- whether the current solution creates new operational or UX burdens
- whether there are simpler, stronger, more defensible solution directions

Identify:
- current solution strengths
- weak solution choices
- missing solution layers
- missing workflow integration
- missing trust/governance/operability aspects
- better solution patterns to consider
- opportunities to reduce user effort while increasing correctness and reliability

---

# Mission E: Full audit of code, tests, docs, and configs against vision

Audit the full system against the intended vision and product promise.

Review:
- code quality and correctness
- architecture quality
- API/contract correctness
- data model/persistence integrity
- UI/UX completeness and simplicity
- tests and proof quality
- configs and environment safety
- build/deploy/runtime readiness
- documentation quality and alignment
- operational readiness and hardening posture

For each major capability, trace end to end:
- user-facing entry point
- page/route/component
- state transitions
- API/contract
- backend/service/domain logic
- schema/migration/persistence
- validation/auth/authz
- async/background side effects
- logging/observability/audit behavior
- tests proving behavior

Determine whether each capability is:
- complete and credible
- complete but fragile
- partial
- incorrect
- misleadingly complete
- non-production-grade

---

# Mission F: End-to-end correctness audit

Verify whether the product does what it claims correctly.

Check:
- user workflows
- route/page/screen behavior
- form handling and validation
- state transitions
- business rules and computations
- API request/response correctness
- persistence effects
- permission handling
- loading/empty/error/success states
- retries/failures/recovery behavior
- search/filter/sort/pagination
- import/export/sync/reporting/integration behavior
- edge-case handling
- stale-state, concurrency, and idempotency behavior where relevant

For each critical workflow:
- expected behavior
- actual behavior
- missing pieces
- incorrect behavior
- evidence
- severity
- release confidence

---

# Mission G: Hardening audit

Determine whether the product is truly hardened for production use.

Audit for:
- boundary validation
- failure handling
- retry/idempotency correctness
- concurrency/race safety
- data consistency/integrity
- authn/authz correctness
- config/env safety
- secrets handling
- dependency risk
- safe defaults
- migration/release safety
- brittle integration points
- observability gaps
- auditability gaps
- operational blind spots
- graceful degradation vs silent corruption
- resilience under partial failure
- ability to debug and operate safely in production

Do not accept “works in happy path demo” as hardened.

---

# Mission H: Fake completeness and shortcut detection

Aggressively search for:
- mocks/stubs/placeholders in production paths
- TODO/FIXME/HACK in critical flows
- hardcoded fake/sample/demo data
- simulated responses
- no-op implementations
- incomplete adapters/integrations
- dead/unreachable code
- optimistic UI not backed by durable backend truth
- partial workflows exposed as complete
- wrapper fixes around broken root logic
- inefficient shortcuts used instead of proper design
- temporary workarounds accepted as final implementation

Distinguish:
- acceptable test-only usage
- unacceptable production-path usage

---

# Mission I: Test and proof audit

Do not count tests. Judge proof quality.

Review:
- unit tests
- integration tests
- API/service E2E tests
- browser E2E tests
- contract tests
- migration tests
- perf/load tests
- failure-mode tests
- security tests where relevant

For each major capability/use case:
- what proof should exist
- what proof exists
- whether existing tests validate behavior or implementation trivia
- whether tests rely too much on mocks
- whether critical flows lack proof
- whether proof is sufficient for release confidence

---

# Mission J: UX audit

Determine whether the UI/UX is:
- simple
- understandable
- low cognitive load
- complete
- correctly wired to backend truth
- robust across all states
- consistent and actionable
- capable of guiding users through real workflows without confusion

Find:
- broken journeys
- missing states
- confusing interactions
- clutter
- hidden complexity
- misleading completeness
- weak feedback after actions
- broken continuity across routes/screens
- accessibility/usability issues
- places where UX makes correctness or trust harder

---

# Mission K: Identify gaps to fill

Identify the most important gaps across:
- vision clarity
- problem-solution fit
- market relevance
- product scope
- use-case coverage
- correctness
- hardening
- architecture
- integrations
- data model
- testing/proof
- UX
- observability/operations
- scalability/performance
- differentiation and defensibility

For each gap include:
- what is missing or weak
- why it matters
- current or future impact
- whether it blocks trust, adoption, correctness, or scale
- urgency
- recommended direction

---

# Mission L: Positioning, differentiation, and frontrunner path

Determine how the product can become stronger in the market.

Recommend:
- where to narrow or sharpen the problem focus
- which pains to own first
- which workflows to complete more deeply
- what must be hardened to earn trust
- what differentiators are meaningful vs commodity
- what disruptive or innovative moves are credible
- what long-term moat candidates exist
- what product claims should be strengthened, reframed, or removed
- what solution directions could make the product a frontrunner

For each proposed differentiator or strategic move:
- what pain it solves
- why it matters
- why competitors do not solve it well
- whether it is credible to implement
- whether it improves acquisition, retention, expansion, trust, or defensibility
- whether it solves current pain, future pain, or both

Distinguish clearly:
- commodity improvements
- meaningful differentiators
- disruptive differentiators
- long-term moat candidates

---

# Output format

Return one structured report with these sections:

## 1. Executive verdict
- Production readiness: Ready / Not Ready / Critically Not Ready
- Feature completeness: Complete / Partial / Misleadingly Complete / Incomplete
- Correctness confidence: High / Medium / Low
- Hardening: Strong / Moderate / Weak
- UI/UX quality: Strong / Moderate / Weak
- Problem-solution fit: Strong / Moderate / Weak
- Competitive position: Strong / Moderate / Weak
- Innovation/differentiation potential: Strong / Moderate / Weak
- Top 10–15 critical findings

## 2. Vision and scope assessment
- inferred vision
- target users/customers
- key product claims
- scope clarity vs ambiguity
- alignment/misalignment between docs and implementation

## 3. Problem and pain-point analysis
For each major pain point:
- pain point
- who has it
- why it matters
- severity/frequency
- current solutions and their weakness
- whether this product addresses it credibly
- verdict

## 4. Market and competitor analysis
- major competitors/substitutes
- where they are stronger
- where they are weaker
- unresolved market pain
- whitespace opportunities
- where this product is currently behind
- where it can credibly lead

## 5. Solution assessment
- current solution strengths
- weak solution choices
- missing solution layers
- alternative/better solution directions
- whether the product solves root problems or symptoms

## 6. Product claim vs reality matrix
For each major capability:
- capability
- claimed in
- implementation evidence
- missing pieces
- correctness
- hardening
- test evidence
- final verdict

## 7. Gap analysis
Group by:
- vision/product
- problem-solution fit
- frontend
- backend
- data/persistence
- contracts/APIs
- testing/proof
- hardening/security
- observability/operations
- performance/scalability
- UX
- market/differentiation

## 8. Hardening findings
- location
- issue
- failure/risk mode
- severity
- required fix direction

## 9. Fake completeness findings
- location
- evidence
- why unacceptable
- risk
- required replacement

## 10. End-to-end correctness findings
- workflow
- expected behavior
- actual behavior
- affected layers/files
- severity
- required correction

## 11. Testing and proof gaps
- capability/use case
- expected proof
- current proof
- confidence level
- recommended tests

## 12. UI/UX findings
- broken journeys
- missing states
- confusing patterns
- hidden complexity
- accessibility/usability concerns
- recommended simplifications

## 13. Strategic recommendations
- what to remove
- what to simplify
- what to complete
- what to harden
- what to validate with customers/market
- what to differentiate on
- what to deprioritize

## 14. Prioritized action plan
- Phase 0: correctness blockers + fake completeness + hardening gaps
- Phase 1: proof/tests + workflow completion + architecture fixes
- Phase 2: UX simplification + operational readiness + performance
- Phase 3: differentiation + innovation + frontrunner positioning

For each item:
- problem
- why it matters
- fix direction
- priority
- expected impact

---

# Style

- Be direct, critical, and concrete.
- Use bullets, not essays.
- Reference real files/modules/routes/services/tests where possible.
- Clearly distinguish:
  - claimed vs implemented
  - complete vs partial
  - correct vs plausible
  - hardened vs demo-ready
  - valuable vs low-value
  - innovative vs gimmicky
  - differentiated vs commodity

Final goal: determine whether the product is solving the right problems, whether the current system actually solves them correctly and credibly, what must be hardened, what gaps remain, and what product/technical/strategic moves are required to become a strong, trusted, differentiated frontrunner.