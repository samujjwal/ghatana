# Project Siddhanta: Broker + DP Proposal Insert

Version: 1.0.1
Status: Customer-facing broker/depository-participant insert derived from verified claims only
Last revised: March 10, 2026

Primary source baseline: [Customer_Facing_Claims_Pack.md](Customer_Facing_Claims_Pack.md)  
Source register: [Authoritative_Source_Register.md](Authoritative_Source_Register.md)  
Legal traceability: [Legal_Claim_Citation_Appendix.md](Legal_Claim_Citation_Appendix.md)

---

## Why This Matters For Brokers

Nepal’s broker ecosystem is large enough to justify controls-grade workflow modernization.

As of March 1, 2026:

- SEBON lists `90` stock brokers and `2` stock dealers (`Ref: ASR-NEP-SEBON-2026-03-01`).
- CDSC shows `122` licensed depository participants (`Ref: ASR-NEP-CDSC-2026-03-01`).

The operational pressure points are not just trading throughput. The harder problems are:

- client-funds segregation,
- margin control,
- contract-note timing,
- quarterly return timeliness,
- audit-ready evidence.

---

## What Siddhanta Delivers

Project Siddhanta is built as a **generic, extensible capital markets operating platform** — Nepal is the first jurisdiction, not the ceiling. The Generic Core handles all platform-wide concerns (identity, ledger, compliance rule evaluation, event sourcing, AI governance, audit) without any hard-coded Nepal-specific logic. Nepal's regulatory rules, exchange parameters, and fee schedules are implemented as pluggable packs that can be upgraded or replaced as rules change — without touching the core.

For brokers and DPs, Siddhanta focuses on:

- broker control automation,
- post-trade reconciliation and exception handling,
- margin and exposure workflow support,
- regulator-facing reporting support,
- audit and evidence traceability.

The platform is designed to improve control quality first, then reduce manual workload inside those controls.

---

## Clause-Backed Broker Controls

The current documentation set includes clause-backed anchors for the core broker workflow:

- broker onboarding must maintain client records and capture pre-account-opening risk disclosure acknowledgement (`Ref: LCA-010`)
- trading members must use a separate NEPSE-designated clearing bank account and cannot cross-use customer advances (`Ref: LCA-012`)
- trading members must provide prompt trade-completion information and retain complete trading / clearing / settlement records for five years (`Ref: LCA-012`)
- a licensed broker must provide the contract note on the day following the transaction (`Ref: LCA-021`)
- brokers, dealers, and market makers must file the Board-specified quarterly return within `30 days` of quarter close (`Ref: LCA-024`)

These are strong anchors for a controls-first operating platform because they connect product value to real obligations, not generic “compliance automation” language.

---

## Commercial And Operational Impact

The platform also aligns to the currently pinned broker economics and margin rules:

- the current brokerage schedule is clause-backed by slab (`Ref: LCA-023`)
- the Board levy on brokerage service charge and the securities-market transaction fee are both explicitly traceable (`Ref: LCA-023`)
- margin-service eligibility and the `30%` / `20%` minimum margin thresholds are clause-backed (`Ref: LCA-011`)

This supports:

- accurate fee logic,
- fewer manual posting errors,
- stronger exception handling,
- cleaner margin-call and liquidation workflows.

---

## Safe External Positioning

Use:

- “The platform is built around clause-backed broker controls such as segregated client-funds handling, next-day contract notes, and Board-timed quarterly returns.”
- “Broker fee logic and margin thresholds are tied to current clause-backed rules rather than ad hoc table logic.”
- “The platform reduces manual control breaks while preserving human approval where the workflow is high-impact.”

Avoid:

- claiming a clause-verified daily-reconciliation legal clock until `LCA-022` is closed,
- claiming any fixed live filing template without current channel revalidation,
- claiming full autonomy for regulated broker decisions.

---

## Suggested Pilot

1. Client-money and post-trade control workflows
2. Margin monitoring and margin-call orchestration
3. Quarterly return preparation and evidence-pack export

This is the fastest way to prove reduced operational risk and faster regulator response with a limited implementation surface.
