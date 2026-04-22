/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.event.validation;

import com.ghatana.datacloud.StorageTier;
import com.ghatana.datacloud.event.model.EventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for enhanced event validation features.
 *
 * <p>Validates:
 * <ul>
 *   <li>JSON Schema validation for headers and payloads</li>
 *   <li>Schema compatibility checking</li>
 *   <li>Custom validator registration and invocation</li>
 *   <li>Lifecycle status enforcement</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Enhanced event validation feature tests
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("EventValidator Enhanced Features")
class EventValidatorTest {

    private EventValidator validator;

    @BeforeEach
    void setUp() {
        validator = EventValidator.create();
    }

    // =========================================================================
    // JSON Schema Validation
    // =========================================================================

    @Nested
    @DisplayName("JSON Schema validation")
    class JsonSchemaValidation {

        @Test
        @DisplayName("should validate valid header schema")
        void shouldValidateValidHeaderSchema() {
            EventType eventType = createEventType(
                Map.of("source", Map.of("type", "string", "required", true)),
                Map.of("orderId", Map.of("type", "string", "required", true))
            );

            validator.registerEventType(eventType);

            Map<String, Object> header = Map.of("source", "order-service");
            Map<String, Object> payload = Map.of("orderId", "order-123");

            EventValidator.EventValidationResult result = validator.validate(eventType, header, payload);

            assertThat(result.valid()).isTrue();
        }

        @Test
        @DisplayName("should reject header missing required field")
        void shouldRejectHeaderMissingRequiredField() {
            EventType eventType = createEventType(
                Map.of("source", Map.of("type", "string", "required", true)),
                Map.of("orderId", Map.of("type", "string", "required", true))
            );

            validator.registerEventType(eventType);

            Map<String, Object> header = Map.of(); // Missing required "source"
            Map<String, Object> payload = Map.of("orderId", "order-123");

            EventValidator.EventValidationResult result = validator.validate(eventType, header, payload);

            assertThat(result.valid()).isFalse();
            assertThat(result.violations()).anyMatch(v -> v.contains("Header validation error"));
        }

        @Test
        @DisplayName("should validate valid payload schema")
        void shouldValidateValidPayloadSchema() {
            EventType eventType = createEventType(
                Map.of("source", Map.of("type", "string")),
                Map.of("orderId", Map.of("type", "string", "required", true))
            );

            validator.registerEventType(eventType);

            Map<String, Object> header = Map.of("source", "order-service");
            Map<String, Object> payload = Map.of("orderId", "order-123");

            EventValidator.EventValidationResult result = validator.validate(eventType, header, payload);

            assertThat(result.valid()).isTrue();
        }

        @Test
        @DisplayName("should reject payload with wrong type")
        void shouldRejectPayloadWithWrongType() {
            EventType eventType = createEventType(
                Map.of("source", Map.of("type", "string")),
                Map.of("orderId", Map.of("type", "string", "required", true))
            );

            validator.registerEventType(eventType);

            Map<String, Object> header = Map.of("source", "order-service");
            Map<String, Object> payload = Map.of("orderId", 123); // Wrong type

            EventValidator.EventValidationResult result = validator.validate(eventType, header, payload);

            assertThat(result.valid()).isFalse();
            assertThat(result.violations()).anyMatch(v -> v.contains("Payload validation error"));
        }

        @Test
        @DisplayName("should handle empty schemas gracefully")
        void shouldHandleEmptySchemasGracefully() {
            EventType eventType = createEventType(Map.of(), Map.of());

            validator.registerEventType(eventType);

            Map<String, Object> header = Map.of("source", "order-service");
            Map<String, Object> payload = Map.of("orderId", "order-123");

            EventValidator.EventValidationResult result = validator.validate(eventType, header, payload);

            // Should pass with no schema validation
            assertThat(result.valid()).isTrue();
        }
    }

    // =========================================================================
    // Lifecycle Status Enforcement
    // =========================================================================

    @Nested
    @DisplayName("Lifecycle status enforcement")
    class LifecycleStatusEnforcement {

        @Test
        @DisplayName("should accept events from ACTIVE event type")
        void shouldAcceptEventsFromActiveEventType() {
            EventType eventType = createEventTypeWithStatus(EventType.LifecycleStatus.ACTIVE);

            validator.registerEventType(eventType);

            Map<String, Object> header = Map.of("source", "order-service");
            Map<String, Object> payload = Map.of("orderId", "order-123");

            EventValidator.EventValidationResult result = validator.validate(eventType, header, payload);

            assertThat(result.valid()).isTrue();
        }

        @Test
        @DisplayName("should accept events from DEPRECATED event type")
        void shouldAcceptEventsFromDeprecatedEventType() {
            EventType eventType = createEventTypeWithStatus(EventType.LifecycleStatus.DEPRECATED);

            validator.registerEventType(eventType);

            Map<String, Object> header = Map.of("source", "order-service");
            Map<String, Object> payload = Map.of("orderId", "order-123");

            EventValidator.EventValidationResult result = validator.validate(eventType, header, payload);

            assertThat(result.valid()).isTrue();
        }

        @Test
        @DisplayName("should reject events from DRAFT event type")
        void shouldRejectEventsFromDraftEventType() {
            EventType eventType = createEventTypeWithStatus(EventType.LifecycleStatus.DRAFT);

            validator.registerEventType(eventType);

            Map<String, Object> header = Map.of("source", "order-service");
            Map<String, Object> payload = Map.of("orderId", "order-123");

            EventValidator.EventValidationResult result = validator.validate(eventType, header, payload);

            assertThat(result.valid()).isFalse();
            assertThat(result.violations()).anyMatch(v -> v.contains("DRAFT") && v.contains("cannot accept events"));
        }

        @Test
        @DisplayName("should reject events from RETIRED event type")
        void shouldRejectEventsFromRetiredEventType() {
            EventType eventType = createEventTypeWithStatus(EventType.LifecycleStatus.RETIRED);

            validator.registerEventType(eventType);

            Map<String, Object> header = Map.of("source", "order-service");
            Map<String, Object> payload = Map.of("orderId", "order-123");

            EventValidator.EventValidationResult result = validator.validate(eventType, header, payload);

            assertThat(result.valid()).isFalse();
            assertThat(result.violations()).anyMatch(v -> v.contains("RETIRED") && v.contains("cannot accept events"));
        }
    }

    // =========================================================================
    // Compatibility Checking
    // =========================================================================

    @Nested
    @DisplayName("Compatibility checking")
    class CompatibilityChecking {

        @Test
        @DisplayName("should pass backward compatibility when no required fields removed")
        void shouldPassBackwardCompatibilityWhenNoRequiredFieldsRemoved() {
            EventType oldType = createEventType(
                Map.of(),
                Map.of("orderId", Map.of("type", "string", "required", true))
            );

            EventType newType = EventType.builder()
                .tenantId("tenant-123")
                .namespace("commerce")
                .name("order.created")
                .label("Order Created")
                .schemaVersion("1.0.0")
                .headerSchema(Map.of())
                .payloadSchema(Map.of("orderId", Map.of("type", "string", "required", true), "amount", Map.of("type", "number")))
                .lifecycleStatus(EventType.LifecycleStatus.ACTIVE)
                .compatibilityPolicy(EventType.CompatibilityPolicy.BACKWARD)
                .defaultStorageTier(StorageTier.WARM)
                .build();

            EventValidator.CompatibilityResult result = validator.checkCompatibility(oldType, newType);

            assertThat(result.compatible()).isTrue();
        }

        @Test
        @DisplayName("should fail backward compatibility when required field removed")
        void shouldFailBackwardCompatibilityWhenRequiredFieldRemoved() {
            EventType oldType = createEventType(
                Map.of(),
                Map.of("orderId", Map.of("type", "string", "required", true), "amount", Map.of("type", "number"))
            );

            EventType newType = EventType.builder()
                .tenantId("tenant-123")
                .namespace("commerce")
                .name("order.created")
                .label("Order Created")
                .schemaVersion("1.0.0")
                .headerSchema(Map.of())
                .payloadSchema(Map.of("orderId", Map.of("type", "string", "required", true))) // "amount" removed
                .lifecycleStatus(EventType.LifecycleStatus.ACTIVE)
                .compatibilityPolicy(EventType.CompatibilityPolicy.BACKWARD)
                .defaultStorageTier(StorageTier.WARM)
                .build();

            EventValidator.CompatibilityResult result = validator.checkCompatibility(oldType, newType);

            assertThat(result.compatible()).isFalse();
            assertThat(result.violations()).anyMatch(v -> v.contains("required field") && v.contains("removed"));
        }

        @Test
        @DisplayName("should pass forward compatibility when no required fields added")
        void shouldPassForwardCompatibilityWhenNoRequiredFieldsAdded() {
            EventType oldType = createEventType(
                Map.of(),
                Map.of("orderId", Map.of("type", "string", "required", true))
            );

            EventType newType = EventType.builder()
                .tenantId("tenant-123")
                .namespace("commerce")
                .name("order.created")
                .label("Order Created")
                .schemaVersion("1.0.0")
                .headerSchema(Map.of())
                .payloadSchema(Map.of("orderId", Map.of("type", "string", "required", true), "amount", Map.of("type", "number", "required", false)))
                .lifecycleStatus(EventType.LifecycleStatus.ACTIVE)
                .compatibilityPolicy(EventType.CompatibilityPolicy.FORWARD)
                .defaultStorageTier(StorageTier.WARM)
                .build();

            EventValidator.CompatibilityResult result = validator.checkCompatibility(oldType, newType);

            assertThat(result.compatible()).isTrue();
        }

        @Test
        @DisplayName("should fail forward compatibility when required field added")
        void shouldFailForwardCompatibilityWhenRequiredFieldAdded() {
            EventType oldType = createEventType(
                Map.of(),
                Map.of("orderId", Map.of("type", "string", "required", true))
            );

            EventType newType = EventType.builder()
                .tenantId("tenant-123")
                .namespace("commerce")
                .name("order.created")
                .label("Order Created")
                .schemaVersion("1.0.0")
                .headerSchema(Map.of())
                .payloadSchema(Map.of("orderId", Map.of("type", "string", "required", true), "amount", Map.of("type", "number", "required", true)))
                .lifecycleStatus(EventType.LifecycleStatus.ACTIVE)
                .compatibilityPolicy(EventType.CompatibilityPolicy.FORWARD)
                .defaultStorageTier(StorageTier.WARM)
                .build();

            EventValidator.CompatibilityResult result = validator.checkCompatibility(oldType, newType);

            assertThat(result.compatible()).isFalse();
            assertThat(result.violations()).anyMatch(v -> v.contains("New required field") && v.contains("added"));
        }

        @Test
        @DisplayName("should pass NONE compatibility policy")
        void shouldPassNoneCompatibilityPolicy() {
            EventType oldType = createEventType(Map.of(), Map.of("orderId", Map.of("type", "string")));
            EventType newType = EventType.builder()
                .tenantId("tenant-123")
                .namespace("commerce")
                .name("order.created")
                .label("Order Created")
                .schemaVersion("1.0.0")
                .headerSchema(Map.of())
                .payloadSchema(Map.of()) // Completely different
                .lifecycleStatus(EventType.LifecycleStatus.ACTIVE)
                .compatibilityPolicy(EventType.CompatibilityPolicy.NONE)
                .defaultStorageTier(StorageTier.WARM)
                .build();

            EventValidator.CompatibilityResult result = validator.checkCompatibility(oldType, newType);

            assertThat(result.compatible()).isTrue();
        }
    }

    // =========================================================================
    // Custom Validators
    // =========================================================================

    @Nested
    @DisplayName("Custom validators")
    class CustomValidators {

        @Test
        @DisplayName("should invoke custom validator after schema validation")
        void shouldInvokeCustomValidatorAfterSchemaValidation() {
            EventType eventType = createEventType(Map.of(), Map.of("amount", Map.of("type", "number")));

            validator.registerEventType(eventType);
            validator.registerCustomValidator(eventType, (et, header, payload) -> {
                if (payload.get("amount") instanceof Number amount && amount.doubleValue() < 0) {
                    return Optional.of("Amount cannot be negative");
                }
                return Optional.empty();
            });

            Map<String, Object> header = Map.of("source", "order-service");
            Map<String, Object> payload = Map.of("amount", -10.0);

            EventValidator.EventValidationResult result = validator.validate(eventType, header, payload);

            assertThat(result.valid()).isFalse();
            assertThat(result.violations()).contains("Amount cannot be negative");
        }

        @Test
        @DisplayName("should allow custom validator to pass")
        void shouldAllowCustomValidatorToPass() {
            EventType eventType = createEventType(Map.of(), Map.of("amount", Map.of("type", "number")));

            validator.registerEventType(eventType);
            validator.registerCustomValidator(eventType, (et, header, payload) -> Optional.empty());

            Map<String, Object> header = Map.of("source", "order-service");
            Map<String, Object> payload = Map.of("amount", 100.0);

            EventValidator.EventValidationResult result = validator.validate(eventType, header, payload);

            assertThat(result.valid()).isTrue();
        }

        @Test
        @DisplayName("should not invoke custom validators if schema validation fails")
        void shouldNotInvokeCustomValidatorsIfSchemaValidationFails() {
            EventType eventType = createEventType(
                Map.of("source", Map.of("type", "string", "required", true)),
                Map.of("amount", Map.of("type", "number"))
            );

            validator.registerEventType(eventType);
            validator.registerCustomValidator(eventType, (et, header, payload) -> Optional.of("Custom violation"));

            Map<String, Object> header = Map.of(); // Missing required "source"
            Map<String, Object> payload = Map.of("amount", 100.0);

            EventValidator.EventValidationResult result = validator.validate(eventType, header, payload);

            assertThat(result.valid()).isFalse();
            // Only schema validation violation, not custom validator
            assertThat(result.violations()).hasSize(1);
            assertThat(result.violations().get(0)).contains("Header validation error");
        }
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private EventType createEventType(Map<String, Object> headerSchema, Map<String, Object> payloadSchema) {
        return EventType.builder()
            .tenantId("tenant-123")
            .namespace("commerce")
            .name("order.created")
            .label("Order Created")
            .schemaVersion("1.0.0")
            .headerSchema(headerSchema)
            .payloadSchema(payloadSchema)
            .lifecycleStatus(EventType.LifecycleStatus.ACTIVE)
            .compatibilityPolicy(EventType.CompatibilityPolicy.BACKWARD)
            .defaultStorageTier(StorageTier.WARM)
            .build();
    }

    private EventType createEventTypeWithStatus(EventType.LifecycleStatus status) {
        return EventType.builder()
            .tenantId("tenant-123")
            .namespace("commerce")
            .name("order.created")
            .label("Order Created")
            .schemaVersion("1.0.0")
            .headerSchema(Map.of())
            .payloadSchema(Map.of())
            .lifecycleStatus(status)
            .compatibilityPolicy(EventType.CompatibilityPolicy.BACKWARD)
            .defaultStorageTier(StorageTier.WARM)
            .build();
    }
}
