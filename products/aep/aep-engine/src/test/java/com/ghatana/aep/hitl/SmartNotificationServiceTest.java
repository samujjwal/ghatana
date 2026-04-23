/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
    void setUp() { // GH-90000
        service = new DefaultSmartNotificationService(); // GH-90000
    }

    @Nested
    @DisplayName("shouldNotify()")
    class ShouldNotifyTests {

        @Test
        @DisplayName("notifies for critical priority")
        void notifiesForCriticalPriority() { // GH-90000
            SmartNotificationService.NotificationDecision decision = service.shouldNotify( // GH-90000
                "item-1",
                SmartNotificationService.Priority.CRITICAL,
                Map.of("tenantId", "tenant-1") // GH-90000
            );

            assertThat(decision.shouldNotify()).isTrue(); // GH-90000
            assertThat(decision.channel()).isEqualTo("in-app");
        }

        @Test
        @DisplayName("notifies for high priority")
        void notifiesForHighPriority() { // GH-90000
            SmartNotificationService.NotificationDecision decision = service.shouldNotify( // GH-90000
                "item-1",
                SmartNotificationService.Priority.HIGH,
                Map.of("tenantId", "tenant-1") // GH-90000
            );

            assertThat(decision.shouldNotify()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("does not notify for info priority")
        void doesNotNotifyForInfoPriority() { // GH-90000
            SmartNotificationService.NotificationDecision decision = service.shouldNotify( // GH-90000
                "item-1",
                SmartNotificationService.Priority.INFO,
                Map.of("tenantId", "tenant-1") // GH-90000
            );

            assertThat(decision.shouldNotify()).isFalse(); // GH-90000
            assertThat(decision.reason()).isEqualTo("priority_disabled");
        }

        @Test
        @DisplayName("respects confidence threshold")
        void respectsConfidenceThreshold() { // GH-90000
            SmartNotificationService.NotificationDecision decision = service.shouldNotify( // GH-90000
                "item-1",
                SmartNotificationService.Priority.HIGH,
                Map.of("tenantId", "tenant-1", "confidence", 0.5) // GH-90000
            );

            assertThat(decision.shouldNotify()).isFalse(); // GH-90000
            assertThat(decision.reason()).isEqualTo("low_confidence");
        }

        @Test
        @DisplayName("uses email channel for offline users")
        void usesEmailChannelForOfflineUsers() { // GH-90000
            SmartNotificationService.NotificationDecision decision = service.shouldNotify( // GH-90000
                "item-1",
                SmartNotificationService.Priority.HIGH,
                Map.of("tenantId", "tenant-1", "userOnline", false) // GH-90000
            );

            assertThat(decision.shouldNotify()).isTrue(); // GH-90000
            assertThat(decision.channel()).isEqualTo("email");
        }

        @Test
        @DisplayName("uses preferred channel from context")
        void usesPreferredChannelFromContext() { // GH-90000
            SmartNotificationService.NotificationDecision decision = service.shouldNotify( // GH-90000
                "item-1",
                SmartNotificationService.Priority.MEDIUM,
                Map.of("tenantId", "tenant-1", "preferredChannel", "sms") // GH-90000
            );

            assertThat(decision.shouldNotify()).isTrue(); // GH-90000
            assertThat(decision.channel()).isEqualTo("sms");
        }

        @Test
        @DisplayName("rate limits notifications")
        void rateLimitsNotifications() { // GH-90000
            String tenantId = "tenant-1";
            
            // First notification
            service.shouldNotify("item-1", SmartNotificationService.Priority.HIGH, Map.of("tenantId", tenantId)); // GH-90000
            
            // Immediate second notification should be rate limited
            SmartNotificationService.NotificationDecision decision = service.shouldNotify( // GH-90000
                "item-2",
                SmartNotificationService.Priority.HIGH,
                Map.of("tenantId", tenantId) // GH-90000
            );

            assertThat(decision.shouldNotify()).isFalse(); // GH-90000
            assertThat(decision.reason()).isEqualTo("rate_limited");
        }
    }

    @Nested
    @DisplayName("Response Tracking")
    class ResponseTrackingTests {

        @Test
        @DisplayName("records response time")
        void recordsResponseTime() { // GH-90000
            service.recordResponseTime("tenant-1-item-1", 5000); // GH-90000
            
            SmartNotificationService.NotificationStats stats = service.getStats("tenant-1");
            assertThat(stats.averageResponseTimeMs()).isEqualTo(5000.0); // GH-90000
        }

        @Test
        @DisplayName("records dismissal")
        void recordsDismissal() { // GH-90000
            service.recordDismissal("tenant-1-item-1", "not_relevant"); // GH-90000
            
            SmartNotificationService.NotificationStats stats = service.getStats("tenant-1");
            assertThat(stats.totalDismissed()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("calculates notification rate")
        void calculatesNotificationRate() { // GH-90000
            String tenantId = "tenant-1";
            
            service.shouldNotify("item-1", SmartNotificationService.Priority.HIGH, Map.of("tenantId", tenantId)); // GH-90000
            service.shouldNotify("item-2", SmartNotificationService.Priority.HIGH, Map.of("tenantId", tenantId)); // GH-90000
            service.recordDismissal("tenant-1-item-1", "ignored"); // GH-90000
            
            SmartNotificationService.NotificationStats stats = service.getStats(tenantId); // GH-90000
            assertThat(stats.notificationRate()).isEqualTo(0.5); // GH-90000
        }

        @Test
        @DisplayName("handles null tenant in response time")
        void handlesNullTenantInResponseTime() { // GH-90000
            service.recordResponseTime("item-1", 5000); // GH-90000
            
            // Should not throw
            SmartNotificationService.NotificationStats stats = service.getStats("default");
            assertThat(stats.averageResponseTimeMs()).isEqualTo(0.0); // GH-90000
        }
    }

    @Nested
    @DisplayName("getStats()")
    class GetStatsTests {

        @Test
        @DisplayName("returns stats for tenant")
        void returnsStatsForTenant() { // GH-90000
            String tenantId = "tenant-1";
            service.shouldNotify("item-1", SmartNotificationService.Priority.HIGH, Map.of("tenantId", tenantId)); // GH-90000
            
            SmartNotificationService.NotificationStats stats = service.getStats(tenantId); // GH-90000
            assertThat(stats.totalSent()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("returns empty stats for unknown tenant")
        void returnsEmptyStatsForUnknownTenant() { // GH-90000
            SmartNotificationService.NotificationStats stats = service.getStats("unknown");
            
            assertThat(stats.totalSent()).isEqualTo(0); // GH-90000
            assertThat(stats.totalDismissed()).isEqualTo(0); // GH-90000
            assertThat(stats.totalResponded()).isEqualTo(0); // GH-90000
        }
    }

    @Nested
    @DisplayName("NotificationDecision")
    class NotificationDecisionTests {

        @Test
        @DisplayName("decision has required fields")
        void decisionHasRequiredFields() { // GH-90000
            SmartNotificationService.NotificationDecision decision = new SmartNotificationService.NotificationDecision( // GH-90000
                true, "in-app", "reason", Map.of() // GH-90000
            );

            assertThat(decision.shouldNotify()).isNotNull(); // GH-90000
            assertThat(decision.channel()).isNotNull(); // GH-90000
            assertThat(decision.reason()).isNotNull(); // GH-90000
            assertThat(decision.metadata()).isNotNull(); // GH-90000
        }
    }
}
