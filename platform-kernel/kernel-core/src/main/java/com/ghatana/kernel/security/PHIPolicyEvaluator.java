/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.security;

import io.activej.promise.Promise;

import java.util.Map;
import java.util.Set;

/**
 * Kernel-level PHI (Protected Health Information) policy evaluator.
 *
 * <p>Provides generic PHI access policy evaluation for healthcare products.
 * This service evaluates PHI access requests against consent, treatment
 * relationships, emergency access rules, and data classification policies.
 * Healthcare products can use this as the foundation for their product-specific
 * policy evaluation.</p>
 *
 * <p>PHI access decisions are based on:</p>
 * <ul>
 *   <li>Consent status (granted, denied, expired)</li>
 *   <li>Treatment relationship between accessor and patient</li>
 *   <li>Emergency access justification and approval</li>
 *   <li>Data classification and access scope</li>
 *   <li>Role-based access rules</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose Kernel-level PHI access policy evaluation for healthcare products
 * @doc.layer core
 * @doc.pattern Service
 * @author Ghatana Kernel Team
 * @since 1.1.0
 */
public interface PHIPolicyEvaluator {

    /**
     * Evaluates whether an accessor is permitted to access PHI for a patient.
     *
     * <p>This method checks consent, treatment relationships, emergency access,
     * and role-based access rules to determine if PHI access should be granted.</p>
     *
     * @param request the PHI access request
     * @return Promise containing the access decision
     */
    Promise<PHIAccessDecision> evaluateAccess(PHIAccessRequest request);

    /**
     * Checks if a consent grant is valid for a specific PHI access request.
     *
     * @param consentId the consent grant identifier
     * @param patientId the patient identifier
     * @param accessorId the accessor identifier
     * @param resourceTypes the resource types being accessed
     * @return Promise containing the consent validation result
     */
    Promise<ConsentValidationResult> validateConsent(
            String consentId,
            String patientId,
            String accessorId,
            Set<String> resourceTypes);

    /**
     * Evaluates emergency break-glass access request.
     *
     * <p>Emergency access requires justification, operational necessity, and
     * triggers mandatory review and patient notification.</p>
     *
     * @param request the emergency access request
     * @return Promise containing the emergency access decision
     */
    Promise<EmergencyAccessDecision> evaluateEmergencyAccess(EmergencyAccessRequest request);

    /**
     * Checks if an accessor has an active treatment relationship with a patient.
     *
     * @param accessorId the accessor identifier
     * @param patientId the patient identifier
     * @return Promise containing true if treatment relationship exists
     */
    Promise<Boolean> hasActiveTreatmentRelationship(String accessorId, String patientId);

    /**
     * Redacts PHI from data based on classification and access level.
     *
     * @param data the data containing potential PHI
     * @param classification the data classification
     * @param accessLevel the access level of the requester
     * @return redacted data with PHI masked appropriately
     */
    String redactPHI(String data, PrivacyManager.DataClassification classification, AccessLevel accessLevel);

    /**
     * PHI access request.
     */
    record PHIAccessRequest(
            String requestId,
            String patientId,
            String accessorId,
            String accessorRole,
            Set<String> resourceTypes,
            String purpose,
            Map<String, String> metadata) {
        public PHIAccessRequest {
            if (patientId == null || patientId.isBlank()) {
                throw new IllegalArgumentException("patientId must not be blank");
            }
            if (accessorId == null || accessorId.isBlank()) {
                throw new IllegalArgumentException("accessorId must not be blank");
            }
            if (resourceTypes == null) {
                resourceTypes = Set.of();
            }
            if (metadata == null) {
                metadata = Map.of();
            }
        }
    }

    /**
     * PHI access decision.
     */
    record PHIAccessDecision(
            boolean granted,
            String reason,
            Set<String> allowedResources,
            String consentId,
            boolean emergencyAccess,
            Map<String, String> metadata) {
        public PHIAccessDecision {
            if (allowedResources == null) {
                allowedResources = Set.of();
            }
            if (metadata == null) {
                metadata = Map.of();
            }
        }
    }

    /**
     * Consent validation result.
     */
    record ConsentValidationResult(
            boolean valid,
            String status,
            String consentId,
            Set<String> allowedResourceTypes,
            java.time.Instant expiresAt) {
        public ConsentValidationResult {
            if (allowedResourceTypes == null) {
                allowedResourceTypes = Set.of();
            }
        }
    }

    /**
     * Emergency access request.
     */
    record EmergencyAccessRequest(
            String requestId,
            String patientId,
            String accessorId,
            String accessorRole,
            String justification,
            Set<String> resourceTypes,
            java.time.Instant requestedAt) {
        public EmergencyAccessRequest {
            if (patientId == null || patientId.isBlank()) {
                throw new IllegalArgumentException("patientId must not be blank");
            }
            if (accessorId == null || accessorId.isBlank()) {
                throw new IllegalArgumentException("accessorId must not be blank");
            }
            if (justification == null || justification.isBlank()) {
                throw new IllegalArgumentException("justification must not be blank");
            }
            if (justification.length() < 20) {
                throw new IllegalArgumentException("justification must be at least 20 characters");
            }
            if (resourceTypes == null || resourceTypes.isEmpty()) {
                throw new IllegalArgumentException("resourceTypes must not be empty");
            }
            if (requestedAt == null) {
                requestedAt = java.time.Instant.now();
            }
        }
    }

    /**
     * Emergency access decision.
     */
    record EmergencyAccessDecision(
            boolean granted,
            String reason,
            String emergencyAccessId,
            java.time.Instant accessExpiresAt,
            String reviewCaseId,
            java.time.Instant reviewDueAt,
            boolean patientNotificationRequired,
            boolean complianceNotificationRequired) {
        public EmergencyAccessDecision {
            if (granted && emergencyAccessId == null) {
                throw new IllegalArgumentException("emergencyAccessId required when granted");
            }
            if (granted && accessExpiresAt == null) {
                throw new IllegalArgumentException("accessExpiresAt required when granted");
            }
        }
    }

    /**
     * Access level for PHI redaction.
     */
    enum AccessLevel {
        /** Full access - no redaction */
        FULL,
        /** Partial access - sensitive fields redacted */
        PARTIAL,
        /** Minimal access - most fields redacted */
        MINIMAL,
        /** Audit-only access - all PHI redacted except metadata */
        AUDIT_ONLY
    }
}
