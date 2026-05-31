package com.ghatana.datacloud;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DataCloud event envelope validation")
class DataCloudEventEnvelopeValidationTest extends EventloopTestBase {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("non-local profiles reject source-less events")
    void nonLocalRejectsSourceLessEvents() {
        DataCloud.DataCloudConfig config = DataCloud.DataCloudConfig.builder()
            .profile(DataCloud.DataCloudConfig.DataCloudProfile.SOVEREIGN)
            .customConfig(Map.of("sovereign.dataDir", tempDir.resolve("strict").toString()))
            .build();

        DataCloudClient client = DataCloud.create(config);
        try {
            DataCloudClient.Event event = DataCloudClient.Event.builder()
                .type("entity.saved")
                .payload(Map.of("id", "1"))
                .build();

            assertThatThrownBy(() -> runPromise(() -> client.appendEvent("tenant-1", event)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("event.source is required");
        } finally {
            client.close();
        }
    }

    @Test
    @DisplayName("local profile allows legacy source-less events")
    void localAllowsSourceLessEvents() {
        DataCloudClient client = DataCloud.forTesting();
        try {
            DataCloudClient.Event event = DataCloudClient.Event.builder()
                .type("entity.saved")
                .payload(Map.of("id", "1"))
                .build();
            runPromise(() -> client.appendEvent("tenant-1", event));
        } finally {
            client.close();
        }
    }

    @Test
    @DisplayName("P3-06: Valid events with all canonical fields are accepted")
    void validEventsWithAllCanonicalFieldsAccepted() {
        DataCloud.DataCloudConfig config = DataCloud.DataCloudConfig.builder()
            .profile(DataCloud.DataCloudConfig.DataCloudProfile.SOVEREIGN)
            .customConfig(Map.of("sovereign.dataDir", tempDir.resolve("valid").toString()))
            .build();

        DataCloudClient client = DataCloud.create(config);
        try {
            DataCloudClient.Event event = DataCloudClient.Event.builder()
                .type("entity.saved")
                .payload(Map.of("id", "1", "data", "test"))
                .source("test-service")
                .subjectType("Entity")
                .subjectId("entity-123")
                .schemaVersion("1.0.0")
                .correlationId("corr-abc-123")
                .causationId("cause-xyz-789")
                .actor("test-user")
                .classification("internal")
                .policyContext("default")
                .provenance("unit-test")
                .traceContext("trace-001")
                .build();

            // Should not throw
            DataCloudClient.Offset offset = runPromise(() -> client.appendEvent("tenant-1", event));
            org.assertj.core.api.Assertions.assertThat(offset.value()).isGreaterThanOrEqualTo(0L);
        } finally {
            client.close();
        }
    }

    @Test
    @DisplayName("P3-06: Events with minimal required fields are accepted")
    void eventsWithMinimalRequiredFieldsAccepted() {
        DataCloud.DataCloudConfig config = DataCloud.DataCloudConfig.builder()
            .profile(DataCloud.DataCloudConfig.DataCloudProfile.SOVEREIGN)
            .customConfig(Map.of("sovereign.dataDir", tempDir.resolve("minimal").toString()))
            .build();

        DataCloudClient client = DataCloud.create(config);
        try {
            DataCloudClient.Event event = DataCloudClient.Event.builder()
                .type("test.event")
                .payload(Map.of("key", "value"))
                .source("test-source")
                .build();

            // Should not throw - only type and source are required
            DataCloudClient.Offset offset = runPromise(() -> client.appendEvent("tenant-1", event));
            org.assertj.core.api.Assertions.assertThat(offset.value()).isGreaterThanOrEqualTo(0L);
        } finally {
            client.close();
        }
    }

    @Test
    @DisplayName("P3-06: Event.validate() checks required fields")
    void eventValidateChecksRequiredFields() {
        // Test event without type
        DataCloudClient.Event eventWithoutType = DataCloudClient.Event.builder()
            .payload(Map.of("key", "value"))
            .source("test")
            .build();
        org.assertj.core.api.Assertions.assertThatThrownBy(eventWithoutType::validate)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("event.type is required");

        // Test event with empty type
        DataCloudClient.Event eventWithEmptyType = DataCloudClient.Event.builder()
            .type("")
            .payload(Map.of("key", "value"))
            .source("test")
            .build();
        org.assertj.core.api.Assertions.assertThatThrownBy(eventWithEmptyType::validate)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("event.type is required");

        // Test event with blank source
        DataCloudClient.Event eventWithBlankSource = DataCloudClient.Event.builder()
            .type("test.event")
            .payload(Map.of("key", "value"))
            .source("")
            .build();
        org.assertj.core.api.Assertions.assertThatThrownBy(eventWithBlankSource::validate)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("event.source is required");
    }

    @Test
    @DisplayName("P3-06: Valid event passes validation")
    void validEventPassesValidation() {
        DataCloudClient.Event validEvent = DataCloudClient.Event.builder()
            .type("valid.event")
            .payload(Map.of("data", "test"))
            .source("test-service")
            .subjectType("TestSubject")
            .subjectId("123")
            .schemaVersion("1.0")
            .correlationId("corr-123")
            .causationId("cause-456")
            .actor("test-actor")
            .classification("test")
            .policyContext("default")
            .provenance("test-suite")
            .traceContext("trace-123")
            .build();

        // Should not throw
        org.assertj.core.api.Assertions.assertThatNoException()
            .isThrownBy(validEvent::validate);
    }

    @Test
    @DisplayName("P3-06: Optional fields can be empty")
    void optionalFieldsCanBeEmpty() {
        DataCloudClient.Event event = DataCloudClient.Event.builder()
            .type("test.event")
            .payload(Map.of("key", "value"))
            .source("test-source")
            // No optional fields set
            .build();

        // Should not throw - only type and source are required
        org.assertj.core.api.Assertions.assertThatNoException()
            .isThrownBy(event::validate);
    }

    @Test
    @DisplayName("P3-06: Event builder with all fields produces correct event")
    void eventBuilderWithAllFields() {
        java.time.Instant now = java.time.Instant.now();

        DataCloudClient.Event event = DataCloudClient.Event.builder()
            .type("comprehensive.test")
            .payload(Map.of("nested", Map.of("key", "value"), "array", java.util.List.of(1, 2, 3)))
            .headers(Map.of("custom-header", "custom-value"))
            .timestamp(now)
            .source("full-test-suite")
            .subjectType("TestEntity")
            .subjectId("entity-uuid-123")
            .schemaVersion("2.1.0")
            .correlationId("correlation-abc-def")
            .causationId("causation-ghi-jkl")
            .actor("test-actor-001")
            .classification("confidential")
            .policyContext("strict-policy")
            .provenance("test-data-center")
            .traceContext("distributed-trace-001")
            .build();

        // Verify all fields
        org.assertj.core.api.Assertions.assertThat(event.type()).isEqualTo("comprehensive.test");
        org.assertj.core.api.Assertions.assertThat(event.payload().get("nested")).isNotNull();
        org.assertj.core.api.Assertions.assertThat(event.headers().get("custom-header")).isEqualTo("custom-value");
        org.assertj.core.api.Assertions.assertThat(event.timestamp()).isEqualTo(now);
        org.assertj.core.api.Assertions.assertThat(event.source()).hasValue("full-test-suite");
        org.assertj.core.api.Assertions.assertThat(event.subjectType()).hasValue("TestEntity");
        org.assertj.core.api.Assertions.assertThat(event.subjectId()).hasValue("entity-uuid-123");
        org.assertj.core.api.Assertions.assertThat(event.schemaVersion()).hasValue("2.1.0");
        org.assertj.core.api.Assertions.assertThat(event.correlationId()).hasValue("correlation-abc-def");
        org.assertj.core.api.Assertions.assertThat(event.causationId()).hasValue("causation-ghi-jkl");
        org.assertj.core.api.Assertions.assertThat(event.actor()).hasValue("test-actor-001");
        org.assertj.core.api.Assertions.assertThat(event.classification()).hasValue("confidential");
        org.assertj.core.api.Assertions.assertThat(event.policyContext()).hasValue("strict-policy");
        org.assertj.core.api.Assertions.assertThat(event.provenance()).hasValue("test-data-center");
        org.assertj.core.api.Assertions.assertThat(event.traceContext()).hasValue("distributed-trace-001");

        // Validate should pass
        org.assertj.core.api.Assertions.assertThatNoException()
            .isThrownBy(event::validate);
    }

    @Test
    @DisplayName("P3-06: Deprecated factory method still works")
    void deprecatedFactoryMethodStillWorks() {
        // Using deprecated factory for backward compatibility
        @SuppressWarnings("deprecation")
        DataCloudClient.Event event = DataCloudClient.Event.of("legacy.event", Map.of("data", "value"));

        org.assertj.core.api.Assertions.assertThat(event.type()).isEqualTo("legacy.event");
        org.assertj.core.api.Assertions.assertThat(event.payload().get("data")).isEqualTo("value");

        // Validation will fail because source is not set (expected for legacy events)
        org.assertj.core.api.Assertions.assertThatThrownBy(event::validate)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("event.source is required");
    }
}
