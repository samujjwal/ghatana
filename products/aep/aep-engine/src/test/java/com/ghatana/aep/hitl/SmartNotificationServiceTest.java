/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.hitl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for SmartNotificationService.
 *
 * @doc.type class
 * @doc.purpose Test smart notification service
 * @doc.layer product
 * @doc.pattern UnitTest
 */
@DisplayName("SmartNotificationService")
class SmartNotificationServiceTest {

    private SmartNotificationService service;

    @BeforeEach
    void setUp() {
        service = new DefaultSmartNotificationService();
    }

    @Nested
    @DisplayName("shouldNotify()")
    class ShouldNotifyTests {

        @Test
        @DisplayName("notifies for critical priority")
        void notifiesForCriticalPriority() {
            SmartNotificationService.NotificationDecision decision = service.shouldNotify(
                "item-1",
                SmartNotificationService.Priority.CRITICAL,
                Map.of("tenantId", "tenant-1")
            );

            assertThat(decision.shouldNotify()).isTrue();
            assertThat(decision.channel()).isEqualTo("in-app");
        }

        @Test
        @DisplayName("notifies for high priority")
        void notifiesForHighPriority() {
            SmartNotificationService.NotificationDecision decision = service.shouldNotify(
                "item-1",
                SmartNotificationService.Priority.HIGH,
                Map.of("tenantId", "tenant-1")
            );

            assertThat(decision.shouldNotify()).isTrue();
        }

        @Test
        @DisplayName("does not notify for info priority")
        void doesNotNotifyForInfoPriority() {
            SmartNotificationService.NotificationDecision decision = service.shouldNotify(
                "item-1",
                SmartNotificationService.Priority.INFO,
                Map.of("tenantId", "tenant-1")
            );

            assertThat(decision.shouldNotify()).isFalse();
            assertThat(decision.reason()).isEqualTo("priority_disabled");
        }

        @Test
        @DisplayName("respects confidence threshold")
        void respectsConfidenceThreshold() {
            SmartNotificationService.NotificationDecision decision = service.shouldNotify(
                "item-1",
                SmartNotificationService.Priority.HIGH,
                Map.of("tenantId", "tenant-1", "confidence", 0.5)
            );

            assertThat(decision.shouldNotify()).isFalse();
            assertThat(decision.reason()).isEqualTo("low_confidence");
        }

        @Test
        @DisplayName("uses email channel for offline users")
        void usesEmailChannelForOfflineUsers() {
            SmartNotificationService.NotificationDecision decision = service.shouldNotify(
                "item-1",
                SmartNotificationService.Priority.HIGH,
                Map.of("tenantId", "tenant-1", "userOnline", false)
            );

            assertThat(decision.shouldNotify()).isTrue();
            assertThat(decision.channel()).isEqualTo("email");
        }

        @Test
        @DisplayName("uses preferred channel from context")
        void usesPreferredChannelFromContext() {
            SmartNotificationService.NotificationDecision decision = service.shouldNotify(
                "item-1",
                SmartNotificationService.Priority.MEDIUM,
                Map.of("tenantId", "tenant-1", "preferredChannel", "sms")
            );

            assertThat(decision.shouldNotify()).isTrue();
            assertThat(decision.channel()).isEqualTo("sms");
        }

        @Test
        @DisplayName("rate limits notifications")
        void rateLimitsNotifications() {
            String tenantId = "tenant-1";
            
            // First notification
            service.shouldNotify("item-1", SmartNotificationService.Priority.HIGH, Map.of("tenantId", tenantId));
            
            // Immediate second notification should be rate limited
            SmartNotificationService.NotificationDecision decision = service.shouldNotify(
                "item-2",
                SmartNotificationService.Priority.HIGH,
                Map.of("tenantId", tenantId)
            );

            assertThat(decision.shouldNotify()).isFalse();
            assertThat(decision.reason()).isEqualTo("rate_limited");
        }
    }

    @Nested
    @DisplayName("Response Tracking")
    class ResponseTrackingTests {

        @Test
        @DisplayName("records response time")
        void recordsResponseTime() {
            service.recordResponseTime("tenant-1-item-1", 5000);
            
            SmartNotificationService.NotificationStats stats = service.getStats("tenant-1");
            assertThat(stats.averageResponseTimeMs()).isEqualTo(5000.0);
        }

        @Test
        @DisplayName("records dismissal")
        void recordsDismissal() {
            service.recordDismissal("tenant-1-item-1", "not_relevant");
            
            SmartNotificationService.NotificationStats stats = service.getStats("tenant-1");
            assertThat(stats.totalDismissed()).isEqualTo(1);
        }

        @Test
        @DisplayName("calculates notification rate")
        void calculatesNotificationRate() {
            String tenantId = "tenant-1";
            
            service.shouldNotify("item-1", SmartNotificationService.Priority.HIGH, Map.of("tenantId", tenantId));
            service.shouldNotify("item-2", SmartNotificationService.Priority.HIGH, Map.of("tenantId", tenantId));
            service.recordDismissal("tenant-1-item-1", "ignored");
            
            SmartNotificationService.NotificationStats stats = service.getStats(tenantId);
            assertThat(stats.notificationRate()).isEqualTo(0.5);
        }

        @Test
        @DisplayName("handles null tenant in response time")
        void handlesNullTenantInResponseTime() {
            service.recordResponseTime("item-1", 5000);
            
            // Should not throw
            SmartNotificationService.NotificationStats stats = service.getStats("default");
            assertThat(stats.averageResponseTimeMs()).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("getStats()")
    class GetStatsTests {

        @Test
        @DisplayName("returns stats for tenant")
        void returnsStatsForTenant() {
            String tenantId = "tenant-1";
            service.shouldNotify("item-1", SmartNotificationService.Priority.HIGH, Map.of("tenantId", tenantId));
            
            SmartNotificationService.NotificationStats stats = service.getStats(tenantId);
            assertThat(stats.totalSent()).isEqualTo(1);
        }

        @Test
        @DisplayName("returns empty stats for unknown tenant")
        void returnsEmptyStatsForUnknownTenant() {
            SmartNotificationService.NotificationStats stats = service.getStats("unknown");
            
            assertThat(stats.totalSent()).isEqualTo(0);
            assertThat(stats.totalDismissed()).isEqualTo(0);
            assertThat(stats.totalResponded()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("NotificationDecision")
    class NotificationDecisionTests {

        @Test
        @DisplayName("decision has required fields")
        void decisionHasRequiredFields() {
            SmartNotificationService.NotificationDecision decision = new SmartNotificationService.NotificationDecision(
                true, "in-app", "reason", Map.of()
            );

            assertThat(decision.shouldNotify()).isNotNull();
            assertThat(decision.channel()).isNotNull();
            assertThat(decision.reason()).isNotNull();
            assertThat(decision.metadata()).isNotNull();
        }
    }
}
