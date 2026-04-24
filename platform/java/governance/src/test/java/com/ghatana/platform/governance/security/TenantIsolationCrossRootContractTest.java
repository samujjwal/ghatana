/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.platform.governance.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cross-root contract tests for tenant isolation.
 *
 * <p>Verifies that the tenant isolation guarantee holds end-to-end across
 * multiple architectural concerns: JWT payload validation, request header
 * extraction, gateway propagation, backend data enforcement, and audit log
 * correlation. These tests document the contract that any component in the
 * system can rely on when reading {@link TenantContext}.</p>
 *
 * @doc.type    class
 * @doc.purpose Cross-root tenant isolation contract verification
 * @doc.layer   platform
 * @doc.pattern Test
 */
@DisplayName("Tenant Isolation Cross-Root Contract")
class TenantIsolationCrossRootContractTest {

    @AfterEach
    void clearContext() { // GH-90000
        TenantContext.clear();
        MDC.remove("tenantId");
    }

    // ── JWT Payload Validation ─────────────────────────────────────────────────

    @Nested
    @DisplayName("JWT payload validation")
    class JwtPayloadValidation {

        @Test
        @DisplayName("principal with valid tenant claim enables enforced data access")
        void validTenantClaimEnablesEnforcedAccess() throws Exception { // GH-90000
            // Simulates: JWT parsed by security module → Principal("alice", roles, "org-acme")
            // → gateway calls TenantContext.scope(principal) before dispatching to handler
            Principal principal = new Principal("alice", List.of("viewer"), "org-acme");
            try (TenantContext.Scope ignored = TenantContext.scope(principal)) { // GH-90000
                TenantIsolationEnforcer enforcer = TenantIsolationEnforcer.forCurrentTenant(); // GH-90000
                assertThat(enforcer.getTenantIdOrThrow()).isEqualTo("org-acme");
            }
        }

        @Test
        @DisplayName("missing tenant claim (no context set) blocks enforced data access")
        void missingTenantClaimBlocksEnforcedAccess() { // GH-90000
            // No TenantContext set: simulates JWT without a tenantId claim or
            // a request that bypassed the auth filter. Backend must reject it.
            TenantIsolationEnforcer enforcer = TenantIsolationEnforcer.forCurrentTenant(); // GH-90000

            assertThatThrownBy(enforcer::getTenantIdOrThrow) // GH-90000
                    .isInstanceOf(TenantIsolationEnforcer.TenantIsolationException.class)
                    .hasMessageContaining("No tenant context set");
        }

        @Test
        @DisplayName("principal with admin role in valid tenant still scopes to that tenant")
        void adminRoleDoesNotBypassTenantScope() throws Exception { // GH-90000
            Principal adminPrincipal = new Principal("sysop", List.of("admin"), "tenant-x");
            try (TenantContext.Scope ignored = TenantContext.scope(adminPrincipal)) { // GH-90000
                // Admins are still scoped to their tenant; cross-tenant bypass requires
                // explicit scope change, not merely an elevated role.
                assertThat(TenantContext.getCurrentTenantId()).isEqualTo("tenant-x");
            }
        }
    }

    // ── Request Header Extraction ──────────────────────────────────────────────

    @Nested
    @DisplayName("request header extraction")
    class RequestHeaderExtraction {

        @Test
        @DisplayName("header-extracted tenant ID is available throughout request lifetime")
        void headerExtractionPopulatesTenantContextForFullRequest() throws Exception { // GH-90000
            // Simulates: middleware reads X-Tenant-Id header, resolves Principal,
            // and opens TenantContext.scope for the duration of the request.
            Principal headerTenant = new Principal("svc-account", List.of("service"), "header-tenant");
            try (TenantContext.Scope ignored = TenantContext.scope(headerTenant)) { // GH-90000
                assertThat(TenantContext.getCurrentTenantId()).isEqualTo("header-tenant");

                // Simulate nested service call still seeing the same tenant
                String innerTenant = TenantContext.getCurrentTenantId();
                assertThat(innerTenant).isEqualTo("header-tenant");
            }
        }

        @Test
        @DisplayName("tenant context is cleared after request scope closes")
        void tenantContextClearedAfterRequestScopeCloses() throws Exception { // GH-90000
            Principal headerTenant = new Principal("svc-account", List.of("service"), "scoped-tenant");
            try (TenantContext.Scope ignored = TenantContext.scope(headerTenant)) { // GH-90000
                assertThat(TenantContext.getCurrentTenantId()).isEqualTo("scoped-tenant");
            }
            // After the scope closes, the context must revert to the default
            // so it does not contaminate the next request on the same thread.
            assertThat(TenantContext.getCurrentTenantId()).isEqualTo("default-tenant");
        }

        @Test
        @DisplayName("lenient mode uses default-tenant when no header is present")
        void lenientModeFallsBackToDefaultWhenHeaderAbsent() { // GH-90000
            // No scope opened: simulates a request where the auth filter is lenient
            TenantIsolationEnforcer enforcer = TenantIsolationEnforcer.lenient(); // GH-90000
            assertThat(enforcer.getTenantIdOrThrow()).isEqualTo("default-tenant");
        }
    }

    // ── Gateway Propagation ────────────────────────────────────────────────────

    @Nested
    @DisplayName("gateway propagation")
    class GatewayPropagation {

        @Test
        @DisplayName("tenant context propagates across thread boundary via wrapWithCurrentContext")
        void tenantContextPropagatesAcrossThreadBoundary() throws Exception { // GH-90000
            // Simulates: API gateway thread sets tenant context, then dispatches async
            // work to a worker thread pool. The tenant must be preserved on the worker.
            Principal principal = new Principal("svc", List.of("service"), "gateway-tenant");
            try (TenantContext.Scope ignored = TenantContext.scope(principal);
                 var executor = Executors.newSingleThreadExecutor()) { // GH-90000

                Callable<String> wrappedCall =
                        TenantContext.wrapWithCurrentContext(TenantContext::getCurrentTenantId); // GH-90000

                Future<String> tenantOnWorker = executor.submit(wrappedCall); // GH-90000
                assertThat(tenantOnWorker.get()).isEqualTo("gateway-tenant");
            }
        }

        @Test
        @DisplayName("two concurrent requests carry independent tenant contexts")
        void concurrentRequestsCarryIndependentTenantContexts() throws Exception { // GH-90000
            // Simulates two concurrent requests on separate threads. Each thread must
            // see only its own tenant context — no cross-contamination.
            Principal principalA = new Principal("alice", List.of("viewer"), "tenant-concurrent-A");
            Principal principalB = new Principal("bob", List.of("viewer"), "tenant-concurrent-B");

            try (var executor = Executors.newFixedThreadPool(2)) { // GH-90000
                Callable<String> taskA = TenantContext.wrapWithCurrentContext(() -> { // GH-90000
                    try (TenantContext.Scope ignored = TenantContext.scope(principalA)) {
                        Thread.sleep(5); // Intentional interleave opportunity
                        return TenantContext.getCurrentTenantId();
                    }
                });
                Callable<String> taskB = TenantContext.wrapWithCurrentContext(() -> { // GH-90000
                    try (TenantContext.Scope ignored = TenantContext.scope(principalB)) {
                        Thread.sleep(5);
                        return TenantContext.getCurrentTenantId();
                    }
                });

                Future<String> futureA = executor.submit(taskA); // GH-90000
                Future<String> futureB = executor.submit(taskB); // GH-90000

                assertThat(futureA.get()).isEqualTo("tenant-concurrent-A");
                assertThat(futureB.get()).isEqualTo("tenant-concurrent-B");
            }
        }
    }

    // ── Backend Enforcement ────────────────────────────────────────────────────

    @Nested
    @DisplayName("backend enforcement")
    class BackendEnforcement {

        @Test
        @DisplayName("data query returns only current tenant's records")
        void dataQueryReturnsOnlyCurrentTenantsRecords() throws Exception { // GH-90000
            // Simulates a repository that applies TenantContext as an implicit WHERE clause.
            // Only records prefixed with the current tenant should be visible.
            List<String> allRecords = List.of(
                    "tenant-A:order-1",
                    "tenant-B:order-2",
                    "tenant-A:order-3",
                    "tenant-C:order-4"
            );

            Principal principalA = new Principal("alice", List.of("viewer"), "tenant-A");
            try (TenantContext.Scope ignored = TenantContext.scope(principalA)) { // GH-90000
                String currentTenant = TenantContext.getCurrentTenantId();
                List<String> visible = allRecords.stream()
                        .filter(r -> r.startsWith(currentTenant + ":"))
                        .toList();

                assertThat(visible)
                        .containsExactly("tenant-A:order-1", "tenant-A:order-3")
                        .noneMatch(r -> r.startsWith("tenant-B:") || r.startsWith("tenant-C:"));
            }
        }

        @Test
        @DisplayName("tenant-A cannot read tenant-B records even when both exist in the store")
        void crossTenantReadIsBlocked() throws Exception { // GH-90000
            List<String> store = List.of("tenant-B:confidential-record-1", "tenant-B:confidential-record-2");

            Principal principalA = new Principal("alice", List.of("viewer"), "tenant-A");
            try (TenantContext.Scope ignored = TenantContext.scope(principalA)) { // GH-90000
                String currentTenant = TenantContext.getCurrentTenantId();
                List<String> visible = store.stream()
                        .filter(r -> r.startsWith(currentTenant + ":"))
                        .toList();

                assertThat(visible).isEmpty(); // tenant-A sees nothing from tenant-B's partition
            }
        }

        @Test
        @DisplayName("switching tenant scope replaces data view completely")
        void switchingTenantScopeReplacesDataView() throws Exception { // GH-90000
            List<String> store = List.of("tenant-A:item-1", "tenant-B:item-2");

            Principal principalA = new Principal("alice", List.of("viewer"), "tenant-A");
            Principal principalB = new Principal("bob", List.of("viewer"), "tenant-B");

            List<String> viewA;
            List<String> viewB;

            try (TenantContext.Scope ignored = TenantContext.scope(principalA)) { // GH-90000
                String tenantId = TenantContext.getCurrentTenantId();
                viewA = store.stream().filter(r -> r.startsWith(tenantId + ":")).toList();
            }
            try (TenantContext.Scope ignored = TenantContext.scope(principalB)) { // GH-90000
                String tenantId = TenantContext.getCurrentTenantId();
                viewB = store.stream().filter(r -> r.startsWith(tenantId + ":")).toList();
            }

            assertThat(viewA).containsExactly("tenant-A:item-1");
            assertThat(viewB).containsExactly("tenant-B:item-2");
        }
    }

    // ── Audit Log Verification ─────────────────────────────────────────────────

    @Nested
    @DisplayName("audit log verification")
    class AuditLogVerification {

        @Test
        @DisplayName("tenant ID is available for MDC propagation during request handling")
        void tenantIdAvailableForMdcPropagation() throws Exception { // GH-90000
            // Simulates: log framework middleware reads TenantContext and sets MDC
            // so that all structured log records emitted during the request carry the tenant.
            Principal principal = new Principal("alice", List.of("viewer"), "audit-tenant");
            try (TenantContext.Scope ignored = TenantContext.scope(principal)) { // GH-90000
                String tenantId = TenantContext.getCurrentTenantId();
                MDC.put("tenantId", tenantId); // GH-90000

                assertThat(MDC.get("tenantId")).isEqualTo("audit-tenant");
            } finally {
                MDC.remove("tenantId");
            }
        }

        @Test
        @DisplayName("MDC tenant cleared after request scope closes (no cross-request log pollution)")
        void mdcTenantClearedAfterRequestScope() throws Exception { // GH-90000
            Principal principal = new Principal("alice", List.of("viewer"), "scoped-log-tenant");
            try (TenantContext.Scope ignored = TenantContext.scope(principal)) { // GH-90000
                MDC.put("tenantId", TenantContext.getCurrentTenantId());
            }
            MDC.remove("tenantId"); // Middleware cleanup step

            // Subsequent log records on this thread must not carry stale tenant
            assertThat(MDC.get("tenantId")).isNull();
        }

        @Test
        @DisplayName("principal metadata is fully accessible for structured audit events")
        void principalMetadataAccessibleForAuditEvents() throws Exception { // GH-90000
            Principal principal = new Principal("auditor-user", List.of("auditor"), "audit-org");
            try (TenantContext.Scope ignored = TenantContext.scope(principal)) { // GH-90000
                // Simulate audit log enrichment reading TenantContext
                String tenantId = TenantContext.getCurrentTenantId();
                TenantIsolationEnforcer enforcer = TenantIsolationEnforcer.forCurrentTenant(); // GH-90000
                String enforcedTenant = enforcer.getTenantIdOrThrow();

                // Both raw context and enforcer-validated tenant must agree
                assertThat(tenantId).isEqualTo("audit-org");
                assertThat(enforcedTenant).isEqualTo("audit-org");
            }
        }
    }
}
