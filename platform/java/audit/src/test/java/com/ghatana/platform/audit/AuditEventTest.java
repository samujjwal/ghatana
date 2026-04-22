package com.ghatana.platform.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link AuditEvent} value object.
 */
@DisplayName("AuditEvent [GH-90000]")
class AuditEventTest {

    @Nested
    @DisplayName("Builder [GH-90000]")
    class BuilderTests {

        @Test
        @DisplayName("should build event with all fields [GH-90000]")
        void shouldBuildWithAllFields() { // GH-90000
            var now = Instant.now(); // GH-90000
            var event = AuditEvent.builder() // GH-90000
                    .id("evt-1 [GH-90000]")
                    .tenantId("tenant-1 [GH-90000]")
                    .eventType("USER_LOGIN [GH-90000]")
                    .principal("user@example.com [GH-90000]")
                    .resourceType("Session [GH-90000]")
                    .resourceId("session-123 [GH-90000]")
                    .success(true) // GH-90000
                    .timestamp(now) // GH-90000
                    .detail("ip", "192.168.1.1") // GH-90000
                    .build(); // GH-90000

            assertThat(event.getId()).isEqualTo("evt-1 [GH-90000]");
            assertThat(event.getTenantId()).isEqualTo("tenant-1 [GH-90000]");
            assertThat(event.getEventType()).isEqualTo("USER_LOGIN [GH-90000]");
            assertThat(event.getPrincipal()).isEqualTo("user@example.com [GH-90000]");
            assertThat(event.getResourceType()).isEqualTo("Session [GH-90000]");
            assertThat(event.getResourceId()).isEqualTo("session-123 [GH-90000]");
            assertThat(event.getSuccess()).isTrue(); // GH-90000
            assertThat(event.getTimestamp()).isEqualTo(now); // GH-90000
            assertThat(event.getDetail("ip [GH-90000]")).isEqualTo("192.168.1.1 [GH-90000]");
        }

        @Test
        @DisplayName("should generate id when not provided [GH-90000]")
        void shouldGenerateIdWhenMissing() { // GH-90000
            var event = AuditEvent.builder() // GH-90000
                    .tenantId("t1 [GH-90000]")
                    .eventType("TEST [GH-90000]")
                    .build(); // GH-90000

            assertThat(event.getId()).isNotNull().isNotEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should generate timestamp when not provided [GH-90000]")
        void shouldGenerateTimestampWhenMissing() { // GH-90000
            var before = Instant.now(); // GH-90000
            var event = AuditEvent.builder() // GH-90000
                    .tenantId("t1 [GH-90000]")
                    .eventType("TEST [GH-90000]")
                    .build(); // GH-90000
            var after = Instant.now(); // GH-90000

            assertThat(event.getTimestamp()).isBetween(before, after); // GH-90000
        }

        @Test
        @DisplayName("should reject null tenantId [GH-90000]")
        void shouldRejectNullTenantId() { // GH-90000
            assertThatThrownBy(() -> AuditEvent.builder() // GH-90000
                    .eventType("TEST [GH-90000]")
                    .build()) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("tenantId [GH-90000]");
        }

        @Test
        @DisplayName("should reject null eventType [GH-90000]")
        void shouldRejectNullEventType() { // GH-90000
            assertThatThrownBy(() -> AuditEvent.builder() // GH-90000
                    .tenantId("t1 [GH-90000]")
                    .build()) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("eventType [GH-90000]");
        }
    }

    @Nested
    @DisplayName("Immutability [GH-90000]")
    class ImmutabilityTests {

        @Test
        @DisplayName("details map should be unmodifiable [GH-90000]")
        void detailsShouldBeUnmodifiable() { // GH-90000
            var event = AuditEvent.builder() // GH-90000
                    .tenantId("t1 [GH-90000]")
                    .eventType("TEST [GH-90000]")
                    .detail("key", "value") // GH-90000
                    .build(); // GH-90000

            assertThatThrownBy(() -> event.getDetails().put("bad", "entry")) // GH-90000
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        }

        @Test
        @DisplayName("should support bulk details via map [GH-90000]")
        void shouldSupportBulkDetails() { // GH-90000
            var details = Map.<String, Object>of("a", 1, "b", 2); // GH-90000
            var event = AuditEvent.builder() // GH-90000
                    .tenantId("t1 [GH-90000]")
                    .eventType("TEST [GH-90000]")
                    .details(details) // GH-90000
                    .build(); // GH-90000

            assertThat(event.getDetails()).containsEntry("a", 1).containsEntry("b", 2); // GH-90000
        }
    }
}
