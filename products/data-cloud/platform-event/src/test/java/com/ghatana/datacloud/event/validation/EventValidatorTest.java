/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
@DisplayName("EventValidator Enhanced Features [GH-90000]")
class EventValidatorTest {

    private EventValidator validator;

    @BeforeEach
    void setUp() { // GH-90000
        validator = EventValidator.create(); // GH-90000
    }

    // =========================================================================
    // JSON Schema Validation
    // =========================================================================

    @Nested
    @DisplayName("JSON Schema validation [GH-90000]")
    class JsonSchemaValidation {

        @Test
        @DisplayName("should validate valid header schema [GH-90000]")
        void shouldValidateValidHeaderSchema() { // GH-90000
            EventType eventType = createEventType( // GH-90000
                Map.of("source", Map.of("type", "string", "required", true)), // GH-90000
                Map.of("orderId", Map.of("type", "string", "required", true)) // GH-90000
            );

            validator.registerEventType(eventType); // GH-90000

            Map<String, Object> header = Map.of("source", "order-service"); // GH-90000
            Map<String, Object> payload = Map.of("orderId", "order-123"); // GH-90000

            EventValidator.EventValidationResult result = validator.validate(eventType, header, payload); // GH-90000

            assertThat(result.valid()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should reject header missing required field [GH-90000]")
        void shouldRejectHeaderMissingRequiredField() { // GH-90000
            EventType eventType = createEventType( // GH-90000
                Map.of("source", Map.of("type", "string", "required", true)), // GH-90000
                Map.of("orderId", Map.of("type", "string", "required", true)) // GH-90000
            );

            validator.registerEventType(eventType); // GH-90000

            Map<String, Object> header = Map.of(); // Missing required "source" // GH-90000
            Map<String, Object> payload = Map.of("orderId", "order-123"); // GH-90000

            EventValidator.EventValidationResult result = validator.validate(eventType, header, payload); // GH-90000

            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.violations()).anyMatch(v -> v.contains("Header validation error [GH-90000]"));
        }

        @Test
        @DisplayName("should validate valid payload schema [GH-90000]")
        void shouldValidateValidPayloadSchema() { // GH-90000
            EventType eventType = createEventType( // GH-90000
                Map.of("source", Map.of("type", "string")), // GH-90000
                Map.of("orderId", Map.of("type", "string", "required", true)) // GH-90000
            );

            validator.registerEventType(eventType); // GH-90000

            Map<String, Object> header = Map.of("source", "order-service"); // GH-90000
            Map<String, Object> payload = Map.of("orderId", "order-123"); // GH-90000

            EventValidator.EventValidationResult result = validator.validate(eventType, header, payload); // GH-90000

            assertThat(result.valid()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should reject payload with wrong type [GH-90000]")
        void shouldRejectPayloadWithWrongType() { // GH-90000
            EventType eventType = createEventType( // GH-90000
                Map.of("source", Map.of("type", "string")), // GH-90000
                Map.of("orderId", Map.of("type", "string", "required", true)) // GH-90000
            );

            validator.registerEventType(eventType); // GH-90000

            Map<String, Object> header = Map.of("source", "order-service"); // GH-90000
            Map<String, Object> payload = Map.of("orderId", 123); // Wrong type // GH-90000

            EventValidator.EventValidationResult result = validator.validate(eventType, header, payload); // GH-90000

            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.violations()).anyMatch(v -> v.contains("Payload validation error [GH-90000]"));
        }

        @Test
        @DisplayName("should handle empty schemas gracefully [GH-90000]")
        void shouldHandleEmptySchemasGracefully() { // GH-90000
            EventType eventType = createEventType(Map.of(), Map.of()); // GH-90000

            validator.registerEventType(eventType); // GH-90000

            Map<String, Object> header = Map.of("source", "order-service"); // GH-90000
            Map<String, Object> payload = Map.of("orderId", "order-123"); // GH-90000

            EventValidator.EventValidationResult result = validator.validate(eventType, header, payload); // GH-90000

            // Should pass with no schema validation
            assertThat(result.valid()).isTrue(); // GH-90000
        }
    }

    // =========================================================================
    // Lifecycle Status Enforcement
    // =========================================================================

    @Nested
    @DisplayName("Lifecycle status enforcement [GH-90000]")
    class LifecycleStatusEnforcement {

        @Test
        @DisplayName("should accept events from ACTIVE event type [GH-90000]")
        void shouldAcceptEventsFromActiveEventType() { // GH-90000
            EventType eventType = createEventTypeWithStatus(EventType.LifecycleStatus.ACTIVE); // GH-90000

            validator.registerEventType(eventType); // GH-90000

            Map<String, Object> header = Map.of("source", "order-service"); // GH-90000
            Map<String, Object> payload = Map.of("orderId", "order-123"); // GH-90000

            EventValidator.EventValidationResult result = validator.validate(eventType, header, payload); // GH-90000

            assertThat(result.valid()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should accept events from DEPRECATED event type [GH-90000]")
        void shouldAcceptEventsFromDeprecatedEventType() { // GH-90000
            EventType eventType = createEventTypeWithStatus(EventType.LifecycleStatus.DEPRECATED); // GH-90000

            validator.registerEventType(eventType); // GH-90000

            Map<String, Object> header = Map.of("source", "order-service"); // GH-90000
            Map<String, Object> payload = Map.of("orderId", "order-123"); // GH-90000

            EventValidator.EventValidationResult result = validator.validate(eventType, header, payload); // GH-90000

            assertThat(result.valid()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should reject events from DRAFT event type [GH-90000]")
        void shouldRejectEventsFromDraftEventType() { // GH-90000
            EventType eventType = createEventTypeWithStatus(EventType.LifecycleStatus.DRAFT); // GH-90000

            validator.registerEventType(eventType); // GH-90000

            Map<String, Object> header = Map.of("source", "order-service"); // GH-90000
            Map<String, Object> payload = Map.of("orderId", "order-123"); // GH-90000

            EventValidator.EventValidationResult result = validator.validate(eventType, header, payload); // GH-90000

            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.violations()).anyMatch(v -> v.contains("DRAFT [GH-90000]") && v.contains("cannot accept events [GH-90000]"));
        }

        @Test
        @DisplayName("should reject events from RETIRED event type [GH-90000]")
        void shouldRejectEventsFromRetiredEventType() { // GH-90000
            EventType eventType = createEventTypeWithStatus(EventType.LifecycleStatus.RETIRED); // GH-90000

            validator.registerEventType(eventType); // GH-90000

            Map<String, Object> header = Map.of("source", "order-service"); // GH-90000
            Map<String, Object> payload = Map.of("orderId", "order-123"); // GH-90000

            EventValidator.EventValidationResult result = validator.validate(eventType, header, payload); // GH-90000

            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.violations()).anyMatch(v -> v.contains("RETIRED [GH-90000]") && v.contains("cannot accept events [GH-90000]"));
        }
    }

    // =========================================================================
    // Compatibility Checking
    // =========================================================================

    @Nested
    @DisplayName("Compatibility checking [GH-90000]")
    class CompatibilityChecking {

        @Test
        @DisplayName("should pass backward compatibility when no required fields removed [GH-90000]")
        void shouldPassBackwardCompatibilityWhenNoRequiredFieldsRemoved() { // GH-90000
            EventType oldType = createEventType( // GH-90000
                Map.of(), // GH-90000
                Map.of("orderId", Map.of("type", "string", "required", true)) // GH-90000
            );

            EventType newType = EventType.builder() // GH-90000
                .tenantId("tenant-123 [GH-90000]")
                .namespace("commerce [GH-90000]")
                .name("order.created [GH-90000]")
                .label("Order Created [GH-90000]")
                .schemaVersion("1.0.0 [GH-90000]")
                .headerSchema(Map.of()) // GH-90000
                .payloadSchema(Map.of("orderId", Map.of("type", "string", "required", true), "amount", Map.of("type", "number"))) // GH-90000
                .lifecycleStatus(EventType.LifecycleStatus.ACTIVE) // GH-90000
                .compatibilityPolicy(EventType.CompatibilityPolicy.BACKWARD) // GH-90000
                .defaultStorageTier(StorageTier.WARM) // GH-90000
                .build(); // GH-90000

            EventValidator.CompatibilityResult result = validator.checkCompatibility(oldType, newType); // GH-90000

            assertThat(result.compatible()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should fail backward compatibility when required field removed [GH-90000]")
        void shouldFailBackwardCompatibilityWhenRequiredFieldRemoved() { // GH-90000
            EventType oldType = createEventType( // GH-90000
                Map.of(), // GH-90000
                Map.of("orderId", Map.of("type", "string", "required", true), "amount", Map.of("type", "number")) // GH-90000
            );

            EventType newType = EventType.builder() // GH-90000
                .tenantId("tenant-123 [GH-90000]")
                .namespace("commerce [GH-90000]")
                .name("order.created [GH-90000]")
                .label("Order Created [GH-90000]")
                .schemaVersion("1.0.0 [GH-90000]")
                .headerSchema(Map.of()) // GH-90000
                .payloadSchema(Map.of("orderId", Map.of("type", "string", "required", true))) // "amount" removed // GH-90000
                .lifecycleStatus(EventType.LifecycleStatus.ACTIVE) // GH-90000
                .compatibilityPolicy(EventType.CompatibilityPolicy.BACKWARD) // GH-90000
                .defaultStorageTier(StorageTier.WARM) // GH-90000
                .build(); // GH-90000

            EventValidator.CompatibilityResult result = validator.checkCompatibility(oldType, newType); // GH-90000

            assertThat(result.compatible()).isFalse(); // GH-90000
            assertThat(result.violations()).anyMatch(v -> v.contains("required field [GH-90000]") && v.contains("removed [GH-90000]"));
        }

        @Test
        @DisplayName("should pass forward compatibility when no required fields added [GH-90000]")
        void shouldPassForwardCompatibilityWhenNoRequiredFieldsAdded() { // GH-90000
            EventType oldType = createEventType( // GH-90000
                Map.of(), // GH-90000
                Map.of("orderId", Map.of("type", "string", "required", true)) // GH-90000
            );

            EventType newType = EventType.builder() // GH-90000
                .tenantId("tenant-123 [GH-90000]")
                .namespace("commerce [GH-90000]")
                .name("order.created [GH-90000]")
                .label("Order Created [GH-90000]")
                .schemaVersion("1.0.0 [GH-90000]")
                .headerSchema(Map.of()) // GH-90000
                .payloadSchema(Map.of("orderId", Map.of("type", "string", "required", true), "amount", Map.of("type", "number", "required", false))) // GH-90000
                .lifecycleStatus(EventType.LifecycleStatus.ACTIVE) // GH-90000
                .compatibilityPolicy(EventType.CompatibilityPolicy.FORWARD) // GH-90000
                .defaultStorageTier(StorageTier.WARM) // GH-90000
                .build(); // GH-90000

            EventValidator.CompatibilityResult result = validator.checkCompatibility(oldType, newType); // GH-90000

            assertThat(result.compatible()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should fail forward compatibility when required field added [GH-90000]")
        void shouldFailForwardCompatibilityWhenRequiredFieldAdded() { // GH-90000
            EventType oldType = createEventType( // GH-90000
                Map.of(), // GH-90000
                Map.of("orderId", Map.of("type", "string", "required", true)) // GH-90000
            );

            EventType newType = EventType.builder() // GH-90000
                .tenantId("tenant-123 [GH-90000]")
                .namespace("commerce [GH-90000]")
                .name("order.created [GH-90000]")
                .label("Order Created [GH-90000]")
                .schemaVersion("1.0.0 [GH-90000]")
                .headerSchema(Map.of()) // GH-90000
                .payloadSchema(Map.of("orderId", Map.of("type", "string", "required", true), "amount", Map.of("type", "number", "required", true))) // GH-90000
                .lifecycleStatus(EventType.LifecycleStatus.ACTIVE) // GH-90000
                .compatibilityPolicy(EventType.CompatibilityPolicy.FORWARD) // GH-90000
                .defaultStorageTier(StorageTier.WARM) // GH-90000
                .build(); // GH-90000

            EventValidator.CompatibilityResult result = validator.checkCompatibility(oldType, newType); // GH-90000

            assertThat(result.compatible()).isFalse(); // GH-90000
            assertThat(result.violations()).anyMatch(v -> v.contains("New required field [GH-90000]") && v.contains("added [GH-90000]"));
        }

        @Test
        @DisplayName("should pass NONE compatibility policy [GH-90000]")
        void shouldPassNoneCompatibilityPolicy() { // GH-90000
            EventType oldType = createEventType(Map.of(), Map.of("orderId", Map.of("type", "string"))); // GH-90000
            EventType newType = EventType.builder() // GH-90000
                .tenantId("tenant-123 [GH-90000]")
                .namespace("commerce [GH-90000]")
                .name("order.created [GH-90000]")
                .label("Order Created [GH-90000]")
                .schemaVersion("1.0.0 [GH-90000]")
                .headerSchema(Map.of()) // GH-90000
                .payloadSchema(Map.of()) // Completely different // GH-90000
                .lifecycleStatus(EventType.LifecycleStatus.ACTIVE) // GH-90000
                .compatibilityPolicy(EventType.CompatibilityPolicy.NONE) // GH-90000
                .defaultStorageTier(StorageTier.WARM) // GH-90000
                .build(); // GH-90000

            EventValidator.CompatibilityResult result = validator.checkCompatibility(oldType, newType); // GH-90000

            assertThat(result.compatible()).isTrue(); // GH-90000
        }
    }

    // =========================================================================
    // Custom Validators
    // =========================================================================

    @Nested
    @DisplayName("Custom validators [GH-90000]")
    class CustomValidators {

        @Test
        @DisplayName("should invoke custom validator after schema validation [GH-90000]")
        void shouldInvokeCustomValidatorAfterSchemaValidation() { // GH-90000
            EventType eventType = createEventType(Map.of(), Map.of("amount", Map.of("type", "number"))); // GH-90000

            validator.registerEventType(eventType); // GH-90000
            validator.registerCustomValidator(eventType, (et, header, payload) -> { // GH-90000
                if (payload.get("amount [GH-90000]") instanceof Number amount && amount.doubleValue() < 0) {
                    return Optional.of("Amount cannot be negative [GH-90000]");
                }
                return Optional.empty(); // GH-90000
            });

            Map<String, Object> header = Map.of("source", "order-service"); // GH-90000
            Map<String, Object> payload = Map.of("amount", -10.0); // GH-90000

            EventValidator.EventValidationResult result = validator.validate(eventType, header, payload); // GH-90000

            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.violations()).contains("Amount cannot be negative [GH-90000]");
        }

        @Test
        @DisplayName("should allow custom validator to pass [GH-90000]")
        void shouldAllowCustomValidatorToPass() { // GH-90000
            EventType eventType = createEventType(Map.of(), Map.of("amount", Map.of("type", "number"))); // GH-90000

            validator.registerEventType(eventType); // GH-90000
            validator.registerCustomValidator(eventType, (et, header, payload) -> Optional.empty()); // GH-90000

            Map<String, Object> header = Map.of("source", "order-service"); // GH-90000
            Map<String, Object> payload = Map.of("amount", 100.0); // GH-90000

            EventValidator.EventValidationResult result = validator.validate(eventType, header, payload); // GH-90000

            assertThat(result.valid()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should not invoke custom validators if schema validation fails [GH-90000]")
        void shouldNotInvokeCustomValidatorsIfSchemaValidationFails() { // GH-90000
            EventType eventType = createEventType( // GH-90000
                Map.of("source", Map.of("type", "string", "required", true)), // GH-90000
                Map.of("amount", Map.of("type", "number")) // GH-90000
            );

            validator.registerEventType(eventType); // GH-90000
            validator.registerCustomValidator(eventType, (et, header, payload) -> Optional.of("Custom violation [GH-90000]"));

            Map<String, Object> header = Map.of(); // Missing required "source" // GH-90000
            Map<String, Object> payload = Map.of("amount", 100.0); // GH-90000

            EventValidator.EventValidationResult result = validator.validate(eventType, header, payload); // GH-90000

            assertThat(result.valid()).isFalse(); // GH-90000
            // Only schema validation violation, not custom validator
            assertThat(result.violations()).hasSize(1); // GH-90000
            assertThat(result.violations().get(0)).contains("Header validation error [GH-90000]");
        }
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private EventType createEventType(Map<String, Object> headerSchema, Map<String, Object> payloadSchema) { // GH-90000
        return EventType.builder() // GH-90000
            .tenantId("tenant-123 [GH-90000]")
            .namespace("commerce [GH-90000]")
            .name("order.created [GH-90000]")
            .label("Order Created [GH-90000]")
            .schemaVersion("1.0.0 [GH-90000]")
            .headerSchema(headerSchema) // GH-90000
            .payloadSchema(payloadSchema) // GH-90000
            .lifecycleStatus(EventType.LifecycleStatus.ACTIVE) // GH-90000
            .compatibilityPolicy(EventType.CompatibilityPolicy.BACKWARD) // GH-90000
            .defaultStorageTier(StorageTier.WARM) // GH-90000
            .build(); // GH-90000
    }

    private EventType createEventTypeWithStatus(EventType.LifecycleStatus status) { // GH-90000
        return EventType.builder() // GH-90000
            .tenantId("tenant-123 [GH-90000]")
            .namespace("commerce [GH-90000]")
            .name("order.created [GH-90000]")
            .label("Order Created [GH-90000]")
            .schemaVersion("1.0.0 [GH-90000]")
            .headerSchema(Map.of()) // GH-90000
            .payloadSchema(Map.of()) // GH-90000
            .lifecycleStatus(status) // GH-90000
            .compatibilityPolicy(EventType.CompatibilityPolicy.BACKWARD) // GH-90000
            .defaultStorageTier(StorageTier.WARM) // GH-90000
            .build(); // GH-90000
    }
}
