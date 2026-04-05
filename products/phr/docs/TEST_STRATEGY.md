# PHR Product Test Strategy

**Created**: 2026-04-04  
**Target Coverage**: 100%  
**Current Coverage**: ~70%  
**Gap**: 33 test files

---

## Executive Summary

PHR product requires comprehensive test coverage for FHIR R4 compliance, HIPAA compliance, and Nepal Directive 2081 compliance. This strategy prioritizes FHIR endpoints, emergency access, and AI agent integration.

---

## Test Breakdown

### Priority 1: FHIR Endpoints (15 tests)

#### FHIR Patient Resource (3 tests)
**Coverage Target**: 100%  
**Current**: ~60%

| Test File | Priority | LOC | Purpose |
|-----------|----------|-----|---------|
| FhirPatientEndpointTest.java | Critical | 400 | Patient CRUD + search |
| FhirPatientValidationTest.java | Critical | 300 | FHIR R4 validation |
| FhirPatientSecurityTest.java | Critical | 300 | Consent enforcement |

**Total**: 1,000 LOC

#### FHIR Clinical Resources (6 tests)
| Test File | Priority | LOC | Purpose |
|-----------|----------|-----|---------|
| FhirObservationEndpointTest.java | Critical | 350 | Lab results, vitals |
| FhirMedicationEndpointTest.java | Critical | 350 | Prescriptions |
| FhirAppointmentEndpointTest.java | High | 300 | Appointments |
| FhirConditionEndpointTest.java | High | 300 | Diagnoses |
| FhirProcedureEndpointTest.java | High | 300 | Procedures |
| FhirAllergyIntoleranceEndpointTest.java | High | 300 | Allergies |

**Total**: 1,900 LOC

#### FHIR Infrastructure (6 tests)
| Test File | Priority | LOC | Purpose |
|-----------|----------|-----|---------|
| FhirBundleProcessingTest.java | Critical | 350 | Transaction bundles |
| FhirSearchParametersTest.java | Critical | 300 | Search functionality |
| FhirR4ValidationTest.java | Critical | 300 | FHIR validation |
| FhirSecurityTest.java | Critical | 300 | Authentication/authorization |
| FhirConsentEndpointTest.java | Critical | 300 | Consent management |
| FhirDocumentReferenceEndpointTest.java | High | 300 | Document management |

**Total**: 1,850 LOC

---

### Priority 2: Emergency Access (5 tests)

| Test File | Priority | LOC | Purpose |
|-----------|----------|-----|---------|
| EmergencyAccessBreakGlassTest.java | Critical | 400 | Break-glass workflow |
| EmergencyAccessComplianceTest.java | Critical | 300 | HIPAA compliance |
| EmergencyAccessAuditTest.java | Critical | 300 | Audit trail |
| EmergencyAccessLoadTest.java | High | 250 | Performance under load |
| EmergencyAccessScenariosTest.java | High | 300 | Real-world scenarios |

**Total**: 1,550 LOC

---

### Priority 3: AI Agent Integration (5 tests)

| Test File | Priority | LOC | Purpose |
|-----------|----------|-----|---------|
| LabAnomalyDetectionIntegrationTest.java | Critical | 350 | Anomaly detection |
| MedicationInteractionIntegrationTest.java | Critical | 350 | Drug interactions |
| ReadmissionRiskIntegrationTest.java | High | 300 | Risk scoring |
| AIAgentOrchestrationTest.java | High | 300 | Multi-agent workflows |
| AIGovernanceIntegrationTest.java | Critical | 300 | AI governance |

**Total**: 1,600 LOC

---

### Priority 4: Clinical Decision Support (5 tests)

| Test File | Priority | LOC | Purpose |
|-----------|----------|-----|---------|
| ClinicalDecisionSupportRecommendationTest.java | Critical | 350 | CDS recommendations |
| ClinicalDecisionSupportIntegrationTest.java | High | 300 | EHR integration |
| ClinicalDecisionSupportPerformanceTest.java | Medium | 250 | Performance |
| ClinicalDecisionSupportComplianceTest.java | High | 250 | Compliance |
| ClinicalDecisionSupportCustomizationTest.java | Medium | 250 | Customization |

**Total**: 1,400 LOC

---

### Priority 5: Additional Coverage (3 tests)

| Test File | Priority | LOC | Purpose |
|-----------|----------|-----|---------|
| HealthcareKernelIntegrationTest.java | High | 300 | Kernel integration |
| HIPAAComplianceValidationTest.java | Critical | 300 | HIPAA validation |
| NepalDirectiveComplianceTest.java | Critical | 300 | Nepal compliance |

**Total**: 900 LOC

---

## Implementation Timeline

### Week 1: FHIR Patient & Observation (3 files)
- FhirPatientEndpointTest.java
- FhirPatientValidationTest.java
- FhirObservationEndpointTest.java

### Week 2: FHIR Clinical Resources (4 files)
- FhirMedicationEndpointTest.java
- FhirBundleProcessingTest.java
- FhirSearchParametersTest.java
- FhirSecurityTest.java

### Week 3: Emergency Access (5 files)
- EmergencyAccessBreakGlassTest.java
- EmergencyAccessComplianceTest.java
- EmergencyAccessAuditTest.java
- EmergencyAccessLoadTest.java
- EmergencyAccessScenariosTest.java

### Week 4: AI & CDS (8 files)
- LabAnomalyDetectionIntegrationTest.java
- MedicationInteractionIntegrationTest.java
- AIGovernanceIntegrationTest.java
- ClinicalDecisionSupportRecommendationTest.java
- Remaining FHIR and compliance tests

---

## Test Standards

### FHIR R4 Testing
```java
@DisplayName("FHIR Patient Endpoint Tests")
class FhirPatientEndpointTest extends EventloopTestBase {
    
    @Test
    @DisplayName("Should create FHIR R4 compliant patient")
    void testCreatePatient() {
        // GIVEN
        Patient patient = Patient.builder()
            .identifier(Identifier.builder()
                .system("http://hospital.org/mrn")
                .value("12345")
                .build())
            .name(HumanName.builder()
                .family("Doe")
                .given(List.of("John"))
                .build())
            .build();
        
        // WHEN
        Patient created = runPromise(() -> 
            fhirPatientService.create(patient)
        );
        
        // THEN
        assertThat(created.getId()).isNotNull();
        assertThat(created.getMeta().getVersionId()).isEqualTo("1");
        assertThat(created.getName().get(0).getFamily())
            .isEqualTo("Doe");
    }
}
```

### Emergency Access Testing
```java
@DisplayName("Emergency Access Break-Glass Tests")
class EmergencyAccessBreakGlassTest extends EventloopTestBase {
    
    @Test
    @DisplayName("Should enforce dual authorization for break-glass")
    void testDualAuthorization() {
        // GIVEN
        EmergencyAccessRequest request = EmergencyAccessRequest.builder()
            .patientId("patient-123")
            .reason("Cardiac emergency")
            .requestedBy("dr-smith")
            .build();
        
        // WHEN: First authorization
        runPromise(() -> emergencyAccessService.authorize(
            request, "supervisor-1"
        ));
        
        // THEN: Access not granted yet
        assertThat(emergencyAccessService.isGranted(request))
            .isFalse();
        
        // WHEN: Second authorization
        runPromise(() -> emergencyAccessService.authorize(
            request, "supervisor-2"
        ));
        
        // THEN: Access granted
        assertThat(emergencyAccessService.isGranted(request))
            .isTrue();
    }
}
```

---

## Compliance Requirements

### HIPAA Compliance
- All PHI access must have audit trail tests
- All consent enforcement must have dedicated tests
- All breach notification workflows must be tested

### FHIR R4 Compliance
- All resources must pass FHIR validator
- All search parameters must be tested
- All operations must conform to FHIR spec

### Nepal Directive 2081 Compliance
- Emergency access workflows must be tested
- Data retention policies must be validated
- Cross-border data transfer restrictions tested

---

## Performance Targets

- FHIR Patient create: <100ms p95
- FHIR search: <200ms p95
- Emergency access grant: <200ms p95
- AI anomaly detection: <1s p95
- CDS recommendations: <500ms p95

---

## Success Criteria

- ✅ 100% line coverage for all modules
- ✅ 100% FHIR R4 validation passing
- ✅ All HIPAA compliance tests passing
- ✅ All Nepal Directive tests passing
- ✅ All emergency access workflows tested
- ✅ All AI agent integration tests passing
- ✅ Zero test failures in CI/CD

---

**Next Action**: Begin implementing FHIR Patient endpoint tests
