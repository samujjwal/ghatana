/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.governance.audit;

import com.ghatana.datacloud.entity.audit.AuditAction;
import com.ghatana.datacloud.entity.audit.AuditLog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Phase 3: Contract tests for GovernanceAuditService.
 *
 * <p>These tests enforce:
 * <ul>
 *   <li>Policy creation audit logging</li>
 *   <li>Policy update audit logging</li>
 *   <li>Policy deletion audit logging</li>
 *   <li>Rule creation audit logging</li>
 *   <li>Rule update audit logging</li>
 *   <li>Rule deletion audit logging</li>
 * </ul>
 */
@DisplayName("Governance Audit Service Tests (Phase 3)")
class GovernanceAuditServiceTest {

    // =========================================================================
    //  Policy Audit Logging
    // =========================================================================

    @Nested
    @DisplayName("Policy Audit Logging")
    class PolicyAuditTests {

        @Test
        @DisplayName("logs policy creation event")
        void logsPolicyCreationEvent() {
            GovernanceAuditService.AuditLogger logger = mock(GovernanceAuditService.AuditLogger.class);
            GovernanceAuditService service = new GovernanceAuditService(logger);

            service.logPolicyCreated("tenant-1", "user-1", "policy-1", Map.of("name", "Test Policy"));

            verify(logger).log(org.mockito.ArgumentMatchers.argThat(log -> 
                log.getAction() == AuditAction.CREATE_ENTITY &&
                log.getResourceType().equals("policy") &&
                log.getResourceId().equals("policy-1")
            ));
        }

        @Test
        @DisplayName("logs policy update event")
        void logsPolicyUpdateEvent() {
            GovernanceAuditService.AuditLogger logger = mock(GovernanceAuditService.AuditLogger.class);
            GovernanceAuditService service = new GovernanceAuditService(logger);

            Map<String, Map.Entry<String, String>> changes = Map.of(
                "name", Map.entry("Old Name", "New Name")
            );
            service.logPolicyUpdated("tenant-1", "user-1", "policy-1", changes);

            verify(logger).log(org.mockito.ArgumentMatchers.argThat(log -> 
                log.getAction() == AuditAction.UPDATE_ENTITY &&
                log.getResourceType().equals("policy") &&
                log.getResourceId().equals("policy-1")
            ));
        }

        @Test
        @DisplayName("logs policy deletion event")
        void logsPolicyDeletionEvent() {
            GovernanceAuditService.AuditLogger logger = mock(GovernanceAuditService.AuditLogger.class);
            GovernanceAuditService service = new GovernanceAuditService(logger);

            service.logPolicyDeleted("tenant-1", "user-1", "policy-1");

            verify(logger).log(org.mockito.ArgumentMatchers.argThat(log -> 
                log.getAction() == AuditAction.DELETE_ENTITY &&
                log.getResourceType().equals("policy") &&
                log.getResourceId().equals("policy-1")
            ));
        }
    }

    // =========================================================================
    //  Rule Audit Logging
    // =========================================================================

    @Nested
    @DisplayName("Rule Audit Logging")
    class RuleAuditTests {

        @Test
        @DisplayName("logs rule creation event")
        void logsRuleCreationEvent() {
            GovernanceAuditService.AuditLogger logger = mock(GovernanceAuditService.AuditLogger.class);
            GovernanceAuditService service = new GovernanceAuditService(logger);

            service.logRuleCreated("tenant-1", "user-1", "rule-1", Map.of("name", "Test Rule"));

            verify(logger).log(org.mockito.ArgumentMatchers.argThat(log -> 
                log.getAction() == AuditAction.CREATE_ENTITY &&
                log.getResourceType().equals("rule") &&
                log.getResourceId().equals("rule-1")
            ));
        }

        @Test
        @DisplayName("logs rule update event")
        void logsRuleUpdateEvent() {
            GovernanceAuditService.AuditLogger logger = mock(GovernanceAuditService.AuditLogger.class);
            GovernanceAuditService service = new GovernanceAuditService(logger);

            Map<String, Map.Entry<String, String>> changes = Map.of(
                "condition", Map.entry("old", "new")
            );
            service.logRuleUpdated("tenant-1", "user-1", "rule-1", changes);

            verify(logger).log(org.mockito.ArgumentMatchers.argThat(log -> 
                log.getAction() == AuditAction.UPDATE_ENTITY &&
                log.getResourceType().equals("rule") &&
                log.getResourceId().equals("rule-1")
            ));
        }

        @Test
        @DisplayName("logs rule deletion event")
        void logsRuleDeletionEvent() {
            GovernanceAuditService.AuditLogger logger = mock(GovernanceAuditService.AuditLogger.class);
            GovernanceAuditService service = new GovernanceAuditService(logger);

            service.logRuleDeleted("tenant-1", "user-1", "rule-1");

            verify(logger).log(org.mockito.ArgumentMatchers.argThat(log -> 
                log.getAction() == AuditAction.DELETE_ENTITY &&
                log.getResourceType().equals("rule") &&
                log.getResourceId().equals("rule-1")
            ));
        }
    }
}
