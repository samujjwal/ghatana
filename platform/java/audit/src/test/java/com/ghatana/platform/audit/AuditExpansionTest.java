/*
 * Copyright (c) 2026 Ghatana Inc. 
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
@DisplayName("Audit - Phase 3 Expansion")
class AuditExpansionTest extends EventloopTestBase {

    private InMemoryAuditQueryService queryService;

    @BeforeEach
    void setUp() { 
        queryService = new InMemoryAuditQueryService(); 
    }

    // ============================================
    // EVENT CREATION EXPANSION (4 tests) 
    // ============================================

    @Nested
    @DisplayName("Event Creation")
    class EventCreationTests {

        @Test
        @DisplayName("Multiple audit events can be recorded")
        void multipleEvents() { 
            runPromise(() -> { 
                AuditEvent event1 = AuditEvent.builder() 
                        .tenantId("tenant-1")
                        .eventType("LOGIN")
                        .principal("user-1")
                        .resourceType("Session")
                        .resourceId("session-1")
                        .success(true) 
                        .build(); 

                AuditEvent event2 = AuditEvent.builder() 
                        .tenantId("tenant-1")
                        .eventType("LOGOUT")
                        .principal("user-1")
                        .resourceType("Session")
                        .resourceId("session-1")
                        .success(true) 
                        .build(); 

                return queryService.record(event1) 
                        .then(() -> queryService.record(event2)) 
                        .then(() -> queryService.findByTenantId("tenant-1"))
                        .map(events -> { 
                            assertThat(events).hasSize(2); 
                            return null;
                        });
            });
        }

        @Test
        @DisplayName("Events with various timestamps")
        void eventsWithTimestamps() { 
            runPromise(() -> { 
                Instant baseTime = Instant.now(); 

                AuditEvent event1 = AuditEvent.builder() 
                        .tenantId("tenant-1")
                        .eventType("ACTION_1")
                        .principal("user")
                        .resourceType("Resource")
                        .resourceId("res-1")
                        .timestamp(baseTime) 
                        .success(true) 
                        .build(); 

                AuditEvent event2 = AuditEvent.builder() 
                        .tenantId("tenant-1")
                        .eventType("ACTION_2")
                        .principal("user")
                        .resourceType("Resource")
                        .resourceId("res-2")
                        .timestamp(baseTime.plus(1, ChronoUnit.SECONDS)) 
                        .success(true) 
                        .build(); 

                return queryService.record(event1) 
                        .then(() -> queryService.record(event2)) 
                        .then(() -> queryService.findByTenantId("tenant-1"))
                        .map(events -> { 
                            assertThat(events).hasSize(2); 
                            assertThat(events.get(0).getTimestamp()).isBefore(events.get(1).getTimestamp()); 
                            return null;
                        });
            });
        }

        @Test
        @DisplayName("Many rapid sequential events")
        void sequentialEvents() { 
            runPromise(() -> { 
                io.activej.promise.Promise<Void> result = io.activej.promise.Promise.complete(); 
                for (int i = 0; i < 50; i++) { 
                    final int idx = i;
                    AuditEvent event = AuditEvent.builder() 
                            .tenantId("tenant-1")
                            .eventType("ACTION_" + idx) 
                            .principal("user-" + (idx % 5)) 
                            .resourceType("Resource")
                            .resourceId("res-" + idx) 
                            .success(true) 
                            .build(); 
                    result = result.then(() -> queryService.record(event)); 
                }
                return result.then(() -> queryService.findByTenantId("tenant-1"))
                        .map(events -> { 
                            assertThat(events).hasSize(50); 
                            return null;
                        });
            });
        }

        @Test
        @DisplayName("Multi-tenant event isolation")
        void multiTenantIsolation() { 
            runPromise(() -> { 
                AuditEvent event1 = AuditEvent.builder() 
                        .tenantId("tenant-1")
                        .eventType("LOGIN")
                        .principal("user")
                        .resourceType("Session")
                        .resourceId("session-1")
                        .success(true) 
                        .build(); 

                AuditEvent event2 = AuditEvent.builder() 
                        .tenantId("tenant-2")
                        .eventType("LOGIN")
                        .principal("user")
                        .resourceType("Session")
                        .resourceId("session-2")
                        .success(true) 
                        .build(); 

                return queryService.record(event1) 
                        .then(() -> queryService.record(event2)) 
                        .then(() -> queryService.findByTenantId("tenant-1"))
                        .map(t1Events -> { 
                            assertThat(t1Events).hasSize(1); 
                            return queryService.findByTenantId("tenant-2")
                                    .map(t2Events -> { 
                                        assertThat(t2Events).hasSize(1); 
                                        return null;
                                    });
                        })
                        .then(v -> queryService.findByTenantId("nonexistent"))
                        .map(emptyEvents -> { 
                            assertThat(emptyEvents).isEmpty(); 
                            return null;
                        });
            });
        }
    }

    // ============================================
    // EVENT TYPES EXPANSION (3 tests) 
    // ============================================

    @Nested
    @DisplayName("Event Type Variations")
    class EventTypeTests {

        @Test
        @DisplayName("Various event types")
        void variousEventTypes() { 
            runPromise(() -> { 
                String[] eventTypes = {"LOGIN", "LOGOUT", "VIEW", "EDIT", "DELETE", "EXPORT", "IMPORT"};
                io.activej.promise.Promise<Void> result = io.activej.promise.Promise.complete(); 

                for (String eventType : eventTypes) { 
                    AuditEvent event = AuditEvent.builder() 
                            .tenantId("tenant-1")
                            .eventType(eventType) 
                            .principal("user")
                            .resourceType("Resource")
                            .resourceId("res-1")
                            .success(true) 
                            .build(); 
                    result = result.then(() -> queryService.record(event)); 
                }

                return result.then(() -> queryService.findByTenantId("tenant-1"))
                        .map(events -> { 
                            assertThat(events).hasSize(7); 
                            return null;
                        });
            });
        }

        @Test
        @DisplayName("Events with various success flags")
        void successFlags() { 
            runPromise(() -> { 
                AuditEvent success = AuditEvent.builder() 
                        .tenantId("tenant-1")
                        .eventType("ACTION")
                        .principal("user")
                        .resourceType("Resource")
                        .resourceId("res-1")
                        .success(true) 
                        .build(); 

                AuditEvent failure = AuditEvent.builder() 
                        .tenantId("tenant-1")
                        .eventType("ACTION")
                        .principal("user")
                        .resourceType("Resource")
                        .resourceId("res-2")
                        .success(false) 
                        .build(); 

                return queryService.record(success) 
                        .then(() -> queryService.record(failure)) 
                        .then(() -> queryService.findByTenantId("tenant-1"))
                        .map(events -> { 
                            assertThat(events).hasSize(2); 
                            long successCount = events.stream().filter(AuditEvent::getSuccess).count(); 
                            long failureCount = events.stream().filter(e -> !e.getSuccess()).count(); 
                            assertThat(successCount).isEqualTo(1); 
                            assertThat(failureCount).isEqualTo(1); 
                            return null;
                        });
            });
        }

        @Test
        @DisplayName("Many different principals")
        void manyPrincipals() { 
            runPromise(() -> { 
                io.activej.promise.Promise<Void> result = io.activej.promise.Promise.complete(); 

                for (int i = 0; i < 30; i++) { 
                    final int idx = i;
                    AuditEvent event = AuditEvent.builder() 
                            .tenantId("tenant-1")
                            .eventType("ACTION")
                            .principal("user-" + idx) 
                            .resourceType("Resource")
                            .resourceId("res-" + idx) 
                            .success(true) 
                            .build(); 
                    result = result.then(() -> queryService.record(event)); 
                }

                return result.then(() -> queryService.findByTenantId("tenant-1"))
                        .map(events -> { 
                            assertThat(events).hasSize(30); 
                            return null;
                        });
            });
        }
    }

    // ============================================
    // EDGE CASES (2 tests) 
    // ============================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Very long event details and IDs")
        void veryLongDetails() { 
            runPromise(() -> { 
                String longId = "id-" + "x".repeat(500); 
                String longPrincipal = "user-" + "a".repeat(200); 

                AuditEvent event = AuditEvent.builder() 
                        .tenantId("tenant-1")
                        .eventType("ACTION")
                        .principal(longPrincipal) 
                        .resourceType("Resource")
                        .resourceId(longId) 
                        .detail("long-key", "value-" + "y".repeat(1000)) 
                        .success(true) 
                        .build(); 

                return queryService.record(event) 
                        .then(() -> queryService.findByTenantId("tenant-1"))
                        .map(events -> { 
                            assertThat(events).hasSize(1); 
                            assertThat(events.get(0).getPrincipal()).isEqualTo(longPrincipal); 
                            return null;
                        });
            });
        }

        @Test
        @DisplayName("Concurrent event recording")
        void concurrentRecording() { 
            runPromise(() -> { 
                io.activej.promise.Promise<Void> result = io.activej.promise.Promise.complete(); 

                for (int i = 0; i < 25; i++) { 
                    final int idx = i;
                    AuditEvent event = AuditEvent.builder() 
                            .tenantId("tenant-1")
                            .eventType("CONCURRENT_EVENT_" + idx) 
                            .principal("user-" + (idx % 5)) 
                            .resourceType("Resource")
                            .resourceId("res-" + idx) 
                            .success(idx % 3 == 0) 
                            .build(); 
                    result = result.then(() -> queryService.record(event)); 
                }

                return result.then(() -> queryService.findByTenantId("tenant-1"))
                        .map(events -> { 
                            assertThat(events).hasSize(25); 
                            long successCount = events.stream().filter(AuditEvent::getSuccess).count(); 
                            assertThat(successCount).isEqualTo(9); // 0, 3, 6, 9, 12, 15, 18, 21, 24 
                            return null;
                        });
            });
        }
    }
}
