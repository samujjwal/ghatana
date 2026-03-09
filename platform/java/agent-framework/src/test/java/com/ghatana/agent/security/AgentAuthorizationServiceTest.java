/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Task 4.9 — Security Hardening: Tests for AgentAuthorizationService.
 */
package com.ghatana.agent.security;

import com.ghatana.agent.security.AgentAuthorizationService.AgentAuthPolicy;
import com.ghatana.agent.security.AgentAuthorizationService.AgentAuthorizationException;
import com.ghatana.platform.governance.security.Principal;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive tests for {@link AgentAuthorizationService}.
 * Tests cover all authorization dimensions: tenant, role, principal grants, admin bypass,
 * and combined policies.
 */
@DisplayName("AgentAuthorizationService")
class AgentAuthorizationServiceTest {

    private static final String TENANT_A = "tenant-alpha";
    private static final String TENANT_B = "tenant-beta";
    private static final String AGENT_FRAUD = "fraud-detector";
    private static final String AGENT_CLASSIFIER = "document-classifier";

    private AgentAuthorizationService authService;

    @BeforeEach
    void setUp() {
        authService = new AgentAuthorizationService();
    }

    // =========================================================================
    // 1. No-Policy (Open Access)
    // =========================================================================

    @Nested
    @DisplayName("No Policy (Open Access)")
    class NoPolicyOpenAccess {

        @Test
        @DisplayName("Any principal can execute agent with no policy defined")
        void anyPrincipalAllowedWithNoPolicy() {
            Principal user = new Principal("alice", List.of("viewer"), TENANT_A);
            assertThat(authService.isAuthorized(user, AGENT_FRAUD)).isTrue();
        }

        @Test
        @DisplayName("Null principal is always denied")
        void nullPrincipalDenied() {
            assertThat(authService.isAuthorized(null, AGENT_FRAUD)).isFalse();
        }

        @Test
        @DisplayName("requireAuthorization with null principal throws")
        void requireAuthWithNullPrincipalThrows() {
            assertThatThrownBy(() -> authService.requireAuthorization(null, AGENT_FRAUD))
                    .isInstanceOf(AgentAuthorizationException.class)
                    .hasMessageContaining("<anonymous>")
                    .hasMessageContaining(AGENT_FRAUD);
        }
    }

    // =========================================================================
    // 2. Tenant Restriction
    // =========================================================================

    @Nested
    @DisplayName("Tenant Restriction")
    class TenantRestriction {

        @Test
        @DisplayName("Agent restricted to tenant A denies tenant B principal")
        void tenantRestrictionDeniesWrongTenant() {
            authService.registerPolicy(AGENT_FRAUD,
                    AgentAuthPolicy.forTenants(TENANT_A));

            Principal tenantB = new Principal("bob", List.of("processor"), TENANT_B);
            assertThat(authService.isAuthorized(tenantB, AGENT_FRAUD)).isFalse();
        }

        @Test
        @DisplayName("Agent restricted to tenant A allows tenant A principal")
        void tenantRestrictionAllowsCorrectTenant() {
            authService.registerPolicy(AGENT_FRAUD,
                    AgentAuthPolicy.forTenants(TENANT_A));

            Principal tenantA = new Principal("alice", List.of("processor"), TENANT_A);
            assertThat(authService.isAuthorized(tenantA, AGENT_FRAUD)).isTrue();
        }

        @Test
        @DisplayName("Agent with multiple allowed tenants permits any listed tenant")
        void multipleTenantRestriction() {
            authService.registerPolicy(AGENT_FRAUD,
                    AgentAuthPolicy.forTenants(TENANT_A, TENANT_B));

            Principal tenantA = new Principal("alice", List.of("viewer"), TENANT_A);
            Principal tenantB = new Principal("bob", List.of("viewer"), TENANT_B);
            Principal tenantC = new Principal("charlie", List.of("viewer"), "tenant-gamma");

            assertThat(authService.isAuthorized(tenantA, AGENT_FRAUD)).isTrue();
            assertThat(authService.isAuthorized(tenantB, AGENT_FRAUD)).isTrue();
            assertThat(authService.isAuthorized(tenantC, AGENT_FRAUD)).isFalse();
        }
    }

    // =========================================================================
    // 3. Role Restriction
    // =========================================================================

    @Nested
    @DisplayName("Role Restriction")
    class RoleRestriction {

        @Test
        @DisplayName("Agent requiring 'processor' denies 'viewer'")
        void roleRestrictionDeniesWrongRole() {
            authService.registerPolicy(AGENT_FRAUD,
                    AgentAuthPolicy.forRoles("processor"));

            Principal viewer = new Principal("alice", List.of("viewer"), TENANT_A);
            assertThat(authService.isAuthorized(viewer, AGENT_FRAUD)).isFalse();
        }

        @Test
        @DisplayName("Agent requiring 'processor' allows principal with that role")
        void roleRestrictionAllowsCorrectRole() {
            authService.registerPolicy(AGENT_FRAUD,
                    AgentAuthPolicy.forRoles("processor"));

            Principal processor = new Principal("alice", List.of("processor"), TENANT_A);
            assertThat(authService.isAuthorized(processor, AGENT_FRAUD)).isTrue();
        }

        @Test
        @DisplayName("Agent with multiple required roles accepts any matching role")
        void multipleRequiredRolesAnyMatch() {
            authService.registerPolicy(AGENT_FRAUD,
                    AgentAuthPolicy.forRoles("processor", "operator"));

            Principal processor = new Principal("alice", List.of("processor"), TENANT_A);
            Principal operator = new Principal("bob", List.of("operator"), TENANT_A);
            Principal viewer = new Principal("charlie", List.of("viewer"), TENANT_A);

            assertThat(authService.isAuthorized(processor, AGENT_FRAUD)).isTrue();
            assertThat(authService.isAuthorized(operator, AGENT_FRAUD)).isTrue();
            assertThat(authService.isAuthorized(viewer, AGENT_FRAUD)).isFalse();
        }
    }

    // =========================================================================
    // 4. Principal Grants
    // =========================================================================

    @Nested
    @DisplayName("Principal Grants")
    class PrincipalGrants {

        @Test
        @DisplayName("Agent with explicit principal grants allows only named principals")
        void explicitGrantsOnly() {
            authService.registerPolicy(AGENT_CLASSIFIER,
                    AgentAuthPolicy.forPrincipals("alice", "svc-pipeline"));

            Principal alice = new Principal("alice", List.of("viewer"), TENANT_A);
            Principal svc = new Principal("svc-pipeline", List.of("processor"), TENANT_A);
            Principal bob = new Principal("bob", List.of("viewer"), TENANT_A);

            assertThat(authService.isAuthorized(alice, AGENT_CLASSIFIER)).isTrue();
            assertThat(authService.isAuthorized(svc, AGENT_CLASSIFIER)).isTrue();
            assertThat(authService.isAuthorized(bob, AGENT_CLASSIFIER)).isFalse();
        }
    }

    // =========================================================================
    // 5. Admin Bypass
    // =========================================================================

    @Nested
    @DisplayName("Admin Role Bypass")
    class AdminBypass {

        @Test
        @DisplayName("Admin role bypasses all restrictions")
        void adminBypassesAllRestrictions() {
            // Very restrictive policy: only tenant A, only 'processor' role, only 'svc-agent'
            authService.registerPolicy(AGENT_FRAUD,
                    new AgentAuthPolicy(Set.of(TENANT_A), Set.of("processor"), Set.of("svc-agent")));

            // Admin from wrong tenant should still pass
            Principal admin = new Principal("super-admin", List.of("admin"), TENANT_B);
            assertThat(authService.isAuthorized(admin, AGENT_FRAUD)).isTrue();
        }

        @Test
        @DisplayName("Admin from any tenant bypasses tenant restrictions")
        void adminBypassesTenantRestriction() {
            authService.registerPolicy(AGENT_FRAUD,
                    AgentAuthPolicy.forTenants(TENANT_A));

            Principal adminB = new Principal("admin-b", List.of("admin"), TENANT_B);
            assertThat(authService.isAuthorized(adminB, AGENT_FRAUD)).isTrue();
        }
    }

    // =========================================================================
    // 6. Combined Policies
    // =========================================================================

    @Nested
    @DisplayName("Combined Policies")
    class CombinedPolicies {

        @Test
        @DisplayName("Tenant + role restriction: must satisfy both")
        void tenantAndRoleBothRequired() {
            authService.registerPolicy(AGENT_FRAUD,
                    AgentAuthPolicy.forTenantsAndRoles(
                            Set.of(TENANT_A), Set.of("processor")));

            // Correct tenant, wrong role → denied
            Principal viewerA = new Principal("alice", List.of("viewer"), TENANT_A);
            assertThat(authService.isAuthorized(viewerA, AGENT_FRAUD)).isFalse();

            // Wrong tenant, correct role → denied
            Principal processorB = new Principal("bob", List.of("processor"), TENANT_B);
            assertThat(authService.isAuthorized(processorB, AGENT_FRAUD)).isFalse();

            // Correct tenant + correct role → allowed
            Principal processorA = new Principal("charlie", List.of("processor"), TENANT_A);
            assertThat(authService.isAuthorized(processorA, AGENT_FRAUD)).isTrue();
        }

        @Test
        @DisplayName("Full combined policy: tenant + role + principal grants")
        void fullCombinedPolicy() {
            authService.registerPolicy(AGENT_FRAUD,
                    new AgentAuthPolicy(
                            Set.of(TENANT_A),           // Only tenant A
                            Set.of("processor"),         // Must have processor role
                            Set.of("svc-fraud-engine")   // Must be this specific service
                    ));

            // All three conditions met → allowed
            Principal svc = new Principal("svc-fraud-engine", List.of("processor"), TENANT_A);
            assertThat(authService.isAuthorized(svc, AGENT_FRAUD)).isTrue();

            // Wrong principal name → denied
            Principal wrongName = new Principal("svc-other", List.of("processor"), TENANT_A);
            assertThat(authService.isAuthorized(wrongName, AGENT_FRAUD)).isFalse();

            // Wrong tenant → denied
            Principal wrongTenant = new Principal("svc-fraud-engine", List.of("processor"), TENANT_B);
            assertThat(authService.isAuthorized(wrongTenant, AGENT_FRAUD)).isFalse();

            // Wrong role → denied
            Principal wrongRole = new Principal("svc-fraud-engine", List.of("viewer"), TENANT_A);
            assertThat(authService.isAuthorized(wrongRole, AGENT_FRAUD)).isFalse();
        }
    }

    // =========================================================================
    // 7. Policy Management
    // =========================================================================

    @Nested
    @DisplayName("Policy Management")
    class PolicyManagement {

        @Test
        @DisplayName("registerPolicy replaces existing policy")
        void registerReplaces() {
            authService.registerPolicy(AGENT_FRAUD, AgentAuthPolicy.forTenants(TENANT_A));
            assertThat(authService.policyCount()).isEqualTo(1);

            // Replace with broader policy
            authService.registerPolicy(AGENT_FRAUD, AgentAuthPolicy.forTenants(TENANT_A, TENANT_B));
            assertThat(authService.policyCount()).isEqualTo(1);

            Principal tenantB = new Principal("bob", List.of("viewer"), TENANT_B);
            assertThat(authService.isAuthorized(tenantB, AGENT_FRAUD)).isTrue();
        }

        @Test
        @DisplayName("removePolicy reverts to open access")
        void removeRevertsToOpen() {
            authService.registerPolicy(AGENT_FRAUD, AgentAuthPolicy.forTenants(TENANT_A));

            Principal tenantB = new Principal("bob", List.of("viewer"), TENANT_B);
            assertThat(authService.isAuthorized(tenantB, AGENT_FRAUD)).isFalse();

            authService.removePolicy(AGENT_FRAUD);
            assertThat(authService.isAuthorized(tenantB, AGENT_FRAUD)).isTrue();
        }

        @Test
        @DisplayName("getPolicy returns Optional based on existence")
        void getPolicyReturnsOptional() {
            assertThat(authService.getPolicy(AGENT_FRAUD)).isEmpty();

            authService.registerPolicy(AGENT_FRAUD, AgentAuthPolicy.forTenants(TENANT_A));
            assertThat(authService.getPolicy(AGENT_FRAUD)).isPresent();
        }

        @Test
        @DisplayName("clearPolicies removes all policies")
        void clearRemovesAll() {
            authService.registerPolicy(AGENT_FRAUD, AgentAuthPolicy.forTenants(TENANT_A));
            authService.registerPolicy(AGENT_CLASSIFIER, AgentAuthPolicy.forRoles("processor"));
            assertThat(authService.policyCount()).isEqualTo(2);

            authService.clearPolicies();
            assertThat(authService.policyCount()).isZero();
        }
    }

    // =========================================================================
    // 8. requireAuthorization
    // =========================================================================

    @Nested
    @DisplayName("Require Authorization")
    class RequireAuthorization {

        @Test
        @DisplayName("requireAuthorization passes silently when authorized")
        void passesWhenAuthorized() {
            Principal admin = new Principal("alice", List.of("admin"), TENANT_A);
            assertThatCode(() -> authService.requireAuthorization(admin, AGENT_FRAUD))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("requireAuthorization throws AgentAuthorizationException when denied")
        void throwsWhenDenied() {
            authService.registerPolicy(AGENT_FRAUD, AgentAuthPolicy.forTenants(TENANT_A));
            Principal wrongTenant = new Principal("bob", List.of("viewer"), TENANT_B);

            assertThatThrownBy(() -> authService.requireAuthorization(wrongTenant, AGENT_FRAUD))
                    .isInstanceOf(AgentAuthorizationException.class)
                    .satisfies(ex -> {
                        AgentAuthorizationException aex = (AgentAuthorizationException) ex;
                        assertThat(aex.getPrincipalName()).isEqualTo("bob");
                        assertThat(aex.getTenantId()).isEqualTo(TENANT_B);
                        assertThat(aex.getAgentId()).isEqualTo(AGENT_FRAUD);
                    });
        }
    }

    // =========================================================================
    // 9. Policy Record Factories
    // =========================================================================

    @Nested
    @DisplayName("AgentAuthPolicy Factories")
    class PolicyFactories {

        @Test
        @DisplayName("open() creates unrestricted policy")
        void openPolicyUnrestricted() {
            AgentAuthPolicy policy = AgentAuthPolicy.open();
            assertThat(policy.allowedTenants()).isEmpty();
            assertThat(policy.requiredRoles()).isEmpty();
            assertThat(policy.grantedPrincipals()).isEmpty();
        }

        @Test
        @DisplayName("forTenants() creates tenant-restricted policy")
        void forTenantsPolicy() {
            AgentAuthPolicy policy = AgentAuthPolicy.forTenants(TENANT_A, TENANT_B);
            assertThat(policy.allowedTenants()).containsExactlyInAnyOrder(TENANT_A, TENANT_B);
            assertThat(policy.requiredRoles()).isEmpty();
        }

        @Test
        @DisplayName("forRoles() creates role-restricted policy")
        void forRolesPolicy() {
            AgentAuthPolicy policy = AgentAuthPolicy.forRoles("processor", "admin");
            assertThat(policy.requiredRoles()).containsExactlyInAnyOrder("processor", "admin");
            assertThat(policy.allowedTenants()).isEmpty();
        }

        @Test
        @DisplayName("forPrincipals() creates principal-restricted policy")
        void forPrincipalsPolicy() {
            AgentAuthPolicy policy = AgentAuthPolicy.forPrincipals("alice", "bob");
            assertThat(policy.grantedPrincipals()).containsExactlyInAnyOrder("alice", "bob");
        }

        @Test
        @DisplayName("Null collections in policy are converted to empty sets")
        void nullsConvertedToEmpty() {
            AgentAuthPolicy policy = new AgentAuthPolicy(null, null, null);
            assertThat(policy.allowedTenants()).isEmpty();
            assertThat(policy.requiredRoles()).isEmpty();
            assertThat(policy.grantedPrincipals()).isEmpty();
        }

        @Test
        @DisplayName("Policy sets are immutable")
        void policySetsImmutable() {
            AgentAuthPolicy policy = AgentAuthPolicy.forTenants(TENANT_A);
            assertThatThrownBy(() -> policy.allowedTenants().add(TENANT_B))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
