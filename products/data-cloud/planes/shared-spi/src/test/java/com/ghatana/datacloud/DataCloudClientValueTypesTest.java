/*
 * Copyright (c) 2026 Ghatana Inc. 
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
@DisplayName("DataCloudClient value types")
class DataCloudClientValueTypesTest {

    // ─── Entity ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Entity")
    class EntityTest {

        @Test
        void factoryCreatesEntity() { 
            DataCloudClient.Entity en = DataCloudClient.Entity.of("e1", "orders", Map.of("a", 1)); 
            assertThat(en.id()).isEqualTo("e1");
            assertThat(en.collection()).isEqualTo("orders");
            assertThat(en.data()).containsEntry("a", 1); 
            assertThat(en.version()).isEqualTo(1); 
        }

        @Test
        void nullDataDefaultsToEmptyMap() { 
            DataCloudClient.Entity en = new DataCloudClient.Entity("e2", "coll", null, Instant.now(), Instant.now(), 1); 
            assertThat(en.data()).isEmpty(); 
        }
    }

    // ─── Query ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Query")
    class QueryTest {

        @Test
        void queryAllDefaults() { 
            DataCloudClient.Query q = DataCloudClient.Query.all(); 
            assertThat(q.filters()).isEmpty(); 
            assertThat(q.limit()).isEqualTo(100); 
        }

        @Test
        void queryLimit() { 
            DataCloudClient.Query q = DataCloudClient.Query.limit(25); 
            assertThat(q.limit()).isEqualTo(25); 
        }

        @Test
        void builderFiltersAndSorts() { 
            DataCloudClient.Query q = DataCloudClient.Query.builder() 
                    .filter(DataCloudClient.Filter.eq("status", "active")) 
                    .limit(50) 
                    .offset(10) 
                    .build(); 
            assertThat(q.filters()).hasSize(1); 
            assertThat(q.limit()).isEqualTo(50); 
            assertThat(q.offset()).isEqualTo(10); 
        }

        @Test
        void builderSortsAccepted() { 
            DataCloudClient.Query q = DataCloudClient.Query.builder() 
                    .sorts(List.of(DataCloudClient.Sort.asc("name"), DataCloudClient.Sort.desc("createdAt")))
                    .build(); 
            assertThat(q.sorts()).hasSize(2); 
        }
    }

    // ─── Filter ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Filter")
    class FilterTest {

        @Test
        void eqFilter() { 
            DataCloudClient.Filter f = DataCloudClient.Filter.eq("age", 30); 
            assertThat(f.field()).isEqualTo("age");
            assertThat(f.operator()).isEqualTo("eq");
            assertThat(f.value()).isEqualTo(30); 
        }

        @Test
        void neFilter() { 
            assertThat(DataCloudClient.Filter.ne("x", "a").operator()).isEqualTo("ne");
        }

        @Test
        void gtFilter() { 
            assertThat(DataCloudClient.Filter.gt("score", 5).operator()).isEqualTo("gt");
        }

        @Test
        void gteFilter() { 
            assertThat(DataCloudClient.Filter.gte("score", 5).operator()).isEqualTo("gte");
        }

        @Test
        void ltFilter() { 
            assertThat(DataCloudClient.Filter.lt("score", 10).operator()).isEqualTo("lt");
        }

        @Test
        void lteFilter() { 
            assertThat(DataCloudClient.Filter.lte("score", 10).operator()).isEqualTo("lte");
        }

        @Test
        void likeFilter() { 
            assertThat(DataCloudClient.Filter.like("name", "%smith%").operator()).isEqualTo("like");
        }
    }

    // ─── Sort ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Sort")
    class SortTest {

        @Test
        void ascSort() { 
            DataCloudClient.Sort s = DataCloudClient.Sort.asc("name");
            assertThat(s.field()).isEqualTo("name");
            assertThat(s.ascending()).isTrue(); 
        }

        @Test
        void descSort() { 
            DataCloudClient.Sort s = DataCloudClient.Sort.desc("createdAt");
            assertThat(s.ascending()).isFalse(); 
        }
    }

    // ─── Event ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Event")
    class EventTest {

        @Test
        void factoryCreatesEvent() { 
            DataCloudClient.Event ev = DataCloudClient.Event.builder()
                .type("OrderCreated")
                .payload(Map.of("orderId", "o1"))
                .build(); 
            assertThat(ev.type()).isEqualTo("OrderCreated");
            assertThat(ev.payload()).containsEntry("orderId", "o1"); 
            assertThat(ev.headers()).isEmpty(); 
            assertThat(ev.timestamp()).isNotNull(); 
        }
    }
}
