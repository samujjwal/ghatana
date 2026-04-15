/*
 * Copyright (c) 2026 Ghatana Inc.
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
        void createWithTenantId() {
            TenantContext ctx = TenantContext.of("t-1");
            assertThat(ctx.tenantId()).isEqualTo("t-1");
            assertThat(ctx.workspaceId()).isEmpty();
            assertThat(ctx.metadata()).isEmpty();
        }

        @Test
        void rejectsNullTenantId() {
            assertThatThrownBy(() -> TenantContext.of(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void rejectsBlankTenantId() {
            assertThatThrownBy(() -> TenantContext.of("  "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("blank");
        }
    }

    @Nested
    @DisplayName("of(tenantId, workspaceId) factory")
    class WithWorkspace {

        @Test
        void createWithWorkspace() {
            TenantContext ctx = TenantContext.of("t-1", "ws-1");
            assertThat(ctx.workspaceId()).contains("ws-1");
        }

        @Test
        void nullWorkspaceIdBecomesEmpty() {
            TenantContext ctx = TenantContext.of("t-1", (String) null);
            assertThat(ctx.workspaceId()).isEmpty();
        }
    }

    @Nested
    @DisplayName("of(tenantId, metadata) factory")
    class WithMetadata {

        @Test
        void createWithMetadata() {
            TenantContext ctx = TenantContext.of("t-1", Map.of("env", "prod"));
            assertThat(ctx.metadata()).containsEntry("env", "prod");
        }

        @Test
        void nullMetadataBecomesEmpty() {
            TenantContext ctx = TenantContext.of("t-1", (Map<String, String>) null);
            assertThat(ctx.metadata()).isEmpty();
        }

        @Test
        void metadataIsImmutable() {
            TenantContext ctx = TenantContext.of("t-1", Map.of("k", "v"));
            assertThatThrownBy(() -> ctx.metadata().put("new", "entry"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("withMetadata()")
    class WithMetadataMethod {

        @Test
        void addsKeyToNewContext() {
            TenantContext ctx = TenantContext.of("t-1");
            TenantContext ctx2 = ctx.withMetadata("region", "us-east-1");
            assertThat(ctx2.metadata()).containsEntry("region", "us-east-1");
        }

        @Test
        void doesNotMutateOriginal() {
            TenantContext ctx = TenantContext.of("t-1");
            ctx.withMetadata("region", "us-east-1");
            assertThat(ctx.metadata()).isEmpty();
        }
    }

    @Nested
    @DisplayName("withWorkspace()")
    class WithWorkspaceMethod {

        @Test
        void setsWorkspace() {
            TenantContext ctx = TenantContext.of("t-1").withWorkspace("ws-2");
            assertThat(ctx.workspaceId()).contains("ws-2");
        }

        @Test
        void nullWorkspaceClearsIt() {
            TenantContext ctx = TenantContext.of("t-1", "ws-1").withWorkspace(null);
            assertThat(ctx.workspaceId()).isEmpty();
        }
    }

    @Test
    @DisplayName("record equality and hashCode")
    void recordEquality() {
        TenantContext a = TenantContext.of("t-1", Map.of("k", "v"));
        TenantContext b = TenantContext.of("t-1", Map.of("k", "v"));
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
