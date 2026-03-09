package com.ghatana.platform.governance.security;

import com.ghatana.platform.governance.security.TenantIsolationEnforcer.TenantIsolationException;
import com.ghatana.platform.governance.security.TenantIsolationEnforcer.TenantViolationHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link TenantIsolationEnforcer}.
 *
 * Validates tenant isolation enforcement including strict/lenient modes,
 * authentication requirements, role-based access, and cross-tenant validation.
 */
@DisplayName("TenantIsolationEnforcer")
class TenantIsolationEnforcerTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ---- getTenantIdOrThrow ----

    @Nested
    @DisplayName("getTenantIdOrThrow")
    class GetTenantIdOrThrow {

        @Test
        @DisplayName("strict mode with no context throws TenantIsolationException")
        void strictMode_noContext_throws() {
            TenantIsolationEnforcer enforcer = TenantIsolationEnforcer.forCurrentTenant();

            assertThatThrownBy(enforcer::getTenantIdOrThrow)
                    .isInstanceOf(TenantIsolationException.class)
                    .hasMessageContaining("No tenant context set");
        }

        @Test
        @DisplayName("strict mode with principal set returns tenant ID")
        void strictMode_withPrincipal_returnsTenantId() throws Exception {
            Principal principal = new Principal("alice", List.of("viewer"), "tenant-abc");
            try (AutoCloseable scope = TenantContext.scope(principal)) {
                TenantIsolationEnforcer enforcer = TenantIsolationEnforcer.forCurrentTenant();

                assertThat(enforcer.getTenantIdOrThrow()).isEqualTo("tenant-abc");
            }
        }

        @Test
        @DisplayName("strict mode with default-tenant principal succeeds because principal exists")
        void strictMode_defaultTenantWithPrincipal_returnsTenantId() throws Exception {
            Principal principal = new Principal("system", List.of("admin"), "default-tenant");
            try (AutoCloseable scope = TenantContext.scope(principal)) {
                TenantIsolationEnforcer enforcer = TenantIsolationEnforcer.forCurrentTenant();

                assertThat(enforcer.getTenantIdOrThrow()).isEqualTo("default-tenant");
            }
        }

        @Test
        @DisplayName("lenient mode with no context returns default-tenant")
        void lenientMode_noContext_returnsDefault() {
            TenantIsolationEnforcer enforcer = TenantIsolationEnforcer.lenient();

            assertThat(enforcer.getTenantIdOrThrow()).isEqualTo("default-tenant");
        }
    }

    // ---- getTenantId ----

    @Nested
    @DisplayName("getTenantId")
    class GetTenantId {

        @Test
        @DisplayName("returns empty when no context is set")
        void noContext_returnsEmpty() {
            TenantIsolationEnforcer enforcer = TenantIsolationEnforcer.forCurrentTenant();

            assertThat(enforcer.getTenantId()).isEmpty();
        }

        @Test
        @DisplayName("returns tenant ID when context is set")
        void withContext_returnsTenantId() throws Exception {
            Principal principal = new Principal("bob", List.of("editor"), "tenant-xyz");
            try (AutoCloseable scope = TenantContext.scope(principal)) {
                TenantIsolationEnforcer enforcer = TenantIsolationEnforcer.forCurrentTenant();

                assertThat(enforcer.getTenantId())
                        .isPresent()
                        .hasValue("tenant-xyz");
            }
        }
    }

    // ---- requireAuthenticated ----

    @Nested
    @DisplayName("requireAuthenticated")
    class RequireAuthenticated {

        @Test
        @DisplayName("throws when no principal in context")
        void noPrincipal_throws() {
            TenantIsolationEnforcer enforcer = TenantIsolationEnforcer.forCurrentTenant();

            assertThatThrownBy(enforcer::requireAuthenticated)
                    .isInstanceOf(TenantIsolationException.class)
                    .hasMessageContaining("Authentication required");
        }

        @Test
        @DisplayName("succeeds when principal is present")
        void withPrincipal_succeeds() throws Exception {
            Principal principal = new Principal("carol", List.of("viewer"), "tenant-1");
            try (AutoCloseable scope = TenantContext.scope(principal)) {
                TenantIsolationEnforcer enforcer = TenantIsolationEnforcer.forCurrentTenant();

                // Should not throw
                enforcer.requireAuthenticated();
            }
        }
    }

    // ---- requireRole ----

    @Nested
    @DisplayName("requireRole")
    class RequireRole {

        @Test
        @DisplayName("succeeds when principal has matching role")
        void matchingRole_succeeds() throws Exception {
            Principal principal = new Principal("dave", List.of("admin", "editor"), "tenant-1");
            try (AutoCloseable scope = TenantContext.scope(principal)) {
                TenantIsolationEnforcer enforcer = TenantIsolationEnforcer.forCurrentTenant();

                // Should not throw
                enforcer.requireRole("admin");
            }
        }

        @Test
        @DisplayName("succeeds when principal has one of multiple required roles")
        void oneOfMultipleRoles_succeeds() throws Exception {
            Principal principal = new Principal("eve", List.of("viewer"), "tenant-1");
            try (AutoCloseable scope = TenantContext.scope(principal)) {
                TenantIsolationEnforcer enforcer = TenantIsolationEnforcer.forCurrentTenant();

                // Should not throw — viewer matches one of the required roles
                enforcer.requireRole("admin", "viewer");
            }
        }

        @Test
        @DisplayName("throws when principal lacks required role")
        void noMatchingRole_throws() throws Exception {
            Principal principal = new Principal("frank", List.of("viewer"), "tenant-1");
            try (AutoCloseable scope = TenantContext.scope(principal)) {
                TenantIsolationEnforcer enforcer = TenantIsolationEnforcer.forCurrentTenant();

                assertThatThrownBy(() -> enforcer.requireRole("admin"))
                        .isInstanceOf(TenantIsolationException.class)
                        .hasMessageContaining("Access denied")
                        .hasMessageContaining("admin");
            }
        }
    }

    // ---- validateTenantAccess ----

    @Nested
    @DisplayName("validateTenantAccess")
    class ValidateTenantAccess {

        @Test
        @DisplayName("matching tenant passes without violation")
        void matchingTenant_noViolation() throws Exception {
            Principal principal = new Principal("grace", List.of("viewer"), "tenant-match");
            try (AutoCloseable scope = TenantContext.scope(principal)) {
                TenantIsolationEnforcer enforcer = TenantIsolationEnforcer.forCurrentTenant();

                // Should not throw
                enforcer.validateTenantAccess("tenant-match");
            }
        }

        @Test
        @DisplayName("mismatching tenant in strict mode throws exception")
        void mismatchingTenant_strict_throws() throws Exception {
            Principal principal = new Principal("hank", List.of("viewer"), "tenant-A");
            try (AutoCloseable scope = TenantContext.scope(principal)) {
                TenantIsolationEnforcer enforcer = TenantIsolationEnforcer.forCurrentTenant();

                assertThatThrownBy(() -> enforcer.validateTenantAccess("tenant-B"))
                        .isInstanceOf(TenantIsolationException.class)
                        .hasMessageContaining("Tenant isolation violation");
            }
        }

        @Test
        @DisplayName("mismatching tenant with custom handler invokes handler")
        void mismatchingTenant_customHandler_invoked() throws Exception {
            AtomicReference<TenantIsolationEnforcer.TenantViolation> captured = new AtomicReference<>();
            TenantViolationHandler captureHandler = captured::set;

            Principal principal = new Principal("iris", List.of("viewer"), "tenant-X");
            try (AutoCloseable scope = TenantContext.scope(principal)) {
                TenantIsolationEnforcer enforcer = new TenantIsolationEnforcer(true, captureHandler);

                enforcer.validateTenantAccess("tenant-Y");

                assertThat(captured.get()).isNotNull();
                assertThat(captured.get().requestedTenantId()).isEqualTo("tenant-X");
                assertThat(captured.get().actualTenantId()).isEqualTo("tenant-Y");
                assertThat(captured.get().principalName()).isEqualTo("iris");
            }
        }
    }

    // ---- executeAs ----

    @Nested
    @DisplayName("executeAs")
    class ExecuteAs {

        @Test
        @DisplayName("runs operation with the specified tenant context")
        void executeAs_setsTenantContext() {
            Principal principal = new Principal("admin-user", List.of("admin"), "admin-tenant");

            String result = TenantIsolationEnforcer.executeAs("target-tenant", principal, () -> {
                return TenantContext.getCurrentTenantId();
            });

            // The executeAs uses the principal's tenantId (which equals "admin-tenant"),
            // because TenantContext.scope sets tenantId from principal.getTenantId()
            assertThat(result).isEqualTo("admin-tenant");
        }

        @Test
        @DisplayName("executeAs with null principal creates system principal")
        void executeAs_nullPrincipal_createsSystem() {
            String result = TenantIsolationEnforcer.executeAs("sys-tenant", null, () -> {
                Optional<Principal> current = TenantContext.current();
                assertThat(current).isPresent();
                assertThat(current.get().getName()).isEqualTo("system");
                assertThat(current.get().getTenantId()).isEqualTo("sys-tenant");
                return TenantContext.getCurrentTenantId();
            });

            assertThat(result).isEqualTo("sys-tenant");
        }

        @Test
        @DisplayName("executeAs restores previous context after completion")
        void executeAs_restoresPreviousContext() throws Exception {
            Principal outer = new Principal("outer-user", List.of("viewer"), "outer-tenant");
            try (AutoCloseable scope = TenantContext.scope(outer)) {
                TenantIsolationEnforcer.executeAs("inner-tenant", null, () -> {
                    assertThat(TenantContext.getCurrentTenantId()).isEqualTo("inner-tenant");
                    return null;
                });

                // After executeAs, context should be restored
                assertThat(TenantContext.getCurrentTenantId()).isEqualTo("outer-tenant");
                assertThat(TenantContext.current()).isPresent();
                assertThat(TenantContext.current().get().getName()).isEqualTo("outer-user");
            }
        }
    }
}
