# Project Siddhanta: Merchant Banker Proposal Insert

Version: 1.0.1
Status: Customer-facing merchant-banker insert derived from verified claims only
Last revised: March 10, 2026

Primary source baseline: [Customer_Facing_Claims_Pack.md](Customer_Facing_Claims_Pack.md)  
Source register: [Authoritative_Source_Register.md](Authoritative_Source_Register.md)  
Legal traceability: [Legal_Claim_Citation_Appendix.md](Legal_Claim_Citation_Appendix.md)

---

## Why This Matters For Merchant Bankers

Primary-market execution in Nepal is operationally heavy and deadline-sensitive.

As of March 1, 2026:

- SEBON lists `32` merchant bankers (`Ref: ASR-NEP-SEBON-2026-03-01`).
- CDSC shows `43` registered C-ASBA banks (`Ref: ASR-NEP-CDSC-2026-03-01`).

That creates a workflow environment where issue execution depends on:

- clean prospectus filing,
- due-diligence traceability,
- ASBA coordination,
- timely issue-close reporting,
- auditable allotment support.

---

## What Siddhanta Delivers

Siddhanta is built on a **pluggable, extensible architecture** — jurisdiction-specific filing timelines, prospectus schemas, and submission mechanics are implemented as versioned Rule Packs and Config Packs layered on top of a jurisdiction-agnostic Generic Core. When SEBON updates a directive, the corresponding Rule Pack is updated and deployed without core system changes.

For merchant bankers, Siddhanta focuses on:

- primary-market workflow orchestration,
- prospectus and filing-pack control,
- due-diligence evidence management,
- allotment and issue-close reporting support,
- exception-aware ASBA and application reconciliation.

The target is a controls-grade issue workflow rather than a loosely automated document process.

---

## Clause-Backed Primary-Market Anchors

The current documentation set now includes clause-backed anchors for the core issue workflow:

- merchant bankers are legally allowed to perform issue management, underwriting, and share registration / registrar functions (`Ref: LCA-005`, `Ref: LCA-006`)
- draft prospectus approval requires a defined submission pack, including an issuance/sales-manager due diligence certificate (`Ref: LCA-025`)
- the Board-approved prospectus follows the prescribed schedule format (`Ref: LCA-025`)
- after completing a public issue, sale, distribution, or allotment, the issuer must inform the Board within `7 days` with the prescribed issue-close particulars (`Ref: LCA-026`)

This makes the platform’s primary-market workflow story materially stronger because the key submission and timing claims are now traceable to clause-backed sources.

---

## What That Means Operationally

Siddhanta can be positioned as a system that helps merchant bankers:

- assemble the current filing pack correctly,
- maintain a due-diligence evidence trail,
- coordinate issue-close and allotment reporting against the current `7-day` clock,
- keep live filing templates configurable and revalidated per issue rather than hard-coded.

That last point matters: the timing duties are clause-backed, but the exact active electronic template still needs live-process validation before filing.

---

## Safe External Positioning

Use:

- “The platform is built around the current clause-backed primary-market filing baseline: prescribed prospectus structure, issuance-manager due diligence certificate, and a 7-day post-issue close reporting clock.”
- “The platform improves issue execution by making the filing pack, evidence trail, and post-issue close reporting workflow auditable and deadline-aware.”
- “Allotment and issue-close reporting remain configurable because live filing templates must still be revalidated per issue.”

Avoid:

- claiming a fixed modern portal template unless it is refreshed from the live filing channel,
- claiming that every issue follows an identical exchange or Board submission workflow without current validation.

---

## Suggested Pilot

1. Prospectus and due-diligence submission-pack workflow
2. Application / ASBA exception handling and reconciliation
3. Allotment and post-issue close reporting within the current 7-day clock

This gives a fast proof point on issue execution discipline, reporting timeliness, and reduced manual filing risk.
