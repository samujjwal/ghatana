# Product Vision Document - PHR Nepal

## 1. Product Identification

| Attribute | Value |
|-----------|-------|
| **Product Name** | PHR Nepal — Personal Health Records |
| **Version** | 1.0.0 |
| **Group** | com.ghatana.products |
| **Status** | Alpha — Core Implementation Complete |
| **Stack** | Java 21 + ActiveJ · React 19 + Tailwind CSS · React Native (Expo planned) |
| **Owner** | @ghatana/phr-team |

---

## 2. Executive Summary

**Observed in code** - PHR Nepal is a personal health records application for the Nepal market. It provides patients and healthcare providers with a secure, interoperable platform for managing medical records, prescriptions, lab results, appointment history, clinical notes, imaging, immunizations, referrals, caregiver access, telemedicine, and emergency break-glass access.

**Core Value Proposition**: 
- **Patient-centric**: Patients control their health data with granular consent
- **Interoperable**: FHIR R4 standards for health data exchange
- **Compliant**: Nepal Directive 2081, Nepal Privacy Act 2075, HIPAA
- **AI-augmented**: Clinical decision support through AI agents

**Evidence**: `@/home/samujjwal/Developments/ghatana/products/phr/README.md:7-10`

---

## 3. Problem Statement

**Inferred from implementation** - Healthcare in Nepal faces:
- Fragmented patient records across providers
- Lack of patient control over health data
- Privacy and consent management challenges
- Need for regulatory compliance (Nepal Directive 2081)
- Limited interoperability between healthcare systems
- Emergency access requirements with audit requirements

**Evidence**: Comprehensive service implementation addressing each of these needs.

---

## 4. Vision

**Observed in code** - "Personal Health Records application for the Nepal market providing secure, interoperable platform for managing medical records with patient consent and regulatory compliance."

**Long-term Vision**: 
- Nepal's national health records platform enabling:
  - Patient-controlled health data with granular consent
  - Seamless provider-to-provider data sharing
  - AI-powered clinical insights
  - Compliance with evolving healthcare regulations
  - Integration with Nepal Health Information Exchange (HIE)

---

## 5. Goals

### Primary Goals (Observed Implementation)

| Goal | Evidence | Status |
|------|----------|--------|
| Patient record management with FHIR R4 | `PhrCapabilities.PATIENT_RECORDS`, `PatientRecordService.java` | ✅ Complete |
| Consent lifecycle management | `ConsentManagementService.java`, Nepal Directive 2081 compliance | ✅ Complete |
| FHIR R4 transformation engine | `FhirR4TransformationEngine.java` | ✅ Complete |
| Clinical document management | `DocumentService.java`, OCR support | ✅ Complete |
| Medication tracking | `MedicationService.java` | ✅ Complete |
| Appointment scheduling | `AppointmentService.java` | ✅ Complete |
| Privacy/security compliance | `PHRSecurityManagerImpl`, `PHRPrivacyManagerImpl` | ✅ Complete |
| AI/ML clinical decision support | Lab anomaly, medication interaction, readmission agents | ✅ Complete |

### Secondary Goals (Implementation Status)

| Goal | Evidence | Status |
|------|----------|--------|
| FHIR Server Endpoint | Listed as "Planned" in README | ❌ Not Implemented |
| Mobile App (React Native) | Listed as "Planned" | ❌ Not Started |
| Nepal HIE Integration | Listed as "Planned" | ❌ Not Started |

---

## 6. Target Users / Personas

**Observed in service layer**:

| Persona | Role | Features Used |
|---------|------|---------------|
| **Patient** | Individual health record owner | View records, manage consent, appointments, caregiver delegation |
| **Healthcare Provider** | Doctor, nurse, clinic | Access patient records (with consent), create clinical notes, prescribe medications |
| **Caregiver** | Family member, guardian | Delegated access to patient records |
| **Emergency Personnel** | Emergency room staff | Break-glass access with mandatory audit |
| **Lab Technician** | Diagnostic staff | Upload lab results, imaging |
| **Administrator** | Hospital/clinic admin | Appointment scheduling, billing |
| **Compliance Officer** | Auditor, regulator | Audit trails, retention policy enforcement |

---

## 7. Value Proposition

**Observed in code and documentation**:

### For Patients:
- **Control**: Granular field-level consent per Nepal Privacy Act 2075
- **Portability**: FHIR R4 enables record transfer between providers
- **Access**: 24/7 access to health records via web (mobile planned)
- **Transparency**: Full audit trail of who accessed what data

### For Healthcare Providers:
- **Interoperability**: FHIR R4 standard for data exchange
- **Decision Support**: AI agents for lab anomaly detection, medication interactions
- **Compliance**: Built-in Nepal Directive 2081 and HIPAA compliance
- **Efficiency**: Digital workflows for prescriptions, referrals, appointments

### For Healthcare System:
- **Data Retention**: Automated retention policies (25 years for clinical data)
- **Auditability**: Immutable audit trail for compliance
- **Emergency Access**: Break-glass capability with post-access review
- **National Integration**: HIE-ready architecture

---

## 8. Scope

### In Scope (Observed Implementation)

**Core Services (15 registered in `PhrKernelModule`)**:
- `PatientRecordService` - Patient demographics, health history
- `ConsentManagementService` - Consent lifecycle with distributed cache
- `DocumentService` - Clinical documents with OCR
- `AppointmentService` - Scheduling, reminders, rescheduling
- `MedicationService` - Prescriptions, refills, interaction checks
- `LabResultService` - Lab reports with LOINC codes
- `ImmunizationService` - Vaccination records with CVX codes
- `ClinicalNoteService` - Provider clinical documentation
- `ClinicalDecisionSupportService` - AI-powered insights
- `ImagingService` - Medical imaging orders and studies
- `ReferralService` - Provider-to-provider referrals
- `BillingService` - Encounters and insurance claims
- `TelemedicineService` - Virtual consultation sessions
- `CaregiverService` - Delegated access management
- `EmergencyAccessLogService` - Break-glass audit logging

**Domain Capabilities (6 PHR-specific)**:
- `phr.patient-records` - FHIR R4 and Nepal-2081 compliance
- `phr.consent-management` - Field-level consent
- `phr.fhir-interop` - FHIR R4 resources
- `phr.clinical-documents` - Document management with OCR
- `phr.medication-management` - Prescription tracking
- `phr.appointment-scheduling` - Scheduling workflows

**Security & Compliance**:
- Nepal Directive 2081 compliance
- Nepal Privacy Act 2075 consent model
- HIPAA compatibility for international standards
- 25-year retention for clinical data
- Permanent retention for immunizations and emergency access logs

### Out of Scope (Observed)

| Item | Status | Notes |
|------|--------|-------|
| FHIR Server Endpoint | Planned | Only transformation engine exists |
| Mobile Application | Planned | React Native scaffold not started |
| Nepal HIE Integration | Planned | Interface design pending |
| EDI Clearinghouse | Out of Scope | Listed as "out of scope for v1" |

---

## 9. Non-Goals

**Observed in README**:
- EDI clearinghouse integration (billing side) - out of scope for v1
- Internationalization beyond Nepal market - not addressed
- Non-healthcare data management - product scope limited to health records

---

## 10. Maturity Assessment

| Dimension | Rating | Evidence |
|-----------|--------|----------|
| Backend Implementation | High | 15 services complete, kernel integration done |
| Security/Privacy | High | PHRSecurityManager, PHRPrivacyManager complete |
| FHIR Compliance | High | R4 transformation engine complete |
| AI Integration | Medium-High | 3 agents implemented, more planned |
| Frontend | Not Started | React 19 + Tailwind planned but not observed |
| Mobile | Not Started | React Native scaffold not started |
| Test Coverage | Medium | 16 test files, more needed for full coverage |
| Documentation | High | Comprehensive README, capability definitions |

---

## 11. Strategic Risks

| Risk | Likelihood | Impact | Evidence/Mitigation |
|------|------------|--------|---------------------|
| FHIR Server delay | Medium | High | Listed as "Planned" - blocks API consumers |
| Mobile app timeline | Medium | Medium | Not started - limits patient adoption |
| Nepal HIE changes | Medium | Medium | Interface pending - dependency on external spec |
| Regulatory changes | Low | Medium | Nepal Directive 2081 compliance built-in |
| AI agent accuracy | Medium | Medium | Clinical decision support requires validation |

---

## 12. Known Unknowns

| Unknown | Impact | Path to Resolution |
|---------|--------|-------------------|
| FHIR Server architecture | High | Design needed for Patient/Provider APIs |
| Mobile UX requirements | Medium | Patient/Provider app design needed |
| HIE integration spec | Medium | Coordination with Nepal MoHP |
| Production load patterns | Medium | Load testing with simulated patient volumes |
| AI agent clinical validation | High | Clinical trials for decision support |

---

## 13. Evidence Basis

**Primary Source Code**:
- `@/home/samujjwal/Developments/ghatana/products/phr/src/main/java/com/ghatana/phr/kernel/PhrKernelModule.java`
- `@/home/samujjwal/Developments/ghatana/products/phr/src/main/java/com/ghatana/phr/kernel/PhrCapabilities.java`
- `@/home/samujjwal/Developments/ghatana/products/phr/src/main/java/com/ghatana/phr/kernel/service/ConsentManagementService.java`
- `@/home/samujjwal/Developments/ghatana/products/phr/src/main/java/com/ghatana/phr/fhir/FhirR4TransformationEngine.java`
- `@/home/samujjwal/Developments/ghatana/products/phr/src/main/java/com/ghatana/phr/security/PHRSecurityManagerImpl.java`

**Build Configuration**:
- `@/home/samujjwal/Developments/ghatana/products/phr/build.gradle.kts`

**Documentation**:
- `@/home/samujjwal/Developments/ghatana/products/phr/README.md`
- `@/home/samujjwal/Developments/ghatana/products/phr/PHR_KERNEL_INTEGRATION_README.md`

**Schema Contracts (Observed in `PhrKernelModule`)**:
- `phr.patient.records`
- `phr.consent.grants`
- `phr.medications`
- `phr.lab.results`
- `phr.immunizations`
- `phr.clinical.notes`
- `phr.emergency.access.log`
- Plus 8 additional schema contracts

---

*Status: Evidence-based with clear provenance. Gaps explicitly noted where implementation is planned but not complete.*
