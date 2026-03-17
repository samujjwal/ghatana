# PHR Nepal Application — End-to-End Requirements Specification

**Version:** 2.0 (Enhanced with Global Standards, Data Classification, and Security Hardening)  
**Date:** January 2026  
**Status:** Aligned with the current MVP and Phase 2 rollout

| Field              | Value                                                                                                                                                                      |
| ------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Document Owner** | PHR Platform Lead                                                                                                                                                          |
| **Approved By**    | Chief Product Officer                                                                                                                                                      |
| **Classification** | Internal — Restricted                                                                                                                                                      |
| **Last Review**    | 2026-01-19                                                                                                                                                                 |
| **Next Review**    | 2026-04-19 (Quarterly)                                                                                                                                                     |
| **Companion Docs** | [Consolidated Report](phr-consolidated-report-v2.md), [Traceability Matrix](../01_governance/phr_requirements_traceability_matrix.md), [Feature List](phr-feature-list.md) |

> **📌 What changed in v2.0:** Added global compliance cross-reference table, data classification tags for all requirement categories, security hardening requirements (OWASP, DPIA, SAST/DAST), emergency QR health card requirement, FCHV integration requirement, enhanced gap analysis with prioritized remediation plan, and cross-references to proven global PHR systems.

---

## Table of Contents

1. [Executive Overview](#1-executive-overview)
2. [Stakeholder Requirements](#2-stakeholder-requirements)
3. [Functional Requirements by Module](#3-functional-requirements-by-module)
4. [Non-Functional Requirements](#4-non-functional-requirements)
5. [Integration Requirements](#5-integration-requirements)
6. [Data Requirements & Data Model](#6-data-requirements--data-model)
7. [Security & Compliance Requirements](#7-security--compliance-requirements)
8. [User Interface & UX Requirements](#8-user-interface--ux-requirements)
9. [Deployment & Infrastructure Requirements](#9-deployment--infrastructure-requirements)
10. [Testing & Quality Assurance Requirements](#10-testing--quality-assurance-requirements)
11. [Operational Requirements](#11-operational-requirements)
12. [Identified Gaps & Additions](#12-identified-gaps--additions)

---

# 1. Executive Overview

## 1.1 System Vision

A patient-centric Personal Health Record (PHR) system for Nepal that:

- Enables patients to own and control their medical records
- Integrates with healthcare facilities via FHIR APIs
- Complies with Government Directive 2081 and data sovereignty requirements
- Provides a core patient-owned record experience first, including OCR-assisted and voice-assisted data entry in MVP
- Supports telemedicine consultations (video + audio) in Phase 2
- Enables insurance claim processing through openIMIS in Phase 2, with eligibility baseline in MVP
- Maintains data on-premise within Nepal borders

## 1.2 System Scope

### In Scope

- Patient health record management (storage, retrieval, sharing)
- Provider access to patient records
- Appointment scheduling and reminders
- Medication management and adherence tracking
- Insurance eligibility checking
- OCR-assisted document digitization and review
- Voice-assisted patient and provider data entry
- Mobile app (iOS, Android), Web app (provider, admin, patient portals), and Desktop app shell
- FHIR API for external system integration

### Planned Phase 2 Expansion

- Telemedicine consultation (video, audio, transcription)
- Insurance claim submission and tracking
- Caregiver/family profile management
- Offline-first capability with sync

### Out of Scope (Phase 1-2)

- AI-powered diagnostic suggestions (Phase 4)
- Wearable device integration (Phase 2+)
- IoT device support (Phase 2+)
- Predictive health modeling (Phase 4)
- Research data warehousing (Phase 3+)
- Pharmacy dispensing system (only partner integration)
- Hospital billing system (integration only, not standalone)

## 1.3 Success Criteria

| Metric                      | Target                               | Timeline |
| --------------------------- | ------------------------------------ | -------- |
| **Functional Completeness** | All MVP features operational         | Month 6  |
| **User Adoption**           | 10,000 registered users              | Month 18 |
| **FHIR Compliance**         | SIL-Nepal certification              | Month 16 |
| **Security**                | Third-party audit pass               | Month 16 |
| **Uptime**                  | 99.5% availability                   | Ongoing  |
| **Performance**             | <2s page load time (90th percentile) | Month 12 |
| **User Satisfaction**       | NPS >50                              | Month 18 |

---

# 2. Stakeholder Requirements

## 2.1 Patient/User Requirements

### Primary Needs

- **REQ-PATIENT-001:** View complete medical history across providers
- **REQ-PATIENT-002:** Add/import medical records from multiple providers
- **REQ-PATIENT-003:** Grant time-limited access to providers (revocable)
- **REQ-PATIENT-004:** Receive medication reminders (customizable frequency)
- **REQ-PATIENT-005:** Schedule and attend telemedicine appointments (Phase 2)
- **REQ-PATIENT-006:** Track health metrics (vitals, lab results, trends)
- **REQ-PATIENT-007:** Manage family member records (with consent) (Phase 2)
- **REQ-PATIENT-008:** Export health data (FHIR, PDF, CSV formats)
- **REQ-PATIENT-009:** Access records offline (Phase 2)
- **REQ-PATIENT-010:** Use voice/audio for data entry and queries

### Secondary Needs

- **REQ-PATIENT-011:** Receive appointment reminders (SMS + push)
- **REQ-PATIENT-012:** View insurance claim status (Phase 2)
- **REQ-PATIENT-013:** Compare healthcare costs across providers
- **REQ-PATIENT-014:** Receive health alerts (abnormal results, medication interactions)
- **REQ-PATIENT-015:** Manage caregiver access (permissions) (Phase 2)
- **REQ-PATIENT-016:** Create emergency medical information summary
- **REQ-PATIENT-017:** Search medical records by date, provider, condition
- **REQ-PATIENT-018:** Share specific records with selected providers
- **REQ-PATIENT-019:** Receive health tips and education content (in Nepali)
- **REQ-PATIENT-020:** Reset account password easily (account recovery)

### Accessibility Needs

- **REQ-PATIENT-021:** Large text/high contrast mode (accessibility)
- **REQ-PATIENT-022:** Screen reader compatible interface (WCAG 2.2 AA)
- **REQ-PATIENT-023:** Voice-based app navigation (hands-free)
- **REQ-PATIENT-024:** Audio playback of medical terms with pronunciation
- **REQ-PATIENT-025:** Video consultation with captions (for deaf users) (Phase 2)

---

## 2.2 Healthcare Provider Requirements

### Physician/Doctor Needs

- **REQ-PROVIDER-001:** View assigned patient medical history
- **REQ-PROVIDER-002:** Search patient records by medical condition, medication, lab results
- **REQ-PROVIDER-003:** Add clinical notes to patient record (with signature)
- **REQ-PROVIDER-004:** Enter/verify prescriptions (with medication interaction checking)
- **REQ-PROVIDER-005:** View patient's medication list and adherence history
- **REQ-PROVIDER-006:** Document diagnoses using ICD-10 codes
- **REQ-PROVIDER-007:** Request laboratory tests and track results
- **REQ-PROVIDER-008:** Refer patients to specialists (with data transfer)
- **REQ-PROVIDER-009:** Conduct telemedicine consultations (video + screen sharing) (Phase 2)
- **REQ-PROVIDER-010:** Record consultation outcomes and follow-up plans (Phase 2 when produced from telemedicine workflow)

### Nurse/Healthcare Worker Needs

- **REQ-PROVIDER-011:** Enter vital signs (BP, heart rate, temperature, SpO2)
- **REQ-PROVIDER-012:** Log patient vitals with timestamp and signature
- **REQ-PROVIDER-013:** View patient allergy and medication history
- **REQ-PROVIDER-014:** Generate patient summaries for discharge
- **REQ-PROVIDER-015:** Track medication administration (for hospitalized patients)

### Administrative Staff Needs

- **REQ-PROVIDER-016:** Verify insurance eligibility before consultation
- **REQ-PROVIDER-017:** Submit insurance claims on behalf of patients (Phase 2)
- **REQ-PROVIDER-018:** Track claim status and reimbursement (Phase 2)
- **REQ-PROVIDER-019:** Generate patient appointment reminders (SMS/push)
- **REQ-PROVIDER-020:** Manage provider schedule and availability

---

## 2.3 Health Insurance Board (HIB) Requirements

### Insurance Claim Processing (Phase 2)

- **REQ-HIB-001:** Receive claim submissions in FHIR Claim format
- **REQ-HIB-002:** Validate patient eligibility in real-time
- **REQ-HIB-003:** Process claims with supporting documents (PDF, images)
- **REQ-HIB-004:** Return claim status updates to patients/providers
- **REQ-HIB-005:** Generate claim approval/rejection notices
- **REQ-HIB-006:** Track claims history for fraud detection (aggregated)
- **REQ-HIB-007:** Retain claims data for 7+ years per HIB policy

### Reporting & Analytics

- **REQ-HIB-008:** Extract anonymized claim data for HMIS (aggregated, consented)
- **REQ-HIB-009:** Generate monthly claim volume and approval rate reports
- **REQ-HIB-010:** Monitor facility-level claim processing patterns

---

## 2.4 Ministry of Health & Population (MoHP) Requirements

### Regulatory Compliance

- **REQ-MOHP-001:** System must comply with Integrated EMR Directive 2081
- **REQ-MOHP-002:** FHIR compliance verified by SIL-Nepal
- **REQ-MOHP-003:** Third-party security audit completed annually
- **REQ-MOHP-004:** Data sovereignty: All data stored within Nepal borders
- **REQ-MOHP-005:** Audit logs maintained for regulatory inspection
- **REQ-MOHP-006:** Privacy compliance per Privacy Act 2075

### Public Health Integration

- **REQ-MOHP-007:** Enable anonymized data contribution to HMIS (with consent)
- **REQ-MOHP-008:** Support disease surveillance reporting (aggregated)
- **REQ-MOHP-009:** Provide epidemiological data for public health planning
- **REQ-MOHP-010:** Enable vaccination coverage tracking

---

## 2.5 Administrator Requirements

### System Administration

- **REQ-ADMIN-001:** Manage user accounts (create, disable, reset)
- **REQ-ADMIN-002:** Configure role-based access control (RBAC)
- **REQ-ADMIN-003:** Monitor system health (uptime, performance, errors)
- **REQ-ADMIN-004:** View audit logs for compliance verification
- **REQ-ADMIN-005:** Generate system reports (user statistics, features usage)
- **REQ-ADMIN-006:** Manage facility configurations and integrations
- **REQ-ADMIN-007:** Configure notification settings (SMS, email, push)
- **REQ-ADMIN-008:** Backup and restore data (with encryption)

---

# 3. Functional Requirements by Module

## 3.1 Patient Registration & Profile Management

### Patient Registration (MVP)

- **REQ-REG-001:** Register new patient with name, DOB, gender, contact
- **REQ-REG-002:** Verify identity using NID/citizenship number (integration with NID database)
- **REQ-REG-003:** Verify email and phone number via OTP
- **REQ-REG-004:** Create unique patient identifier (UUID, no direct NID storage)
- **REQ-REG-005:** Support bulk import of existing patient databases
- **REQ-REG-006:** Record emergency contact information
- **REQ-REG-007:** Record primary healthcare provider/facility
- **REQ-REG-008:** Support multiple language patient interactions (Nepali, English)

### Patient Profile Management (MVP)

- **REQ-PROFILE-001:** Display patient profile with demographics
- **REQ-PROFILE-002:** Edit profile information (self-service)
- **REQ-PROFILE-003:** Upload profile photo (encrypted storage)
- **REQ-PROFILE-004:** Record blood type and Rh factor
- **REQ-PROFILE-005:** Record allergies with severity levels (mild, moderate, severe)
- **REQ-PROFILE-006:** Record medical conditions/chronic diseases
- **REQ-PROFILE-007:** Record medication allergies separately
- **REQ-PROFILE-008:** Record family medical history
- **REQ-PROFILE-009:** Record organ donation preferences
- **REQ-PROFILE-010:** Record Do Not Resuscitate (DNR) preferences
- **REQ-PROFILE-011:** Record surgical history with dates and outcomes
- **REQ-PROFILE-012:** Record immunization/vaccination history
- **REQ-PROFILE-013:** Display last updated timestamp for all fields
- **REQ-PROFILE-014:** Support data accuracy verification by provider

---

## 3.2 Medical Records Management

### Record Import & Ingestion (MVP)

- **REQ-MED-001:** Import patient records via FHIR API from healthcare facilities
- **REQ-MED-002:** Support manual upload of PDF medical documents
- **REQ-MED-003:** Support CSV bulk import of patient records
- **REQ-MED-004:** OCR conversion of scanned documents to searchable text
- **REQ-MED-005:** Auto-categorize documents (visit notes, prescriptions, labs, etc.)
- **REQ-MED-006:** Generate QR codes for paper medical documents
- **REQ-MED-007:** Scan QR codes to import digital records
- **REQ-MED-008:** Detect and warn on duplicate records
- **REQ-MED-009:** Store original documents with encryption
- **REQ-MED-010:** Track record source (which facility, which provider)

### Medical Record Organization (MVP)

- **REQ-MED-011:** Display records in chronological timeline
- **REQ-MED-012:** Filter records by type (visit, lab, prescription, imaging)
- **REQ-MED-013:** Filter records by provider or facility
- **REQ-MED-014:** Filter records by medical condition
- **REQ-MED-015:** Search records by keyword (full-text search)
- **REQ-MED-016:** Tag records manually for organization
- **REQ-MED-017:** Archive old records (no deletion, but not daily visible)
- **REQ-MED-018:** Pin important records to top of list
- **REQ-MED-019:** Group records by encounter/visit
- **REQ-MED-020:** Show record metadata (date, provider, facility, status)

### Record Display & Sharing (MVP)

- **REQ-MED-021:** Display record details (full text view)
- **REQ-MED-022:** Export record as PDF (formatted for printing)
- **REQ-MED-023:** Export record as FHIR JSON (for provider systems)
- **REQ-MED-024:** Share specific record with provider (time-limited URL)
- **REQ-MED-025:** Share specific record with family member (with consent)
- **REQ-MED-026:** Share entire medical history with new provider (one-click)
- **REQ-MED-027:** Revoke shared access anytime
- **REQ-MED-028:** Track who accessed which records (audit trail visible to patient)
- **REQ-MED-029:** Generate shareable summary report (for insurance, doctor visit)

### Clinical Observations & Vital Signs (MVP)

- **REQ-MED-030:** Store vital signs (BP, heart rate, temperature, SpO2, weight, height)
- **REQ-MED-031:** Record blood glucose levels (for diabetics)
- **REQ-MED-032:** Record cholesterol values (total, LDL, HDL, triglycerides)
- **REQ-MED-033:** Display vital sign trends over time (graphs)
- **REQ-MED-034:** Alert if vital signs out of normal range
- **REQ-MED-035:** Compare vitals across multiple dates
- **REQ-MED-036:** Calculate BMI from height/weight
- **REQ-MED-037:** Record oxygen saturation (SpO2) with timestamp

### Diagnoses & Conditions (MVP)

- **REQ-MED-038:** Record diagnoses with ICD-10 codes
- **REQ-MED-039:** Record diagnosis date and provider
- **REQ-MED-040:** Display active vs. resolved conditions
- **REQ-MED-041:** Show condition-related medications automatically
- **REQ-MED-042:** Link condition to related visits and lab results
- **REQ-MED-043:** Record severity of condition (mild, moderate, severe)
- **REQ-MED-044:** Track condition progression/resolution

---

## 3.3 Medication Management

### Prescription Management (MVP)

- **REQ-MED-045:** Store prescription records (medication name, dose, frequency, duration)
- **REQ-MED-046:** Display prescription in Nepali and English
- **REQ-MED-047:** Record prescribing provider and date
- **REQ-MED-048:** Link prescription to related diagnosis
- **REQ-MED-049:** Mark prescriptions as active, expired, or completed
- **REQ-MED-050:** Display instructions for medication use (plain language)
- **REQ-MED-051:** Track refills (number of refills remaining)
- **REQ-MED-052:** Record pharmacy where prescription was filled
- **REQ-MED-053:** Generate printable prescription copy
- **REQ-MED-054:** Share prescription with pharmacy
- **REQ-MED-055:** Generic alternative suggestions (when available)

### Medication History & Tracking (MVP)

- **REQ-MED-056:** Display complete medication history (past prescriptions)
- **REQ-MED-057:** Show current active medications list
- **REQ-MED-058:** Timeline view of medication changes over time
- **REQ-MED-059:** Track medication adherence (did you take it?)
- **REQ-MED-060:** Record medication side effects (patient-reported)
- **REQ-MED-061:** Record medication allergies separately from drug allergies
- **REQ-MED-062:** Track medication costs (paid by patient or insurance)

### Medication Interaction Checking (MVP)

- **REQ-MED-063:** Check for drug-drug interactions (all current medications)
- **REQ-MED-064:** Check for drug-allergy interactions
- **REQ-MED-065:** Display interaction severity (minor, moderate, severe)
- **REQ-MED-066:** Warn provider before prescribing interacting medication
- **REQ-MED-067:** Display interaction explanation in plain language
- **REQ-MED-068:** Suggest alternative medications if safe interaction unavailable

---

## 3.4 Laboratory Results & Imaging

### Lab Result Management (MVP)

- **REQ-LAB-001:** Import lab results from partner laboratories
- **REQ-LAB-002:** Display lab test name, value, unit, reference range
- **REQ-LAB-003:** Mark results as normal, low, or high
- **REQ-LAB-004:** Store lab report PDF with results
- **REQ-LAB-005:** Record lab collection date and result date
- **REQ-LAB-006:** Link results to related diagnosis
- **REQ-LAB-007:** Display lab facility and ordering provider

### Lab Trends & Analytics (MVP)

- **REQ-LAB-008:** Plot lab value trends over time (line chart)
- **REQ-LAB-009:** Compare lab values across visits
- **REQ-LAB-010:** Alert if lab value out of normal range
- **REQ-LAB-011:** Show reference ranges for different age/gender

### Lab Interpretation (MVP)

- **REQ-LAB-012:** Display plain-language interpretation of results
- **REQ-LAB-013:** Link to education content (what does this result mean?)
- **REQ-LAB-014:** Provide links to reliable health information resources

### Imaging & Radiology (Phase 2)

- **REQ-IMG-001:** Store imaging metadata (type, date, facility, provider)
- **REQ-IMG-002:** DICOM viewer for X-rays, CT, MRI, ultrasound
- **REQ-IMG-003:** Zoom, rotate, pan capabilities in DICOM viewer
- **REQ-IMG-004:** Display radiology report with images
- **REQ-IMG-005:** Before/after comparison of imaging studies
- **REQ-IMG-006:** Secure download of images for second opinion

---

## 3.5 Appointment Management

### Appointment Scheduling (MVP)

- **REQ-APT-001:** List available appointment slots (provider calendar)
- **REQ-APT-002:** Filter appointments by provider, facility, type
- **REQ-APT-003:** Book appointment with confirmation
- **REQ-APT-004:** Display appointment location and directions (GPS)
- **REQ-APT-005:** Show estimated wait time at facility
- **REQ-APT-006:** Support telemedicine appointment booking
- **REQ-APT-007:** Cancel or reschedule appointment
- **REQ-APT-008:** Request appointment with preferred time/date
- **REQ-APT-009:** Auto-suggest appointment based on patient history (e.g., annual checkup due)

### Appointment Reminders (MVP)

- **REQ-APT-010:** Send reminder 24 hours before appointment
- **REQ-APT-011:** Send reminder 2 hours before appointment
- **REQ-APT-012:** Remind via SMS (required) + push notification (optional)
- **REQ-APT-013:** Reminder includes appointment details (time, location, provider)
- **REQ-APT-014:** Allow user to reschedule or cancel from reminder
- **REQ-APT-015:** Confirm appointment attendance (check-in)
- **REQ-APT-016:** Record no-show (patient didn't attend appointment)

### Appointment History (MVP)

- **REQ-APT-017:** Display past appointments with notes
- **REQ-APT-018:** Display upcoming appointments
- **REQ-APT-019:** Export appointment list (for travel, reference)

---

## 3.6 Telemedicine & Remote Consultation (Phase 2)

### Video Consultation (Phase 2)

- **REQ-TELE-001:** Initiate video call with provider
- **REQ-TELE-002:** Real-time video streaming (HD quality)
- **REQ-TELE-003:** Automatic bitrate adjustment (for low-bandwidth areas)
- **REQ-TELE-004:** Screen sharing (provider shares patient's medical record on screen)
- **REQ-TELE-005:** End-to-end encryption for video (SRTP)
- **REQ-TELE-006:** Record consultation (with explicit consent from both parties)
- **REQ-TELE-007:** Stored consultation recording (encrypted, access-controlled)
- **REQ-TELE-008:** Chat messages during consultation
- **REQ-TELE-009:** Provider can send links/resources during call
- **REQ-TELE-010:** Session timer (display call duration)

### Audio Consultation (Phase 2)

- **REQ-TELE-011:** Initiate audio-only call (for low-bandwidth users)
- **REQ-TELE-012:** Audio quality optimization (noise reduction)
- **REQ-TELE-013:** Record audio call (with consent)
- **REQ-TELE-014:** Store audio file (encrypted)

### Real-Time Audio Transcription (Phase 2)

- **REQ-TELE-015:** Enable transcription of video/audio consultation
- **REQ-TELE-016:** Display live captions during call (for deaf users)
- **REQ-TELE-017:** Automatic ASR (Automatic Speech Recognition) in Nepali
- **REQ-TELE-018:** Highlight medical terminology in transcript
- **REQ-TELE-019:** Provider can manually correct transcript
- **REQ-TELE-020:** Save transcript to patient record (with consent)
- **REQ-TELE-021:** Search transcripts for keywords

### Voice Input for Data Entry (Phase 2)

- **REQ-TELE-022:** Provider can record clinical notes via voice
- **REQ-TELE-023:** Auto-transcription to text (with manual review)
- **REQ-TELE-024:** Provider can dictate prescriptions
- **REQ-TELE-025:** Provider can record vital signs verbally
- **REQ-TELE-026:** Medical term recognition (acronyms, abbreviations)

### Consultation Documentation (Phase 2)

- **REQ-TELE-027:** Auto-generate consultation summary from transcript
- **REQ-TELE-028:** Provider writes consultation notes
- **REQ-TELE-029:** Record consultation outcome (diagnosis, treatment plan)
- **REQ-TELE-030:** Schedule follow-up appointment from consultation
- **REQ-TELE-031:** Auto-generate prescription from consultation
- **REQ-TELE-032:** Store consultation record in patient's medical history

---

## 3.7 Medication Reminders & Adherence

### Medication Reminder Setup (MVP)

- **REQ-MED-REMIND-001:** Patient sets medication reminder frequency
- **REQ-MED-REMIND-002:** Customize reminder time (morning, afternoon, evening)
- **REQ-MED-REMIND-003:** Option to disable reminders
- **REQ-MED-REMIND-004:** Reminder includes medication name, dose, timing

### Medication Adherence Tracking (MVP)

- **REQ-MED-REMIND-005:** Patient logs when medication taken
- **REQ-MED-REMIND-006:** System tracks adherence rate (% of doses taken)
- **REQ-MED-REMIND-007:** Identify patterns of missed doses
- **REQ-MED-REMIND-008:** Alert if patient missed doses (for caregiver)
- **REQ-MED-REMIND-009:** Display adherence summary (daily, weekly, monthly)

### Medication Refill Reminders (MVP)

- **REQ-MED-REMIND-010:** Remind patient 7 days before prescription expires
- **REQ-MED-REMIND-011:** Enable refill request from reminder
- **REQ-MED-REMIND-012:** Notify pharmacy of refill request
- **REQ-MED-REMIND-013:** Track refill status (requested, approved, ready for pickup)

---

## 3.8 Insurance & Billing

### Insurance Information Management (MVP)

- **REQ-INS-001:** Store insurance card information (digitally)
- **REQ-INS-002:** Record insurance provider and policy number
- **REQ-INS-003:** Display insurance coverage details
- **REQ-INS-004:** Show covered vs. non-covered services
- **REQ-INS-005:** Display co-payment amounts

### Insurance Eligibility Verification (MVP)

- **REQ-INS-006:** Real-time eligibility check against openIMIS
- **REQ-INS-007:** Display eligibility status before treatment
- **REQ-INS-008:** Show coverage limit for current year
- **REQ-INS-009:** Display remaining benefits/coverage amount
- **REQ-INS-010:** Verify eligibility at point of care (provider portal)

### Claim Submission & Tracking (Phase 2)

- **REQ-INS-011:** Submit insurance claim on patient's behalf (with consent)
- **REQ-INS-012:** Attach supporting documents (receipts, test results, prescriptions)
- **REQ-INS-013:** Auto-populate claim with patient/provider/facility data
- **REQ-INS-014:** FHIR Claim format for openIMIS integration
- **REQ-INS-015:** Track claim status (submitted, received, under review, approved, rejected)
- **REQ-INS-016:** Display claim approval decision
- **REQ-INS-017:** Show reimbursement amount and payment date
- **REQ-INS-018:** Re-submit rejected claims with corrections
- **REQ-INS-019:** Appeal claim denials (with documentation)

### Billing Management (Phase 2)

- **REQ-INS-020:** Store medical bills from providers
- **REQ-INS-021:** Display itemized bill (procedure, cost, insurance covered, out-of-pocket)
- **REQ-INS-022:** Calculate out-of-pocket costs
- **REQ-INS-023:** Show co-pay vs. full insurance coverage
- **REQ-INS-024:** Track payment status (unpaid, partially paid, fully paid)
- **REQ-INS-025:** Record payment method (cash, digital wallet, insurance)
- **REQ-INS-026:** Generate bill receipt (PDF)
- **REQ-INS-027:** Expense categorization (doctor visit, lab, pharmacy, etc.)

### Financial Analytics (Phase 2)

- **REQ-INS-028:** Calculate total healthcare spending (monthly, yearly)
- **REQ-INS-029:** Show spending by category (doctor, lab, pharmacy, etc.)
- **REQ-INS-030:** Compare costs across providers (transparency)
- **REQ-INS-031:** Track insurance reimbursement vs. out-of-pocket
- **REQ-INS-032:** Generate annual health expense summary (for tax purposes)

---

## 3.9 Emergency & Critical Information

### Emergency Contact Management (MVP)

- **REQ-EMG-001:** Store emergency contact names and phone numbers
- **REQ-EMG-002:** Mark primary emergency contact
- **REQ-EMG-003:** Support multiple emergency contacts
- **REQ-EMG-004:** Display emergency contacts prominently

### Emergency Medical Summary (MVP)

- **REQ-EMG-005:** Generate one-page medical emergency summary
- **REQ-EMG-006:** Include allergies, blood type, current medications
- **REQ-EMG-007:** Include chronic conditions and procedures
- **REQ-EMG-008:** Include emergency contact information
- **REQ-EMG-009:** Display emergency summary without authentication (emergency mode)
- **REQ-EMG-010:** Emergency QR code (first responders can scan)

### Critical Alerts (MVP)

- **REQ-EMG-011:** Drug allergy alerts (when prescribed incompatible medication)
- **REQ-EMG-012:** Life-threatening drug interaction alerts
- **REQ-EMG-013:** Critical lab value alerts (dangerously high/low)
- **REQ-EMG-014:** Immediate notification to emergency contacts
- **REQ-EMG-015:** Store Do Not Resuscitate (DNR) preferences

---

## 3.10 Notifications & Alerts

### Notification Delivery (MVP)

- **REQ-NOTIF-001:** Send push notifications (mobile app)
- **REQ-NOTIF-002:** Send SMS notifications (for non-app users)
- **REQ-NOTIF-003:** Send email notifications (opt-in)
- **REQ-NOTIF-004:** User can customize notification preferences
- **REQ-NOTIF-005:** Disable notifications for specific types
- **REQ-NOTIF-006:** Set quiet hours (no notifications between hours)
- **REQ-NOTIF-007:** Urgent notifications bypass quiet hours

### Alert Types (MVP)

- **REQ-NOTIF-008:** Appointment reminder (24h, 2h before)
- **REQ-NOTIF-009:** Medication reminder
- **REQ-NOTIF-010:** Medication refill reminder
- **REQ-NOTIF-011:** Lab result available
- **REQ-NOTIF-012:** Abnormal lab result alert
- **REQ-NOTIF-013:** Insurance claim status update
- **REQ-NOTIF-014:** Prescription ready for pickup
- **REQ-NOTIF-015:** Doctor message/communication
- **REQ-NOTIF-016:** Preventive care due (e.g., vaccination)
- **REQ-NOTIF-017:** Annual checkup reminder

---

## 3.11 Referral Management (Phase 2)

### Referral Creation (Phase 2)

- **REQ-REF-001:** Create referral to specialist from primary doctor
- **REQ-REF-002:** Include patient medical summary in referral
- **REQ-REF-003:** Specify reason for referral
- **REQ-REF-004:** Recommend specialist type or specific doctor
- **REQ-REF-005:** Send referral electronically to specialist facility

### Referral Tracking (Phase 2)

- **REQ-REF-006:** Track referral status (accepted, scheduled, completed)
- **REQ-REF-007:** Display specialist appointment scheduled via referral
- **REQ-REF-008:** Receive feedback from specialist back to primary doctor
- **REQ-REF-009:** Cross-facility referral tracking (between hospitals)
- **REQ-REF-010:** Store referral outcome (diagnosis, treatment)

---

## 3.12 Blood Bank Management (Phase 2)

### Blood Donor Registry (Phase 2)

- **REQ-BLOOD-001:** Record patient blood type (A, B, AB, O)
- **REQ-BLOOD-002:** Record Rh factor (+/-)
- **REQ-BLOOD-003:** Allow patient to register as blood donor
- **REQ-BLOOD-004:** Store blood donation history (date, amount, facility)
- **REQ-BLOOD-005:** Display blood donor status

---

## 3.13 Family & Caregiver Management

### Multi-User Family Profiles (Phase 2)

- **REQ-FAMILY-001:** Create family account (primary user + dependents)
- **REQ-FAMILY-002:** Add family members (children, spouse, parents)
- **REQ-FAMILY-003:** Share family health hub (visible to all family members)
- **REQ-FAMILY-004:** Family members can be added with consent
- **REQ-FAMILY-005:** Dependent minors added by legal guardian
- **REQ-FAMILY-006:** Display family tree (relationships)

### Caregiver Access & Permissions (Phase 2)

- **REQ-FAMILY-007:** Assign caregiver role to family member
- **REQ-FAMILY-008:** Configure caregiver permissions (read-only, edit, etc.)
- **REQ-FAMILY-009:** Time-limited caregiver access (auto-expire)
- **REQ-FAMILY-010:** Revoke caregiver access anytime
- **REQ-FAMILY-011:** Emergency override (caregiver access when patient unconscious)
- **REQ-FAMILY-012:** Caregiver can manage appointments on behalf of patient
- **REQ-FAMILY-013:** Caregiver receives medication reminders (can log doses)
- **REQ-FAMILY-014:** Caregiver receives health alerts

### Caregiver Dashboard (Phase 2)

- **REQ-FAMILY-015:** View health status of assigned dependents
- **REQ-FAMILY-016:** Track medication adherence for dependent
- **REQ-FAMILY-017:** Schedule appointments for dependent
- **REQ-FAMILY-018:** Receive alerts for dependent's health
- **REQ-FAMILY-019:** Communication channel with dependent's providers

### Child Health Tracking (MVP for Phase 2)

- **REQ-FAMILY-020:** Track child growth (height, weight percentiles)
- **REQ-FAMILY-021:** Immunization schedule tracker for children
- **REQ-FAMILY-022:** Developmental milestones tracking
- **REQ-FAMILY-023:** Pediatric allergy tracking
- **REQ-FAMILY-024:** School health records storage

---

## 3.14 Data Import/Export & Interoperability

### FHIR API Gateway (MVP)

- **REQ-FHIR-001:** Accept inbound FHIR API calls from external EMR systems
- **REQ-FHIR-002:** Standard FHIR R4 resources (Patient, Medication, Observation, etc.)
- **REQ-FHIR-003:** API authentication via OAuth2 + client certificates
- **REQ-FHIR-004:** Rate limiting and API quota management
- **REQ-FHIR-005:** API versioning support
- **REQ-FHIR-006:** Comprehensive API documentation

### Data Export (MVP)

- **REQ-EXPORT-001:** Export all patient data as FHIR JSON
- **REQ-EXPORT-002:** Export medical records as PDF (formatted)
- **REQ-EXPORT-003:** Export structured data as CSV
- **REQ-EXPORT-004:** Export visit summary report
- **REQ-EXPORT-005:** Export medication list (current + historical)
- **REQ-EXPORT-006:** Export lab results
- **REQ-EXPORT-007:** Schedule automated exports (e.g., monthly)
- **REQ-EXPORT-008:** Encrypt exported data

### Data Import (MVP)

- **REQ-IMPORT-001:** Import FHIR data from partner facilities
- **REQ-IMPORT-002:** Import PDF medical documents
- **REQ-IMPORT-003:** Import CSV patient data (bulk)
- **REQ-IMPORT-004:** Detect and handle duplicate records
- **REQ-IMPORT-005:** Data validation on import
- **REQ-IMPORT-006:** Import audit trail (what data was imported from where)

---

# 4. Non-Functional Requirements

## 4.1 Performance Requirements

| Requirement           | Target                   | Rationale                       |
| --------------------- | ------------------------ | ------------------------------- |
| **Page Load Time**    | <2s (90th percentile)    | Mobile users with slow networks |
| **Search Response**   | <1s for 100K records     | Full-text search performance    |
| **API Response Time** | <500ms (p95)             | External integrations           |
| **Concurrent Users**  | 10,000 peak              | Scale to 50K users              |
| **Database Query**    | <100ms for typical query | Responsive UI                   |
| **Video Bitrate**     | 500kbps min (adaptive)   | Work on 2G/3G networks          |
| **App Startup**       | <3s on modern phone      | User experience                 |
| **File Upload**       | Support 50MB files       | Medical imaging support         |

**Implementation:**

- REQ-PERF-001: Implement database indexing for common queries
- REQ-PERF-002: Use caching (Valkey) for frequently accessed data
- REQ-PERF-003: Lazy-load records (paginate timeline)
- REQ-PERF-004: Optimize images (compression, thumbnails)
- REQ-PERF-005: Use CDN for static assets
- REQ-PERF-006: Monitor performance (APM tools)

---

## 4.2 Scalability Requirements

| Requirement            | Target                       | Justification                |
| ---------------------- | ---------------------------- | ---------------------------- |
| **User Scaling**       | 50,000+ users by Year 3      | Market opportunity           |
| **Data Scaling**       | 1B+ medical records          | Historical data accumulation |
| **Horizontal Scaling** | Add servers without downtime | Handle growth                |
| **Database Scaling**   | Sharding/replication support | Large dataset management     |
| **Storage Scaling**    | Multi-TB storage             | Medical imaging storage      |

**Implementation:**

- REQ-SCALE-001: Stateless web servers (scale horizontally)
- REQ-SCALE-002: Database replication (read replicas)
- REQ-SCALE-003: Message queue (async processing)
- REQ-SCALE-004: Auto-scaling based on load

---

## 4.3 Availability & Reliability

| Requirement                      | Target                  | Implementation                 |
| -------------------------------- | ----------------------- | ------------------------------ |
| **Uptime SLA**                   | 99.5%                   | Redundant servers, monitoring  |
| **Mean Time to Recovery (MTTR)** | <15 minutes             | Automated failover             |
| **Backup Frequency**             | Daily encrypted backups | Data protection                |
| **Disaster Recovery**            | Recover within 4 hours  | Backup in secondary datacenter |
| **Data Consistency**             | No data loss            | Transaction logs, replication  |

**Implementation:**

- REQ-AVAIL-001: Primary + secondary servers in different locations
- REQ-AVAIL-002: Load balancer with health checks
- REQ-AVAIL-003: Automated database failover
- REQ-AVAIL-004: Daily backup with verification
- REQ-AVAIL-005: Disaster recovery plan tested quarterly

---

## 4.4 Maintainability & Supportability

| Requirement          | Implementation                                 |
| -------------------- | ---------------------------------------------- |
| **Code Quality**     | SonarQube checks, 80% code coverage            |
| **Documentation**    | API docs, architecture docs, operations manual |
| **Logging**          | Centralized logging (OpenSearch + Fluent Bit)  |
| **Monitoring**       | Real-time alerting (Prometheus + Grafana)      |
| **Support Runbooks** | Documented procedures for common issues        |
| **CI/CD Pipeline**   | Automated testing and deployment               |

**Implementation:**

- REQ-MAINTAIN-001: Automated tests (unit, integration, e2e)
- REQ-MAINTAIN-002: Code review process
- REQ-MAINTAIN-003: Version control (Git)
- REQ-MAINTAIN-004: Staged deployments (dev → staging → prod)

---

## 4.5 Usability Requirements

| Requirement                   | Implementation                                               |
| ----------------------------- | ------------------------------------------------------------ |
| **WCAG 2.2 AA Accessibility** | Screen reader compatible, high contrast, keyboard navigation |
| **Mobile-First Design**       | Responsive, touch-optimized, works on older phones           |
| **Multilingual Support**      | Nepali, English, Maithili, Newari                            |
| **Health Literacy**           | Plain language, illustrations, glossary                      |
| **User Testing**              | Usability testing with 10+ users per feature                 |
| **Accessibility Testing**     | Automated + manual accessibility audits                      |

---

## 4.6 Compliance Requirements

| Requirement          | Details                                            |
| -------------------- | -------------------------------------------------- |
| **Directive 2081**   | FHIR compliance, 21 core modules, security audit   |
| **Privacy Act 2075** | Consent, data minimization, right to access/delete |
| **Data Sovereignty** | Data stored on-premise (within Nepal)              |
| **Security Audit**   | Third-party audit annually                         |
| **HIB Compliance**   | openIMIS integration, claim format, data retention |
| **FHIR Standard**    | R4 resources, conformance profile                  |

---

# 5. Integration Requirements

## 5.1 Healthcare Facility Integrations (MVP)

### EMR Integration (FHIR-based)

- **REQ-INT-EMR-001:** Receive patient data from hospital EMR systems
- **REQ-INT-EMR-002:** Standard FHIR R4 resources
- **REQ-INT-EMR-003:** OAuth2 authentication for EMR API calls
- **REQ-INT-EMR-004:** Automated daily data sync
- **REQ-INT-EMR-005:** Handle API failures gracefully (retry, queue)
- **REQ-INT-EMR-006:** Data validation on receipt

### Pharmacy Integration (Phase 2)

- **REQ-INT-PHARM-001:** Query pharmacy for medication availability
- **REQ-INT-PHARM-002:** Send prescription to pharmacy (order placement)
- **REQ-INT-PHARM-003:** Receive prescription fulfillment notification
- **REQ-INT-PHARM-004:** Track prescription pickup status

### Lab Integration (Phase 2)

- **REQ-INT-LAB-001:** Receive lab results from partner labs
- **REQ-INT-LAB-002:** Support multiple lab partners
- **REQ-INT-LAB-003:** Auto-notify patient when results available
- **REQ-INT-LAB-004:** Store lab report PDFs

---

## 5.2 Insurance Integration

### openIMIS Integration (MVP baseline + Phase 2 claims)

- **REQ-INT-OPENIMIS-001:** Real-time eligibility verification (MVP)
- **REQ-INT-OPENIMIS-002:** Submit claims in FHIR format (Phase 2)
- **REQ-INT-OPENIMIS-003:** Receive claim response (approved/rejected) (Phase 2)
- **REQ-INT-OPENIMIS-004:** Retrieve claim status (Phase 2)
- **REQ-INT-OPENIMIS-005:** Handle HIB API authentication (API key)
- **REQ-INT-OPENIMIS-006:** Error handling and retry logic

---

## 5.3 Government System Integration

### HMIS Integration (Phase 2)

- **REQ-INT-HMIS-001:** Export anonymized health data to HMIS
- **REQ-INT-HMIS-002:** Aggregated, no patient-identifying information
- **REQ-INT-HMIS-003:** Frequency: Monthly or quarterly
- **REQ-INT-HMIS-004:** Data format: CSV or agreed-upon format

### SIL-Nepal Conformance (MVP)

- **REQ-INT-SIL-001:** FHIR conformance testing
- **REQ-INT-SIL-002:** Validation against SIL-Nepal profiles
- **REQ-INT-SIL-003:** Annual re-testing

---

## 5.4 Telemedicine Platform Integration (Phase 2)

### Video Conferencing Integration

- **REQ-INT-VIDEO-001:** Integrate Jitsi (Apache 2.0, self-hosted)
- **REQ-INT-VIDEO-002:** Support WebRTC for web app
- **REQ-INT-VIDEO-003:** Encrypt video calls (SRTP)
- **REQ-INT-VIDEO-004:** Record calls (encrypted)

---

## 5.5 Payment Integration (Phase 2)

### Digital Payment (Phase 2)

- **REQ-INT-PAY-001:** Khalti integration (Nepal's largest digital wallet)
- **REQ-INT-PAY-002:** eSewa integration (alternative payment)
- **REQ-INT-PAY-003:** Mobile wallet payments
- **REQ-INT-PAY-004:** Payment verification and confirmation
- **REQ-INT-PAY-005:** Refund handling
- **REQ-INT-PAY-006:** Subscription billing integration

---

## 5.6 Communication Services

### SMS Notifications (MVP)

- **REQ-INT-SMS-001:** Integrate Sparrow SMS (Nepal-based)
- **REQ-INT-SMS-002:** Send appointment reminders via SMS
- **REQ-INT-SMS-003:** Send medication reminders via SMS
- **REQ-INT-SMS-004:** Send alerts via SMS
- **REQ-INT-SMS-005:** Two-way SMS support (reply to confirm)

### Push Notifications (MVP)

- **REQ-INT-PUSH-001:** ntfy (Apache 2.0, self-hosted) for Android
- **REQ-INT-PUSH-002:** Apple Push Notification (APN) for iOS
- **REQ-INT-PUSH-003:** Rich notifications with actions

### Email (MVP)

- **REQ-INT-EMAIL-001:** Postfix on-premise email
- **REQ-INT-EMAIL-002:** Send password reset emails
- **REQ-INT-EMAIL-003:** Send appointment reminders (opt-in)
- **REQ-INT-EMAIL-004:** Send reports (health summary)

---

## 5.7 Analytics & Monitoring Integration (Phase 2)

### Health Analytics

- **REQ-INT-ANALYTICS-001:** Integrate Plausible Analytics (privacy-compliant)
- **REQ-INT-ANALYTICS-002:** Track user behavior (no personal data)
- **REQ-INT-ANALYTICS-003:** Feature usage analytics

### System Monitoring

- **REQ-INT-MONITOR-001:** Prometheus for metrics collection
- **REQ-INT-MONITOR-002:** Grafana for dashboards
- **REQ-INT-MONITOR-003:** Alerting on threshold breaches

---

## 5.8 ASR/Voice Integration (MVP Baseline)

### Speech-to-Text (MVP Baseline)

- **REQ-INT-ASR-001:** Vosk on-premise ASR (Apache 2.0) (MVP baseline)
- **REQ-INT-ASR-002:** Nepali language support
- **REQ-INT-ASR-003:** Medical terminology recognition (enhanced)
- **REQ-INT-ASR-004:** Custom ASR model (Phase 2+)
- **REQ-INT-ASR-005:** Batch processing for non-real-time transcription
- **REQ-INT-ASR-006:** Streaming ASR for live consultations

---

# 6. Data Requirements & Data Model

## 6.1 Core Data Entities

### Patient Entity

```
Patient
├─ ID (UUID, not NID)
├─ Demographics
│  ├─ Name (first, last)
│  ├─ Date of Birth
│  ├─ Gender
│  ├─ Contact (phone, email)
│  ├─ Address
│  └─ Emergency Contact(s)
├─ Medical Profile
│  ├─ Blood Type (A, B, AB, O, +/-)
│  ├─ Allergies (list with severity)
│  ├─ Medical History (conditions, procedures)
│  ├─ Medications (current + historical)
│  ├─ Immunizations
│  └─ Family History
├─ Insurance Info
│  ├─ Provider & Policy Number
│  ├─ Coverage Details
│  └─ Eligibility Status
└─ Preferences
   ├─ Language
   ├─ Notification Settings
   └─ Sharing Preferences
```

**Storage:**

- REQ-DATA-PATIENT-001: Encrypt sensitive fields (name, contact)
- REQ-DATA-PATIENT-002: Store audit log of access
- REQ-DATA-PATIENT-003: Support soft delete (archive, not permanent)

### Medical Record Entity

```
MedicalRecord
├─ ID (UUID)
├─ PatientID (reference)
├─ RecordType (visit, lab, prescription, imaging, etc.)
├─ Content
│  ├─ Text/Notes
│  ├─ Structured Data (FHIR format)
│  ├─ Attachments (PDF, images)
│  └─ Metadata
├─ Provenance
│  ├─ Source Facility
│  ├─ Provider Name
│  ├─ Creation Date
│  └─ Upload Date
└─ Status
   ├─ Active/Archived
   ├─ Verified by Provider
   └─ Accuracy Status
```

### Medication Entity

```
Medication
├─ ID (UUID)
├─ PatientID (reference)
├─ Details
│  ├─ Name
│  ├─ Dose
│  ├─ Frequency
│  ├─ Duration
│  ├─ Instructions
│  └─ Generic Alternative
├─ Provider Info
│  ├─ Prescribing Doctor
│  ├─ Prescription Date
│  └─ Facility
├─ Pharmacy Info
│  ├─ Filled Date
│  ├─ Pharmacy Name
│  └─ Refills Remaining
└─ Adherence Tracking
   ├─ Doses Taken
   ├─ Adherence Rate
   └─ Side Effects
```

### Appointment Entity

```
Appointment
├─ ID (UUID)
├─ PatientID (reference)
├─ Provider Info
│  ├─ Provider Name
│  ├─ Facility
│  └─ Type (in-person, video, audio)
├─ Scheduling
│  ├─ Date & Time
│  ├─ Duration
│  └─ Location (GPS)
├─ Status
│  ├─ Scheduled/Completed/Cancelled/No-show
│  └─ Reminders Sent
└─ Notes
   ├─ Reason for Visit
   └─ Outcome
```

### Consultation Entity

```
Consultation
├─ ID (UUID)
├─ AppointmentID (reference)
├─ Details
│  ├─ Start Time
│  ├─ Duration
│  └─ Type (video/audio)
├─ Content
│  ├─ Raw Recording (encrypted)
│  ├─ Transcript (auto + manual)
│  ├─ Clinical Notes
│  ├─ Diagnoses (ICD-10 codes)
│  ├─ Prescriptions Generated
│  └─ Follow-up Plan
└─ Compliance
   ├─ Consent for Recording
   └─ Encryption Verification
```

### Insurance Claim Entity

```
Claim
├─ ID (UUID)
├─ PatientID (reference)
├─ Details
│  ├─ Claim Date
│  ├─ Service Date
│  ├─ Provider & Facility
│  ├─ Procedure/Service
│  └─ Amount Claimed
├─ Supporting Documents
│  ├─ Receipt
│  ├─ Lab Results
│  └─ Prescription
├─ FHIR Claim Resource
│  └─ Standard Format
├─ Status
│  ├─ Submitted
│  ├─ Under Review
│  ├─ Approved/Rejected
│  └─ Payment Date
└─ Audit Trail
   ├─ Submitted By
   └─ Timestamp
```

---

## 6.2 Data Storage Architecture

### Primary Storage (On-Premise, Nepal-IX)

```
PostgreSQL Database
├─ Patient Records
├─ Medical Records
├─ Medications
├─ Appointments
├─ Consultations
├─ Claims
├─ Audit Logs (immutable)
└─ User Access Logs
```

**Encryption:**

- REQ-DATA-ENC-001: AES-256 for sensitive fields (names, medical history)
- REQ-DATA-ENC-002: TLS 1.3 for all network communication
- REQ-DATA-ENC-003: Row-level security (patient can only see own data)

### File Storage (Ceph RADOS Gateway, S3-compatible)

```
Ceph Bucket Structure
├─ patients/{patient_id}/
│  ├─ medical_records/
│  │  └─ {record_id}.pdf
│  ├─ images/
│  │  └─ {image_id}.dcm (DICOM)
│  ├─ consultations/
│  │  └─ {consultation_id}.mp4 (encrypted)
│  └─ exports/
│     └─ {export_id}.zip
```

**Backup Strategy:**

- REQ-DATA-BACKUP-001: Daily encrypted backup
- REQ-DATA-BACKUP-002: Geographic redundancy (secondary datacenter in Nepal)
- REQ-DATA-BACKUP-003: Backup retention: 1 month (recent) + 1 archive (yearly)
- REQ-DATA-BACKUP-004: Weekly backup verification

---

## 6.3 Data Retention & Lifecycle

| Data Type                   | Retention Period                                       | Rationale                       |
| --------------------------- | ------------------------------------------------------ | ------------------------------- |
| **Medical Records**         | Indefinite (patient's lifetime + 10 years after death) | Clinical requirement            |
| **Insurance Claims**        | 7 years                                                | HIB policy                      |
| **Audit Logs**              | 3 years                                                | Regulatory requirement          |
| **Consultation Recordings** | 1 year (then delete, keep transcript)                  | Storage optimization            |
| **Backup Archives**         | 7 years                                                | Disaster recovery               |
| **Deleted Accounts**        | 30 days (then permanently delete)                      | GDPR-like right to be forgotten |

---

## 6.4 Data Privacy & Encryption

### Personal Data Classification

```
Level 1 (Highly Sensitive) - Encrypt + Log Access
├─ Patient Name
├─ Medical Records
├─ Contact Information
└─ Insurance Claims

Level 2 (Sensitive) - Encrypt
├─ Appointment History
├─ Medication List
└─ Consultation Notes

Level 3 (Semi-Public) - No Encryption
├─ De-identified Aggregate Statistics
└─ Public Health Insights (anonymized)
```

**Encryption Implementation:**

- REQ-DATA-PRIV-001: Use AES-256 for Level 1 & 2
- REQ-DATA-PRIV-002: TLS 1.3 for all data in transit
- REQ-DATA-PRIV-003: Field-level encryption (not just database encryption)
- REQ-DATA-PRIV-004: Encryption keys managed separately (HSM or Vault)

---

# 7. Security & Compliance Requirements

## 7.1 Authentication & Access Control

### User Authentication (MVP)

- **REQ-SEC-AUTH-001:** Username/password with strong validation
- **REQ-SEC-AUTH-002:** Multi-factor authentication (MFA) for providers
- **REQ-SEC-AUTH-003:** OTP via SMS for MFA
- **REQ-SEC-AUTH-004:** Biometric authentication (fingerprint, face) on mobile
- **REQ-SEC-AUTH-005:** Session timeout (15 min for sensitive operations)
- **REQ-SEC-AUTH-006:** Force re-authentication for sensitive actions (data deletion)
- **REQ-SEC-AUTH-007:** Account lockout after 5 failed login attempts

### Authorization & RBAC (MVP)

- **REQ-SEC-RBAC-001:** Role-based access control (RBAC)
- **REQ-SEC-RBAC-002:** Roles: Patient, Provider (doctor/nurse), Admin, Caregiver
- **REQ-SEC-RBAC-003:** Least privilege principle (minimal permissions)
- **REQ-SEC-RBAC-004:** Permission matrix (which role can do what)
- **REQ-SEC-RBAC-005:** Custom roles for organizations
- **REQ-SEC-RBAC-006:** Audit role assignments and changes

**Permission Matrix:**
| Action | Patient | Provider | Admin | Caregiver |
|---|---|---|---|---|
| View own records | ✅ | ❌ | ❌ | ❌ |
| View assigned patient records | ❌ | ✅ (approved) | ❌ | ❌ |
| Add clinical notes | ❌ | ✅ | ❌ | ❌ |
| View family member records | ❌ | ❌ | ❌ | ✅ (consented) |
| Delete account | ✅ | ✅ | ✅ | ❌ |
| View audit logs | ❌ | ❌ | ✅ | ❌ |

---

## 7.2 Data Encryption & Protection

### Encryption Standards (MVP)

- **REQ-SEC-ENC-001:** AES-256 for data at rest
- **REQ-SEC-ENC-002:** TLS 1.3 for data in transit
- **REQ-SEC-ENC-003:** SHA-256 for password hashing (bcrypt or Argon2)
- **REQ-SEC-ENC-004:** SRTP for video calls
- **REQ-SEC-ENC-005:** PFS (Perfect Forward Secrecy) for TLS

### Key Management (MVP)

- **REQ-SEC-KEY-001:** Encryption keys stored separately from data
- **REQ-SEC-KEY-002:** Key rotation annually
- **REQ-SEC-KEY-003:** Master key stored in hardware security module (HSM) or Vault
- **REQ-SEC-KEY-004:** Access to keys logged and audited
- **REQ-SEC-KEY-005:** Backup keys encrypted separately

---

## 7.3 Audit Logging & Monitoring

### Audit Trail (MVP)

- **REQ-SEC-AUDIT-001:** Log all data access (read, create, update, delete)
- **REQ-SEC-AUDIT-002:** Immutable logs (cannot be deleted or modified)
- **REQ-SEC-AUDIT-003:** Log entries include: timestamp, user, IP, action, object, result
- **REQ-SEC-AUDIT-004:** Retention: 3+ years
- **REQ-SEC-AUDIT-005:** Real-time alerts on suspicious activity

**Example Audit Log Entry:**

```json
{
  "timestamp": "2025-03-15T10:30:45.123Z",
  "user_id": "user_12345",
  "user_role": "provider",
  "action": "read",
  "resource_type": "medical_record",
  "resource_id": "record_98765",
  "resource_patient_id": "patient_11111",
  "ip_address": "203.0.113.45",
  "user_agent": "Mozilla/5.0...",
  "result": "success",
  "details": "Viewed lab results",
  "consent_valid": true
}
```

### Security Monitoring (MVP)

- **REQ-SEC-MONITOR-001:** Real-time security event monitoring
- **REQ-SEC-MONITOR-002:** Alert on suspicious patterns (multiple failed logins, mass data access)
- **REQ-SEC-MONITOR-003:** Automated response to threats (lock account, disable access)
- **REQ-SEC-MONITOR-004:** Centralized security information and event management (SIEM)
- **REQ-SEC-MONITOR-005:** Weekly security review meetings

---

## 7.4 Vulnerability Management

### Vulnerability Assessment (MVP)

- **REQ-SEC-VULN-001:** Annual penetration testing by certified firm
- **REQ-SEC-VULN-002:** Vulnerability scanning on code (SAST)
- **REQ-SEC-VULN-003:** Vulnerability scanning on dependencies (SCA)
- **REQ-SEC-VULN-004:** Security code review for critical code paths
- **REQ-SEC-VULN-005:** Responsible disclosure program (bug bounty)

### Patch Management (MVP)

- **REQ-SEC-PATCH-001:** Patch critical vulnerabilities within 24 hours
- **REQ-SEC-PATCH-002:** Patch high-severity within 1 week
- **REQ-SEC-PATCH-003:** Patch medium/low within 30 days
- **REQ-SEC-PATCH-004:** Test patches in staging before production
- **REQ-SEC-PATCH-005:** Zero-downtime deployment of patches

---

## 7.5 Incident Response & Data Breach

### Incident Response Plan (MVP)

- **REQ-SEC-INCIDENT-001:** Documented incident response procedure
- **REQ-SEC-INCIDENT-002:** Incident classification (severity levels)
- **REQ-SEC-INCIDENT-003:** Incident reporting template
- **REQ-SEC-INCIDENT-004:** Escalation procedures
- **REQ-SEC-INCIDENT-005:** Post-incident analysis (root cause)

### Data Breach Notification (MVP)

- **REQ-SEC-BREACH-001:** Notify affected patients within 72 hours
- **REQ-SEC-BREACH-002:** Notify MoHP within 72 hours (per draft IT Bill)
- **REQ-SEC-BREACH-003:** Preserve evidence for investigation
- **REQ-SEC-BREACH-004:** Document breach response
- **REQ-SEC-BREACH-005:** Communicate remediation steps to patients

**Breach Notification Template:**

```
Subject: Healthcare Data Security Incident — Action Required

Dear [Patient Name],

We are notifying you of a security incident affecting your medical records.

What happened: [Brief description]
When: [Date]
Potential impact: [What data was exposed]
What we're doing: [Remediation steps]
What you should do: [Recommended actions]
Contact: [Support hotline]

Sincerely,
[Company] Security Team
```

---

## 7.6 Compliance Requirements

### Directive 2081 Compliance (MVP)

- **REQ-SEC-DIR2081-001:** FHIR R4 resource implementation
- **REQ-SEC-DIR2081-002:** All 21 EMR modules support (receive from external EMRs)
- **REQ-SEC-DIR2081-003:** Interoperability layer (FHIR API Gateway)
- **REQ-SEC-DIR2081-004:** SIL-Nepal conformance testing
- **REQ-SEC-DIR2081-005:** Third-party security audit

### Privacy Act 2075 Compliance (MVP)

- **REQ-SEC-PRIVACY-001:** Data minimization (collect only necessary data)
- **REQ-SEC-PRIVACY-002:** Explicit consent for data processing
- **REQ-SEC-PRIVACY-003:** Right to access (provide copy of personal data)
- **REQ-SEC-PRIVACY-004:** Right to rectification (correct inaccurate data)
- **REQ-SEC-PRIVACY-005:** Right to erasure (delete account and data)
- **REQ-SEC-PRIVACY-006:** Privacy policy in Nepali and English
- **REQ-SEC-PRIVACY-007:** Privacy impact assessment (PIA)

### Data Sovereignty (MVP)

- **REQ-SEC-SOVEREIGN-001:** All primary data stored in Nepal
- **REQ-SEC-SOVEREIGN-002:** No backup or copy outside Nepal
- **REQ-SEC-SOVEREIGN-003:** Data processing occurs within Nepal
- **REQ-SEC-SOVEREIGN-004:** Regular audit of data location
- **REQ-SEC-SOVEREIGN-005:** Documentation for MoHP verification

---

# 8. User Interface & UX Requirements

## 8.1 Mobile App UI Requirements

### Patient Mobile App (iOS & Android)

**Core Screens (MVP):**

- **Dashboard:** Health summary, upcoming appointments, recent records, alerts
- **Medical History:** Timeline of records, search, filter, organize
- **Medications:** Current meds, reminders, adherence tracking
- **Appointments:** Schedule, view upcoming, check-in
- **Insurance:** Coverage and eligibility baseline; claim status in Phase 2
- **Profile:** Edit details and emergency contacts; caregiver access in Phase 2
- **Settings:** Notifications, language, privacy, logout

**UI/UX Principles:**

- REQ-UI-MOBILE-001: Mobile-first responsive design
- REQ-UI-MOBILE-002: Touch-optimized buttons (minimum 44x44 pts)
- REQ-UI-MOBILE-003: Bottom navigation for easy thumb access
- REQ-UI-MOBILE-004: Simplified forms (progressive disclosure)
- REQ-UI-MOBILE-005: Large fonts for readability (minimum 14pt body text)
- REQ-UI-MOBILE-006: High contrast colors (WCAG AA)
- REQ-UI-MOBILE-007: Clear visual hierarchy (headings, emphasis)
- REQ-UI-MOBILE-008: Native app feel (smooth animations, transitions)

---

## 8.2 Web App UI Requirements

### Provider Web Portal

**Screens:**

- **Dashboard:** Assigned patients, pending tasks, alerts
- **Patient Search:** Find patient, view records
- **Patient Details:** Complete medical history, add notes
- **Prescription:** View/add/edit prescriptions
- **Appointments:** Schedule, manage, send reminders
- **Reports:** Generate patient summary, export
- **Settings:** Configure availability, manage access

**UI/UX Requirements:**

- REQ-UI-WEB-PROV-001: Desktop-optimized layout
- REQ-UI-WEB-PROV-002: Keyboard shortcuts for power users
- REQ-UI-WEB-PROV-003: Customizable dashboard
- REQ-UI-WEB-PROV-004: Bulk operations (multi-select records)
- REQ-UI-WEB-PROV-005: Dark mode option (for low-light environments)

### Admin Web Portal

**Screens:**

- **Dashboard:** System health, facility config, and operational metrics
- **Audit Logs:** Searchable access logs
- **Reports:** Analytics and usage summaries within dashboard surfaces
- **User Management:** Optional Phase 2 extension if separated from dashboard

---

## 8.3 Accessibility Requirements (WCAG 2.2 AA)

### Vision Accessibility

- **REQ-ACCESS-VISION-001:** Minimum 4.5:1 contrast ratio for text
- **REQ-ACCESS-VISION-002:** Resizable text (up to 200%)
- **REQ-ACCESS-VISION-003:** High contrast mode toggle
- **REQ-ACCESS-VISION-004:** Color-blind friendly palette (no red-green alone)
- **REQ-ACCESS-VISION-005:** Text not communicated by color alone
- **REQ-ACCESS-VISION-006:** Screen reader compatible (semantic HTML, ARIA labels)
- **REQ-ACCESS-VISION-007:** Focus indicator visible (keyboard navigation)

### Hearing Accessibility

- **REQ-ACCESS-HEARING-001:** Captions for video content
- **REQ-ACCESS-HEARING-002:** Transcripts for audio consultations
- **REQ-ACCESS-HEARING-003:** Visual alerts (flashing for important notifications)
- **REQ-ACCESS-HEARING-004:** Alternative to audio alerts (haptic feedback, visual)

### Motor Accessibility

- **REQ-ACCESS-MOTOR-001:** Large touch targets (48x48 dp minimum)
- **REQ-ACCESS-MOTOR-002:** Keyboard-only navigation (no mouse required)
- **REQ-ACCESS-MOTOR-003:** Voice command support (hands-free)
- **REQ-ACCESS-MOTOR-004:** Ample white space (not crowded)
- **REQ-ACCESS-MOTOR-005:** No time-limited interactions (no flash/blink/auto-advance)

### Cognitive Accessibility

- **REQ-ACCESS-COGNITIVE-001:** Simple, clear language (avoid jargon)
- **REQ-ACCESS-COGNITIVE-002:** Consistent design patterns
- **REQ-ACCESS-COGNITIVE-003:** Clear error messages (what went wrong, how to fix)
- **REQ-ACCESS-COGNITIVE-004:** Predictable navigation
- **REQ-ACCESS-COGNITIVE-005:** No sudden context changes (no auto-submit)

---

## 8.4 Multilingual Support

### Language Requirements

- **REQ-UI-LANG-001:** Nepali (primary language, complete support)
- **REQ-UI-LANG-002:** English (secondary, all key features)
- **REQ-UI-LANG-003:** Maithili (eastern Nepal, key features) — Phase 2
- **REQ-UI-LANG-004:** Newari (Kathmandu Valley, key features) — Phase 2

### Localization (i18n) Implementation

- REQ-UI-I18N-001: String translation framework (gettext or i18n library)
- REQ-UI-I18N-002: Pseudo-localization for QA (testing)
- REQ-UI-I18N-003: Right-to-left (RTL) text support (if needed)
- REQ-UI-I18N-004: Date/time formatting (Nepali calendar support)
- REQ-UI-I18N-005: Number formatting (Nepali numerals option)
- REQ-UI-I18N-006: Currency display (NPR)

---

## 8.5 Health Literacy Design

### Plain Language Requirements

- **REQ-UI-LITERACY-001:** Define medical terms in plain language
- **REQ-UI-LITERACY-002:** Use illustrations for concepts
- **REQ-UI-LITERACY-003:** Medical term glossary (in-app)
- **REQ-UI-LITERACY-004:** Tooltips explaining features
- **REQ-UI-LITERACY-005:** Step-by-step wizards for complex tasks
- **REQ-UI-LITERACY-006:** Example data in forms

### Health Education Features

- **REQ-UI-LITERACY-007:** Health tips and education articles
- **REQ-UI-LITERACY-008:** Video tutorials (with captions)
- **REQ-UI-LITERACY-009:** FAQ section
- **REQ-UI-LITERACY-010:** Links to reliable health resources (MoHP, WHO Nepal)

---

# 9. Deployment & Infrastructure Requirements

## 9.1 Infrastructure Architecture

### Server Infrastructure (MVP)

```
Nepal-IX Datacenter (Primary)
├─ Load Balancer (Nginx/HAProxy)
│  └─ [Primary] [Secondary] — Dual redundancy
├─ Web Servers (Node.js + Express)
│  ├─ Server 1 (4 CPU, 8GB RAM)
│  └─ Server 2 (4 CPU, 8GB RAM) — Failover
├─ FHIR Server (HAPI FHIR)
│  └─ Dedicated server (8 CPU, 16GB RAM)
├─ Database (PostgreSQL Primary)
│  └─ (8 CPU, 32GB RAM, 500GB SSD)
├─ Database Replication (Read Replica)
│  └─ (4 CPU, 16GB RAM, 500GB SSD)
├─ Valkey Cache
│  └─ (2 CPU, 8GB RAM)
├─ File Storage (Ceph RADOS Gateway)
│  └─ (4 CPU, 16GB RAM, 2TB storage)
└─ Backup System
   └─ Daily encrypted backup (external 2TB drive)

Secondary Datacenter (Backup — Different location in Nepal)
└─ Standby infrastructure for disaster recovery
```

**Infrastructure Requirements:**

- REQ-INFRA-001: Redundant power supply (UPS, generator)
- REQ-INFRA-002: Redundant network connectivity
- REQ-INFRA-003: Environmental monitoring (temperature, humidity)
- REQ-INFRA-004: Physical security (controlled access, CCTV)
- REQ-INFRA-005: Colocation agreement with Nepal-IX

---

## 9.2 Network Requirements

### Network Security

- **REQ-NET-001:** Firewall (UFW on servers, hardware firewall)
- **REQ-NET-002:** VPN for provider access (OpenVPN)
- **REQ-NET-003:** All traffic encrypted (TLS 1.3 minimum)
- **REQ-NET-004:** API rate limiting (prevent DDoS)
- **REQ-NET-005:** WAF (Web Application Firewall) rules

### Network Performance

- **REQ-NET-006:** Low latency (<50ms within Nepal)
- **REQ-NET-007:** Sufficient bandwidth (10 Mbps redundant links)
- **REQ-NET-008:** CDN for static assets (optional, Cloudflare with Nepal jurisdiction support)

---

## 9.3 Deployment Strategy

### CI/CD Pipeline

- **REQ-DEPLOY-001:** Automated testing (unit, integration, e2e)
- **REQ-DEPLOY-002:** Code review before merge
- **REQ-DEPLOY-003:** Staged deployments (dev → staging → production)
- **REQ-DEPLOY-004:** Zero-downtime deployment
- **REQ-DEPLOY-005:** Rollback capability (revert bad deployments)
- **REQ-DEPLOY-006:** Deployment documentation and runbooks

### Deployment Process

```
Git Push
  ↓
Automated Tests (4 hours)
  ↓
Code Review (manual, 1 day)
  ↓
Merge to Main Branch
  ↓
Deploy to Staging (test environment)
  ↓
Manual QA Testing (2 days)
  ↓
Approval for Production
  ↓
Deploy to Production (during low-traffic window)
  ↓
Monitoring (24 hours post-deployment)
```

---

## 9.4 Configuration Management

### Environment Configuration

- **REQ-CONFIG-001:** Separate configs for dev, staging, production
- **REQ-CONFIG-002:** Use environment variables for secrets (API keys, DB passwords)
- **REQ-CONFIG-003:** Configuration management tool (Ansible or Terraform)
- **REQ-CONFIG-004:** Immutable infrastructure (rebuild servers from code)

---

# 10. Testing & Quality Assurance Requirements

## 10.1 Testing Strategy

### Unit Testing (MVP)

- **REQ-TEST-UNIT-001:** Minimum 80% code coverage
- **REQ-TEST-UNIT-002:** Test all business logic
- **REQ-TEST-UNIT-003:** Jest or Mocha for JavaScript testing
- **REQ-TEST-UNIT-004:** Run on every code commit

### Integration Testing (MVP)

- **REQ-TEST-INT-001:** Test API endpoints
- **REQ-TEST-INT-002:** Test database operations
- **REQ-TEST-INT-003:** Test external integrations (openIMIS, SMS, email)
- **REQ-TEST-INT-004:** Run on staging environment

### End-to-End Testing (MVP)

- **REQ-TEST-E2E-001:** Test complete user workflows (register → book appointment → consult)
- **REQ-TEST-E2E-002:** Selenium or Cypress for browser automation
- **REQ-TEST-E2E-003:** Test on real devices (iOS, Android)
- **REQ-TEST-E2E-004:** Test on multiple browsers (Chrome, Safari, Firefox)
- **REQ-TEST-E2E-005:** Run weekly, before each release

### Performance Testing (Phase 2)

- **REQ-TEST-PERF-001:** Load testing (concurrent users)
- **REQ-TEST-PERF-002:** Stress testing (beyond expected load)
- **REQ-TEST-PERF-003:** Endurance testing (sustained load over hours)
- **REQ-TEST-PERF-004:** Use Apache JMeter or k6

### Security Testing (MVP)

- **REQ-TEST-SEC-001:** Static analysis (SonarQube, Semgrep)
- **REQ-TEST-SEC-002:** Dynamic analysis (OWASP ZAP)
- **REQ-TEST-SEC-003:** Dependency scanning (Trivy)
- **REQ-TEST-SEC-004:** Penetration testing (annual, external firm)
- **REQ-TEST-SEC-005:** Vulnerability assessment

### Accessibility Testing (MVP)

- **REQ-TEST-ACC-001:** Automated accessibility checks (axe, WAVE)
- **REQ-TEST-ACC-002:** Manual screen reader testing (NVDA, JAWS)
- **REQ-TEST-ACC-003:** Keyboard navigation testing
- **REQ-TEST-ACC-004:** Color contrast verification

### Usability Testing (MVP)

- **REQ-TEST-UX-001:** User testing with 5+ participants per feature
- **REQ-TEST-UX-002:** Think-aloud protocol (observe and record)
- **REQ-TEST-UX-003:** Task-based scenarios
- **REQ-TEST-UX-004:** Collect feedback (SUS score, NPS)
- **REQ-TEST-UX-005:** Iterate based on findings

---

## 10.2 Quality Metrics

| Metric                       | Target      | Measurement                           |
| ---------------------------- | ----------- | ------------------------------------- |
| **Code Coverage**            | ≥80%        | SonarQube                             |
| **Bug Escape Rate**          | <1%         | Bugs found in production / total bugs |
| **Test Automation**          | 90%+        | Automated tests / total test cases    |
| **Uptime**                   | 99.5%       | Monitoring dashboard                  |
| **Page Load Time**           | <2s (p90)   | Real User Monitoring (RUM)            |
| **NPS Score**                | >50         | User surveys                          |
| **Accessibility Compliance** | WCAG 2.2 AA | Automated + manual audit              |

---

## 10.3 Release Management

### Release Cadence

- **REQ-RELEASE-001:** Major releases: Quarterly (every 3 months)
- **REQ-RELEASE-002:** Minor releases: Monthly feature updates
- **REQ-RELEASE-003:** Patch releases: As needed for bugs/security
- **REQ-RELEASE-004:** Hotfixes: Production issues, deploy within 4 hours

### Release Checklist

- [ ] All tests pass (unit, integration, e2e)
- [ ] Security scan clean (no vulnerabilities)
- [ ] Code review approved
- [ ] Database migration tested
- [ ] Release notes prepared
- [ ] User documentation updated
- [ ] Deployment plan finalized
- [ ] Rollback plan prepared
- [ ] Monitoring alerts configured
- [ ] Post-deployment testing planned

---

# 11. Operational Requirements

## 11.1 Monitoring & Alerting

### System Monitoring (MVP)

- **REQ-OPS-MON-001:** CPU usage monitoring
- **REQ-OPS-MON-002:** Memory usage monitoring
- **REQ-OPS-MON-003:** Disk space monitoring
- **REQ-OPS-MON-004:** Database query performance
- **REQ-OPS-MON-005:** Network I/O monitoring
- **REQ-OPS-MON-006:** Error rate monitoring

### Application Monitoring (MVP)

- **REQ-OPS-APP-001:** API response time (p50, p95, p99)
- **REQ-OPS-APP-002:** Error rates by endpoint
- **REQ-OPS-APP-003:** User session tracking
- **REQ-OPS-APP-004:** Feature usage analytics

### Alerting (MVP)

- **REQ-OPS-ALERT-001:** CPU >80% → alert
- **REQ-OPS-ALERT-002:** Memory >90% → alert
- **REQ-OPS-ALERT-003:** Disk >85% → alert
- **REQ-OPS-ALERT-004:** Error rate >1% → alert
- **REQ-OPS-ALERT-005:** API response time >2s (p95) → alert
- **REQ-OPS-ALERT-006:** Database down → emergency alert
- **REQ-OPS-ALERT-007:** Backup failure → alert

**Alert Delivery:**

- Slack channel (operations team)
- PagerDuty (escalation)
- SMS (critical issues)
- Email (logs and summaries)

---

## 11.2 Support & Maintenance

### Customer Support (MVP)

- **REQ-OPS-SUPPORT-001:** Email support (support@company.com)
- **REQ-OPS-SUPPORT-002:** Response time: <24 hours for normal, <4 hours for urgent
- **REQ-OPS-SUPPORT-003:** Support knowledge base (FAQ)
- **REQ-OPS-SUPPORT-004:** Support ticket tracking system
- **REQ-OPS-SUPPORT-005:** Escalation process for unresolved issues

### Maintenance Windows (MVP)

- **REQ-OPS-MAINT-001:** Regular maintenance: Monthly, Sunday 12-2 AM (low-traffic)
- **REQ-OPS-MAINT-002:** Emergency maintenance: As needed, with user notification
- **REQ-OPS-MAINT-003:** Maintenance window: ≤30 minutes
- **REQ-OPS-MAINT-004:** Advance notice: 1 week for non-emergency maintenance
- **REQ-OPS-MAINT-005:** Status page: www.status.company.com (real-time uptime)

---

## 11.3 Runbooks & Documentation

### Operational Runbooks

- **REQ-OPS-RUNBOOK-001:** Server restart procedure
- **REQ-OPS-RUNBOOK-002:** Database failover procedure
- **REQ-OPS-RUNBOOK-003:** Backup and restore procedure
- **REQ-OPS-RUNBOOK-004:** Deployment rollback procedure
- **REQ-OPS-RUNBOOK-005:** Incident response checklist
- **REQ-OPS-RUNBOOK-006:** Data breach response procedure
- **REQ-OPS-RUNBOOK-007:** Performance troubleshooting guide

### Documentation (MVP)

- **REQ-OPS-DOC-001:** API documentation (Swagger/OpenAPI)
- **REQ-OPS-DOC-002:** Architecture documentation
- **REQ-OPS-DOC-003:** Deployment guide
- **REQ-OPS-DOC-004:** Security guide
- **REQ-OPS-DOC-005:** Data model diagram (ERD)
- **REQ-OPS-DOC-006:** System design document
- **REQ-OPS-DOC-007:** Operations manual

---

# 12. Identified Gaps & Additions

## 12.1 Gaps Found in Original Requirements

### Gap Analysis & Additions

| Gap ID   | Category            | Description                                   | Impact | Recommendation                                              | Priority |
| -------- | ------------------- | --------------------------------------------- | ------ | ----------------------------------------------------------- | -------- |
| **G-01** | **User Management** | No user account recovery (forgotten password) | High   | Implement password reset via email + OTP                    | MVP      |
| **G-02** | **User Management** | No account deactivation/deletion process      | High   | Implement 30-day delete window with data archival           | MVP      |
| **G-03** | **Performance**     | No caching strategy documented                | High   | Implement Valkey caching for common queries                 | MVP      |
| **G-04** | **Scalability**     | No database sharding strategy                 | Medium | Plan sharding for 1M+ records (Phase 3)                     | Phase 2  |
| **G-05** | **Testing**         | No API versioning strategy                    | Medium | Implement API v1, v2 support for backwards compatibility    | Phase 2  |
| **G-06** | **Monitoring**      | No real-time health dashboard for ops team    | High   | Build ops dashboard (uptime, errors, performance)           | Phase 1  |
| **G-07** | **Documentation**   | No API rate limiting specification            | Medium | Implement 100 requests/min per user (configurable)          | Phase 1  |
| **G-08** | **Support**         | No self-service password reset                | High   | Add email-based password reset (OTP verification)           | MVP      |
| **G-09** | **Consent**         | No consent revocation interface for patients  | High   | Allow patient to revoke provider access anytime             | MVP      |
| **G-10** | **Data Quality**    | No data validation rules specified            | High   | Validate date ranges, medical codes, numeric ranges         | MVP      |
| **G-11** | **Sync**            | No offline sync conflict resolution           | Medium | Implement last-write-wins strategy with user notification   | Phase 1  |
| **G-12** | **Emergency**       | No Emergency SOS button specification         | Medium | 1-tap emergency contact calling + location sharing          | Phase 2  |
| **G-13** | **Analytics**       | No anonymized data export for research        | Medium | Support de-identified dataset export (with ethics approval) | Phase 3  |
| **G-14** | **Compliance**      | No data breach simulation/testing             | High   | Annual breach response drill (tabletop exercise)            | Phase 1  |
| **G-15** | **API**             | No webhook support for external systems       | Medium | Support webhooks for EMR integrations (real-time updates)   | Phase 2  |
| **G-16** | **Mobile**          | No app signing/certificate management         | High   | Implement proper app signing certificates (iOS/Android)     | MVP      |
| **G-17** | **Backup**          | No backup integrity verification              | High   | Weekly backup restoration test (verify recovery)            | Phase 1  |
| **G-18** | **Load Balancing**  | No session stickiness requirement             | Medium | Implement sticky sessions or distributed session store      | Phase 1  |
| **G-19** | **Frontend**        | No error boundary/crash reporting             | Medium | Implement Sentry for frontend error tracking                | Phase 1  |
| **G-20** | **Security**        | No rate limiting on login attempts (explicit) | High   | 5-attempt lockout, progressive delays (1s, 5s, 30s)         | MVP      |

---

## 12.2 New Requirements to Add

### A. Authentication & Password Management

**REQ-NEW-AUTH-001: Password Reset Flow**

```
Patient clicks "Forgot Password"
  ↓
Enter email address
  ↓
System sends reset link (expires in 24 hours)
  ↓
Patient clicks link, enters new password
  ↓
Confirm password reset, redirected to login
  ↓
2FA: Send OTP to phone (optional)
  ↓
Login successful
```

**REQ-NEW-AUTH-002: Account Deletion**

```
Patient requests account deletion
  ↓
Confirmation email sent
  ↓
30-day waiting period (grace period)
  ↓
Patient confirms deletion in email link
  ↓
All personal data deleted (audit logs retained)
  ↓
Account marked as deleted
```

**REQ-NEW-AUTH-003: Session Management**

- Session timeout: 15 minutes for providers, 30 minutes for patients
- Session refresh: Auto-refresh on activity
- Multiple sessions: Allow 1 active session per device, option to invalidate other sessions
- Session history: Log all login/logout events with device info

---

### B. Consent & Data Sharing

**REQ-NEW-CONSENT-001: Granular Consent Model**

```
Patient grants consent to Provider:
├─ Data types: All / Specific (medications, lab results, notes)
├─ Time period: Indefinite / Limited (6 months / 1 year)
├─ Purpose: Consultation / Treatment / Emergency
├─ Revocation: Anytime (with 7-day notice for ongoing treatment)
└─ Audit: Patient can see who accessed what data and when
```

**REQ-NEW-CONSENT-002: Consent Withdrawal**

- Patient can revoke provider access immediately
- Provider gets notification of revoked access
- System removes provider's access (no future queries)
- Existing data accessed before revocation remains in logs (audit trail)

**REQ-NEW-CONSENT-003: Emergency Access Override**

- First responders can access emergency summary with emergency PIN
- Emergency access bypasses normal consent (for life-threatening situations)
- Emergency access fully logged with timestamp and responder info
- Patient notified of emergency access post-incident

---

### C. Data Quality & Validation

**REQ-NEW-VALID-001: Medical Data Validation**

- Date of birth: Not in future, not >120 years old
- Blood pressure: Systolic 40-300 mmHg, Diastolic 20-180 mmHg
- Weight: 0.5-600 kg
- Height: 50-250 cm
- Blood glucose: 20-600 mg/dL
- Cholesterol: 0-1000 mg/dL
- Medication dose: Cannot be zero, must be reasonable amount
- ICD-10 codes: Must match official WHO list

**REQ-NEW-VALID-002: Provider Verification**

- Doctor license verification via Nepal Medical Council
- Nurse license verification via Nursing Council
- License validity checked quarterly
- Automatic account disable if license expired

**REQ-NEW-VALID-003: Facility Verification**

- Facility registration checked against DoHS registry
- Valid facility types: Hospital, Clinic, Lab, Pharmacy, Imaging Center
- Facility contact info required (phone, address, GPS)
- Annual re-verification

---

### D. Notifications

**REQ-NEW-NOTIF-001: Notification Center**

- In-app notification center (history of all notifications)
- Notification categories (appointments, medications, alerts, messages)
- Filter/search notifications
- Mark as read/unread
- Delete old notifications

**REQ-NEW-NOTIF-002: Notification Preferences (Advanced)**

- Per-notification-type preferences (enable/disable)
- Delivery method preferences (push, SMS, email)
- Do Not Disturb hours
- Quiet mode (no notifications except emergencies)

**REQ-NEW-NOTIF-003: SMS Fallback**

- If push fails, retry via SMS
- SMS for critical alerts (emergency, medication missed)
- SMS confirmation link for appointment reminders

---

### E. Offline & Sync

**REQ-NEW-OFFLINE-001: Offline Data Sync**

- Queue for failed requests (stored locally)
- Auto-retry when connectivity restored
- Conflict resolution: Last-write-wins with user notification
- Show sync status indicator
- Allow manual sync button

**REQ-NEW-OFFLINE-002: Offline Data Integrity**

- Verify data integrity after sync
- Flag conflicts for user review (manual resolution)
- Log all sync events for audit

**REQ-NEW-OFFLINE-003: Offline Limitations Disclosure**

- Display banner when offline: "Some features not available"
- Show which features are unavailable offline
- Estimate when sync will occur

---

### F. Emergency Features

**REQ-NEW-EMERGENCY-001: SOS Button (Phase 2)**

```
Patient taps SOS button
  ↓
Immediate screen displays:
├─ Emergency medical summary (allergies, blood type, meds)
├─ Emergency contacts (with one-tap calling)
├─ GPS location (shareable with first responders)
├─ Current medications list
└─ Do Not Resuscitate (DNR) status if applicable
  ↓
Automatic SMS/notification sent to emergency contacts:
"[Patient] activated SOS. Location: [GPS]. Emergency: [reason]"
  ↓
First responders can access emergency summary via QR code
```

**REQ-NEW-EMERGENCY-002: Emergency Mode (Bypass)** (Phase 2)

- Access emergency summary without authentication (emergency PIN only)
- First respader authentication: via SMS + phone number
- QR code on patient emergency card → access emergency info
- Limited data exposure (only critical info, not full history)

---

### G. Analytics & Insights

**REQ-NEW-ANALYTICS-001: Personal Health Dashboard (Phase 3)**

- Health score (composite metric: 0-100)
- Risk indicators (cardio risk, diabetes risk, etc.)
- Medication adherence rate
- Appointment attendance rate
- Preventive care completion status
- Health trends (BMI, BP over time)

**REQ-NEW-ANALYTICS-002: AI Health Insights (Phase 4)**

- Personalized health recommendations based on age, conditions, risk factors
- Medication effectiveness tracking (user reports symptoms improving/worsening)
- Drug interaction alerts (AI-powered, more comprehensive)
- Preventive care reminders (age-appropriate screenings)
- Lifestyle recommendations (based on health data)

---

### H. Accessibility & Design

**REQ-NEW-ACCESS-001: Simplified Mode (for elderly/low-literacy users)**

- Hide advanced options
- Larger text (18pt+)
- Fewer menu items (most common actions)
- Audio guidance for all screens
- Step-by-step wizards for complex tasks

**REQ-NEW-ACCESS-002: Voice Navigation**

- Voice commands: "Show my medications," "Schedule appointment," "Call doctor"
- Voice confirmation for actions: "Are you sure you want to delete this record?"
- Voice feedback: System reads out actions and confirmations

**REQ-NEW-ACCESS-003: Color & Text Customization**

- Custom font sizes (12-24pt)
- Color theme: Light, Dark, High Contrast
- Dyslexia-friendly font option (OpenDyslexic)
- Text spacing: Normal, Wide, Very Wide

---

### I. Medical Record Enhancements

**REQ-NEW-MED-001: Record Versioning**

- Track changes to records (who edited what, when)
- Show before/after comparison
- Revert to previous version (with audit trail)
- Reason for edit field (optional but recommended)

**REQ-NEW-MED-002: Record Comments/Collaboration**

- Provider can add comments to records
- Patient can comment on records
- Discussion thread for each record
- @mentions to notify specific people
- Comment history with edit tracking

**REQ-NEW-MED-003: Record Attachments**

- Attach multiple file types (PDF, images, audio, video)
- Video uploads for patient-recorded symptoms
- Audio uploads for voice notes
- File size limit per attachment (50MB max)
- Virus scanning on upload

---

### J. Export & Reporting

**REQ-NEW-EXPORT-001: Scheduled Exports**

- Auto-export medical records weekly/monthly
- Delivery method: Email, cloud backup (Google Drive, Dropbox)
- Format: FHIR JSON, PDF, CSV
- Retention: 1 year of exports
- Notification when export complete

**REQ-NEW-EXPORT-002: Custom Report Generator**

- Select date range
- Select record types (medications, labs, visits)
- Select fields to include
- Format: PDF, Excel, CSV
- Add signature line for printing

**REQ-NEW-EXPORT-003: Annual Health Summary**

- Automatic generation on anniversary date
- Include: Top diagnoses, medications, providers, costs
- Visuals: Charts, trends
- Deliverable: PDF + email
- User can regenerate anytime

---

### K. Provider Tools

**REQ-NEW-PROVIDER-001: Provider Dashboard Customization**

- Drag-and-drop widgets
- Filter patients by facility, department, condition
- Saved views (e.g., "My Diabetic Patients")
- Bulk actions on patient list (mass messaging, reminders)

**REQ-NEW-PROVIDER-002: Template Notes**

- Reusable clinical note templates (for common conditions)
- Auto-populate with patient data where applicable
- Drag-and-drop template builder
- Share templates across facility

**REQ-NEW-PROVIDER-003: Decision Support**

- Drug interaction checker (built-in)
- Dosage calculator (for age, weight)
- Drug-disease interaction checker
- Guideline recommendations (for common conditions)
- Lab result interpretation tool

---

### L. Compliance & Governance

**REQ-NEW-COMPLY-001: Data Subject Access Request (DSAR)**

- Patient can request copy of all personal data (GDPR-style)
- System generates DSAR report (all records, logs, metadata)
- Deliver as FHIR export + CSV + PDF
- Fulfill within 30 days
- Track all DSARs and fulfillment

**REQ-NEW-COMPLY-002: Audit Report Generation**

- Generate compliance report for regulators (MoHP, HIB)
- Include: User access logs, data modifications, security events
- Time period: Last 3 months, 1 year, custom
- Format: PDF report + raw CSV

**REQ-NEW-COMPLY-003: Privacy Policy & Terms**

- Versioned privacy policy (track changes)
- User acceptance logging (when user accepted which version)
- Multi-language support (Nepali, English)
- Plain-language summary
- Link to full legal text

---

### M. Marketing & User Acquisition

**REQ-NEW-MARKETING-001: Referral Program**

- Patient refers friend → both get incentive (NPR 100-500)
- Referral link: Unique per user
- Tracking: Referrer → Referred-to linking
- Redemption: Credit in account, withdrawal to wallet

**REQ-NEW-MARKETING-002: In-App Messaging**

- In-app banners for product announcements
- Pop-up tips for new features (first-time users)
- Email campaigns (newsletters, feature announcements)
- Push notifications (limited, for important updates)
- Unsubscribe option for all

**REQ-NEW-MARKETING-003: Onboarding Flow**

- Interactive tutorial on first login
- Checklist: Complete profile → Add record → Schedule appointment
- Incentive: Unlock features as you complete actions
- Skip option: Advanced users can skip

---

## 12.3 Phase-Wise Requirement Breakdown

### MVP (Phase 1, Months 1-6): Core Foundation

**Count: 150+ requirements**

**Must Include:**

- Patient registration & profile
- Medical record import/storage
- Medication management
- Appointment scheduling & reminders
- Insurance eligibility checking
- FHIR API gateway
- Security audit readiness
- Nepali language support
- Mobile app (iOS, Android)
- Web provider portal
- Desktop app shell for provider/admin and assisted workflows

**Can Defer to Phase 2+:**

- Telemedicine (video/audio/transcription)
- Insurance claims
- Custom ASR model
- Wearable integration
- Caregiver/family profiles
- Offline sync
- AI insights
- Advanced analytics

---

### Phase 2 (Months 7-12): MVP+

**Count: 50+ new requirements**

**Add:**

- Offline-first capability
- Caregiver/family profiles
- Medication refill integration
- Lab integration
- Imaging (DICOM viewer)
- Referral management
- Advanced notifications
- Payment integration
- Health literacy features
- Accessibility hardening and extended UX coverage (WCAG 2.2 AA baseline)
- Maithili, Newari language support
- SOS button
- Emergency medical summary

---

### Phase 3 (Months 13-18): Extended Features

**Count: 30+ new requirements**

**Add:**

- Custom Nepali ASR model (deployment)
- FCHV integration
- Wearable device support
- Anonymous data export (research)
- Government procurement support
- HMIS integration
- Advanced analytics dashboard
- User-generated content (comments, attachments)
- Record versioning
- Template notes for providers
- Scheduled exports

---

### Phase 4+ (Months 19-24): Advanced & AI

**Count: 20+ new requirements**

**Add:**

- Predictive health modeling
- AI health insights & recommendations
- IoT device integration
- Advanced decision support
- Regional expansion (India, Bangladesh)
- APIs for third-party apps
- Marketplace for health apps

---

## 12.4 Requirement Prioritization Matrix

| Priority              | Count | Examples                                                      | Timeline      |
| --------------------- | ----- | ------------------------------------------------------------- | ------------- |
| **P0 — Critical**     | 80    | Auth, FHIR, encryption, audit logs, registration              | MVP (Month 6) |
| **P1 — High**         | 70    | Medical records, medications, appointments, telemedicine, ASR | MVP + Phase 2 |
| **P2 — Medium**       | 60    | Offline sync, caregiver, analytics, integrations              | Phase 2       |
| **P3 — Low**          | 50    | AI insights, advanced features, marketplace                   | Phase 3+      |
| **P4 — Nice-to-Have** | 40    | Customization, advanced personalization                       | Phase 4+      |

---

# 13. Global Compliance Cross-Reference (Added in v2.0)

Every requirement category maps to one or more international standards. This cross-reference ensures that Nepal PHR is aligned with proven global approaches and is positioned for future cross-border interoperability.

| Requirement Category      | Nepal Regulation                      | International Standard                      | Global PHR Precedent                                          | Notes                                                       |
| ------------------------- | ------------------------------------- | ------------------------------------------- | ------------------------------------------------------------- | ----------------------------------------------------------- |
| **Patient registration**  | Directive 2081 (patient ID)           | FHIR R4 Patient resource                    | India ABDM (ABHA ID), Estonia (eID)                           | Federated patient ID linked to NID but not dependent on it  |
| **Medical records**       | Directive 2081 (21 modules)           | FHIR R4 (Encounter, Condition, Observation) | Australia MHR (document types)                                | ICD-10 for conditions, LOINC for labs from Day 1            |
| **Consent management**    | Privacy Act 2075 (explicit consent)   | FHIR R4 Consent resource                    | Estonia X-Road (audit-visible consent)                        | Patient-visible audit log for all consent decisions         |
| **Data sovereignty**      | Directive 2081 (Nepal-only storage)   | —                                           | India ABDM (data localization), EU GDPR                       | All primary storage and processing within Nepal borders     |
| **Insurance integration** | HIB/openIMIS mandate                  | FHIR R4 Claim, Coverage, EligibilityRequest | openIMIS IG (Tanzania/Nepal)                                  | Follow openIMIS Implementation Guide exactly                |
| **Security audit**        | Directive 2081 (annual audit)         | OWASP Top 10, ISO 27001                     | UK NHS (Cyber Essentials Plus)                                | SAST + DAST + penetration test in CI/CD pipeline            |
| **Accessibility**         | —                                     | WCAG 2.2 AA                                 | UK NHS App (20+ languages), WHO SMART (equity)                | Nepali + English; future: Maithili, Newari, Tharu           |
| **Offline capability**    | —                                     | —                                           | Rwanda/Kenya OpenMRS (offline-first)                          | Encrypted SQLite, conflict resolution, freshness indicators |
| **Voice input (ASR)**     | —                                     | —                                           | India (eSanjeevani voice), WHO SMART (low-literacy)           | Vosk + Whisper; 85%+ accuracy target for medical Nepali     |
| **Emergency access**      | —                                     | —                                           | Australia MHR (emergency function), Estonia (break-the-glass) | Emergency QR card with privacy-preserving summary           |
| **Telemedicine**          | Nepal Telemedicine Guidelines (draft) | —                                           | India eSanjeevani (150M+ consults), UK NHS (video consult)    | Audio-only fallback for 2G/EDGE networks                    |
| **Caregiver/family**      | —                                     | FHIR R4 RelatedPerson                       | UK NHS (Proxy access), India ABDM (Aadhaar-linked family)     | NRN diaspora health corridor (cross-timezone support)       |

# 14. Critical New Requirements (Added in v2.0)

These requirements address gaps identified during global PHR analysis and are not covered in the original 450+ requirements.

## 14.1 Emergency QR Health Card (P1 — Core MVP)

| ID                     | Requirement                                                                       | Acceptance Criteria                                                                |
| ---------------------- | --------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------- |
| `REQ-EMERGENCY-QR-001` | System shall generate a printable QR code encoding emergency medical summary      | QR contains: blood type, active allergies, current medications, emergency contacts |
| `REQ-EMERGENCY-QR-002` | QR code shall be scannable by any smartphone camera without requiring the PHR app | Standard QR code renders a minimal HTML page with emergency data                   |
| `REQ-EMERGENCY-QR-003` | QR-encoded data shall NOT contain full medical history (privacy-preserving)       | Only life-critical data elements are included; patient controls which fields       |
| `REQ-EMERGENCY-QR-004` | QR code shall refresh when underlying data changes                                | QR regenerated on allergy, medication, or emergency contact updates                |

**Global precedent:** Australia My Health Record emergency access, India ABHA health card.

## 14.2 FCHV Digital Health Bridge (P2 — Phase 2)

| ID             | Requirement                                                                                           | Acceptance Criteria                                               |
| -------------- | ----------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------- |
| `REQ-FCHV-001` | System shall provide a simplified registration flow for FCHVs to register patients during home visits | Icon-based navigation, voice input, QR-based patient registration |
| `REQ-FCHV-002` | FCHV flow shall capture basic health data (vitals, symptoms, medication adherence)                    | Data is stored locally and synced when connectivity available     |
| `REQ-FCHV-003` | FCHV role shall have restricted access (cannot view full medical history)                             | FCHV sees only data they collected + aggregate health status      |

**Global precedent:** Rwanda CHW tablet program (55,000 CHWs), Kenya community health digitization.

## 14.3 Security Hardening (P0 — Core MVP)

| ID                   | Requirement                                                                           | Acceptance Criteria                                                    |
| -------------------- | ------------------------------------------------------------------------------------- | ---------------------------------------------------------------------- |
| `REQ-SEC-OWASP-001`  | All OWASP Top 10 (2021) risks shall be addressed with documented controls             | Each OWASP risk has a mapped control and automated verification        |
| `REQ-SEC-DPIA-001`   | A Data Protection Impact Assessment shall be completed before processing patient data | DPIA document covering all FHIR resources with PII                     |
| `REQ-SEC-SAST-001`   | Static Application Security Testing shall run on every code commit                    | CI/CD gate blocks merge on critical/high findings                      |
| `REQ-SEC-DAST-001`   | Dynamic Application Security Testing shall run against staging on every release       | OWASP ZAP or equivalent; no critical findings allowed                  |
| `REQ-SEC-DEPS-001`   | Automated dependency vulnerability scanning with 72-hour SLA for critical CVEs        | Trivy or OWASP Dependency-Check in CI/CD                               |
| `REQ-SEC-KEYROT-001` | Encryption key rotation every 90 days with zero-downtime procedure                    | Documented and tested key rotation runbook                             |
| `REQ-SEC-BREACH-001` | 72-hour breach notification procedure operational before launch                       | Procedure documented, tested with tabletop exercise, contacts verified |

## 14.4 Performance Targets (P1 — Core MVP)

| ID                    | Requirement                                                 | Acceptance Criteria                           |
| --------------------- | ----------------------------------------------------------- | --------------------------------------------- |
| `REQ-PERF-API-001`    | API response time p95 ≤ 300ms for read operations           | Load test verified with 1000 concurrent users |
| `REQ-PERF-API-002`    | API response time p95 ≤ 500ms for write operations          | Load test verified with 500 concurrent users  |
| `REQ-PERF-PAGE-001`   | Page load time ≤ 2 seconds on 3G connection (1.6 Mbps)      | Lighthouse audit score ≥ 80 on mobile         |
| `REQ-PERF-SEARCH-001` | Patient search returns results ≤ 500ms for up to 1M records | Database query plan verified; indexed search  |

---

# Summary

This comprehensive end-to-end requirements document covers:

✅ **450+ detailed requirements** organized by category
✅ **All gaps identified** in original feature list + report
✅ **New requirements added** for critical missing features
✅ **Phase-wise breakdown** (MVP, Phase 2, Phase 3, Phase 4)
✅ **Priority classification** (P0-P4)
✅ **Implementation guidance** for development teams
✅ **Security & compliance** requirements (Directive 2081, Privacy Act)
✅ **Non-functional requirements** (performance, scalability, availability)
✅ **Testing strategy** (unit, integration, e2e, security, accessibility)
✅ **Operational requirements** (monitoring, support, maintenance)
✅ **Data requirements** (entities, storage, encryption, lifecycle)

**Added in v2.0:**

✅ **Global compliance cross-reference** mapping every requirement category to international standards and proven global PHR systems
✅ **Emergency QR health card** requirements (4 requirements, P1)
✅ **FCHV digital health bridge** requirements (3 requirements, P2)
✅ **Security hardening** requirements (7 requirements, P0) covering OWASP, DPIA, SAST/DAST, key rotation, breach notification
✅ **Performance targets** with specific latency SLAs (4 requirements, P1)

**Next Step:** Use this document as your detailed specification for:

- Development sprint planning
- Architecture design
- QA test case creation
- Vendor/contractor SOW
- Investor & regulator communication

---

_End-to-End Requirements Document v2.0 — Ready for Implementation Planning_
