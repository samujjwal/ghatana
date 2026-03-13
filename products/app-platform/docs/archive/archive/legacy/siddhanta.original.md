# **Project Siddhanta: An AI-Native Capital Markets Platform for Nepal**
**Strategic Vision, Tactical Blueprint & Technical Reference Document**

**Status:** Archived historical draft. Superseded by `siddhanta.md` and `Siddhanta_Platform_Specification.md`. Data points, timelines, and assumptions here should not be used without revalidation.

## **Executive Overview: The Nepalese Imperative**

This document outlines the strategy for **Project Siddhanta** (सिद्धान्त), an AI-native, all-in-one capital markets platform designed explicitly for Nepal. While building upon the global and South Asian analysis in the foundational report, this plan recognizes that Nepal is not a smaller version of India—it is a unique market with distinct regulatory fractures, infrastructure bottlenecks, and a digitally-savvy investor base constrained by legacy systems.

The core thesis is that Nepal’s market is at a critical juncture. As evidenced by the February 2026 regulatory standoff over dual ISINs and the freezing of promoter shares worth billions , the current system is struggling to scale. The opportunity is not to merely digitize existing broken processes, but to introduce an **AI-native platform that provides the "source of truth" and automated governance** that regulators and the market desperately need.

**The Strategic Wedge:** In Nepal, the entry point is not competing on trade execution speed, but on **regulatory clarity, automated compliance, and operational integrity.** The platform is positioned as a response to the "Nepali paradox"—a market with millions of demat accounts but still burdened by fragmented integrations, manual overrides, flagging code failures, and policy paralysis.

### **Why AI-Native is Essential for Nepal**

Nepal’s capital market infrastructure is wrestling with challenges that are perfectly suited for an AI-first approach. The failure of the "flagging code" system to prevent illegal trading of locked shares  is a foundational problem that rule-based systems cannot solve.
*   **From Flagging to Intelligence:** Instead of a static flag, a policy-driven ledger can enforce lock-in controls. Clear rule breaches can be handled via deterministic controls, while ambiguous cases are routed to human review with a tamper-evident audit trail for regulators like SEBON and CDSC.
*   **The "Dual ISIN" Dilemma:** The current debate—whether to use one ISIN or two—is a symptom of a deeper data integrity issue. A stronger data model can support a single-ISIN-with-attributes or dual-ISIN approach, depending on the final regulator decision.
*   **Automated Regulatory Reporting:** With SEBON and NRB issuing new directives on margin trading and KYC, the reporting burden on intermediaries will increase. An AI copilot can help draft and validate reports, but final submissions should remain subject to human approval.

## **1. The Nepalese Market Landscape: A Deep Dive**

The national conversation on AI in capital markets had clearly begun by January 2026, with public discussions involving NEPSE leadership on AI's regulatory role. This signaled a potentially receptive policy environment, but not blanket approval for high-autonomy AI in regulated market operations.

### **1.1. Market Infrastructure Scale (Legacy Snapshot; Revalidate)**

| Indicator | Value | Source / Date |
| :--- | :--- | :--- |
| **Demat Accounts** | ~6.57 million | CDSC, Mid-2025  |
| **Meroshare Users** | ~6.32 million | CDSC, Mid-2025  |
| **Listed Companies** | 270+ | SEBON, Mid-2025  |
| **Stock Brokers** | 90 | SEBON Directory  |
| **Market Capitalization** | ~Rs 4,657 billion (~$34 bn USD) | NRB, Mid-2025  |
| **Monthly Turnover (Jan 2026)** | 346 million shares | NEPSE via CEIC  |
| **Daily Turnover (Peak)** | Rs 16.35 billion (Jan 26, 2026) | NEPSE via Kathmandu Post  |

### **1.2. The Market Fracture: Opportunity in Disruption**

The ongoing regulatory dispute over dual ISINs is the single most important market signal for a new platform .
*   **The Problem:** The current system's inability to prevent trading of locked promoter shares has eroded trust. Retail investor groups report "illegal trading" and market manipulation due to the failure of the electronic flagging code . This has led to a radical proposal from CDSC to enforce dual ISINs for all sectors, which has, in turn, frozen the listing of new companies and locked up billions in promoter capital.
*   **The Platform Solution:** **Project Siddhanta** can offer an alternative: a **"Regulatory Clarity Layer."** By providing an immutable, event-sourced ledger of share ownership with built-in compliance rules, the platform can give SEBON and CDSC better real-time visibility. The goal is to support clearer controls under either a single-ISIN-with-attributes or dual-ISIN regime, depending on regulator decisions.

## **2. Regulatory Architecture for Nepal**

The regulatory landscape in Nepal is rapidly evolving. A platform must be built to adapt to these changes natively.

### **2.1. Primary Regulators & Mandates**

*   **Securities Board of Nepal (SEBON):** The primary regulator. Recent focus areas included the **Margin Trading Facility Directive-2082** and the ISIN deadlock.
*   **Nepal Rastra Bank (NRB):** The central bank. Its reach into capital markets is expanding via KYC (Know Your Customer) rules, AML/CFT (Anti-Money Laundering/Combating the Financing of Terrorism), and the **AI Guidelines (December 2025)** for banks and financial institutions.
*   **CDSC & NEPSE:** The market infrastructure institutions (MIIs). CDSC is pushing for technological changes (dual ISINs, API development) , while NEPSE is engaging in dialogues about AI's role in market regulation .

### **2.2. Key Regulatory Signals & Platform Implications**

| Regulatory Signal | Implication for Platform | AI-Native Capability |
| :--- | :--- | :--- |
| **NRB's Centralized KYC via National ID**  | The platform should treat National ID integration as strategic, but delivery depends on regulatory approvals and interface readiness. | **AI-assisted onboarding:** Use IDP to extract and verify document data, reducing manual effort once approved rails are available. |
| **NRB AI Guidelines (December 2025)**  | Requires transparency, fairness, and board oversight for AI used in regulated workflows. | **Model Governance & Explainability:** The platform's AI layer must include a full model registry, bias monitoring, bounded autonomy, and explainability reports for AI-assisted decisions. |
| **NRB Cyber Resilience Guidelines (2023) & cyber roadmap work (2026)**  | Requires strong cyber controls, resilience, MFA, encryption, and disciplined incident handling. | **Zero-Trust, Resilient Infrastructure:** The platform architecture can support zero-trust. AI-assisted observability should support detection and triage, while remediation and incident notification follow approved operational processes. |
| **SEBON Margin Trading Directive**  | Requires brokers to have a minimum paid-up capital of Rs 20 crore and defines eligible securities. | **AI-Powered Risk & Margin Engine:** The platform can automate margin calls, dynamically calculate eligibility based on the latest SEBON criteria, and provide regulators with a real-time dashboard of all margin lending activity. |
| **Dual ISIN / Single ISIN Debate**  | The platform's data model must be flexible enough to support either outcome without a core rewrite. | **Attribute-Based Control (ABC):** Instead of relying on a code flag, the platform enforces rules based on a rich set of attributes attached to each security (e.g., `lock_in_end_date`, `promoter_status`, `sector_regulator`). This is more robust than any ISIN-based system. |

## **3. An AI-Native Platform for Nepal: Technical Blueprint**

This section adapts the global architecture to Nepal's specific constraints and opportunities.

### **3.1. Core Architectural Shifts for Nepal**

1.  **Mobile-First, Offline-Capable:** With high mobile adoption across Nepal, the platform's client interfaces should be mobile-first. Given Nepal's internet reliability constraints, the architecture should support offline capabilities for field agents (e.g., for collecting physical KYC in rural areas) that sync upon reconnection.

2.  **National ID as the Root of Trust:** The platform's identity layer must be built around Nepal's National ID system. Every entity (investor, broker, issuer) should be anchored to a verified National ID, creating an unbroken chain of identity from onboarding through to settlement.

3.  **Polyglot Persistence with a Graph Core:** While the global architecture mentions a knowledge graph, in Nepal, it is central. A graph database (like Neo4j) is ideal for modeling the complex relationships mandated by Nepali regulators: tracing beneficial ownership, mapping promoter networks across companies, and instantly visualizing the web of related-party transactions that can lead to market manipulation.

### **3.2. Nepal-Specific AI Agents**

The global report outlines generic agents. Here are agents tailored for the Nepali market:

*   **The "Meroshare" Reconciliation Agent:** Meroshare is the primary portal for investors, but it operates as a separate system from many broker back-offices. An AI-assisted service can reconcile investor holdings with the broker's ledger using approved integrations or authorized exports, reducing manual reconciliation work.

*   **The Promoter Share Compliance Agent:** This control service would monitor all transactions involving shares tagged with `promoter:true`. It would compare the transaction timestamp against the `lock_in_end_date` attribute and any sector-specific regulatory approvals (e.g., from NRB for banks). Clear rule breaches can be blocked by deterministic controls, while exceptions are escalated for human review.

*   **The Hydropower Risk Agent:** Given the dominance of the hydropower sector in the market , this agent would ingest monsoon forecasts, reservoir levels, and tariff policy changes, and model their impact on the portfolio risk of major hydropower promoter-shareholders. It would provide a daily risk briefing to brokers and regulators.

## **4. The Plugin Ecosystem: Nepal Operator Packs**

Plugins are not just add-ons; they are the primary mechanism for localizing the platform. Each pack maps to a specific Nepali business function.

### **4.1. High-Value Plugin Categories for Nepal**

| Plugin Category | Target Users | Core Functionality | Regulatory Alignment |
| :--- | :--- | :--- | :--- |
| **eKYC & National ID Pack** | All Brokers, DPs | Connector for NRB-approved eKYC / National ID workflows, subject to access approval and interface availability. Assisted onboarding and biometric hooks where legally available. | NRB KYC and eKYC policy direction  |
| **Promoter Share Management Pack** | Issuers, CDSC, Brokers | Manages the entire lifecycle of promoter shares: lock-in periods, conversion requests, regulatory approval workflows. Provides a real-time dashboard to SEBON. | SEBON/ CDSC ISIN Directive  |
| **Margin Trading & Risk Pack** | Brokers, NEPSE | Implements SEBON's new margin trading rules. Includes automated margin calls, eligibility checking, and portfolio stress-testing for brokers. | SEBON Margin Trading Directive-2082  |
| **Regulatory Reporting Pack (SEBON/NRB)** | Brokers, DPs, Issuers | Auto-generates all periodic reports required by SEBON and NRB. Uses AI to validate data against source transactions. Format outputs in PDF, Excel, or machine-readable XML. | SEBON Periodic Reporting Rules |
| **Cyber Resilience & FinCERT Pack** | All Intermediaries | Monitors the platform environment for threats, orchestrates approved remediation workflows, and prepares audit evidence for regulator/CERT submissions if and when FinCERT-Nepal operating processes are formalized. | NRB Cyber Resilience Guidelines 2023, evolving cyber roadmap work  |

### **4.2. The Plugin Manifest: A Nepal Example**

The global report's plugin schema is excellent. A manifest for a Nepali plugin would look like this:

```json
{
  "name": "np.promoter-share-manager",
  "version": "1.0.0",
  "vendor": {
    "name": "Your Company",
    "compliance_contact": "compliance@yourcompany.com.np"
  },
  "capabilities": ["issuer.promoter_share_mgmt", "reg_reporting.sebon"],
  "interfaces": {
    "api": { "openapi": "./promoter-api.yaml" },
    "data_contracts": [
      {
        "name": "Promoter Record",
        "schema": "./schemas/promoter.avsc",
        "classification": "restricted",
        "regulatory_class": "SEBON-CRITICAL"
      }
    ]
  },
  "security": {
    "permissions": ["read.share_ledger", "write.lock_status"],
    "needs_nrb_approval": true,
    "audit": {
      "log_level": "verbose",
      "retention_period_days": 3650
    }
  },
  "ai_governance": {
    "models_used": [
      {
        "name": "lock-violation-detector",
        "purpose": "Detect attempted trading of locked promoter shares",
        "oversight": "human_in_the_loop",
        "training_data_description": "Historical trade data and lock-in schedules from CDSC (anonymized)"
      }
    ],
    "explainability_method": "rule_extraction"
  },
  "compatibility": {
    "platform_min_version": "2.0.0",
    "required_nepal_integrations": ["cdsc_api", "national_id_gateway"]
  }
}
```

## **5. Implementation Roadmap for Nepal**

Given the immediate market crisis and regulatory openings, the timeline can be accelerated compared to the global report.

### **Phase 1: The "Regulatory Clarity" MVP (0-9 Months)**
*   **Goal:** Solve the immediate ISIN/promoter share crisis.
*   **Core Team:** 20-30 FTEs (including 5-7 AI/ML engineers).
*   **Deliverables:**
    1.  Core Event-Sourced Ledger.
    2.  **Promoter Share Management Pack:** A standalone, sellable module that CDSC or a large issuer can use to manage locked shares with immutable auditability.
    3.  National ID integration for operator authentication.
    4.  A basic **"Regulator Dashboard"** for SEBON, showing a real-time, trustworthy view of promoter shareholdings and lock-in status for a pilot group of companies.
*   **Cost Estimate:** $2M - $6M USD.

### **Phase 2: Broker & DP Modernization (9-18 Months)**
*   **Goal:** Onboard the first tier of brokers and DPs.
*   **Team:** 40-60 FTEs.
*   **Deliverables:**
    1.  Brokerage/DP Operator Pack (OMS, risk, client ledger).
    2.  **AI-Powered Reconciliation Agent** (auto-matching with CDSC and Meroshare).
    3.  **eKYC & Onboarding Pack** (powered by National ID).
    4.  Plugin Marketplace v1 (hosting the above packs).
*   **Key Metric:** Target STP rate for clearly defined straight-through broker workflows >95%.
*   **Cost Estimate:** $6M - $15M USD.

### **Phase 3: Market-Wide Utility & Autonomous Ops (18-36 Months)**
*   **Goal:** Become the default operating system for Nepal's capital markets.
*   **Team:** 60-100+ FTEs.
*   **Deliverables:**
    1.  Full **AI Agent Suite** (Hydropower Risk Agent, Margin Call Agent, AML Agent).
    2.  NEPSE and CDSC core system connectors (for settlement and trading).
    3.  **Autonomous Operations:** The platform begins auto-resolving a significant percentage of operational exceptions.
    4.  Integration with **FinCERT-Nepal** for automated threat intelligence sharing .
*   **Cost Estimate:** $15M - $35M USD.

## **6. Risk Assessment & Mitigation (Nepal-Specific)**

| Risk | Impact | Mitigation Strategy |
| :--- | :--- | :--- |
| **Regulatory Policy Reversal** (e.g., SEBON mandates dual ISINs against our single-source-of-truth model) | **High:** Could require major rework. | **Architectural Abstraction:** The core ledger is attribute-based, not ISIN-based. Adapting to a dual-ISIN world should be a bounded data-model and integration change rather than a full platform rewrite. We will work *with* CDSC, not against them. |
| **Political Instability & Policy Paralysis** (as seen in the ISIN deadlock)  | **Medium:** Procurement and deployment timelines for government entities (CDSC, NEPSE) could be severely delayed. | **Focus on Private Sector:** Initial focus should be on private brokers, DPs, and issuers who are directly feeling the pain of the current system. Government sales can follow once the platform has proven market traction. |
| **Talent Scarcity** (AI/ML and capital markets domain expertise)  | **Medium:** The local talent pool for AI-native fintech is nascent. | **Hybrid Team Model:** Build a core Nepali team for domain expertise, regulatory navigation, and support. Augment with experienced international AI/ML and platform architects for the first 18-24 months, with a formal knowledge transfer plan. |
| **Data Sovereignty & Localization** (NRB cloud feasibility study, new Data Center Directive)  | **High:** Customer data residency is a core compliance constraint. | **Cloud-Native, In-Country by Default:** The Kubernetes-based architecture is portable. Primary regulated data should remain in Nepal unless an explicit regulator-approved exception is granted for narrowly scoped disaster recovery or tooling. |

## **Conclusion: The Siddhanta Mandate**

The chaos of the current market is the clearest signal of opportunity. The dual-ISIN dispute is not just a technical argument; it is a cry for a new kind of infrastructure—one that is transparent, intelligent, and trustworthy by design.

**Project Siddhanta** is not just building software for Nepal. It is offering a path out of the current regulatory and operational maze. By embedding the rules into the architecture, automating compliance, and providing an immutable source of truth, we can help Nepal leapfrog the growing pains of other markets and build a capital markets ecosystem that is truly fit for the 21st century. The first step is not a grand launch, but a targeted intervention to solve the promoter share crisis, proving that technology can do what flagging codes and policy debates cannot.
