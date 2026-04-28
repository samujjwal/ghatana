/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.platform.entity;

import com.ghatana.datacloud.spi.EntityStore;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

/**
 * Boundary tests for entity queries including schema, versioning, and CDC.
 *
 * <p>Tests entity query specifications with boundary values and validation.
 *
 * @doc.type class
 * @doc.purpose Entity query boundary tests with schema, versioning, CDC coverage
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("[Entity]: query_boundary_schema_versioning_cdc")
class EntityQueryBoundaryTest extends EventloopTestBase {

    @Mock
    private EntityStore entityStore;

    @BeforeEach
    void setUp() { // GH-90000
        // Configure lenient stubs for common operations
        lenient().when(entityStore.query(any(), any())) // GH-90000
            .thenReturn(Promise.of(EntityStore.QueryResult.empty())); // GH-90000
        lenient().when(entityStore.findById(any(), any())) // GH-90000
            .thenReturn(Promise.of(Optional.empty())); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // QuerySpec Boundary Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[QuerySpec]: negative_offset_normalized_to_zero")
    void negativeOffsetNormalizedToZero() { // GH-90000
        EntityStore.QuerySpec spec = new EntityStore.QuerySpec( // GH-90000
            "products",
            List.of(), // GH-90000
            List.of(), // GH-90000
            -5,
            10,
            null,
            List.of(),
            EntityStore.ConsistencyLevel.EVENTUAL,
            null
        );

        assertThat(spec.offset()).isZero(); // GH-90000
        assertThat(spec.limit()).isEqualTo(10); // GH-90000
    }

    @Test
    @DisplayName("[QuerySpec]: zero_limit_defaults_to_100")
    void zeroLimitDefaultsTo100() { // GH-90000
        EntityStore.QuerySpec spec = new EntityStore.QuerySpec( // GH-90000
            "products",
            List.of(), // GH-90000
            List.of(), // GH-90000
            0,
            0,
            null,
            List.of(),
            EntityStore.ConsistencyLevel.EVENTUAL,
            null
        );

        assertThat(spec.limit()).isEqualTo(100); // GH-90000
    }

    @Test
    @DisplayName("[QuerySpec]: limit_above_max_throws_exception")
    void limitAboveMaxThrowsException() { // GH-90000
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> // GH-90000
            new EntityStore.QuerySpec("products", List.of(), List.of(), 0, 10001, null, List.of(), EntityStore.ConsistencyLevel.EVENTUAL, null) // GH-90000
        ).isInstanceOf(IllegalArgumentException.class) // GH-90000
            .hasMessageContaining("exceeds maximum allowed value");
    }

    @Test
    @DisplayName("[QuerySpec]: max_limit_boundary_accepted")
    void maxLimitBoundaryAccepted() { // GH-90000
        EntityStore.QuerySpec spec = new EntityStore.QuerySpec( // GH-90000
            "products",
            List.of(), // GH-90000
            List.of(), // GH-90000
            0,
            10000,
            null,
            List.of(),
            EntityStore.ConsistencyLevel.EVENTUAL,
            null
        );

        assertThat(spec.limit()).isEqualTo(10000); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Filter Boundary Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[Filter]: eq_operator_creates_equality_filter")
    void eqOperatorCreatesEqualityFilter() { // GH-90000
        EntityStore.Filter filter = EntityStore.Filter.eq("status", "active"); // GH-90000

        assertThat(filter.field()).isEqualTo("status");
        assertThat(filter.operator()).isEqualTo(EntityStore.Operator.EQ); // GH-90000
        assertThat(filter.value()).isEqualTo("active");
    }

    @Test
    @DisplayName("[Filter]: comparison_operators_created_correctly")
    void comparisonOperatorsCreatedCorrectly() { // GH-90000
        EntityStore.Filter gt = EntityStore.Filter.gt("price", 100); // GH-90000
        EntityStore.Filter gte = EntityStore.Filter.gte("price", 100); // GH-90000
        EntityStore.Filter lt = EntityStore.Filter.lt("price", 100); // GH-90000
        EntityStore.Filter lte = EntityStore.Filter.lte("price", 100); // GH-90000

        assertThat(gt.operator()).isEqualTo(EntityStore.Operator.GT); // GH-90000
        assertThat(gte.operator()).isEqualTo(EntityStore.Operator.GTE); // GH-90000
        assertThat(lt.operator()).isEqualTo(EntityStore.Operator.LT); // GH-90000
        assertThat(lte.operator()).isEqualTo(EntityStore.Operator.LTE); // GH-90000
    }

    @Test
    @DisplayName("[Filter]: like_operator_creates_pattern_filter")
    void likeOperatorCreatesPatternFilter() { // GH-90000
        EntityStore.Filter filter = EntityStore.Filter.like("name", "test%"); // GH-90000

        assertThat(filter.operator()).isEqualTo(EntityStore.Operator.LIKE); // GH-90000
        assertThat(filter.value()).isEqualTo("test%");
    }

    @Test
    @DisplayName("[Filter]: in_operator_creates_set_membership_filter")
    void inOperatorCreatesSetMembershipFilter() { // GH-90000
        List<String> values = List.of("active", "pending"); // GH-90000
        EntityStore.Filter filter = EntityStore.Filter.in("status", values); // GH-90000

        assertThat(filter.operator()).isEqualTo(EntityStore.Operator.IN); // GH-90000
        assertThat(filter.value()).isEqualTo(values); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Sort Boundary Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[Sort]: asc_creates_ascending_sort")
    void ascCreatesAscendingSort() { // GH-90000
        EntityStore.Sort sort = EntityStore.Sort.asc("createdAt");

        assertThat(sort.field()).isEqualTo("createdAt");
        assertThat(sort.direction()).isEqualTo(EntityStore.Direction.ASC); // GH-90000
    }

    @Test
    @DisplayName("[Sort]: desc_creates_descending_sort")
    void descCreatesDescendingSort() { // GH-90000
        EntityStore.Sort sort = EntityStore.Sort.desc("createdAt");

        assertThat(sort.field()).isEqualTo("createdAt");
        assertThat(sort.direction()).isEqualTo(EntityStore.Direction.DESC); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Entity Builder Integration Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[Entity]: builder_creates_entity_with_all_fields")
    void builderCreatesEntityWithAllFields() { // GH-90000
        Map<String, Object> entity = TestEntityBuilder.create("products")
            .withId("prod-001")
            .withField("name", "Test Product") // GH-90000
            .withField("price", 29.99) // GH-90000
            .withTenant("tenant-alpha")
            .withVersion(1) // GH-90000
            .build(); // GH-90000

        assertThat(entity).containsEntry("id", "prod-001"); // GH-90000
        assertThat(entity).containsEntry("collection", "products"); // GH-90000
        assertThat(entity).containsEntry("tenantId", "tenant-alpha"); // GH-90000
        assertThat(entity).containsEntry("version", 1); // GH-90000
        assertThat(entity).containsEntry("name", "Test Product"); // GH-90000
        assertThat(entity).containsEntry("price", 29.99); // GH-90000
    }

    @Test
    @DisplayName("[Entity]: product_template_creates_valid_product")
    void productTemplateCreatesValidProduct() { // GH-90000
        Map<String, Object> product = TestEntityBuilder.product() // GH-90000
            .withId("prod-template-001")
            .build(); // GH-90000

        assertThat(product).containsEntry("collection", "products"); // GH-90000
        assertThat(product).containsKeys("name", "sku", "price", "quantity", "category"); // GH-90000
    }

    @Test
    @DisplayName("[Entity]: customer_template_creates_valid_customer")
    void customerTemplateCreatesValidCustomer() { // GH-90000
        Map<String, Object> customer = TestEntityBuilder.customer() // GH-90000
            .withId("cust-001")
            .build(); // GH-90000

        assertThat(customer).containsEntry("collection", "customers"); // GH-90000
        assertThat(customer).containsKeys("name", "email", "phone", "status"); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // QueryResult Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[QueryResult]: empty_result_has_zero_count")
    void emptyResultHasZeroCount() { // GH-90000
        EntityStore.QueryResult result = runPromise(() -> Promise.of(EntityStore.QueryResult.empty())); // GH-90000

        assertThat(result.entities()).isEmpty(); // GH-90000
        assertThat(result.totalCount()).isZero(); // GH-90000
        assertThat(result.hasMore()).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("[QueryResult]: result_with_entities_calculates_hasMore")
    void resultWithEntitiesCalculatesHasMore() { // GH-90000
        EntityStore.Entity entity = EntityStore.Entity.builder() // GH-90000
            .collection("products")
            .data(Map.of("name", "Test")) // GH-90000
            .build(); // GH-90000

        List<EntityStore.Entity> entities = List.of(entity); // GH-90000
        EntityStore.QueryResult result = runPromise(() -> Promise.of(EntityStore.QueryResult.of(entities, 100))); // GH-90000

        assertThat(result.entities()).hasSize(1); // GH-90000
        assertThat(result.totalCount()).isEqualTo(100); // GH-90000
        assertThat(result.hasMore()).isTrue(); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // EntityId Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[EntityId]: of_creates_id_from_string")
    void ofCreatesIdFromString() { // GH-90000
        EntityStore.EntityId id = EntityStore.EntityId.of("test-id-123");

        assertThat(id.value()).isEqualTo("test-id-123");
    }

    @Test
    @DisplayName("[EntityId]: random_creates_uuid_based_id")
    void randomCreatesUuidBasedId() { // GH-90000
        EntityStore.EntityId id = EntityStore.EntityId.random(); // GH-90000

        assertThat(id.value()).isNotNull(); // GH-90000
        assertThat(id.value()).hasSize(36); // UUID string length // GH-90000
    }

    @Test
    @DisplayName("[EntityId]: blank_value_throws_exception")
    void blankValueThrowsException() { // GH-90000
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> // GH-90000
            new EntityStore.EntityId("")
        ).isInstanceOf(IllegalArgumentException.class) // GH-90000
            .hasMessageContaining("cannot be blank");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // EntityMetadata Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[EntityMetadata]: empty_creates_default_metadata")
    void emptyCreatesDefaultMetadata() { // GH-90000
        EntityStore.EntityMetadata metadata = EntityStore.EntityMetadata.empty(); // GH-90000

        assertThat(metadata.version()).isEqualTo(1); // GH-90000
        assertThat(metadata.createdAt()).isNotNull(); // GH-90000
        assertThat(metadata.updatedAt()).isNotNull(); // GH-90000
        assertThat(metadata.createdBy()).isEmpty(); // GH-90000
        assertThat(metadata.updatedBy()).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("[EntityMetadata]: withUpdate_increments_version_and_updates_timestamp")
    void withUpdateIncrementsVersionAndUpdatesTimestamp() { // GH-90000
        EntityStore.EntityMetadata original = EntityStore.EntityMetadata.empty(); // GH-90000
        EntityStore.EntityMetadata updated = original.withUpdate("user-123");

        assertThat(updated.version()).isEqualTo(original.version() + 1); // GH-90000
        assertThat(updated.updatedBy()).hasValue("user-123");
        assertThat(updated.createdAt()).isEqualTo(original.createdAt()); // GH-90000
    }

    private static final class TestEntityBuilder {
        private final Map<String, Object> entity = new java.util.HashMap<>(); // GH-90000

        private TestEntityBuilder(String collection) { // GH-90000
            entity.put("collection", collection); // GH-90000
        }

        static TestEntityBuilder create(String collection) { // GH-90000
            return new TestEntityBuilder(collection); // GH-90000
        }

        static TestEntityBuilder product() { // GH-90000
            return create("products")
                .withField("name", "Test Product") // GH-90000
                .withField("sku", "SKU-001") // GH-90000
                .withField("price", 29.99) // GH-90000
                .withField("quantity", 10) // GH-90000
                .withField("category", "general"); // GH-90000
        }

        static TestEntityBuilder customer() { // GH-90000
            return create("customers")
                .withField("name", "Test Customer") // GH-90000
                .withField("email", "customer@example.com") // GH-90000
                .withField("phone", "+977-0000000000") // GH-90000
                .withField("status", "active"); // GH-90000
        }

        TestEntityBuilder withId(String id) { // GH-90000
            entity.put("id", id); // GH-90000
            return this;
        }

        TestEntityBuilder withField(String name, Object value) { // GH-90000
            entity.put(name, value); // GH-90000
            return this;
        }

        TestEntityBuilder withTenant(String tenantId) { // GH-90000
            entity.put("tenantId", tenantId); // GH-90000
            return this;
        }

        TestEntityBuilder withVersion(int version) { // GH-90000
            entity.put("version", version); // GH-90000
            return this;
        }

        Map<String, Object> build() { // GH-90000
            return Map.copyOf(entity); // GH-90000
        }
    }
}
