# Documentation Glossary and Policy Appendix

Version: 1.0  
Status: Shared terminology and policy baseline for project documentation  
Date: March 1, 2026

---

## Purpose

This appendix defines the standard terminology and policy rules that all active Project Siddhanta documents should use. It exists to prevent drift across strategy, specification, research, and analysis artifacts.

Active planning documents should reference this appendix rather than inventing local terminology for AI oversight, integration readiness, or data residency.
Time-sensitive external facts should be sourced from [Authoritative_Source_Register.md](Authoritative_Source_Register.md) or an equivalent primary source with an explicit as-of date.
Reference style for time-sensitive external facts: use `ASR-*` identifiers from the source register.
Legal-claim traceability: [Legal_Claim_Citation_Appendix.md](Legal_Claim_Citation_Appendix.md)
Recurring refresh procedure: [ASR_Monthly_Refresh_Checklist.md](ASR_Monthly_Refresh_Checklist.md)
Compact review artifact: [Claim_Traceability_Matrix.md](Claim_Traceability_Matrix.md)
Customer-safe external summary: [Customer_Facing_Claims_Pack.md](Customer_Facing_Claims_Pack.md)

---

## Standard Terms

### AI Oversight Taxonomy

- `deterministic_controls_only`
  - Fixed rules execute the decision. AI may observe, score, or prioritize, but cannot decide the action.
- `human_review_required`
  - AI may prepare draft outputs or recommendations, but no effect occurs until a human explicitly reviews and approves.
- `human_in_the_loop`
  - AI participates in the decision path, but a human approval step is required before final effect.
- `human_on_the_loop`
  - AI may execute within pre-approved guardrails while a human monitors exceptions, audit trails, and override controls.

### Automation Scope

- `bounded autonomous`
  - Automation allowed only for tightly bounded, low-impact, well-understood workflows with explicit scope limits, fallback behavior, and auditable controls.
- `standard-case straight-through automation`
  - Automation targets that apply only to routine, structured, low-variance cases. Exceptions, edge cases, and material events remain reviewable.

### Integration Readiness

- `approval-dependent eKYC`
  - National ID / eKYC integration is a strategic target, but production use depends on regulator approval, contractual access, legal permissibility, and interface readiness.
- `integration adapter`
  - External connectivity should be described as an adapter pattern, not assumed uniform API access. Adapters may use API, file exchange, SFTP, batch, or assisted workflows.

### Data Residency

- `in-country default data residency`
  - Regulated data should remain in-country by default. Cross-border processing, failover, or tooling is allowed only where explicitly permitted by the applicable regulator or legal framework.

### Evidence and Forecasting Language

- `verified metric`
  - A date-sensitive fact backed by a cited source and a clear as-of date.
- `illustrative estimate`
  - A planning estimate or modelled range used for strategy, not a committed forecast or guaranteed commercial outcome.
- `ASR reference ID`
  - A stable citation key that points to an entry in `Authoritative_Source_Register.md` and should be used instead of ad hoc inline source prose for time-sensitive external facts.

---

## Policy Rules

1. High-impact regulated actions must not be described as `full autonomy` or `human_out_of_the_loop`.
2. National ID / eKYC references must use approval-dependent language unless production availability is independently confirmed.
3. Data residency language must default to in-country storage for regulated data unless an explicit exception is stated.
4. Performance claims must be bounded by scope, conditions, and operating context.
5. Metrics in active planning documents should include explicit dates when they are time-sensitive.
6. Archived or superseded documents are non-authoritative and should be labeled accordingly.
7. Time-sensitive, externally sourced facts should cite `Authoritative_Source_Register.md` or the relevant primary-source URL.
8. When a time-sensitive fact is reused across documents, prefer the same `ASR-*` identifier everywhere.

---

## Active Source Precedence

For conflicts between active documents, use this order:

1. `Siddhanta_Platform_Specification.md`
2. `siddhanta.md`
3. `revised_comprehensive_analysis.md`
4. `deep-research-report (2).md`
5. `All_In_One_Capital_Markets_Platform_Specification.md`

Archived materials and generated intermediate reports are reference-only.
For time-sensitive external facts, the source register overrides stale embedded counts in older drafts.
