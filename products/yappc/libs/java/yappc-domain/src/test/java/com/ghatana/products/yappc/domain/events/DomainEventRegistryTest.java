/*
 * Copyright (c) 2026 Ghatana Technologies // GH-90000
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
@DisplayName("DomainEventRegistry [GH-90000]")
class DomainEventRegistryTest {

    /** Fresh registry instance per test to avoid cross-test pollution. */
    private DomainEventRegistry registry;

    // ─── Stub event classes used in tests ─────────────────────────────────────

    /** Minimal concrete event — uses default schemaVersion=1 inferred from class name. */
    static final class OrderPlacedEvent extends DomainEvent {
        OrderPlacedEvent() { // GH-90000
            super("OrderPlaced", "agg-1", "Order", "tenant-1", "user-1"); // GH-90000
        }
        @Override public Map<String, Object> toPayload() { return Map.of("orderId", "o1"); } // GH-90000
    }

    /** Event annotated with explicit metadata. */
    @DomainEventMetaInfo(eventType = "UserRegistered", schemaVersion = 2) // GH-90000
    static final class UserSignedUpEvent extends DomainEvent {
        UserSignedUpEvent() { // GH-90000
            super("UserRegistered", "agg-2", "User", "tenant-1", "user-1"); // GH-90000
        }
        @Override public Map<String, Object> toPayload() { return Map.of("email", "u@example.com"); } // GH-90000
    }

    /** Malformed event subclass whose declared schema version is 0 (simulates a bug). */ // GH-90000
    @DomainEventMetaInfo(eventType = "BrokenEvent", schemaVersion = 0) // GH-90000
    static final class BrokenEvent extends DomainEvent {
        BrokenEvent() { // GH-90000
            super("BrokenEvent", "agg-3", "Thing", "tenant-1", "user-1"); // GH-90000
        }
        @Override public Map<String, Object> toPayload() { return Map.of(); } // GH-90000
    }

    @BeforeEach
    void setUp() { // GH-90000
        // Use a fresh instance per test to avoid cross-test pollution.
        // DomainEventRegistry.INSTANCE is the production singleton; tests use a local one.
        registry = new DomainEventRegistry() {}; // GH-90000
    }

    // ─── Nested: Registration ──────────────────────────────────────────────────

    @Nested
    @DisplayName("register() [GH-90000]")
    class Register {

        @Test
        @DisplayName("infers event type by stripping 'Event' suffix when no annotation [GH-90000]")
        void shouldInferEventTypeFromClassName() { // GH-90000
            registry.register(OrderPlacedEvent.class); // GH-90000

            assertThat(registry.registeredTypes()).containsExactly("OrderPlaced [GH-90000]");
        }

        @Test
        @DisplayName("uses @DomainEventMetaInfo annotation eventType when present [GH-90000]")
        void shouldUseAnnotationEventType() { // GH-90000
            registry.register(UserSignedUpEvent.class); // GH-90000

            assertThat(registry.registeredTypes()).containsExactly("UserRegistered [GH-90000]");
        }

        @Test
        @DisplayName("uses annotation schemaVersion=2 from @DomainEventMetaInfo [GH-90000]")
        void shouldUseAnnotationSchemaVersion() { // GH-90000
            registry.register(UserSignedUpEvent.class); // GH-90000

            DomainEventRegistry.EventTypeMetadata meta = registry.getMetadata("UserRegistered [GH-90000]");
            assertThat(meta).isNotNull(); // GH-90000
            assertThat(meta.schemaVersion()).isEqualTo(2); // GH-90000
        }

        @Test
        @DisplayName("defaults schemaVersion to 1 when no annotation present [GH-90000]")
        void shouldDefaultSchemaVersionToOne() { // GH-90000
            registry.register(OrderPlacedEvent.class); // GH-90000

            DomainEventRegistry.EventTypeMetadata meta = registry.getMetadata("OrderPlaced [GH-90000]");
            assertThat(meta).isNotNull(); // GH-90000
            assertThat(meta.schemaVersion()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("registers multiple event types independently [GH-90000]")
        void shouldRegisterMultipleTypes() { // GH-90000
            registry.register(OrderPlacedEvent.class); // GH-90000
            registry.register(UserSignedUpEvent.class); // GH-90000

            assertThat(registry.registeredTypes()).containsExactlyInAnyOrder("OrderPlaced", "UserRegistered"); // GH-90000
            assertThat(registry.size()).isEqualTo(2); // GH-90000
        }

        @Test
        @DisplayName("stores fully qualified class name in metadata [GH-90000]")
        void shouldStoreFullyQualifiedClassName() { // GH-90000
            registry.register(OrderPlacedEvent.class); // GH-90000

            DomainEventRegistry.EventTypeMetadata meta = registry.getMetadata("OrderPlaced [GH-90000]");
            assertThat(meta).isNotNull(); // GH-90000
            assertThat(meta.className()).isEqualTo(OrderPlacedEvent.class.getName()); // GH-90000
        }
    }

    // ─── Nested: Validation ────────────────────────────────────────────────────

    @Nested
    @DisplayName("validate() [GH-90000]")
    class Validate {

        @Test
        @DisplayName("passes when all registered events have schemaVersion >= 1 [GH-90000]")
        void shouldPassForValidEvents() { // GH-90000
            registry.register(OrderPlacedEvent.class); // GH-90000
            registry.register(UserSignedUpEvent.class); // GH-90000

            // Must not throw
            assertThatCode(() -> registry.validate()).doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("throws InvalidEventRegistrationException when event has schemaVersion=0 [GH-90000]")
        void shouldFailForZeroSchemaVersion() { // GH-90000
            registry.register(BrokenEvent.class); // GH-90000

            assertThatThrownBy(() -> registry.validate()) // GH-90000
                    .isInstanceOf(DomainEventRegistry.InvalidEventRegistrationException.class) // GH-90000
                    .hasMessageContaining("BrokenEvent [GH-90000]")
                    .hasMessageContaining("schemaVersion=0 [GH-90000]");
        }

        @Test
        @DisplayName("lists ALL violations in a single exception message [GH-90000]")
        void shouldListAllViolations() { // GH-90000
            registry.register(BrokenEvent.class); // GH-90000

            // Two violations registered
            @DomainEventMetaInfo(eventType = "AnotherBroken", schemaVersion = 0) // GH-90000
            class AnotherBrokenEvent extends DomainEvent {
                AnotherBrokenEvent() { super("AnotherBroken", "a", "A", "t", "u"); } // GH-90000
                @Override public Map<String, Object> toPayload() { return Map.of(); } // GH-90000
            }
            registry.register(AnotherBrokenEvent.class); // GH-90000

            assertThatThrownBy(() -> registry.validate()) // GH-90000
                    .isInstanceOf(DomainEventRegistry.InvalidEventRegistrationException.class) // GH-90000
                    .hasMessageContaining("BrokenEvent [GH-90000]")
                    .hasMessageContaining("AnotherBroken [GH-90000]");
        }

        @Test
        @DisplayName("logs warning but does not throw when no events registered [GH-90000]")
        void shouldWarnOnEmptyRegistry() { // GH-90000
            assertThatCode(() -> registry.validate()).doesNotThrowAnyException(); // GH-90000
        }
    }

    // ─── Nested: Queries ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Queries [GH-90000]")
    class Queries {

        @Test
        @DisplayName("isRegistered returns true for registered type [GH-90000]")
        void shouldReturnTrueForRegisteredType() { // GH-90000
            registry.register(OrderPlacedEvent.class); // GH-90000

            assertThat(registry.isRegistered("OrderPlaced [GH-90000]")).isTrue();
        }

        @Test
        @DisplayName("isRegistered returns false for unknown type [GH-90000]")
        void shouldReturnFalseForUnknownType() { // GH-90000
            assertThat(registry.isRegistered("Unknown [GH-90000]")).isFalse();
        }

        @Test
        @DisplayName("getMetadata returns null for unregistered type [GH-90000]")
        void shouldReturnNullForUnregisteredType() { // GH-90000
            assertThat(registry.getMetadata("Ghost [GH-90000]")).isNull();
        }

        @Test
        @DisplayName("registeredTypes returns unmodifiable set [GH-90000]")
        void shouldReturnUnmodifiableSet() { // GH-90000
            registry.register(OrderPlacedEvent.class); // GH-90000

            assertThatThrownBy(() -> registry.registeredTypes().add("Hacked [GH-90000]"))
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        }

        @Test
        @DisplayName("size() reflects the number of registered types [GH-90000]")
        void shouldTrackSize() { // GH-90000
            assertThat(registry.size()).isEqualTo(0); // GH-90000

            registry.register(OrderPlacedEvent.class); // GH-90000
            assertThat(registry.size()).isEqualTo(1); // GH-90000

            registry.register(UserSignedUpEvent.class); // GH-90000
            assertThat(registry.size()).isEqualTo(2); // GH-90000
        }
    }
}
