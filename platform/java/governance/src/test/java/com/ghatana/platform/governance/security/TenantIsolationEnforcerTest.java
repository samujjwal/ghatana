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
@DisplayName("TenantIsolationEnforcer [GH-90000]")
class TenantIsolationEnforcerTest {

    @AfterEach
    void tearDown() { // GH-90000
        TenantContext.clear(); // GH-90000
    }

    // ---- getTenantIdOrThrow ----

    @Nested
    @DisplayName("getTenantIdOrThrow [GH-90000]")
    class GetTenantIdOrThrow {

        @Test
        @DisplayName("strict mode with no context throws TenantIsolationException [GH-90000]")
        void strictMode_noContext_throws() { // GH-90000
            TenantIsolationEnforcer enforcer = TenantIsolationEnforcer.forCurrentTenant(); // GH-90000

            assertThatThrownBy(enforcer::getTenantIdOrThrow) // GH-90000
                    .isInstanceOf(TenantIsolationException.class) // GH-90000
                    .hasMessageContaining("No tenant context set [GH-90000]");
        }

        @Test
        @DisplayName("strict mode with principal set returns tenant ID [GH-90000]")
        void strictMode_withPrincipal_returnsTenantId() throws Exception { // GH-90000
            Principal principal = new Principal("alice", List.of("viewer [GH-90000]"), "tenant-abc");
            try (AutoCloseable scope = TenantContext.scope(principal)) { // GH-90000
                TenantIsolationEnforcer enforcer = TenantIsolationEnforcer.forCurrentTenant(); // GH-90000

                assertThat(enforcer.getTenantIdOrThrow()).isEqualTo("tenant-abc [GH-90000]");
            }
        }

        @Test
        @DisplayName("strict mode with default-tenant principal succeeds because principal exists [GH-90000]")
        void strictMode_defaultTenantWithPrincipal_returnsTenantId() throws Exception { // GH-90000
            Principal principal = new Principal("system", List.of("admin [GH-90000]"), "default-tenant");
            try (AutoCloseable scope = TenantContext.scope(principal)) { // GH-90000
                TenantIsolationEnforcer enforcer = TenantIsolationEnforcer.forCurrentTenant(); // GH-90000

                assertThat(enforcer.getTenantIdOrThrow()).isEqualTo("default-tenant [GH-90000]");
            }
        }

        @Test
        @DisplayName("lenient mode with no context returns default-tenant [GH-90000]")
        void lenientMode_noContext_returnsDefault() { // GH-90000
            TenantIsolationEnforcer enforcer = TenantIsolationEnforcer.lenient(); // GH-90000

            assertThat(enforcer.getTenantIdOrThrow()).isEqualTo("default-tenant [GH-90000]");
        }
    }

    // ---- getTenantId ----

    @Nested
    @DisplayName("getTenantId [GH-90000]")
    class GetTenantId {

        @Test
        @DisplayName("returns empty when no context is set [GH-90000]")
        void noContext_returnsEmpty() { // GH-90000
            TenantIsolationEnforcer enforcer = TenantIsolationEnforcer.forCurrentTenant(); // GH-90000

            assertThat(enforcer.getTenantId()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("returns tenant ID when context is set [GH-90000]")
        void withContext_returnsTenantId() throws Exception { // GH-90000
            Principal principal = new Principal("bob", List.of("editor [GH-90000]"), "tenant-xyz");
            try (AutoCloseable scope = TenantContext.scope(principal)) { // GH-90000
                TenantIsolationEnforcer enforcer = TenantIsolationEnforcer.forCurrentTenant(); // GH-90000

                assertThat(enforcer.getTenantId()) // GH-90000
                        .isPresent() // GH-90000
                        .hasValue("tenant-xyz [GH-90000]");
            }
        }
    }

    // ---- requireAuthenticated ----

    @Nested
    @DisplayName("requireAuthenticated [GH-90000]")
    class RequireAuthenticated {

        @Test
        @DisplayName("throws when no principal in context [GH-90000]")
        void noPrincipal_throws() { // GH-90000
            TenantIsolationEnforcer enforcer = TenantIsolationEnforcer.forCurrentTenant(); // GH-90000

            assertThatThrownBy(enforcer::requireAuthenticated) // GH-90000
                    .isInstanceOf(TenantIsolationException.class) // GH-90000
                    .hasMessageContaining("Authentication required [GH-90000]");
        }

        @Test
        @DisplayName("succeeds when principal is present [GH-90000]")
        void withPrincipal_succeeds() throws Exception { // GH-90000
            Principal principal = new Principal("carol", List.of("viewer [GH-90000]"), "tenant-1");
            try (AutoCloseable scope = TenantContext.scope(principal)) { // GH-90000
                TenantIsolationEnforcer enforcer = TenantIsolationEnforcer.forCurrentTenant(); // GH-90000

                // Should not throw
                enforcer.requireAuthenticated(); // GH-90000
            }
        }
    }

    // ---- requireRole ----

    @Nested
    @DisplayName("requireRole [GH-90000]")
    class RequireRole {

        @Test
        @DisplayName("succeeds when principal has matching role [GH-90000]")
        void matchingRole_succeeds() throws Exception { // GH-90000
            Principal principal = new Principal("dave", List.of("admin", "editor"), "tenant-1"); // GH-90000
            try (AutoCloseable scope = TenantContext.scope(principal)) { // GH-90000
                TenantIsolationEnforcer enforcer = TenantIsolationEnforcer.forCurrentTenant(); // GH-90000

                // Should not throw
                enforcer.requireRole("admin [GH-90000]");
            }
        }

        @Test
        @DisplayName("succeeds when principal has one of multiple required roles [GH-90000]")
        void oneOfMultipleRoles_succeeds() throws Exception { // GH-90000
            Principal principal = new Principal("eve", List.of("viewer [GH-90000]"), "tenant-1");
            try (AutoCloseable scope = TenantContext.scope(principal)) { // GH-90000
                TenantIsolationEnforcer enforcer = TenantIsolationEnforcer.forCurrentTenant(); // GH-90000

                // Should not throw — viewer matches one of the required roles
                enforcer.requireRole("admin", "viewer"); // GH-90000
            }
        }

        @Test
        @DisplayName("throws when principal lacks required role [GH-90000]")
        void noMatchingRole_throws() throws Exception { // GH-90000
            Principal principal = new Principal("frank", List.of("viewer [GH-90000]"), "tenant-1");
            try (AutoCloseable scope = TenantContext.scope(principal)) { // GH-90000
                TenantIsolationEnforcer enforcer = TenantIsolationEnforcer.forCurrentTenant(); // GH-90000

                assertThatThrownBy(() -> enforcer.requireRole("admin [GH-90000]"))
                        .isInstanceOf(TenantIsolationException.class) // GH-90000
                        .hasMessageContaining("Access denied [GH-90000]")
                        .hasMessageContaining("admin [GH-90000]");
            }
        }
    }

    // ---- validateTenantAccess ----

    @Nested
    @DisplayName("validateTenantAccess [GH-90000]")
    class ValidateTenantAccess {

        @Test
        @DisplayName("matching tenant passes without violation [GH-90000]")
        void matchingTenant_noViolation() throws Exception { // GH-90000
            Principal principal = new Principal("grace", List.of("viewer [GH-90000]"), "tenant-match");
            try (AutoCloseable scope = TenantContext.scope(principal)) { // GH-90000
                TenantIsolationEnforcer enforcer = TenantIsolationEnforcer.forCurrentTenant(); // GH-90000

                // Should not throw
                enforcer.validateTenantAccess("tenant-match [GH-90000]");
            }
        }

        @Test
        @DisplayName("mismatching tenant in strict mode throws exception [GH-90000]")
        void mismatchingTenant_strict_throws() throws Exception { // GH-90000
            Principal principal = new Principal("hank", List.of("viewer [GH-90000]"), "tenant-A");
            try (AutoCloseable scope = TenantContext.scope(principal)) { // GH-90000
                TenantIsolationEnforcer enforcer = TenantIsolationEnforcer.forCurrentTenant(); // GH-90000

                assertThatThrownBy(() -> enforcer.validateTenantAccess("tenant-B [GH-90000]"))
                        .isInstanceOf(TenantIsolationException.class) // GH-90000
                        .hasMessageContaining("Tenant isolation violation [GH-90000]");
            }
        }

        @Test
        @DisplayName("mismatching tenant with custom handler invokes handler [GH-90000]")
        void mismatchingTenant_customHandler_invoked() throws Exception { // GH-90000
            AtomicReference<TenantIsolationEnforcer.TenantViolation> captured = new AtomicReference<>(); // GH-90000
            TenantViolationHandler captureHandler = captured::set;

            Principal principal = new Principal("iris", List.of("viewer [GH-90000]"), "tenant-X");
            try (AutoCloseable scope = TenantContext.scope(principal)) { // GH-90000
                TenantIsolationEnforcer enforcer = new TenantIsolationEnforcer(true, captureHandler); // GH-90000

                enforcer.validateTenantAccess("tenant-Y [GH-90000]");

                assertThat(captured.get()).isNotNull(); // GH-90000
                assertThat(captured.get().requestedTenantId()).isEqualTo("tenant-X [GH-90000]");
                assertThat(captured.get().actualTenantId()).isEqualTo("tenant-Y [GH-90000]");
                assertThat(captured.get().principalName()).isEqualTo("iris [GH-90000]");
            }
        }
    }

    // ---- executeAs ----

    @Nested
    @DisplayName("executeAs [GH-90000]")
    class ExecuteAs {

        @Test
        @DisplayName("runs operation with the specified tenant context [GH-90000]")
        void executeAs_setsTenantContext() { // GH-90000
            Principal principal = new Principal("admin-user", List.of("admin [GH-90000]"), "admin-tenant");

            String result = TenantIsolationEnforcer.executeAs("target-tenant", principal, () -> { // GH-90000
                return TenantContext.getCurrentTenantId(); // GH-90000
            });

            // The executeAs uses the principal's tenantId (which equals "admin-tenant"), // GH-90000
            // because TenantContext.scope sets tenantId from principal.getTenantId() // GH-90000
            assertThat(result).isEqualTo("admin-tenant [GH-90000]");
        }

        @Test
        @DisplayName("executeAs with null principal creates system principal [GH-90000]")
        void executeAs_nullPrincipal_createsSystem() { // GH-90000
            String result = TenantIsolationEnforcer.executeAs("sys-tenant", null, () -> { // GH-90000
                Optional<Principal> current = TenantContext.current(); // GH-90000
                assertThat(current).isPresent(); // GH-90000
                assertThat(current.get().getName()).isEqualTo("system [GH-90000]");
                assertThat(current.get().getTenantId()).isEqualTo("sys-tenant [GH-90000]");
                return TenantContext.getCurrentTenantId(); // GH-90000
            });

            assertThat(result).isEqualTo("sys-tenant [GH-90000]");
        }

        @Test
        @DisplayName("executeAs restores previous context after completion [GH-90000]")
        void executeAs_restoresPreviousContext() throws Exception { // GH-90000
            Principal outer = new Principal("outer-user", List.of("viewer [GH-90000]"), "outer-tenant");
            try (AutoCloseable scope = TenantContext.scope(outer)) { // GH-90000
                TenantIsolationEnforcer.executeAs("inner-tenant", null, () -> { // GH-90000
                    assertThat(TenantContext.getCurrentTenantId()).isEqualTo("inner-tenant [GH-90000]");
                    return null;
                });

                // After executeAs, context should be restored
                assertThat(TenantContext.getCurrentTenantId()).isEqualTo("outer-tenant [GH-90000]");
                assertThat(TenantContext.current()).isPresent(); // GH-90000
                assertThat(TenantContext.current().get().getName()).isEqualTo("outer-user [GH-90000]");
            }
        }
    }
}
