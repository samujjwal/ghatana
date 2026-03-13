# Customer-Facing Claims Pack

Version: 1.0.1  
Status: External-safe summary of source-backed and clause-backed claims for proposals, demos, and customer discussions  
Last revised: March 10, 2026

Shared authoritative source register: [Authoritative_Source_Register.md](Authoritative_Source_Register.md)  
Legal-claim traceability: [Legal_Claim_Citation_Appendix.md](Legal_Claim_Citation_Appendix.md)  
Compact traceability matrix: [Claim_Traceability_Matrix.md](Claim_Traceability_Matrix.md)
Sales-ready summary: [One_Page_Proposal_Insert.md](One_Page_Proposal_Insert.md)
Buyer-specific inserts: [Broker_Proposal_Insert.md](Broker_Proposal_Insert.md), [Merchant_Banker_Proposal_Insert.md](Merchant_Banker_Proposal_Insert.md), [Issuer_Proposal_Insert.md](Issuer_Proposal_Insert.md)
Indexed bundle: [sales-kit/README.md](sales-kit/README.md)
Routing note: [sales-kit/cover-note.md](sales-kit/cover-note.md)

---

## Purpose

This file is the customer-safe subset of Project Siddhanta claims.

Use it when preparing:

- proposals,
- solution briefs,
- demo scripts,
- investor/customer Q&A,
- regulator-adjacent summary decks.

Only statements in this file, or separately revalidated primary-source claims, should be used externally as factual or legal anchors.

---

## Use Rules

1. Treat `ASR-*` rows as date-sensitive facts. Keep the stated as-of date when reusing them.
2. Treat `LCA-*` rows here as the numeric legal-claim identifiers maintained in the legal appendix and claim matrix, not as the semantic epic/control IDs from the compliance-code registry.
3. Do not upgrade `document-verified` or `project-policy` items into hard legal mandates.
4. Revalidate live filing templates, issue-specific reporting formats, and operator-specific circulars before promising an exact filing layout.
5. If a claim is not listed here, do not use it externally without adding a source or legal trace first.

---

## 1. Live Nepal Market Facts

| Safe external claim                                                                                                                                      | Evidence level  | Reference ID               | Primary source                                                       | As of         |
| :------------------------------------------------------------------------------------------------------------------------------------------------------- | :-------------- | :------------------------- | :------------------------------------------------------------------- | :------------ |
| Nepal has `7,511,020` BO demat accounts and `6,586,798` registered MeroShare users.                                                                      | `source-backed` | `ASR-NEP-CDSC-2026-03-01`  | [CDSC homepage](https://cdsc.com.np/)                                | March 1, 2026 |
| CDSC currently shows `122` licensed depository participants, `43` registered C-ASBA banks, and `46` RTA entries.                                         | `source-backed` | `ASR-NEP-CDSC-2026-03-01`  | [CDSC homepage](https://cdsc.com.np/)                                | March 1, 2026 |
| SEBON currently lists `90` stock brokers, `2` stock dealers, and `32` merchant bankers.                                                                  | `source-backed` | `ASR-NEP-SEBON-2026-03-01` | [SEBON intermediaries page](https://www.sebon.gov.np/intermediaries) | March 1, 2026 |
| SEBON currently lists `24` mutual funds and `19` specialized investment fund managers.                                                                   | `source-backed` | `ASR-NEP-SEBON-2026-03-01` | [SEBON intermediaries page](https://www.sebon.gov.np/intermediaries) | March 1, 2026 |
| The SEBON category labeled `Fund Manager and Depository` is currently `17`; do not restate that as `17 standalone fund managers` without separate proof. | `source-backed` | `ASR-NEP-SEBON-2026-03-01` | [SEBON intermediaries page](https://www.sebon.gov.np/intermediaries) | March 1, 2026 |

---

## 2. Document-Backed Regulatory Baselines

| Safe external claim                                                                                                                                                                  | Evidence level      | Reference ID                            | Primary source                                                                                                                          | Notes                                                                           |
| :----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :------------------ | :-------------------------------------- | :-------------------------------------------------------------------------------------------------------------------------------------- | :------------------------------------------------------------------------------ |
| NRB's AI Guidelines used in this project are dated December 2025.                                                                                                                    | `source-backed`     | `ASR-NEP-NRB-AI-2025-12`                | [NRB AI Guidelines PDF](https://www.nrb.org.np/contents/uploads/2025/12/AI-Guidelines.pdf)                                              | Do not describe this as "AI Guidelines 2024."                                   |
| National ID / centralized eKYC in Nepal should be described as approval-dependent and readiness-dependent, not as a universally live production rail.                                | `document-verified` | `LCA-003` / `ASR-NEP-NRB-EKYC-2025-01`  | [NRB Payment Systems Oversight Report FY 2023/24](https://www.nrb.org.np/contents/uploads/2025/01/Payment-Oversight-Report-2023-24.pdf) | Safe phrasing is "approval-dependent eKYC."                                     |
| NRB cyber guidance supports strong cyber controls and incident workflows, but this documentation does not claim a fixed `24-hour` FinCERT deadline from the currently pinned source. | `document-verified` | `LCA-004` / `ASR-NEP-NRB-CYBER-2023-08` | [NRB Cyber Resilience Guidelines 2023](https://www.nrb.org.np/contents/uploads/2023/08/Cyber-Resilience-Guidelines-2023.pdf)            | Do not promise a hard sector-CERT notification clock unless separately sourced. |

---

## 3. Clause-Backed Compliance Anchors

| Safe external claim                                                                                                                                                                                                                                                | Evidence level  | Reference ID                      | Primary source                                                                                                                                                                                  |
| :----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :-------------- | :-------------------------------- | :---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| AI-enabled customer-facing use of data requires explicit customer consent and opt-out handling.                                                                                                                                                                    | `clause-backed` | `LCA-001`                         | [NRB AI Guidelines PDF](https://www.nrb.org.np/contents/uploads/2025/12/AI-Guidelines.pdf)                                                                                                      |
| AI governance for regulated workflows requires board-approved oversight, risk classification, audit trails, and incident reporting.                                                                                                                                | `clause-backed` | `LCA-002`                         | [NRB AI Guidelines PDF](https://www.nrb.org.np/contents/uploads/2025/12/AI-Guidelines.pdf)                                                                                                      |
| High-impact regulated actions should not be described as fully autonomous; human oversight remains part of the control model.                                                                                                                                      | `clause-backed` | `LCA-015`                         | [NRB AI Guidelines PDF](https://www.nrb.org.np/contents/uploads/2025/12/AI-Guidelines.pdf)                                                                                                      |
| Merchant bankers are legally allowed to perform issue management, underwriting, and share registration / registrar functions.                                                                                                                                      | `clause-backed` | `LCA-005` / `LCA-006`             | [Securities Businessperson (Merchant Banker) Regulations, 2008](https://www.sebon.gov.np/uploads/uploads/fxSbSu2xUILb2u3skrb6tF9YmkL8SDIpynMpFxzS.pdf)                                          |
| Mutual-fund operations require a board-approved fund-manager / depository structure, and fund managers are role-constrained unless the Board approves otherwise.                                                                                                   | `clause-backed` | `LCA-007` / `LCA-008` / `LCA-009` | [Mutual Fund Regulations, 2067 (2010 A.D.)](https://www.sebon.gov.np/uploads/uploads/5k3Gq6Rin0wM9MD3BuhiYk2SqSP5sIDVLiCCxect.pdf)                                                              |
| Broker onboarding must maintain client records and capture pre-account-opening risk disclosure acknowledgement.                                                                                                                                                    | `clause-backed` | `LCA-010`                         | [Securities Businesspersons (Stockbroker, Dealer & Market Maker) Regulations, 2074 (with amendments)](https://www.sebon.gov.np/uploads/shares/4F7r6bPWJVCt9nyk4E5cyzL6uk6tPS3p4sYs14E1.pdf)     |
| Margin-service providers must meet the Rule 4 eligibility threshold, with a minimum `30%` initial margin and `20%` maintenance margin.                                                                                                                             | `clause-backed` | `LCA-011`                         | [Margin Transaction Related Guideline, 2082](https://www.sebon.gov.np/uploads/shares/yb8vXWjXg3rmDIiW2nwQ5fpxrQx2dkueLT4M2zTL.pdf)                                                              |
| Trading members must use a separate NEPSE-designated clearing bank account, cannot cross-use customer advances, must provide prompt trade-completion information, and must retain complete trading / clearing / settlement records for five years.                 | `clause-backed` | `LCA-012`                         | [Securities Trading Regulations, 2075 (Second Amendment)](https://www.sebon.gov.np/uploads/acts/WEeLw4Ld7uZPPFOPn4tb7WWQv7qkDxwXzNliGHQu.pdf)                                                   |
| General broker / dealer / market-maker categories have distinct minimum paid-up-capital thresholds, and full-service brokers can perform additional regulated functions with Board approval.                                                                       | `clause-backed` | `LCA-019` / `LCA-020`             | [Securities Businesspersons (Stockbroker, Dealer & Market Maker) Regulations, 2074 (with amendments)](https://www.sebon.gov.np/uploads/shares/4F7r6bPWJVCt9nyk4E5cyzL6uk6tPS3p4sYs14E1.pdf)     |
| A licensed securities broker must provide the contract note to the client on the day following the transaction.                                                                                                                                                    | `clause-backed` | `LCA-021`                         | [SEBON Acts Page (English Securities Act download)](https://www.sebon.gov.np/acts)                                                                                                              |
| The current brokerage schedule is `0.40%`, `0.37%`, `0.34%`, `0.30%`, and `0.27%` by slab, with a `Rs 10` minimum; the Board levy is `0.6%` of total brokerage service charge; the securities-market transaction fee is `0.015%` of each share transaction amount. | `clause-backed` | `LCA-023`                         | [Securities Businesspersons (Stockbroker, Dealer & Market Maker) Regulations, 2074 (with amendments)](https://www.sebon.gov.np/uploads/2022/09/13/UxvD7NtfU7rxZnXxU4po9z8cXUVdhg67gIkMwRO9.pdf) |
| Brokers, dealers, and market makers must file the Board-specified quarterly return within `30 days` after quarter close.                                                                                                                                           | `clause-backed` | `LCA-024`                         | [Securities and Commodities Exchange Market Related Laws: Special Publication 2019](https://www.sebon.gov.np/uploads/uploads/VxEhnG33sobzXraP4EsxgV06juNWEupcTacEHCpU.pdf)                      |
| Draft prospectus approval requires a defined submission pack, including an issuance/sales-manager due diligence certificate, and the Board approves the prospectus in the prescribed schedule format.                                                              | `clause-backed` | `LCA-025`                         | [Securities and Commodities Exchange Market Related Laws: Special Publication 2019](https://www.sebon.gov.np/uploads/uploads/VxEhnG33sobzXraP4EsxgV06juNWEupcTacEHCpU.pdf)                      |
| After completing a public issue, sale, distribution, or allotment, the issuer must inform the Board within `7 days` with the prescribed issue-close particulars.                                                                                                   | `clause-backed` | `LCA-026`                         | [Securities and Commodities Exchange Market Related Laws: Special Publication 2019](https://www.sebon.gov.np/uploads/uploads/VxEhnG33sobzXraP4EsxgV06juNWEupcTacEHCpU.pdf)                      |
| Listed issuers must provide book-closure / record-date notices, meeting packs, progress reports, and shareholder-record disclosures on defined timelines under the listing agreement.                                                                              | `clause-backed` | `LCA-027`                         | [Securities and Commodities Exchange Market Related Laws: Special Publication 2019](https://www.sebon.gov.np/uploads/uploads/VxEhnG33sobzXraP4EsxgV06juNWEupcTacEHCpU.pdf)                      |
| Failure to provide price-sensitive information or annual / semiannual reports on time can trigger listing suspension.                                                                                                                                              | `clause-backed` | `LCA-028`                         | [Securities and Commodities Exchange Market Related Laws: Special Publication 2019](https://www.sebon.gov.np/uploads/uploads/VxEhnG33sobzXraP4EsxgV06juNWEupcTacEHCpU.pdf)                      |

---

## 4. Claims Explicitly Excluded From External Hard-Sell Use

These items are intentionally not safe to market as hard legal or factual guarantees unless they are separately revalidated:

- `LCA-013`: universal 5-year retention across every entity and workflow (platform baseline, not a single clause-backed universal law)
- `LCA-014`: in-country data residency as a universal regulator-wide legal mandate (platform policy, not a single clause-backed law)
- `LCA-016`: schema-per-tenant segregation as a legally prescribed implementation pattern (architecture policy, not a clause)
- `LCA-018`: Nepali-first outputs as a universal legal mandate (product localization policy)
- `LCA-022`: explicit daily-reconciliation legal clock (still pending clause extraction)
- exact live allotment-report templates and issue-close filing forms (must be revalidated per issue and current approval conditions)
- illustrative TAM/SAM/SOM ranges, comparative benchmarks, and non-primary-source market-size estimates unless separately labeled as estimates
- named lists of currently active prospectus-stage issuers unless refreshed from a current primary source

---

## 5. Suggested External Positioning Language

Use concise wording like:

- "As of March 1, 2026, Nepal's depository infrastructure shows 7.51 million demat accounts and 122 licensed DPs (`Ref: ASR-NEP-CDSC-2026-03-01`)."
- "The platform is designed around clause-backed broker controls such as segregated client-funds handling, next-day contract notes, and Board-timed quarterly returns (`Ref: LCA-012`, `Ref: LCA-021`, `Ref: LCA-024`)."
- "For Nepal primary-market workflows, the current filing baseline includes a prescribed prospectus structure and an issuance-manager due diligence certificate (`Ref: LCA-025`)."
- "For Nepal primary-market workflows, the current filing baseline includes a prescribed prospectus structure, an issuance-manager due diligence certificate, and a 7-day post-issue close reporting clock (`Ref: LCA-025`, `Ref: LCA-026`)."
- "For listed issuers, disclosure timeliness matters because book-closure, meeting, and price-sensitive notices sit on defined timelines and late reporting can trigger suspension risk (`Ref: LCA-027`, `Ref: LCA-028`)."
- "High-impact AI decisions are governed with explicit consent, auditability, and human oversight rather than full autonomy (`Ref: LCA-001`, `Ref: LCA-002`, `Ref: LCA-015`)."

Avoid wording like:

- "required by regulator" unless the exact claim is backed by a `clause-backed` row above,
- "all data must stay in Nepal by law" unless a clause-level source is added,
- "daily reconciliation is explicitly mandated by SEBON" until `LCA-022` is closed,
- "these templates are fixed" for issue filings or quarterly returns without current template revalidation.
