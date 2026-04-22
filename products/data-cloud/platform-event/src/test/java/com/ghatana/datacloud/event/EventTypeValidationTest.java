package com.ghatana.datacloud.event;

import com.ghatana.datacloud.StorageTier;
import com.ghatana.datacloud.event.model.EventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive tests for EventType validation including schema validation,
 * lifecycle management, and governance enforcement.
 *
 * @doc.type test
 * @doc.purpose EventType validation and lifecycle tests
 * @doc.layer domain
 * @doc.pattern Test
 */
@DisplayName("EventType Validation Tests [GH-90000]")
class EventTypeValidationTest {

    // =========================================================================
    // BUILDER VALIDATION
    // =========================================================================

    @Nested
    @DisplayName("Builder validation [GH-90000]")
    class BuilderValidation {

        @Test
        @DisplayName("should build valid EventType with required fields [GH-90000]")
        void shouldBuildValidEventTypeWithRequiredFields() { // GH-90000
            EventType eventType = EventType.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .name("order.created [GH-90000]")
                .schemaVersion("1.0.0 [GH-90000]")
                .build(); // GH-90000

            assertThat(eventType.getTenantId()).isEqualTo("tenant-1 [GH-90000]");
            assertThat(eventType.getName()).isEqualTo("order.created [GH-90000]");
            assertThat(eventType.getSchemaVersion()).isEqualTo("1.0.0 [GH-90000]");
            assertThat(eventType.getLifecycleStatus()).isEqualTo(EventType.LifecycleStatus.DRAFT); // GH-90000
            assertThat(eventType.getCompatibilityPolicy()).isEqualTo(EventType.CompatibilityPolicy.BACKWARD); // GH-90000
        }

        @Test
        @DisplayName("should build EventType with all fields [GH-90000]")
        void shouldBuildEventTypeWithAllFields() { // GH-90000
            Map<String, Object> headerSchema = Map.of( // GH-90000
                "source", Map.of("type", "string", "required", true), // GH-90000
                "correlationId", Map.of("type", "string", "required", false) // GH-90000
            );

            Map<String, Object> payloadSchema = Map.of( // GH-90000
                "orderId", Map.of("type", "string", "required", true), // GH-90000
                "amount", Map.of("type", "number", "required", true) // GH-90000
            );

            Map<String, Object> governance = Map.of( // GH-90000
                "owner", "commerce-team",
                "sla", "99.9%"
            );

            Map<String, Object> storageHints = Map.of( // GH-90000
                "partitionKey", "orderId",
                "compression", "lz4"
            );

            EventType eventType = EventType.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .namespace("commerce [GH-90000]")
                .name("order.created [GH-90000]")
                .label("Order Created [GH-90000]")
                .description("Emitted when a new order is created [GH-90000]")
                .schemaVersion("1.0.0 [GH-90000]")
                .headerSchema(headerSchema) // GH-90000
                .payloadSchema(payloadSchema) // GH-90000
                .lifecycleStatus(EventType.LifecycleStatus.ACTIVE) // GH-90000
                .compatibilityPolicy(EventType.CompatibilityPolicy.BACKWARD) // GH-90000
                .governance(governance) // GH-90000
                .defaultStorageTier(StorageTier.WARM) // GH-90000
                .storageHints(storageHints) // GH-90000
                .tags(Set.of("production", "critical", "pii")) // GH-90000
                .examples(List.of("{\"orderId\":\"123\",\"amount\":99.99}")) // GH-90000
                .aliases(Set.of("order_created_v1 [GH-90000]"))
                .build(); // GH-90000

            assertThat(eventType.getNamespace()).isEqualTo("commerce [GH-90000]");
            assertThat(eventType.getLabel()).isEqualTo("Order Created [GH-90000]");
            assertThat(eventType.getHeaderSchema()).isEqualTo(headerSchema); // GH-90000
            assertThat(eventType.getPayloadSchema()).isEqualTo(payloadSchema); // GH-90000
            assertThat(eventType.getLifecycleStatus()).isEqualTo(EventType.LifecycleStatus.ACTIVE); // GH-90000
            assertThat(eventType.getTags()).contains("production", "critical"); // GH-90000
            assertThat(eventType.getExamples()).hasSize(1); // GH-90000
        }

        @Test
        @DisplayName("should use default values for optional fields [GH-90000]")
        void shouldUseDefaultsForOptionalFields() { // GH-90000
            EventType eventType = EventType.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .name("test.event [GH-90000]")
                .schemaVersion("1.0.0 [GH-90000]")
                .build(); // GH-90000

            assertThat(eventType.getHeaderSchema()).isNotNull().isEmpty(); // GH-90000
            assertThat(eventType.getPayloadSchema()).isNotNull().isEmpty(); // GH-90000
            assertThat(eventType.getGovernance()).isNotNull().isEmpty(); // GH-90000
            assertThat(eventType.getStorageHints()).isNotNull().isEmpty(); // GH-90000
            assertThat(eventType.getTags()).isNotNull().isEmpty(); // GH-90000
            assertThat(eventType.getExamples()).isNotNull().isEmpty(); // GH-90000
            assertThat(eventType.getAliases()).isNotNull().isEmpty(); // GH-90000
        }
    }

    // =========================================================================
    // SCHEMA VALIDATION
    // =========================================================================

    @Nested
    @DisplayName("Schema validation [GH-90000]")
    class SchemaValidation {

        @Test
        @DisplayName("should validate header schema structure [GH-90000]")
        void shouldValidateHeaderSchemaStructure() { // GH-90000
            Map<String, Object> headerSchema = Map.of( // GH-90000
                "source", Map.of("type", "string", "required", true), // GH-90000
                "priority", Map.of("type", "integer", "required", false, "default", 5) // GH-90000
            );

            EventType eventType = EventType.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .name("test.event [GH-90000]")
                .headerSchema(headerSchema) // GH-90000
                .build(); // GH-90000

            assertThat(eventType.getHeaderSchema()).isNotNull(); // GH-90000
            assertThat(eventType.getHeaderSchema()).containsKey("source [GH-90000]");
            assertThat(eventType.getHeaderSchema()).containsKey("priority [GH-90000]");
        }

        @Test
        @DisplayName("should validate payload schema structure [GH-90000]")
        void shouldValidatePayloadSchemaStructure() { // GH-90000
            Map<String, Object> payloadSchema = Map.of( // GH-90000
                "id", Map.of("type", "string", "required", true), // GH-90000
                "value", Map.of("type", "number", "required", true), // GH-90000
                "metadata", Map.of("type", "object", "required", false) // GH-90000
            );

            EventType eventType = EventType.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .name("test.event [GH-90000]")
                .payloadSchema(payloadSchema) // GH-90000
                .build(); // GH-90000

            assertThat(eventType.getPayloadSchema()).isNotNull(); // GH-90000
            assertThat(eventType.getPayloadSchema()).containsKey("id [GH-90000]");
            assertThat(eventType.getPayloadSchema()).containsKey("value [GH-90000]");
            assertThat(eventType.getPayloadSchema()).containsKey("metadata [GH-90000]");
        }

        @Test
        @DisplayName("should handle empty schemas [GH-90000]")
        void shouldHandleEmptySchemas() { // GH-90000
            EventType eventType = EventType.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .name("test.event [GH-90000]")
                .headerSchema(Map.of()) // GH-90000
                .payloadSchema(Map.of()) // GH-90000
                .build(); // GH-90000

            assertThat(eventType.getHeaderSchema()).isEmpty(); // GH-90000
            assertThat(eventType.getPayloadSchema()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should handle null schemas with defaults [GH-90000]")
        void shouldHandleNullSchemasWithDefaults() { // GH-90000
            EventType eventType = EventType.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .name("test.event [GH-90000]")
                .headerSchema(null) // GH-90000
                .payloadSchema(null) // GH-90000
                .build(); // GH-90000

            assertThat(eventType.getHeaderSchema()).isNotNull().isEmpty(); // GH-90000
            assertThat(eventType.getPayloadSchema()).isNotNull().isEmpty(); // GH-90000
        }
    }

    // =========================================================================
    // LIFECYCLE VALIDATION
    // =========================================================================

    @Nested
    @DisplayName("Lifecycle validation [GH-90000]")
    class LifecycleValidation {

        @Test
        @DisplayName("should activate DRAFT event type [GH-90000]")
        void shouldActivateDraftEventType() { // GH-90000
            EventType eventType = EventType.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .name("test.event [GH-90000]")
                .lifecycleStatus(EventType.LifecycleStatus.DRAFT) // GH-90000
                .build(); // GH-90000

            eventType.activate(); // GH-90000

            assertThat(eventType.getLifecycleStatus()).isEqualTo(EventType.LifecycleStatus.ACTIVE); // GH-90000
            assertThat(eventType.acceptsEvents()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should activate DEPRECATED event type [GH-90000]")
        void shouldActivateDeprecatedEventType() { // GH-90000
            EventType eventType = EventType.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .name("test.event [GH-90000]")
                .lifecycleStatus(EventType.LifecycleStatus.DEPRECATED) // GH-90000
                .build(); // GH-90000

            eventType.activate(); // GH-90000

            assertThat(eventType.getLifecycleStatus()).isEqualTo(EventType.LifecycleStatus.ACTIVE); // GH-90000
        }

        @Test
        @DisplayName("should throw when activating RETIRED event type [GH-90000]")
        void shouldThrowWhenActivatingRetiredEventType() { // GH-90000
            EventType eventType = EventType.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .name("test.event [GH-90000]")
                .lifecycleStatus(EventType.LifecycleStatus.RETIRED) // GH-90000
                .build(); // GH-90000

            assertThatThrownBy(() -> eventType.activate()) // GH-90000
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("Cannot activate a retired event type [GH-90000]");
        }

        @Test
        @DisplayName("should deprecate event type with metadata [GH-90000]")
        void shouldDeprecateEventTypeWithMetadata() { // GH-90000
            EventType eventType = EventType.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .name("test.event [GH-90000]")
                .lifecycleStatus(EventType.LifecycleStatus.ACTIVE) // GH-90000
                .build(); // GH-90000

            Instant deprecationDate = Instant.now().plusSeconds(86400 * 30); // 30 days // GH-90000
            String migrationGuide = "https://docs.example.com/migration";

            eventType.deprecate(deprecationDate, migrationGuide); // GH-90000

            assertThat(eventType.getLifecycleStatus()).isEqualTo(EventType.LifecycleStatus.DEPRECATED); // GH-90000
            assertThat(eventType.getGovernance()).containsKey("deprecationDate [GH-90000]");
            assertThat(eventType.getGovernance()).containsKey("migrationGuide [GH-90000]");
            assertThat(eventType.acceptsEvents()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should deprecate event type without migration guide [GH-90000]")
        void shouldDeprecateEventTypeWithoutMigrationGuide() { // GH-90000
            EventType eventType = EventType.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .name("test.event [GH-90000]")
                .lifecycleStatus(EventType.LifecycleStatus.ACTIVE) // GH-90000
                .build(); // GH-90000

            eventType.deprecate(Instant.now().plusSeconds(86400), null); // GH-90000

            assertThat(eventType.getLifecycleStatus()).isEqualTo(EventType.LifecycleStatus.DEPRECATED); // GH-90000
            assertThat(eventType.getGovernance()).containsKey("deprecationDate [GH-90000]");
            assertThat(eventType.getGovernance()).doesNotContainKey("migrationGuide [GH-90000]");
        }

        @Test
        @DisplayName("should retire event type [GH-90000]")
        void shouldRetireEventType() { // GH-90000
            EventType eventType = EventType.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .name("test.event [GH-90000]")
                .lifecycleStatus(EventType.LifecycleStatus.DEPRECATED) // GH-90000
                .build(); // GH-90000

            eventType.retire(); // GH-90000

            assertThat(eventType.getLifecycleStatus()).isEqualTo(EventType.LifecycleStatus.RETIRED); // GH-90000
            assertThat(eventType.acceptsEvents()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("should accept events when ACTIVE [GH-90000]")
        void shouldAcceptEventsWhenActive() { // GH-90000
            EventType eventType = EventType.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .name("test.event [GH-90000]")
                .lifecycleStatus(EventType.LifecycleStatus.ACTIVE) // GH-90000
                .build(); // GH-90000

            assertThat(eventType.acceptsEvents()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should accept events when DEPRECATED [GH-90000]")
        void shouldAcceptEventsWhenDeprecated() { // GH-90000
            EventType eventType = EventType.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .name("test.event [GH-90000]")
                .lifecycleStatus(EventType.LifecycleStatus.DEPRECATED) // GH-90000
                .build(); // GH-90000

            assertThat(eventType.acceptsEvents()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should not accept events when DRAFT [GH-90000]")
        void shouldNotAcceptEventsWhenDraft() { // GH-90000
            EventType eventType = EventType.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .name("test.event [GH-90000]")
                .lifecycleStatus(EventType.LifecycleStatus.DRAFT) // GH-90000
                .build(); // GH-90000

            assertThat(eventType.acceptsEvents()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("should not accept events when RETIRED [GH-90000]")
        void shouldNotAcceptEventsWhenRetired() { // GH-90000
            EventType eventType = EventType.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .name("test.event [GH-90000]")
                .lifecycleStatus(EventType.LifecycleStatus.RETIRED) // GH-90000
                .build(); // GH-90000

            assertThat(eventType.acceptsEvents()).isFalse(); // GH-90000
        }
    }

    // =========================================================================
    // COMPATIBILITY POLICY VALIDATION
    // =========================================================================

    @Nested
    @DisplayName("Compatibility policy validation [GH-90000]")
    class CompatibilityPolicyValidation {

        @Test
        @DisplayName("should set BACKWARD compatibility policy [GH-90000]")
        void shouldSetBackwardCompatibilityPolicy() { // GH-90000
            EventType eventType = EventType.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .name("test.event [GH-90000]")
                .compatibilityPolicy(EventType.CompatibilityPolicy.BACKWARD) // GH-90000
                .build(); // GH-90000

            assertThat(eventType.getCompatibilityPolicy()).isEqualTo(EventType.CompatibilityPolicy.BACKWARD); // GH-90000
        }

        @Test
        @DisplayName("should set FORWARD compatibility policy [GH-90000]")
        void shouldSetForwardCompatibilityPolicy() { // GH-90000
            EventType eventType = EventType.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .name("test.event [GH-90000]")
                .compatibilityPolicy(EventType.CompatibilityPolicy.FORWARD) // GH-90000
                .build(); // GH-90000

            assertThat(eventType.getCompatibilityPolicy()).isEqualTo(EventType.CompatibilityPolicy.FORWARD); // GH-90000
        }

        @Test
        @DisplayName("should set FULL compatibility policy [GH-90000]")
        void shouldSetFullCompatibilityPolicy() { // GH-90000
            EventType eventType = EventType.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .name("test.event [GH-90000]")
                .compatibilityPolicy(EventType.CompatibilityPolicy.FULL) // GH-90000
                .build(); // GH-90000

            assertThat(eventType.getCompatibilityPolicy()).isEqualTo(EventType.CompatibilityPolicy.FULL); // GH-90000
        }

        @Test
        @DisplayName("should set NONE compatibility policy [GH-90000]")
        void shouldSetNoneCompatibilityPolicy() { // GH-90000
            EventType eventType = EventType.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .name("test.event [GH-90000]")
                .compatibilityPolicy(EventType.CompatibilityPolicy.NONE) // GH-90000
                .build(); // GH-90000

            assertThat(eventType.getCompatibilityPolicy()).isEqualTo(EventType.CompatibilityPolicy.NONE); // GH-90000
        }
    }

    // =========================================================================
    // STORAGE HINTS VALIDATION
    // =========================================================================

    @Nested
    @DisplayName("Storage hints validation [GH-90000]")
    class StorageHintsValidation {

        @Test
        @DisplayName("should set storage tier [GH-90000]")
        void shouldSetStorageTier() { // GH-90000
            EventType eventType = EventType.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .name("test.event [GH-90000]")
                .defaultStorageTier(StorageTier.COLD) // GH-90000
                .build(); // GH-90000

            assertThat(eventType.getDefaultStorageTier()).isEqualTo(StorageTier.COLD); // GH-90000
        }

        @Test
        @DisplayName("should set storage hints [GH-90000]")
        void shouldSetStorageHints() { // GH-90000
            Map<String, Object> hints = Map.of( // GH-90000
                "partitionKey", "orderId",
                "compression", "zstd",
                "retention", "90d",
                "indexedFields", List.of("orderId", "customerId") // GH-90000
            );

            EventType eventType = EventType.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .name("test.event [GH-90000]")
                .storageHints(hints) // GH-90000
                .build(); // GH-90000

            assertThat(eventType.getStorageHints()).isEqualTo(hints); // GH-90000
            assertThat(eventType.getStorageHints()).containsKey("partitionKey [GH-90000]");
        }

        @Test
        @DisplayName("should handle empty storage hints [GH-90000]")
        void shouldHandleEmptyStorageHints() { // GH-90000
            EventType eventType = EventType.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .name("test.event [GH-90000]")
                .storageHints(Map.of()) // GH-90000
                .build(); // GH-90000

            assertThat(eventType.getStorageHints()).isEmpty(); // GH-90000
        }
    }

    // =========================================================================
    // GOVERNANCE METADATA VALIDATION
    // =========================================================================

    @Nested
    @DisplayName("Governance metadata validation [GH-90000]")
    class GovernanceMetadataValidation {

        @Test
        @DisplayName("should set governance metadata [GH-90000]")
        void shouldSetGovernanceMetadata() { // GH-90000
            Map<String, Object> governance = Map.of( // GH-90000
                "owner", "platform-team",
                "sla", "99.95%",
                "reviewDate", "2026-06-01"
            );

            EventType eventType = EventType.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .name("test.event [GH-90000]")
                .governance(governance) // GH-90000
                .build(); // GH-90000

            assertThat(eventType.getGovernance()).isEqualTo(governance); // GH-90000
            assertThat(eventType.getOwner()).isEqualTo("platform-team [GH-90000]");
        }

        @Test
        @DisplayName("should get owner from governance [GH-90000]")
        void shouldGetOwnerFromGovernance() { // GH-90000
            EventType eventType = EventType.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .name("test.event [GH-90000]")
                .governance(Map.of("owner", "data-team")) // GH-90000
                .build(); // GH-90000

            assertThat(eventType.getOwner()).isEqualTo("data-team [GH-90000]");
        }

        @Test
        @DisplayName("should return null when owner not set [GH-90000]")
        void shouldReturnNullWhenOwnerNotSet() { // GH-90000
            EventType eventType = EventType.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .name("test.event [GH-90000]")
                .governance(Map.of()) // GH-90000
                .build(); // GH-90000

            assertThat(eventType.getOwner()).isNull(); // GH-90000
        }
    }

    // =========================================================================
    // DISCOVERY METADATA VALIDATION
    // =========================================================================

    @Nested
    @DisplayName("Discovery metadata validation [GH-90000]")
    class DiscoveryMetadataValidation {

        @Test
        @DisplayName("should set tags [GH-90000]")
        void shouldSetTags() { // GH-90000
            Set<String> tags = Set.of("production", "critical", "pii"); // GH-90000

            EventType eventType = EventType.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .name("test.event [GH-90000]")
                .tags(tags) // GH-90000
                .build(); // GH-90000

            assertThat(eventType.getTags()).contains("production", "critical", "pii"); // GH-90000
        }

        @Test
        @DisplayName("should set examples [GH-90000]")
        void shouldSetExamples() { // GH-90000
            List<String> examples = List.of( // GH-90000
                "{\"id\":\"123\",\"value\":100}",
                "{\"id\":\"456\",\"value\":200}"
            );

            EventType eventType = EventType.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .name("test.event [GH-90000]")
                .examples(examples) // GH-90000
                .build(); // GH-90000

            assertThat(eventType.getExamples()).hasSize(2); // GH-90000
            assertThat(eventType.getExamples()).contains("{\"id\":\"123\",\"value\":100}"); // GH-90000
        }

        @Test
        @DisplayName("should set aliases [GH-90000]")
        void shouldSetAliases() { // GH-90000
            Set<String> aliases = Set.of("event_v1", "legacy_event"); // GH-90000

            EventType eventType = EventType.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .name("test.event [GH-90000]")
                .aliases(aliases) // GH-90000
                .build(); // GH-90000

            assertThat(eventType.getAliases()).contains("event_v1", "legacy_event"); // GH-90000
        }

        @Test
        @DisplayName("should get fully qualified name with namespace [GH-90000]")
        void shouldGetFullyQualifiedNameWithNamespace() { // GH-90000
            EventType eventType = EventType.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .namespace("commerce [GH-90000]")
                .name("order.created [GH-90000]")
                .build(); // GH-90000

            assertThat(eventType.getFullyQualifiedName()).isEqualTo("commerce.order.created [GH-90000]");
        }

        @Test
        @DisplayName("should get name only when namespace is empty [GH-90000]")
        void shouldGetNameOnlyWhenNamespaceEmpty() { // GH-90000
            EventType eventType = EventType.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .name("test.event [GH-90000]")
                .namespace(" [GH-90000]")
                .build(); // GH-90000

            assertThat(eventType.getFullyQualifiedName()).isEqualTo("test.event [GH-90000]");
        }

        @Test
        @DisplayName("should get name only when namespace is null [GH-90000]")
        void shouldGetNameOnlyWhenNamespaceNull() { // GH-90000
            EventType eventType = EventType.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .name("test.event [GH-90000]")
                .namespace(null) // GH-90000
                .build(); // GH-90000

            assertThat(eventType.getFullyQualifiedName()).isEqualTo("test.event [GH-90000]");
        }
    }

    // =========================================================================
    // NULL SAFETY VALIDATION
    // =========================================================================

    @Nested
    @DisplayName("Null safety validation [GH-90000]")
    class NullSafetyValidation {

        @Test
        @DisplayName("should handle null header schema [GH-90000]")
        void shouldHandleNullHeaderSchema() { // GH-90000
            EventType eventType = EventType.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .name("test.event [GH-90000]")
                .headerSchema(null) // GH-90000
                .build(); // GH-90000

            assertThat(eventType.getHeaderSchema()).isNotNull().isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should handle null payload schema [GH-90000]")
        void shouldHandleNullPayloadSchema() { // GH-90000
            EventType eventType = EventType.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .name("test.event [GH-90000]")
                .payloadSchema(null) // GH-90000
                .build(); // GH-90000

            assertThat(eventType.getPayloadSchema()).isNotNull().isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should handle null governance [GH-90000]")
        void shouldHandleNullGovernance() { // GH-90000
            EventType eventType = EventType.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .name("test.event [GH-90000]")
                .governance(null) // GH-90000
                .build(); // GH-90000

            assertThat(eventType.getGovernance()).isNotNull().isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should handle null storage hints [GH-90000]")
        void shouldHandleNullStorageHints() { // GH-90000
            EventType eventType = EventType.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .name("test.event [GH-90000]")
                .storageHints(null) // GH-90000
                .build(); // GH-90000

            assertThat(eventType.getStorageHints()).isNotNull().isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should handle null tags [GH-90000]")
        void shouldHandleNullTags() { // GH-90000
            EventType eventType = EventType.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .name("test.event [GH-90000]")
                .tags(null) // GH-90000
                .build(); // GH-90000

            assertThat(eventType.getTags()).isNotNull().isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should handle null examples [GH-90000]")
        void shouldHandleNullExamples() { // GH-90000
            EventType eventType = EventType.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .name("test.event [GH-90000]")
                .examples(null) // GH-90000
                .build(); // GH-90000

            assertThat(eventType.getExamples()).isNotNull().isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should handle null aliases [GH-90000]")
        void shouldHandleNullAliases() { // GH-90000
            EventType eventType = EventType.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .name("test.event [GH-90000]")
                .aliases(null) // GH-90000
                .build(); // GH-90000

            assertThat(eventType.getAliases()).isNotNull().isEmpty(); // GH-90000
        }
    }
}
