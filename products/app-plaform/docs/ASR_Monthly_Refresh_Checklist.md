# ASR Monthly Refresh Checklist

Version: 1.0.1
Status: Operating checklist for maintaining the Authoritative Source Register (`ASR`)
Last revised: March 10, 2026

---

## Purpose

This checklist defines the minimum recurring process for keeping [Authoritative_Source_Register.md](Authoritative_Source_Register.md) current and preventing stale counts or stale source IDs from drifting into the active Project Siddhanta documents.

Use this checklist whenever a monthly documentation refresh is performed.

---

## Refresh Cadence

- `Monthly`
  - Nepal live counts and regulator-baseline scan.
- `Quarterly`
  - Regional benchmark refresh for comparative rows in `deep-research-report (2).md`.
- `Event-driven`
  - Immediate refresh when NRB, SEBON, CDSC, NEPSE, or another cited authority publishes a material circular, guideline, or structural market-statistic change.

---

## Monthly Core Refresh (Nepal)

### 1. Refresh CDSC Live Metrics

Source:

- [CDSC homepage](https://cdsc.com.np/)

Update:

- BO demat accounts
- Registered MeroShare users
- Licensed DPs
- Registered C-ASBA banks
- RTA entries

Action rule:

- If any value changes, create a new dated `ASR-NEP-CDSC-YYYY-MM-DD` entry or replace the existing latest entry in the register with the new as-of date.
- Then update every active document that embeds the changed number.

Recommended grep:

```sh
rg -n "ASR-NEP-CDSC-|Demat Accounts|Registered Meroshare Users|RTA Entries" project-files/
```

### 2. Refresh SEBON Intermediary Counts

Source:

- [SEBON intermediaries page](https://www.sebon.gov.np/intermediaries)

Update:

- Stock brokers
- Stock dealers
- Merchant bankers
- Fund Manager and Depository category count
- Depository participants
- Credit rating agencies
- ASBA institutions
- Mutual funds
- Specialized investment fund managers

Action rule:

- If any value changes, create a new dated `ASR-NEP-SEBON-YYYY-MM-DD` entry or replace the current latest entry in the register with the new as-of date.
- Update any active tables or prose that embed the changed counts.
- If the SEBON category label changes, update the wording across the docs, not just the number.

Recommended grep:

```sh
rg -n "ASR-NEP-SEBON-|Stock Brokers|Merchant Bankers|Fund Manager and Depository|Specialized Investment Fund Managers" project-files/
```

### 3. Scan NRB for New or Changed AI / Cyber / eKYC Baselines

Primary sources:

- [NRB AI Guidelines PDF](https://www.nrb.org.np/contents/uploads/2025/12/AI-Guidelines.pdf)
- [NRB Cyber Resilience Guidelines 2023](https://www.nrb.org.np/contents/uploads/2023/08/Cyber-Resilience-Guidelines-2023.pdf)
- [NRB Payment Systems Oversight Report FY 2023/24](https://www.nrb.org.np/contents/uploads/2025/01/Payment-Oversight-Report-2023-24.pdf)

Check for:

- revised AI consent, oversight, reporting, or incident requirements
- new sector-CERT / incident-reporting obligations
- new eKYC readiness statements or production rollout notices

Action rule:

- If the baseline changed, add a new dated `ASR-*` entry and update:
  - [Authoritative_Source_Register.md](Authoritative_Source_Register.md)
  - [Legal_Claim_Citation_Appendix.md](Legal_Claim_Citation_Appendix.md)
  - any active docs using the affected `ASR-*` ID

### 4. Revalidate NEPSE Operational Assumptions

Primary source:

- [NEPSE official website](https://www.nepalstock.com/)

Check for:

- trading-session windows
- settlement-cycle changes
- circuit-breaker changes
- operating-note changes that affect deployment or workflow assumptions

Action rule:

- If an exact current notice is available, replace assumption-only wording with notice-backed wording and update `ASR-NEP-NEPSE-OPS-ASSUMPTION`.
- If not, keep assumption language and refresh the last-reviewed date in the source register only when needed.

---

## Quarterly Regional Benchmark Refresh

These rows are comparative only, but should not be allowed to become silently stale.

### India

Sources:

- [SEBI Bulletin / current statistics](https://www.sebi.gov.in/sebi_data/attachdocs/oct-2025/1761810646113.pdf)
- [WFE market statistics](https://focus.world-exchanges.org/issue/september-2025/market-statistics)

Refresh:

- demat accounts
- market capitalisation
- mutual fund AUM
- merchant bankers / DPs
- comparative market-cap chart values

### Bangladesh

Sources:

- [CDBL homepage](https://www.cdbl.com.bd/)
- exchange benchmark publication currently referenced in the source register

Refresh:

- BO accounts
- DPs
- any broker-base benchmark if a newer exchange source exists

### Sri Lanka

Sources:

- [SEC Sri Lanka digitalization page](https://sec.gov.lk/digitalization-and-the-securities-market/)
- [CSE mobile app / CDS e-Connect page](https://www.cse.lk/mobile-app)
- [WFE market statistics](https://focus.world-exchanges.org/issue/september-2025/market-statistics)

Refresh:

- onboarding workflow claims
- stated operational turnaround
- comparative market-cap benchmark

### Myanmar

Source:

- [YSX daily market statistics](https://ysx-mm.com/)

Refresh:

- local-currency market capitalization
- only compute a USD chart value if a same-date or explicitly documented FX basis is added

---

## Update Workflow

### 1. Update the source register first

Always update [Authoritative_Source_Register.md](Authoritative_Source_Register.md) before editing narrative docs.

### 2. Then update the legal claim appendix

If a legal or quasi-legal baseline changed, update [Legal_Claim_Citation_Appendix.md](Legal_Claim_Citation_Appendix.md) so evidence status and notes stay aligned.

### 3. Then update active docs that embed facts

Because the current docs embed selected numbers directly, changing the source register alone is not sufficient.

Run targeted search-and-review:

```sh
rg -n "ASR-NEP-CDSC-|ASR-NEP-SEBON-|ASR-NEP-NRB-|ASR-IND-|ASR-BGD-|ASR-LKA-|ASR-MMR-" project-files/
```

### 4. Keep the citation style stable

- Reuse the same `ASR-*` ID format.
- In prose, use `Ref: ASR-...`.
- In tables, prefer a `Reference ID` column.

### 5. Record the refresh date

After a refresh, update:

- `Last verified` in [Authoritative_Source_Register.md](Authoritative_Source_Register.md)
- `Last revised` in [Legal_Claim_Citation_Appendix.md](Legal_Claim_Citation_Appendix.md) if the legal appendix changed
- affected active docs if embedded numbers or wording changed materially

---

## Escalation Rules

Escalate to a manual review when any of the following occurs:

1. A cited regulator page disappears, moves, or no longer exposes the referenced data.
2. A legal baseline changes in a way that could invalidate current product assumptions.
3. A number changes but the category definition also changes (for example, SEBON renames or merges an intermediary class).
4. A source contradicts an existing `ASR-*` entry.

---

## Minimum Deliverables Per Refresh

1. Updated `ASR-*` entries where needed
2. Updated `Legal_Claim_Citation_Appendix.md` if any legal baseline moved
3. Updated embedded numbers in active docs where affected
4. A short refresh note in the commit or work log summarizing what changed
