/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Domain — DomainEventRegistry Tests
 */
package com.ghatana.products.yappc.domain.events;

import com.ghatana.yappc.api.events.DomainEvent;
import com.ghatana.yappc.api.events.DomainEventMetaInfo;
import com.ghatana.yappc.api.events.DomainEventRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link DomainEventRegistry}: registration, validation, and metadata lookup.
 *
 * @doc.type class
 * @doc.purpose Verifies DomainEventRegistry correctly manages event type registration and schema validation
 * @doc.layer domain
 * @doc.pattern Test
 */
@DisplayName("DomainEventRegistry")
class DomainEventRegistryTest {

    /** Fresh registry instance per test to avoid cross-test pollution. */
    private DomainEventRegistry registry;

    // ─── Stub event classes used in tests ─────────────────────────────────────

    /** Minimal concrete event — uses default schemaVersion=1 inferred from class name. */
    static final class OrderPlacedEvent extends DomainEvent {
        OrderPlacedEvent() {
            super("OrderPlaced", "agg-1", "Order", "tenant-1", "user-1");
        }
        @Override public Map<String, Object> toPayload() { return Map.of("orderId", "o1"); }
    }

    /** Event annotated with explicit metadata. */
    @DomainEventMetaInfo(eventType = "UserRegistered", schemaVersion = 2)
    static final class UserSignedUpEvent extends DomainEvent {
        UserSignedUpEvent() {
            super("UserRegistered", "agg-2", "User", "tenant-1", "user-1");
        }
        @Override public Map<String, Object> toPayload() { return Map.of("email", "u@example.com"); }
    }

    /** Malformed event subclass whose declared schema version is 0 (simulates a bug). */
    @DomainEventMetaInfo(eventType = "BrokenEvent", schemaVersion = 0)
    static final class BrokenEvent extends DomainEvent {
        BrokenEvent() {
            super("BrokenEvent", "agg-3", "Thing", "tenant-1", "user-1");
        }
        @Override public Map<String, Object> toPayload() { return Map.of(); }
    }

    @BeforeEach
    void setUp() {
        // Use a fresh instance per test to avoid cross-test pollution.
        // DomainEventRegistry.INSTANCE is the production singleton; tests use a local one.
        registry = new DomainEventRegistry() {};
    }

    // ─── Nested: Registration ──────────────────────────────────────────────────

    @Nested
    @DisplayName("register()")
    class Register {

        @Test
        @DisplayName("infers event type by stripping 'Event' suffix when no annotation")
        void shouldInferEventTypeFromClassName() {
            registry.register(OrderPlacedEvent.class);

            assertThat(registry.registeredTypes()).containsExactly("OrderPlaced");
        }

        @Test
        @DisplayName("uses @DomainEventMetaInfo annotation eventType when present")
        void shouldUseAnnotationEventType() {
            registry.register(UserSignedUpEvent.class);

            assertThat(registry.registeredTypes()).containsExactly("UserRegistered");
        }

        @Test
        @DisplayName("uses annotation schemaVersion=2 from @DomainEventMetaInfo")
        void shouldUseAnnotationSchemaVersion() {
            registry.register(UserSignedUpEvent.class);

            DomainEventRegistry.EventTypeMetadata meta = registry.getMetadata("UserRegistered");
            assertThat(meta).isNotNull();
            assertThat(meta.schemaVersion()).isEqualTo(2);
        }

        @Test
        @DisplayName("defaults schemaVersion to 1 when no annotation present")
        void shouldDefaultSchemaVersionToOne() {
            registry.register(OrderPlacedEvent.class);

            DomainEventRegistry.EventTypeMetadata meta = registry.getMetadata("OrderPlaced");
            assertThat(meta).isNotNull();
            assertThat(meta.schemaVersion()).isEqualTo(1);
        }

        @Test
        @DisplayName("registers multiple event types independently")
        void shouldRegisterMultipleTypes() {
            registry.register(OrderPlacedEvent.class);
            registry.register(UserSignedUpEvent.class);

            assertThat(registry.registeredTypes()).containsExactlyInAnyOrder("OrderPlaced", "UserRegistered");
            assertThat(registry.size()).isEqualTo(2);
        }

        @Test
        @DisplayName("stores fully qualified class name in metadata")
        void shouldStoreFullyQualifiedClassName() {
            registry.register(OrderPlacedEvent.class);

            DomainEventRegistry.EventTypeMetadata meta = registry.getMetadata("OrderPlaced");
            assertThat(meta).isNotNull();
            assertThat(meta.className()).isEqualTo(OrderPlacedEvent.class.getName());
        }
    }

    // ─── Nested: Validation ────────────────────────────────────────────────────

    @Nested
    @DisplayName("validate()")
    class Validate {

        @Test
        @DisplayName("passes when all registered events have schemaVersion >= 1")
        void shouldPassForValidEvents() {
            registry.register(OrderPlacedEvent.class);
            registry.register(UserSignedUpEvent.class);

            // Must not throw
            assertThatCode(() -> registry.validate()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("throws InvalidEventRegistrationException when event has schemaVersion=0")
        void shouldFailForZeroSchemaVersion() {
            registry.register(BrokenEvent.class);

            assertThatThrownBy(() -> registry.validate())
                    .isInstanceOf(DomainEventRegistry.InvalidEventRegistrationException.class)
                    .hasMessageContaining("BrokenEvent")
                    .hasMessageContaining("schemaVersion=0");
        }

        @Test
        @DisplayName("lists ALL violations in a single exception message")
        void shouldListAllViolations() {
            registry.register(BrokenEvent.class);

            // Two violations registered
            @DomainEventMetaInfo(eventType = "AnotherBroken", schemaVersion = 0)
            class AnotherBrokenEvent extends DomainEvent {
                AnotherBrokenEvent() { super("AnotherBroken", "a", "A", "t", "u"); }
                @Override public Map<String, Object> toPayload() { return Map.of(); }
            }
            registry.register(AnotherBrokenEvent.class);

            assertThatThrownBy(() -> registry.validate())
                    .isInstanceOf(DomainEventRegistry.InvalidEventRegistrationException.class)
                    .hasMessageContaining("BrokenEvent")
                    .hasMessageContaining("AnotherBroken");
        }

        @Test
        @DisplayName("logs warning but does not throw when no events registered")
        void shouldWarnOnEmptyRegistry() {
            assertThatCode(() -> registry.validate()).doesNotThrowAnyException();
        }
    }

    // ─── Nested: Queries ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Queries")
    class Queries {

        @Test
        @DisplayName("isRegistered returns true for registered type")
        void shouldReturnTrueForRegisteredType() {
            registry.register(OrderPlacedEvent.class);

            assertThat(registry.isRegistered("OrderPlaced")).isTrue();
        }

        @Test
        @DisplayName("isRegistered returns false for unknown type")
        void shouldReturnFalseForUnknownType() {
            assertThat(registry.isRegistered("Unknown")).isFalse();
        }

        @Test
        @DisplayName("getMetadata returns null for unregistered type")
        void shouldReturnNullForUnregisteredType() {
            assertThat(registry.getMetadata("Ghost")).isNull();
        }

        @Test
        @DisplayName("registeredTypes returns unmodifiable set")
        void shouldReturnUnmodifiableSet() {
            registry.register(OrderPlacedEvent.class);

            assertThatThrownBy(() -> registry.registeredTypes().add("Hacked"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("size() reflects the number of registered types")
        void shouldTrackSize() {
            assertThat(registry.size()).isEqualTo(0);

            registry.register(OrderPlacedEvent.class);
            assertThat(registry.size()).isEqualTo(1);

            registry.register(UserSignedUpEvent.class);
            assertThat(registry.size()).isEqualTo(2);
        }
    }
}
