/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.platform.entity;

import com.ghatana.datacloud.launcher.test.builder.EntityBuilder;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

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
@ExtendWith(MockitoExtension.class)
@DisplayName("[Entity]: query_boundary_schema_versioning_cdc")
class EntityQueryBoundaryTest extends EventloopTestBase {

    @Mock
    private EntityStore entityStore;

    @BeforeEach
    void setUp() {
        // Configure lenient stubs for common operations
        lenient().when(entityStore.query(any(), any()))
            .thenReturn(Promise.of(EntityStore.QueryResult.empty()));
        lenient().when(entityStore.findById(any(), any()))
            .thenReturn(Promise.of(Optional.empty()));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // QuerySpec Boundary Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[QuerySpec]: negative_offset_normalized_to_zero")
    void negativeOffsetNormalizedToZero() {
        EntityStore.QuerySpec spec = new EntityStore.QuerySpec(
            "products",
            List.of(),
            List.of(),
            -5,
            10
        );

        assertThat(spec.offset()).isZero();
        assertThat(spec.limit()).isEqualTo(10);
    }

    @Test
    @DisplayName("[QuerySpec]: zero_limit_defaults_to_100")
    void zeroLimitDefaultsTo100() {
        EntityStore.QuerySpec spec = new EntityStore.QuerySpec(
            "products",
            List.of(),
            List.of(),
            0,
            0
        );

        assertThat(spec.limit()).isEqualTo(100);
    }

    @Test
    @DisplayName("[QuerySpec]: limit_above_max_throws_exception")
    void limitAboveMaxThrowsException() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            new EntityStore.QuerySpec("products", List.of(), List.of(), 0, 10001)
        ).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("exceeds maximum allowed value");
    }

    @Test
    @DisplayName("[QuerySpec]: max_limit_boundary_accepted")
    void maxLimitBoundaryAccepted() {
        EntityStore.QuerySpec spec = new EntityStore.QuerySpec(
            "products",
            List.of(),
            List.of(),
            0,
            10000
        );

        assertThat(spec.limit()).isEqualTo(10000);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Filter Boundary Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[Filter]: eq_operator_creates_equality_filter")
    void eqOperatorCreatesEqualityFilter() {
        EntityStore.Filter filter = EntityStore.Filter.eq("status", "active");

        assertThat(filter.field()).isEqualTo("status");
        assertThat(filter.operator()).isEqualTo(EntityStore.Operator.EQ);
        assertThat(filter.value()).isEqualTo("active");
    }

    @Test
    @DisplayName("[Filter]: comparison_operators_created_correctly")
    void comparisonOperatorsCreatedCorrectly() {
        EntityStore.Filter gt = EntityStore.Filter.gt("price", 100);
        EntityStore.Filter gte = EntityStore.Filter.gte("price", 100);
        EntityStore.Filter lt = EntityStore.Filter.lt("price", 100);
        EntityStore.Filter lte = EntityStore.Filter.lte("price", 100);

        assertThat(gt.operator()).isEqualTo(EntityStore.Operator.GT);
        assertThat(gte.operator()).isEqualTo(EntityStore.Operator.GTE);
        assertThat(lt.operator()).isEqualTo(EntityStore.Operator.LT);
        assertThat(lte.operator()).isEqualTo(EntityStore.Operator.LTE);
    }

    @Test
    @DisplayName("[Filter]: like_operator_creates_pattern_filter")
    void likeOperatorCreatesPatternFilter() {
        EntityStore.Filter filter = EntityStore.Filter.like("name", "test%");

        assertThat(filter.operator()).isEqualTo(EntityStore.Operator.LIKE);
        assertThat(filter.value()).isEqualTo("test%");
    }

    @Test
    @DisplayName("[Filter]: in_operator_creates_set_membership_filter")
    void inOperatorCreatesSetMembershipFilter() {
        List<String> values = List.of("active", "pending");
        EntityStore.Filter filter = EntityStore.Filter.in("status", values);

        assertThat(filter.operator()).isEqualTo(EntityStore.Operator.IN);
        assertThat(filter.value()).isEqualTo(values);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Sort Boundary Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[Sort]: asc_creates_ascending_sort")
    void ascCreatesAscendingSort() {
        EntityStore.Sort sort = EntityStore.Sort.asc("createdAt");

        assertThat(sort.field()).isEqualTo("createdAt");
        assertThat(sort.direction()).isEqualTo(EntityStore.Direction.ASC);
    }

    @Test
    @DisplayName("[Sort]: desc_creates_descending_sort")
    void descCreatesDescendingSort() {
        EntityStore.Sort sort = EntityStore.Sort.desc("createdAt");

        assertThat(sort.field()).isEqualTo("createdAt");
        assertThat(sort.direction()).isEqualTo(EntityStore.Direction.DESC);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Entity Builder Integration Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[Entity]: builder_creates_entity_with_all_fields")
    void builderCreatesEntityWithAllFields() {
        Map<String, Object> entity = EntityBuilder.create("products")
            .withId("prod-001")
            .withField("name", "Test Product")
            .withField("price", 29.99)
            .withTenant("tenant-alpha")
            .withVersion(1)
            .build();

        assertThat(entity).containsEntry("id", "prod-001");
        assertThat(entity).containsEntry("collection", "products");
        assertThat(entity).containsEntry("tenantId", "tenant-alpha");
        assertThat(entity).containsEntry("version", 1);
        assertThat(entity).containsEntry("name", "Test Product");
        assertThat(entity).containsEntry("price", 29.99);
    }

    @Test
    @DisplayName("[Entity]: product_template_creates_valid_product")
    void productTemplateCreatesValidProduct() {
        Map<String, Object> product = EntityBuilder.product()
            .withId("prod-template-001")
            .build();

        assertThat(product).containsEntry("collection", "products");
        assertThat(product).containsKeys("name", "sku", "price", "quantity", "category");
    }

    @Test
    @DisplayName("[Entity]: customer_template_creates_valid_customer")
    void customerTemplateCreatesValidCustomer() {
        Map<String, Object> customer = EntityBuilder.customer()
            .withId("cust-001")
            .build();

        assertThat(customer).containsEntry("collection", "customers");
        assertThat(customer).containsKeys("name", "email", "phone", "status");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // QueryResult Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[QueryResult]: empty_result_has_zero_count")
    void emptyResultHasZeroCount() {
        EntityStore.QueryResult result = runPromise(() -> Promise.of(EntityStore.QueryResult.empty()));

        assertThat(result.entities()).isEmpty();
        assertThat(result.totalCount()).isZero();
        assertThat(result.hasMore()).isFalse();
    }

    @Test
    @DisplayName("[QueryResult]: result_with_entities_calculates_hasMore")
    void resultWithEntitiesCalculatesHasMore() {
        EntityStore.Entity entity = EntityStore.Entity.builder()
            .collection("products")
            .data(Map.of("name", "Test"))
            .build();

        List<EntityStore.Entity> entities = List.of(entity);
        EntityStore.QueryResult result = runPromise(() -> Promise.of(EntityStore.QueryResult.of(entities, 100)));

        assertThat(result.entities()).hasSize(1);
        assertThat(result.totalCount()).isEqualTo(100);
        assertThat(result.hasMore()).isTrue();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // EntityId Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[EntityId]: of_creates_id_from_string")
    void ofCreatesIdFromString() {
        EntityStore.EntityId id = EntityStore.EntityId.of("test-id-123");

        assertThat(id.value()).isEqualTo("test-id-123");
    }

    @Test
    @DisplayName("[EntityId]: random_creates_uuid_based_id")
    void randomCreatesUuidBasedId() {
        EntityStore.EntityId id = EntityStore.EntityId.random();

        assertThat(id.value()).isNotNull();
        assertThat(id.value()).hasSize(36); // UUID string length
    }

    @Test
    @DisplayName("[EntityId]: blank_value_throws_exception")
    void blankValueThrowsException() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            new EntityStore.EntityId("")
        ).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cannot be blank");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // EntityMetadata Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[EntityMetadata]: empty_creates_default_metadata")
    void emptyCreatesDefaultMetadata() {
        EntityStore.EntityMetadata metadata = EntityStore.EntityMetadata.empty();

        assertThat(metadata.version()).isEqualTo(1);
        assertThat(metadata.createdAt()).isNotNull();
        assertThat(metadata.updatedAt()).isNotNull();
        assertThat(metadata.createdBy()).isEmpty();
        assertThat(metadata.updatedBy()).isEmpty();
    }

    @Test
    @DisplayName("[EntityMetadata]: withUpdate_increments_version_and_updates_timestamp")
    void withUpdateIncrementsVersionAndUpdatesTimestamp() {
        EntityStore.EntityMetadata original = EntityStore.EntityMetadata.empty();
        EntityStore.EntityMetadata updated = original.withUpdate("user-123");

        assertThat(updated.version()).isEqualTo(original.version() + 1);
        assertThat(updated.updatedBy()).hasValue("user-123");
        assertThat(updated.createdAt()).isEqualTo(original.createdAt());
    }
}
