package com.ghatana.datacloud.entity;

import io.activej.promise.Promise;
import jakarta.persistence.OptimisticLockException;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @doc.type class
 * @doc.purpose Coverage for tenant isolation, optimistic concurrency, soft-delete audit preservation, and DTO/persistence contract behavior
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Entity Repository Coverage Tests")
class EntityRepositoryCoverageTest extends EventloopTestBase {

    @Test
    @DisplayName("DE-1: tenant isolation blocks cross-tenant reads")
    void shouldEnforceTenantIsolation() {
        InMemoryTenantEntityRepository repository = new InMemoryTenantEntityRepository();
        Entity entity = sampleEntity("tenant-a", "orders");

        Entity saved = runPromise(() -> repository.save("tenant-a", entity));

        Optional<Entity> sameTenant = runPromise(() -> repository.findById("tenant-a", "orders", saved.getId()));
        Optional<Entity> otherTenant = runPromise(() -> repository.findById("tenant-b", "orders", saved.getId()));

        assertThat(sameTenant).isPresent();
        assertThat(otherTenant).isEmpty();
    }

    @Test
    @DisplayName("DE-2: optimistic concurrency conflict rejects stale update")
    void shouldRejectStaleVersionUpdate() {
        InMemoryTenantEntityRepository repository = new InMemoryTenantEntityRepository();
        Entity created = runPromise(() -> repository.save("tenant-a", sampleEntity("tenant-a", "orders")));

        Entity staleUpdate = created.toBuilder()
            .version(created.getVersion() - 1)
            .build();

        assertThatThrownBy(() -> runPromise(() -> repository.save("tenant-a", staleUpdate)))
            .isInstanceOf(OptimisticLockException.class)
            .hasMessageContaining("Version conflict");
    }

    @Test
    @DisplayName("DE-3: soft delete preserves audit fields and payload")
    void shouldPreserveAuditTrailOnSoftDelete() {
        InMemoryTenantEntityRepository repository = new InMemoryTenantEntityRepository();
        Entity created = runPromise(() -> repository.save("tenant-a", sampleEntity("tenant-a", "orders")));

        runPromise(() -> repository.delete("tenant-a", "orders", created.getId()));
        Optional<Entity> maybeEntity = repository.findIncludingInactive("tenant-a", "orders", created.getId());

        assertThat(maybeEntity).isPresent();
        Entity deleted = maybeEntity.orElseThrow();
        assertThat(deleted.getActive()).isFalse();
        assertThat(deleted.getUpdatedBy()).isEqualTo("system-soft-delete");
        assertThat(deleted.getData()).containsEntry("status", "NEW");
    }

    @Test
    @DisplayName("DE-4: DTO and persistence model contract stays consistent")
    void shouldKeepDtoPersistenceContract() {
        InMemoryTenantEntityRepository repository = new InMemoryTenantEntityRepository();

        Map<String, Object> dto = new HashMap<>();
        dto.put("orderId", "ORD-100");
        dto.put("customerId", "CUST-77");
        dto.put("status", "NEW");

        Entity entity = Entity.builder()
            .id(UUID.randomUUID())
            .tenantId("tenant-a")
            .collectionName("orders")
            .version(1)
            .active(true)
            .data(dto)
            .createdBy("api-user")
            .build();

        Entity saved = runPromise(() -> repository.save("tenant-a", entity));
        Entity reloaded = runPromise(() -> repository.findById("tenant-a", "orders", saved.getId())).orElseThrow();

        assertThat(DataCloudColumnNames.TENANT_ID).isEqualTo("tenant_id");
        assertThat(DataCloudColumnNames.COLLECTION_NAME).isEqualTo("collection_name");
        assertThat(reloaded.getTenantId()).isEqualTo("tenant-a");
        assertThat(reloaded.getCollectionName()).isEqualTo("orders");
        assertThat(reloaded.getData())
            .containsEntry("orderId", "ORD-100")
            .containsEntry("customerId", "CUST-77")
            .containsEntry("status", "NEW");
    }

    @Test
    @DisplayName("DE-5: Entity equals and hashCode work correctly")
    void shouldImplementEqualsAndHashCode() {
        UUID id = UUID.randomUUID();
        Entity entity1 = Entity.builder()
            .id(id)
            .tenantId("tenant-a")
            .collectionName("orders")
            .version(1)
            .active(true)
            .data(Map.of("status", "NEW"))
            .createdBy("user-1")
            .build();

        Entity entity2 = Entity.builder()
            .id(id)
            .tenantId("tenant-b")
            .collectionName("products")
            .version(2)
            .active(false)
            .data(Map.of("status", "DONE"))
            .createdBy("user-2")
            .build();

        Entity entity3 = Entity.builder()
            .id(UUID.randomUUID())
            .tenantId("tenant-a")
            .collectionName("orders")
            .version(1)
            .active(true)
            .data(Map.of("status", "NEW"))
            .createdBy("user-1")
            .build();

        assertThat(entity1).isEqualTo(entity2);
        assertThat(entity1.hashCode()).isEqualTo(entity2.hashCode());
        assertThat(entity1).isNotEqualTo(entity3);
    }

    @Test
    @DisplayName("DE-6: Entity toString provides useful debug information")
    void shouldProvideUsefulToString() {
        UUID id = UUID.randomUUID();
        Entity entity = Entity.builder()
            .id(id)
            .tenantId("tenant-a")
            .collectionName("orders")
            .version(1)
            .active(true)
            .data(Map.of("status", "NEW"))
            .createdBy("user-1")
            .build();

        String str = entity.toString();
        assertThat(str).contains("id=" + id);
        assertThat(str).contains("tenantId='tenant-a'");
        assertThat(str).contains("collectionName='orders'");
        assertThat(str).contains("version=1");
        assertThat(str).contains("active=true");
    }

    @Test
    @DisplayName("DE-7: Entity toBuilder creates copy with modifications")
    void shouldSupportBuilderCopy() {
        Entity original = Entity.builder()
            .id(UUID.randomUUID())
            .tenantId("tenant-a")
            .collectionName("orders")
            .version(1)
            .active(true)
            .data(Map.of("status", "NEW"))
            .createdBy("user-1")
            .build();

        Entity modified = original.toBuilder()
            .version(2)
            .data(Map.of("status", "DONE"))
            .build();

        assertThat(modified.getId()).isEqualTo(original.getId());
        assertThat(modified.getTenantId()).isEqualTo(original.getTenantId());
        assertThat(modified.getCollectionName()).isEqualTo(original.getCollectionName());
        assertThat(modified.getVersion()).isEqualTo(2);
        assertThat(modified.getData()).containsEntry("status", "DONE");
    }

    @Test
    @DisplayName("DE-8: DataType enum provides all expected types")
    void shouldProvideAllDataTypes() {
        DataType[] types = DataType.values();
        assertThat(types).hasSizeGreaterThan(15);
        assertThat(types).contains(DataType.STRING, DataType.NUMBER, DataType.BOOLEAN);
        assertThat(types).contains(DataType.DATE, DataType.DATETIME, DataType.TIME);
        assertThat(types).contains(DataType.REFERENCE, DataType.ARRAY, DataType.EMBEDDED);
    }

    @Test
    @DisplayName("DE-9: RelationType enum provides all expected relations")
    void shouldProvideAllRelationTypes() {
        RelationType[] types = RelationType.values();
        assertThat(types).hasSizeGreaterThan(3);
        assertThat(types).contains(RelationType.SIMILAR, RelationType.REFERENCED);
        assertThat(types).contains(RelationType.RELATED, RelationType.HIERARCHICAL);
    }

    @Test
    @DisplayName("DE-10: EntitySuggestion creates valid suggestions")
    void shouldCreateEntitySuggestions() {
        Map<String, Object> data = Map.of("customerId", "CUST-123", "name", "Test Customer");
        EntitySuggestion suggestion = new EntitySuggestion(data, 0.95, List.of("AI reasoning"));

        assertThat(suggestion.suggestedData()).isEqualTo(data);
        assertThat(suggestion.confidence()).isEqualTo(0.95);
        assertThat(suggestion.isHighConfidence()).isTrue();
        assertThat(suggestion.reasoning()).hasSize(1);
    }

    @Test
    @DisplayName("DE-11: FieldValidation creates valid validation rules")
    void shouldCreateFieldValidation() {
        FieldValidation validation = FieldValidation.builder()
            .required(true)
            .minLength(1)
            .maxLength(100)
            .pattern("^[a-zA-Z0-9]+$")
            .build();

        assertThat(validation.required()).isTrue();
        assertThat(validation.minLength()).isEqualTo(1);
        assertThat(validation.maxLength()).isEqualTo(100);
        assertThat(validation.pattern()).isEqualTo("^[a-zA-Z0-9]+$");
    }

    @Test
    @DisplayName("DE-12: FieldUiConfig creates valid UI configuration")
    void shouldCreateFieldUiConfig() {
        FieldUiConfig config = FieldUiConfig.builder()
            .placeholder("Enter customer name")
            .helpText("The full name of the customer")
            .inputType("text")
            .build();

        assertThat(config.placeholder()).isEqualTo("Enter customer name");
        assertThat(config.helpText()).isEqualTo("The full name of the customer");
        assertThat(config.inputType()).isEqualTo("text");
    }

    @Test
    @DisplayName("DE-13: FieldValidation toMap and fromMap conversion")
    void shouldConvertFieldValidationToMapAndBack() {
        FieldValidation original = FieldValidation.builder()
            .required(true)
            .minLength(1)
            .maxLength(100)
            .pattern("^[a-zA-Z0-9]+$")
            .build();

        Map<String, Object> map = original.toMap();
        FieldValidation restored = FieldValidation.fromMap(map);

        assertThat(restored.required()).isEqualTo(original.required());
        assertThat(restored.minLength()).isEqualTo(original.minLength());
        assertThat(restored.maxLength()).isEqualTo(original.maxLength());
        assertThat(restored.pattern()).isEqualTo(original.pattern());
    }

    @Test
    @DisplayName("DE-14: FieldUiConfig toMap and fromMap conversion")
    void shouldConvertFieldUiConfigToMapAndBack() {
        FieldUiConfig original = FieldUiConfig.builder()
            .placeholder("Enter customer name")
            .helpText("The full name of the customer")
            .inputType("text")
            .build();

        Map<String, Object> map = original.toMap();
        FieldUiConfig restored = FieldUiConfig.fromMap(map);

        assertThat(restored.placeholder()).isEqualTo(original.placeholder());
        assertThat(restored.helpText()).isEqualTo(original.helpText());
        assertThat(restored.inputType()).isEqualTo(original.inputType());
    }

    @Test
    @DisplayName("DE-15: FieldValidation factory methods work correctly")
    void shouldUseFieldValidationFactoryMethods() {
        FieldValidation required = FieldValidation.requiredField();
        assertThat(required.required()).isTrue();

        FieldValidation range = FieldValidation.range(0.0, 100.0);
        assertThat(range.min()).isEqualTo(0.0);
        assertThat(range.max()).isEqualTo(100.0);

        FieldValidation length = FieldValidation.length(1, 100);
        assertThat(length.minLength()).isEqualTo(1);
        assertThat(length.maxLength()).isEqualTo(100);

        FieldValidation pattern = FieldValidation.pattern("^[a-z]+$");
        assertThat(pattern.pattern()).isEqualTo("^[a-z]+$");
    }

    @Test
    @DisplayName("DE-16: FieldUiConfig factory methods work correctly")
    void shouldUseFieldUiConfigFactoryMethods() {
        FieldUiConfig visible = FieldUiConfig.visible(1);
        assertThat(visible.visible()).isTrue();
        assertThat(visible.order()).isEqualTo(1);

        FieldUiConfig hidden = FieldUiConfig.hiddenField();
        assertThat(hidden.hidden()).isTrue();
        assertThat(hidden.visible()).isFalse();

        FieldUiConfig readOnly = FieldUiConfig.readOnly(2);
        assertThat(readOnly.readOnly()).isTrue();
        assertThat(readOnly.visible()).isTrue();

        FieldUiConfig fullWidth = FieldUiConfig.fullWidth(3);
        assertThat(fullWidth.span()).isEqualTo(12);
    }

    @Test
    @DisplayName("DE-17: EntitySuggestion confidence methods work correctly")
    void shouldCheckEntitySuggestionConfidence() {
        EntitySuggestion high = new EntitySuggestion(Map.of(), 0.95, List.of());
        assertThat(high.isHighConfidence()).isTrue();
        assertThat(high.isMediumConfidence()).isFalse();
        assertThat(high.isLowConfidence()).isFalse();

        EntitySuggestion medium = new EntitySuggestion(Map.of(), 0.65, List.of());
        assertThat(medium.isHighConfidence()).isFalse();
        assertThat(medium.isMediumConfidence()).isTrue();
        assertThat(medium.isLowConfidence()).isFalse();

        EntitySuggestion low = new EntitySuggestion(Map.of(), 0.25, List.of());
        assertThat(low.isHighConfidence()).isFalse();
        assertThat(low.isMediumConfidence()).isFalse();
        assertThat(low.isLowConfidence()).isTrue();
    }

    @Test
    @DisplayName("DE-18: EntitySuggestion validates confidence range")
    void shouldValidateEntitySuggestionConfidenceRange() {
        assertThatThrownBy(() -> new EntitySuggestion(Map.of(), 1.5, List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Confidence must be between 0.0 and 1.0");

        assertThatThrownBy(() -> new EntitySuggestion(Map.of(), -0.1, List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Confidence must be between 0.0 and 1.0");

        assertThatThrownBy(() -> new EntitySuggestion(null, 0.5, List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Suggested data must not be null");

        assertThatThrownBy(() -> new EntitySuggestion(Map.of(), 0.5, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Reasoning must not be null");
    }

    @Test
    @DisplayName("DE-19: RelationType helper methods work correctly")
    void shouldCheckRelationTypeCategories() {
        assertThat(RelationType.SIMILAR.isContentBased()).isTrue();
        assertThat(RelationType.SIMILAR.isExplicit()).isFalse();
        assertThat(RelationType.SIMILAR.isUsageBased()).isFalse();

        assertThat(RelationType.REFERENCED.isContentBased()).isFalse();
        assertThat(RelationType.REFERENCED.isExplicit()).isTrue();
        assertThat(RelationType.REFERENCED.isUsageBased()).isFalse();

        assertThat(RelationType.RELATED.isContentBased()).isFalse();
        assertThat(RelationType.RELATED.isExplicit()).isFalse();
        assertThat(RelationType.RELATED.isUsageBased()).isTrue();

        assertThat(RelationType.HIERARCHICAL.isContentBased()).isFalse();
        assertThat(RelationType.HIERARCHICAL.isExplicit()).isTrue();
        assertThat(RelationType.HIERARCHICAL.isUsageBased()).isFalse();
    }

    @Test
    @DisplayName("DE-20: EntityEnrichment creates valid enrichments")
    void shouldCreateEntityEnrichment() {
        EntityEnrichment enrichment = new EntityEnrichment(
            "description",
            "Test description",
            "AI reasoning",
            0.85
        );

        assertThat(enrichment.fieldName()).isEqualTo("description");
        assertThat(enrichment.suggestedValue()).isEqualTo("Test description");
        assertThat(enrichment.reason()).isEqualTo("AI reasoning");
        assertThat(enrichment.confidence()).isEqualTo(0.85);
        assertThat(enrichment.isHighConfidence()).isTrue();
        assertThat(enrichment.shouldAutoApply()).isFalse();
    }

    @Test
    @DisplayName("DE-21: EntityEnrichment validates inputs")
    void shouldValidateEntityEnrichmentInputs() {
        assertThatThrownBy(() -> new EntityEnrichment("", "value", "reason", 0.5))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Field name must not be blank");

        assertThatThrownBy(() -> new EntityEnrichment("field", null, "reason", 0.5))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Suggested value must not be null");

        assertThatThrownBy(() -> new EntityEnrichment("field", "value", "", 0.5))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Reason must not be blank");

        assertThatThrownBy(() -> new EntityEnrichment("field", "value", "reason", 1.5))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Confidence must be between 0.0 and 1.0");
    }

    @Test
    @DisplayName("DE-22: EntityEnrichment shouldAutoApply threshold")
    void shouldCheckEntityEnrichmentAutoApplyThreshold() {
        EntityEnrichment high = new EntityEnrichment("field", "value", "reason", 0.95);
        assertThat(high.shouldAutoApply()).isTrue();

        EntityEnrichment medium = new EntityEnrichment("field", "value", "reason", 0.85);
        assertThat(medium.shouldAutoApply()).isFalse();
        assertThat(medium.isHighConfidence()).isTrue();

        EntityEnrichment low = new EntityEnrichment("field", "value", "reason", 0.75);
        assertThat(low.shouldAutoApply()).isFalse();
        assertThat(low.isHighConfidence()).isFalse();
    }

    @Test
    @DisplayName("DE-23: DataType enum has all expected types")
    void shouldHaveAllExpectedDataTypes() {
        DataType[] types = DataType.values();
        assertThat(types).containsExactlyInAnyOrder(
            DataType.STRING, DataType.NUMBER, DataType.BOOLEAN,
            DataType.DATE, DataType.DATETIME, DataType.TIME,
            DataType.REFERENCE, DataType.ARRAY, DataType.EMBEDDED,
            DataType.IMAGE, DataType.FILE, DataType.EMAIL,
            DataType.URL, DataType.PHONE, DataType.RICHTEXT,
            DataType.JSON, DataType.UUID, DataType.ENUM,
            DataType.GEOLOCATION, DataType.COLOR, DataType.CURRENCY,
            DataType.PERCENTAGE, DataType.RATING, DataType.TAGS,
            DataType.MARKDOWN
        );
    }

    @Test
    @DisplayName("DE-24: FieldValidation with numeric constraints")
    void shouldCreateFieldValidationWithNumericConstraints() {
        FieldValidation validation = FieldValidation.builder()
            .min(0.0)
            .max(100.0)
            .build();

        assertThat(validation.min()).isEqualTo(0.0);
        assertThat(validation.max()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("DE-25: FieldValidation with enum values")
    void shouldCreateFieldValidationWithEnumValues() {
        List<String> values = List.of("LOW", "MEDIUM", "HIGH");
        FieldValidation validation = FieldValidation.builder()
            .enumValues(values)
            .build();

        assertThat(validation.enumValues()).isEqualTo(values);
    }

    @Test
    @DisplayName("DE-26: FieldValidation with date constraints")
    void shouldCreateFieldValidationWithDateConstraints() {
        FieldValidation validation = FieldValidation.builder()
            .minDate("2024-01-01")
            .maxDate("2024-12-31")
            .build();

        assertThat(validation.minDate()).isEqualTo("2024-01-01");
        assertThat(validation.maxDate()).isEqualTo("2024-12-31");
    }

    @Test
    @DisplayName("DE-27: FieldValidation with reference validation")
    void shouldCreateFieldValidationWithReferenceValidation() {
        FieldValidation validation = FieldValidation.builder()
            .referenceCollection("customers")
            .referenceField("customerId")
            .build();

        assertThat(validation.referenceCollection()).isEqualTo("customers");
        assertThat(validation.referenceField()).isEqualTo("customerId");
    }

    @Test
    @DisplayName("DE-28: FieldUiConfig with visibility settings")
    void shouldCreateFieldUiConfigWithVisibilitySettings() {
        FieldUiConfig config = FieldUiConfig.builder()
            .visible(true)
            .hidden(false)
            .readOnly(false)
            .disabled(false)
            .build();

        assertThat(config.visible()).isTrue();
        assertThat(config.hidden()).isFalse();
        assertThat(config.readOnly()).isFalse();
        assertThat(config.disabled()).isFalse();
    }

    @Test
    @DisplayName("DE-29: FieldUiConfig with layout settings")
    void shouldCreateFieldUiConfigWithLayoutSettings() {
        FieldUiConfig config = FieldUiConfig.builder()
            .order(1)
            .span(6)
            .width("50%")
            .build();

        assertThat(config.order()).isEqualTo(1);
        assertThat(config.span()).isEqualTo(6);
        assertThat(config.width()).isEqualTo("50%");
    }

    @Test
    @DisplayName("DE-30: FieldUiConfig with display settings")
    void shouldCreateFieldUiConfigWithDisplaySettings() {
        FieldUiConfig config = FieldUiConfig.builder()
            .placeholder("Enter value")
            .helpText("Help text")
            .tooltip("Tooltip")
            .icon("icon-name")
            .build();

        assertThat(config.placeholder()).isEqualTo("Enter value");
        assertThat(config.helpText()).isEqualTo("Help text");
        assertThat(config.tooltip()).isEqualTo("Tooltip");
        assertThat(config.icon()).isEqualTo("icon-name");
    }

    @Test
    @DisplayName("DE-31: FieldUiConfig with input settings")
    void shouldCreateFieldUiConfigWithInputSettings() {
        FieldUiConfig config = FieldUiConfig.builder()
            .inputType("textarea")
            .rows(5)
            .multiline(true)
            .build();

        assertThat(config.inputType()).isEqualTo("textarea");
        assertThat(config.rows()).isEqualTo(5);
        assertThat(config.multiline()).isTrue();
    }

    @Test
    @DisplayName("DE-32: FieldUiConfig with formatting settings")
    void shouldCreateFieldUiConfigWithFormattingSettings() {
        FieldUiConfig config = FieldUiConfig.builder()
            .format("MM/dd/yyyy")
            .prefix("$")
            .suffix("USD")
            .build();

        assertThat(config.format()).isEqualTo("MM/dd/yyyy");
        assertThat(config.prefix()).isEqualTo("$");
        assertThat(config.suffix()).isEqualTo("USD");
    }

    @Test
    @DisplayName("DE-33: FieldUiConfig with grouping settings")
    void shouldCreateFieldUiConfigWithGroupingSettings() {
        FieldUiConfig config = FieldUiConfig.builder()
            .section("Customer Info")
            .group("Personal Details")
            .build();

        assertThat(config.section()).isEqualTo("Customer Info");
        assertThat(config.group()).isEqualTo("Personal Details");
    }

    @Test
    @DisplayName("DE-34: FieldUiConfig with conditional display")
    void shouldCreateFieldUiConfigWithConditionalDisplay() {
        FieldUiConfig config = FieldUiConfig.builder()
            .showWhen("status == 'active'")
            .hideWhen("status == 'inactive'")
            .build();

        assertThat(config.showWhen()).isEqualTo("status == 'active'");
        assertThat(config.hideWhen()).isEqualTo("status == 'inactive'");
    }

    private static Entity sampleEntity(String tenantId, String collection) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("status", "NEW");
        payload.put("amount", 42);

        return Entity.builder()
            .id(UUID.randomUUID())
            .tenantId(tenantId)
            .collectionName(collection)
            .version(1)
            .active(true)
            .data(payload)
            .createdBy("integration-test")
            .build();
    }

    private static final class InMemoryTenantEntityRepository implements EntityRepository {
        private final Map<UUID, Entity> store = new ConcurrentHashMap<>();
        private final Map<String, Entity> idempotencyStore = new ConcurrentHashMap<>();

        @Override
        public Promise<Optional<Entity>> findById(String tenantId, String collectionName, UUID entityId) {
            return Promise.of(findIncludingInactive(tenantId, collectionName, entityId)
                .filter(entity -> Boolean.TRUE.equals(entity.getActive())));
        }

        Optional<Entity> findIncludingInactive(String tenantId, String collectionName, UUID entityId) {
            Entity entity = store.get(entityId);
            if (entity == null) {
                return Optional.empty();
            }
            if (!tenantId.equals(entity.getTenantId()) || !collectionName.equals(entity.getCollectionName())) {
                return Optional.empty();
            }
            return Optional.of(entity);
        }

        @Override
        public Promise<List<Entity>> findAll(String tenantId, String collectionName, Map<String, Object> filter, String sort, int offset, int limit) {
            List<Entity> result = new ArrayList<>();
            for (Entity entity : store.values()) {
                if (tenantId.equals(entity.getTenantId())
                    && collectionName.equals(entity.getCollectionName())
                    && Boolean.TRUE.equals(entity.getActive())) {
                    result.add(entity);
                }
            }
            return Promise.of(result);
        }

        @Override
        public Promise<Entity> save(String tenantId, Entity entity) {
            if (!tenantId.equals(entity.getTenantId())) {
                return Promise.ofException(new IllegalArgumentException("tenantId mismatch"));
            }

            Entity existing = store.get(entity.getId());
            if (existing != null && !existing.getVersion().equals(entity.getVersion())) {
                return Promise.ofException(new OptimisticLockException("Version conflict"));
            }

            Entity toSave;
            if (existing == null) {
                toSave = entity;
            } else {
                toSave = entity.toBuilder()
                    .version(existing.getVersion() + 1)
                    .build();
            }
            store.put(toSave.getId(), toSave);
            return Promise.of(toSave);
        }

        @Override
        public Promise<Entity> saveWithIdempotency(String tenantId, Entity entity, String idempotencyKey) {
            String key = tenantId + ":" + entity.getCollectionName() + ":" + idempotencyKey;
            Entity existing = idempotencyStore.get(key);
            if (existing != null) {
                return Promise.of(existing);
            }
            return save(tenantId, entity).map(saved -> {
                idempotencyStore.put(key, saved);
                return saved;
            });
        }

        @Override
        public Promise<Void> delete(String tenantId, String collectionName, UUID entityId) {
            Entity existing = store.get(entityId);
            if (existing != null
                && tenantId.equals(existing.getTenantId())
                && collectionName.equals(existing.getCollectionName())) {
                Entity deleted = existing.toBuilder().build();
                deleted.softDelete("system-soft-delete");
                store.put(entityId, deleted);
            }
            return Promise.complete();
        }

        @Override
        public Promise<Boolean> exists(String tenantId, String collectionName, UUID entityId) {
            return Promise.of(findIncludingInactive(tenantId, collectionName, entityId)
                .filter(entity -> Boolean.TRUE.equals(entity.getActive()))
                .isPresent());
        }

        @Override
        public Promise<Long> count(String tenantId, String collectionName) {
            long count = store.values().stream()
                .filter(entity -> tenantId.equals(entity.getTenantId()))
                .filter(entity -> collectionName.equals(entity.getCollectionName()))
                .filter(entity -> Boolean.TRUE.equals(entity.getActive()))
                .count();
            return Promise.of(count);
        }

        @Override
        public Promise<Long> countByFilter(String tenantId, String collectionName, Map<String, Object> filter) {
            return count(tenantId, collectionName);
        }

        @Override
        public Promise<List<Entity>> findByQuery(String tenantId, String collectionName, Object querySpec) {
            return findAll(tenantId, collectionName, Map.of(), null, 0, Integer.MAX_VALUE);
        }

        @Override
        public Promise<List<Entity>> saveAll(String tenantId, List<Entity> entities) {
            Promise<List<Entity>> promise = Promise.of(new ArrayList<>());
            for (Entity entity : entities) {
                promise = promise.then(saved -> save(tenantId, entity).map(item -> {
                    saved.add(item);
                    return saved;
                }));
            }
            return promise;
        }

        @Override
        public Promise<Void> deleteAll(String tenantId, String collectionName, List<UUID> entityIds) {
            Promise<Void> promise = Promise.complete();
            for (UUID entityId : entityIds) {
                promise = promise.then(() -> delete(tenantId, collectionName, entityId));
            }
            return promise;
        }
    }
}



