# Authoritative Source Register

Version: 1.0.1
Status: Primary-source register for time-sensitive external facts used in active project documents
Last revised: March 10, 2026 | Source data verified: March 1, 2026

---

## Purpose

This register is the canonical reference point for date-sensitive market counts, regulatory-status statements, and external-source caveats used across the active Project Siddhanta documentation set.

Use this file before reusing embedded counts from any strategy, specification, or research document. If a number here changes, the active documents should be refreshed to match or downgraded to an illustrative estimate.

## Reference Style

Use `ASR-*` identifiers when citing this register from active project documents.

Legal traceability companion: [Legal_Claim_Citation_Appendix.md](Legal_Claim_Citation_Appendix.md)  
Refresh procedure: [ASR_Monthly_Refresh_Checklist.md](ASR_Monthly_Refresh_Checklist.md)
Compact traceability matrix: [Claim_Traceability_Matrix.md](Claim_Traceability_Matrix.md)
Customer-safe external summary: [Customer_Facing_Claims_Pack.md](Customer_Facing_Claims_Pack.md)

Formatting rule:

- Prefer `Ref: ASR-...` in prose.
- Prefer a dedicated `Reference ID` column in tables that carry time-sensitive facts.
- Multiple references may be combined with `/` when a row depends on more than one source.

---

## Verified Operational Metrics

### CDSC (Central Depository System and Clearing Limited)

Reference ID: `ASR-NEP-CDSC-2026-03-01`

Primary source: [CDSC homepage](https://cdsc.com.np/)

Verified on March 1, 2026:

- BO Demat Accounts: `7,511,020`
- Registered MeroShare Users: `6,586,798`
- Licensed Depository Participants: `122`
- Registered C-ASBA Banks: `43`
- RTA List entries: `46`

Usage rule:

- Treat these as the current live counts for Nepal depository-adoption references unless a newer CDSC homepage check supersedes them.

### SEBON (Securities Board of Nepal)

Reference ID: `ASR-NEP-SEBON-2026-03-01`

Primary source: [SEBON intermediaries page](https://www.sebon.gov.np/intermediaries)

Verified on March 1, 2026:

- Stock Brokers: `90`
- Stock Dealers: `2`
- Merchant Bankers: `32`
- Fund Manager and Depository: `17`
- Depository Participants: `122`
- Credit Rating Agencies: `3`
- ASBA institutions: `43`
- Mutual Funds: `24`
- Specialized Investment Fund Managers: `19`

Usage rule:

- The `17` figure is the SEBON category label `Fund Manager and Depository`. Do not restate it as `17 standalone fund managers` unless separately verified.

---

## Verified Regulatory Baselines

### NRB AI Governance

Reference ID: `ASR-NEP-NRB-AI-2025-12`

Primary source: [NRB AI Guidelines PDF](https://www.nrb.org.np/contents/uploads/2025/12/AI-Guidelines.pdf)

Verified baseline:

- The document is dated December 2025.
- AI-enabled customer-facing access requires explicit consent.

Usage rule:

- Do not describe this as `AI Guidelines 2024`.
- Do not infer consent-expiry periods unless the product policy explicitly defines them.

### NRB eKYC / National ID Readiness

Reference ID: `ASR-NEP-NRB-EKYC-2025-01`

Primary source: [NRB Payment Systems Oversight Report FY 2023/24](https://www.nrb.org.np/contents/uploads/2025/01/Payment-Oversight-Report-2023-24.pdf)

Verified baseline:

- NRB states it is coordinating with the Department of National ID and Civil Registration to implement eKYC in Nepal.

Usage rule:

- National ID / centralized eKYC should be described as approval-dependent and readiness-dependent, not assumed live for production integration.

### NRB Cyber Resilience

Reference ID: `ASR-NEP-NRB-CYBER-2023-08`

Primary source: [NRB Cyber Resilience Guidelines 2023](https://www.nrb.org.np/contents/uploads/2023/08/Cyber-Resilience-Guidelines-2023.pdf)

Verified baseline:

- This is the current directly verified NRB cyber-governance source used in the docs.

Usage rule:

- Do not state that this source creates a fixed `24-hour` breach-notification obligation to `FinCERT-Nepal` unless a separate active circular is cited.
- Do not treat a financial-sector `FinCERT` workflow as an active institution or mandatory channel unless formally published.

---

## Design Assumptions That Require Revalidation

### NEPSE Trading Windows

Reference ID: `ASR-NEP-NEPSE-OPS-ASSUMPTION`

Primary source for future validation: [NEPSE official website](https://www.nepalstock.com/)

Current documentation rule:

- Treat exact trading-session windows as design assumptions that must be revalidated against the latest NEPSE circulars and operating notices before production rollout.
- Do not present an exact session timetable in active documents as a permanent regulatory fact unless linked to a current official notice.

### NEPSE Listing-Process Mechanics

Reference ID: `ASR-NEP-NEPSE-LISTING-OPS-ASSUMPTION`

Primary source for future validation: [NEPSE official website](https://www.nepalstock.com/)

Current documentation rule:

- Use `LCA-027` and `LCA-028` for clause-backed listed-issuer disclosure and suspension duties.
- Treat exact NEPSE submission mechanics, channel workflow, and any exchange-specific listing-operation circulars as live-process assumptions that must be revalidated against the latest NEPSE notices before production rollout.
- Do not present a generic `NEPSE Listing Rules` label as a clause-backed authority in active documents unless a current official NEPSE rule or circular extract is pinned.

### NEPSE AI Interaction Policy Signal

Reference ID: `ASR-NEP-NEPSE-AI-2026-01-29-ASSUMPTION`

Primary source for future validation: [NEPSE official website](https://www.nepalstock.com/)

Current documentation rule:

- A reported January 29, 2026 `Capital Market in AI Era` interaction involving NEPSE leadership may be retained only as a policy-signal assumption pending an official NEPSE notice, publication, or equivalent authoritative source.
- Do not present that interaction as a verified regulatory or exchange-position statement in active documents until a primary-source artifact is pinned.

### NEPSE Settlement-Cycle Operating Assumption

Reference ID: `ASR-NEP-NEPSE-SETTLEMENT-OPS-ASSUMPTION`

Primary source for future validation: [NEPSE official website](https://www.nepalstock.com/)

Current documentation rule:

- Treat exact settlement-cycle text in active documents as an operating assumption that must be revalidated against current NEPSE/CDSC settlement notices before production rollout.
- Keep settlement-cycle logic parameterized in product design and do not market a hard-coded cycle as clause-verified unless a current primary source is pinned.

### NEPSE Circuit-Breaker Operating Assumption

Reference ID: `ASR-NEP-NEPSE-CB-OPS-ASSUMPTION`

Primary source for future validation: [NEPSE official website](https://www.nepalstock.com/)

Current documentation rule:

- Treat exact intraday circuit-breaker bands and halt triggers as operating assumptions that must be revalidated against the latest NEPSE circulars before production rollout.
- Do not present specific percentage bands in active documents as fixed regulator-grade facts unless a current official notice is pinned.

### Market Capitalization and Turnover Benchmarks

Reference ID: `ASR-NEP-MKT-ILL-2025` / `ASR-NEP-TURNOVER-ILL-2026-01`

Current documentation rule:

- Mid-2025 market-cap figures, working listed-company ranges, and January 2026 turnover references may be retained as illustrative estimates or secondary-source benchmarks.
- These should not be treated as current live operating metrics unless replaced with a current primary-source publication.

### Nepal Fiscal-Year Baseline

Reference ID: `ASR-NEP-FY-STATUTORY`

Statutory baseline:

- Nepal's fiscal year is generally referenced in the documentation as Shrawan to Ashadh under the Bikram Sambat calendar.

Usage rule:

- This is a structural legal-calendar reference, not a live market metric.

---

## Regional Benchmark References (Comparative / Mixed-Date)

These references support comparative rows in `deep-research-report (2).md`. They are benchmark inputs, not Nepal go-live operating baselines.

### India Current Statistics (SEBI)

Reference ID: `ASR-IND-SEBI-2025-09`

Primary source: [SEBI Bulletin, Current Statistics table (October 2025 issue carrying September 2025 data)](https://www.sebi.gov.in/sebi_data/attachdocs/oct-2025/1761810646113.pdf)

Verified benchmark values:

- Demat accounts: `20,70,59,626` (~207.1M)
- All India market capitalisation: `452 lakh crore`
- Mutual fund average assets under management: `75,61,309 crore`
- Merchant bankers: `235`
- Depository participants: `NSDL 299`, `CDSL 582`

Usage rule:

- Use this for the India comparative table rows unless replaced by a newer official SEBI current-statistics release.

### India / Bangladesh / Sri Lanka Market-Cap Benchmarks (WFE)

Reference ID: `ASR-IND-WFE-2025-07` / `ASR-BGD-WFE-2025-07` / `ASR-LKA-WFE-2025-07`

Primary source: [WFE market statistics page (September 2025 issue; July 2025 exchange rows)](https://focus.world-exchanges.org/issue/september-2025/market-statistics)

Verified benchmark values:

- National Stock Exchange of India domestic market capitalisation: `USD 4,831,709.86 million`
- Dhaka Stock Exchange domestic market capitalisation: `USD 29,738.19 million`
- Colombo Stock Exchange domestic market capitalisation: `USD 22,095.48 million`

Usage rule:

- Use these only for cross-country comparative charts or benchmark framing, not as current transaction-operating metrics.

### Bangladesh Depository Indicators (CDBL)

Reference ID: `ASR-BGD-CDBL-2026-02-26`

Primary source: [CDBL homepage](https://www.cdbl.com.bd/)

Verified on February 26, 2026:

- BO accounts (operable in CDS): `1,650,111`
- Depository participants: `559`
- ISO 27001 certification is stated on the homepage

Usage rule:

- This is the preferred Bangladesh depository benchmark for the regional report.

### Bangladesh Broker-Base Benchmark (Exchange Publication)

Reference ID: `ASR-BGD-CSE-2025-Q1`

Secondary exchange-market publication: [CSE Portfolio, January-March 2025](https://www.cse.com.bd/upload/CSE_Porfolio/2410341851.pdf)

Verified benchmark values:

- Dhaka Stock Exchange TREC holders: `250`
- Active TREC holders: `234`

Usage rule:

- Treat this as a comparative benchmark, not a live membership count. Replace it with a newer official exchange source when available.

### Sri Lanka Digital Onboarding (SEC Sri Lanka)

Reference ID: `ASR-LKA-SEC-2026`

Primary source: [SEC Sri Lanka digitalization overview](https://sec.gov.lk/digitalization-and-the-securities-market/)

Verified baseline:

- SEC Sri Lanka describes digital onboarding with eKYC, biometric authentication, NIC verification, and an onboarding target of less than five minutes.

Usage rule:

- Keep this as a market-direction indicator; revalidate exact operational timings against current CSE/CDS workflows before treating it as an SLA.

### Sri Lanka CDS App Workflow (CSE)

Reference ID: `ASR-LKA-CSE-APP-2026`

Primary source: [CSE mobile app / CDS e-Connect page](https://www.cse.lk/mobile-app)

Verified baseline:

- The page states that investors can create a new CDS account and receive details within 24 hours via the app.

Usage rule:

- Use this as the current operational descriptor for Sri Lanka’s digital-account-opening workflow.

### Myanmar Market-Cap Benchmark (YSX)

Reference ID: `ASR-MMR-YSX-2026-02-11`

Primary source: [YSX daily market statistics PDF (February 11, 2026)](https://ysx-mm.com/wp-content/uploads/2026/02/20260211-1.pdf)

Verified benchmark value:

- Total market capitalisation: `794,378 million MMK`

Usage rule:

- Use the local-currency market-cap figure unless an explicitly dated FX conversion is required for a comparative chart.

---

## Source Priority

For time-sensitive external facts, use this priority order:

1. Official regulator / market-infrastructure website or PDF
2. This source register (if already refreshed from the official source)
3. Embedded references in active project documents
4. Secondary press or market-data references, clearly labeled as illustrative or provisional
