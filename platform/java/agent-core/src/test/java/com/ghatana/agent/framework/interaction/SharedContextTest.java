/*
 * Copyright (c) 2026 Ghatana Inc.
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
        void generatesId() {
            var ctx = SharedContext.create("ns", "key", "value", "tenant-1", ContextSharingScope.SESSION);
            assertThat(ctx.entryId()).isNotBlank();
        }

        @Test
        @DisplayName("sets all fields correctly")
        void setsFields() {
            var ctx = SharedContext.create("ns1", "k1", "v1", "t1", ContextSharingScope.CONVERSATION);
            assertThat(ctx.namespace()).isEqualTo("ns1");
            assertThat(ctx.key()).isEqualTo("k1");
            assertThat(ctx.value()).isEqualTo("v1");
            assertThat(ctx.tenantId()).isEqualTo("t1");
            assertThat(ctx.scope()).isEqualTo(ContextSharingScope.CONVERSATION);
            assertThat(ctx.createdAt()).isNotNull();
        }

        @Test
        @DisplayName("null value creates a tombstone entry")
        void nullValueIsTombstone() {
            var ctx = SharedContext.create("ns", "key", null, "t", ContextSharingScope.TENANT);
            assertThat(ctx.isDeleted()).isTrue();
        }

        @Test
        @DisplayName("non-null value is not deleted")
        void nonNullIsNotDeleted() {
            var ctx = SharedContext.create("ns", "key", 42, "t", ContextSharingScope.COMPOSITION_GROUP);
            assertThat(ctx.isDeleted()).isFalse();
        }

        @Test
        @DisplayName("each call generates unique entryId")
        void uniqueIds() {
            var c1 = SharedContext.create("ns", "key", "v", "t", ContextSharingScope.SESSION);
            var c2 = SharedContext.create("ns", "key", "v", "t", ContextSharingScope.SESSION);
            assertThat(c1.entryId()).isNotEqualTo(c2.entryId());
        }
    }

    @Nested
    @DisplayName("ContextSharingScope")
    class ScopeTest {

        @Test
        @DisplayName("all four scopes exist")
        void allScopes() {
            assertThat(ContextSharingScope.values())
                    .containsExactlyInAnyOrder(
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
        void rejectsNullNamespace() {
            assertThatThrownBy(() -> SharedContext.create(null, "k", "v", "t", ContextSharingScope.SESSION))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("null tenantId throws NPE")
        void rejectsNullTenant() {
            assertThatThrownBy(() -> SharedContext.create("ns", "k", "v", null, ContextSharingScope.SESSION))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
