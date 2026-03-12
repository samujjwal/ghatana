# Project Siddhanta: One-Page Proposal Insert

Version: 1.0.1
Status: Customer-facing insert derived from verified and clause-backed claims only
Last revised: March 10, 2026

Primary source baseline: [Customer_Facing_Claims_Pack.md](Customer_Facing_Claims_Pack.md)  
Source register: [Authoritative_Source_Register.md](Authoritative_Source_Register.md)  
Legal traceability: [Legal_Claim_Citation_Appendix.md](Legal_Claim_Citation_Appendix.md)

---

## What Problem This Solves

Nepal’s capital markets have reached consumer scale but still rely on fragmented operational workflows, manual reconciliation, and regulator-sensitive reporting.

As of March 1, 2026:

- Nepal’s depository infrastructure shows `7,511,020` demat accounts and `6,586,798` registered MeroShare users (`Ref: ASR-NEP-CDSC-2026-03-01`).
- CDSC currently shows `122` licensed depository participants, `43` registered C-ASBA banks, and `46` RTA entries (`Ref: ASR-NEP-CDSC-2026-03-01`).
- SEBON currently lists `90` stock brokers, `2` stock dealers, `32` merchant bankers, `24` mutual funds, and `19` specialized investment fund managers (`Ref: ASR-NEP-SEBON-2026-03-01`).

The market is large enough to justify infrastructure-grade workflow automation, but operational risk remains concentrated in:

- broker controls and reconciliations,
- primary-market issue execution,
- issuer disclosure and filing timeliness,
- regulator-facing evidence production.

---

## What Siddhanta Delivers

Project Siddhanta is an AI-native, controls-first capital markets operating platform — built as a generic, extensible core with Nepal as the first jurisdiction instantiation.

The architecture is designed as **one platform for all operators, all jurisdictions, all asset classes**: a single Generic Core (19 kernel modules covering identity, ledger, compliance, events, AI governance, and more) extended by pluggable Jurisdiction Packs, Operator Packs, and Exchange Adapters. Adding a new country means shipping a new plugin bundle — not rebuilding the platform. Adding a new operator category means composing a new Operator Pack — not forking the codebase.

For Nepal's current market, Siddhanta delivers:

- clause-backed broker control automation,
- primary-market workflow orchestration for merchant bankers,
- issuer disclosure and filing support,
- regulator-ready audit and evidence packs,
- bounded AI assistance with explicit human oversight for high-impact actions.

The platform is designed around deterministic controls first, with AI used for document validation, reconciliation support, exception handling, and draft reporting inside auditable guardrails.

---

## Why The Compliance Story Is Defensible

The current documentation set now includes clause-backed anchors for the core operating claims:

- Broker controls:
  - segregated client-funds handling and restricted use of customer cash (`Ref: LCA-012`)
  - next-day contract notes (`Ref: LCA-021`)
  - Board-timed quarterly returns within `30 days` (`Ref: LCA-024`)
- Broker economics:
  - brokerage slabs, Board levy, and securities-market transaction fee (`Ref: LCA-023`)
- Primary-market filing:
  - prescribed prospectus structure and issuance-manager due diligence certificate (`Ref: LCA-025`)
  - `7-day` post-issue close reporting to the Board after sale / distribution / allotment completion (`Ref: LCA-026`)
- Listed issuer disclosure:
  - defined timelines for book-closure notices, meeting packs, progress reporting, and shareholder-record disclosures (`Ref: LCA-027`)
  - suspension exposure for late price-sensitive or annual / semiannual reporting (`Ref: LCA-028`)
- AI governance:
  - explicit consent, auditability, and board-governed oversight expectations (`Ref: LCA-001`, `Ref: LCA-002`, `Ref: LCA-015`)

This gives the platform a stronger external story than generic “compliance-ready” messaging because the core workflow claims now map to specific source-backed and clause-backed obligations.

---

## Recommended Entry Wedge

The strongest near-term wedge is:

1. Broker + DP operating controls
2. Merchant banker issue workflows
3. Issuer disclosure support

That bundle targets the most visible operational pain:

- reconciliation breaks,
- filing deadlines,
- issue-close reporting,
- manual evidence assembly,
- audit and regulator response time.

This is a stronger entry point than trying to sell a full exchange-grade replacement on day one.

---

## What To Say Externally

Use:

- “The platform is built around clause-backed broker controls such as segregated client-funds handling, next-day contract notes, and Board-timed quarterly returns.”
- “For Nepal primary-market workflows, the current filing baseline includes a prescribed prospectus structure, an issuance-manager due diligence certificate, and a 7-day post-issue close reporting clock.”
- “For listed issuers, disclosure timeliness is a real control issue because late price-sensitive or periodic reporting can create suspension exposure.”
- “High-impact AI actions are governed with explicit human oversight rather than full autonomy.”

Avoid:

- claiming a fixed live filing template unless revalidated for the specific issuer or filing channel,
- claiming a universal legal requirement for in-country hosting unless a clause is separately pinned,
- claiming a clause-verified daily-reconciliation legal clock until `LCA-022` is closed.

---

## Suggested Pilot Scope

A credible first implementation can focus on:

1. Broker reconciliation and post-trade controls
2. Merchant banker IPO/FPO workflow and issue-close reporting
3. Issuer filing calendar, disclosure tracking, and evidence export

This keeps the pilot narrow enough to ship, while still proving the platform’s core value: fewer manual breaks, faster reporting, and stronger regulator-visible control evidence.
