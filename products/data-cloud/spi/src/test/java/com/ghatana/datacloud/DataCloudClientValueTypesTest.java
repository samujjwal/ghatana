/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link DataCloudClient} inner record types.
 */
@DisplayName("DataCloudClient value types [GH-90000]")
class DataCloudClientValueTypesTest {

    // ─── Entity ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Entity [GH-90000]")
    class EntityTest {

        @Test
        void factoryCreatesEntity() { // GH-90000
            DataCloudClient.Entity en = DataCloudClient.Entity.of("e1", "orders", Map.of("a", 1)); // GH-90000
            assertThat(en.id()).isEqualTo("e1 [GH-90000]");
            assertThat(en.collection()).isEqualTo("orders [GH-90000]");
            assertThat(en.data()).containsEntry("a", 1); // GH-90000
            assertThat(en.version()).isEqualTo(1); // GH-90000
        }

        @Test
        void nullDataDefaultsToEmptyMap() { // GH-90000
            DataCloudClient.Entity en = new DataCloudClient.Entity("e2", "coll", null, Instant.now(), Instant.now(), 1); // GH-90000
            assertThat(en.data()).isEmpty(); // GH-90000
        }
    }

    // ─── Query ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Query [GH-90000]")
    class QueryTest {

        @Test
        void queryAllDefaults() { // GH-90000
            DataCloudClient.Query q = DataCloudClient.Query.all(); // GH-90000
            assertThat(q.filters()).isEmpty(); // GH-90000
            assertThat(q.limit()).isEqualTo(100); // GH-90000
        }

        @Test
        void queryLimit() { // GH-90000
            DataCloudClient.Query q = DataCloudClient.Query.limit(25); // GH-90000
            assertThat(q.limit()).isEqualTo(25); // GH-90000
        }

        @Test
        void builderFiltersAndSorts() { // GH-90000
            DataCloudClient.Query q = DataCloudClient.Query.builder() // GH-90000
                    .filter(DataCloudClient.Filter.eq("status", "active")) // GH-90000
                    .limit(50) // GH-90000
                    .offset(10) // GH-90000
                    .build(); // GH-90000
            assertThat(q.filters()).hasSize(1); // GH-90000
            assertThat(q.limit()).isEqualTo(50); // GH-90000
            assertThat(q.offset()).isEqualTo(10); // GH-90000
        }

        @Test
        void builderSortsAccepted() { // GH-90000
            DataCloudClient.Query q = DataCloudClient.Query.builder() // GH-90000
                    .sorts(List.of(DataCloudClient.Sort.asc("name [GH-90000]"), DataCloudClient.Sort.desc("createdAt [GH-90000]")))
                    .build(); // GH-90000
            assertThat(q.sorts()).hasSize(2); // GH-90000
        }
    }

    // ─── Filter ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Filter [GH-90000]")
    class FilterTest {

        @Test
        void eqFilter() { // GH-90000
            DataCloudClient.Filter f = DataCloudClient.Filter.eq("age", 30); // GH-90000
            assertThat(f.field()).isEqualTo("age [GH-90000]");
            assertThat(f.operator()).isEqualTo("eq [GH-90000]");
            assertThat(f.value()).isEqualTo(30); // GH-90000
        }

        @Test
        void neFilter() { // GH-90000
            assertThat(DataCloudClient.Filter.ne("x", "a").operator()).isEqualTo("ne [GH-90000]");
        }

        @Test
        void gtFilter() { // GH-90000
            assertThat(DataCloudClient.Filter.gt("score", 5).operator()).isEqualTo("gt [GH-90000]");
        }

        @Test
        void gteFilter() { // GH-90000
            assertThat(DataCloudClient.Filter.gte("score", 5).operator()).isEqualTo("gte [GH-90000]");
        }

        @Test
        void ltFilter() { // GH-90000
            assertThat(DataCloudClient.Filter.lt("score", 10).operator()).isEqualTo("lt [GH-90000]");
        }

        @Test
        void lteFilter() { // GH-90000
            assertThat(DataCloudClient.Filter.lte("score", 10).operator()).isEqualTo("lte [GH-90000]");
        }

        @Test
        void likeFilter() { // GH-90000
            assertThat(DataCloudClient.Filter.like("name", "%smith%").operator()).isEqualTo("like [GH-90000]");
        }
    }

    // ─── Sort ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Sort [GH-90000]")
    class SortTest {

        @Test
        void ascSort() { // GH-90000
            DataCloudClient.Sort s = DataCloudClient.Sort.asc("name [GH-90000]");
            assertThat(s.field()).isEqualTo("name [GH-90000]");
            assertThat(s.ascending()).isTrue(); // GH-90000
        }

        @Test
        void descSort() { // GH-90000
            DataCloudClient.Sort s = DataCloudClient.Sort.desc("createdAt [GH-90000]");
            assertThat(s.ascending()).isFalse(); // GH-90000
        }
    }

    // ─── Event ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Event [GH-90000]")
    class EventTest {

        @Test
        void factoryCreatesEvent() { // GH-90000
            DataCloudClient.Event ev = DataCloudClient.Event.of("OrderCreated", Map.of("orderId", "o1")); // GH-90000
            assertThat(ev.type()).isEqualTo("OrderCreated [GH-90000]");
            assertThat(ev.payload()).containsEntry("orderId", "o1"); // GH-90000
            assertThat(ev.headers()).isEmpty(); // GH-90000
            assertThat(ev.timestamp()).isNotNull(); // GH-90000
        }
    }
}
