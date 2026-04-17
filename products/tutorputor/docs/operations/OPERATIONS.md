# Tutorputor – Operations Guide

## 1. Overview

Tutorputor services and apps run as part of the broader Ghatana platform. Operations include deployment, configuration, and monitoring.

## 2. Deployment & Configuration

- Deploy core services in `services/` and connect apps in `apps/` to them.
- Configure environment, persistence, and observability via shared mechanisms.

This guide is self-contained and documents operational considerations for Tutorputor.

## 3. Security Verification

At-rest encryption verification artifacts:

- [AT_REST_ENCRYPTION_AUDIT_2026-04-16.md](AT_REST_ENCRYPTION_AUDIT_2026-04-16.md)
- [AT_REST_ENCRYPTION_CHECKLIST.md](AT_REST_ENCRYPTION_CHECKLIST.md)
- [AT_REST_ENCRYPTION_EVIDENCE_TEMPLATE.md](AT_REST_ENCRYPTION_EVIDENCE_TEMPLATE.md)

Verification scripts:

- `scripts/verify-at-rest-encryption.ps1`
- `scripts/verify-postgres-at-rest-encryption.sql`
- `scripts/verify-object-storage-encryption.sh`

## 4. Critical Journey E2E Proof (TP-008)

Critical-journey execution and signoff artifacts:

- [CRITICAL_JOURNEY_E2E_EVIDENCE_2026-04-16.md](CRITICAL_JOURNEY_E2E_EVIDENCE_2026-04-16.md)
- [CRITICAL_JOURNEY_E2E_CHECKLIST.md](CRITICAL_JOURNEY_E2E_CHECKLIST.md)
- [CRITICAL_JOURNEY_E2E_RUNBOOK.md](CRITICAL_JOURNEY_E2E_RUNBOOK.md)
- [CRITICAL_JOURNEY_E2E_DATA_SETS.md](CRITICAL_JOURNEY_E2E_DATA_SETS.md)
- [CRITICAL_JOURNEY_E2E_SIGNOFF_TEMPLATE.md](CRITICAL_JOURNEY_E2E_SIGNOFF_TEMPLATE.md)
- [CRITICAL_JOURNEY_EVIDENCE_LOCAL_2026-04-16.md](CRITICAL_JOURNEY_EVIDENCE_LOCAL_2026-04-16.md)
- [CRITICAL_JOURNEY_EVIDENCE_STAGING_2026-04-16.md](CRITICAL_JOURNEY_EVIDENCE_STAGING_2026-04-16.md)
- [CRITICAL_JOURNEY_EVIDENCE_PREPROD_2026-04-16.md](CRITICAL_JOURNEY_EVIDENCE_PREPROD_2026-04-16.md)
- [CRITICAL_JOURNEY_EVIDENCE_PRODUCTION_2026-04-16.md](CRITICAL_JOURNEY_EVIDENCE_PRODUCTION_2026-04-16.md)

Execution scripts:

- `scripts/run-critical-journey-e2e.ps1`
- `scripts/run-critical-journey-e2e.sh`
- `scripts/collect-critical-journey-evidence.ps1`
- `scripts/collect-critical-journey-evidence.sh`

## 5. GDPR Deletion Flow Proof (TP-012)

Deletion-flow runbook and evidence template:

- [GDPR_DELETION_FLOW_RUNBOOK.md](GDPR_DELETION_FLOW_RUNBOOK.md)
- [GDPR_DELETION_FLOW_EVIDENCE_TEMPLATE.md](GDPR_DELETION_FLOW_EVIDENCE_TEMPLATE.md)
- [GDPR_DELETION_EVIDENCE_LOCAL_2026-04-16.md](GDPR_DELETION_EVIDENCE_LOCAL_2026-04-16.md)
- [GDPR_DELETION_EVIDENCE_STAGING_2026-04-16.md](GDPR_DELETION_EVIDENCE_STAGING_2026-04-16.md)
- [GDPR_DELETION_EVIDENCE_PREPROD_2026-04-16.md](GDPR_DELETION_EVIDENCE_PREPROD_2026-04-16.md)
- [GDPR_DELETION_EVIDENCE_PRODUCTION_2026-04-16.md](GDPR_DELETION_EVIDENCE_PRODUCTION_2026-04-16.md)

Verification scripts:

- `scripts/verify-gdpr-delete-flow.ps1`
- `scripts/verify-gdpr-delete-flow.sh`
- `scripts/verify-gdpr-delete-cascade.sql`
- `scripts/collect-gdpr-deletion-evidence.ps1`
- `scripts/collect-gdpr-deletion-evidence.sh`
- `scripts/collect-lti-validation-evidence.ps1`
- `scripts/collect-lti-validation-evidence.sh`

## 6. Combined Proof Execution

Run all major remediation proofs in one pass:

- `scripts/run-remediation-proof-suite.ps1`
- `scripts/run-remediation-proof-suite.sh`

At-rest evidence files by environment:

- [ENCRYPTION_EVIDENCE_LOCAL_2026-04-16.md](ENCRYPTION_EVIDENCE_LOCAL_2026-04-16.md)
- [ENCRYPTION_EVIDENCE_STAGING_2026-04-16.md](ENCRYPTION_EVIDENCE_STAGING_2026-04-16.md)
- [ENCRYPTION_EVIDENCE_PREPROD_2026-04-16.md](ENCRYPTION_EVIDENCE_PREPROD_2026-04-16.md)
- [ENCRYPTION_EVIDENCE_PRODUCTION_2026-04-16.md](ENCRYPTION_EVIDENCE_PRODUCTION_2026-04-16.md)

Execution wrappers:

- `scripts/run-remediation-proof-suite-staging.ps1`
- `scripts/run-remediation-proof-suite-preprod.ps1`
- `scripts/run-remediation-proof-suite-production.ps1`
- `scripts/run-remediation-proof-suite-staging.sh`
- `scripts/run-remediation-proof-suite-preprod.sh`
- `scripts/run-remediation-proof-suite-production.sh`

## 7. Reporting and LTI

Proof tracking artifacts:

- [REMEDIATION_PROOF_EXECUTION_LOG_2026-04-16.md](REMEDIATION_PROOF_EXECUTION_LOG_2026-04-16.md)
- [REMEDIATION_PROOF_STATUS_MATRIX_2026-04-16.md](REMEDIATION_PROOF_STATUS_MATRIX_2026-04-16.md)
- [REMEDIATION_PROOF_SIGNOFF_2026-04-16.md](REMEDIATION_PROOF_SIGNOFF_2026-04-16.md)
- [ROUTE_VALIDATION_ROLLOUT_MATRIX_2026-04-16.md](ROUTE_VALIDATION_ROLLOUT_MATRIX_2026-04-16.md)
- [LTI_VALIDATION_EVIDENCE_2026-04-16.md](LTI_VALIDATION_EVIDENCE_2026-04-16.md)
- [LTI_PHASE2_ROUTE_VALIDATION_EVIDENCE_2026-04-16.md](LTI_PHASE2_ROUTE_VALIDATION_EVIDENCE_2026-04-16.md)
- [LTI_PHASE2_PROOF_RUNBOOK.md](LTI_PHASE2_PROOF_RUNBOOK.md)
- [LTI_PHASE2_PROOF_CHECKLIST.md](LTI_PHASE2_PROOF_CHECKLIST.md)
- [LTI_PHASE2_PROOF_STATUS_MATRIX_2026-04-16.md](LTI_PHASE2_PROOF_STATUS_MATRIX_2026-04-16.md)
- [LTI_PHASE2_PROOF_EXECUTION_LOG_2026-04-16.md](LTI_PHASE2_PROOF_EXECUTION_LOG_2026-04-16.md)
- [LTI_PHASE2_PROOF_SIGNOFF_2026-04-16.md](LTI_PHASE2_PROOF_SIGNOFF_2026-04-16.md)
- [LTI_PHASE2_EVIDENCE_LOCAL_2026-04-16.md](LTI_PHASE2_EVIDENCE_LOCAL_2026-04-16.md)
- [LTI_PHASE2_EVIDENCE_STAGING_2026-04-16.md](LTI_PHASE2_EVIDENCE_STAGING_2026-04-16.md)
- [LTI_PHASE2_EVIDENCE_PREPROD_2026-04-16.md](LTI_PHASE2_EVIDENCE_PREPROD_2026-04-16.md)
- [LTI_PHASE2_EVIDENCE_PRODUCTION_2026-04-16.md](LTI_PHASE2_EVIDENCE_PRODUCTION_2026-04-16.md)

LTI verification scripts:

- `scripts/verify-lti-config.ps1`
- `scripts/verify-lti-config.sh`
- `scripts/verify-lti-grade-passback.ps1`
- `scripts/verify-lti-grade-passback.sh`
- `scripts/verify-lti-phase2-routes.ps1`
- `scripts/verify-lti-phase2-routes.sh`
- `scripts/collect-lti-phase2-validation-evidence.ps1`
- `scripts/collect-lti-phase2-validation-evidence.sh`
- `scripts/run-lti-phase2-proof-suite.ps1`
- `scripts/run-lti-phase2-proof-suite.sh`
- `scripts/run-lti-phase2-proof-suite-staging.ps1`
- `scripts/run-lti-phase2-proof-suite-preprod.ps1`
- `scripts/run-lti-phase2-proof-suite-production.ps1`
- `scripts/run-lti-phase2-proof-suite-staging.sh`
- `scripts/run-lti-phase2-proof-suite-preprod.sh`
- `scripts/run-lti-phase2-proof-suite-production.sh`

## 8. Social Route Proof (TP-009)

Social proof artifacts:

- [SOCIAL_VALIDATION_EVIDENCE_2026-04-16.md](SOCIAL_VALIDATION_EVIDENCE_2026-04-16.md)
- [SOCIAL_PROOF_RUNBOOK.md](SOCIAL_PROOF_RUNBOOK.md)
- [SOCIAL_PROOF_CHECKLIST.md](SOCIAL_PROOF_CHECKLIST.md)
- [SOCIAL_PROOF_STATUS_MATRIX_2026-04-16.md](SOCIAL_PROOF_STATUS_MATRIX_2026-04-16.md)
- [SOCIAL_PROOF_EXECUTION_LOG_2026-04-16.md](SOCIAL_PROOF_EXECUTION_LOG_2026-04-16.md)
- [SOCIAL_PROOF_SIGNOFF_2026-04-16.md](SOCIAL_PROOF_SIGNOFF_2026-04-16.md)
- [SOCIAL_EVIDENCE_LOCAL_2026-04-16.md](SOCIAL_EVIDENCE_LOCAL_2026-04-16.md)
- [SOCIAL_EVIDENCE_STAGING_2026-04-16.md](SOCIAL_EVIDENCE_STAGING_2026-04-16.md)
- [SOCIAL_EVIDENCE_PREPROD_2026-04-16.md](SOCIAL_EVIDENCE_PREPROD_2026-04-16.md)
- [SOCIAL_EVIDENCE_PRODUCTION_2026-04-16.md](SOCIAL_EVIDENCE_PRODUCTION_2026-04-16.md)

Social proof scripts:

- `scripts/collect-social-validation-evidence.ps1`
- `scripts/collect-social-validation-evidence.sh`
- `scripts/verify-social-routes.ps1`
- `scripts/verify-social-routes.sh`
- `scripts/run-social-proof-suite.ps1`
- `scripts/run-social-proof-suite.sh`
- `scripts/run-social-proof-suite-staging.ps1`
- `scripts/run-social-proof-suite-preprod.ps1`
- `scripts/run-social-proof-suite-production.ps1`
- `scripts/run-social-proof-suite-staging.sh`
- `scripts/run-social-proof-suite-preprod.sh`
- `scripts/run-social-proof-suite-production.sh`

## 9. Content Route Proof (TP-009)

Content proof artifacts:

- [CONTENT_ROUTE_VALIDATION_EVIDENCE_2026-04-16.md](CONTENT_ROUTE_VALIDATION_EVIDENCE_2026-04-16.md)
- [CONTENT_PHASE2_ROUTE_VALIDATION_EVIDENCE_2026-04-16.md](CONTENT_PHASE2_ROUTE_VALIDATION_EVIDENCE_2026-04-16.md)
- [CONTENT_PROOF_RUNBOOK.md](CONTENT_PROOF_RUNBOOK.md)
- [CONTENT_PHASE2_PROOF_RUNBOOK.md](CONTENT_PHASE2_PROOF_RUNBOOK.md)
- [CONTENT_PROOF_CHECKLIST.md](CONTENT_PROOF_CHECKLIST.md)
- [CONTENT_PHASE2_PROOF_CHECKLIST.md](CONTENT_PHASE2_PROOF_CHECKLIST.md)
- [CONTENT_PROOF_STATUS_MATRIX_2026-04-16.md](CONTENT_PROOF_STATUS_MATRIX_2026-04-16.md)
- [CONTENT_PHASE2_PROOF_STATUS_MATRIX_2026-04-16.md](CONTENT_PHASE2_PROOF_STATUS_MATRIX_2026-04-16.md)
- [CONTENT_PROOF_EXECUTION_LOG_2026-04-16.md](CONTENT_PROOF_EXECUTION_LOG_2026-04-16.md)
- [CONTENT_PHASE2_PROOF_EXECUTION_LOG_2026-04-16.md](CONTENT_PHASE2_PROOF_EXECUTION_LOG_2026-04-16.md)
- [CONTENT_PROOF_SIGNOFF_2026-04-16.md](CONTENT_PROOF_SIGNOFF_2026-04-16.md)
- [CONTENT_PHASE2_PROOF_SIGNOFF_2026-04-16.md](CONTENT_PHASE2_PROOF_SIGNOFF_2026-04-16.md)
- [CONTENT_EVIDENCE_LOCAL_2026-04-16.md](CONTENT_EVIDENCE_LOCAL_2026-04-16.md)
- [CONTENT_EVIDENCE_STAGING_2026-04-16.md](CONTENT_EVIDENCE_STAGING_2026-04-16.md)
- [CONTENT_EVIDENCE_PREPROD_2026-04-16.md](CONTENT_EVIDENCE_PREPROD_2026-04-16.md)
- [CONTENT_EVIDENCE_PRODUCTION_2026-04-16.md](CONTENT_EVIDENCE_PRODUCTION_2026-04-16.md)
- [CONTENT_PHASE2_EVIDENCE_LOCAL_2026-04-16.md](CONTENT_PHASE2_EVIDENCE_LOCAL_2026-04-16.md)
- [CONTENT_PHASE2_EVIDENCE_STAGING_2026-04-16.md](CONTENT_PHASE2_EVIDENCE_STAGING_2026-04-16.md)
- [CONTENT_PHASE2_EVIDENCE_PREPROD_2026-04-16.md](CONTENT_PHASE2_EVIDENCE_PREPROD_2026-04-16.md)
- [CONTENT_PHASE2_EVIDENCE_PRODUCTION_2026-04-16.md](CONTENT_PHASE2_EVIDENCE_PRODUCTION_2026-04-16.md)

Route validation batch 3 artifacts:

- [ROUTE_VALIDATION_BATCH3_EVIDENCE_2026-04-16.md](ROUTE_VALIDATION_BATCH3_EVIDENCE_2026-04-16.md)
- [ROUTE_VALIDATION_BATCH3_RUNBOOK.md](ROUTE_VALIDATION_BATCH3_RUNBOOK.md)
- [ROUTE_VALIDATION_BATCH3_CHECKLIST.md](ROUTE_VALIDATION_BATCH3_CHECKLIST.md)
- [ROUTE_VALIDATION_BATCH3_STATUS_MATRIX_2026-04-16.md](ROUTE_VALIDATION_BATCH3_STATUS_MATRIX_2026-04-16.md)
- [ROUTE_VALIDATION_BATCH3_EXECUTION_LOG_2026-04-16.md](ROUTE_VALIDATION_BATCH3_EXECUTION_LOG_2026-04-16.md)
- [ROUTE_VALIDATION_BATCH3_SIGNOFF_2026-04-16.md](ROUTE_VALIDATION_BATCH3_SIGNOFF_2026-04-16.md)
- [ROUTE_VALIDATION_BATCH3_EVIDENCE_LOCAL_2026-04-16.md](ROUTE_VALIDATION_BATCH3_EVIDENCE_LOCAL_2026-04-16.md)
- [ROUTE_VALIDATION_BATCH3_EVIDENCE_STAGING_2026-04-16.md](ROUTE_VALIDATION_BATCH3_EVIDENCE_STAGING_2026-04-16.md)
- [ROUTE_VALIDATION_BATCH3_EVIDENCE_PREPROD_2026-04-16.md](ROUTE_VALIDATION_BATCH3_EVIDENCE_PREPROD_2026-04-16.md)
- [ROUTE_VALIDATION_BATCH3_EVIDENCE_PRODUCTION_2026-04-16.md](ROUTE_VALIDATION_BATCH3_EVIDENCE_PRODUCTION_2026-04-16.md)

Content proof scripts:

- `scripts/collect-content-validation-evidence.ps1`
- `scripts/collect-content-validation-evidence.sh`
- `scripts/verify-content-routes.ps1`
- `scripts/verify-content-routes.sh`
- `scripts/run-content-proof-suite.ps1`
- `scripts/run-content-proof-suite.sh`
- `scripts/run-content-proof-suite-staging.ps1`
- `scripts/run-content-proof-suite-preprod.ps1`
- `scripts/run-content-proof-suite-production.ps1`
- `scripts/run-content-proof-suite-staging.sh`
- `scripts/run-content-proof-suite-preprod.sh`
- `scripts/run-content-proof-suite-production.sh`
- `scripts/collect-content-phase2-validation-evidence.ps1`
- `scripts/collect-content-phase2-validation-evidence.sh`
- `scripts/verify-content-phase2-routes.ps1`
- `scripts/verify-content-phase2-routes.sh`
- `scripts/run-content-phase2-proof-suite.ps1`
- `scripts/run-content-phase2-proof-suite.sh`
- `scripts/run-content-phase2-proof-suite-staging.ps1`
- `scripts/run-content-phase2-proof-suite-preprod.ps1`
- `scripts/run-content-phase2-proof-suite-production.ps1`
- `scripts/run-content-phase2-proof-suite-staging.sh`
- `scripts/run-content-phase2-proof-suite-preprod.sh`
- `scripts/run-content-phase2-proof-suite-production.sh`
- `scripts/verify-route-validation-batch3.ps1`
- `scripts/verify-route-validation-batch3.sh`
- `scripts/collect-route-validation-batch3-evidence.ps1`
- `scripts/collect-route-validation-batch3-evidence.sh`
- `scripts/run-route-validation-batch3-proof-suite.ps1`
- `scripts/run-route-validation-batch3-proof-suite.sh`
- `scripts/run-route-validation-batch3-proof-suite-staging.ps1`
- `scripts/run-route-validation-batch3-proof-suite-preprod.ps1`
- `scripts/run-route-validation-batch3-proof-suite-production.ps1`
- `scripts/run-route-validation-batch3-proof-suite-staging.sh`
- `scripts/run-route-validation-batch3-proof-suite-preprod.sh`
- `scripts/run-route-validation-batch3-proof-suite-production.sh`
