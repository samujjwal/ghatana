# PHR Production-Critical E2E Test Matrix

## Overview

This document defines the production-critical end-to-end test scenarios for the PHR product. These tests must pass before any production release.

## Test Categories

### 1. Authentication and Authorization

| Test ID | Scenario | Priority | Success Criteria |
|---------|----------|----------|------------------|
| AUTH-001 | Patient login with valid credentials | P0 | Successful login, session token issued, dashboard loads |
| AUTH-002 | Patient login with invalid credentials | P0 | Login fails with appropriate error message |
| AUTH-003 | Session expiry after timeout | P0 | User is redirected to login, session cleared |
| AUTH-004 | Role-based access control - patient | P0 | Patient can only access patient-specific routes |
| AUTH-005 | Role-based access control - clinician | P0 | Clinician can access clinical routes, blocked from admin routes |
| AUTH-006 | Role-based access control - admin | P0 | Admin can access all routes including audit and release readiness |
| AUTH-007 | Missing identity headers on API requests | P0 | Request fails with 400 error, no data returned |
| AUTH-008 | Spoofed identity headers on API requests | P0 | Request fails with 403 error, no data returned |

### 2. PHI Access and Policy Enforcement

| Test ID | Scenario | Priority | Success Criteria |
|---------|----------|----------|------------------|
| PHI-001 | Patient accesses own record | P0 | Access granted, data displayed correctly |
| PHI-002 | Patient attempts to access another patient's record | P0 | Access denied with 403 error |
| PHI-003 | Caregiver accesses dependent's record with consent | P0 | Access granted, data displayed correctly |
| PHI-004 | Caregiver attempts to access dependent's record without consent | P0 | Access denied with 403 error |
| PHI-005 | Clinician accesses patient record with treatment relationship | P0 | Access granted, data displayed correctly |
| PHI-006 | Clinician attempts to access patient without treatment relationship | P0 | Access denied with 403 error |
| PHI-007 | Admin accesses any patient record | P0 | Access granted, audit trail created |
| PHI-008 | Emergency break-glass access with valid justification | P0 | Access granted, patient notified, audit trail created |
| PHI-009 | Emergency break-glass access without justification | P0 | Access denied with 400 error |
| PHI-010 | Emergency break-glass access with trivial justification (< 20 chars) | P0 | Access denied with 400 error |

### 3. Consent Management

| Test ID | Scenario | Priority | Success Criteria |
|---------|----------|----------|------------------|
| CONSENT-001 | Patient grants consent to caregiver | P0 | Consent created, caregiver can access data |
| CONSENT-002 | Patient revokes consent from caregiver | P0 | Consent revoked, caregiver access blocked |
| CONSENT-003 | Consent expiry | P0 | Expired consent automatically revoked, access blocked |
| CONSENT-004 | Consent revocation clears encrypted PHI cache | P0 | PHI cache cleared on mobile device |
| CONSENT-005 | Consent revocation triggers notification | P0 | Notification sent to affected parties |
| CONSENT-006 | Admin revokes consent on behalf of patient | P0 | Consent revoked, audit trail created |

### 4. Mobile Security

| Test ID | Scenario | Priority | Success Criteria |
|---------|----------|----------|------------------|
| MOBILE-001 | PHI encrypted storage - data at rest | P0 | PHI stored encrypted in AsyncStorage, key in SecureStore |
| MOBILE-002 | PHI encrypted storage - decryption | P0 | PHI decrypts correctly with valid key |
| MOBILE-003 | PHI encrypted storage - tamper detection | P0 | Tampered data fails to decrypt, returns null |
| MOBILE-004 | Key rotation on expiry | P0 | Key rotates after 90 days, data re-encrypted |
| MOBILE-005 | Biometric authentication required | P0 | Biometric prompt shown before PHI access |
| MOBILE-006 | Biometric authentication failure | P0 | PHI access denied on biometric failure |
| MOBILE-007 | Session persistence across app restart | P0 | Session persists, PHI remains encrypted |
| MOBILE-008 | Session expiry clears PHI cache | P0 | PHI cache cleared on session expiry |
| MOBILE-009 | Logout clears PHI cache | P0 | PHI cache cleared on logout |
| MOBILE-010 | Role change clears PHI cache | P0 | PHI cache cleared on role/persona change |
| MOBILE-011 | No direct AsyncStorage writes of PHI | P0 | All PHI goes through encrypted adapter |
| MOBILE-012 | Mobile dashboard endpoint protected | P0 | Requires valid session headers, role check enforced |

### 5. Backend API Security

| Test ID | Scenario | Priority | Success Criteria |
|---------|----------|----------|------------------|
| API-001 | Mobile dashboard endpoint - valid session | P0 | Returns dashboard data for authenticated patient |
| API-002 | Mobile dashboard endpoint - missing headers | P0 | Returns 400 error, no data |
| API-003 | Mobile dashboard endpoint - invalid role | P0 | Returns 403 error, no data |
| API-004 | Consent revoke endpoint - correct contract | P0 | Endpoint matches `/consents/grants/:grantId/revoke` |
| API-005 | Consent revoke endpoint - patient owner | P0 | Patient can revoke own consent |
| API-006 | Consent revoke endpoint - non-owner | P0 | Non-owner cannot revoke consent, 403 error |
| API-007 | Route entitlements - fail closed on missing identity | P0 | Returns 400 error for missing headers |
| API-008 | Route entitlements - role-based filtering | P0 | Returns only routes for authenticated role |

### 6. Data Integrity and Validation

| Test ID | Scenario | Priority | Success Criteria |
|---------|----------|----------|------------------|
| DATA-001 | Patient record creation with valid data | P0 | Record created, data persisted correctly |
| DATA-002 | Patient record creation with invalid data | P0 | Creation fails with validation error |
| DATA-003 | Patient record update with valid data | P0 | Record updated, changes persisted |
| DATA-004 | Patient record update with invalid data | P0 | Update fails with validation error |
| DATA-005 | Document upload with valid file | P0 | Document uploaded, metadata stored |
| DATA-006 | Document upload with invalid file type | P0 | Upload fails with validation error |
| DATA-007 | OCR processing of uploaded document | P0 | OCR completes, text extracted |
| DATA-008 | OCR review and acceptance | P0 | OCR result accepted, stored as record |

### 7. Audit and Compliance

| Test ID | Scenario | Priority | Success Criteria |
|---------|----------|----------|------------------|
| AUDIT-001 | PHI access logged | P0 | All PHI access events logged with timestamp |
| AUDIT-002 | Emergency access audit trail | P0 | Emergency access logged with justification |
| AUDIT-003 | Consent change audit trail | P0 | Consent grants/revokes logged |
| AUDIT-004 | Admin action audit trail | P0 | All admin actions logged |
| AUDIT-005 | Audit trail accessible to admin | P0 | Admin can view audit logs |
| AUDIT-006 | Audit trail not accessible to non-admin | P0 | Non-admin cannot view audit logs |
| AUDIT-007 | PHI-safe logging - no PHI in logs | P0 | Logs contain redacted PHI only |

### 8. Release Readiness

| Test ID | Scenario | Priority | Success Criteria |
|---------|----------|----------|------------------|
| RELEASE-001 | Release readiness endpoint accessible to admin | P0 | Admin can view release readiness data |
| RELEASE-002 | Release readiness endpoint not accessible to non-admin | P0 | Non-admin cannot view release readiness |
| RELEASE-003 | Release readiness uses Kernel runtime API | P0 | Data fetched from Kernel, not file parsing |
| RELEASE-004 | All required sections present | P0 | All required sections return data |
| RELEASE-005 | Evidence freshness validation | P0 | Fresh evidence present for all sections |

### 9. Error Handling and Resilience

| Test ID | Scenario | Priority | Success Criteria |
|---------|----------|----------|------------------|
| ERROR-001 | Network timeout handling | P0 | App shows appropriate error, no crash |
| ERROR-002 | Server error (500) handling | P0 | App shows appropriate error, no crash |
| ERROR-003 | Invalid JSON response handling | P0 | App shows appropriate error, no crash |
| ERROR-004 | Missing required field handling | P0 | App shows appropriate error, no crash |
| ERROR-005 | Concurrent request handling | P0 | No race conditions, data consistency maintained |

### 10. Internationalization and Accessibility

| Test ID | Scenario | Priority | Success Criteria |
|---------|----------|----------|------------------|
| I18N-001 | Nepali language display | P1 | All UI elements display in Nepali |
| I18N-002 | English language display | P1 | All UI elements display in English |
| I18N-003 | Language switch functionality | P1 | Language switch works correctly |
| A11Y-001 | Screen reader compatibility | P1 | Screen reader announces all elements |
| A11Y-002 | Keyboard navigation | P1 | All interactive elements keyboard accessible |
| A11Y-003 | Color contrast compliance | P1 | WCAG AA contrast ratio met |

## Test Execution Requirements

### Environment Setup
- Tests must run in a staging environment that mirrors production
- Test data must be isolated from production data
- Test users must have known credentials and roles

### Test Data
- Pre-configured test patients with known IDs
- Pre-configured test clinicians with treatment relationships
- Pre-configured test caregivers with consent grants
- Pre-configured test admin users

### Test Execution Order
1. Authentication and Authorization tests
2. PHI Access and Policy Enforcement tests
3. Consent Management tests
4. Mobile Security tests
5. Backend API Security tests
6. Data Integrity and Validation tests
7. Audit and Compliance tests
8. Release Readiness tests
9. Error Handling and Resilience tests
10. Internationalization and Accessibility tests

### Pass/Fail Criteria
- All P0 tests must pass for production release
- P1 tests should pass but may be deferred with documented exception
- Test failures must have associated bug tickets
- Regression tests must pass on every release

## Test Automation

### Automated Test Suite
- All P0 tests should be automated where possible
- Automated tests should run in CI/CD pipeline
- Test results must be reported to stakeholders

### Manual Test Suite
- Tests requiring visual verification (a11y, i18n)
- Tests requiring physical device interaction (biometrics)
- Tests requiring manual security validation

## Test Reporting

### Test Report Contents
- Test execution summary (total, passed, failed, skipped)
- Failed test details with error messages
- Screenshots for UI failures
- Logs for backend failures
- Performance metrics (response times, page load times)

### Test Report Distribution
- Sent to engineering team
- Sent to product management
- Sent to security team
- Archived for compliance

## Maintenance

### Test Update Frequency
- Tests updated when new features are added
- Tests updated when existing features change
- Tests reviewed quarterly for relevance

### Test Data Maintenance
- Test data refreshed monthly
- Stale test data removed
- Test user credentials rotated regularly
