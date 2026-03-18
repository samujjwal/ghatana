# PHR Nepal Application — Comprehensive Feature List

**Document Version:** 3.0 (Enhanced with Global Best Practices, Innovation Features, and Compliance Hardening)  
**Date:** January 2026  
**Status:** Compact feature inventory aligned to the current MVP and Phase 2 rollout

| Field              | Value                                                                                                                                                                              |
| ------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Document Owner** | PHR Platform Lead                                                                                                                                                                  |
| **Classification** | Internal — Restricted                                                                                                                                                              |
| **Last Review**    | 2026-01-19                                                                                                                                                                         |
| **Next Review**    | 2026-04-19 (Quarterly)                                                                                                                                                             |
| **Companion Docs** | [E2E Requirements](phr-e2e-requirements.md), [Traceability Matrix](../01_governance/phr_requirements_traceability_matrix.md), [Consolidated Report](phr-consolidated-report-v2.md) |

> **📌 What changed in v3.0:** Added Section 12 (Emergency & Safety Features), Section 13 (FCHV & Community Health Features), Section 14 (Nepal Innovation Features), expanded Compliance Matrix with global standards, added Global PHR Feature Comparison table.

> Scope note:
> Core MVP covers patient profile, records, medications, appointments, documents, consent, audit, and insurance coverage/eligibility.
> OCR/voice input, caregiver/dependent flows, generalized offline sync for approved workflows, payment flows, referrals, imaging viewer, and mobile/web/desktop delivery are Core MVP commitments. Telemedicine and claims remain Phase 2 items in the current rollout.

---

## Table of Contents

1. [Core Health Record Features](#1-core-health-record-features)
2. [Telemedicine & Remote Care](#2-telemedicine--remote-care)
3. [Voice & Audio Features](#3-voice--audio-features)
4. [Clinical Integration Features](#4-clinical-integration-features)
5. [Insurance & Billing Features](#5-insurance--billing-features)
6. [Notification & Reminder Features](#6-notification--reminder-features)
7. [Security & Compliance Features](#7-security--compliance-features)
8. [Data Management & Analytics](#8-data-management--analytics)
9. [User Experience & Accessibility](#9-user-experience--accessibility)
10. [Advanced Features & Integrations](#10-advanced-features--integrations)
11. [Caregiver & Family Features](#11-caregiver--family-features)

---

## 1. Core Health Record Features

### 1.1 Patient Profile & Registration

- **Patient registration and enrollment** with NID/citizenship verification
- **Personal profile management** (name, DOB, gender, blood type, emergency contacts)
- **Profile photo and digital identity** management
- **Multiple profile support** (personal + family members)
- **Donor registry** (blood donor status, organ donation preferences)
- **Medical allergies and adverse reactions** documentation
- **Medical conditions and chronic disease tracking** (diabetes, hypertension, etc.)
- **Medication allergies with severity levels** (mild, moderate, severe)
- **Genetic/family history** recording
- **Vaccination history** with immunization records and schedule

### 1.2 Medical Records Management

- **Complete medical history** with searchable archive
- **Visit records** from multiple healthcare providers
- **Encounter documentation** (date, facility, provider, reason for visit)
- **Clinical notes and medical observations** (searchable)
- **Diagnoses and conditions** with ICD-10 coding
- **Current medication list** with active prescriptions
- **Medication history** with historical fill dates
- **Prescription refill requests** and tracking
- **Lab test results** with reference ranges and interpretations
- **Test history and trends** (visual charts)
- **Imaging and radiology records** with DICOM support
- **Surgical history** with operation details and outcomes
- **Discharge summaries** from hospital admissions
- **Outpatient procedure records**
- **Pathology and specimen tracking** (where applicable)

### 1.3 Record Import & Data Integration

- **Automated EHR/EMR data import** from partnering hospitals (FHIR-compliant)
- **Bulk upload capability** (CSV, PDF medical records)
- **Barcode scanning** of physical medical documents
- **OCR (Optical Character Recognition)** to digitize paper records
- **QR code generation** for medical documents
- **FHIR API integration** with health facilities
- **Interoperability layer** for receiving data from all 21 EMR modules
- **Data reconciliation** when receiving records from multiple sources
- **Version history** of imported records

---

## 2. Telemedicine & Remote Care (Phase 2)

### 2.1 Video Consultation Features

- **Schedule virtual appointments** with healthcare providers
- **Real-time video consultation** (1-on-1 with provider)
- **Screen sharing** for provider to review records during call
- **Video recording and playback** (with consent)
- **Session recording storage** in encrypted format
- **Consultation notes generation** after video call
- **Prescription issuance** during video consultation
- **Follow-up appointment scheduling** post-consultation
- **Provider directory and ratings** (with profile verification)
- **Video call quality adaptive bitrate** (for low-bandwidth areas)

### 2.2 Audio Consultation Features

- **Audio-only consultation option** for lower bandwidth areas
- **Call scheduling and reminders**
- **Real-time transcription of audio calls** (optional, with consent)
- **Call recording** (with explicit provider consent)
- **Audio message exchange** between patient and provider
- **Asynchronous voice messaging** for non-urgent queries

### 2.3 Consultation Management

- **Consultation history and archive**
- **Provider information and credentials** display
- **Availability calendar** of providers
- **Consultation duration tracking**
- **Consultation type selection** (video, audio, in-person booking)
- **Consultation outcomes documentation**
- **Multi-language consultation support**

---

## 3. Voice & Audio Features (MVP Baseline)

### 3.1 Audio Transcription

- **Real-time speech-to-text** during audio consultations
- **Automatic audio transcription** for clinical notes
- **Medical dictation support** (provider records notes by voice)
- **Nepali language transcription** with medical terminology
- **English language transcription** option
- **Transcript editing and correction** interface
- **Confidence scoring** for transcription accuracy
- **Redaction of sensitive information** from transcripts
- **Searchable transcript archive**
- **Export transcripts** as text or PDF

### 3.2 Voice Input for Record Creation

- **Voice-based clinical note entry** by healthcare providers
- **Voice prescription entry** (spoken → digital prescription)
- **Voice-to-structured data** (vital signs, measurements spoken aloud)
- **Medication instruction recording** via voice
- **Patient self-report via voice** (symptoms, wellness logs)
- **Voice queries** (patient asking about their health data)
- **Hands-free operation** for provider data entry
- **Background noise filtering** and audio enhancement

### 3.3 Medical Speech Recognition (ASR)

- **Vosk on-premise ASR (Apache 2.0)** integration (MVP baseline)
- **Custom Nepali medical terminology corpus** development
- **Custom medical ASR model** training (Year 2+)
- **Support for medical abbreviations** (BP, HR, SpO2, etc.)
- **Multi-speaker recognition** (patient + provider in same call)
- **Accent and dialect adaptation** (Newari, Maithili, etc.)
- **Integration with Kathmandu University ASR research**

### 3.4 Voice Features for Accessibility

- **Voice-based app navigation** (hands-free for elderly/disabled)
- **Audio playback of medical records** (screen reader integration)
- **Verbal appointments reminders**
- **Verbal medication reminders**
- **Voice confirmation for critical actions** (data deletion, form submission)

---

## 4. Clinical Integration Features

### 4.1 Laboratory Information Integration

- **Lab test order placement** (from provider)
- **Lab test result receipt** and storage
- **Lab result interpretation** with reference ranges
- **Historical lab trend visualization** (graphs and charts)
- **Abnormal result alerts** and notifications
- **Lab facility directory** (accredited labs in Nepal)
- **Home sample collection booking** (where available)
- **Lab report PDF import** and OCR
- **LIS (Laboratory Information System) integration** with partner labs

### 4.2 Pharmacy Integration

- **Prescription delivery tracking**
- **Medication availability checking** across pharmacies
- **Prescription transfer** between pharmacies
- **Medication inventory** (what's in stock at nearby pharmacies)
- **Medication pricing comparison**
- **Pharmacy location finder** (GPS-based)
- **Pharmacist consultation** (chat/video)
- **Medication side effects and interactions** checking
- **Generic alternative suggestions**
- **Pharmacy partner network** across Nepal

### 4.3 Radiology & Imaging

- **Radiology order placement**
- **Imaging appointment scheduling**
- **DICOM image storage** (X-ray, CT, MRI, ultrasound)
- **DICOM viewer** with zoom and rotation
- **Radiology report attachment** to images
- **Imaging history timeline**
- **Comparison imaging** (before/after views)
- **Imaging facility directory**

### 4.4 Specialist Referral Management

- **Referral request generation** by primary provider
- **Referral status tracking**
- **Specialist search and booking**
- **Referral document sharing** with specialists
- **Feedback from specialists** to referring doctor
- **Referral outcome documentation**
- **Cross-facility referral tracking** (inter-hospital)

### 4.5 Emergency & Critical Information

- **Emergency contact storage** (phone numbers)
- **Emergency medical information** (blood type, allergies, advance directives)
- **Emergency access code** (for first responders)
- **Critical medication list** (life-saving drugs)
- **Do Not Resuscitate (DNR) preferences** documentation
- **Organ donation status**
- **Advanced care preferences** (living will equivalent)
- **Emergency facility list** (hospitals, ambulance services)

---

## 5. Insurance & Billing Features

### 5.1 Insurance Integration

- **Social health insurance enrollment** verification
- **Insurance eligibility checking** (real-time)
- **Insurance card storage** (digital insurance ID)
- **Coverage details display** (what's covered)
- **Claim submission tracking**
- **Claim status monitoring** (submitted, approved, rejected, paid)
- **Insurance claim history** archive
- **Premium payment status**
- **Coverage period tracking**
- **Dependent management** (under family plan)
- **openIMIS integration** for claim processing

### 5.2 Billing & Payment

- **Medical bill storage** from facilities
- **Payment receipt tracking**
- **Out-of-pocket expense tracking**
- **Expense categorization** (doctor visit, lab, pharmacy, etc.)
- **Monthly/annual expense summaries**
- **Cost analysis** by provider/facility
- **Insurance reimbursement tracking**
- **Multiple payment method support** (digital wallets, bank transfer)
- **Bill splitting** (insurance + out-of-pocket)
- **Itemized billing breakdown**

### 5.3 Financial Analytics

- **Healthcare spending dashboard**
- **Annual medical expense report** (tax deduction purposes)
- **Family health spending trends**
- **Budget forecasting** for anticipated medical expenses
- **Cost comparison** across facilities/services
- **Insurance vs out-of-pocket breakdown**
- **Reimbursement rates display** (what insurance pays)

---

## 6. Notification & Reminder Features

### 6.1 Appointment Reminders

- **Appointment scheduling** (at any facility or provider)
- **Appointment confirmation** (SMS + app notification)
- **Reminder before appointment** (24 hours, 2 hours)
- **Location and directions** to appointment venue
- **Reschedule/cancel option** via notification
- **No-show tracking** and warnings
- **Telehealth appointment reminders**
- **Provider's calendar access** for appointment sync

### 6.2 Medication Reminders

- **Medication schedule setup** by patient or provider
- **Daily medication reminders** (push notification + SMS)
- **Medication adherence tracking**
- **Medication refill reminders** (7 days before running out)
- **Dosage and timing alerts**
- **Drug-drug interaction warnings**
- **Medication effectiveness check-in** (how are you feeling?)
- **Custom reminder timing** (morning, afternoon, evening)
- **Medication log** (did you take it?)

### 6.3 Health Alerts

- **Abnormal lab result alerts**
- **Blood pressure out of range** alerts
- **Chronic disease management** alerts (diabetes, hypertension)
- **Preventive care due alerts** (vaccination, screening)
- **Medication allergy alerts** (provider is prescribing something you're allergic to)
- **Drug interaction warnings**
- **Expiring prescription alerts**
- **Insurance coverage expiration alerts**

### 6.4 Preventive Care Reminders

- **Annual health checkup** reminders
- **Cancer screening reminders** (age-appropriate)
- **Vaccination schedule reminders**
- **Weight management** tips and reminders
- **Eye exam reminders**
- **Dental checkup reminders**
- **Mental health check-in** reminders

---

## 7. Security & Compliance Features

### 7.1 Authentication & Access Control

- **Multi-factor authentication (MFA)** for user login
  - OTP via SMS
  - Biometric (fingerprint, face recognition)
  - Hardware security key support
- **Role-based access control (RBAC)**
  - Patient role (own records only)
  - Provider role (assigned patient records)
  - Admin role (system management)
  - Caregiver role (family member records)
- **Session management** and timeout
- **Device recognition** and trusted device list
- **Login history** and suspicious activity alerts
- **Password policy enforcement** (strength, expiration)
- **Biometric unlock** option

### 7.2 Data Encryption & Protection

- **AES-256 encryption** for data at rest
- **TLS 1.3 encryption** for data in transit
- **End-to-end encryption** for sensitive consultations
- **Database encryption** with key management
- **File-level encryption** for uploads
- **Backup encryption** (encrypted backups only)
- **Key rotation** policy implementation
- **Secure deletion** (when data needs to be removed)

### 7.3 Audit Logging & Compliance

- **Tamper-proof audit logs** of all data access
- **Audit trail entry fields:**
  - Timestamp
  - User ID
  - IP address
  - Action performed
  - Record accessed
  - Result (success/failure)
- **Immutable logging** (logs cannot be modified)
- **Access logging** for sensitive records
- **Data modification tracking** (who changed what, when)
- **Export audit logs** for compliance review
- **Regulatory audit readiness**

### 7.4 Data Sovereignty & Localization

- **On-premise data storage** within Nepal
- **No cloud storage outside Nepal** borders
- **Colocation at Nepal-IX** (primary)
- **Secondary data center backup** (within Nepal)
- **Data residency certification**
- **Compliance with Nepal government requirements**
- **Disaster recovery plan** (within Nepal infrastructure)
- **Data center access controls**

### 7.5 Privacy & Consent Management

- **Granular consent management**
  - Consent for each provider/organization accessing data
  - Consent for data sharing with family members
  - Consent for specific data types (lab results vs notes)
  - Time-limited consent (revocation capability)
- **Privacy policy** in Nepali and English
- **Transparent data usage** disclosure
- **User data deletion request** (right to be forgotten)
- **Data export capability** (user can download all data)
- **Consent withdrawal** at any time
- **Activity log visibility** (see who accessed your data)

### 7.6 Regulatory Compliance

- **Privacy Act 2075 (2018)** compliance
- **Health Insurance Board (HIB)** openIMIS compliance
- **Integrated EMR Directive 2081** compliance
- **FHIR standard** adherence (Module 20)
- **SIL-Nepal conformance testing** ready
- **Third-party security audit** (annual)
- **Penetration testing** (annual by certified firm)
- **Incident response plan** (72-hour breach notification)
- **Data breach insurance**

---

## 8. Data Management & Analytics

### 8.1 Personal Health Analytics

- **Health dashboard** with key metrics
- **BMI tracking** (weight, height trends)
- **Blood pressure trends** (visualization over time)
- **Blood sugar trends** (for diabetic users)
- **Cholesterol trends**
- **Wellness score** (composite health indicator)
- **Activity logging** (integration with fitness trackers)
- **Sleep tracking** (if wearable integrated)
- **Stress and mental health tracking**
- **Health timeline** (major events visualization)

### 8.2 Medical History Search & Organization

- **Full-text search** across all medical records
- **Timeline view** of all medical events
- **Chronological organization** by date
- **Condition-based organization** (group records by disease)
- **Provider-based organization** (group by doctor/facility)
- **Facility-based organization** (group by hospital)
- **Tag and categorize** records manually
- **Advanced filters** (date range, record type, etc.)
- **Quick access to recent records**

### 8.3 Data Export & Sharing

- **Export records as PDF** (formatted for printing)
- **Export records as FHIR JSON** (for providers)
- **Export summary report** (for insurance)
- **Export visit history** (CSV format)
- **QR code sharing** of selected records
- **Share specific records** with providers/family
- **Time-limited sharing links** (expiring share URLs)
- **Print-friendly format** for medical records
- **Bulk export** of all records

### 8.4 Data Visualization & Insights

- **Disease trend charts** (visualization of chronic conditions)
- **Medication timeline** (what you took, when)
- **Lab result graphs** (trending values over time)
- **Spending analytics** (healthcare cost visualization)
- **Health insights** (AI-generated recommendations — Phase 4)
- **Risk scoring** (cardiovascular risk, diabetes risk, etc. — Phase 4)

---

## 9. User Experience & Accessibility

### 9.1 Multilingual Support

- **Nepali interface** (primary language)
- **English interface** (for international users/providers)
- **Maithili support** (dialect support for eastern Nepal)
- **Newari support** (dialect for Kathmandu Valley)
- **Language preferences** (user selectable)
- **Nepali medical terminology** database
- **Right-to-left text support** (where applicable)

### 9.2 Accessibility Features

- **Screen reader compatibility** (WCAG 2.2 AA compliance)
- **High contrast mode** (for visually impaired users)
- **Text size adjustment** (large font option)
- **Voice-based navigation** (hands-free)
- **Color-blind friendly design** (accessible color palettes)
- **Keyboard-only navigation** (no mouse required)
- **Closed captions** for video consultations
- **Audio descriptions** of visual content
- **Simple language option** (lower health literacy users)

### 9.3 Health Literacy Design

- **Plain language** explanations of medical terms
- **Medical term glossary** (built-in definitions)
- **Visual health literacy aids** (illustrations, icons)
- **Guided tutorials** for first-time users
- **Context-sensitive help** (tooltips, explanations)
- **Video education modules** (health topics)
- **FAQ section** for common questions
- **Illustrated medication instructions**

### 9.4 Design for Elderly & Vulnerable Users

- **Large, easy-to-tap buttons**
- **Simple, uncluttered interface**
- **Reduced animation** (minimalist design)
- **High-contrast text**
- **Clear, friendly language**
- **Step-by-step guided workflows**
- **Caregiver support mode** (see next section)
- **Phone number for customer support** (not just chat)

### 9.5 Offline Capability

- **Offline-first architecture** (work without internet)
- **Offline record access** (medical history cached locally)
- **Offline medication reminders**
- **Offline appointment viewing**
- **Queue for upload** when connectivity returns
- **Sync queue management** (retry failed uploads)
- **Data integrity checks** post-sync
- **Offline mode indicator**

---

## 10. Advanced Features & Integrations

### 10.1 Wearable Device Integration

- **Smartwatch health data sync** (Apple Watch, Wear OS)
- **Fitness tracker data** (steps, exercise, calories)
- **Health metrics from wearables** (heart rate, SpO2, temperature)
- **Automatic vital signs** capture from wearables
- **Data normalization** from multiple wearable sources
- **Continuous monitoring** data storage and visualization
- **Anomaly detection** from wearable data (Phase 4)

### 10.2 IoT Health Devices

- **Blood pressure monitor sync** (Bluetooth)
- **Glucose meter sync** (for diabetics)
- **Thermometer integration**
- **Weight scale sync** (smart scales)
- **ECG device integration** (where available)
- **Pulse oximeter** (SpO2 tracking)
- **Automatic reading recording** (no manual entry)
- **Device pairing and management**

### 10.3 FCHV (Female Community Health Volunteer) Integration

- **FCHV data entry portal** (for health workers)
- **FCHV training** on PHR data entry
- **Community health worker dashboard**
- **Patient registration** by FCHV
- **Referral tracking** from FCHVs
- **FCHV incentive tracking** (commission on active registrations)
- **SMS-based reporting** (for low-literacy FCHVs)
- **Data accuracy validation** by FCHVs

### 10.4 AI/ML Health Insights (Phase 4+)

- **Disease risk scoring** (predictive models)
- **Medication interaction warnings** (AI-enhanced)
- **Health trend predictions** (future health status)
- **Preventive care recommendations** (personalized)
- **Provider recommendation** (based on condition)
- **Treatment outcome predictions**
- **Chronic disease management suggestions**
- **Lifestyle recommendations** (diet, exercise)

### 10.5 Provider Directory & Reputation

- **Provider search** (doctor, facility, specialist)
- **Provider credentials** display and verification
- **Patient ratings and reviews** (anonymized)
- **Availability calendar** (real-time)
- **Consultation pricing** (transparent)
- **Specialization and qualifications** display
- **Hospital/clinic affiliations** listing
- **Opening hours** and location

### 10.6 Public Health Integration

- **Disease surveillance reporting** (for health ministry)
- **Epidemiological data** (anonymized, aggregated)
- **Outbreak alerts** (if disease detected)
- **Vaccination coverage reporting** (national health stats)
- **Health statistics** contribution (with consent)
- **Research participation** (clinical trials, studies)

---

## 11. Caregiver & Family Features (MVP baseline)

### 11.1 Multi-User Family Profiles

- **Family account setup** (primary + dependent profiles)
- **Family plan pricing** (discount for multiple users)
- **Shared family health hub**
- **Family health calendar** (all appointments visible)
- **Family medication list** (all medications tracked)
- **Child health tracking** (pediatric-specific)
- **Elderly parent health tracking** (parent monitoring)
- **Emergency contact sharing** within family
- **Family health summary** (monthly report)

### 11.2 Caregiver Role & Permissions

- **Caregiver profile creation** (parent, spouse, adult child)
- **Limited access permissions** (read-only or edit)
- **Data sharing consent** (what caregiver can see)
- **Activity notifications** (caregiver gets alerts)
- **Medication administration tracking** (caregiver logs doses)
- **Appointment coordination** (caregiver scheduling)
- **Emergency override** (caregiver can access in emergency)
- **Caregiver communication** channel (in-app messaging)

### 11.3 Elderly & Dependent Care

- **Simplified interface** for elderly users
- **Large text and buttons** for accessibility
- **Medication reminders** (verbal and visual)
- **Appointment reminders** (verbal and visual)
- **Emergency SOS button** (quick access to contacts)
- **Health status check-in** (caregiver initiated)
- **Activity monitoring** (passive, non-invasive)
- **Fall risk assessment** (for elderly users)

### 11.4 Parent-Child Health Management

- **Child growth tracking** (height, weight percentiles)
- **Immunization schedule** (childhood vaccines)
- **Developmental milestones** (tracking)
- **Pediatric allergy tracking**
- **School health records** storage
- **Pediatric visit history**
- **Parenting health tips** (age-appropriate)
- **Emergency pediatric contacts** (nearest children's hospital)

---

## Implementation Priority Matrix

### Phase 1 (MVP — Months 1-6): Foundation

**MUST HAVE:**

- Patient registration and profile management
- Medical record import/upload capability
- Medication and prescription tracking
- Lab test result storage
- Caregiver/dependent delegated access
- Offline sync for approved MVP workflows
- Appointment scheduling and reminders
- Document upload and retrieval
- Insurance eligibility checking
- Billing and digital payment flows
- Referral tracking baseline
- Imaging viewer baseline
- Consent and access control
- Authentication and access control
- Basic security features (encryption, audit logs)

### Phase 2 (Months 7-12): Core Features

**SHOULD HAVE:**

- Telemedicine video consultation
- Audio transcription (basic)
- Insurance claim submission
- Health analytics dashboard
- Family health hub analytics
- Advanced offline automation and recovery tooling
- FHIR API integration
- Advanced search and filters

### Phase 3 (Months 13-18): Extended Features

**NICE TO HAVE:**

- Custom medical ASR model
- Wearable device integration
- FCHV data entry portal
- Advanced health insights
- AI-driven recommendations
- Multi-dialect support
- Provider directory and ratings

### Phase 4 (Months 19-24): Advanced & AI Features

**FUTURE STATE:**

- Predictive health modeling
- Personalized health recommendations
- IoT device ecosystem
- Public health integration
- Research data contribution
- Advanced AI/ML analytics
- Regional expansion capabilities

---

## Feature Compliance Matrix

| Feature Category | EMR Directive 2081 Module    | HIB/openIMIS Compliant | FHIR Compatible | Security Audit Ready | Global Standard                  |
| ---------------- | ---------------------------- | ---------------------- | --------------- | -------------------- | -------------------------------- |
| Patient Profile  | 01 — Online Registration     | ✅ Yes                 | ✅ Yes          | ✅ Yes               | FHIR R4 Patient, ABDM ABHA model |
| Medical Records  | 02 — Clinical Documentation  | ✅ Yes                 | ✅ Partial      | ✅ Yes               | FHIR R4, ICD-10, LOINC           |
| Prescriptions    | 03 — Prescription Management | ✅ Yes                 | ✅ Yes          | ✅ Yes               | FHIR R4 MedicationRequest        |
| Lab Results      | 04 — Laboratory Integration  | ✅ Yes                 | ✅ Yes          | ✅ Yes               | FHIR R4 Observation, LOINC       |
| Billing          | 05 — Billing/Financial       | ✅ Yes                 | ✅ Yes          | ✅ Yes               | openIMIS IG, FHIR Claim          |
| Notifications    | 06 — Notifications/Alerts    | ✅ Yes                 | ✅ Yes          | ✅ Yes               | —                                |
| Telemedicine     | 17 — Telemedicine            | ✅ Yes                 | ✅ Yes          | ✅ Yes               | eSanjeevani model                |
| Mobile App       | 19 — Mobile Application      | ✅ Yes                 | ✅ Yes          | ✅ Yes               | WCAG 2.2 AA                      |
| Interoperability | 20 — Interoperability Layer  | ✅ Yes                 | ✅ Yes          | ✅ Yes               | FHIR R4, SIL-Nepal               |
| Security/Audit   | 21 — Security & Audit        | ✅ Yes                 | ✅ Yes          | ✅ Yes               | OWASP Top 10, ISO 27001          |
| Emergency QR     | — (new)                      | ✅ Yes                 | ✅ Yes          | ✅ Yes               | Australia MHR emergency          |
| FCHV Integration | — (new)                      | ✅ Yes                 | ✅ Yes          | ✅ Yes               | Rwanda CHW model, WHO SMART      |
| Caregiver/Family | — (new)                      | ✅ Yes                 | ✅ Yes          | ✅ Yes               | UK NHS Proxy Access              |

---

## 12. Emergency & Safety Features (Core MVP)

### 12.1 Emergency QR Health Card

- **Printable QR code** encoding blood type, known allergies, active medications, emergency contacts
- **Universal scanning** — any smartphone camera can display emergency summary (no app required)
- **Privacy-preserving** — only life-critical data exposed; patient controls included fields
- **Auto-refresh** — QR regenerated when underlying data changes
- **Physical card template** — printable credit-card-size format for wallet carry

**Global precedent:** Australia My Health Record emergency function, India ABHA health card, Estonia break-the-glass emergency access.

### 12.2 Emergency Access Protocol

- **Break-the-glass** provider access for unconscious/incapacitated patients
- **Mandatory post-hoc audit** — all emergency access is logged with justification required within 24 hours
- **Patient notification** — automated alert to patient when emergency access is used
- **Time-limited** — emergency access expires after 4 hours; must be re-authorized

### 12.3 Adverse Event Reporting

- **Patient-reported adverse drug reactions** with severity classification
- **Auto-alert to prescribing provider** when reaction reported
- **Integration path** to national pharmacovigilance system (future)

---

## 13. FCHV & Community Health Features (Phase 2)

### 13.1 FCHV Registration Flow

- **Simplified icon-based registration** for patients during home visits
- **Voice-assisted data entry** for basic health data (vitals, symptoms)
- **QR-based patient registration** — scan NID or PHR QR to link patient
- **Offline-capable** — data stored locally, synced when connectivity available

### 13.2 FCHV Health Data Collection

- **Basic vital signs** (blood pressure, temperature, weight, pulse)
- **Symptom checklist** with icon-based input
- **Medication adherence tracking** (yes/no with date)
- **Referral trigger** — auto-generate referral when danger signs detected
- **Photo capture** for wound/rash documentation (store-and-forward)

### 13.3 FCHV Access Control

- **Restricted role** — FCHV cannot view full medical history
- **Sees only own-collected data** plus aggregate health status indicators
- **Audit trail** — all FCHV data collection is logged with GPS coordinates
- **Supervised access** — health post supervisor can review FCHV-collected data

**Global precedent:** Rwanda's 55,000 CHW tablet program, Kenya community health digitization, WHO SMART Guidelines for community health workers.

---

## 14. Nepal Innovation Features

### 14.1 Nepali Medical ASR (Core MVP → Phase 2)

- **Voice search** for medical terms in Nepali (MVP: basic dictation)
- **Medical vocabulary overlay** — custom Nepali medical terminology model
- **Accuracy target:** ≥ 85% on top 500 medical terms by 6 months post-launch
- **Continuous improvement** — correction data from providers refines the model
- **Multi-dialect support** (Phase 3): Maithili, Newari, Tharu

**Competitive moat:** No competitor offers Nepali medical ASR. First-to-market advantage.

### 14.2 NRN Health Corridor (Phase 2)

- **Cross-timezone caregiver notifications** (Qatar, Saudi, Malaysia, UK time zones)
- **NRN can view parent health summaries**, manage appointments, receive alerts
- **Time-bounded delegation** — NRN caregiver access has explicit start/end dates
- **Language interface** — Nepali language accessible from any country

**Market sizing:** 500K+ NRN households with elderly dependents; estimated $5-10/month WTP for premium family plan.

### 14.3 Paper-to-Digital OCR Pipeline (Core MVP)

- **Prescription photo capture** with guided framing and quality check
- **OCR extraction** of medication names, dosages, frequencies
- **Human-in-the-loop review** — all OCR results require patient/provider confirmation
- **Accuracy tracking** — OCR confidence scores visible; low-confidence fields highlighted
- **Nepali script support** — Devanagari text extraction for Nepali prescriptions

### 14.4 Health Data Cooperative (Phase 3-4)

- **Patient-governed anonymized data contribution** for public health research
- **Ethical review board approval** required for researcher access
- **Revenue sharing** — approved research use generates returns for cooperative members
- **FHIR-based de-identification** — automated k-anonymity before data leaves platform

---

## Global PHR Feature Comparison

How Nepal PHR features compare to proven global systems:

| Feature                     | Nepal PHR     | India ABDM     | Estonia X-Road   | Australia MHR  | UK NHS App     | Rwanda OpenMRS    |
| --------------------------- | ------------- | -------------- | ---------------- | -------------- | -------------- | ----------------- |
| **Patient-owned records**   | ✅ Core       | ✅             | ✅               | ✅             | ✅             | ⚠️ Facility-owned |
| **FHIR R4 native**          | ✅ Day 1      | ✅             | ⚠️ Transitioning | ⚠️ Partial     | ⚠️ Partial     | ⚠️ Façade         |
| **Medical voice input**     | ✅ Nepali ASR | ❌             | ❌               | ❌             | ❌             | ❌                |
| **Offline-first**           | ✅ MVP        | ❌             | ❌               | ❌             | ❌             | ✅                |
| **Emergency QR**            | ✅ MVP        | ⚠️ ABHA card   | ⚠️ Break-glass   | ✅             | ❌             | ❌                |
| **Insurance integration**   | ✅ openIMIS   | ✅ PMJAY       | ✅ EHIS          | ✅ Medicare    | ✅ NHS         | ⚠️ CBHI           |
| **Community health worker** | ✅ FCHV       | ⚠️ ASHA        | ❌               | ❌             | ❌             | ✅ CHW            |
| **Telemedicine**            | ✅ Phase 2    | ✅ eSanjeevani | ❌               | ❌             | ✅             | ❌                |
| **Caregiver delegation**    | ✅ MVP        | ⚠️ Limited     | ❌               | ✅             | ✅ Proxy       | ❌                |
| **Data sovereignty**        | ✅ Nepal-only | ✅ India-only  | ✅ Estonia       | ✅ Australia   | ✅ UK          | ✅ Rwanda         |
| **Open-source stack**       | ✅ 100%       | ⚠️ Partial     | ⚠️ X-Road OSS    | ❌ Proprietary | ❌ Proprietary | ✅ 100%           |

---

## Notes for Development Team

1. **All features must be FHIR-compatible** for SIL-Nepal certification
2. **Data must remain on-premise** (within Nepal borders) — no cloud exports
3. **Security audit required** before Phase 3 launch — ensure OWASP Top 10 + DPIA + SAST/DAST pipeline
4. **Offline sync architecture** must ship in MVP for approved patient, caregiver, provider-assisted, and FCHV workflows, with more advanced recovery tooling landing in Phase 2
5. **Nepali language support** is non-negotiable (MVP requirement)
6. **Custom ASR development** begins Month 7, delivery Month 15+
7. **Telemedicine video** can use Jitsi (Apache 2.0, self-hosted) for Phase 2
8. **openIMIS integration** requires HIB API access (request in Month 2)
9. **FCHV integration** requires DoHS MOU signing
10. **Accessibility (WCAG 2.2 AA)** required for all user-facing features
11. **Emergency QR health card** is a high-value, low-complexity MVP feature — prioritize early
12. **NRN health corridor** is a unique monetization opportunity — design the caregiver delegation model to support it

---

_Feature list prepared based on PHR Nepal Consolidated Report v3.0 with global best practices, innovation features, and compliance enhancements applied. Ready for sprint planning and development roadmap creation._
