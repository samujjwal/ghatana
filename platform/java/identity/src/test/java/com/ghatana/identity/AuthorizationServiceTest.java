/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.identity;

import com.ghatana.identity.spi.InMemoryIdentityResolver;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive tests for {@link DefaultAuthorizationService}.
 *
 * @doc.type class
 * @doc.purpose Tests for RBAC enforcement, scope validation, authorization decisions
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("DefaultAuthorizationService")
class AuthorizationServiceTest extends EventloopTestBase {

    private DefaultAuthorizationService authzService;
    private DefaultIdentityService identityService;
    private InMemoryIdentityResolver resolver;

    @BeforeEach
    void setUp() { // GH-90000
        resolver = new InMemoryIdentityResolver(); // GH-90000
        identityService = new DefaultIdentityService(resolver); // GH-90000
        authzService = new DefaultAuthorizationService(identityService); // GH-90000

        // Register test agents with scopes
        AgentIdentity reader = new AgentIdentity("t1", "reader-agent", // GH-90000
            "spiffe://ghatana.io/t1/reader", Set.of("collection:read", "job:read"), Instant.now()); // GH-90000
        resolver.register(reader); // GH-90000

        AgentIdentity writer = new AgentIdentity("t1", "writer-agent", // GH-90000
            "spiffe://ghatana.io/t1/writer", Set.of("collection:read", "collection:write", "job:execute"), Instant.now()); // GH-90000
        resolver.register(writer); // GH-90000

        AgentIdentity admin = new AgentIdentity("t1", "admin-agent", // GH-90000
            "spiffe://ghatana.io/t1/admin", Set.of("*"), Instant.now());
        resolver.register(admin); // GH-90000
    }

    @Nested
    @DisplayName("isAuthorized()")
    class IsAuthorizedTests {

        @Test
        @DisplayName("Grants access when principal has required scope")
        void grantsAccessWithScope() { // GH-90000
            Boolean authorized = runPromise(() -> // GH-90000
                authzService.isAuthorized("t1", "reader-agent", "collection:read")); // GH-90000

            assertThat(authorized).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("Denies access when principal lacks required scope")
        void deniesAccessWithoutScope() { // GH-90000
            Boolean authorized = runPromise(() -> // GH-90000
                authzService.isAuthorized("t1", "reader-agent", "collection:write")); // GH-90000

            assertThat(authorized).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("Grants all access to agent with wildcard scope")
        void grantsWildcardAccess() { // GH-90000
            Boolean authorized = runPromise(() -> // GH-90000
                authzService.isAuthorized("t1", "admin-agent", "anything:resource")); // GH-90000

            assertThat(authorized).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("Denies access for unknown principal")
        void deniesUnknownPrincipal() { // GH-90000
            Boolean authorized = runPromise(() -> // GH-90000
                authzService.isAuthorized("t1", "unknown-agent", "collection:read")); // GH-90000

            assertThat(authorized).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("Enforces tenant isolation")
        void enforcesTenantIsolation() { // GH-90000
            Boolean authorized = runPromise(() -> // GH-90000
                authzService.isAuthorized("t2", "reader-agent", "collection:read")); // GH-90000

            assertThat(authorized).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("Handles multiple scopes correctly")
        void handlesMultipleScopes() { // GH-90000
            Boolean canRead = runPromise(() -> // GH-90000
                authzService.isAuthorized("t1", "writer-agent", "collection:read")); // GH-90000
            Boolean canWrite = runPromise(() -> // GH-90000
                authzService.isAuthorized("t1", "writer-agent", "collection:write")); // GH-90000
            Boolean canExecute = runPromise(() -> // GH-90000
                authzService.isAuthorized("t1", "writer-agent", "job:execute")); // GH-90000
            Boolean cannotDelete = runPromise(() -> // GH-90000
                authzService.isAuthorized("t1", "writer-agent", "collection:delete")); // GH-90000

            assertThat(canRead).isTrue(); // GH-90000
            assertThat(canWrite).isTrue(); // GH-90000
            assertThat(canExecute).isTrue(); // GH-90000
            assertThat(cannotDelete).isFalse(); // GH-90000
        }
    }

    @Nested
    @DisplayName("hasScope()")
    class HasScopeTests {

        @Test
        @DisplayName("Returns true when principal has scope")
        void returnsTrueForScope() { // GH-90000
            Boolean has = runPromise(() -> // GH-90000
                authzService.hasScope("t1", "reader-agent", "collection:read")); // GH-90000

            assertThat(has).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("Returns false when principal lacks scope")
        void returnsFalseForMissingScope() { // GH-90000
            Boolean has = runPromise(() -> // GH-90000
                authzService.hasScope("t1", "reader-agent", "collection:write")); // GH-90000

            assertThat(has).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("Returns true for wildcard scope holder")
        void returnsTrueForWildcard() { // GH-90000
            Boolean has = runPromise(() -> // GH-90000
                authzService.hasScope("t1", "admin-agent", "any:scope:here")); // GH-90000

            assertThat(has).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("Returns false for unknown principal")
        void returnsFalseForUnknownPrincipal() { // GH-90000
            Boolean has = runPromise(() -> // GH-90000
                authzService.hasScope("t1", "unknown-agent", "collection:read")); // GH-90000

            assertThat(has).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("Returns false for wrong tenant")
        void returnsFalseForWrongTenant() { // GH-90000
            Boolean has = runPromise(() -> // GH-90000
                authzService.hasScope("t2", "reader-agent", "collection:read")); // GH-90000

            assertThat(has).isFalse(); // GH-90000
        }
    }

    @Nested
    @DisplayName("enforce()")
    class EnforceTests {

        @Test
        @DisplayName("Completes successfully when authorized")
        void completesWhenAuthorized() { // GH-90000
            Void result = runPromise(() -> // GH-90000
                authzService.enforce("t1", "reader-agent", "collection:read")); // GH-90000

            assertThat(result).isNull(); // GH-90000
        }

        @Test
        @DisplayName("Throws AuthorizationDeniedException when denied")
        void throwsWhenDenied() { // GH-90000
            assertThatThrownBy(() -> runPromise(() -> // GH-90000
                authzService.enforce("t1", "reader-agent", "collection:delete") // GH-90000
            )).isInstanceOf(AuthorizationDeniedException.class); // GH-90000
        }

        @Test
        @DisplayName("Exception contains correct details")
        void exceptionHasDetails() { // GH-90000
            assertThatThrownBy(() -> runPromise(() -> // GH-90000
                authzService.enforce("t1", "reader-agent", "sensitive:operation") // GH-90000
            )).isInstanceOf(AuthorizationDeniedException.class) // GH-90000
                .satisfies(ex -> { // GH-90000
                    AuthorizationDeniedException authEx = (AuthorizationDeniedException) ex; // GH-90000
                    assertThat(authEx.principal()).isEqualTo("reader-agent");
                    assertThat(authEx.resource()).isEqualTo("sensitive:operation");
                    assertThat(authEx.tenantId()).isEqualTo("t1");
                });
        }

        @Test
        @DisplayName("Throws for unknown principal")
        void throwsForUnknownPrincipal() { // GH-90000
            assertThatThrownBy(() -> runPromise(() -> // GH-90000
                authzService.enforce("t1", "unknown-agent", "collection:read") // GH-90000
            )).isInstanceOf(AuthorizationDeniedException.class); // GH-90000
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Handles empty resource string")
        void handlesEmptyResource() { // GH-90000
            Boolean authorized = runPromise(() -> // GH-90000
                authzService.isAuthorized("t1", "admin-agent", "")); // GH-90000

            // Should not crash; behavior depends on parsing
            assertThat(authorized).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("Handles malformed resource (no colon)")
        void handlesMalformedResource() { // GH-90000
            Boolean authorized = runPromise(() -> // GH-90000
                authzService.isAuthorized("t1", "reader-agent", "malformed-resource")); // GH-90000

            // Should not crash
            assertThat(authorized).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("Case-sensitive scope matching")
        void caseSensitiveScopes() { // GH-90000
            Boolean authorized = runPromise(() -> // GH-90000
                authzService.isAuthorized("t1", "reader-agent", "Collection:Read")); // GH-90000

            assertThat(authorized).isFalse(); // Different case // GH-90000
        }

        @Test
        @DisplayName("Handles principal with no scopes")
        void handlesNoScopes() { // GH-90000
            // Create agent with empty scopes
            AgentIdentity noscope = new AgentIdentity("t1", "noscope-agent", // GH-90000
                "spiffe://ghatana.io/t1/noscope", Set.of(), Instant.now()); // GH-90000
            resolver.register(noscope); // GH-90000

            Boolean authorized = runPromise(() -> // GH-90000
                authzService.isAuthorized("t1", "noscope-agent", "collection:read")); // GH-90000

            assertThat(authorized).isFalse(); // GH-90000
        }
    }
}
