/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.agent.framework.interaction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("SharedContext value type")
class SharedContextTest {

    @Nested
    @DisplayName("create factory")
    class Create {

        @Test
        @DisplayName("generates a non-blank entryId")
        void generatesId() { // GH-90000
            var ctx = SharedContext.create("ns", "key", "value", "tenant-1", ContextSharingScope.SESSION); // GH-90000
            assertThat(ctx.entryId()).isNotBlank(); // GH-90000
        }

        @Test
        @DisplayName("sets all fields correctly")
        void setsFields() { // GH-90000
            var ctx = SharedContext.create("ns1", "k1", "v1", "t1", ContextSharingScope.CONVERSATION); // GH-90000
            assertThat(ctx.namespace()).isEqualTo("ns1");
            assertThat(ctx.key()).isEqualTo("k1");
            assertThat(ctx.value()).isEqualTo("v1");
            assertThat(ctx.tenantId()).isEqualTo("t1");
            assertThat(ctx.scope()).isEqualTo(ContextSharingScope.CONVERSATION); // GH-90000
            assertThat(ctx.createdAt()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("null value creates a tombstone entry")
        void nullValueIsTombstone() { // GH-90000
            var ctx = SharedContext.create("ns", "key", null, "t", ContextSharingScope.TENANT); // GH-90000
            assertThat(ctx.isDeleted()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("non-null value is not deleted")
        void nonNullIsNotDeleted() { // GH-90000
            var ctx = SharedContext.create("ns", "key", 42, "t", ContextSharingScope.COMPOSITION_GROUP); // GH-90000
            assertThat(ctx.isDeleted()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("each call generates unique entryId")
        void uniqueIds() { // GH-90000
            var c1 = SharedContext.create("ns", "key", "v", "t", ContextSharingScope.SESSION); // GH-90000
            var c2 = SharedContext.create("ns", "key", "v", "t", ContextSharingScope.SESSION); // GH-90000
            assertThat(c1.entryId()).isNotEqualTo(c2.entryId()); // GH-90000
        }
    }

    @Nested
    @DisplayName("ContextSharingScope")
    class ScopeTest {

        @Test
        @DisplayName("all four scopes exist")
        void allScopes() { // GH-90000
            assertThat(ContextSharingScope.values()) // GH-90000
                    .containsExactlyInAnyOrder( // GH-90000
                            ContextSharingScope.CONVERSATION,
                            ContextSharingScope.SESSION,
                            ContextSharingScope.COMPOSITION_GROUP,
                            ContextSharingScope.TENANT);
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName("null namespace throws NPE")
        void rejectsNullNamespace() { // GH-90000
            assertThatThrownBy(() -> SharedContext.create(null, "k", "v", "t", ContextSharingScope.SESSION)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("null tenantId throws NPE")
        void rejectsNullTenant() { // GH-90000
            assertThatThrownBy(() -> SharedContext.create("ns", "k", "v", null, ContextSharingScope.SESSION)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }
}
