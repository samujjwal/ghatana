/*
 * Copyright (c) 2025 Ghatana.
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ghatana.datacloud.plugins.enterprise.compliance;

import io.activej.promise.Promise;
import lombok.Builder;
import lombok.Getter;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

/**
 * Compliance reporting system for GDPR, HIPAA, and SOC2.
 *
 * <p>
 * Provides:
 * <ul>
 * <li>GDPR Data Subject Access Requests (DSAR)</li>
 * <li>GDPR Right to Erasure (RTBF)</li>
 * <li>HIPAA Audit Log Export</li>
 * <li>SOC2 Compliance Dashboard</li>
 * <li>Data Retention Policy Enforcement</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Compliance reporting
 * @doc.layer product
 * @doc.pattern Service
 */
public class ComplianceReporter {

    /**
     * DSAR request storage.
     */
    private final Map<String, DSARRequest> dsarRequests;

    /**
     * Erasure request storage.
     */
    private final Map<String, ErasureRequest> erasureRequests;

    /**
     * Audit log storage.
     */
    private final List<AuditLogEntry> auditLog;

    /**
     * Retention policies.
     */
    private final Map<String, RetentionPolicy> retentionPolicies;

    /**
     * SOC2 control assessments.
     */
    private final Map<String, SOC2Control> soc2Controls;

    /**
     * Creates a new compliance reporter.
     */
    public ComplianceReporter() {
        this.dsarRequests = new ConcurrentHashMap<>();
        this.erasureRequests = new ConcurrentHashMap<>();
        this.auditLog = new ArrayList<>();
        this.retentionPolicies = new ConcurrentHashMap<>();
        this.soc2Controls = new ConcurrentHashMap<>();
        initializeSOC2Controls();
    }

    // --- GDPR DSAR Methods ---
    /**
     * Creates a GDPR Data Subject Access Request.
     *
     * @param subjectEmail Email of the data subject
     * @param subjectId Optional subject identifier
     * @param requestedBy Who made the request
     * @return Promise of DSAR request
     */
    public Promise<DSARRequest> createDSAR(
            String subjectEmail,
            String subjectId,
            String requestedBy) {

        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            String requestId = UUID.randomUUID().toString();

            DSARRequest request = DSARRequest.builder()
                    .requestId(requestId)
                    .subjectEmail(subjectEmail)
                    .subjectId(subjectId)
                    .requestedBy(requestedBy)
                    .status(RequestStatus.PENDING)
                    .build();

            dsarRequests.put(requestId, request);

            // Log audit entry
            logAudit(AuditAction.DSAR_CREATED, requestedBy,
                    Map.of("requestId", requestId, "subjectEmail", subjectEmail));

            return request;
        });
    }

    /**
     * Processes a DSAR request and collects data.
     *
     * @param requestId Request identifier
     * @param dataCollector Function to collect data for the subject
     * @return Promise of DSAR response
     */
    public Promise<DSARResponse> processDSAR(
            String requestId,
            DataCollector dataCollector) {

        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            DSARRequest request = dsarRequests.get(requestId);
            if (request == null) {
                throw new IllegalArgumentException("DSAR not found: " + requestId);
            }

            // Update status
            request = request.toBuilder()
                    .status(RequestStatus.IN_PROGRESS)
                    .processingStarted(Instant.now())
                    .build();
            dsarRequests.put(requestId, request);

            // Collect data
            List<DataCategory> collectedData = dataCollector.collectData(
                    request.getSubjectEmail(),
                    request.getSubjectId()
            );

            // Build response
            DSARResponse response = DSARResponse.builder()
                    .requestId(requestId)
                    .subjectEmail(request.getSubjectEmail())
                    .dataCategories(collectedData)
                    .totalRecords(collectedData.stream()
                            .mapToInt(DataCategory::getRecordCount)
                            .sum())
                    .generatedAt(Instant.now())
                    .validUntil(Instant.now().plus(Duration.ofDays(30)))
                    .build();

            // Update request with completion
            request = request.toBuilder()
                    .status(RequestStatus.COMPLETED)
                    .completedAt(Instant.now())
                    .build();
            dsarRequests.put(requestId, request);

            logAudit(AuditAction.DSAR_COMPLETED, "system",
                    Map.of("requestId", requestId, "recordCount", response.getTotalRecords()));

            return response;
        });
    }

    // --- GDPR Right to Erasure (RTBF) ---
    /**
     * Creates a GDPR erasure request.
     *
     * @param subjectEmail Email of the data subject
     * @param subjectId Optional subject identifier
     * @param requestedBy Who made the request
     * @param scope Scope of erasure (full or partial)
     * @return Promise of erasure request
     */
    public Promise<ErasureRequest> createErasureRequest(
            String subjectEmail,
            String subjectId,
            String requestedBy,
            ErasureScope scope) {

        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            String requestId = UUID.randomUUID().toString();

            ErasureRequest request = ErasureRequest.builder()
                    .requestId(requestId)
                    .subjectEmail(subjectEmail)
                    .subjectId(subjectId)
                    .requestedBy(requestedBy)
                    .scope(scope)
                    .status(RequestStatus.PENDING)
                    .build();

            erasureRequests.put(requestId, request);

            logAudit(AuditAction.ERASURE_REQUESTED, requestedBy,
                    Map.of("requestId", requestId, "scope", scope.name()));

            return request;
        });
    }

    /**
     * Executes an erasure request.
     *
     * @param requestId Request identifier
     * @param dataEraser Function to erase data
     * @return Promise of erasure result
     */
    public Promise<ErasureResult> executeErasure(
            String requestId,
            DataEraser dataEraser) {

        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            ErasureRequest request = erasureRequests.get(requestId);
            if (request == null) {
                throw new IllegalArgumentException("Erasure request not found: " + requestId);
            }

            // Update status
            request = request.toBuilder()
                    .status(RequestStatus.IN_PROGRESS)
                    .build();
            erasureRequests.put(requestId, request);

            // Execute erasure
            ErasureResult result = dataEraser.eraseData(
                    request.getSubjectEmail(),
                    request.getSubjectId(),
                    request.getScope()
            );

            // Update request
            request = request.toBuilder()
                    .status(result.isSuccess() ? RequestStatus.COMPLETED : RequestStatus.FAILED)
                    .completedAt(Instant.now())
                    .build();
            erasureRequests.put(requestId, request);

            logAudit(AuditAction.ERASURE_EXECUTED, "system",
                    Map.of(
                            "requestId", requestId,
                            "success", result.isSuccess(),
                            "deletedRecords", result.getDeletedRecords()
                    ));

            return result;
        });
    }

    // --- HIPAA Audit Log ---
    /**
     * Logs a HIPAA-compliant audit entry.
     *
     * @param action Action performed
     * @param actor Who performed the action
     * @param resourceType Type of resource accessed
     * @param resourceId Resource identifier
     * @param details Additional details
     */
    public void logHIPAAAudit(
            String action,
            String actor,
            String resourceType,
            String resourceId,
            Map<String, Object> details) {

        AuditLogEntry entry = AuditLogEntry.builder()
                .entryId(UUID.randomUUID().toString())
                .action(action)
                .actor(actor)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .details(details != null ? new HashMap<>(details) : new HashMap<>())
                .ipAddress(details != null ? (String) details.get("ip_address") : null)
                .complianceFramework(ComplianceFramework.HIPAA)
                .build();

        synchronized (auditLog) {
            auditLog.add(entry);
        }
    }

    /**
     * Exports HIPAA audit log for a date range.
     *
     * @param startDate Start of range
     * @param endDate End of range
     * @return Promise of audit log entries
     */
    public Promise<List<AuditLogEntry>> exportHIPAAAuditLog(
            Instant startDate,
            Instant endDate) {

        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            synchronized (auditLog) {
                return auditLog.stream()
                        .filter(e -> e.getComplianceFramework() == ComplianceFramework.HIPAA)
                        .filter(e -> !e.getTimestamp().isBefore(startDate) && !e.getTimestamp().isAfter(endDate))
                        .collect(Collectors.toList());
            }
        });
    }

    // --- SOC2 Compliance ---
    /**
     * Gets SOC2 compliance dashboard data.
     *
     * @return Promise of SOC2 dashboard
     */
    public Promise<SOC2Dashboard> getSOC2Dashboard() {
        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            List<SOC2Control> controls = new ArrayList<>(soc2Controls.values());

            long compliant = controls.stream()
                    .filter(c -> c.getStatus() == ControlStatus.COMPLIANT)
                    .count();
            long nonCompliant = controls.stream()
                    .filter(c -> c.getStatus() == ControlStatus.NON_COMPLIANT)
                    .count();
            long needsReview = controls.stream()
                    .filter(c -> c.getStatus() == ControlStatus.NEEDS_REVIEW)
                    .count();

            double complianceScore = (double) compliant / controls.size() * 100;

            return SOC2Dashboard.builder()
                    .totalControls(controls.size())
                    .compliantControls((int) compliant)
                    .nonCompliantControls((int) nonCompliant)
                    .needsReviewControls((int) needsReview)
                    .complianceScore(complianceScore)
                    .controls(controls)
                    .lastAssessmentDate(Instant.now())
                    .build();
        });
    }

    /**
     * Updates a SOC2 control status.
     *
     * @param controlId Control identifier
     * @param status New status
     * @param assessedBy Who assessed
     * @param notes Assessment notes
     * @return Promise of updated control
     */
    public Promise<SOC2Control> updateSOC2Control(
            String controlId,
            ControlStatus status,
            String assessedBy,
            String notes) {

        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            SOC2Control control = soc2Controls.get(controlId);
            if (control == null) {
                throw new IllegalArgumentException("Control not found: " + controlId);
            }

            control = control.toBuilder()
                    .status(status)
                    .lastAssessedBy(assessedBy)
                    .lastAssessedAt(Instant.now())
                    .assessmentNotes(notes)
                    .build();

            soc2Controls.put(controlId, control);

            logAudit(AuditAction.SOC2_CONTROL_UPDATED, assessedBy,
                    Map.of("controlId", controlId, "status", status.name()));

            return control;
        });
    }

    // --- Retention Policy ---
    /**
     * Sets a data retention policy.
     *
     * @param datasetId Dataset identifier
     * @param policy Retention policy
     * @return Promise of saved policy
     */
    public Promise<RetentionPolicy> setRetentionPolicy(
            String datasetId,
            RetentionPolicy policy) {

        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            retentionPolicies.put(datasetId, policy);

            logAudit(AuditAction.RETENTION_POLICY_SET, "system",
                    Map.of(
                            "datasetId", datasetId,
                            "retentionDays", policy.getRetentionDays()
                    ));

            return policy;
        });
    }

    /**
     * Checks and enforces retention policies.
     *
     * @param dataDeleter Function to delete expired data
     * @return Promise of enforcement results
     */
    public Promise<List<RetentionEnforcementResult>> enforceRetentionPolicies(
            DataDeleter dataDeleter) {

        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            List<RetentionEnforcementResult> results = new ArrayList<>();
            Instant now = Instant.now();

            for (Map.Entry<String, RetentionPolicy> entry : retentionPolicies.entrySet()) {
                String datasetId = entry.getKey();
                RetentionPolicy policy = entry.getValue();

                Instant cutoffDate = now.minus(Duration.ofDays(policy.getRetentionDays()));
                int deletedCount = dataDeleter.deleteOlderThan(datasetId, cutoffDate);

                results.add(RetentionEnforcementResult.builder()
                        .datasetId(datasetId)
                        .policyName(policy.getPolicyName())
                        .cutoffDate(cutoffDate)
                        .recordsDeleted(deletedCount)
                        .enforcedAt(now)
                        .build());

                logAudit(AuditAction.RETENTION_ENFORCED, "system",
                        Map.of(
                                "datasetId", datasetId,
                                "deletedRecords", deletedCount
                        ));
            }

            return results;
        });
    }

    // --- Private Helper Methods ---
    private void logAudit(AuditAction action, String actor, Map<String, Object> details) {
        AuditLogEntry entry = AuditLogEntry.builder()
                .entryId(UUID.randomUUID().toString())
                .action(action.name())
                .actor(actor)
                .details(details)
                .complianceFramework(ComplianceFramework.GDPR)
                .build();

        synchronized (auditLog) {
            auditLog.add(entry);
        }
    }

    private void initializeSOC2Controls() {
        // Security (CC1)
        addControl("CC1.1", "Security", "Control Environment",
                "Management demonstrates commitment to integrity and ethical values");
        addControl("CC1.2", "Security", "Control Environment",
                "Board provides oversight of internal control");
        addControl("CC1.3", "Security", "Control Environment",
                "Management establishes structure, reporting lines, and responsibilities");

        // Availability (A1)
        addControl("A1.1", "Availability", "System Availability",
                "System availability policies are established");
        addControl("A1.2", "Availability", "System Recovery",
                "System recovery procedures are documented and tested");

        // Processing Integrity (PI1)
        addControl("PI1.1", "Processing Integrity", "Data Processing",
                "System processing is complete, accurate, and timely");
        addControl("PI1.2", "Processing Integrity", "Data Validation",
                "Inputs are validated and data is complete");

        // Confidentiality (C1)
        addControl("C1.1", "Confidentiality", "Data Classification",
                "Confidential information is identified and classified");
        addControl("C1.2", "Confidentiality", "Access Control",
                "Access to confidential data is restricted");

        // Privacy (P1)
        addControl("P1.1", "Privacy", "Notice",
                "Privacy notice is provided to data subjects");
        addControl("P1.2", "Privacy", "Consent",
                "Consent is obtained for data collection");
        addControl("P1.3", "Privacy", "Data Minimization",
                "Only necessary data is collected and retained");
    }

    private void addControl(String controlId, String category, String subcategory, String description) {
        SOC2Control control = SOC2Control.builder()
                .controlId(controlId)
                .category(category)
                .subcategory(subcategory)
                .description(description)
                .status(ControlStatus.NEEDS_REVIEW)
                .build();
        soc2Controls.put(controlId, control);
    }

    // --- Inner Classes and Enums ---
    /**
     * Compliance frameworks.
     */
    public enum ComplianceFramework {
        GDPR, HIPAA, SOC2, PCI_DSS
    }

    /**
     * Request status.
     */
    public enum RequestStatus {
        PENDING, IN_PROGRESS, COMPLETED, FAILED, CANCELLED
    }

    /**
     * Audit actions.
     */
    public enum AuditAction {
        DSAR_CREATED,
        DSAR_COMPLETED,
        ERASURE_REQUESTED,
        ERASURE_EXECUTED,
        DATA_ACCESSED,
        DATA_MODIFIED,
        DATA_DELETED,
        RETENTION_POLICY_SET,
        RETENTION_ENFORCED,
        SOC2_CONTROL_UPDATED
    }

    /**
     * Erasure scope.
     */
    public enum ErasureScope {
        FULL, PARTIAL, ANONYMIZE
    }

    /**
     * SOC2 control status.
     */
    public enum ControlStatus {
        COMPLIANT, NON_COMPLIANT, NEEDS_REVIEW, NOT_APPLICABLE
    }

    /**
     * GDPR DSAR request.
     */
    @Getter
    @Builder(toBuilder = true)
    public static class DSARRequest {

        private final String requestId;
        private final String subjectEmail;
        private final String subjectId;
        private final String requestedBy;
        private final RequestStatus status;
        @Builder.Default
        private final Instant requestedAt = Instant.now();
        private final Instant processingStarted;
        private final Instant completedAt;
    }

    /**
     * DSAR response with collected data.
     */
    @Getter
    @Builder
    public static class DSARResponse {

        private final String requestId;
        private final String subjectEmail;
        @Builder.Default
        private final List<DataCategory> dataCategories = List.of();
        private final int totalRecords;
        private final Instant generatedAt;
        private final Instant validUntil;
    }

    /**
     * Data category in DSAR response.
     */
    @Getter
    @Builder
    public static class DataCategory {

        private final String categoryName;
        private final String description;
        private final int recordCount;
        private final List<String> dataFields;
        private final String retentionPeriod;
        private final String legalBasis;
    }

    /**
     * GDPR erasure request.
     */
    @Getter
    @Builder(toBuilder = true)
    public static class ErasureRequest {

        private final String requestId;
        private final String subjectEmail;
        private final String subjectId;
        private final String requestedBy;
        private final ErasureScope scope;
        private final RequestStatus status;
        @Builder.Default
        private final Instant requestedAt = Instant.now();
        private final Instant completedAt;
    }

    /**
     * Erasure execution result.
     */
    @Getter
    @Builder
    public static class ErasureResult {

        private final String requestId;
        private final boolean success;
        private final int deletedRecords;
        private final int anonymizedRecords;
        @Builder.Default
        private final List<String> datasetsAffected = List.of();
        @Builder.Default
        private final List<String> errors = List.of();
        private final Instant completedAt;
    }

    /**
     * Audit log entry.
     */
    @Getter
    @Builder
    public static class AuditLogEntry {

        private final String entryId;
        private final String action;
        private final String actor;
        private final String resourceType;
        private final String resourceId;
        @Builder.Default
        private final Map<String, Object> details = Map.of();
        private final String ipAddress;
        private final ComplianceFramework complianceFramework;
        @Builder.Default
        private final Instant timestamp = Instant.now();
    }

    /**
     * SOC2 control.
     */
    @Getter
    @Builder(toBuilder = true)
    public static class SOC2Control {

        private final String controlId;
        private final String category;
        private final String subcategory;
        private final String description;
        private final ControlStatus status;
        private final String lastAssessedBy;
        private final Instant lastAssessedAt;
        private final String assessmentNotes;
        @Builder.Default
        private final List<String> evidence = List.of();
    }

    /**
     * SOC2 dashboard.
     */
    @Getter
    @Builder
    public static class SOC2Dashboard {

        private final int totalControls;
        private final int compliantControls;
        private final int nonCompliantControls;
        private final int needsReviewControls;
        private final double complianceScore;
        @Builder.Default
        private final List<SOC2Control> controls = List.of();
        private final Instant lastAssessmentDate;
    }

    /**
     * Retention policy.
     */
    @Getter
    @Builder
    public static class RetentionPolicy {

        private final String policyName;
        private final int retentionDays;
        private final String legalBasis;
        private final boolean autoEnforce;
    }

    /**
     * Retention enforcement result.
     */
    @Getter
    @Builder
    public static class RetentionEnforcementResult {

        private final String datasetId;
        private final String policyName;
        private final Instant cutoffDate;
        private final int recordsDeleted;
        private final Instant enforcedAt;
    }

    // --- Functional Interfaces ---
    @FunctionalInterface
    public interface DataCollector {

        List<DataCategory> collectData(String subjectEmail, String subjectId);
    }

    @FunctionalInterface
    public interface DataEraser {

        ErasureResult eraseData(String subjectEmail, String subjectId, ErasureScope scope);
    }

    @FunctionalInterface
    public interface DataDeleter {

        int deleteOlderThan(String datasetId, Instant cutoffDate);
    }
}
