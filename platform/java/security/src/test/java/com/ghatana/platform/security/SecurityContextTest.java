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

    @Nested
    @DisplayName("using test fixtures and mock factory")
    class FixtureAndMockFactoryTests {

        @Test
        @DisplayName("should create admin context with fixture")
        void createAdminContextWithFixture() { 
            SecurityContext context = SecurityTestFixture.securityContext() 
                .admin() 
                .build(); 

            assertThat(context.isAuthenticated()).isTrue(); 
            assertThat(context.getRoles()).containsExactly("ADMIN");
            assertThat(context.getPermissions()).containsExactlyInAnyOrder("read", "write", "delete", "admin"); 
        }

        @Test
        @DisplayName("should create viewer context with fixture preset")
        void createViewerContextWithFixture() { 
            SecurityContext context = SecurityTestFixture.securityContext() 
                .viewer() 
                .build(); 

            assertThat(context.isAuthenticated()).isTrue(); 
            assertThat(context.getRoles()).containsExactly("VIEWER");
            assertThat(context.getPermissions()).containsExactly("read");
        }

        @Test
        @DisplayName("should create admin context via mock factory")
        void createAdminContextViaMockFactory() { 
            SecurityContext context = SecurityMockFactory.adminContext(); 

            assertThat(context.isAuthenticated()).isTrue(); 
            assertThat(context.getRoles()).containsExactly("ADMIN");
            assertThat(context.getPermissions()).containsExactlyInAnyOrder("read", "write", "delete", "admin"); 
        }

        @Test
        @DisplayName("should create user context via mock factory")
        void createUserContextViaMockFactory() { 
            SecurityContext context = SecurityMockFactory.userContext(); 

            assertThat(context.isAuthenticated()).isTrue(); 
            assertThat(context.getRoles()).containsExactly("USER");
            assertThat(context.getPermissions()).containsExactly("read");
        }

        @Test
        @DisplayName("should create unauthenticated context via mock factory")
        void createUnauthenticatedContextViaMockFactory() { 
            SecurityContext context = SecurityMockFactory.unauthenticatedContext(); 

            assertThat(context.isAuthenticated()).isFalse(); 
            assertThat(context.getUserId()).isEmpty(); 
            assertThat(context.getRoles()).isEmpty(); 
            assertThat(context.getPermissions()).isEmpty(); 
        }
    }

    @Nested
    @DisplayName("fluent builder patterns")
    class FluentBuilderTests {

        @Test
        @DisplayName("should build custom role set")
        void buildCustomRoleSet() { 
            SecurityContext context = SecurityTestFixture.securityContext() 
                .userId("editor-1")
                .roles("EDITOR", "CONTRIBUTOR") 
                .permissions("read", "write", "comment") 
                .build(); 

            assertThat(context.getUserId()).hasValue("editor-1");
            assertThat(context.getRoles()).containsExactlyInAnyOrder("EDITOR", "CONTRIBUTOR"); 
            assertThat(context.getPermissions()).containsExactlyInAnyOrder("read", "write", "comment"); 
        }

        @Test
        @DisplayName("should override tenant in builder")
        void overrideTenantInBuilder() { 
            SecurityContext context = SecurityTestFixture.securityContext() 
                .tenantId("custom-tenant")
                .build(); 

            assertThat(context.getTenantId()).hasValue("custom-tenant");
        }

        @Test
        @DisplayName("should build with multiple roles")
        void buildWithMultipleRoles() { 
            SecurityContext context = SecurityTestFixture.securityContext() 
                .roles("ADMIN", "AUDITOR", "OPERATOR") 
                .build(); 

            Set<String> roles = context.getRoles(); 

            assertThat(roles).containsExactlyInAnyOrder("ADMIN", "AUDITOR", "OPERATOR"); 
        }
    }

    @Nested
    @DisplayName("immutability guarantees")
    class ImmutabilityTests {

        @Test
        @DisplayName("should return immutable role set")
        void roleSetIsImmutable() { 
            SecurityContext ctx = new SecurityContext.Default( 
                "user-1", "tenant-1",
                Set.of("ADMIN", "USER"), 
                Set.of("read", "write") 
            );

            Set<String> roles1 = ctx.getRoles(); 
            Set<String> roles2 = ctx.getRoles(); 

            // Should be equal but potentially different instances
            assertThat(roles1).isEqualTo(roles2); 
            assertThat(roles1).containsExactlyInAnyOrder("ADMIN", "USER"); 
        }

        @Test
        @DisplayName("should return immutable permission set")
        void permissionSetIsImmutable() { 
            SecurityContext ctx = new SecurityContext.Default( 
                "user-1", "tenant-1",
                Set.of("ADMIN"),
                Set.of("read", "write", "delete") 
            );

            Set<String> perms1 = ctx.getPermissions(); 
            Set<String> perms2 = ctx.getPermissions(); 

            assertThat(perms1).isEqualTo(perms2); 
            assertThat(perms1).containsExactlyInAnyOrder("read", "write", "delete"); 
        }
    }
}
