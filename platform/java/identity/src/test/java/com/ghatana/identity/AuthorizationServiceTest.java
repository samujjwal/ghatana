/*
 * Copyright (c) 2026 Ghatana Inc.
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
    void setUp() {
        resolver = new InMemoryIdentityResolver();
        identityService = new DefaultIdentityService(resolver);
        authzService = new DefaultAuthorizationService(identityService);

        // Register test agents with scopes
        AgentIdentity reader = new AgentIdentity("t1", "reader-agent",
            "spiffe://ghatana.io/t1/reader", Set.of("collection:read", "job:read"), Instant.now());
        resolver.register(reader);

        AgentIdentity writer = new AgentIdentity("t1", "writer-agent",
            "spiffe://ghatana.io/t1/writer", Set.of("collection:read", "collection:write", "job:execute"), Instant.now());
        resolver.register(writer);

        AgentIdentity admin = new AgentIdentity("t1", "admin-agent",
            "spiffe://ghatana.io/t1/admin", Set.of("*"), Instant.now());
        resolver.register(admin);
    }

    @Nested
    @DisplayName("isAuthorized()")
    class IsAuthorizedTests {

        @Test
        @DisplayName("Grants access when principal has required scope")
        void grantsAccessWithScope() {
            Boolean authorized = runPromise(() ->
                authzService.isAuthorized("t1", "reader-agent", "collection:read"));

            assertThat(authorized).isTrue();
        }

        @Test
        @DisplayName("Denies access when principal lacks required scope")
        void deniesAccessWithoutScope() {
            Boolean authorized = runPromise(() ->
                authzService.isAuthorized("t1", "reader-agent", "collection:write"));

            assertThat(authorized).isFalse();
        }

        @Test
        @DisplayName("Grants all access to agent with wildcard scope")
        void grantsWildcardAccess() {
            Boolean authorized = runPromise(() ->
                authzService.isAuthorized("t1", "admin-agent", "anything:resource"));

            assertThat(authorized).isTrue();
        }

        @Test
        @DisplayName("Denies access for unknown principal")
        void deniesUnknownPrincipal() {
            Boolean authorized = runPromise(() ->
                authzService.isAuthorized("t1", "unknown-agent", "collection:read"));

            assertThat(authorized).isFalse();
        }

        @Test
        @DisplayName("Enforces tenant isolation")
        void enforcesTenantIsolation() {
            Boolean authorized = runPromise(() ->
                authzService.isAuthorized("t2", "reader-agent", "collection:read"));

            assertThat(authorized).isFalse();
        }

        @Test
        @DisplayName("Handles multiple scopes correctly")
        void handlesMultipleScopes() {
            Boolean canRead = runPromise(() ->
                authzService.isAuthorized("t1", "writer-agent", "collection:read"));
            Boolean canWrite = runPromise(() ->
                authzService.isAuthorized("t1", "writer-agent", "collection:write"));
            Boolean canExecute = runPromise(() ->
                authzService.isAuthorized("t1", "writer-agent", "job:execute"));
            Boolean cannotDelete = runPromise(() ->
                authzService.isAuthorized("t1", "writer-agent", "collection:delete"));

            assertThat(canRead).isTrue();
            assertThat(canWrite).isTrue();
            assertThat(canExecute).isTrue();
            assertThat(cannotDelete).isFalse();
        }
    }

    @Nested
    @DisplayName("hasScope()")
    class HasScopeTests {

        @Test
        @DisplayName("Returns true when principal has scope")
        void returnsTrueForScope() {
            Boolean has = runPromise(() ->
                authzService.hasScope("t1", "reader-agent", "collection:read"));

            assertThat(has).isTrue();
        }

        @Test
        @DisplayName("Returns false when principal lacks scope")
        void returnsFalseForMissingScope() {
            Boolean has = runPromise(() ->
                authzService.hasScope("t1", "reader-agent", "collection:write"));

            assertThat(has).isFalse();
        }

        @Test
        @DisplayName("Returns true for wildcard scope holder")
        void returnsTrueForWildcard() {
            Boolean has = runPromise(() ->
                authzService.hasScope("t1", "admin-agent", "any:scope:here"));

            assertThat(has).isTrue();
        }

        @Test
        @DisplayName("Returns false for unknown principal")
        void returnsFalseForUnknownPrincipal() {
            Boolean has = runPromise(() ->
                authzService.hasScope("t1", "unknown-agent", "collection:read"));

            assertThat(has).isFalse();
        }

        @Test
        @DisplayName("Returns false for wrong tenant")
        void returnsFalseForWrongTenant() {
            Boolean has = runPromise(() ->
                authzService.hasScope("t2", "reader-agent", "collection:read"));

            assertThat(has).isFalse();
        }
    }

    @Nested
    @DisplayName("enforce()")
    class EnforceTests {

        @Test
        @DisplayName("Completes successfully when authorized")
        void completesWhenAuthorized() {
            Void result = runPromise(() ->
                authzService.enforce("t1", "reader-agent", "collection:read"));

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Throws AuthorizationDeniedException when denied")
        void throwsWhenDenied() {
            assertThatThrownBy(() -> runPromise(() ->
                authzService.enforce("t1", "reader-agent", "collection:delete")
            )).isInstanceOf(AuthorizationDeniedException.class);
        }

        @Test
        @DisplayName("Exception contains correct details")
        void exceptionHasDetails() {
            assertThatThrownBy(() -> runPromise(() ->
                authzService.enforce("t1", "reader-agent", "sensitive:operation")
            )).isInstanceOf(AuthorizationDeniedException.class)
                .satisfies(ex -> {
                    AuthorizationDeniedException authEx = (AuthorizationDeniedException) ex;
                    assertThat(authEx.principal()).isEqualTo("reader-agent");
                    assertThat(authEx.resource()).isEqualTo("sensitive:operation");
                    assertThat(authEx.tenantId()).isEqualTo("t1");
                });
        }

        @Test
        @DisplayName("Throws for unknown principal")
        void throwsForUnknownPrincipal() {
            assertThatThrownBy(() -> runPromise(() ->
                authzService.enforce("t1", "unknown-agent", "collection:read")
            )).isInstanceOf(AuthorizationDeniedException.class);
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Handles empty resource string")
        void handlesEmptyResource() {
            Boolean authorized = runPromise(() ->
                authzService.isAuthorized("t1", "admin-agent", ""));

            // Should not crash; behavior depends on parsing
            assertThat(authorized).isNotNull();
        }

        @Test
        @DisplayName("Handles malformed resource (no colon)")
        void handlesMalformedResource() {
            Boolean authorized = runPromise(() ->
                authzService.isAuthorized("t1", "reader-agent", "malformed-resource"));

            // Should not crash
            assertThat(authorized).isNotNull();
        }

        @Test
        @DisplayName("Case-sensitive scope matching")
        void caseSensitiveScopes() {
            Boolean authorized = runPromise(() ->
                authzService.isAuthorized("t1", "reader-agent", "Collection:Read"));

            assertThat(authorized).isFalse(); // Different case
        }

        @Test
        @DisplayName("Handles principal with no scopes")
        void handlesNoScopes() {
            // Create agent with empty scopes
            AgentIdentity noscope = new AgentIdentity("t1", "noscope-agent",
                "spiffe://ghatana.io/t1/noscope", Set.of(), Instant.now());
            resolver.register(noscope);

            Boolean authorized = runPromise(() ->
                authzService.isAuthorized("t1", "noscope-agent", "collection:read"));

            assertThat(authorized).isFalse();
        }
    }
}
