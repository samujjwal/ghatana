# Compliance Code Registry

**Generated:** March 10, 2026  
**Purpose:** Centralized registry of all compliance and regulatory codes referenced across epics

---

## Overview

This registry documents all compliance codes (LCA-_) and authoritative source register codes (ASR-_) referenced throughout the epic specifications. These codes ensure traceability to regulatory requirements and internal compliance policies.

**Namespace Note:** This registry governs the semantic epic/control identifiers used in the implementation-ready architecture set, such as `LCA-AUDIT-001`, `LCA-SOD-001`, and `ASR-SEC-001`.

It does **not** replace the numeric legal-claim identifiers maintained in [docs/Legal_Claim_Citation_Appendix.md](/home/samujjwal/Developments/finance/docs/Legal_Claim_Citation_Appendix.md), such as `LCA-001` through `LCA-032`. Those numeric IDs are a separate legal-claim traceability namespace for source-backed factual and clause-level assertions in the broader documentation set.

---

## LCA Codes (Local Compliance & Audit)

### LCA-AUDIT-001: Comprehensive Audit Logging

- **Category:** Audit & Traceability
- **Description:** All system actions, state changes, and user activities must be logged immutably with actor identity, timestamp (dual-calendar), and action details.
- **Requirements:**
  - Immutable audit trail (append-only)
  - Dual-calendar timestamps (Gregorian + BS)
  - Actor identification (user_id, service_id)
  - Action details (before/after state)
  - Retention: Minimum 10 years
- **Referenced By:** K-01, K-02, K-03, K-05, K-06, K-07, K-08, K-09, K-10, K-11, K-13, K-14, K-15, K-16, D-01, D-07, D-08, D-09, D-10, D-12, W-01, W-02, O-01, P-01, R-01, PU-004
- **Regulatory Basis:** SEBON Directives, NRB Guidelines, SOX, GDPR Article 30

### LCA-SOD-001: Segregation of Duties (Maker-Checker)

- **Category:** Access Control
- **Description:** Critical operations require dual approval (maker-checker) to prevent fraud and ensure oversight.
- **Requirements:**
  - Two distinct authorized users for approval
  - Same user cannot be both maker and checker
  - All approvals logged to audit trail
  - Timeout/escalation policies defined
- **Referenced By:** K-01, K-03, K-14, K-15, D-01, D-07, D-12, W-02, R-01
- **Regulatory Basis:** SEBON Market Conduct Rules, NRB Internal Control Guidelines

### LCA-RET-001: Data Retention Policy

- **Category:** Data Governance
- **Description:** Minimum retention periods for different data types to meet regulatory requirements.
- **Requirements:**
  - Trade records: 10 years minimum
  - Audit logs: 10 years minimum
  - Client records: 10 years minimum (approved), 2 years minimum (rejected)
  - Corporate action records: 10 years minimum
  - Compliance records: 10 years minimum
  - Operational logs: 1-2 years
- **Referenced By:** K-01, K-07, K-08, D-01, D-10, D-12, W-02, R-01
- **Regulatory Basis:** SEBON Record Keeping Requirements, NRB Directives

### LCA-AMLKYC-001: AML/KYC Compliance

- **Category:** Compliance
- **Description:** Anti-Money Laundering and Know Your Customer requirements for client onboarding and monitoring.
- **Requirements:**
  - Identity verification (National ID, biometrics)
  - Sanctions screening (OFAC, UN, EU lists)
  - PEP (Politically Exposed Person) checks
  - Ongoing monitoring and suspicious activity reporting
  - Enhanced due diligence for high-risk clients
- **Referenced By:** K-01, D-07, W-02
- **Regulatory Basis:** SEBON AML Directives, NRB KYC Guidelines, FATF Recommendations

### LCA-TAX-001: Tax Withholding Compliance

- **Category:** Tax
- **Description:** Accurate calculation and withholding of taxes on dividends, capital gains, and other income.
- **Requirements:**
  - Jurisdiction-specific tax rates (via T2 Rule Packs)
  - TDS (Tax Deducted at Source) calculation
  - Tax reporting to authorities
  - Certificate generation for taxpayers
- **Referenced By:** D-12
- **Regulatory Basis:** Nepal Income Tax Act, TDS Rules

### LCA-COMP-001: Pre-Trade Compliance Checks

- **Category:** Compliance
- **Description:** Mandatory compliance checks before order execution to prevent prohibited trades.
- **Requirements:**
  - Insider trading checks
  - Position limit verification
  - Restricted security checks
  - Circuit breaker compliance
  - Market manipulation detection
- **Referenced By:** K-03, D-01, D-07
- **Regulatory Basis:** SEBON Market Conduct Rules, Insider Trading Regulations

### LCA-BESTEX-001: Best Execution

- **Category:** Trading
- **Description:** Obligation to achieve best execution for client orders.
- **Requirements:**
  - Execution quality monitoring
  - Venue selection justification
  - Transaction cost analysis (TCA)
  - Slippage tracking
  - Periodic best execution reports
- **Referenced By:** D-01, D-02
- **Regulatory Basis:** SEBON Best Execution Guidelines, MiFID II (reference)

### LCA-SUIT-001: Client Suitability and Appropriateness

- **Category:** Client Protection
- **Description:** Products, services, and trading permissions must align with the client's assessed risk profile, experience, and suitability constraints.
- **Requirements:**
  - Suitability assessments tied to current client profile and risk tier
  - Product or workflow restrictions enforced when suitability conditions are not met
  - Overrides or exceptions require governed review and audit evidence
  - Suitability decisions retained for regulator and audit inspection
- **Referenced By:** Regulatory Architecture Document
- **Regulatory Basis:** Investor-protection expectations, suitability and appropriateness controls for broker-dealer and advisory workflows

### LCA-DUP-001: Duplicate Submission Prevention

- **Category:** Transaction Integrity
- **Description:** Order and workflow submissions must be protected against duplicate creation, replay, or accidental resubmission.
- **Requirements:**
  - Unique client or workflow identifiers enforced for idempotent processing
  - Duplicate submissions rejected or reconciled without creating inconsistent state
  - Detection and rejection events retained as operational evidence
  - Replay protections documented for externally submitted instructions
- **Referenced By:** Regulatory Architecture Document
- **Regulatory Basis:** Trade-integrity expectations, operational control requirements, auditability of order intake and replay handling

### LCA-CA-001: Corporate Action Entitlement Integrity

- **Category:** Corporate Actions
- **Description:** Corporate-action processing must preserve the correctness and traceability of entitlement calculations, record-date eligibility, and downstream postings.
- **Requirements:**
  - Entitlement calculations tied to authoritative issuer events and eligible holdings
  - Record-date and ex-date determinations preserved with audit evidence
  - Exceptions, manual adjustments, and reruns retained with justification
  - Downstream ledger and notification effects traceable to the originating action
- **Referenced By:** Regulatory Architecture Document
- **Regulatory Basis:** Issuer-action processing controls, investor-protection obligations, audit evidence requirements for entitlement correctness

### LCA-011: Margin Trading Eligibility and Thresholds

- **Category:** Margin Trading
- **Description:** Margin trading workflows must enforce jurisdiction-approved provider eligibility, initial margin, maintenance margin, and liquidation rights.
- **Requirements:**
  - Margin-service eligibility checks aligned to the governing margin directive
  - Minimum initial margin thresholds enforced via configuration and rules
  - Minimum maintenance margin thresholds enforced for ongoing exposure monitoring
  - Margin call and liquidation workflows traceable to the governing legal basis
- **Referenced By:** D-06
- **Regulatory Basis:** SEBON Margin Transaction Related Guideline 2082

### LCA-AI-001: Governed AI Decision Traceability

- **Category:** AI Governance
- **Description:** AI-assisted decisions and model operations must remain explainable, reviewable, and auditable under human oversight.
- **Requirements:**
  - Explainability artifacts stored for every governed AI decision
  - Human override path for material AI-assisted outcomes
  - Model and prompt version traceability retained with decisions
  - Drift and rollback actions logged to audit trail
  - Regulatory review evidence exportable on demand
- **Referenced By:** K-09
- **Regulatory Basis:** NRB AI Guidelines, SEBON supervisory expectations for automated decisioning, emerging AI governance controls

### LCA-SEG-001: Client Asset Segregation

- **Category:** Asset Protection
- **Description:** Client assets and house assets must remain segregated with provable control boundaries and reconciliation support.
- **Requirements:**
  - Segregated account structures for client and firm assets
  - Reconciliation evidence demonstrating segregation integrity
  - Controls preventing commingling without explicit governed workflow
  - Exception escalation and remediation audit trail
- **Referenced By:** K-16, D-09
- **Regulatory Basis:** SEBON client asset protection rules, custodian segregation controls, investor protection obligations

### LCA-SETTL-001: Settlement Finality Control

- **Category:** Settlement Integrity
- **Description:** Settlement workflows must provide deterministic finality, traceability of compensations, and operational evidence for dispute resolution.
- **Requirements:**
  - Final settlement state recorded immutably
  - Cross-step compensation trails preserved for failed settlement flows
  - Replay or retry paths controlled and auditable
  - Finality exceptions escalated for manual resolution
- **Referenced By:** K-17
- **Regulatory Basis:** SEBON settlement process controls, clearing and settlement risk management expectations

### LCA-OPS-001: Operational Resilience Evidence

- **Category:** Operations
- **Description:** Critical operational controls must produce evidence of resilience testing, degraded-mode handling, and break/failure resolution.
- **Requirements:**
  - Resilience and recovery exercises recorded for audit
  - Degraded-mode decisions logged with rationale and impact
  - Operational incidents, replay actions, and service-restoration paths retained
  - Escalation and closure evidence available for supervisory review
- **Referenced By:** K-18, K-19, D-13, T-02
- **Regulatory Basis:** SEBON operational resilience expectations, business continuity testing practices, supervisory evidence requirements

### LCA-DATA-001: Data Integrity Assurance

- **Category:** Data Governance
- **Description:** Data processing flows must preserve completeness, correctness, and recoverability across failures, replay, and quarantine workflows.
- **Requirements:**
  - Integrity checks for replayed, quarantined, or recovered records
  - Chain-of-custody evidence for corrected or discarded data events
  - Detection and escalation of corrupted or incomplete operational data
  - Idempotent recovery procedures for failed processing paths
- **Referenced By:** K-19
- **Regulatory Basis:** SEBON data quality expectations, audit evidence requirements for corrected operational records

### LCA-RECON-001: Financial Reconciliation Control

- **Category:** Reconciliation
- **Description:** Operational and regulatory reconciliation workflows must evidence matching completeness, break handling, and supervisory review for unresolved discrepancies.
- **Requirements:**
  - Reconciliation runs produce matched/unmatched evidence with aging visibility
  - Break classifications, escalations, and resolutions remain auditable
  - Historical reruns preserve point-in-time inputs and outputs
  - Reconciliation results are available for regulator and audit review
- **Referenced By:** D-13
- **Regulatory Basis:** SEBON reconciliation expectations, broker-dealer books and records obligations, operational control evidence requirements

### LCA-SANCTIONS-001: Sanctions Screening Compliance

- **Category:** Sanctions & AML
- **Description:** Screening workflows must prevent sanctioned counterparties or beneficiaries from transacting without governed review and evidence retention.
- **Requirements:**
  - Sanctions screening at defined critical workflow touchpoints
  - Potential and confirmed matches held for governed review and resolution
  - Screening-list versioning, freshness, and match-decision evidence retained
  - Escalation and blocking outcomes auditable for supervisory inspection
- **Referenced By:** D-14
- **Regulatory Basis:** OFAC, UN, and EU sanctions screening obligations as adopted through SEBON/NRB AML control expectations

### LCA-INCIDENT-001: Incident Disclosure and Escalation Control

- **Category:** Incident Governance
- **Description:** Material operational and security incidents must be classified, escalated, and disclosed within governed timelines with preserved evidence of notifications and acknowledgments.
- **Requirements:**
  - Severity-based disclosure timelines and escalation paths defined and enforced
  - Notification content rendered from approved templates and tracked to acknowledgment
  - Incident lifecycle changes, recipients, and delivery outcomes retained as audit evidence
  - Post-incident remediation and reporting obligations traceable to the originating incident
- **Referenced By:** R-02
- **Regulatory Basis:** SEBON operational incident reporting expectations, supervisory escalation obligations, internal governance requirements for material incidents

### LCA-BREACH-001: Breach Notification Control

- **Category:** Privacy & Security
- **Description:** Personal-data or security-breach notifications must be governed, evidence-backed, and time-bound according to applicable regulatory obligations.
- **Requirements:**
  - Breach-impact assessments retained with notification decisions
  - Affected-party notifications require governed approval where mandated
  - Notification timestamps, recipients, and message versions preserved for audit
  - Cross-jurisdiction breach timelines configurable and enforceable
- **Referenced By:** R-02
- **Regulatory Basis:** GDPR breach-notification principles, emerging Nepal privacy expectations, supervisory disclosure standards for affected-party communication

### LCA-DR-001: Disaster Recovery Evidence

- **Category:** Resilience & Recovery
- **Description:** Disaster-recovery exercises and failover validations must produce auditable evidence of recovery capability, timing, and control effectiveness.
- **Requirements:**
  - DR drills record actual RTO/RPO performance and outcome status
  - Recovery steps, validation evidence, and exceptions retained for audit review
  - Failed drills or unmet recovery objectives escalated for remediation tracking
  - Historical drill results support trend analysis and supervisory inspection
- **Referenced By:** T-02
- **Regulatory Basis:** SEBON business continuity expectations, ISO 22301 recovery testing practices, supervisory review of DR capability

---

## ASR Codes (Authoritative Source Register)

### ASR-OPS-001: Operational Resilience

- **Category:** Operations
- **Description:** Platform must maintain operational resilience with defined RTO/RPO targets.
- **Requirements:**
  - Recovery Time Objective (RTO): < 4 hours
  - Recovery Point Objective (RPO): < 15 minutes
  - Disaster recovery procedures tested quarterly
  - Business continuity plan maintained
  - Incident response workflows documented
- **Referenced By:** O-01, W-01
- **Regulatory Basis:** SEBON Business Continuity Guidelines, ISO 22301

### ASR-SEC-001: Security Controls

- **Category:** Security
- **Description:** Comprehensive security controls for platform protection.
- **Requirements:**
  - Encryption in transit (TLS 1.3+) and at rest
  - Multi-factor authentication for critical access
  - Regular security assessments and penetration testing
  - Vulnerability management program
  - Security incident response plan
  - Third-party code governance
- **Referenced By:** K-01, K-08, K-11, K-12, K-14, P-01, T-01
- **Regulatory Basis:** SEBON Cybersecurity Guidelines, ISO 27001, NIST Cybersecurity Framework

### ASR-DATA-001: Data Residency & Sovereignty

- **Category:** Data Governance
- **Description:** Data must be stored and processed according to jurisdiction-specific residency requirements.
- **Requirements:**
  - Nepal data stored in Nepal (or approved jurisdictions)
  - Cross-border data transfer controls
  - Data localization compliance
  - Jurisdiction-aware data routing
- **Referenced By:** K-08, R-01
- **Regulatory Basis:** Nepal Data Protection Act (proposed), SEBON Data Guidelines

### ASR-CB-001: Circuit Breaker and Market Halt Controls

- **Category:** Market Integrity
- **Description:** Market-data and trading workflows must detect, propagate, and evidence circuit-breaker and market-halt conditions according to venue and jurisdiction controls.
- **Requirements:**
  - Configurable circuit-breaker thresholds sourced from the authoritative rule/config packs
  - Immediate halt-event propagation to dependent trading workflows
  - Audit trail for manual halt overrides and resumptions
  - Historical evidence of triggered market halts and recovery actions
- **Referenced By:** D-04
- **Regulatory Basis:** SEBON market integrity controls, exchange trading halt procedures, supervisory expectations for orderly markets

### ASR-MARG-001: Margin Operations and Exposure Controls

- **Category:** Risk Management
- **Description:** Margin operations must evidence threshold enforcement, breach detection, and supervised liquidation controls for leveraged accounts.
- **Requirements:**
  - Margin threshold definitions traceable to approved legal and policy sources
  - Real-time exposure monitoring and breach detection for leveraged accounts
  - Margin call issuance and forced-liquidation workflows retained as supervisory evidence
  - Reporting artifacts available for regulator and internal risk review
- **Referenced By:** D-06
- **Regulatory Basis:** SEBON margin trading oversight expectations, broker risk-management obligations, supervisory review of leveraged exposure controls

### ASR-SURV-001: Market Abuse Surveillance Readiness

- **Category:** Surveillance
- **Description:** Surveillance workflows must detect, evidence, and escalate suspicious trading patterns and related market-abuse indicators.
- **Requirements:**
  - Detection logic for wash trades, spoofing, layering, and related suspicious patterns
  - Case evidence preserved with trigger rationale and investigation status
  - Escalation and closure decisions auditable for compliance review
  - Reporting artifacts available for supervisory inspections and internal monitoring
- **Referenced By:** D-08
- **Regulatory Basis:** SEBON market abuse monitoring expectations, exchange surveillance obligations, supervisory review of suspicious trading controls

### ASR-RPT-001: Regulatory Reporting

- **Category:** Reporting
- **Description:** Timely and accurate regulatory reporting to authorities.
- **Requirements:**
  - Daily trade reports to SEBON
  - Monthly position reports
  - Quarterly compliance attestations
  - Annual audit reports
  - Dual-calendar timestamps on all reports
- **Referenced By:** K-15, D-10
- **Regulatory Basis:** SEBON Reporting Requirements, NRB Periodic Reporting

### ASR-EVID-001: Evidence Integrity

- **Category:** Compliance
- **Description:** Evidence packages for regulators must be tamper-evident and cryptographically signed.
- **Requirements:**
  - Cryptographic signing of evidence packages
  - Manifest files with checksums
  - Tamper-evident packaging
  - Signature verification required
  - Immutable audit trail of package generation
- **Referenced By:** R-01
- **Regulatory Basis:** SEBON Audit Requirements, Digital Evidence Standards

### ASR-TECH-001: Technical Auditability

- **Category:** Technology
- **Description:** Platform technical state must be auditable and reproducible.
- **Requirements:**
  - SDK version tracking
  - Platform manifest versioning
  - Configuration change tracking
  - Deployment history retention
  - Reproducible builds
- **Referenced By:** K-12, PU-004
- **Regulatory Basis:** SEBON Technology Risk Guidelines, SOC 2 Requirements

### ASR-QA-001: Software Quality Assurance

- **Category:** Quality
- **Description:** Comprehensive testing and quality assurance for platform software.
- **Requirements:**
  - Unit test coverage > 80%
  - Integration testing for all workflows
  - Performance testing against NFR targets
  - Security testing (OWASP Top 10)
  - Regression testing on every commit
- **Referenced By:** T-01
- **Regulatory Basis:** SEBON Software Development Guidelines, ISO 25010

---

## Compliance Code Usage Overview

Exact per-epic reference counts drift quickly as epics are revised. Treat the code definitions above as authoritative and use a repo-wide search when an exact impact count is needed for a review or audit pass.

Current priority guidance:

| Code Family | Typical Coverage Area                                          | Priority |
| ----------- | -------------------------------------------------------------- | -------- |
| LCA-AUDIT   | Kernel, workflow, operational, regulatory, and evidence flows  | Critical |
| LCA-SOD     | Approval, privileged access, and controlled change workflows   | High     |
| LCA-RET     | Audit, data governance, onboarding, reporting, and config data | High     |
| LCA-AMLKYC  | Identity, onboarding, sanctions, and compliance controls       | High     |
| LCA-COMP    | Policy/rules evaluation and regulated business decisions       | High     |
| LCA-BESTEX  | OMS and execution workflows                                    | Medium   |
| LCA-TAX     | Corporate actions and jurisdictional withholding               | Medium   |
| ASR-OPS     | Observability, resilience, workflow, and operator functions    | High     |
| ASR-SEC     | Identity, secrets, packaging, testing, and secure SDK flows    | Critical |
| ASR-DATA    | Data governance and regulator-facing data access               | High     |
| ASR-RPT     | Regulatory reporting and calendar-sensitive submissions        | High     |
| ASR-EVID    | Regulator evidence generation and integrity controls           | Medium   |
| ASR-TECH    | SDK, deployment, and platform manifest auditability            | Medium   |
| ASR-QA      | Test governance and release-readiness evidence                 | Medium   |

---

## Regulatory Framework Mapping

### Nepal Securities Board (SEBON)

- **Applicable Codes:** LCA-AUDIT-001, LCA-SOD-001, LCA-RET-001, LCA-AMLKYC-001, LCA-COMP-001, LCA-BESTEX-001, ASR-OPS-001, ASR-SEC-001, ASR-RPT-001, ASR-EVID-001, ASR-TECH-001
- **Key Regulations:**
  - SEBON Act 2063
  - Securities Market Conduct Rules
  - Insider Trading Regulations
  - AML Directives
  - Cybersecurity Guidelines
  - Business Continuity Guidelines

### Nepal Rastra Bank (NRB)

- **Applicable Codes:** LCA-AMLKYC-001, LCA-RET-001, ASR-RPT-001
- **Key Regulations:**
  - NRB KYC Directive
  - AML/CFT Guidelines
  - Internal Control Guidelines
  - Periodic Reporting Requirements

### International Standards (Reference)

- **SOX (Sarbanes-Oxley):** LCA-AUDIT-001, LCA-SOD-001
- **GDPR:** LCA-AUDIT-001, LCA-RET-001, ASR-DATA-001
- **MiFID II:** LCA-BESTEX-001
- **FATF:** LCA-AMLKYC-001
- **ISO 27001:** ASR-SEC-001
- **ISO 22301:** ASR-OPS-001
- **SOC 2:** LCA-AUDIT-001, ASR-TECH-001

---

## Compliance Verification Checklist

### For Each Epic Implementation

- [ ] All referenced compliance codes documented
- [ ] Compliance requirements implemented in code
- [ ] Audit logging configured per LCA-AUDIT-001
- [ ] Maker-checker workflows implemented (if applicable)
- [ ] Data retention policies configured
- [ ] Security controls implemented per ASR-SEC-001
- [ ] Test cases cover compliance scenarios
- [ ] Compliance evidence generation tested
- [ ] Regulatory reporting validated
- [ ] Documentation updated with compliance details

### For Platform Deployment

- [ ] All LCA codes verified across platform
- [ ] All ASR codes verified across platform
- [ ] Compliance dashboard configured
- [ ] Regulatory reporting pipelines tested
- [ ] Audit trail integrity verified
- [ ] Data residency rules enforced
- [ ] Security controls operational
- [ ] Incident response procedures documented
- [ ] Compliance training completed
- [ ] Regulatory approval obtained (if required)

---

## Maintenance & Updates

### Adding New Compliance Codes

1. Assign unique code (LCA-XXX-### or ASR-XXX-###)
2. Document in this registry with full details
3. Reference in relevant epic specifications
4. Update compliance verification checklist
5. Communicate to implementation teams
6. Update compliance dashboard

### Updating Existing Codes

1. Document change reason and regulatory basis
2. Update all referencing epics
3. Notify affected teams
4. Update test cases
5. Version control the change
6. Audit trail of modification

### Quarterly Review

- Review all compliance codes for regulatory changes
- Validate code usage across epics
- Update regulatory framework mapping
- Assess new regulatory requirements
- Archive deprecated codes

---

**Registry Status:** ✅ ACTIVE  
**Last Updated:** March 10, 2026  
**Next Review:** June 10, 2026  
**Owner:** Compliance & Regulatory Team  
**Approver:** Chief Compliance Officer
