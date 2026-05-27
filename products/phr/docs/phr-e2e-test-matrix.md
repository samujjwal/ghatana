# PHR Production-Critical E2E Test Matrix

This document defines the production-critical end-to-end test scenarios for the PHR product. These tests validate critical user journeys, security boundaries, and compliance requirements.

## Test Categories

### 1. Authentication & Authorization

| Test ID | Scenario | Priority | Persona | Expected Outcome |
|---------|----------|----------|---------|------------------|
| E2E-AUTH-001 | Patient login with valid credentials | P0 | patient | Successful authentication, session token issued |
| E2E-AUTH-002 | Patient login with invalid credentials | P0 | patient | 401 Unauthorized, no session token |
| E2E-AUTH-003 | Session expiry handling | P0 | patient | Expired session rejected, redirect to login |
| E2E-AUTH-004 | Role-based route access (patient routes) | P0 | patient | Patient can access patient routes, denied admin routes |
| E2E-AUTH-005 | Role-based route access (clinician routes) | P0 | clinician | Clinician can access clinical routes, denied admin routes |
| E2E-AUTH-006 | Role-based route access (admin routes) | P0 | admin | Admin can access admin routes |
| E2E-AUTH-007 | Missing identity headers fail closed | P0 | any | 400 Bad Request for missing X-Principal-Id, X-Tenant-Id, X-Role |
| E2E-AUTH-008 | Invalid role rejected | P0 | any | 400 Bad Request for unrecognized role |

### 2. Patient Record Management

| Test ID | Scenario | Priority | Persona | Expected Outcome |
|---------|----------|----------|---------|------------------|
| E2E-REC-001 | Patient views own dashboard | P0 | patient | Dashboard loads with patient's PHI |
| E2E-REC-002 | Patient views own records | P0 | patient | Records list loads with patient's data |
| E2E-REC-003 | Patient cannot view another patient's records | P0 | patient | 403 Forbidden |
| E2E-REC-004 | Clinician views patient records with consent | P0 | clinician | Records load when consent exists |
| E2E-REC-005 | Clinician denied access without consent | P0 | clinician | 403 Forbidden when no consent |
| E2E-REC-006 | Admin views any patient record | P0 | admin | Records load for any patient ID |
| E2E-REC-007 | Patient profile update | P1 | patient | Profile updates successfully |
| E2E-REC-008 | Timeline view loads correctly | P1 | patient | Timeline displays health events |

### 3. Consent Management

| Test ID | Scenario | Priority | Persona | Expected Outcome |
|---------|----------|----------|---------|------------------|
| E2E-CONSENT-001 | Patient grants consent to caregiver | P0 | patient | Consent grant created, status ACTIVE |
| E2E-CONSENT-002 | Patient revokes consent | P0 | patient | Consent status changes to REVOKED |
| E2E-CONSENT-003 | Consent revocation clears PHI cache | P0 | patient | Mobile encrypted cache cleared after revocation |
| E2E-CONSENT-004 | Caregiver access denied after revocation | P0 | caregiver | 403 Forbidden after consent revoked |
| E2E-CONSENT-005 | Consent check API returns correct status | P0 | any | Accurate allowed/denied response |
| E2E-CONSENT-006 | Consent expires automatically | P1 | patient | Status changes to EXPIRED after expiry time |
| E2E-CONSENT-007 | Invalid consent payload rejected | P0 | patient | 400 Bad Request for malformed consent |

### 4. Emergency Break-Glass

| Test ID | Scenario | Priority | Persona | Expected Outcome |
|---------|----------|----------|---------|------------------|
| E2E-EMERGENCY-001 | Clinician requests emergency access | P0 | clinician | Emergency access logged, status PENDING_REVIEW |
| E2E-EMERGENCY-002 | Emergency access requires justification | P0 | clinician | 400 Bad Request without justification |
| E2E-EMERGENCY-003 | Emergency access requires resources | P0 | clinician | 400 Bad Request without resourcesAccessed |
| E2E-EMERGENCY-004 | Non-clinician denied emergency access | P0 | patient | 403 Forbidden for non-clinician role |
| E2E-EMERGENCY-005 | Admin reviews emergency access (approve) | P0 | admin | Status changes to APPROVED |
| E2E-EMERGENCY-006 | Admin reviews emergency access (deny) | P0 | admin | Status changes to DENIED |
| E2E-EMERGENCY-007 | Denied review requires notes | P0 | admin | 400 Bad Request without notes for denial |
| E2E-EMERGENCY-008 | Patient views emergency access log | P0 | patient | Patient can see own emergency events |
| E2E-EMERGENCY-009 | Admin lists pending reviews | P0 | admin | Pending reviews list loads |
| E2E-EMERGENCY-010 | Admin lists overdue reviews | P0 | admin | Overdue reviews list loads |

### 5. Mobile App Security

| Test ID | Scenario | Priority | Persona | Expected Outcome |
|---------|----------|----------|---------|------------------|
| E2E-MOBILE-001 | Mobile dashboard endpoint protected | P0 | patient | Requires valid session headers |
| E2E-MOBILE-002 | Mobile PHI encrypted at rest | P0 | patient | AsyncStorage contains only ciphertext |
| E2E-MOBILE-003 | Mobile key stored in SecureStore | P0 | patient | Encryption key in OS keychain |
| E2E-MOBILE-004 | Logout clears PHI cache | P0 | patient | Encrypted cache cleared on logout |
| E2E-MOBILE-005 | Session expiry clears PHI cache | P0 | patient | Encrypted cache cleared on session expiry |
| E2E-MOBILE-006 | Consent revocation clears PHI cache | P0 | patient | Encrypted cache cleared on consent revocation |
| E2E-MOBILE-007 | Role change clears PHI cache | P0 | patient | Encrypted cache cleared on role change |
| E2E-MOBILE-008 | Mobile consent revocation endpoint correct | P0 | patient | Uses /consents/grants/:id/revoke?patientId= |
| E2E-MOBILE-009 | Offline cache TTL enforced | P1 | patient | Stale cache not served after TTL |

### 6. PHI-Safe Logging

| Test ID | Scenario | Priority | Persona | Expected Outcome |
|---------|----------|----------|---------|------------------|
| E2E-LOG-001 | Patient ID redacted in logs | P0 | any | Logs show ***REDACTED*** for patientId |
| E2E-LOG-002 | Email addresses redacted in logs | P0 | any | Logs show ***EMAIL*** for email patterns |
| E2E-LOG-003 | Phone numbers redacted in logs | P0 | any | Logs show ***PHONE*** for phone patterns |
| E2E-LOG-004 | SSN patterns redacted in logs | P0 | any | Logs show ***SSN*** for SSN patterns |
| E2E-LOG-005 | Diagnosis redacted in logs | P0 | any | Logs show ***REDACTED*** for diagnosis field |

### 7. Route Entitlements

| Test ID | Scenario | Priority | Persona | Expected Outcome |
|---------|----------|----------|---------|------------------|
| E2E-ENTITLE-001 | Backend route entitlements match web | P0 | any | All web routes have backend entitlement |
| E2E-ENTITLE-002 | Patient receives correct route list | P0 | patient | Only patient routes returned |
| E2E-ENTITLE-003 | Clinician receives correct route list | P0 | clinician | Clinical routes included |
| E2E-ENTITLE-004 | Admin receives all routes | P0 | admin | All routes including admin routes |
| E2E-ENTITLE-005 | Feature-flagged routes conditionally shown | P1 | any | Feature-flagged routes based on flag state |

### 8. FHIR Interoperability

| Test ID | Scenario | Priority | Persona | Expected Outcome |
|---------|----------|----------|---------|------------------|
| E2E-FHIR-001 | Create FHIR Patient resource | P1 | clinician | Patient resource created successfully |
| E2E-FHIR-002 | Read FHIR Patient resource | P1 | clinician | Patient resource returned |
| E2E-FHIR-003 | Search FHIR resources | P1 | clinician | Search results returned |
| E2E-FHIR-004 | FHIR validation enforced | P1 | clinician | Invalid FHIR rejected |

### 9. Audit Trail

| Test ID | Scenario | Priority | Persona | Expected Outcome |
|---------|----------|----------|---------|------------------|
| E2E-AUDIT-001 | Emergency access logged | P0 | clinician | Audit event created for emergency access |
| E2E-AUDIT-002 | Consent revocation logged | P0 | patient | Audit event created for revocation |
| E2E-AUDIT-003 | Admin can view audit trail | P0 | admin | Audit events list loads |
| E2E-AUDIT-004 | Non-admin denied audit access | P0 | patient | 403 Forbidden for audit endpoint |

### 10. Release Readiness

| Test ID | Scenario | Priority | Persona | Expected Outcome |
|---------|----------|----------|---------|------------------|
| E2E-RELEASE-001 | Release readiness endpoint accessible | P0 | admin | Admin can access release readiness |
| E2E-AUDIT-002 | Non-admin denied release readiness | P0 | patient | 403 Forbidden for non-admin |
| E2E-RELEASE-003 | Evidence freshness validated | P0 | admin | Evidence freshness section validated |
| E2E-RELEASE-004 | FHIR runtime validated | P0 | admin | FHIR runtime section validated |
| E2E-RELEASE-005 | Consent cache proof validated | P0 | admin | Consent cache section validated |

## Test Execution Requirements

### Environment Setup
- Test environment must mirror production configuration
- Test data must be isolated per test run
- Test credentials must be managed securely

### Test Data Requirements
- Minimum test patients: 5
- Minimum test clinicians: 2
- Minimum test admins: 1
- Minimum test caregivers: 2

### Success Criteria
- All P0 tests must pass before production deployment
- P1 tests should pass but may be deferred with documented exception
- Test execution time must be under 30 minutes for full matrix
- Test results must be archived for compliance audit

### Automation Status
- **Automated**: E2E-AUTH-001 through E2E-AUTH-008
- **Automated**: E2E-REC-001 through E2E-REC-006
- **Automated**: E2E-CONSENT-001 through E2E-CONSENT-007
- **Automated**: E2E-EMERGENCY-001 through E2E-EMERGENCY-010
- **Automated**: E2E-MOBILE-001 through E2E-MOBILE-009
- **Manual**: E2E-LOG-001 through E2E-LOG-005 (requires log inspection)
- **Automated**: E2E-ENTITLE-001 through E2E-ENTITLE-005
- **Automated**: E2E-FHIR-001 through E2E-FHIR-004
- **Automated**: E2E-AUDIT-001 through E2E-AUDIT-004
- **Automated**: E2E-RELEASE-001 through E2E-RELEASE-005

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2026-05-26 | Initial production-critical E2E test matrix |
