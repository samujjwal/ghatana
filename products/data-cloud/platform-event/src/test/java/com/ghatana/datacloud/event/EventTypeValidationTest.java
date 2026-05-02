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
@DisplayName("EventType Validation Tests")
class EventTypeValidationTest {

    // =========================================================================
    // BUILDER VALIDATION
    // =========================================================================

    @Nested
    @DisplayName("Builder validation")
    class BuilderValidation {

        @Test
        @DisplayName("should build valid EventType with required fields")
        void shouldBuildValidEventTypeWithRequiredFields() { 
            EventType eventType = EventType.builder() 
                .tenantId("tenant-1")
                .name("order.created")
                .schemaVersion("1.0.0")
                .build(); 

            assertThat(eventType.getTenantId()).isEqualTo("tenant-1");
            assertThat(eventType.getName()).isEqualTo("order.created");
            assertThat(eventType.getSchemaVersion()).isEqualTo("1.0.0");
            assertThat(eventType.getLifecycleStatus()).isEqualTo(EventType.LifecycleStatus.DRAFT); 
            assertThat(eventType.getCompatibilityPolicy()).isEqualTo(EventType.CompatibilityPolicy.BACKWARD); 
        }

        @Test
        @DisplayName("should build EventType with all fields")
        void shouldBuildEventTypeWithAllFields() { 
            Map<String, Object> headerSchema = Map.of( 
                "source", Map.of("type", "string", "required", true), 
                "correlationId", Map.of("type", "string", "required", false) 
            );

            Map<String, Object> payloadSchema = Map.of( 
                "orderId", Map.of("type", "string", "required", true), 
                "amount", Map.of("type", "number", "required", true) 
            );

            Map<String, Object> governance = Map.of( 
                "owner", "commerce-team",
                "sla", "99.9%"
            );

            Map<String, Object> storageHints = Map.of( 
                "partitionKey", "orderId",
                "compression", "lz4"
            );

            EventType eventType = EventType.builder() 
                .tenantId("tenant-1")
                .namespace("commerce")
                .name("order.created")
                .label("Order Created")
                .description("Emitted when a new order is created")
                .schemaVersion("1.0.0")
                .headerSchema(headerSchema) 
                .payloadSchema(payloadSchema) 
                .lifecycleStatus(EventType.LifecycleStatus.ACTIVE) 
                .compatibilityPolicy(EventType.CompatibilityPolicy.BACKWARD) 
                .governance(governance) 
                .defaultStorageTier(StorageTier.WARM) 
                .storageHints(storageHints) 
                .tags(Set.of("production", "critical", "pii")) 
                .examples(List.of("{\"orderId\":\"123\",\"amount\":99.99}")) 
                .aliases(Set.of("order_created_v1"))
                .build(); 

            assertThat(eventType.getNamespace()).isEqualTo("commerce");
            assertThat(eventType.getLabel()).isEqualTo("Order Created");
            assertThat(eventType.getHeaderSchema()).isEqualTo(headerSchema); 
            assertThat(eventType.getPayloadSchema()).isEqualTo(payloadSchema); 
            assertThat(eventType.getLifecycleStatus()).isEqualTo(EventType.LifecycleStatus.ACTIVE); 
            assertThat(eventType.getTags()).contains("production", "critical"); 
            assertThat(eventType.getExamples()).hasSize(1); 
        }

        @Test
        @DisplayName("should use default values for optional fields")
        void shouldUseDefaultsForOptionalFields() { 
            EventType eventType = EventType.builder() 
                .tenantId("tenant-1")
                .name("test.event")
                .schemaVersion("1.0.0")
                .build(); 

            assertThat(eventType.getHeaderSchema()).isNotNull().isEmpty(); 
            assertThat(eventType.getPayloadSchema()).isNotNull().isEmpty(); 
            assertThat(eventType.getGovernance()).isNotNull().isEmpty(); 
            assertThat(eventType.getStorageHints()).isNotNull().isEmpty(); 
            assertThat(eventType.getTags()).isNotNull().isEmpty(); 
            assertThat(eventType.getExamples()).isNotNull().isEmpty(); 
            assertThat(eventType.getAliases()).isNotNull().isEmpty(); 
        }
    }

    // =========================================================================
    // SCHEMA VALIDATION
    // =========================================================================

    @Nested
    @DisplayName("Schema validation")
    class SchemaValidation {

        @Test
        @DisplayName("should validate header schema structure")
        void shouldValidateHeaderSchemaStructure() { 
            Map<String, Object> headerSchema = Map.of( 
                "source", Map.of("type", "string", "required", true), 
                "priority", Map.of("type", "integer", "required", false, "default", 5) 
            );

            EventType eventType = EventType.builder() 
                .tenantId("tenant-1")
                .name("test.event")
                .headerSchema(headerSchema) 
                .build(); 

            assertThat(eventType.getHeaderSchema()).isNotNull(); 
            assertThat(eventType.getHeaderSchema()).containsKey("source");
            assertThat(eventType.getHeaderSchema()).containsKey("priority");
        }

        @Test
        @DisplayName("should validate payload schema structure")
        void shouldValidatePayloadSchemaStructure() { 
            Map<String, Object> payloadSchema = Map.of( 
                "id", Map.of("type", "string", "required", true), 
                "value", Map.of("type", "number", "required", true), 
                "metadata", Map.of("type", "object", "required", false) 
            );

            EventType eventType = EventType.builder() 
                .tenantId("tenant-1")
                .name("test.event")
                .payloadSchema(payloadSchema) 
                .build(); 

            assertThat(eventType.getPayloadSchema()).isNotNull(); 
            assertThat(eventType.getPayloadSchema()).containsKey("id");
            assertThat(eventType.getPayloadSchema()).containsKey("value");
            assertThat(eventType.getPayloadSchema()).containsKey("metadata");
        }

        @Test
        @DisplayName("should handle empty schemas")
        void shouldHandleEmptySchemas() { 
            EventType eventType = EventType.builder() 
                .tenantId("tenant-1")
                .name("test.event")
                .headerSchema(Map.of()) 
                .payloadSchema(Map.of()) 
                .build(); 

            assertThat(eventType.getHeaderSchema()).isEmpty(); 
            assertThat(eventType.getPayloadSchema()).isEmpty(); 
        }

        @Test
        @DisplayName("should handle null schemas with defaults")
        void shouldHandleNullSchemasWithDefaults() { 
            EventType eventType = EventType.builder() 
                .tenantId("tenant-1")
                .name("test.event")
                .headerSchema(null) 
                .payloadSchema(null) 
                .build(); 

            assertThat(eventType.getHeaderSchema()).isNotNull().isEmpty(); 
            assertThat(eventType.getPayloadSchema()).isNotNull().isEmpty(); 
        }
    }

    // =========================================================================
    // LIFECYCLE VALIDATION
    // =========================================================================

    @Nested
    @DisplayName("Lifecycle validation")
    class LifecycleValidation {

        @Test
        @DisplayName("should activate DRAFT event type")
        void shouldActivateDraftEventType() { 
            EventType eventType = EventType.builder() 
                .tenantId("tenant-1")
                .name("test.event")
                .lifecycleStatus(EventType.LifecycleStatus.DRAFT) 
                .build(); 

            eventType.activate(); 

            assertThat(eventType.getLifecycleStatus()).isEqualTo(EventType.LifecycleStatus.ACTIVE); 
            assertThat(eventType.acceptsEvents()).isTrue(); 
        }

        @Test
        @DisplayName("should activate DEPRECATED event type")
        void shouldActivateDeprecatedEventType() { 
            EventType eventType = EventType.builder() 
                .tenantId("tenant-1")
                .name("test.event")
                .lifecycleStatus(EventType.LifecycleStatus.DEPRECATED) 
                .build(); 

            eventType.activate(); 

            assertThat(eventType.getLifecycleStatus()).isEqualTo(EventType.LifecycleStatus.ACTIVE); 
        }

        @Test
        @DisplayName("should throw when activating RETIRED event type")
        void shouldThrowWhenActivatingRetiredEventType() { 
            EventType eventType = EventType.builder() 
                .tenantId("tenant-1")
                .name("test.event")
                .lifecycleStatus(EventType.LifecycleStatus.RETIRED) 
                .build(); 

            assertThatThrownBy(() -> eventType.activate()) 
                .isInstanceOf(IllegalStateException.class) 
                .hasMessageContaining("Cannot activate a retired event type");
        }

        @Test
        @DisplayName("should deprecate event type with metadata")
        void shouldDeprecateEventTypeWithMetadata() { 
            EventType eventType = EventType.builder() 
                .tenantId("tenant-1")
                .name("test.event")
                .lifecycleStatus(EventType.LifecycleStatus.ACTIVE) 
                .build(); 

            Instant deprecationDate = Instant.now().plusSeconds(86400 * 30); // 30 days 
            String migrationGuide = "https://docs.example.com/migration";

            eventType.deprecate(deprecationDate, migrationGuide); 

            assertThat(eventType.getLifecycleStatus()).isEqualTo(EventType.LifecycleStatus.DEPRECATED); 
            assertThat(eventType.getGovernance()).containsKey("deprecationDate");
            assertThat(eventType.getGovernance()).containsKey("migrationGuide");
            assertThat(eventType.acceptsEvents()).isTrue(); 
        }

        @Test
        @DisplayName("should deprecate event type without migration guide")
        void shouldDeprecateEventTypeWithoutMigrationGuide() { 
            EventType eventType = EventType.builder() 
                .tenantId("tenant-1")
                .name("test.event")
                .lifecycleStatus(EventType.LifecycleStatus.ACTIVE) 
                .build(); 

            eventType.deprecate(Instant.now().plusSeconds(86400), null); 

            assertThat(eventType.getLifecycleStatus()).isEqualTo(EventType.LifecycleStatus.DEPRECATED); 
            assertThat(eventType.getGovernance()).containsKey("deprecationDate");
            assertThat(eventType.getGovernance()).doesNotContainKey("migrationGuide");
        }

        @Test
        @DisplayName("should retire event type")
        void shouldRetireEventType() { 
            EventType eventType = EventType.builder() 
                .tenantId("tenant-1")
                .name("test.event")
                .lifecycleStatus(EventType.LifecycleStatus.DEPRECATED) 
                .build(); 

            eventType.retire(); 

            assertThat(eventType.getLifecycleStatus()).isEqualTo(EventType.LifecycleStatus.RETIRED); 
            assertThat(eventType.acceptsEvents()).isFalse(); 
        }

        @Test
        @DisplayName("should accept events when ACTIVE")
        void shouldAcceptEventsWhenActive() { 
            EventType eventType = EventType.builder() 
                .tenantId("tenant-1")
                .name("test.event")
                .lifecycleStatus(EventType.LifecycleStatus.ACTIVE) 
                .build(); 

            assertThat(eventType.acceptsEvents()).isTrue(); 
        }

        @Test
        @DisplayName("should accept events when DEPRECATED")
        void shouldAcceptEventsWhenDeprecated() { 
            EventType eventType = EventType.builder() 
                .tenantId("tenant-1")
                .name("test.event")
                .lifecycleStatus(EventType.LifecycleStatus.DEPRECATED) 
                .build(); 

            assertThat(eventType.acceptsEvents()).isTrue(); 
        }

        @Test
        @DisplayName("should not accept events when DRAFT")
        void shouldNotAcceptEventsWhenDraft() { 
            EventType eventType = EventType.builder() 
                .tenantId("tenant-1")
                .name("test.event")
                .lifecycleStatus(EventType.LifecycleStatus.DRAFT) 
                .build(); 

            assertThat(eventType.acceptsEvents()).isFalse(); 
        }

        @Test
        @DisplayName("should not accept events when RETIRED")
        void shouldNotAcceptEventsWhenRetired() { 
            EventType eventType = EventType.builder() 
                .tenantId("tenant-1")
                .name("test.event")
                .lifecycleStatus(EventType.LifecycleStatus.RETIRED) 
                .build(); 

            assertThat(eventType.acceptsEvents()).isFalse(); 
        }
    }

    // =========================================================================
    // COMPATIBILITY POLICY VALIDATION
    // =========================================================================

    @Nested
    @DisplayName("Compatibility policy validation")
    class CompatibilityPolicyValidation {

        @Test
        @DisplayName("should set BACKWARD compatibility policy")
        void shouldSetBackwardCompatibilityPolicy() { 
            EventType eventType = EventType.builder() 
                .tenantId("tenant-1")
                .name("test.event")
                .compatibilityPolicy(EventType.CompatibilityPolicy.BACKWARD) 
                .build(); 

            assertThat(eventType.getCompatibilityPolicy()).isEqualTo(EventType.CompatibilityPolicy.BACKWARD); 
        }

        @Test
        @DisplayName("should set FORWARD compatibility policy")
        void shouldSetForwardCompatibilityPolicy() { 
            EventType eventType = EventType.builder() 
                .tenantId("tenant-1")
                .name("test.event")
                .compatibilityPolicy(EventType.CompatibilityPolicy.FORWARD) 
                .build(); 

            assertThat(eventType.getCompatibilityPolicy()).isEqualTo(EventType.CompatibilityPolicy.FORWARD); 
        }

        @Test
        @DisplayName("should set FULL compatibility policy")
        void shouldSetFullCompatibilityPolicy() { 
            EventType eventType = EventType.builder() 
                .tenantId("tenant-1")
                .name("test.event")
                .compatibilityPolicy(EventType.CompatibilityPolicy.FULL) 
                .build(); 

            assertThat(eventType.getCompatibilityPolicy()).isEqualTo(EventType.CompatibilityPolicy.FULL); 
        }

        @Test
        @DisplayName("should set NONE compatibility policy")
        void shouldSetNoneCompatibilityPolicy() { 
            EventType eventType = EventType.builder() 
                .tenantId("tenant-1")
                .name("test.event")
                .compatibilityPolicy(EventType.CompatibilityPolicy.NONE) 
                .build(); 

            assertThat(eventType.getCompatibilityPolicy()).isEqualTo(EventType.CompatibilityPolicy.NONE); 
        }
    }

    // =========================================================================
    // STORAGE HINTS VALIDATION
    // =========================================================================

    @Nested
    @DisplayName("Storage hints validation")
    class StorageHintsValidation {

        @Test
        @DisplayName("should set storage tier")
        void shouldSetStorageTier() { 
            EventType eventType = EventType.builder() 
                .tenantId("tenant-1")
                .name("test.event")
                .defaultStorageTier(StorageTier.COLD) 
                .build(); 

            assertThat(eventType.getDefaultStorageTier()).isEqualTo(StorageTier.COLD); 
        }

        @Test
        @DisplayName("should set storage hints")
        void shouldSetStorageHints() { 
            Map<String, Object> hints = Map.of( 
                "partitionKey", "orderId",
                "compression", "zstd",
                "retention", "90d",
                "indexedFields", List.of("orderId", "customerId") 
            );

            EventType eventType = EventType.builder() 
                .tenantId("tenant-1")
                .name("test.event")
                .storageHints(hints) 
                .build(); 

            assertThat(eventType.getStorageHints()).isEqualTo(hints); 
            assertThat(eventType.getStorageHints()).containsKey("partitionKey");
        }

        @Test
        @DisplayName("should handle empty storage hints")
        void shouldHandleEmptyStorageHints() { 
            EventType eventType = EventType.builder() 
                .tenantId("tenant-1")
                .name("test.event")
                .storageHints(Map.of()) 
                .build(); 

            assertThat(eventType.getStorageHints()).isEmpty(); 
        }
    }

    // =========================================================================
    // GOVERNANCE METADATA VALIDATION
    // =========================================================================

    @Nested
    @DisplayName("Governance metadata validation")
    class GovernanceMetadataValidation {

        @Test
        @DisplayName("should set governance metadata")
        void shouldSetGovernanceMetadata() { 
            Map<String, Object> governance = Map.of( 
                "owner", "platform-team",
                "sla", "99.95%",
                "reviewDate", "2026-06-01"
            );

            EventType eventType = EventType.builder() 
                .tenantId("tenant-1")
                .name("test.event")
                .governance(governance) 
                .build(); 

            assertThat(eventType.getGovernance()).isEqualTo(governance); 
            assertThat(eventType.getOwner()).isEqualTo("platform-team");
        }

        @Test
        @DisplayName("should get owner from governance")
        void shouldGetOwnerFromGovernance() { 
            EventType eventType = EventType.builder() 
                .tenantId("tenant-1")
                .name("test.event")
                .governance(Map.of("owner", "data-team")) 
                .build(); 

            assertThat(eventType.getOwner()).isEqualTo("data-team");
        }

        @Test
        @DisplayName("should return null when owner not set")
        void shouldReturnNullWhenOwnerNotSet() { 
            EventType eventType = EventType.builder() 
                .tenantId("tenant-1")
                .name("test.event")
                .governance(Map.of()) 
                .build(); 

            assertThat(eventType.getOwner()).isNull(); 
        }
    }

    // =========================================================================
    // DISCOVERY METADATA VALIDATION
    // =========================================================================

    @Nested
    @DisplayName("Discovery metadata validation")
    class DiscoveryMetadataValidation {

        @Test
        @DisplayName("should set tags")
        void shouldSetTags() { 
            Set<String> tags = Set.of("production", "critical", "pii"); 

            EventType eventType = EventType.builder() 
                .tenantId("tenant-1")
                .name("test.event")
                .tags(tags) 
                .build(); 

            assertThat(eventType.getTags()).contains("production", "critical", "pii"); 
        }

        @Test
        @DisplayName("should set examples")
        void shouldSetExamples() { 
            List<String> examples = List.of( 
                "{\"id\":\"123\",\"value\":100}",
                "{\"id\":\"456\",\"value\":200}"
            );

            EventType eventType = EventType.builder() 
                .tenantId("tenant-1")
                .name("test.event")
                .examples(examples) 
                .build(); 

            assertThat(eventType.getExamples()).hasSize(2); 
            assertThat(eventType.getExamples()).contains("{\"id\":\"123\",\"value\":100}"); 
        }

        @Test
        @DisplayName("should set aliases")
        void shouldSetAliases() { 
            Set<String> aliases = Set.of("event_v1", "legacy_event"); 

            EventType eventType = EventType.builder() 
                .tenantId("tenant-1")
                .name("test.event")
                .aliases(aliases) 
                .build(); 

            assertThat(eventType.getAliases()).contains("event_v1", "legacy_event"); 
        }

        @Test
        @DisplayName("should get fully qualified name with namespace")
        void shouldGetFullyQualifiedNameWithNamespace() { 
            EventType eventType = EventType.builder() 
                .tenantId("tenant-1")
                .namespace("commerce")
                .name("order.created")
                .build(); 

            assertThat(eventType.getFullyQualifiedName()).isEqualTo("commerce.order.created");
        }

        @Test
        @DisplayName("should get name only when namespace is empty")
        void shouldGetNameOnlyWhenNamespaceEmpty() { 
            EventType eventType = EventType.builder() 
                .tenantId("tenant-1")
                .name("test.event")
                .namespace("")
                .build(); 

            assertThat(eventType.getFullyQualifiedName()).isEqualTo("test.event");
        }

        @Test
        @DisplayName("should get name only when namespace is null")
        void shouldGetNameOnlyWhenNamespaceNull() { 
            EventType eventType = EventType.builder() 
                .tenantId("tenant-1")
                .name("test.event")
                .namespace(null) 
                .build(); 

            assertThat(eventType.getFullyQualifiedName()).isEqualTo("test.event");
        }
    }

    // =========================================================================
    // NULL SAFETY VALIDATION
    // =========================================================================

    @Nested
    @DisplayName("Null safety validation")
    class NullSafetyValidation {

        @Test
        @DisplayName("should handle null header schema")
        void shouldHandleNullHeaderSchema() { 
            EventType eventType = EventType.builder() 
                .tenantId("tenant-1")
                .name("test.event")
                .headerSchema(null) 
                .build(); 

            assertThat(eventType.getHeaderSchema()).isNotNull().isEmpty(); 
        }

        @Test
        @DisplayName("should handle null payload schema")
        void shouldHandleNullPayloadSchema() { 
            EventType eventType = EventType.builder() 
                .tenantId("tenant-1")
                .name("test.event")
                .payloadSchema(null) 
                .build(); 

            assertThat(eventType.getPayloadSchema()).isNotNull().isEmpty(); 
        }

        @Test
        @DisplayName("should handle null governance")
        void shouldHandleNullGovernance() { 
            EventType eventType = EventType.builder() 
                .tenantId("tenant-1")
                .name("test.event")
                .governance(null) 
                .build(); 

            assertThat(eventType.getGovernance()).isNotNull().isEmpty(); 
        }

        @Test
        @DisplayName("should handle null storage hints")
        void shouldHandleNullStorageHints() { 
            EventType eventType = EventType.builder() 
                .tenantId("tenant-1")
                .name("test.event")
                .storageHints(null) 
                .build(); 

            assertThat(eventType.getStorageHints()).isNotNull().isEmpty(); 
        }

        @Test
        @DisplayName("should handle null tags")
        void shouldHandleNullTags() { 
            EventType eventType = EventType.builder() 
                .tenantId("tenant-1")
                .name("test.event")
                .tags(null) 
                .build(); 

            assertThat(eventType.getTags()).isNotNull().isEmpty(); 
        }

        @Test
        @DisplayName("should handle null examples")
        void shouldHandleNullExamples() { 
            EventType eventType = EventType.builder() 
                .tenantId("tenant-1")
                .name("test.event")
                .examples(null) 
                .build(); 

            assertThat(eventType.getExamples()).isNotNull().isEmpty(); 
        }

        @Test
        @DisplayName("should handle null aliases")
        void shouldHandleNullAliases() { 
            EventType eventType = EventType.builder() 
                .tenantId("tenant-1")
                .name("test.event")
                .aliases(null) 
                .build(); 

            assertThat(eventType.getAliases()).isNotNull().isEmpty(); 
        }
    }
}
