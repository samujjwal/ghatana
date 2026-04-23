package com.ghatana.platform.security;

import com.ghatana.platform.security.fixtures.SecurityMockFactory;
import com.ghatana.platform.security.fixtures.SecurityTestFixture;
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

        private final SecurityContext ctx = new SecurityContext.Default( // GH-90000
                "user-123", "tenant-456",
                Set.of("ADMIN", "READER"), // GH-90000
                Set.of("event:read:all", "event:write:all") // GH-90000
        );

        @Test
        @DisplayName("should return user ID")
        void shouldReturnUserId() { // GH-90000
            assertThat(ctx.getUserId()).contains("user-123");
        }

        @Test
        @DisplayName("should return tenant ID")
        void shouldReturnTenantId() { // GH-90000
            assertThat(ctx.getTenantId()).contains("tenant-456");
        }

        @Test
        @DisplayName("should return roles as immutable set")
        void shouldReturnRoles() { // GH-90000
            assertThat(ctx.getRoles()).containsExactlyInAnyOrder("ADMIN", "READER"); // GH-90000
        }

        @Test
        @DisplayName("should return permissions as immutable set")
        void shouldReturnPermissions() { // GH-90000
            assertThat(ctx.getPermissions()).containsExactlyInAnyOrder("event:read:all", "event:write:all"); // GH-90000
        }

        @Test
        @DisplayName("should be authenticated")
        void shouldBeAuthenticated() { // GH-90000
            assertThat(ctx.isAuthenticated()).isTrue(); // GH-90000
        }
    }

    @Nested
    @DisplayName("unauthenticated context")
    class UnauthenticatedContext {

        private final SecurityContext ctx = new SecurityContext.Default( // GH-90000
                null, null, null, null
        );

        @Test
        @DisplayName("should return empty user ID")
        void shouldReturnEmptyUserId() { // GH-90000
            assertThat(ctx.getUserId()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should return empty tenant ID")
        void shouldReturnEmptyTenantId() { // GH-90000
            assertThat(ctx.getTenantId()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should return empty roles")
        void shouldReturnEmptyRoles() { // GH-90000
            assertThat(ctx.getRoles()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should return empty permissions")
        void shouldReturnEmptyPermissions() { // GH-90000
            assertThat(ctx.getPermissions()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should not be authenticated")
        void shouldNotBeAuthenticated() { // GH-90000
            assertThat(ctx.isAuthenticated()).isFalse(); // GH-90000
        }
    }

    @Nested
    @DisplayName("partial context")
    class PartialContext {

        @Test
        @DisplayName("should handle user with no tenant")
        void shouldHandleNoTenant() { // GH-90000
            SecurityContext ctx = new SecurityContext.Default("user-1", null, Set.of("READER"), Set.of());

            assertThat(ctx.getUserId()).contains("user-1");
            assertThat(ctx.getTenantId()).isEmpty(); // GH-90000
            assertThat(ctx.isAuthenticated()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should handle tenant with no user")
        void shouldHandleTenantNoUser() { // GH-90000
            SecurityContext ctx = new SecurityContext.Default(null, "tenant-1", Set.of(), Set.of()); // GH-90000

            assertThat(ctx.getUserId()).isEmpty(); // GH-90000
            assertThat(ctx.getTenantId()).contains("tenant-1");
            assertThat(ctx.isAuthenticated()).isFalse(); // GH-90000
        }
    }

    @Nested
    @DisplayName("using test fixtures and mock factory")
    class FixtureAndMockFactoryTests {

        @Test
        @DisplayName("should create admin context with fixture")
        void createAdminContextWithFixture() { // GH-90000
            SecurityContext context = SecurityTestFixture.securityContext() // GH-90000
                .admin() // GH-90000
                .build(); // GH-90000

            assertThat(context.isAuthenticated()).isTrue(); // GH-90000
            assertThat(context.getRoles()).containsExactly("ADMIN");
            assertThat(context.getPermissions()).containsExactlyInAnyOrder("read", "write", "delete", "admin"); // GH-90000
        }

        @Test
        @DisplayName("should create viewer context with fixture preset")
        void createViewerContextWithFixture() { // GH-90000
            SecurityContext context = SecurityTestFixture.securityContext() // GH-90000
                .viewer() // GH-90000
                .build(); // GH-90000

            assertThat(context.isAuthenticated()).isTrue(); // GH-90000
            assertThat(context.getRoles()).containsExactly("VIEWER");
            assertThat(context.getPermissions()).containsExactly("read");
        }

        @Test
        @DisplayName("should create admin context via mock factory")
        void createAdminContextViaMockFactory() { // GH-90000
            SecurityContext context = SecurityMockFactory.adminContext(); // GH-90000

            assertThat(context.isAuthenticated()).isTrue(); // GH-90000
            assertThat(context.getRoles()).containsExactly("ADMIN");
            assertThat(context.getPermissions()).containsExactlyInAnyOrder("read", "write", "delete", "admin"); // GH-90000
        }

        @Test
        @DisplayName("should create user context via mock factory")
        void createUserContextViaMockFactory() { // GH-90000
            SecurityContext context = SecurityMockFactory.userContext(); // GH-90000

            assertThat(context.isAuthenticated()).isTrue(); // GH-90000
            assertThat(context.getRoles()).containsExactly("USER");
            assertThat(context.getPermissions()).containsExactly("read");
        }

        @Test
        @DisplayName("should create unauthenticated context via mock factory")
        void createUnauthenticatedContextViaMockFactory() { // GH-90000
            SecurityContext context = SecurityMockFactory.unauthenticatedContext(); // GH-90000

            assertThat(context.isAuthenticated()).isFalse(); // GH-90000
            assertThat(context.getUserId()).isEmpty(); // GH-90000
            assertThat(context.getRoles()).isEmpty(); // GH-90000
            assertThat(context.getPermissions()).isEmpty(); // GH-90000
        }
    }

    @Nested
    @DisplayName("fluent builder patterns")
    class FluentBuilderTests {

        @Test
        @DisplayName("should build custom role set")
        void buildCustomRoleSet() { // GH-90000
            SecurityContext context = SecurityTestFixture.securityContext() // GH-90000
                .userId("editor-1")
                .roles("EDITOR", "CONTRIBUTOR") // GH-90000
                .permissions("read", "write", "comment") // GH-90000
                .build(); // GH-90000

            assertThat(context.getUserId()).hasValue("editor-1");
            assertThat(context.getRoles()).containsExactlyInAnyOrder("EDITOR", "CONTRIBUTOR"); // GH-90000
            assertThat(context.getPermissions()).containsExactlyInAnyOrder("read", "write", "comment"); // GH-90000
        }

        @Test
        @DisplayName("should override tenant in builder")
        void overrideTenantInBuilder() { // GH-90000
            SecurityContext context = SecurityTestFixture.securityContext() // GH-90000
                .tenantId("custom-tenant")
                .build(); // GH-90000

            assertThat(context.getTenantId()).hasValue("custom-tenant");
        }

        @Test
        @DisplayName("should build with multiple roles")
        void buildWithMultipleRoles() { // GH-90000
            SecurityContext context = SecurityTestFixture.securityContext() // GH-90000
                .roles("ADMIN", "AUDITOR", "OPERATOR") // GH-90000
                .build(); // GH-90000

            Set<String> roles = context.getRoles(); // GH-90000

            assertThat(roles).containsExactlyInAnyOrder("ADMIN", "AUDITOR", "OPERATOR"); // GH-90000
        }
    }

    @Nested
    @DisplayName("immutability guarantees")
    class ImmutabilityTests {

        @Test
        @DisplayName("should return immutable role set")
        void roleSetIsImmutable() { // GH-90000
            SecurityContext ctx = new SecurityContext.Default( // GH-90000
                "user-1", "tenant-1",
                Set.of("ADMIN", "USER"), // GH-90000
                Set.of("read", "write") // GH-90000
            );

            Set<String> roles1 = ctx.getRoles(); // GH-90000
            Set<String> roles2 = ctx.getRoles(); // GH-90000

            // Should be equal but potentially different instances
            assertThat(roles1).isEqualTo(roles2); // GH-90000
            assertThat(roles1).containsExactlyInAnyOrder("ADMIN", "USER"); // GH-90000
        }

        @Test
        @DisplayName("should return immutable permission set")
        void permissionSetIsImmutable() { // GH-90000
            SecurityContext ctx = new SecurityContext.Default( // GH-90000
                "user-1", "tenant-1",
                Set.of("ADMIN"),
                Set.of("read", "write", "delete") // GH-90000
            );

            Set<String> perms1 = ctx.getPermissions(); // GH-90000
            Set<String> perms2 = ctx.getPermissions(); // GH-90000

            assertThat(perms1).isEqualTo(perms2); // GH-90000
            assertThat(perms1).containsExactlyInAnyOrder("read", "write", "delete"); // GH-90000
        }
    }
}
