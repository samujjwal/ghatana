package com.ghatana.platform.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SecurityContext} and {@link SecurityContext.Default}.
 *
 * @doc.type class
 * @doc.purpose SecurityContext unit tests
 * @doc.layer core
 * @doc.pattern Unit Test
 */
@DisplayName("SecurityContext.Default")
class SecurityContextTest {

    @Nested
    @DisplayName("authenticated context")
    class AuthenticatedContext {

        private final SecurityContext ctx = new SecurityContext.Default(
                "user-123", "tenant-456",
                Set.of("ADMIN", "READER"),
                Set.of("event:read:all", "event:write:all")
        );

        @Test
        @DisplayName("should return user ID")
        void shouldReturnUserId() {
            assertThat(ctx.getUserId()).contains("user-123");
        }

        @Test
        @DisplayName("should return tenant ID")
        void shouldReturnTenantId() {
            assertThat(ctx.getTenantId()).contains("tenant-456");
        }

        @Test
        @DisplayName("should return roles as immutable set")
        void shouldReturnRoles() {
            assertThat(ctx.getRoles()).containsExactlyInAnyOrder("ADMIN", "READER");
        }

        @Test
        @DisplayName("should return permissions as immutable set")
        void shouldReturnPermissions() {
            assertThat(ctx.getPermissions()).containsExactlyInAnyOrder("event:read:all", "event:write:all");
        }

        @Test
        @DisplayName("should be authenticated")
        void shouldBeAuthenticated() {
            assertThat(ctx.isAuthenticated()).isTrue();
        }
    }

    @Nested
    @DisplayName("unauthenticated context")
    class UnauthenticatedContext {

        private final SecurityContext ctx = new SecurityContext.Default(
                null, null, null, null
        );

        @Test
        @DisplayName("should return empty user ID")
        void shouldReturnEmptyUserId() {
            assertThat(ctx.getUserId()).isEmpty();
        }

        @Test
        @DisplayName("should return empty tenant ID")
        void shouldReturnEmptyTenantId() {
            assertThat(ctx.getTenantId()).isEmpty();
        }

        @Test
        @DisplayName("should return empty roles")
        void shouldReturnEmptyRoles() {
            assertThat(ctx.getRoles()).isEmpty();
        }

        @Test
        @DisplayName("should return empty permissions")
        void shouldReturnEmptyPermissions() {
            assertThat(ctx.getPermissions()).isEmpty();
        }

        @Test
        @DisplayName("should not be authenticated")
        void shouldNotBeAuthenticated() {
            assertThat(ctx.isAuthenticated()).isFalse();
        }
    }

    @Nested
    @DisplayName("partial context")
    class PartialContext {

        @Test
        @DisplayName("should handle user with no tenant")
        void shouldHandleNoTenant() {
            SecurityContext ctx = new SecurityContext.Default("user-1", null, Set.of("READER"), Set.of());

            assertThat(ctx.getUserId()).contains("user-1");
            assertThat(ctx.getTenantId()).isEmpty();
            assertThat(ctx.isAuthenticated()).isTrue();
        }

        @Test
        @DisplayName("should handle tenant with no user")
        void shouldHandleTenantNoUser() {
            SecurityContext ctx = new SecurityContext.Default(null, "tenant-1", Set.of(), Set.of());

            assertThat(ctx.getUserId()).isEmpty();
            assertThat(ctx.getTenantId()).contains("tenant-1");
            assertThat(ctx.isAuthenticated()).isFalse();
        }
    }
}
