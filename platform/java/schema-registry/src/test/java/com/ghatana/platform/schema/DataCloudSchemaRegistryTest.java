package com.ghatana.platform.schema;

import com.ghatana.datacloud.spi.TenantContext;
import com.ghatana.datacloud.spi.provider.InMemoryEventLogStoreProvider;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link DataCloudSchemaRegistry}.
 *
 * <p>Uses an {@link InMemoryEventLogStoreProvider} as the backing store so no
 * network or database is required. The ActiveJ event-loop is driven by
 * {@link EventloopTestBase#runPromise}.
 *
 * @doc.type class
 * @doc.purpose Tests for DataCloudSchemaRegistry — registration, retrieval, validation, compatibility
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("DataCloudSchemaRegistry")
class DataCloudSchemaRegistryTest extends EventloopTestBase {

    private static final TenantContext TENANT = TenantContext.of("test-tenant");

    /** Minimal JSON Schema (Draft-07) with one required field. */
    private static final String SCHEMA_V1 = """
            {
              "$schema": "http://json-schema.org/draft-07/schema#",
              "type": "object",
              "required": ["orderId"],
              "properties": {
                "orderId": {"type": "string"}
              }
            }
            """;

    /** V2 adds an optional field — BACKWARD compatible with V1. */
    private static final String SCHEMA_V2_BACKWARD = """
            {
              "$schema": "http://json-schema.org/draft-07/schema#",
              "type": "object",
              "required": ["orderId"],
              "properties": {
                "orderId": {"type": "string"},
                "amount":  {"type": "number"}
              }
            }
            """;

    /** V3 removes required field — NOT backward compatible. */
    private static final String SCHEMA_V3_BREAKS_BACKWARD = """
            {
              "$schema": "http://json-schema.org/draft-07/schema#",
              "type": "object",
              "required": [],
              "properties": {
                "amount": {"type": "number"}
              }
            }
            """;

    private DataCloudSchemaRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new DataCloudSchemaRegistry(new InMemoryEventLogStoreProvider(), TENANT);
    }

    // =========================================================================
    //  registerSchema() + getSchema()
    // =========================================================================

    @Nested
    @DisplayName("registerSchema() and getSchema()")
    class RegisterAndGetTests {

        @Test
        @DisplayName("registered schema is retrievable by name and version")
        void registeredSchemaIsRetrievable() {
            runPromise(() ->
                registry.registerSchema("OrderCreated", "1.0.0", SCHEMA_V1)
                    .then(reg -> registry.getSchema("OrderCreated", "1.0.0"))
                    .map(opt -> {
                        assertThat(opt).isPresent();
                        assertThat(opt.get().schemaName()).isEqualTo("OrderCreated");
                        assertThat(opt.get().schemaVersion()).isEqualTo("1.0.0");
                        assertThat(opt.get().qualifiedId()).isEqualTo("OrderCreated:1.0.0");
                        return opt;
                    })
            );
        }

        @Test
        @DisplayName("getSchema() returns empty Optional when schema not registered")
        void unknownSchemaReturnsEmpty() {
            Optional<RegisteredSchema> result = runPromise(() ->
                registry.getSchema("NonExistent", "1.0.0")
            );
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("getLatestSchema() returns the schema when only one version exists")
        void getLatestReturnsSingleVersion() {
            Optional<RegisteredSchema> result = runPromise(() ->
                registry.registerSchema("OrderCreated", "1.0.0", SCHEMA_V1)
                    .then(ignored -> registry.getLatestSchema("OrderCreated"))
            );
            assertThat(result).isPresent();
            assertThat(result.get().schemaVersion()).isEqualTo("1.0.0");
        }

        @Test
        @DisplayName("getLatestSchema() returns empty when no schemas registered")
        void getLatestReturnsEmptyWhenNoneRegistered() {
            Optional<RegisteredSchema> result = runPromise(() ->
                registry.getLatestSchema("Unknown")
            );
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("idempotent registration: same schema registered twice is a no-op")
        void idempotentRegistrationIsNoOp() {
            runPromise(() ->
                registry.registerSchema("OrderCreated", "1.0.0", SCHEMA_V1)
                    .then(first ->
                        // Second registration of same name+version+schema is a no-op
                        registry.registerSchema("OrderCreated", "1.0.0", SCHEMA_V1))
                    .then(ignored -> registry.getSchema("OrderCreated", "1.0.0"))
                    .map(opt -> {
                        assertThat(opt).isPresent();
                        return opt;
                    })
            );
        }
    }

    // =========================================================================
    //  validate()
    // =========================================================================

    @Nested
    @DisplayName("validate()")
    class ValidateTests {

        @Test
        @DisplayName("valid payload passes validation")
        void validPayloadPassesValidation() {
            runPromise(() ->
                registry.registerSchema("OrderCreated", "1.0.0", SCHEMA_V1)
                    .then(ignored -> registry.validate(
                        "OrderCreated", "1.0.0",
                        "{\"orderId\": \"order-abc\"}"))
                    .map(result -> {
                        assertThat(result.isValid()).isTrue();
                        assertThat(result.errors()).isEmpty();
                        return result;
                    })
            );
        }

        @Test
        @DisplayName("payload missing required field fails validation")
        void missingRequiredFieldFailsValidation() {
            runPromise(() ->
                registry.registerSchema("OrderCreated", "1.0.0", SCHEMA_V1)
                    .then(ignored -> registry.validate(
                        "OrderCreated", "1.0.0",
                        "{}"))  // missing orderId
                    .map(result -> {
                        assertThat(result.isValid()).isFalse();
                        assertThat(result.errors()).isNotEmpty();
                        return result;
                    })
            );
        }

        @Test
        @DisplayName("validate against unknown schema version returns failure")
        void validateUnknownSchemaVersionReturnsFailure() {
            ValidationResult result = runPromise(() ->
                registry.validate("Unknown", "9.9.9", "{\"x\": 1}")
            );
            assertThat(result.isValid()).isFalse();
        }
    }

    // =========================================================================
    //  Compatibility checking
    // =========================================================================

    @Nested
    @DisplayName("compatibility enforcement")
    class CompatibilityTests {

        @Test
        @DisplayName("BACKWARD compatible schema is accepted as new version")
        void backwardCompatibleNewVersionAccepted() {
            // V1 → V2 (adds optional field): backward compatible
            assertThatNoException().isThrownBy(() -> runPromise(() ->
                registry.registerSchema("Order", "1.0.0", SCHEMA_V1, CompatibilityMode.BACKWARD)
                    .then(ignored ->
                        registry.registerSchema("Order", "2.0.0", SCHEMA_V2_BACKWARD, CompatibilityMode.BACKWARD))
            ));
        }

        @Test
        @DisplayName("removing required field from BACKWARD mode schema throws SchemaCompatibilityException")
        void removingRequiredFieldBreaksBackwardCompatibility() {
            assertThatThrownBy(() -> runPromise(() ->
                registry.registerSchema("Order", "1.0.0", SCHEMA_V1, CompatibilityMode.BACKWARD)
                    .then(ignored ->
                        registry.registerSchema("Order", "2.0.0", SCHEMA_V3_BREAKS_BACKWARD, CompatibilityMode.BACKWARD))
            )).isInstanceOf(SchemaCompatibilityException.class)
              .hasMessageContaining("BACKWARD");
        }
    }
}
