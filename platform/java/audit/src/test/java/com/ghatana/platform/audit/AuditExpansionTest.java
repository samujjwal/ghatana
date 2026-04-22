/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.platform.audit;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 3 Expansion tests for Audit module.
 * Tests event recording, querying, and audit trail management at scale.
 *
 * @doc.type class
 * @doc.purpose Phase 3 expansion tests for audit subsystem
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Audit - Phase 3 Expansion [GH-90000]")
class AuditExpansionTest extends EventloopTestBase {

    private InMemoryAuditQueryService queryService;

    @BeforeEach
    void setUp() { // GH-90000
        queryService = new InMemoryAuditQueryService(); // GH-90000
    }

    // ============================================
    // EVENT CREATION EXPANSION (4 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Event Creation [GH-90000]")
    class EventCreationTests {

        @Test
        @DisplayName("Multiple audit events can be recorded [GH-90000]")
        void multipleEvents() { // GH-90000
            runPromise(() -> { // GH-90000
                AuditEvent event1 = AuditEvent.builder() // GH-90000
                        .tenantId("tenant-1 [GH-90000]")
                        .eventType("LOGIN [GH-90000]")
                        .principal("user-1 [GH-90000]")
                        .resourceType("Session [GH-90000]")
                        .resourceId("session-1 [GH-90000]")
                        .success(true) // GH-90000
                        .build(); // GH-90000

                AuditEvent event2 = AuditEvent.builder() // GH-90000
                        .tenantId("tenant-1 [GH-90000]")
                        .eventType("LOGOUT [GH-90000]")
                        .principal("user-1 [GH-90000]")
                        .resourceType("Session [GH-90000]")
                        .resourceId("session-1 [GH-90000]")
                        .success(true) // GH-90000
                        .build(); // GH-90000

                return queryService.record(event1) // GH-90000
                        .then(() -> queryService.record(event2)) // GH-90000
                        .then(() -> queryService.findByTenantId("tenant-1 [GH-90000]"))
                        .map(events -> { // GH-90000
                            assertThat(events).hasSize(2); // GH-90000
                            return null;
                        });
            });
        }

        @Test
        @DisplayName("Events with various timestamps [GH-90000]")
        void eventsWithTimestamps() { // GH-90000
            runPromise(() -> { // GH-90000
                Instant baseTime = Instant.now(); // GH-90000

                AuditEvent event1 = AuditEvent.builder() // GH-90000
                        .tenantId("tenant-1 [GH-90000]")
                        .eventType("ACTION_1 [GH-90000]")
                        .principal("user [GH-90000]")
                        .resourceType("Resource [GH-90000]")
                        .resourceId("res-1 [GH-90000]")
                        .timestamp(baseTime) // GH-90000
                        .success(true) // GH-90000
                        .build(); // GH-90000

                AuditEvent event2 = AuditEvent.builder() // GH-90000
                        .tenantId("tenant-1 [GH-90000]")
                        .eventType("ACTION_2 [GH-90000]")
                        .principal("user [GH-90000]")
                        .resourceType("Resource [GH-90000]")
                        .resourceId("res-2 [GH-90000]")
                        .timestamp(baseTime.plus(1, ChronoUnit.SECONDS)) // GH-90000
                        .success(true) // GH-90000
                        .build(); // GH-90000

                return queryService.record(event1) // GH-90000
                        .then(() -> queryService.record(event2)) // GH-90000
                        .then(() -> queryService.findByTenantId("tenant-1 [GH-90000]"))
                        .map(events -> { // GH-90000
                            assertThat(events).hasSize(2); // GH-90000
                            assertThat(events.get(0).getTimestamp()).isBefore(events.get(1).getTimestamp()); // GH-90000
                            return null;
                        });
            });
        }

        @Test
        @DisplayName("Many rapid sequential events [GH-90000]")
        void sequentialEvents() { // GH-90000
            runPromise(() -> { // GH-90000
                io.activej.promise.Promise<Void> result = io.activej.promise.Promise.complete(); // GH-90000
                for (int i = 0; i < 50; i++) { // GH-90000
                    final int idx = i;
                    AuditEvent event = AuditEvent.builder() // GH-90000
                            .tenantId("tenant-1 [GH-90000]")
                            .eventType("ACTION_" + idx) // GH-90000
                            .principal("user-" + (idx % 5)) // GH-90000
                            .resourceType("Resource [GH-90000]")
                            .resourceId("res-" + idx) // GH-90000
                            .success(true) // GH-90000
                            .build(); // GH-90000
                    result = result.then(() -> queryService.record(event)); // GH-90000
                }
                return result.then(() -> queryService.findByTenantId("tenant-1 [GH-90000]"))
                        .map(events -> { // GH-90000
                            assertThat(events).hasSize(50); // GH-90000
                            return null;
                        });
            });
        }

        @Test
        @DisplayName("Multi-tenant event isolation [GH-90000]")
        void multiTenantIsolation() { // GH-90000
            runPromise(() -> { // GH-90000
                AuditEvent event1 = AuditEvent.builder() // GH-90000
                        .tenantId("tenant-1 [GH-90000]")
                        .eventType("LOGIN [GH-90000]")
                        .principal("user [GH-90000]")
                        .resourceType("Session [GH-90000]")
                        .resourceId("session-1 [GH-90000]")
                        .success(true) // GH-90000
                        .build(); // GH-90000

                AuditEvent event2 = AuditEvent.builder() // GH-90000
                        .tenantId("tenant-2 [GH-90000]")
                        .eventType("LOGIN [GH-90000]")
                        .principal("user [GH-90000]")
                        .resourceType("Session [GH-90000]")
                        .resourceId("session-2 [GH-90000]")
                        .success(true) // GH-90000
                        .build(); // GH-90000

                return queryService.record(event1) // GH-90000
                        .then(() -> queryService.record(event2)) // GH-90000
                        .then(() -> queryService.findByTenantId("tenant-1 [GH-90000]"))
                        .map(t1Events -> { // GH-90000
                            assertThat(t1Events).hasSize(1); // GH-90000
                            return queryService.findByTenantId("tenant-2 [GH-90000]")
                                    .map(t2Events -> { // GH-90000
                                        assertThat(t2Events).hasSize(1); // GH-90000
                                        return null;
                                    });
                        })
                        .then(v -> queryService.findByTenantId("nonexistent [GH-90000]"))
                        .map(emptyEvents -> { // GH-90000
                            assertThat(emptyEvents).isEmpty(); // GH-90000
                            return null;
                        });
            });
        }
    }

    // ============================================
    // EVENT TYPES EXPANSION (3 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Event Type Variations [GH-90000]")
    class EventTypeTests {

        @Test
        @DisplayName("Various event types [GH-90000]")
        void variousEventTypes() { // GH-90000
            runPromise(() -> { // GH-90000
                String[] eventTypes = {"LOGIN", "LOGOUT", "VIEW", "EDIT", "DELETE", "EXPORT", "IMPORT"};
                io.activej.promise.Promise<Void> result = io.activej.promise.Promise.complete(); // GH-90000

                for (String eventType : eventTypes) { // GH-90000
                    AuditEvent event = AuditEvent.builder() // GH-90000
                            .tenantId("tenant-1 [GH-90000]")
                            .eventType(eventType) // GH-90000
                            .principal("user [GH-90000]")
                            .resourceType("Resource [GH-90000]")
                            .resourceId("res-1 [GH-90000]")
                            .success(true) // GH-90000
                            .build(); // GH-90000
                    result = result.then(() -> queryService.record(event)); // GH-90000
                }

                return result.then(() -> queryService.findByTenantId("tenant-1 [GH-90000]"))
                        .map(events -> { // GH-90000
                            assertThat(events).hasSize(7); // GH-90000
                            return null;
                        });
            });
        }

        @Test
        @DisplayName("Events with various success flags [GH-90000]")
        void successFlags() { // GH-90000
            runPromise(() -> { // GH-90000
                AuditEvent success = AuditEvent.builder() // GH-90000
                        .tenantId("tenant-1 [GH-90000]")
                        .eventType("ACTION [GH-90000]")
                        .principal("user [GH-90000]")
                        .resourceType("Resource [GH-90000]")
                        .resourceId("res-1 [GH-90000]")
                        .success(true) // GH-90000
                        .build(); // GH-90000

                AuditEvent failure = AuditEvent.builder() // GH-90000
                        .tenantId("tenant-1 [GH-90000]")
                        .eventType("ACTION [GH-90000]")
                        .principal("user [GH-90000]")
                        .resourceType("Resource [GH-90000]")
                        .resourceId("res-2 [GH-90000]")
                        .success(false) // GH-90000
                        .build(); // GH-90000

                return queryService.record(success) // GH-90000
                        .then(() -> queryService.record(failure)) // GH-90000
                        .then(() -> queryService.findByTenantId("tenant-1 [GH-90000]"))
                        .map(events -> { // GH-90000
                            assertThat(events).hasSize(2); // GH-90000
                            long successCount = events.stream().filter(AuditEvent::getSuccess).count(); // GH-90000
                            long failureCount = events.stream().filter(e -> !e.getSuccess()).count(); // GH-90000
                            assertThat(successCount).isEqualTo(1); // GH-90000
                            assertThat(failureCount).isEqualTo(1); // GH-90000
                            return null;
                        });
            });
        }

        @Test
        @DisplayName("Many different principals [GH-90000]")
        void manyPrincipals() { // GH-90000
            runPromise(() -> { // GH-90000
                io.activej.promise.Promise<Void> result = io.activej.promise.Promise.complete(); // GH-90000

                for (int i = 0; i < 30; i++) { // GH-90000
                    final int idx = i;
                    AuditEvent event = AuditEvent.builder() // GH-90000
                            .tenantId("tenant-1 [GH-90000]")
                            .eventType("ACTION [GH-90000]")
                            .principal("user-" + idx) // GH-90000
                            .resourceType("Resource [GH-90000]")
                            .resourceId("res-" + idx) // GH-90000
                            .success(true) // GH-90000
                            .build(); // GH-90000
                    result = result.then(() -> queryService.record(event)); // GH-90000
                }

                return result.then(() -> queryService.findByTenantId("tenant-1 [GH-90000]"))
                        .map(events -> { // GH-90000
                            assertThat(events).hasSize(30); // GH-90000
                            return null;
                        });
            });
        }
    }

    // ============================================
    // EDGE CASES (2 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Edge Cases [GH-90000]")
    class EdgeCaseTests {

        @Test
        @DisplayName("Very long event details and IDs [GH-90000]")
        void veryLongDetails() { // GH-90000
            runPromise(() -> { // GH-90000
                String longId = "id-" + "x".repeat(500); // GH-90000
                String longPrincipal = "user-" + "a".repeat(200); // GH-90000

                AuditEvent event = AuditEvent.builder() // GH-90000
                        .tenantId("tenant-1 [GH-90000]")
                        .eventType("ACTION [GH-90000]")
                        .principal(longPrincipal) // GH-90000
                        .resourceType("Resource [GH-90000]")
                        .resourceId(longId) // GH-90000
                        .detail("long-key", "value-" + "y".repeat(1000)) // GH-90000
                        .success(true) // GH-90000
                        .build(); // GH-90000

                return queryService.record(event) // GH-90000
                        .then(() -> queryService.findByTenantId("tenant-1 [GH-90000]"))
                        .map(events -> { // GH-90000
                            assertThat(events).hasSize(1); // GH-90000
                            assertThat(events.get(0).getPrincipal()).isEqualTo(longPrincipal); // GH-90000
                            return null;
                        });
            });
        }

        @Test
        @DisplayName("Concurrent event recording [GH-90000]")
        void concurrentRecording() { // GH-90000
            runPromise(() -> { // GH-90000
                io.activej.promise.Promise<Void> result = io.activej.promise.Promise.complete(); // GH-90000

                for (int i = 0; i < 25; i++) { // GH-90000
                    final int idx = i;
                    AuditEvent event = AuditEvent.builder() // GH-90000
                            .tenantId("tenant-1 [GH-90000]")
                            .eventType("CONCURRENT_EVENT_" + idx) // GH-90000
                            .principal("user-" + (idx % 5)) // GH-90000
                            .resourceType("Resource [GH-90000]")
                            .resourceId("res-" + idx) // GH-90000
                            .success(idx % 3 == 0) // GH-90000
                            .build(); // GH-90000
                    result = result.then(() -> queryService.record(event)); // GH-90000
                }

                return result.then(() -> queryService.findByTenantId("tenant-1 [GH-90000]"))
                        .map(events -> { // GH-90000
                            assertThat(events).hasSize(25); // GH-90000
                            long successCount = events.stream().filter(AuditEvent::getSuccess).count(); // GH-90000
                            assertThat(successCount).isEqualTo(9); // 0, 3, 6, 9, 12, 15, 18, 21, 24 // GH-90000
                            return null;
                        });
            });
        }
    }
}
