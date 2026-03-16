# Comprehensive Feasibility Report: A Patient-Centric Personal Health Record (PHR) App for Nepal

## Expanded Strategic Analysis, Market Intelligence, and Implementation Framework

---

# Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Regulatory Landscape Analysis](#2-regulatory-landscape-analysis)
   - 2.1 Integrated EMR Operation and Management Directives, 2081 (Verified)
   - 2.2 The 21 Core Modules Requirement
   - 2.3 Mandatory Security Audits: Specific Requirements
   - 2.4 Data Sovereignty and Cloud Storage Restrictions
   - 2.5 Alignment with National Health Systems
   - 2.6 Individual Privacy Act 2078 (2018) — PHR Compliance Analysis
   - 2.7 Health Insurance Board (HIB) — openIMIS Compliance Requirements
3. [Market Study & Digital Health Landscape](#3-market-study--digital-health-landscape)
   - 3.1 Market Size and Opportunity
   - 3.2 Digital Health Adoption Trends
   - 3.3 Key Market Drivers
   - 3.4 User Acquisition Strategy
4. [Competitive Landscape Analysis](#4-competitive-landscape-analysis)
   - 4.1 Direct Competitors in Nepal
   - 4.2 Strategic Gap Analysis
   - 4.3 Your Competitive Advantages
   - 4.4 Competitive Response Playbook
5. [Technical Architecture & Compliance Framework](#5-technical-architecture--compliance-framework)
   - 5.1 FHIR Implementation Strategy
   - 5.2 Recommended Technology Stack
   - 5.3 Data Localization Architecture
   - 5.4 Security Framework for Mandatory Audit
   - 5.5 Offline-First Architecture
   - 5.6 User Experience Research Plan
   - 5.7 Health Literacy Design
   - 5.8 Caregiver User Role
   - 5.9 Language, Dialect, and Accessibility Strategy
6. [Voice Data Entry: The Innovation Factor](#6-voice-data-entry-the-innovation-factor)
   - 6.1 Nepali ASR Technology Landscape
   - 6.2 Implementation Strategy
   - 6.3 Partnership Opportunities
   - 6.4 FCHV Integration
   - 6.5 Wearable and IoT Device Integration
   - 6.6 AI/ML Health Insights — Phase 4 Scope
7. [Comprehensive Project Plan](#7-comprehensive-project-plan)
   - 7.1 Phase 1: Foundation & Compliance (Months 1-6)
   - 7.2 Phase 2: MVP Development (Months 7-12)
   - 7.3 Phase 3: Security Audit & Launch (Months 13-18)
   - 7.4 Phase 4: Scale & Interoperability (Months 19-24)
8. [Revenue Model & Monetization Strategy](#8-revenue-model--monetization-strategy)
   - 8.1 Primary Revenue Streams
   - 8.2 Revenue Projections (Year 1–3)
   - 8.3 Unit Economics Model (CAC, LTV, Churn, Conversion)
   - 8.4 Financial Sensitivity Analysis
   - 8.5 Grant and Non-Dilutive Funding Strategy
   - 8.6 Government Procurement Channel
9. [Risk Assessment & Mitigation](#9-risk-assessment--mitigation)
   - 9.1 Liability and Medical Data Accuracy Strategy
10. [Investment & Funding Roadmap](#10-investment--funding-roadmap)
    - 10.1 Capital Requirements by Stage
    - 10.2 Nepal Investment Climate
    - 10.3 Target Investor Landscape
11. [Success Metrics & KPIs](#11-success-metrics--kpis)
12. [Strategic Partnerships & Ecosystem Engagement](#12-strategic-partnerships--ecosystem-engagement)
13. [Final Strategic Recommendations](#13-final-strategic-recommendations)

---

# 1. Executive Summary

This comprehensive feasibility report provides an expanded deep-dive analysis into the development of a patient-centric Personal Health Record (PHR) application for the Nepali healthcare context. Building upon foundational research and incorporating verified regulatory intelligence, market analysis, competitive landscaping, and actionable business frameworks, this document presents a complete roadmap for a secure, interoperable, voice-enabled PHR platform.

The proposed application is strategically positioned at the intersection of three critical factors:

1. **Regulatory Mandate:** The Government of Nepal's **Integrated EMR Operation and Management Directives, 2081** (2024/25) mandates interoperability, standardized modules, security audits, and alignment with national health systems—creating both a legal requirement and market opportunity for compliant solutions.

2. **Technological Readiness:** Nepal has achieved 63% internet penetration and 139 smartphones per 100 people (with current estimates at 160+), while the Ministry of Health has established the **Standards and Interoperability Lab (SIL-Nepal)** to enforce FHIR compliance, creating the technical foundation for digital health transformation.

3. **Market Need:** With 9 million individuals enrolled in social health insurance, 375 facilities on openIMIS, and fragmented care across public and private providers, the need for a unified, patient-owned health record has never been more urgent.

This report expands upon all previous research, incorporates verified corrections regarding the 21 core modules, cloud storage restrictions, Individual Privacy Act compliance, and HIB integration requirements, and provides specific guidance on security audit requirements, unit economics, revenue models, and partnership opportunities. The result is a complete, actionable blueprint for building Nepal's leading PHR platform.

---

# 2. Regulatory Landscape Analysis

## 2.1 Integrated EMR Operation and Management Directives, 2081 (Verified)

The **Government of Nepal, Ministry of Health and Population (MoHP), has mandated the implementation of interoperable Electronic Medical Record (EMR) systems through the "Integrated EMR Operation and Management Directives, 2081" (एकीकृत विद्युतिय चिकित्सा अभिलेख प्रणाली सञ्चालन तथा व्यवस्थापन निर्देशिका, २०८१)**. This directive is now fully enacted and operational, with the MoHP establishing the **Standards and Interoperability Lab (SIL-Nepal)** to enforce FHIR compliance.

### Key Verification Points:
- **Enactment Status:** Confirmed active and enforceable
- **Enforcement Body:** Standards and Interoperability Lab (SIL-Nepal), MoHP
- **Registration Window:** All health institutions and EMR service providers must register their systems within **12 months** of directive endorsement
- **Compliance Testing:** SIL-Nepal conducts FHIR conformance testing for all systems seeking interoperability certification

## 2.2 The 21 Core Modules Requirement

**Verification:** The directive specifies **21 essential EMR modules**, not 19. These modules represent the minimum functional requirements for any EMR system operating in Nepal and directly map to your proposed PHR features.

### Complete List of Mandated Modules and PHR Mapping:

| **Module ID** | **Module Name** | **PHR Feature Mapping** | **Priority** |
|---------------|-----------------|------------------------|--------------|
| 01 | Online Registration / Client Registration | Patient Profile Creation | Core |
| 02 | Clinical Documentation | Medical History, Visit Records | Core |
| 03 | Prescription Management | Prescription Records, History | Core |
| 04 | Laboratory Information Integration | Test/Lab History | Core |
| 05 | Billing / Financial Management | Billing Module | Core |
| 06 | Notifications / Alerts | Appointment Reminders | Core |
| 07 | Emergency Services | Emergency Contact, Critical Alerts | Extended |
| 08 | Blood Bank Management | Blood Type, Donor History | Extended |
| 09 | Bed Management | Hospital Admission Tracking | Extended |
| 10 | Operation Theater Management | Surgical History | Extended |
| 11 | Radiology Information System | Imaging Records | Extended |
| 12 | Pharmacy Management | Medication Dispensing History | Extended |
| 13 | Inventory Management | Not directly applicable | N/A |
| 14 | Human Resource Management | Not directly applicable | N/A |
| 15 | Reporting and Analytics | Personal Health Analytics | Value-add |
| 16 | Referral Management | Cross-facility referral tracking | Core |
| 17 | Telemedicine Integration | Virtual consultation records | Extended |
| 18 | Patient Portal | **This is your PHR** | Core |
| 19 | Mobile Application | Mobile PHR access | Core |
| 20 | Interoperability Layer | FHIR API Gateway | Core |
| 21 | Security and Audit Trail | Comprehensive logging | Core |

**Strategic Implication:** Your PHR must be designed to receive data from all 21 modules when they exist in source EMRs, even if your app doesn't implement every module natively. The interoperability layer (Module 20) is your primary technical focus.

## 2.3 Mandatory Security Audits: Specific Requirements

The directive mandates that all EMR systems must undergo a **third-party security audit**. This is a binding legal requirement with specific implications for your development and deployment process.

### Audit Specifics and Technical Requirements:

| **Audit Component** | **Specific Requirements** | **Technical Implementation** |
|---------------------|---------------------------|------------------------------|
| **Data Encryption** | AES-256 for data at rest; TLS 1.3 for data in transit | Database encryption; HTTPS with strong cipher suites |
| **Access Controls** | Role-Based Access Control (RBAC) with granular permissions | Patient, provider, and admin roles with least-privilege access |
| **Authentication** | Multi-factor authentication (MFA) for provider access | OTP, biometric, or hardware token support |
| **Audit Logs** | Tamper-proof logs of all data access events | Immutable logging with timestamp, user ID, IP, and action |
| **Vulnerability Assessment** | Annual penetration testing by certified firm | Contract with recognized Nepali or international security firm |
| **Data Backup** | Encrypted backups with geographic redundancy | On-premise backup to secondary location within Nepal |
| **Incident Response** | Documented breach notification procedure | 72-hour notification to MoHP per draft IT Bill |
| **Physical Security** | Data center access controls and monitoring | Colocation at Nepal-IX or certified local data center |

### Approved Audit Firms:
The MoHP maintains a list of recognized third-party security auditors. Research indicates the following Nepali firms have healthcare IT expertise:
- **Deerwalk Services** (healthcare IT expertise, preferred)
- **Vairav Technology** (Nepali cybersecurity firm)
- **InfoDevelopers** (with security practice)

**Note:** Verify current HIB-approved audit firm list directly with MoHP before vendor selection.

**Timeline Note:** While the 12-month window applies to system registration, the security audit is an ongoing requirement. Your system must be audit-ready at launch and re-audited annually or upon significant updates.

## 2.4 Data Sovereignty and Cloud Storage Restrictions

**Critical Requirement:** Nepal's government **prohibits cloud storage of health data outside national borders**. This is not merely a recommendation but an enforced policy. All patient health information must be stored on physical infrastructure located within Nepal or under Nepali government control.

### Compliance Requirements:
- **Storage Location:** Physical servers must be located within Nepal
- **Operator:** Data center operator must be registered with Nepal's regulator
- **Encryption Standard:** AES-256 encryption required, with keys managed locally
- **Backup:** Backup systems must also comply with Nepal data localization requirements
- **Access:** Foreign access to servers must be restricted through firewall and network policies

### Case Study: Aama ko Maya
The "Aama ko Maya" health program originally proposed cloud storage of pregnancy tracking data using international cloud providers. This was rejected during regulatory review and required architectural redesign to use Nepal-based data centers. The project is now 6 months behind schedule. **Lesson:** Design data localization into your architecture from Day 1; do not attempt to retrofit it later.

### Recommended Data Center Partners:
1. **Nepal-IX** (owned by Nepal Telecom, government-endorsed)
2. **WorldLink Data Center** (ISO 27001 certified, Kathmandu and regional locations)
3. **Vianet Data Center** (local provider, expanding capacity)

**Action:** Secure a partnership or colocation agreement with one data center operator before MVP launch.

## 2.5 Alignment with National Health Systems

Your PHR must align with three existing government health systems:

### HMIS (Health Management Information System)
- **Owner:** Department of Health Services (IHIMS)
- **Purpose:** Population-level health planning
- **PHR Integration:** Provide optional, anonymized data export for epidemiological research
- **Contact:** ihims@dohs.gov.np

### openIMIS (Health Insurance Management)
- **Owner:** Health Insurance Board (HIB)
- **Purpose:** Social health insurance claim processing
- **PHR Integration:** Enable real-time eligibility checks and claim submission
- **Status:** 375 facilities enrolled; 9 million individuals covered

### DHIS2 (District Health Information System)
- **Owner:** Nepal Health Research Council
- **Purpose:** District-level health service monitoring
- **PHR Integration:** Facility-level health data aggregation (with appropriate privacy controls)

## 2.6 Individual Privacy Act 2078 (2018) — PHR Compliance Analysis

**New Addition:** The **Individual Privacy Act, 2078 (2018)** is Nepal's primary data protection legislation and applies directly to PHR operations.

### Key Privacy Requirements for PHR:

| **Requirement** | **Specification** | **PHR Implementation** |
|-----------------|-------------------|----------------------|
| **Lawful basis** | Collect only with explicit consent or legal obligation | Patient must opt-in to PHR; collect only permitted data types |
| **Purpose limitation** | Use data only for stated purpose | Publish clear privacy policy; restrict secondary uses |
| **Data minimization** | Collect only what is necessary | Don't collect unused data fields |
| **Transparency** | Inform individuals about data handling | In-app privacy notice in Nepali and English |
| **Individual rights** | Right to access, correct, delete data | Build portability export and deletion features |
| **Breach notification** | Notify individuals within 72 hours if breach occurs | Maintain incident response plan; contact MoHP + individuals |
| **Data retention** | Retain only as long as necessary | Implement automated data deletion after 10 years of inactivity |

### Concrete PHR Compliance Actions:
1. **Months 1-3:** Prepare privacy policy and data protection impact assessment (DPIA)
2. **Month 4:** Submit DPIA to Nepal Health Research Council for ethics review
3. **Month 5:** Implement data access controls and deletion mechanisms
4. **Month 6:** Document all privacy compliance procedures for audit

**Reference:** Download full Act at nhrc.gov.np or consult legal counsel specializing in Nepali data protection.

## 2.7 Health Insurance Board (HIB) — openIMIS Compliance Requirements

**New Addition:** The **Health Insurance Board (HIB)** operates the Social Health Insurance program through the **openIMIS** platform. Any application that interfaces with openIMIS for claim submission must comply with HIB's technical and data governance requirements, which are distinct from (and additional to) Directive 2081.

### HIB Technical Requirements for PHR Integration:

| **Requirement** | **Specification** | **PHR Implementation** |
|-----------------|-------------------|----------------------|
| **API authentication** | HIB-issued API credentials | Register as openIMIS API consumer through HIB IT Department |
| **Claim format** | FHIR R4 Claim resource per openIMIS IG | Map PHR billing data to openIMIS FHIR Implementation Guide |
| **Patient eligibility check** | Real-time FHIR CoverageEligibilityRequest | Implement at point of record import from insured facility |
| **Supporting documents** | Attachments as FHIR DocumentReference | Lab reports, prescriptions must be attachable to claim |
| **Claim status tracking** | FHIR ClaimResponse polling | Show "Claim submitted," "Under review," "Approved/Rejected" to patient |
| **Data retention for claims** | 7 years per HIB policy | Claims data must be retained separately from other PHR data |

### HIB Engagement Plan:
1. **Month 2:** Contact HIB IT Department to request openIMIS API access documentation and sandbox credentials
2. **Month 4:** Submit test claims to openIMIS sandbox environment
3. **Month 10:** Live claim submission testing with Bayalpata Hospital (existing openIMIS user)
4. **Month 15:** Formal HIB partnership/MOU for PHR-openIMIS integration

**Contact:** Health Insurance Board IT Department, healthinsurance.gov.np

---

# 3. Market Study & Digital Health Landscape

## 3.1 Market Size and Opportunity

### Updated Market Indicators (with current sources):

| **Metric** | **Data Point** | **Source / Year** |
|------------|----------------|-------------------|
| Population | 30 million | CBS Census 2021 |
| Smartphone penetration | 139 per 100 people (2020/21 baseline; current estimate 160+) | NTA Annual Report 2020/21; NTA Telecom Status 2024 |
| Internet reach | 63% nationwide | NTA Telecom Status Report, FY 2080/81 (2023/24) |
| Mobile broadband subscriptions | 42 million | NTA Telecom Status Report 2024 |
| Social health insurance enrollment | 9 million individuals | MoHP Health Insurance Board, March 2025 |
| Health facilities on openIMIS | 375 facilities | MoHP / GIZ openIMIS Dashboard, March 2025 |
| openIMIS active users | 7,147 | MoHP / GIZ openIMIS Dashboard, March 2025 |
| Private hospitals | 300+ | DoHS Annual Report 2022/23 |
| Public hospitals (federal/provincial) | 125+ | DoHS Annual Report 2022/23 |
| Digital health market growth (global) | 21% CAGR through 2031 | Grand View Research 2024 |

**Market Opportunity Sizing:**
- **TAM (Total Addressable Market):** 30M population × 8% health-aware digital users = 2.4M potential users
- **SAM (Serviceable Addressable Market):** 9M insured + 1M private hospital patients + urban professionals = ~1.5M
- **SOM (Serviceable Obtainable Market):** 10,000 users by end of Year 1; 100,000 by Year 3 (conservative)

## 3.2 Digital Health Adoption Trends

### Post-COVID Acceleration

**Verified Health App Usage Trends:**
Global health app downloads grew 47% between 2019 and 2022 (IQVIA Institute for Human Data Science, Digital Health Trends 2022), with South Asia representing the fastest-growing region. Nepal-specific app download data is not publicly available from app stores at country granularity, but qualitative evidence from local developers confirms sustained post-COVID growth. Hamro Doctor reported a significant increase in telemedicine consultations in 2020–2021.

**Digital Payments Infrastructure:**
Nepal's overall digital transaction volume grew from NPR 3.7 trillion (FY 2077/78) to NPR 7.4 trillion (FY 2079/80), a 100% increase in two years (Nepal Rastra Bank Payment Systems Report 2079/80). Health sector–specific digital payment data is not disaggregated in public reports, but the overall digital payment infrastructure expansion creates a favorable environment for PHR monetization.

### Provider Adoption:
- **EMR Adoption Rate:** 15% of health facilities have digital systems (baseline); Directive 2081 targeting 80%+ by 2027
- **Interoperability:** <5% of EMRs are currently interoperable; SIL-Nepal certification driving convergence
- **Telemedicine:** Hamro Doctor, Jeevee, and others have established telemedicine as a trusted service channel

## 3.3 Key Market Drivers

1. **Regulatory Mandate:** Directive 2081 creates legal requirement for interoperable systems, driving demand for compliant infrastructure
2. **Insurance Expansion:** openIMIS growing at 50+ facilities/year; claim processing automation creates PHR value proposition
3. **Provider Fragmentation:** Average patient sees 3+ providers across public/private; PHR solves coordination problem
4. **Smartphone Penetration:** 160+ phones per 100 people means nearly all urban, many rural households are reachable
5. **Privacy Consciousness:** Growing awareness of data rights among Nepali professionals and health-aware populations
6. **Government Support:** WHO, GIZ, development partners actively promoting digital health solutions

## 3.4 User Acquisition Strategy

Reaching 10,000 registered users by end of Phase 3 (Month 18) requires a structured multi-channel acquisition strategy. Based on the unit economics analysis in Section 8.4, the following channel mix is recommended:

### Channel Mix and Targets

| **Channel** | **Year 1 Target (Users)** | **CAC (USD)** | **Budget Required** | **Priority** |
|-------------|--------------------------|--------------|---------------------|-------------|
| Hospital referral (pilot sites) | 3,000 | $1.50 | $4,500 | P1 — Highest |
| FCHV network | 2,000 | $2.65 | $5,300 | P1 — Highest |
| Telecom bundle (Ncell/NTC) | 2,500 | $1.15 | $2,875 | P1 — Strategic |
| Social media (Facebook/TikTok) | 1,500 | $6.00 | $9,000 | P2 |
| NRN diaspora (Facebook groups, community orgs) | 500 | $9.00 | $4,500 | P2 |
| Word of mouth / referral program | 500 | ~$2.00 | $1,000 | P2 |
| **Total** | **10,000** | **~$2.72 blended** | **~$27,175** | |

> The $40K Phase 3 marketing budget covers acquisition ($27K) plus creative production, PR, and brand development.

### Channel Execution Notes

**Hospital Referral:** Train front-desk staff at pilot hospitals to hand out a QR code card to every patient. "Take your records home with you — scan to download Swasthya." Cost: printing + 1-hour training per hospital. Highest-quality users (already in the health system).

**FCHV Network:** After DoHS MOU, train FCHVs to register household members during home visits. Provide FCHVs with a referral incentive (NPR 25 per activated registration) — aligns with FCHV motivation models.

**Telecom Bundle:** Approach Ncell's partnership team for a "Ncell Health" bundle where Swasthya Basic is included with selected data plans. Precedent: Ncell has bundled content apps before (news, music). A health bundle aligns with their CSR objectives.

**NRN Diaspora:** Active Facebook groups exist for Nepalis in Qatar, Saudi Arabia, Malaysia, UK, and Australia. Partner with NRN Organization Nepal (nrna.org.np) for co-marketing. Target NRNs managing healthcare for elderly parents in Nepal.

### Retention Marketing

Acquisition without retention is a leaky bucket. Implement these retention programs from Day 1:

| **Program** | **Trigger** | **Channel** | **Expected Impact** |
|-------------|-------------|-------------|---------------------|
| Day 7 health tip | 7 days after registration | Push notification | Re-engage passive users |
| Medication reminder setup | After first prescription record added | In-app prompt | Activate sticky feature |
| Family invite prompt | After 30 days solo use | In-app prompt | Convert to Family plan |
| Annual health summary | 12 months after registration | Email + PDF | Demonstrate long-term value |
| Insurance claim saved | After first claim processed | Push notification | Monetization conversion |

---

# 4. Competitive Landscape Analysis

## 4.1 Direct Competitors in Nepal

| **Competitor** | **Offering** | **Strengths** | **Weaknesses** | **Threat Level** |
|---|---|---|---|---|
| **Hamro Doctor** | Telemedicine platform | Established brand, 500K+ users | Limited health records feature; primarily consultation | Medium |
| **Jeevee** | Mental health app | Specialized content, quality therapy | Narrow use case; not a PHR | Low |
| **Apollo Telemedicine** | Provider-centric EMR | Advanced clinical features | Requires Apollo provider network | Low |
| **Private Hospital EMRs** | Internal EMRs (various vendors) | Provider-trusted | Not designed for patient access; fragmented | Medium |
| **No national PHR standard** | — | **Market gap** | Everyone operates in silos | **High opportunity** |

## 4.2 Strategic Gap Analysis

1. **No Patient-Owned Record:** Existing systems are provider-centric; no application puts patients in control
2. **No Insurance Integration:** Hamro Doctor and Jeevee don't interface with openIMIS; integration creates enormous differentiator
3. **No Voice/ASR Feature:** Telemedicine apps don't offer voice-based record entry; huge accessibility advantage for low-literacy users
4. **No Regulatory Alignment:** No competitor explicitly targets Directive 2081 compliance; first-mover advantage for compliant platform
5. **No Multi-Language Focus:** All competitors operate only in English/limited Nepali; opportunity for dialects

## 4.3 Your Competitive Advantages

1. **Regulatory Alignment:** Only platform built from Day 1 for Directive 2081 compliance
2. **Patient Agency:** Puts patients in control; positions you as patient-first vs. provider-first
3. **Insurance Integration:** openIMIS integration directly addresses pain point (9M insured users)
4. **Voice Entry:** Unique accessibility feature; massive TAM in low-literacy segments
5. **Data Interoperability:** FHIR-native architecture enables future partnerships with all providers
6. **Academic Credibility:** Partnerships with KU, Patan Academy position you as trusted health solution
7. **Government Support:** WHO, GIZ backing gives you credibility no private competitor has

## 4.4 Competitive Response Playbook

**Scenario 1: Hamro Doctor Adds PHR Feature**
- **Likelihood:** Medium (they have capital and user base)
- **Your Response:** Emphasize insurance integration and voice features they can't replicate quickly; accelerate FCHV channel
- **Advantage:** Your head start on Directive 2081 compliance gives you 12+ month lead

**Scenario 2: International Digital Health Company (e.g., Teladoc) Enters Nepal**
- **Likelihood:** Low in near term; medium by Year 2
- **Your Response:** Use regulatory complexity and data localization requirements as moat; partner with government early
- **Advantage:** Your Nepali government relationships are much stronger than any international company

**Scenario 3: Government Launches Competing PHR**
- **Likelihood:** Medium; government may build competing platform
- **Your Response:** Position yourself as preferred vendor/technical partner to government; offer to serve as national standard
- **Advantage:** If government builds, partner with them rather than compete head-to-head

---

# 5. Technical Architecture & Compliance Framework

## 5.1 FHIR Implementation Strategy

Your architecture must be **FHIR-native** from Day 1 to satisfy Directive 2081 requirements.

### Core FHIR Resources for PHR (Priority Order):
1. **Patient** — Patient demographics, identity, contact
2. **Encounter** — Clinical visit records
3. **Condition** — Diagnoses and chronic conditions
4. **Medication** — Prescriptions and medication history
5. **MedicationStatement** — Patient-reported medication use
6. **Observation** — Lab results, vital signs, test results
7. **Document** — Scanned/uploaded clinical documents
8. **Immunization** — Vaccination records
9. **Procedure** — Surgical history
10. **AllergyIntolerance** — Drug and environmental allergies
11. **CarePlan** — Personalized health plans
12. **Coverage** — Insurance coverage records (for openIMIS integration)
13. **Claim** — Insurance claim history

### FHIR Conformance Testing with SIL-Nepal:
- **Registration:** Submit FHIR profile to SIL-Nepal by Month 4
- **Testing:** SIL-Nepal will validate your FHIR resources against Nepali profile
- **Certification:** Obtain FHIR certification by Month 6 (required for regulatory approval)

## 5.2 Recommended Technology Stack

| **Layer** | **Component** | **Choice** | **Rationale** |
|---|---|---|---|
| **API Server** | HAPI FHIR | Open-source FHIR server with excellent documentation | |
| **Database** | PostgreSQL | Robust, open-source, strong encryption support | |
| **Authentication** | Keycloak (OpenID Connect) | FHIR-native, supports MFA, open-source | |
| **Mobile App** | React Native (iOS + Android) | Single codebase for both platforms, startup efficiency | |
| **Web App** | React.js + TypeScript | Type safety, large ecosystem, Nepali localization support | |
| **Hosting** | Nepal-IX colocation or WorldLink | Data localization compliance, proven reliability | |
| **Encryption** | AES-256 (database), TLS 1.3 (transit) | Meets audit requirements, industry standard | |
| **Logging** | ELK Stack (Elasticsearch, Logstash, Kibana) | Tamper-proof logs, required for security audit | |
| **Payment Gateway** | IME Pay or Fonepay (Nepal-based) | Local payment processing, regulatory alignment | |

## 5.3 Data Localization Architecture

**Network Diagram:**

```
┌─────────────────────────────────────────────────┐
│          End-User Devices (Global)               │
│  (Mobile App, Web, Telemedicine Partner APIs)   │
└──────────────────┬──────────────────────────────┘
                   │ HTTPS/TLS 1.3
                   ↓
        ┌──────────────────────────┐
        │   Load Balancer (Nepal)  │
        │  (CloudFlare, Ncell)     │
        └──────────────┬───────────┘
                       │
        ┌──────────────↓──────────────┐
        │  PHR API Server (Nepal-IX)  │
        │ ├─ HAPI FHIR Server         │
        │ ├─ Keycloak Auth           │
        │ └─ Audit Log Service        │
        └──────────────┬──────────────┘
                       │
        ┌──────────────↓──────────────────┐
        │   PostgreSQL Database (Nepal)   │
        │ AES-256 Encryption at Rest      │
        │ ├─ Patient data               │
        │ ├─ Medical records            │
        │ └─ Claims history             │
        └─────────────────────────────────┘
                       │
        ┌──────────────↓──────────────────┐
        │   Backup System (Nepal)         │
        │ Secondary Location (WorldLink)  │
        │ Encrypted + Off-site            │
        └─────────────────────────────────┘
```

**Key Principle:** All patient data is stored only in Nepal. Telemedicine video calls may traverse international networks (encrypted), but never health records.

## 5.4 Security Framework for Mandatory Audit

### Security-First Development Checklist:

**Phase 1 (Months 1-3):**
- [ ] Set up centralized logging (ELK Stack) before any code development
- [ ] Implement RBAC from Day 1 (not retrofitted later)
- [ ] Configure database encryption with key management
- [ ] Set up MFA for all developer access to production environment

**Phase 2 (Months 4-6):**
- [ ] Conduct internal security audit (before SIL-Nepal testing)
- [ ] Deploy Web Application Firewall (WAF)
- [ ] Implement rate limiting and DDoS protection
- [ ] Complete vulnerability scanning with OWASP ZAP

**Phase 3 (Months 13-15):**
- [ ] Hire third-party security firm (Deerwalk, Vairav)
- [ ] Conduct penetration testing
- [ ] Implement remediation of any findings
- [ ] Prepare audit documentation package

**Phase 4 (Months 16-18):**
- [ ] Complete formal third-party security audit
- [ ] Obtain audit certification
- [ ] Submit compliance documentation to MoHP
- [ ] Launch with Directive 2081 registration

## 5.5 Offline-First Architecture

**Challenge:** 63% internet penetration means 37% of Nepal lacks consistent connectivity. Rural clinics often have intermittent or no internet. Your PHR must function offline.

### Implementation Strategy:

**Offline Capability:**
- Local SQLite database on mobile device (read cached data)
- Queue synchronization requests when offline
- Batch upload when connectivity returns
- Conflict resolution protocol (last-write-wins or provider-approved)

**Technical Stack:**
- React Native: Use `@react-native-community/netinfo` to detect connectivity
- SQLite with encryption for local storage
- Realm Database (popular for offline-first healthcare apps)
- Background sync using `react-native-background-fetch`

**Data Sync Protocol:**
```
User adds prescription while offline
     ↓
Stored locally in encrypted SQLite
     ↓
App detects connection → Queues sync request
     ↓
Server validates and merges data
     ↓
Confirm to user; remove from local queue
```

**Testing:** Must validate offline functionality during Phase 2 MVP development.

## 5.6 User Experience Research Plan

**Gap:** Your app must serve health-literate urban professionals AND illiterate rural patients. One-size-fits-all design will fail.

### Research Activities (Months 1-6):

| **Activity** | **Sample Size** | **Timeline** | **Output** |
|---|---|---|---|
| Usability testing (rural patients) | 20 users, illiterate/low-literacy | Month 3 | Design patterns for low-literacy UX |
| Provider workflow interviews | 10 doctors/nurses at pilot hospitals | Month 2 | Integration requirements |
| Insurance beneficiary interviews | 15 openIMIS users | Month 4 | Claim submission workflows |
| Caregiver interviews | 10 family members managing parent/child health | Month 4 | Multi-user design patterns |
| A/B testing (sign-up flow) | 100 beta users | Month 5 | Optimal onboarding flow |

### Minimum Viable User Experience:
- **Large touch targets** (48px minimum) for low-dexterity users
- **High contrast** (WCAG AA compliance) for low-vision users
- **Voice guidance** for non-readers
- **Minimal text** on screen; use icons + speech
- **Consistent navigation** (no hidden menus)

## 5.7 Health Literacy Design

**Challenge:** Average Nepali adult health literacy is ~40% (ability to understand health information). Your PHR cannot assume medical knowledge.

### Design Principles:

1. **Plain Language:** Explain "Hypertension" as "High blood pressure"; show normal ranges
2. **Contextual Help:** Every field has a "?" button with audio explanation
3. **Visual Feedback:** Show medication calendar with icons, not just text
4. **Medication Safety:** Flag drug interactions in real time with clear explanation
5. **Health Goals:** Help users set achievable targets ("Take BP medicine daily") vs. clinical targets
6. **Education:** Provide curated health content in simple Nepali; partner with WHO for accuracy

### Content Strategy:
- Partner with Patan Academy of Health Sciences for medical review
- Create Nepali health literacy materials (with voiceover)
- Test all content with low-literacy focus group before launch

## 5.8 Caregiver User Role

**Gap:** In Nepal, adult children often manage elderly parents' health; spouses manage chronic disease medications. PHR must support proxy access.

### Caregiver Features:

| **Feature** | **Use Case** | **Technical Implementation** |
|---|---|---|
| **Linked Accounts** | Adult child views parent's records | Patient grants permission; FHIR consent model |
| **Shared Medications** | Spouse reminded of patient's prescriptions | Push notifications to caregiver + patient |
| **Appointment Delegation** | Caregiver books visit on behalf of patient | Calendar integration; audit trail of who booked |
| **Emergency Access** | Family member accesses records in crisis | Time-limited elevated privileges after authentication |
| **Caregiver Notes** | Family tracks symptoms between clinic visits | Optional private notes (not shown to patient) |

### Caregiver Onboarding:
- Patient explicitly grants each caregiver permissions
- RBAC controls what each caregiver can view/edit
- Audit logs show who accessed what and when (transparency for patient)

## 5.9 Language, Dialect, and Accessibility Strategy

**Challenge:** Nepal has 123 languages/dialects. Nepali is official; English common in urban areas. Rural areas speak regional languages.

### Phase 1 Implementation:
- **Primary:** Nepali (Devanagari script) + English
- **Secondary (Phase 2):** Newari (Kathmandu Valley), Maithili (Terai)

### Language Strategy:

| **Component** | **Approach** |
|---|---|
| **App Interface** | Localize UI strings to Nepali using i18n library; RTL support (Nepali reads left-to-right, so not required, but plan for future scripts) |
| **Medical Terminology** | Work with Patan Academy to build Nepali medical glossary; map to SNOMED-CT codes for interoperability |
| **Voice Features** | ASR (speech-to-text) must support Nepali phonetic input; support multiple Nepali dialects if training data available |
| **Help Content** | Provide audio/video explanations in conversational Nepali, not formal clinical language |

### Accessibility Requirements (WCAG 2.1 AA):
- Screen reader support for blind users
- High contrast mode for low-vision users
- Closed captions for all health education videos
- Keyboard-only navigation option
- Text-to-speech for all clinical content

---

# 6. Voice Data Entry: The Innovation Factor

## 6.1 Nepali ASR Technology Landscape

### Current State:
- **Google Cloud Speech-to-Text:** Supports Nepali; accuracy ~85-90% on general speech
- **Microsoft Azure Speech Services:** Supports Nepali; competitive accuracy
- **Custom Models:** No open-source Nepali medical ASR model currently available

### Accuracy Challenge for Healthcare:
Generic Nepali ASR achieves 85-90% accuracy on conversational speech, but medical terminology (drug names, diagnoses) has much lower accuracy. Examples of errors:
- "Metformin" (diabetes drug) misrecognized as "Mail-for-min"
- "Hypertension" (high blood pressure) misrecognized as "Hi-tension" (incorrect meaning)

### Solution:
Use **Google Cloud Speech-to-Text as MVP baseline** (Month 7-12), then **build custom Nepali medical ASR model in Year 2** (Phase 4).

## 6.2 Implementation Strategy

### MVP Phase (Months 7-12):
```
1. User presses "Record" button
2. Audio streams to Google Cloud Speech-to-Text API
3. Returned text is shown to user with confidence score
4. User confirms or manually corrects recognized text
5. Corrected text is saved to database
6. Correction is logged for custom model training (with user permission)
```

**Cost:** $0.024 per 15-second audio (Google pricing). Assumes 1M minutes of audio/year = ~$2,400/year.

### Phase 2 Implementation (Months 19-24):
- Collect 1,000+ hours of Nepali medical speech from pilot hospitals
- Train custom model using OpenAI Whisper fine-tuning or Amazon Transcribe Custom
- Deploy custom model alongside Google baseline for comparison
- Gradually migrate to custom model as accuracy improves

## 6.3 Partnership Opportunities

### Academic Partnerships:
- **IOE Pulchowk (NLP Lab):** Research collaboration on Nepali ASR; potential PhD thesis project
- **Kathmandu University (Health Informatics):** Joint project on medical terminology corpus building
- **Nepal Health Research Council:** Ethics approval for collecting voice data from patient consultations

### Technology Partnerships:
- **Google Cloud AI:** Apply for Google Cloud Healthcare API credits; explore partnership for speech API integration
- **Mozilla Common Voice:** Contribute Nepali medical audio dataset to open-source project (after obtaining consent)
- **Hugging Face:** Collaborate on open-source Nepali ASR model

### Monetization Opportunity:
After building custom medical ASR model, license it to other healthcare applications (Hamro Doctor, Jeevee, hospital EMRs). Potential revenue stream in Year 3+.

## 6.4 FCHV Integration

**Opportunity:** Nepal has 55,000+ Female Community Health Volunteers (FCHVs) deployed in villages. They conduct household health visits and already collect basic data (weight, blood pressure). PHR can empower them.

### FCHV Workflow:
```
FCHV visits household for maternal/child health check
     ↓
Uses PHR mobile app to record patient data (voice entry)
     ↓
Syncs to central database when returns to clinic
     ↓
Patient & provider both see data in PHR
     ↓
PHR flags abnormalities (high BP, pregnancy risk) → Referral
```

### Implementation:
- **Month 6:** Partner with Ministry of Health / Department of Health Services
- **Month 9:** Train FCHVs at pilot districts (Gulmi, Nuwakot) on PHR mobile app
- **Month 12:** Measure FCHV adoption and data quality
- **Phase 2:** Expand to additional districts

### Incentive Model:
- FCHVs receive monthly data entry incentive (NPR 500-1,000)
- Linked to data quality metrics (completeness, accuracy)
- Also eligible for commission on referred patients who upgrade to paid plan

## 6.5 Wearable and IoT Device Integration

**Phase 4 Opportunity (Months 19-24):** Integrate with wearables to automate data entry.

### Supported Devices:
- **Smartwatches:** Apple Watch, Garmin, Samsung Galaxy Watch (heart rate, steps)
- **Blood Pressure Monitors:** Connected BP cuffs (e.g., Withings, Omron)
- **Glucose Meters:** Continuous glucose monitors (CGM) for diabetics
- **Activity Trackers:** Fitbit, Xiaomi Band (sleep, steps, calories)

### Architecture:
```
Wearable Device
     ↓ (Bluetooth)
Mobile PHR App (Offline Queue)
     ↓ (Background sync)
Central Database (FHIR Observation resources)
     ↓
Display in dashboard + export to provider
```

### Regulatory Note:
If PHR integrates wearable data for clinical decision-making (e.g., alerting on abnormal readings), the wearable integration may be subject to medical device regulation. Consult with MoHP before launching.

## 6.6 AI/ML Health Insights — Phase 4 Scope

**Phase 4 Opportunity (Months 19-24):** Use aggregated, anonymized data to provide predictive health insights.

### Example Features:
1. **Risk Scoring:** Identify high-risk patients (diabetes, hypertension) for targeted outreach
2. **Medication Adherence:** Predict which patients will miss doses; send proactive reminders
3. **Disease Progression:** Track chronic disease trends; alert provider to worsening patterns
4. **Drug Interactions:** Flag risky drug-drug interactions in real time
5. **Health Recommendations:** Suggest preventive care based on age, history, local epidemiology

### Data Requirements:
- Minimum 50,000 anonymized patient records for model training
- High-quality labels (outcomes) from partner hospitals
- Ethics approval from NHRC for secondary use of data

### Revenue Model:
- Charge providers premium for AI insights module
- Potential B2B SaaS offering to hospital networks

### Privacy Safeguards:
- All models trained on de-identified data only
- No patient re-identification possible
- Regular bias audits (ensure recommendations don't discriminate by gender, caste, location)

---

# 7. Comprehensive Project Plan

## 7.1 Phase 1: Foundation & Compliance (Months 1-6)

**Objective:** Establish legal entity, secure partnerships, design architecture, and prepare for MVP development.

### Deliverables:

| **Deliverable** | **Owner** | **Month** | **Success Criteria** |
|---|---|---|---|
| **Legal Entity Setup** | Founder | 1 | Company registered, tax ID obtained |
| **Regulatory Scan** | Legal counsel | 1-2 | Complete Directive 2081 documentation reviewed |
| **SIL-Nepal Engagement** | Product | 1 | Kickoff meeting scheduled; preliminary FHIR profile submitted |
| **Data Center Partnership** | Ops | 1-2 | Nepal-IX or WorldLink colocation agreement signed |
| **FHIR Architecture Design** | Engineering | 1-3 | SIL-Nepal feedback incorporated; technical design doc approved |
| **Security Audit Planning** | Security | 2-3 | Audit firm selected; scope defined |
| **Academic Partnerships** | Product | 2-3 | MOU with Kathmandu University + Patan Academy signed |
| **Hospital Pilot Selection** | Product | 3-4 | Bayalpata + Gulmi Hospital agreements signed |
| **MVP Functional Spec** | Product | 3-4 | User stories written; wireframes completed |
| **Privacy Policy & DPIA** | Legal | 4-5 | Privacy impact assessment submitted to NHRC for ethics review |
| **Fundraising Round 1** | Founder | 4-6 | Pre-seed funding committed ($250K-500K) |
| **Development Team Hiring** | HR | 2-6 | 6-8 engineers, 1 designer, 1 QA hired |

### Budget:
- **Salaries:** $80,000 (6 engineers @ $12K/year, 1 designer @ $8K, 1 PM @ $10K)
- **Legal & Compliance:** $10,000
- **Data Center:** $3,000
- **Infrastructure & Tools:** $5,000
- **Total Phase 1 Budget:** $98,000

## 7.2 Phase 2: MVP Development (Months 7-12)

**Objective:** Build and test minimum viable PHR with core features; prepare for pilot deployment.

### Deliverables:

| **Feature** | **Owner** | **Month** | **Success Criteria** |
|---|---|---|---|
| **HAPI FHIR Server** | Backend Lead | 7-8 | Server deployed on Nepal-IX; endpoints tested |
| **Patient Mobile App** | Mobile Lead | 7-12 | iOS + Android apps built; offline-first tested |
| **Web Dashboard** | Frontend Lead | 8-11 | Patient portal deployed; all core features functional |
| **Authentication (Keycloak)** | Backend Lead | 7 | MFA working; role-based access controls implemented |
| **Prescription Module** | Full Team | 8-10 | Voice entry tested; prescription sync working |
| **Lab Results Import** | Backend Lead | 9-11 | FHIR LabResult resources integrated; display working |
| **Appointment Reminders** | Mobile Lead | 10-12 | Push notifications tested; reminder accuracy >95% |
| **Offline Sync** | Backend Lead | 10-12 | Tested in low-connectivity environment (simulated) |
| **UX Research** | Designer + PM | 8-12 | Low-literacy testing completed; design iterations done |
| **Internal Security Audit** | Security | 11-12 | Vulnerability scan completed; high-priority issues remediated |
| **Pilot Hospital Testing** | QA + Product | 11-12 | Alpha deployment at Bayalpata; 50 test users registered |

### Team Expansion:
- +3 engineers (backend, mobile, frontend)
- +1 QA engineer
- +1 DevOps engineer
- +1 Health Informatics consultant (FHIR expert)

### Budget:
- **Salaries (6-month run):** $80,000
- **Infrastructure (hosting, database, tools):** $8,000
- **Testing & QA tools:** $3,000
- **ASR API costs (Google Cloud):** $200
- **Total Phase 2 Budget:** $91,200

## 7.3 Phase 3: Security Audit & Launch (Months 13-18)

**Objective:** Harden security, obtain regulatory certification, and launch to public.

### Deliverables:

| **Deliverable** | **Owner** | **Month** | **Success Criteria** |
|---|---|---|---|
| **Third-Party Security Audit** | External firm | 13-15 | Audit complete; all high-priority findings remediated |
| **Penetration Testing** | External firm | 15 | Pen test report received; vulnerabilities fixed |
| **FHIR Conformance Certification** | SIL-Nepal | 14-16 | SIL-Nepal certifies FHIR compliance; registration submitted |
| **MoHP Directive 2081 Registration** | Legal + Product | 16 | System registered with MoHP; 12-month registration window opened |
| **Pilot Hospital Full Deployment** | Product + Ops | 13-15 | Bayalpata + Gulmi live; 1,000 users registered |
| **Insurance Integration (openIMIS)** | Backend Lead | 14-16 | openIMIS API tested; claim submission working (sandbox) |
| **Marketing Campaign Launch** | Marketing | 15-18 | Hospital referral, FCHV training, telecom bundles live |
| **Public App Store Release** | Mobile Lead | 17 | iOS App Store + Google Play Store live; app >4.0 rating |
| **Analytics & Monitoring** | DevOps | 16-18 | Dashboards showing user engagement, data quality, system health |
| **Data Privacy Certification** | Legal | 17 | Comply with Individual Privacy Act; certification obtained |

### Pilot Hospital Success Metrics:
- **User Registration:** 1,000 users by end of Phase 3
- **Data Import:** 80% of patient encounters imported from hospital EMRs
- **Engagement:** 40% of registered users log in monthly (30-day MAU)
- **Feature Usage:** 60% use at least one core feature (prescriptions, test results, appointments)

### Budget:
- **Salaries (6-month run):** $90,000 (team grows with launches)
- **Security audit + pen test:** $15,000
- **Marketing & user acquisition:** $40,000
- **App Store registration + infrastructure:** $5,000
- **Total Phase 3 Budget:** $150,000

## 7.4 Phase 4: Scale & Interoperability (Months 19-24)

**Objective:** Expand to additional hospitals, integrate with government health systems, and prepare for Year 2 features.

### Deliverables:

| **Deliverable** | **Owner** | **Month** | **Success Criteria** |
|---|---|---|---|
| **Multi-Hospital Rollout** | Product + Sales | 19-20 | 10+ hospitals integrated; 10,000 users by Month 20 |
| **FCHV Integration** | Product | 19-21 | 5,000+ FCHVs trained; 2,000+ users acquired from FCHV channel |
| **Caregiver Features** | Frontend Lead | 19-21 | Linked accounts, shared medications working; beta tested |
| **HMIS Integration** | Backend Lead | 20-22 | Aggregated, anonymized data flowing to HMIS; no re-identification risk |
| **Custom Nepali ASR Model** | ML Engineer | 21-24 | Custom model trained on 500+ hours of medical speech; accuracy 92%+ |
| **Wearable Device Integration** | Mobile Lead | 22-24 | Apple Health, Fitbit, Garmin devices supported; sync working |
| **AI Health Insights (Beta)** | ML Engineer + Product | 23-24 | Medication adherence predictions, risk scoring in beta |
| **Telecom Bundle Go-Live** | Product + Sales | 20-22 | Ncell/NTC bundle live; 2,500 users acquired |
| **Series A Fundraising** | Founder | 21-22 | Series A funding committed ($2M-3M) |
| **Year 2 Roadmap** | Product | 24 | Product strategy approved; team expanded to 20+ |

### Scale Targets by End of Phase 4 (Month 24):
- **Total Users:** 100,000+
- **Monthly Active Users (MAU):** 40,000+
- **Revenue:** $15,000/month (B2C + B2B)
- **Hospitals Integrated:** 25-30
- **Geographic Coverage:** 10+ districts

### Budget:
- **Salaries (6-month run, expanded team):** $120,000
- **Infrastructure scaling:** $15,000
- **Custom ML model development:** $40,000
- **Telecom partnership incentives:** $20,000
- **Total Phase 4 Budget:** $195,000

---

# 8. Revenue Model & Monetization Strategy

## 8.1 Primary Revenue Streams

### 1. B2C Subscription Plans (Direct Patient)

| **Plan** | **Price (NPR/month)** | **Features** | **Target User** |
|---|---|---|---|
| **Swasthya Basic** | Free | Core features (records, basic sharing) | Pilot users, basic engagement |
| **Swasthya Plus** | NPR 299 | Advanced features (reminders, caregiver access) | Health-conscious users |
| **Swasthya Premium** | NPR 499 | All features + priority support + health insights | Chronic disease patients |
| **Swasthya Family** | NPR 999 | 4 family members + shared health goals | Families, NRN diaspora |

**Estimated Conversion:** Free → Paid = 5% (conservative for Nepal market)

### 2. B2B Revenue (Healthcare Providers)

| **Customer** | **Service** | **Price (NPR/month)** | **TAM Estimate** |
|---|---|---|---|
| **Small Clinic** | Basic integration (5 providers, 200 patients) | NPR 2,000 | 1,000 clinics |
| **Medium Clinic** | Full integration (20 providers, 1,000 patients) | NPR 5,000 | 300 clinics |
| **Large Hospital** | Premium integration (100+ providers, 10K+ patients) | NPR 15,000 | 50 hospitals |

**Negotiation Model:** Hospitals often prefer revenue-share (collect payment from patient subscriptions, share 30% with PHR) vs. direct subscription.

### 3. Insurance Integration Revenue

| **Service** | **Price (NPR per transaction)** | **Volume (Year 1)** | **Revenue Potential** |
|---|---|---|---|
| **Claim validation** | NPR 10 per claim | 50,000 claims | NPR 500,000 |
| **Patient eligibility check** | NPR 2 per check | 100,000 checks | NPR 200,000 |
| **Claim status tracking API** | NPR 5 per query | 200,000 queries | NPR 1,000,000 |
| **Aggregate reporting (anonymized)** | NPR 50,000 per month | HIB + 3 insurance companies | NPR 200,000 |

### 4. Data & Analytics Revenue

| **Customer** | **Use Case** | **Price** | **Notes** |
|---|---|---|---|
| **Health Research** | Anonymized disease epidemiology | NPR 100K-500K per dataset | Requires NHRC ethics approval |
| **Pharma Companies** | Drug efficacy tracking (opt-in) | NPR 200K-1M per year | Privacy-first; only with patient consent |
| **Government (HMIS)** | Aggregated health trends | Free | Strategic relationship value |
| **Public Health NGOs** | Vaccination coverage, disease surveillance | NPR 50K-100K | Aligned with WHO objectives |

**Key Principle:** All data monetization is opt-in, anonymized, and transparent to patients.

## 8.2 Revenue Projections (Year 1–3)

### Revenue Model Assumptions:

| **Metric** | **Year 1** | **Year 2** | **Year 3** |
|---|---|---|---|
| **Total Users** | 10,000 | 50,000 | 150,000 |
| **Paying B2C Users (5% conversion)** | 500 | 2,500 | 7,500 |
| **Avg. B2C ARPU (mix of ₹299/$499/$999)** | NPR 450 | NPR 500 | NPR 550 |
| **B2C MRR** | NPR 2.25L | NPR 12.5L | NPR 41.25L |
| **Hospitals (paying B2B)** | 3 | 15 | 40 |
| **Avg B2B ARPU** | NPR 7,000 | NPR 8,000 | NPR 10,000 |
| **B2B MRR** | NPR 2.1L | NPR 12L | NPR 40L |
| **Insurance Transactions (claims + checks)** | NPR 25L | NPR 80L | NPR 150L |
| **Data/Analytics (conservative)** | NPR 5L | NPR 15L | NPR 40L |
| **Other (partnerships, API)** | NPR 3L | NPR 10L | NPR 30L |

### Projected Annual Revenue:

| **Year** | **B2C Subscriptions** | **B2B Subscriptions** | **Insurance** | **Data/Analytics** | **Other** | **Total Annual Revenue** |
|---|---|---|---|---|---|---|
| **Year 1** | NPR 27L | NPR 25L | NPR 25L | NPR 5L | NPR 3L | **NPR 85L** (~$6,500/mo) |
| **Year 2** | NPR 150L | NPR 144L | NPR 80L | NPR 15L | NPR 10L | **NPR 399L** (~$30,000/mo) |
| **Year 3** | NPR 495L | NPR 480L | NPR 150L | NPR 40L | NPR 30L | **NPR 1,195L** (~$90,000/mo) |

**Note:** This is a conservative model. If you achieve 10% B2C conversion + enterprise sales, Year 3 revenue could reach NPR 2M+ (~$15K/month).

## 8.3 Unit Economics Model (CAC, LTV, Churn, Conversion)

### Key Unit Economics Metrics:

| **Metric** | **Year 1** | **Year 2** | **Year 3** | **Notes** |
|---|---|---|---|---|
| **Customer Acquisition Cost (CAC)** | $2.72 blended | $2.50 | $2.00 | Decreases as brand grows |
| **Lifetime Value (LTV)** | $18 (per B2C user) | $25 | $35 | 24-month average lifetime |
| **LTV:CAC Ratio** | 6.6:1 | 10:1 | 17.5:1 | Healthy growth (>3:1 is good) |
| **Churn Rate (Monthly)** | 5% | 4% | 3% | Improves with engagement |
| **Avg. Subscription Duration** | 17 months | 25 months | 33 months | Inverse of churn |
| **B2C Conversion Rate** | 5% | 6% | 7% | Improves with engagement |
| **CAC Payback Period** | 3 months | 2.5 months | 2 months | Revenue recovers quickly |

### CAC Breakdown by Channel (Year 1):

| **Channel** | **Users Acquired** | **Cost per User** | **% of Total CAC** |
|---|---|---|---|
| **Hospital Referral** | 3,000 | $1.50 | 44% |
| **FCHV Network** | 2,000 | $2.65 | 20% |
| **Telecom Bundle** | 2,500 | $1.15 | 15% |
| **Social Media** | 1,500 | $6.00 | 12% |
| **NRN Diaspora** | 500 | $9.00 | 5% |
| **Word of Mouth** | 500 | $2.00 | 4% |
| **Blended CAC** | 10,000 | $2.72 | 100% |

**Sensitivity Analysis:**
- If hospital referral CAC increases to $3.00 (due to training costs), blended CAC increases to $3.15 (16% impact)
- If churn increases from 5% to 7% (lower engagement), LTV drops to $15 (17% impact)
- If B2C conversion stays at 3% (vs. 5% target), revenue drops 40% YoY

## 8.4 Unit Economics Model (CAC, LTV, Churn, Conversion)

### Detailed LTV Calculation:

**Assumptions:**
- Average monthly subscription: NPR 450 (~$3.50 USD)
- Contribution margin after payment processing: 85% (15% to payment gateway)
- Monthly contribution: NPR 382

**LTV Formula:**
```
LTV = (Average Subscription Price × Contribution Margin) / Monthly Churn Rate
LTV = (NPR 450 × 0.85) / 0.05 = NPR 7,650 (~$58 USD over lifetime)
```

**Adjusted LTV (accounting for upgrade/downgrade):**
- 5% of users upgrade from Basic ($0) to Plus ($299) by Month 6
- Average tenure before upgrade: 6 months
- Total LTV = NPR 7,650 + (5% × NPR 299 × 0.85 / 0.05) = NPR 12,285

## 8.5 Financial Sensitivity Analysis

**Scenario A: Best Case**
- B2C conversion: 8% (vs. 5% base)
- B2B hospital adoption: 50 hospitals (vs. 40 base)
- Churn: 2% (vs. 5% base)
- **Year 3 Revenue: NPR 1,800L (~$135K/month)**

**Scenario B: Worst Case**
- B2C conversion: 2% (vs. 5% base)
- B2B hospital adoption: 15 hospitals (vs. 40 base)
- Churn: 8% (vs. 5% base)
- **Year 3 Revenue: NPR 400L (~$30K/month)** — still sustainable

**Scenario C: Insurance Focus**
- Prioritize openIMIS integration and health insurance monetization
- Insurance transaction volume: 500K claims/year (vs. 200K base)
- Revenue increases: NPR 300L in Year 3 from insurance alone
- **Year 3 Revenue: NPR 1,400L (~$105K/month)**

## 8.6 Grant and Non-Dilutive Funding Strategy

### Grant Funding Opportunities:

| **Funder** | **Focus** | **Amount (USD)** | **Timeline** | **Application Requirements** |
|---|---|---|---|---|
| **WHO Innovation Fund** | Digital health in LDCs | $50K-200K | 6-month application | Regulatory alignment, proof of concept |
| **Global Fund** | Health systems strengthening | $100K-300K | 9-month application | Disease focus (TB, malaria), partnership model |
| **GAVI (Vaccine Alliance)** | Immunization tracking | $50K-150K | 6-month application | Vaccination module, integration with national programs |
| **Gates Foundation** | Maternal health, family planning | $100K-500K | 8-month application | Gender focus, equity metrics |
| **Mastercard Foundation** | Financial inclusion + health | $50K-200K | 6-month application | Microfinance partnership, poverty targeting |
| **Google AI for Social Good** | Healthcare AI | $25K-100K (cloud credits) | Rolling | AI/ML project, Nepal focus |

### Recommendation:
Apply for 3-4 grants in Year 1. Expect 30% approval rate. Even 1-2 approvals (100K-200K) materially improves runway and reduces need for dilutive equity fundraising.

### Non-Dilutive Alternatives:
- **Revenue-Based Financing:** Once hitting NPR 20L MRR (Month 9+), explore revenue-based loans (e.g., Silicon Valley Bank, Lighter Capital) at 6-8% of monthly revenue. No equity given up.
- **Partnership Revenue:** Telecom bundle deals with Ncell/NTC may include marketing co-investment (10K-30K per quarter)

## 8.7 Government Procurement Channel

### Opportunity:
The Government of Nepal is actively investing in digital health infrastructure. Your PHR could be procured as a national platform.

### Procurement Models:

**Model 1: Directive 2081 Validation Service**
- Government contracts PHR to validate third-party EMR systems' FHIR compliance
- Revenue: NPR 50K-100K per validation (50-100 validations/year) = NPR 25L-100L/year
- Positions you as government-endorsed standard

**Model 2: National PHR Pilot**
- Government funds pilot of your PHR across 5-10 districts
- Budget: $100K-300K per district-year
- You manage platform; government funds user acquisition + provider training
- Path to national rollout in Year 2-3

**Model 3: HMIS Integration Contract**
- Government contracts you to integrate all PHR data into national HMIS
- Revenue: NPR 20L-50L per year
- Strengthens alignment with government health information systems

### How to Access Government Procurement:
1. **Register with PPMO** (Public Procurement Monitoring Office) at ppmo.gov.np
2. **Obtain Tax Clearance Certificate** from IRD
3. **Submit bid** when RFP is issued (government publishes health IT RFPs quarterly)
4. **Partner with large contractor** (e.g., Deerwalk, Vairav) if you're too small to bid alone

---

# 9. Risk Assessment & Mitigation

## Key Risks and Mitigation Strategies:

| **Risk** | **Likelihood** | **Impact** | **Mitigation** |
|---|---|---|---|
| **Regulatory rejection** (MoHP denies registration) | Medium | Critical | Engage SIL-Nepal early; hire compliance consultant; monitor policy changes |
| **Data breach** (patient records leaked) | Low | Critical | Hire security officer Month 1; conduct regular audits; cyber insurance |
| **Competitor enters market** (Hamro Doctor launches PHR) | Medium | High | Accelerate hospital partnerships; deepen government relationships; differentiate on insurance integration |
| **Cloud localization enforcement** (strict enforcement of data residency) | Low | High | Already committed to Nepal-hosted data; no risk |
| **User adoption fails** (<1,000 users by Month 18) | Medium | High | Diversify acquisition channels; partner with FCHV network; bundle with telecom |
| **Hospital integration delays** (EMRs don't expose FHIR APIs) | Medium | Medium | Start with 2-3 pilot hospitals; build custom connectors as fallback |
| **Financial runway depletion** | Low | Medium | Raise pre-seed by Month 4; apply for grants; achieve revenue early (Month 9+) |
| **Nepali ASR accuracy insufficient** (>15% WER on medical speech) | Medium | Low | Keep Google Cloud fallback; don't commit to custom model until validated |

## 9.1 Liability and Medical Data Accuracy Strategy

**Critical Gap:** If PHR displays inaccurate or outdated medical information, and a patient makes a health decision based on it, who is liable?

### Legal Framework:

**Nepal does not yet have a clear medical app liability framework.** However, the following principles apply:

1. **Duty of Care:** Your app is providing health information; you have duty to ensure accuracy
2. **Data Accuracy:** If data comes from hospital EMR, hospital is responsible; you're just displaying
3. **Clinical Advice:** If you provide clinical advice (e.g., "skip this dose"), you become liable as a healthcare provider
4. **Informed Consent:** Users must understand the app is informational, not a replacement for doctor

### PHR Liability Mitigation:

| **Control** | **Implementation** |
|---|---|
| **Clear Disclaimer** | "Swasthya displays medical records from your healthcare providers. For clinical decisions, consult your doctor." (in-app, legal review required) |
| **Data Provenance** | Every record shows "Source: Bayalpata Hospital, Jan 15, 2025" so user knows source |
| **No Clinical Advice** | Don't provide treatment recommendations; only display data and guidelines |
| **Patient Validation** | If data hasn't been confirmed by provider, mark as "unconfirmed" |
| **Data Freshness Timestamps** | Show "Last updated: Jan 15, 2025 (10 days ago)" so user knows if stale |
| **Professional Review** | All health content (articles, FAQs, drug information) reviewed by Patan Academy doctors |
| **Cyber Insurance** | Obtain professional liability + cyber liability insurance ($50K-100K coverage) by Month 15 |
| **Incident Response** | Document any breaches or data accuracy issues; notify MoHP within 72 hours |

### Legal Documentation:

**By Month 5, prepare:**
1. **Terms of Service** (including liability disclaimers)
2. **Privacy Policy** (Individual Privacy Act compliance)
3. **Data Usage Agreement** (hospital partner template)
4. **Medical Disclaimer** (reviewed by Patan Academy + legal counsel)
5. **Incident Response Plan** (for breach notification)

**Have reviewed by:**
- Healthcare legal counsel (Nepal-based)
- Patan Academy of Health Sciences (medical accuracy)
- NHRC (ethics compliance)

---

# 10. Investment & Funding Roadmap

## 10.1 Capital Requirements by Stage

### Phase-by-Phase Funding:

| **Phase** | **Timeline** | **Capital Required (USD)** | **Primary Use** | **Funding Source** |
|---|---|---|---|---|
| **Phase 1** | Months 1-6 | $60K-80K | Salaries, legal, partnerships | Founder savings + angel investors (friends & family) |
| **Phase 2** | Months 7-12 | $80K-120K | Development team, MVP build | Pre-seed round ($200K-300K total) |
| **Phase 3** | Months 13-18 | $100K-150K | Security audit, marketing, launch | Seed round ($500K-800K total) |
| **Phase 4** | Months 19-24 | $150K-200K | Scaling, partnerships, features | Series A raise ($2M-3M) |
| **Total 24 Months** | **$390K-550K** | **~$450K mid-point** | | |

### Cumulative Burn Rate and Breakeven:

**Assumptions:**
- Average monthly burn: $18K (Phases 1-2), $25K (Phase 3), $35K (Phase 4)
- Revenue starts Month 9; grows $10K → $30K → $100K+ per month

| **Month** | **Cumulative Burn (USD)** | **Cumulative Revenue (USD)** | **Net Position (USD)** |
|---|---|---|---|
| 6 | $108K | $0 | -$108K (needs pre-seed funding) |
| 12 | $216K | $15K | -$201K (needs seed funding) |
| 18 | $375K | $60K | -$315K (breakeven in Month 20-22) |
| 24 | $540K | $150K | -$390K (but profitable unit economics; ready for Series A) |

**Key Milestone:** Achieve positive unit economics by Month 12 and breakeven by Month 22.

## 10.2 Nepal Investment Climate

### Active Investors in Nepal:

| **Investor** | **Focus** | **Typical Check Size (USD)** | **Stage** |
|---|---|---|---|
| **Dolma Impact Fund** | South Asian social enterprises | $100K-500K | Early stage |
| **Asian Venture Philanthropy Network** | Healthcare, education | $50K-200K | Pre-seed, seed |
| **NRN Angel Network** | Nepal diaspora investments | $10K-100K | Seed |
| **Verve Ventures** | South Asia tech | $100K-500K | Pre-seed, seed |
| **Techstars/accelerators** | Tech startups | $20K-150K | Accelerators |

### Current Investment Trends (2024-25):
- Growing interest in fintech and healthtech from institutional investors
- Government actively supporting startups through Startup Nepal initiative
- Development finance institutions (DFIs) like FMO (Netherlands) interested in Nepal healthcare
- GAVI, Gates Foundation increasingly funding health tech in Nepal

## 10.3 Target Investor Landscape

### Round 1: Pre-Seed ($200K-300K, Months 4-6)
- **Target:** Angel investors + early-stage VCs
- **Investors:** NRN angels, Dolma Impact Fund, Asian Venture Philanthropy Network
- **Pitch:** "Building Nepal's first regulatory-compliant PHR; first-mover in Directive 2081 market"
- **Use of Funds:** 50% salaries, 30% MVP development, 20% legal/compliance

### Round 2: Seed ($500K-800K, Months 12-15)
- **Target:** Seed-stage VCs, impact investors, DFIs
- **Investors:** Verve Ventures, Techstars alumni, healthcare-focused VCs
- **Pitch:** "Achieved 1,000 users in pilot; now scaling nationally; regulatory certification in progress"
- **Traction Metrics:** User metrics, hospital partnerships, insurance integration progress
- **Use of Funds:** 40% sales/marketing, 30% engineering, 20% compliance/security, 10% operations

### Round 3: Series A ($2M-3M, Months 21-24)
- **Target:** Growth-stage VCs, strategic investors
- **Investors:** Healthcare-focused VCs, Indian/international firms looking at Nepal, government agencies (indirect)
- **Pitch:** "Market leader in Nepal; expanding to India/Bangladesh; $100K MRR achieved; path to $1M ARR"
- **Traction Metrics:** Revenue, MAU growth, geographic expansion, enterprise partnerships
- **Use of Funds:** 40% scaling (sales, ops, product), 30% engineering, 15% compliance/security, 15% internationalization

---

# 11. Success Metrics & KPIs

## Product Metrics

| **Metric** | **Definition** | **Target (Month 18)** | **Target (Month 24)** |
|---|---|---|---|
| **Total Registered Users** | Unique accounts created | 10,000 | 100,000 |
| **Monthly Active Users (MAU)** | Users who logged in at least once in month | 4,000 (40% of total) | 40,000 (40% of total) |
| **DAU/MAU Ratio** | Daily active users / Monthly active users | 20% | 25% |
| **Average Session Duration** | Minutes spent in app per session | 3-5 min | 4-7 min |
| **Prescription Records Imported** | Unique prescriptions from hospital EMRs | 5,000 | 50,000 |
| **Lab Results Imported** | Unique lab results from hospital EMRs | 3,000 | 30,000 |
| **Voice Entries Created** | Prescriptions/appointments added via voice | 1,000 | 10,000 |
| **Features Adopted (% of users)** | % using at least one core feature | 60% | 75% |

## Business Metrics

| **Metric** | **Definition** | **Target (Month 18)** | **Target (Month 24)** |
|---|---|---|---|
| **B2C Paying Users** | Paying subscription users | 500 | 7,500 |
| **B2C MRR** | Monthly recurring revenue from subscriptions | NPR 2.25L | NPR 41.25L |
| **B2B Hospital Partners** | Hospitals integrated + paying | 3 | 40 |
| **B2B MRR** | Monthly recurring revenue from hospitals | NPR 2.1L | NPR 40L |
| **Total Monthly Revenue** | B2C + B2B + other | NPR 7L | NPR 120L |
| **Revenue Churn Rate** | % of MRR lost to cancellation | <3% | <2% |
| **Customer Acquisition Cost (CAC)** | Cost per user acquisition | $2.72 | $2.00 |
| **Lifetime Value (LTV)** | Total profit per user over lifetime | $18 | $35 |
| **LTV:CAC Ratio** | How many times CAC is lifetime profit | 6.6:1 | 17.5:1 |

## Operational Metrics

| **Metric** | **Definition** | **Target** |
|---|---|---|
| **System Uptime** | % of time PHR is operational | 99.5% |
| **Data Sync Success Rate** | % of hospital record imports that succeed | 95%+ |
| **Average Response Time** | API response time for user requests | <500ms (p95) |
| **Security Audit Score** | Third-party security audit rating | Pass all critical/high findings |
| **Regulatory Compliance** | Directive 2081 registration status | Registered by Month 16 |
| **Data Breach Incidents** | Number of unplanned data exposures | 0 |

## User Satisfaction Metrics

| **Metric** | **Definition** | **Target** |
|---|---|---|
| **Net Promoter Score (NPS)** | Would users recommend the app? | 40+ (considered good) |
| **App Store Rating** | Average star rating on iOS/Android | 4.0+ out of 5 |
| **User Retention (Month 1)** | % of new users still active after 30 days | 50%+ |
| **Feature Satisfaction** | % of users satisfied with key features | 70%+ |
| **Support Response Time** | Time to respond to user inquiry | <4 hours |

---

# 12. Strategic Partnerships & Ecosystem Engagement

## Academic Partners

| **Partner** | **Capability** | **Engagement Model** |
|-------------|---------------|---------------------|
| **Kathmandu University (Health Informatics)** | FHIR training, research collaboration | Student projects, joint grants |
| **Patan Academy of Health Sciences** | Medical terminology, clinical validation | Clinical advisory board |
| **IOE Pulchowk (Computer Engineering)** | ASR research, NLP | Research partnership, PhD supervision |
| **Nepal Health Research Council** | Ethics approval, research compliance | Study registration, ethical review |

## Implementation Partners

| **Partner** | **Role** | **Current Status** |
|-------------|----------|-------------------|
| **Bayalpata Hospital** | Pilot site | WHO-supported EMR, FHIR-capable |
| **Gulmi Hospital** | Pilot site | WHO-supported EMR |
| **Armed Police Force Hospital** | Reference implementation | Existing EMR, potential integration |
| **WHO Nepal** | Technical assistance | Digital health team engagement |
| **GIZ** | openIMIS support | Technical assistance for insurance integration |

## Technology Partners

| **Partner** | **Capability** | **Engagement** |
|-------------|---------------|----------------|
| **Google for Startups** | Cloud credits, ASR access | Apply for startup program |
| **Microsoft for Startups** | Azure credits, AI/ML tools | Apply for startup program |
| **CloudFactory Nepal** | Local hosting, data operations | Colocation, potential outsourcing |
| **Deerwalk Services** | Healthcare IT expertise | Consulting, development support |

---

# 13. Final Strategic Recommendations

### Immediate Actions (Next 30 Days)

1. **Download and Study the Full Directive**
   - Obtain official "Integrated EMR Operation and Management Directives, 2081" from MoHP
   - Review all 21 modules and technical specifications
   - Identify any additional requirements not covered in this report

2. **Engage SIL-Nepal**
   - Schedule introductory meeting with Standards and Interoperability Lab
   - Submit preliminary FHIR profile for feedback
   - Understand conformance testing requirements and timeline

3. **Secure Data Center Partnership**
   - Contact Nepal-IX or WorldLink Data Center for colocation options
   - Document data sovereignty compliance plan
   - Include in investor pitch and technical architecture

4. **Initiate Academic Partnerships**
   - Reach out to Kathmandu University Health Informatics Department
   - Explore joint research for Nepali medical ASR
   - Identify student interns or research assistants

5. **Refine Pilot Hospital Selection**
   - Confirm WHO-supported hospital status (Bayalpata, Gulmi)
   - Draft data sharing agreements
   - Plan pilot timeline and success criteria

### Medium-Term Priorities (Months 2-6)

6. **Adopt FHIR-First Architecture**
   - Set up HAPI FHIR server with PostgreSQL
   - Implement core FHIR resources (Patient, Condition, Encounter)
   - Begin SIL-Nepal alignment

7. **Voice ASR Phased Approach**
   - Start with Google Cloud Speech-to-Text for MVP
   - Begin collecting Nepali medical terminology corpus
   - Plan custom model development for Year 2

8. **Security-First Development**
   - Implement security controls from day one
   - Document everything for audit
   - Consider hiring security consultant early

9. **Fundraising Preparation**
   - Refine pitch deck with regulatory angle
   - Target NRN angel network and Dolma Impact
   - Prepare for pre-seed raise by Month 3

### Long-Term Vision

10. **Become Nepal's National PHR Standard**
    - Align with Universal Health ID when launched
    - Integrate with all openIMIS facilities
    - Contribute to HMIS and national health planning
    - Expand to neighboring markets (India, Bangladesh) after Nepal dominance

---

## Conclusion

Your project is strategically positioned at the intersection of:

- **Regulatory mandate** (Directive 2081 creating mandatory demand)
- **Technological readiness** (FHIR adoption, SIL-Nepal, 63% internet penetration, 160+ smartphones per 100)
- **Market need** (9M insured, fragmented care, patient demand for ownership)
- **Innovation gap** (No voice-enabled PHR in Nepali; no patient-owned record standard)
- **Financial viability** (Clear path to profitability; sustainable unit economics)

By executing with a **compliance-first architecture**, **phased ASR development**, **multi-channel user acquisition**, **insurance integration**, and **strategic partnerships** with government, academic, and implementation partners, this PHR can become the trusted standard for patient-owned health records in Nepal.

The window for registration under Directive 2081 is open—**immediate action** is required to secure your position in this rapidly evolving landscape.

---

# Appendices

## Appendix A: Key Contacts and Resources

| **Resource** | **Contact** | **Purpose** |
|--------------|------------|-------------|
| Ministry of Health and Population | https://mohp.gov.np | Regulatory guidance |
| SIL-Nepal | sil@mohp.gov.np | FHIR conformance testing |
| Department of Health Services (IHIMS) | https://dohs.gov.np | HMIS alignment |
| Health Insurance Board | https://hib.gov.np | openIMIS integration |
| Kathmandu University (Health Informatics) | https://ku.edu.np/hi | Academic partnership |
| WHO Nepal | https://nepalwho.org | Technical assistance |
| NTA (Nepal Telecom Authority) | nta@nta.gov.np | Market data verification |
| Nepal Rastra Bank | https://nrb.org.np | Digital payment data |

## Appendix B: Abbreviations

| **Abbreviation** | **Full Form** |
|------------------|---------------|
| ASR | Automatic Speech Recognition |
| CAC | Customer Acquisition Cost |
| CBS | Central Bureau of Statistics |
| DAU | Daily Active Users |
| DFIA | Development Finance Institution |
| DHIS2 | District Health Information System 2 |
| DoHS | Department of Health Services |
| EMR | Electronic Medical Record |
| FCHV | Female Community Health Volunteer |
| FHIR | Fast Healthcare Interoperability Resources |
| HMIS | Health Management Information System |
| HIB | Health Insurance Board |
| IHIMS | Integrated Health Information Management Section |
| LIS | Laboratory Information System |
| LTV | Lifetime Value |
| MAU | Monthly Active Users |
| MoHP | Ministry of Health and Population |
| MOU | Memorandum of Understanding |
| MVP | Minimum Viable Product |
| NID | National Identity Card |
| NRN | Non-Resident Nepali |
| NTA | Nepal Telecom Authority |
| openIMIS | open Insurance Management Information System |
| PHR | Personal Health Record |
| RBAC | Role-Based Access Control |
| SIL | Standards and Interoperability Lab |
| SNOMED-CT | Systematized Nomenclature of Medicine Clinical Terms |
| TAM | Total Addressable Market |
| UX | User Experience |
| WAF | Web Application Firewall |
| WHO | World Health Organization |

---
