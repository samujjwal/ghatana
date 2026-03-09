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
@DisplayName("AuditEvent")
class AuditEventTest {

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("should build event with all fields")
        void shouldBuildWithAllFields() {
            var now = Instant.now();
            var event = AuditEvent.builder()
                    .id("evt-1")
                    .tenantId("tenant-1")
                    .eventType("USER_LOGIN")
                    .principal("user@example.com")
                    .resourceType("Session")
                    .resourceId("session-123")
                    .success(true)
                    .timestamp(now)
                    .detail("ip", "192.168.1.1")
                    .build();

            assertThat(event.getId()).isEqualTo("evt-1");
            assertThat(event.getTenantId()).isEqualTo("tenant-1");
            assertThat(event.getEventType()).isEqualTo("USER_LOGIN");
            assertThat(event.getPrincipal()).isEqualTo("user@example.com");
            assertThat(event.getResourceType()).isEqualTo("Session");
            assertThat(event.getResourceId()).isEqualTo("session-123");
            assertThat(event.getSuccess()).isTrue();
            assertThat(event.getTimestamp()).isEqualTo(now);
            assertThat(event.getDetail("ip")).isEqualTo("192.168.1.1");
        }

        @Test
        @DisplayName("should generate id when not provided")
        void shouldGenerateIdWhenMissing() {
            var event = AuditEvent.builder()
                    .tenantId("t1")
                    .eventType("TEST")
                    .build();

            assertThat(event.getId()).isNotNull().isNotEmpty();
        }

        @Test
        @DisplayName("should generate timestamp when not provided")
        void shouldGenerateTimestampWhenMissing() {
            var before = Instant.now();
            var event = AuditEvent.builder()
                    .tenantId("t1")
                    .eventType("TEST")
                    .build();
            var after = Instant.now();

            assertThat(event.getTimestamp()).isBetween(before, after);
        }

        @Test
        @DisplayName("should reject null tenantId")
        void shouldRejectNullTenantId() {
            assertThatThrownBy(() -> AuditEvent.builder()
                    .eventType("TEST")
                    .build())
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("tenantId");
        }

        @Test
        @DisplayName("should reject null eventType")
        void shouldRejectNullEventType() {
            assertThatThrownBy(() -> AuditEvent.builder()
                    .tenantId("t1")
                    .build())
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("eventType");
        }
    }

    @Nested
    @DisplayName("Immutability")
    class ImmutabilityTests {

        @Test
        @DisplayName("details map should be unmodifiable")
        void detailsShouldBeUnmodifiable() {
            var event = AuditEvent.builder()
                    .tenantId("t1")
                    .eventType("TEST")
                    .detail("key", "value")
                    .build();

            assertThatThrownBy(() -> event.getDetails().put("bad", "entry"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("should support bulk details via map")
        void shouldSupportBulkDetails() {
            var details = Map.<String, Object>of("a", 1, "b", 2);
            var event = AuditEvent.builder()
                    .tenantId("t1")
                    .eventType("TEST")
                    .details(details)
                    .build();

            assertThat(event.getDetails()).containsEntry("a", 1).containsEntry("b", 2);
        }
    }
}
