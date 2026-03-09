package com.ghatana.eventprocessing.registry;

import com.ghatana.aep.domain.registry.EventTypeRegistration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for registration domain model mapping to/from event payloads.
 *
 * Tests validate: - Bidirectional mapping (domain → payload → domain) - Field
 * preservation across mappings - Backward compatibility with missing optional
 * fields - Error handling for invalid payloads - List copying for immutability
 */
@DisplayName("Registration Mapper Tests")
class RegistrationMapperTest {

    @Test
    @DisplayName("Should map PatternRegistration to event payload and back")
    void shouldMapPatternRegistrationRoundTrip() {
        // GIVEN: Valid PatternRegistration
        UUID patternId = UUID.randomUUID();
        PatternRegistration original = PatternRegistration.builder()
                .patternId(patternId)
                .tenantId("tenant-test")
                .specification("SEQ(login.failed[2], transaction.decline)")
                .schemaVersion("1.0.0")
                .createdBy("user@example.com")
                .agentHint("fraud-detector")
                .consumerHint("security-team")
                .tags(List.of("fraud", "monitoring"))
                .active(true)
                .build();

        // WHEN: Convert to payload and back
        Map<String, Object> payload = RegistrationMappers.patternToEventPayload(original);
        PatternRegistration restored = RegistrationMappers.patternFromEventPayload(payload);

        // THEN: All fields match
        assertThat(restored.getPatternId())
                .as("Pattern ID should match")
                .isEqualTo(original.getPatternId());
        assertThat(restored.getTenantId())
                .as("Tenant ID should match")
                .isEqualTo(original.getTenantId());
        assertThat(restored.getSpecification())
                .as("Specification should match")
                .isEqualTo(original.getSpecification());
        assertThat(restored.getSchemaVersion())
                .as("Schema version should match")
                .isEqualTo(original.getSchemaVersion());
        assertThat(restored.getCreatedBy())
                .as("Creator should match")
                .isEqualTo(original.getCreatedBy());
        assertThat(restored.getAgentHint())
                .as("Agent hint should match")
                .isEqualTo(original.getAgentHint());
        assertThat(restored.getConsumerHint())
                .as("Consumer hint should match")
                .isEqualTo(original.getConsumerHint());
        assertThat(restored.getTags())
                .as("Tags should match")
                .isEqualTo(original.getTags());
        assertThat(restored.isActive())
                .as("Active flag should match")
                .isTrue();
    }

    @Test
    @DisplayName("Should handle PatternRegistration with null optional fields")
    void shouldHandlePatternRegistrationWithNullOptionalFields() {
        // GIVEN: PatternRegistration without optional fields
        UUID patternId = UUID.randomUUID();
        PatternRegistration original = PatternRegistration.builder()
                .patternId(patternId)
                .tenantId("tenant-test")
                .specification("SEQ(a, b)")
                .schemaVersion("1.0.0")
                .createdBy("user@example.com")
                .build();

        // WHEN: Convert to payload
        Map<String, Object> payload = RegistrationMappers.patternToEventPayload(original);

        // THEN: Payload includes null values
        assertThat(payload)
                .as("Payload should contain all fields")
                .containsKeys("agentHint", "consumerHint", "tags");
        assertThat(payload.get("agentHint"))
                .as("Agent hint should be null")
                .isNull();
    }

    @Test
    @DisplayName("Should map EventTypeRegistration to event payload and back")
    void shouldMapEventTypeRegistrationRoundTrip() {
        // GIVEN: Valid EventTypeRegistration
        UUID eventTypeId = UUID.randomUUID();
        Instant now = Instant.now();
        EventTypeRegistration original = EventTypeRegistration.builder()
                .eventTypeId(eventTypeId)
                .tenantId("tenant-test")
                .eventTypeName("sla.violation.detected")
                .schemaJson("{\"type\": \"object\", \"properties\": {}}")
                .schemaVersion("1.0.0")
                .createdBy("user@example.com")
                .createdAt(now)
                .updatedAt(now)
                .sourceHint("monitoring-service")
                .consumerHint("security-agent")
                .tags(List.of("sla", "violation"))
                .active(true)
                .build();

        // WHEN: Convert to payload and back
        Map<String, Object> payload = RegistrationMappers.eventTypeToEventPayload(original);
        EventTypeRegistration restored = RegistrationMappers.eventTypeFromEventPayload(payload);

        // THEN: All fields match
        assertThat(restored.getEventTypeId())
                .as("Event type ID should match")
                .isEqualTo(original.getEventTypeId());
        assertThat(restored.getTenantId())
                .as("Tenant ID should match")
                .isEqualTo(original.getTenantId());
        assertThat(restored.getEventTypeName())
                .as("Event type name should match")
                .isEqualTo(original.getEventTypeName());
        assertThat(restored.getSchemaJson())
                .as("Schema JSON should match")
                .isEqualTo(original.getSchemaJson());
        assertThat(restored.getSchemaVersion())
                .as("Schema version should match")
                .isEqualTo(original.getSchemaVersion());
        assertThat(restored.getSourceHint())
                .as("Source hint should match")
                .isEqualTo(original.getSourceHint());
        assertThat(restored.getConsumerHint())
                .as("Consumer hint should match")
                .isEqualTo(original.getConsumerHint());
    }

    @Test
    @DisplayName("Should reject invalid PatternRegistration mapping")
    void shouldRejectInvalidPatternRegistrationMapping() {
        // GIVEN: Invalid registration (no pattern ID)
        PatternRegistration invalid = PatternRegistration.builder()
                .tenantId("tenant-test")
                .specification("SEQ(a, b)")
                .schemaVersion("1.0.0")
                .createdBy("user@example.com")
                .build();

        // WHEN/THEN: Mapping throws exception
        assertThatThrownBy(
                () -> RegistrationMappers.patternToEventPayload(invalid))
                .as("Should reject invalid registration")
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should reject null payload for pattern mapping")
    void shouldRejectNullPayloadForPatternMapping() {
        // GIVEN: Null payload
        Map<String, Object> nullPayload = null;

        // WHEN/THEN: Mapping throws exception
        assertThatThrownBy(
                () -> RegistrationMappers.patternFromEventPayload(nullPayload))
                .as("Should reject null payload")
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should reject empty payload for event type mapping")
    void shouldRejectEmptyPayloadForEventTypeMapping() {
        // GIVEN: Empty payload
        Map<String, Object> emptyPayload = Map.of();

        // WHEN/THEN: Mapping throws exception
        assertThatThrownBy(
                () -> RegistrationMappers.eventTypeFromEventPayload(emptyPayload))
                .as("Should reject empty payload")
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should support backward compatibility with missing optional fields")
    void shouldSupportBackwardCompatibilityWithMissingOptionalFields() {
        // GIVEN: Payload from older version (missing optional fields)
        Map<String, Object> payload = Map.of(
                "patternId", UUID.randomUUID().toString(),
                "tenantId", "tenant-test",
                "specification", "SEQ(a, b)",
                "schemaVersion", "1.0.0",
                "createdBy", "user@example.com",
                "createdAt", Instant.now().toString(),
                "updatedAt", Instant.now().toString());

        // WHEN: Map from payload
        PatternRegistration restored = RegistrationMappers.patternFromEventPayload(payload);

        // THEN: Registration is valid with default values for missing fields
        assertThat(restored)
                .as("Registration should be valid")
                .matches(PatternRegistration::isValid);
        assertThat(restored.getAgentHint())
                .as("Missing agentHint should be null")
                .isNull();
        assertThat(restored.getConsumerHint())
                .as("Missing consumerHint should be null")
                .isNull();
        assertThat(restored.getTags())
                .as("Missing tags should be empty list")
                .isEmpty();
        assertThat(restored.isActive())
                .as("Missing active flag should default to true")
                .isTrue();
    }
}
