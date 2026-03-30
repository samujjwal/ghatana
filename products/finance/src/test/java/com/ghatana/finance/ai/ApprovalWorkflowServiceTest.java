/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

package com.ghatana.finance.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ApprovalWorkflowService}.
 *
 * <p>Covers: initiation creates a pending record, duplicate initiation is a no-op when
 * already approved, recording approval sets the flag, rejection removes the record.</p>
 *
 * @doc.type class
 * @doc.purpose Unit tests for model approval workflow lifecycle
 * @doc.layer product
 * @doc.pattern TestCase
 */
@DisplayName("ApprovalWorkflowService Tests")
class ApprovalWorkflowServiceTest {

    private ModelApprovalRepository approvalRepository;
    private CapturingAlertService alertService;
    private ApprovalWorkflowService service;

    @BeforeEach
    void setUp() {
        approvalRepository = new ModelApprovalRepository();
        alertService = new CapturingAlertService();
        service = new ApprovalWorkflowService(approvalRepository, alertService);
    }

    @Test
    @DisplayName("initiateApproval creates a PENDING record and fires alert")
    void initiateApproval_createsPendingRecordAndAlert() {
        service.initiateApproval("model-x");

        ModelApprovalRecord record = approvalRepository.findByModelId("model-x");
        assertNotNull(record, "A pending record should be created");
        assertFalse(record.isApproved(), "Newly initiated record must not be approved");
        assertEquals(1, alertService.alertCount, "An alert should be sent to approvers");
        assertTrue(alertService.lastTitle.contains("Approval Required"));
    }

    @Test
    @DisplayName("initiateApproval is a no-op when model is already approved")
    void initiateApproval_noOpWhenAlreadyApproved() {
        service.recordApproval("model-y", "approver-1", "1.0.0");
        int priorAlerts = alertService.alertCount;

        service.initiateApproval("model-y");

        // No additional record should overwrite the approval, no extra alert
        ModelApprovalRecord record = approvalRepository.findByModelId("model-y");
        assertTrue(record.isApproved(), "Approved record must remain approved");
        assertEquals(priorAlerts, alertService.alertCount, "No new alert should fire for an already-approved model");
    }

    @Test
    @DisplayName("recordApproval persists approval with approver and version")
    void recordApproval_persistsApproval() {
        service.recordApproval("model-z", "compliance-officer", "2.3.1");

        ModelApprovalRecord record = approvalRepository.findByModelId("model-z");
        assertNotNull(record);
        assertTrue(record.isApproved());
        assertEquals("compliance-officer", record.getApprover());
        assertEquals("2.3.1", record.getVersion());
        assertNotNull(record.getApprovalDate());
    }

    @Test
    @DisplayName("recordRejection removes the record and fires a rejection alert")
    void recordRejection_removesRecordAndAlerts() {
        service.initiateApproval("model-r");
        service.recordRejection("model-r", "Insufficient accuracy metrics");

        ModelApprovalRecord record = approvalRepository.findByModelId("model-r");
        assertNull(record, "Rejected model's record should be removed");
        assertTrue(alertService.lastTitle.contains("Rejected"),
            "A rejection alert should have been fired");
    }

    @Test
    @DisplayName("recordApproval rejects null modelId")
    void recordApproval_rejectsNullModelId() {
        assertThrows(NullPointerException.class,
            () -> service.recordApproval(null, "approver", "1.0"));
    }

    @Test
    @DisplayName("initiateApproval rejects null modelId")
    void initiateApproval_rejectsNullModelId() {
        assertThrows(NullPointerException.class,
            () -> service.initiateApproval(null));
    }

    // =========================================================================
    // Test helpers
    // =========================================================================

    private static final class CapturingAlertService extends AlertService {
        int alertCount;
        String lastTitle = "";
        String lastMessage = "";

        @Override
        public void sendAlert(String title, String message) {
            alertCount++;
            lastTitle = title;
            lastMessage = message;
        }
    }
}
