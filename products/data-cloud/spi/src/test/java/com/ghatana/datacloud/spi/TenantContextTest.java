/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.spi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link TenantContext}.
 */
@DisplayName("TenantContext")
class TenantContextTest {

    @Nested
    @DisplayName("of(tenantId) factory")
    class SimpleFactory {

        @Test
        void createWithTenantId() { // GH-90000
            TenantContext ctx = TenantContext.of("t-1");
            assertThat(ctx.tenantId()).isEqualTo("t-1");
            assertThat(ctx.workspaceId()).isEmpty(); // GH-90000
            assertThat(ctx.metadata()).isEmpty(); // GH-90000
        }

        @Test
        void rejectsNullTenantId() { // GH-90000
            assertThatThrownBy(() -> TenantContext.of(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        void rejectsBlankTenantId() { // GH-90000
            assertThatThrownBy(() -> TenantContext.of("  "))
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("blank");
        }
    }

    @Nested
    @DisplayName("of(tenantId, workspaceId) factory")
    class WithWorkspace {

        @Test
        void createWithWorkspace() { // GH-90000
            TenantContext ctx = TenantContext.of("t-1", "ws-1"); // GH-90000
            assertThat(ctx.workspaceId()).contains("ws-1");
        }

        @Test
        void nullWorkspaceIdBecomesEmpty() { // GH-90000
            TenantContext ctx = TenantContext.of("t-1", (String) null); // GH-90000
            assertThat(ctx.workspaceId()).isEmpty(); // GH-90000
        }
    }

    @Nested
    @DisplayName("of(tenantId, metadata) factory")
    class WithMetadata {

        @Test
        void createWithMetadata() { // GH-90000
            TenantContext ctx = TenantContext.of("t-1", Map.of("env", "prod")); // GH-90000
            assertThat(ctx.metadata()).containsEntry("env", "prod"); // GH-90000
        }

        @Test
        void nullMetadataBecomesEmpty() { // GH-90000
            TenantContext ctx = TenantContext.of("t-1", (Map<String, String>) null); // GH-90000
            assertThat(ctx.metadata()).isEmpty(); // GH-90000
        }

        @Test
        void metadataIsImmutable() { // GH-90000
            TenantContext ctx = TenantContext.of("t-1", Map.of("k", "v")); // GH-90000
            assertThatThrownBy(() -> ctx.metadata().put("new", "entry")) // GH-90000
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        }
    }

    @Nested
    @DisplayName("withMetadata()")
    class WithMetadataMethod {

        @Test
        void addsKeyToNewContext() { // GH-90000
            TenantContext ctx = TenantContext.of("t-1");
            TenantContext ctx2 = ctx.withMetadata("region", "us-east-1"); // GH-90000
            assertThat(ctx2.metadata()).containsEntry("region", "us-east-1"); // GH-90000
        }

        @Test
        void doesNotMutateOriginal() { // GH-90000
            TenantContext ctx = TenantContext.of("t-1");
            ctx.withMetadata("region", "us-east-1"); // GH-90000
            assertThat(ctx.metadata()).isEmpty(); // GH-90000
        }
    }

    @Nested
    @DisplayName("withWorkspace()")
    class WithWorkspaceMethod {

        @Test
        void setsWorkspace() { // GH-90000
            TenantContext ctx = TenantContext.of("t-1").withWorkspace("ws-2");
            assertThat(ctx.workspaceId()).contains("ws-2");
        }

        @Test
        void nullWorkspaceClearsIt() { // GH-90000
            TenantContext ctx = TenantContext.of("t-1", "ws-1").withWorkspace(null); // GH-90000
            assertThat(ctx.workspaceId()).isEmpty(); // GH-90000
        }
    }

    @Test
    @DisplayName("record equality and hashCode")
    void recordEquality() { // GH-90000
        TenantContext a = TenantContext.of("t-1", Map.of("k", "v")); // GH-90000
        TenantContext b = TenantContext.of("t-1", Map.of("k", "v")); // GH-90000
        assertThat(a).isEqualTo(b); // GH-90000
        assertThat(a.hashCode()).isEqualTo(b.hashCode()); // GH-90000
    }
}
