# System Architecture Overview - PHR Nepal

## 1. System Context

**Observed in code** - PHR Nepal is a Personal Health Records application built on the Kernel Platform, providing healthcare data management with FHIR R4 interoperability, Nepal regulatory compliance, and AI-powered clinical decision support.

### Context Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                        External Systems                          │
│  (FHIR Servers, Nepal HIE - planned, Labs, Imaging Centers)   │
└───────────────────────────────┬─────────────────────────────────┘
                                │ FHIR/HL7
┌───────────────────────────────▼─────────────────────────────────┐
│                      PHR Nepal Application                       │
│                                                                  │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────────────┐ │
│  │   Patient    │  │   Provider   │  │     Admin/Staff        │ │
│  │     UI       │  │      UI      │  │         UI             │ │
│  └──────────────┘  └──────────────┘  └────────────────────────┘ │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │              PHR Kernel Module (15 Services)               ││
│  │  • PatientRecordService    • ConsentManagementService     ││
│  │  • DocumentService         • AppointmentService             ││
│  │  • MedicationService       • LabResultService             ││
│  │  • ImmunizationService     • ClinicalNoteService          ││
│  │  • ClinicalDecisionSupport • ImagingService               ││
│  │  • ReferralService         • BillingService               ││
│  │  • TelemedicineService     • CaregiverService             ││
│  │  • EmergencyAccessLogService                              ││
│  └─────────────────────────────────────────────────────────────┘│
│                                                                  │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────────────┐   │
│  │   Security   │  │    FHIR    │  │      AI Agents         │   │
│  │   (PHR*)     │  │    R4      │  │ • Lab Anomaly          │   │
│  │              │  │ Transform  │  │ • Medication Interact  │   │
│  └──────────────┘  └──────────────┘  │ • Readmission Risk     │   │
│                                         └────────────────────────┘   │
└──────────────────────────────────────────────────────────────────┘
                                │
                                │ Kernel Platform Services
┌───────────────────────────────▼─────────────────────────────────┐
│                     Kernel Platform                              │
│  (Lifecycle, Registry, Capabilities, Context, DataCloud)       │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2. Primary Components

**Observed in source structure** (`src/main/java/com/ghatana/phr/`):

| Component | Responsibility | Key Files |
|-----------|---------------|-----------|
| **Kernel Module** | Product composition root | `kernel/PhrKernelModule.java` |
| **Capabilities** | PHR feature declarations | `kernel/PhrCapabilities.java` |
| **Service Catalog** | Service organization | `kernel/service/PhrServiceCatalog.java` |
| **Clinical Services** | Patient care services | `kernel/service/PatientRecordService.java`, `ClinicalNoteService.java`, etc. |
| **Administrative Services** | Scheduling, billing | `AppointmentService.java`, `BillingService.java`, etc. |
| **Patient Services** | Consent, medications | `ConsentManagementService.java`, `MedicationService.java` |
| **Emergency Services** | Break-glass access | `EmergencyAccessLogService.java` |
| **FHIR Engine** | R4 transformation | `fhir/FhirR4TransformationEngine.java` |
| **Security** | AuthN/AuthZ | `security/PHRSecurityManagerImpl.java` |
| **AI Agents** | Clinical decision support | `ai/agents/LabAnomalyDetectionAgent.java`, etc. |
| **Observability** | Audit trails | `observability/PHRAuditTrailServiceImpl.java` |

---

## 3. Data Flow

### Patient Record Access Flow

```
┌──────────────┐
│    User      │
│   Request    │
└──────┬───────┘
       │
       ▼
┌─────────────────────────────────────────────────────────┐
│  1. AUTHENTICATION & AUTHORIZATION                      │
│     - PHRSecurityManager validates credentials          │
│     - PHRPrivacyManager checks consent                  │
└─────────────────────────────────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────────────────────────┐
│  2. CONSENT VALIDATION                                  │
│     - ConsentManagementService.checkAccess()            │
│     - Validates patient consent for resource type        │
│     - Emergency access with break-glass if needed      │
└─────────────────────────────────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────────────────────────┐
│  3. DATA RETRIEVAL                                      │
│     - PatientRecordService queries repository            │
│     - LabResultService, ImagingService as needed        │
│     - Data filtered by consent scope                    │
└─────────────────────────────────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────────────────────────┐
│  4. FHIR TRANSFORMATION                                 │
│     - FhirR4TransformationEngine converts to FHIR       │
│     - FhirValidator validates output                    │
└─────────────────────────────────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────────────────────────┐
│  5. AUDIT LOGGING                                       │
│     - PHRAuditTrailServiceImpl logs access              │
│     - Immutable record with patient visibility          │
└─────────────────────────────────────────────────────────┘
       │
       ▼
┌──────────────┐
│   Response   │
│  (FHIR R4)   │
└──────────────┘
```

**Evidence**: `@/home/samujjwal/Developments/ghatana/products/phr/src/main/java/com/ghatana/phr/kernel/service/ConsentManagementService.java:33-100`

---

## 4. Trust Boundaries

**Observed in security implementation**:

| Boundary | Mechanism | Evidence |
|----------|-----------|----------|
| **Patient Data Access** | Consent + Role-based | `ConsentManagementService` with HIPAA compliance |
| **Emergency Override** | Break-glass with audit | `EmergencyAccessLogService` - mandatory post-access review |
| **Caregiver Delegation** | Delegated consent | `CaregiverService` with relationship validation |
| **Provider Access** | Professional credentials | `PHRSecurityManagerImpl` with RBAC |
| **Field-Level Privacy** | Granular consent | Nepal Privacy Act 2075 implementation |

---

## 5. Failure Boundaries

**Observed in service implementations**:

| Failure Scenario | Handling Strategy | Evidence |
|------------------|-------------------|----------|
| **Consent Denied** | AccessDeniedException | `ConsentAccessDeniedException.java` |
| **Emergency Access** | Audit logging + review | `EmergencyAccessLogService` logs all access |
| **Data Not Found** | Empty result with logging | Repository pattern with Optional returns |
| **FHIR Validation Fail** | ValidationException | `FhirValidator.java` |
| **AI Agent Error** | Graceful degradation | Agent pattern with error handling |
| **Rate Limit Exceeded** | RateLimiter blocks | `ConsentManagementService:51-55` |

---

## 6. Deployment Boundaries

**Inferred from architecture**:

| Component | Deployment Unit | Scaling Strategy |
|-----------|-----------------|------------------|
| **PHR Services** | Java application | Horizontal with distributed cache |
| **Consent Cache** | Distributed (Redis/Hazelcast) | Clustered for multi-node consistency |
| **FHIR Transformer** | In-process | Scale with application |
| **Audit Trail** | Persistent storage | Write-optimized database |
| **AI Agents** | In-process | Async execution with circuit breakers |

**Evidence**: `ConsentManagementService` uses `DistributedCachePort` (ISSUE-X02 fix)

---

## 7. External Integrations

**Observed and planned**:

| Integration | Status | Adapter/Interface |
|-------------|--------|-------------------|
| **FHIR R4** | ✅ Complete | `FhirR4TransformationEngine` |
| **Kernel Platform** | ✅ Complete | `PhrKernelModule` |
| **DataCloud** | ✅ Complete | Via kernel adapter |
| **Nepal HIE** | ❌ Planned | Interface design pending |
| **Lab Systems** | Inferred | FHIR Observation resources |
| **Imaging Centers** | Inferred | DICOM integration via FHIR ImagingStudy |
| **Billing/Clearinghouse** | Partial | PHR-side billing only |

---

## 8. Runtime Relationships

### Service Dependency Graph

```
PhrKernelModule
    ├── PhrServiceCatalog
    │   ├── ClinicalServices
    │   │   ├── PatientRecordService
    │   │   ├── ClinicalNoteService
    │   │   ├── LabResultService
    │   │   ├── ImagingService
    │   │   ├── DocumentService
    │   │   └── ClinicalDecisionSupportService
    │   ├── AdministrativeServices
    │   │   ├── AppointmentService
    │   │   ├── BillingService
    │   │   ├── ReferralService
    │   │   └── TelemedicineService
    │   ├── PatientServices
    │   │   ├── ConsentManagementService (depends on distributed cache)
    │   │   ├── MedicationService
    │   │   ├── ImmunizationService
    │   │   └── CaregiverService
    │   └── EmergencyServices
    │       └── EmergencyAccessLogService
    ├── PHRSecurityManagerImpl
    ├── PHRPrivacyManagerImpl
    └── PHRAuditTrailServiceImpl
```

**Evidence**: `@/home/samujjwal/Developments/ghatana/products/phr/src/main/java/com/ghatana/phr/kernel/PhrKernelModule.java:115-161`

---

## 9. Architecture Patterns

**Observed throughout codebase**:

| Pattern | Implementation | Evidence |
|---------|----------------|----------|
| **Composition Root** | `PhrKernelModule` | Central service instantiation |
| **Service Catalog** | `PhrServiceCatalog` | Organized service access |
| **Repository** | `PatientRecordRepository`, etc. | Data access abstraction |
| **Adapter** | `DataCloudKernelAdapter` | External system integration |
| **Strategy** | AI agents | `LabAnomalyDetectionAgent`, etc. |
| **Observer** | Event handlers | `PhrEventProcessor.java` |
| **Rate Limiting** | `DefaultRateLimiter` | Consent API protection |
| **Circuit Breaker** | Inferred | Resilience patterns from platform |

---

## 10. Technology Stack

**Observed in build.gradle.kts**:

| Layer | Technology | Purpose |
|-------|------------|---------|
| **Language** | Java 21 | Core implementation |
| **Async Framework** | ActiveJ | Promise-based async |
| **Kernel Platform** | platform:java:kernel | Module lifecycle |
| **Security** | platform:java:security | AuthN/AuthZ |
| **Database** | platform:java:database | Data access |
| **Distributed Cache** | platform:java:distributed-cache | Multi-node consent cache |
| **Observability** | Micrometer + OpenTelemetry | Metrics and tracing |
| **JSON Processing** | Jackson | FHIR serialization |
| **JWT** | Nimbus JOSE | Token handling |
| **Testing** | JUnit 5 + AssertJ + Mockito | Unit and integration tests |
| **Benchmarking** | JMH | Performance testing |

---

## 11. Data Retention Architecture

**Observed in schema contracts** (`PhrKernelModule.java:164-248`):

| Data Type | Retention Period | Schema Contract |
|-----------|------------------|-----------------|
| Patient Records | 25 years | `phr.patient.records` |
| Consent Grants | As specified | `phr.consent.grants` |
| Medications | 10 years | `phr.medications` |
| Lab Results | 25 years | `phr.lab.results` |
| Immunizations | Permanent | `phr.immunizations` |
| Clinical Notes | 25 years | `phr.clinical.notes` |
| Imaging Studies | Permanent | `phr.imaging.studies` |
| Billing Encounters | 10 years | `phr.billing.encounters` |
| Emergency Access Log | Permanent | `phr.emergency.access.log` |

---

## 12. Architectural Weaknesses

**Identified through code inspection**:

| Weakness | Impact | Recommendation |
|----------|--------|----------------|
| **FHIR Server not implemented** | High - API consumers blocked | Priority implementation |
| **Mobile app not started** | Medium - limits adoption | Begin React Native development |
| **Test coverage below target** | Medium - quality risk | Add tests for all services |
| **Emergency access load testing** | Medium - unknown capacity | Performance testing needed |
| **Nepal HIE integration pending** | Medium - ecosystem integration | Interface design with MoHP |

---

## 13. Evidence Reference

**Primary Sources**:
- `@/home/samujjwal/Developments/ghatana/products/phr/src/main/java/com/ghatana/phr/kernel/PhrKernelModule.java`
- `@/home/samujjwal/Developments/ghatana/products/phr/src/main/java/com/ghatana/phr/kernel/PhrCapabilities.java`
- `@/home/samujjwal/Developments/ghatana/products/phr/src/main/java/com/ghatana/phr/kernel/service/PhrServiceCatalog.java`
- `@/home/samujjwal/Developments/ghatana/products/phr/src/main/java/com/ghatana/phr/kernel/service/ConsentManagementService.java`
- `@/home/samujjwal/Developments/ghatana/products/phr/src/main/java/com/ghatana/phr/fhir/FhirR4TransformationEngine.java`
- `@/home/samujjwal/Developments/ghatana/products/phr/src/main/java/com/ghatana/phr/security/PHRSecurityManagerImpl.java`

**Documentation**:
- `@/home/samujjwal/Developments/ghatana/products/phr/README.md`
- `@/home/samujjwal/Developments/ghatana/products/phr/PHR_KERNEL_INTEGRATION_README.md`

---

*Status: Architecture documented from source code evidence with identified gaps.*
