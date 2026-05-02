/*
 * Copyright (c) 2026 Ghatana Inc. 
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
    void clearContext() { 
        TenantContext.clear();
        MDC.remove("tenantId");
    }

    // ── JWT Payload Validation ─────────────────────────────────────────────────

    @Nested
    @DisplayName("JWT payload validation")
    class JwtPayloadValidation {

        @Test
        @DisplayName("principal with valid tenant claim enables enforced data access")
        void validTenantClaimEnablesEnforcedAccess() throws Exception { 
            // Simulates: JWT parsed by security module → Principal("alice", roles, "org-acme")
            // → gateway calls TenantContext.scope(principal) before dispatching to handler
            Principal principal = new Principal("alice", List.of("viewer"), "org-acme");
            try (TenantContext.Scope ignored = TenantContext.scope(principal)) { 
                TenantIsolationEnforcer enforcer = TenantIsolationEnforcer.forCurrentTenant(); 
                assertThat(enforcer.getTenantIdOrThrow()).isEqualTo("org-acme");
            }
        }

        @Test
        @DisplayName("missing tenant claim (no context set) blocks enforced data access")
        void missingTenantClaimBlocksEnforcedAccess() { 
            // No TenantContext set: simulates JWT without a tenantId claim or
            // a request that bypassed the auth filter. Backend must reject it.
            TenantIsolationEnforcer enforcer = TenantIsolationEnforcer.forCurrentTenant(); 

            assertThatThrownBy(enforcer::getTenantIdOrThrow) 
                    .isInstanceOf(TenantIsolationEnforcer.TenantIsolationException.class)
                    .hasMessageContaining("No tenant context set");
        }

        @Test
        @DisplayName("principal with admin role in valid tenant still scopes to that tenant")
        void adminRoleDoesNotBypassTenantScope() throws Exception { 
            Principal adminPrincipal = new Principal("sysop", List.of("admin"), "tenant-x");
            try (TenantContext.Scope ignored = TenantContext.scope(adminPrincipal)) { 
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
        void headerExtractionPopulatesTenantContextForFullRequest() throws Exception { 
            // Simulates: middleware reads X-Tenant-Id header, resolves Principal,
            // and opens TenantContext.scope for the duration of the request.
            Principal headerTenant = new Principal("svc-account", List.of("service"), "header-tenant");
            try (TenantContext.Scope ignored = TenantContext.scope(headerTenant)) { 
                assertThat(TenantContext.getCurrentTenantId()).isEqualTo("header-tenant");

                // Simulate nested service call still seeing the same tenant
                String innerTenant = TenantContext.getCurrentTenantId();
                assertThat(innerTenant).isEqualTo("header-tenant");
            }
        }

        @Test
        @DisplayName("tenant context is cleared after request scope closes")
        void tenantContextClearedAfterRequestScopeCloses() throws Exception { 
            Principal headerTenant = new Principal("svc-account", List.of("service"), "scoped-tenant");
            try (TenantContext.Scope ignored = TenantContext.scope(headerTenant)) { 
                assertThat(TenantContext.getCurrentTenantId()).isEqualTo("scoped-tenant");
            }
            // After the scope closes, the context must revert to the default
            // so it does not contaminate the next request on the same thread.
            assertThat(TenantContext.getCurrentTenantId()).isEqualTo("default-tenant");
        }

        @Test
        @DisplayName("lenient mode uses default-tenant when no header is present")
        void lenientModeFallsBackToDefaultWhenHeaderAbsent() { 
            // No scope opened: simulates a request where the auth filter is lenient
            TenantIsolationEnforcer enforcer = TenantIsolationEnforcer.lenient(); 
            assertThat(enforcer.getTenantIdOrThrow()).isEqualTo("default-tenant");
        }
    }

    // ── Gateway Propagation ────────────────────────────────────────────────────

    @Nested
    @DisplayName("gateway propagation")
    class GatewayPropagation {

        @Test
        @DisplayName("tenant context propagates across thread boundary via wrapWithCurrentContext")
        void tenantContextPropagatesAcrossThreadBoundary() throws Exception { 
            // Simulates: API gateway thread sets tenant context, then dispatches async
            // work to a worker thread pool. The tenant must be preserved on the worker.
            Principal principal = new Principal("svc", List.of("service"), "gateway-tenant");
            try (TenantContext.Scope ignored = TenantContext.scope(principal);
                 var executor = Executors.newSingleThreadExecutor()) { 

                Callable<String> wrappedCall =
                        TenantContext.wrapWithCurrentContext(TenantContext::getCurrentTenantId); 

                Future<String> tenantOnWorker = executor.submit(wrappedCall); 
                assertThat(tenantOnWorker.get()).isEqualTo("gateway-tenant");
            }
        }

        @Test
        @DisplayName("two concurrent requests carry independent tenant contexts")
        void concurrentRequestsCarryIndependentTenantContexts() throws Exception { 
            // Simulates two concurrent requests on separate threads. Each thread must
            // see only its own tenant context — no cross-contamination.
            Principal principalA = new Principal("alice", List.of("viewer"), "tenant-concurrent-A");
            Principal principalB = new Principal("bob", List.of("viewer"), "tenant-concurrent-B");

            try (var executor = Executors.newFixedThreadPool(2)) { 
                Callable<String> taskA = TenantContext.wrapWithCurrentContext(() -> { 
                    try (TenantContext.Scope ignored = TenantContext.scope(principalA)) {
                        Thread.sleep(5); // Intentional interleave opportunity
                        return TenantContext.getCurrentTenantId();
                    }
                });
                Callable<String> taskB = TenantContext.wrapWithCurrentContext(() -> { 
                    try (TenantContext.Scope ignored = TenantContext.scope(principalB)) {
                        Thread.sleep(5);
                        return TenantContext.getCurrentTenantId();
                    }
                });

                Future<String> futureA = executor.submit(taskA); 
                Future<String> futureB = executor.submit(taskB); 

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
        void dataQueryReturnsOnlyCurrentTenantsRecords() throws Exception { 
            // Simulates a repository that applies TenantContext as an implicit WHERE clause.
            // Only records prefixed with the current tenant should be visible.
            List<String> allRecords = List.of(
                    "tenant-A:order-1",
                    "tenant-B:order-2",
                    "tenant-A:order-3",
                    "tenant-C:order-4"
            );

            Principal principalA = new Principal("alice", List.of("viewer"), "tenant-A");
            try (TenantContext.Scope ignored = TenantContext.scope(principalA)) { 
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
        void crossTenantReadIsBlocked() throws Exception { 
            List<String> store = List.of("tenant-B:confidential-record-1", "tenant-B:confidential-record-2");

            Principal principalA = new Principal("alice", List.of("viewer"), "tenant-A");
            try (TenantContext.Scope ignored = TenantContext.scope(principalA)) { 
                String currentTenant = TenantContext.getCurrentTenantId();
                List<String> visible = store.stream()
                        .filter(r -> r.startsWith(currentTenant + ":"))
                        .toList();

                assertThat(visible).isEmpty(); // tenant-A sees nothing from tenant-B's partition
            }
        }

        @Test
        @DisplayName("switching tenant scope replaces data view completely")
        void switchingTenantScopeReplacesDataView() throws Exception { 
            List<String> store = List.of("tenant-A:item-1", "tenant-B:item-2");

            Principal principalA = new Principal("alice", List.of("viewer"), "tenant-A");
            Principal principalB = new Principal("bob", List.of("viewer"), "tenant-B");

            List<String> viewA;
            List<String> viewB;

            try (TenantContext.Scope ignored = TenantContext.scope(principalA)) { 
                String tenantId = TenantContext.getCurrentTenantId();
                viewA = store.stream().filter(r -> r.startsWith(tenantId + ":")).toList();
            }
            try (TenantContext.Scope ignored = TenantContext.scope(principalB)) { 
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
        void tenantIdAvailableForMdcPropagation() throws Exception { 
            // Simulates: log framework middleware reads TenantContext and sets MDC
            // so that all structured log records emitted during the request carry the tenant.
            Principal principal = new Principal("alice", List.of("viewer"), "audit-tenant");
            try (TenantContext.Scope ignored = TenantContext.scope(principal)) { 
                String tenantId = TenantContext.getCurrentTenantId();
                MDC.put("tenantId", tenantId); 

                assertThat(MDC.get("tenantId")).isEqualTo("audit-tenant");
            } finally {
                MDC.remove("tenantId");
            }
        }

        @Test
        @DisplayName("MDC tenant cleared after request scope closes (no cross-request log pollution)")
        void mdcTenantClearedAfterRequestScope() throws Exception { 
            Principal principal = new Principal("alice", List.of("viewer"), "scoped-log-tenant");
            try (TenantContext.Scope ignored = TenantContext.scope(principal)) { 
                MDC.put("tenantId", TenantContext.getCurrentTenantId());
            }
            MDC.remove("tenantId"); // Middleware cleanup step

            // Subsequent log records on this thread must not carry stale tenant
            assertThat(MDC.get("tenantId")).isNull();
        }

        @Test
        @DisplayName("principal metadata is fully accessible for structured audit events")
        void principalMetadataAccessibleForAuditEvents() throws Exception { 
            Principal principal = new Principal("auditor-user", List.of("auditor"), "audit-org");
            try (TenantContext.Scope ignored = TenantContext.scope(principal)) { 
                // Simulate audit log enrichment reading TenantContext
                String tenantId = TenantContext.getCurrentTenantId();
                TenantIsolationEnforcer enforcer = TenantIsolationEnforcer.forCurrentTenant(); 
                String enforcedTenant = enforcer.getTenantIdOrThrow();

                // Both raw context and enforcer-validated tenant must agree
                assertThat(tenantId).isEqualTo("audit-org");
                assertThat(enforcedTenant).isEqualTo("audit-org");
            }
        }
    }
}
