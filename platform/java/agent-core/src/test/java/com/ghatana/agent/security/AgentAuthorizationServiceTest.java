/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved. // GH-90000
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
@DisplayName("AgentAuthorizationService [GH-90000]")
class AgentAuthorizationServiceTest {

    private static final String TENANT_A = "tenant-alpha";
    private static final String TENANT_B = "tenant-beta";
    private static final String AGENT_FRAUD = "fraud-detector";
    private static final String AGENT_CLASSIFIER = "document-classifier";

    private AgentAuthorizationService authService;

    @BeforeEach
    void setUp() { // GH-90000
        authService = new AgentAuthorizationService(); // GH-90000
    }

    // =========================================================================
    // 1. No-Policy (Open Access) // GH-90000
    // =========================================================================

    @Nested
    @DisplayName("No Policy (Open Access) [GH-90000]")
    class NoPolicyOpenAccess {

        @Test
        @DisplayName("Any principal can execute agent with no policy defined [GH-90000]")
        void anyPrincipalAllowedWithNoPolicy() { // GH-90000
            Principal user = new Principal("alice", List.of("viewer [GH-90000]"), TENANT_A);
            assertThat(authService.isAuthorized(user, AGENT_FRAUD)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("Null principal is always denied [GH-90000]")
        void nullPrincipalDenied() { // GH-90000
            assertThat(authService.isAuthorized(null, AGENT_FRAUD)).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("requireAuthorization with null principal throws [GH-90000]")
        void requireAuthWithNullPrincipalThrows() { // GH-90000
            assertThatThrownBy(() -> authService.requireAuthorization(null, AGENT_FRAUD)) // GH-90000
                    .isInstanceOf(AgentAuthorizationException.class) // GH-90000
                    .hasMessageContaining("<anonymous> [GH-90000]")
                    .hasMessageContaining(AGENT_FRAUD); // GH-90000
        }
    }

    // =========================================================================
    // 2. Tenant Restriction
    // =========================================================================

    @Nested
    @DisplayName("Tenant Restriction [GH-90000]")
    class TenantRestriction {

        @Test
        @DisplayName("Agent restricted to tenant A denies tenant B principal [GH-90000]")
        void tenantRestrictionDeniesWrongTenant() { // GH-90000
            authService.registerPolicy(AGENT_FRAUD, // GH-90000
                    AgentAuthPolicy.forTenants(TENANT_A)); // GH-90000

            Principal tenantB = new Principal("bob", List.of("processor [GH-90000]"), TENANT_B);
            assertThat(authService.isAuthorized(tenantB, AGENT_FRAUD)).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("Agent restricted to tenant A allows tenant A principal [GH-90000]")
        void tenantRestrictionAllowsCorrectTenant() { // GH-90000
            authService.registerPolicy(AGENT_FRAUD, // GH-90000
                    AgentAuthPolicy.forTenants(TENANT_A)); // GH-90000

            Principal tenantA = new Principal("alice", List.of("processor [GH-90000]"), TENANT_A);
            assertThat(authService.isAuthorized(tenantA, AGENT_FRAUD)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("Agent with multiple allowed tenants permits any listed tenant [GH-90000]")
        void multipleTenantRestriction() { // GH-90000
            authService.registerPolicy(AGENT_FRAUD, // GH-90000
                    AgentAuthPolicy.forTenants(TENANT_A, TENANT_B)); // GH-90000

            Principal tenantA = new Principal("alice", List.of("viewer [GH-90000]"), TENANT_A);
            Principal tenantB = new Principal("bob", List.of("viewer [GH-90000]"), TENANT_B);
            Principal tenantC = new Principal("charlie", List.of("viewer [GH-90000]"), "tenant-gamma");

            assertThat(authService.isAuthorized(tenantA, AGENT_FRAUD)).isTrue(); // GH-90000
            assertThat(authService.isAuthorized(tenantB, AGENT_FRAUD)).isTrue(); // GH-90000
            assertThat(authService.isAuthorized(tenantC, AGENT_FRAUD)).isFalse(); // GH-90000
        }
    }

    // =========================================================================
    // 3. Role Restriction
    // =========================================================================

    @Nested
    @DisplayName("Role Restriction [GH-90000]")
    class RoleRestriction {

        @Test
        @DisplayName("Agent requiring 'processor' denies 'viewer' [GH-90000]")
        void roleRestrictionDeniesWrongRole() { // GH-90000
            authService.registerPolicy(AGENT_FRAUD, // GH-90000
                    AgentAuthPolicy.forRoles("processor [GH-90000]"));

            Principal viewer = new Principal("alice", List.of("viewer [GH-90000]"), TENANT_A);
            assertThat(authService.isAuthorized(viewer, AGENT_FRAUD)).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("Agent requiring 'processor' allows principal with that role [GH-90000]")
        void roleRestrictionAllowsCorrectRole() { // GH-90000
            authService.registerPolicy(AGENT_FRAUD, // GH-90000
                    AgentAuthPolicy.forRoles("processor [GH-90000]"));

            Principal processor = new Principal("alice", List.of("processor [GH-90000]"), TENANT_A);
            assertThat(authService.isAuthorized(processor, AGENT_FRAUD)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("Agent with multiple required roles accepts any matching role [GH-90000]")
        void multipleRequiredRolesAnyMatch() { // GH-90000
            authService.registerPolicy(AGENT_FRAUD, // GH-90000
                    AgentAuthPolicy.forRoles("processor", "operator")); // GH-90000

            Principal processor = new Principal("alice", List.of("processor [GH-90000]"), TENANT_A);
            Principal operator = new Principal("bob", List.of("operator [GH-90000]"), TENANT_A);
            Principal viewer = new Principal("charlie", List.of("viewer [GH-90000]"), TENANT_A);

            assertThat(authService.isAuthorized(processor, AGENT_FRAUD)).isTrue(); // GH-90000
            assertThat(authService.isAuthorized(operator, AGENT_FRAUD)).isTrue(); // GH-90000
            assertThat(authService.isAuthorized(viewer, AGENT_FRAUD)).isFalse(); // GH-90000
        }
    }

    // =========================================================================
    // 4. Principal Grants
    // =========================================================================

    @Nested
    @DisplayName("Principal Grants [GH-90000]")
    class PrincipalGrants {

        @Test
        @DisplayName("Agent with explicit principal grants allows only named principals [GH-90000]")
        void explicitGrantsOnly() { // GH-90000
            authService.registerPolicy(AGENT_CLASSIFIER, // GH-90000
                    AgentAuthPolicy.forPrincipals("alice", "svc-pipeline")); // GH-90000

            Principal alice = new Principal("alice", List.of("viewer [GH-90000]"), TENANT_A);
            Principal svc = new Principal("svc-pipeline", List.of("processor [GH-90000]"), TENANT_A);
            Principal bob = new Principal("bob", List.of("viewer [GH-90000]"), TENANT_A);

            assertThat(authService.isAuthorized(alice, AGENT_CLASSIFIER)).isTrue(); // GH-90000
            assertThat(authService.isAuthorized(svc, AGENT_CLASSIFIER)).isTrue(); // GH-90000
            assertThat(authService.isAuthorized(bob, AGENT_CLASSIFIER)).isFalse(); // GH-90000
        }
    }

    // =========================================================================
    // 5. Admin Bypass
    // =========================================================================

    @Nested
    @DisplayName("Admin Role Bypass [GH-90000]")
    class AdminBypass {

        @Test
        @DisplayName("Admin role bypasses all restrictions [GH-90000]")
        void adminBypassesAllRestrictions() { // GH-90000
            // Very restrictive policy: only tenant A, only 'processor' role, only 'svc-agent'
            authService.registerPolicy(AGENT_FRAUD, // GH-90000
                    new AgentAuthPolicy(Set.of(TENANT_A), Set.of("processor [GH-90000]"), Set.of("svc-agent [GH-90000]")));

            // Admin from wrong tenant should still pass
            Principal admin = new Principal("super-admin", List.of("admin [GH-90000]"), TENANT_B);
            assertThat(authService.isAuthorized(admin, AGENT_FRAUD)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("Admin from any tenant bypasses tenant restrictions [GH-90000]")
        void adminBypassesTenantRestriction() { // GH-90000
            authService.registerPolicy(AGENT_FRAUD, // GH-90000
                    AgentAuthPolicy.forTenants(TENANT_A)); // GH-90000

            Principal adminB = new Principal("admin-b", List.of("admin [GH-90000]"), TENANT_B);
            assertThat(authService.isAuthorized(adminB, AGENT_FRAUD)).isTrue(); // GH-90000
        }
    }

    // =========================================================================
    // 6. Combined Policies
    // =========================================================================

    @Nested
    @DisplayName("Combined Policies [GH-90000]")
    class CombinedPolicies {

        @Test
        @DisplayName("Tenant + role restriction: must satisfy both [GH-90000]")
        void tenantAndRoleBothRequired() { // GH-90000
            authService.registerPolicy(AGENT_FRAUD, // GH-90000
                    AgentAuthPolicy.forTenantsAndRoles( // GH-90000
                            Set.of(TENANT_A), Set.of("processor [GH-90000]")));

            // Correct tenant, wrong role → denied
            Principal viewerA = new Principal("alice", List.of("viewer [GH-90000]"), TENANT_A);
            assertThat(authService.isAuthorized(viewerA, AGENT_FRAUD)).isFalse(); // GH-90000

            // Wrong tenant, correct role → denied
            Principal processorB = new Principal("bob", List.of("processor [GH-90000]"), TENANT_B);
            assertThat(authService.isAuthorized(processorB, AGENT_FRAUD)).isFalse(); // GH-90000

            // Correct tenant + correct role → allowed
            Principal processorA = new Principal("charlie", List.of("processor [GH-90000]"), TENANT_A);
            assertThat(authService.isAuthorized(processorA, AGENT_FRAUD)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("Full combined policy: tenant + role + principal grants [GH-90000]")
        void fullCombinedPolicy() { // GH-90000
            authService.registerPolicy(AGENT_FRAUD, // GH-90000
                    new AgentAuthPolicy( // GH-90000
                            Set.of(TENANT_A),           // Only tenant A // GH-90000
                            Set.of("processor [GH-90000]"),         // Must have processor role
                            Set.of("svc-fraud-engine [GH-90000]")   // Must be this specific service
                    ));

            // All three conditions met → allowed
            Principal svc = new Principal("svc-fraud-engine", List.of("processor [GH-90000]"), TENANT_A);
            assertThat(authService.isAuthorized(svc, AGENT_FRAUD)).isTrue(); // GH-90000

            // Wrong principal name → denied
            Principal wrongName = new Principal("svc-other", List.of("processor [GH-90000]"), TENANT_A);
            assertThat(authService.isAuthorized(wrongName, AGENT_FRAUD)).isFalse(); // GH-90000

            // Wrong tenant → denied
            Principal wrongTenant = new Principal("svc-fraud-engine", List.of("processor [GH-90000]"), TENANT_B);
            assertThat(authService.isAuthorized(wrongTenant, AGENT_FRAUD)).isFalse(); // GH-90000

            // Wrong role → denied
            Principal wrongRole = new Principal("svc-fraud-engine", List.of("viewer [GH-90000]"), TENANT_A);
            assertThat(authService.isAuthorized(wrongRole, AGENT_FRAUD)).isFalse(); // GH-90000
        }
    }

    // =========================================================================
    // 7. Policy Management
    // =========================================================================

    @Nested
    @DisplayName("Policy Management [GH-90000]")
    class PolicyManagement {

        @Test
        @DisplayName("registerPolicy replaces existing policy [GH-90000]")
        void registerReplaces() { // GH-90000
            authService.registerPolicy(AGENT_FRAUD, AgentAuthPolicy.forTenants(TENANT_A)); // GH-90000
            assertThat(authService.policyCount()).isEqualTo(1); // GH-90000

            // Replace with broader policy
            authService.registerPolicy(AGENT_FRAUD, AgentAuthPolicy.forTenants(TENANT_A, TENANT_B)); // GH-90000
            assertThat(authService.policyCount()).isEqualTo(1); // GH-90000

            Principal tenantB = new Principal("bob", List.of("viewer [GH-90000]"), TENANT_B);
            assertThat(authService.isAuthorized(tenantB, AGENT_FRAUD)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("removePolicy reverts to open access [GH-90000]")
        void removeRevertsToOpen() { // GH-90000
            authService.registerPolicy(AGENT_FRAUD, AgentAuthPolicy.forTenants(TENANT_A)); // GH-90000

            Principal tenantB = new Principal("bob", List.of("viewer [GH-90000]"), TENANT_B);
            assertThat(authService.isAuthorized(tenantB, AGENT_FRAUD)).isFalse(); // GH-90000

            authService.removePolicy(AGENT_FRAUD); // GH-90000
            assertThat(authService.isAuthorized(tenantB, AGENT_FRAUD)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("getPolicy returns Optional based on existence [GH-90000]")
        void getPolicyReturnsOptional() { // GH-90000
            assertThat(authService.getPolicy(AGENT_FRAUD)).isEmpty(); // GH-90000

            authService.registerPolicy(AGENT_FRAUD, AgentAuthPolicy.forTenants(TENANT_A)); // GH-90000
            assertThat(authService.getPolicy(AGENT_FRAUD)).isPresent(); // GH-90000
        }

        @Test
        @DisplayName("clearPolicies removes all policies [GH-90000]")
        void clearRemovesAll() { // GH-90000
            authService.registerPolicy(AGENT_FRAUD, AgentAuthPolicy.forTenants(TENANT_A)); // GH-90000
            authService.registerPolicy(AGENT_CLASSIFIER, AgentAuthPolicy.forRoles("processor [GH-90000]"));
            assertThat(authService.policyCount()).isEqualTo(2); // GH-90000

            authService.clearPolicies(); // GH-90000
            assertThat(authService.policyCount()).isZero(); // GH-90000
        }
    }

    // =========================================================================
    // 8. requireAuthorization
    // =========================================================================

    @Nested
    @DisplayName("Require Authorization [GH-90000]")
    class RequireAuthorization {

        @Test
        @DisplayName("requireAuthorization passes silently when authorized [GH-90000]")
        void passesWhenAuthorized() { // GH-90000
            Principal admin = new Principal("alice", List.of("admin [GH-90000]"), TENANT_A);
            assertThatCode(() -> authService.requireAuthorization(admin, AGENT_FRAUD)) // GH-90000
                    .doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("requireAuthorization throws AgentAuthorizationException when denied [GH-90000]")
        void throwsWhenDenied() { // GH-90000
            authService.registerPolicy(AGENT_FRAUD, AgentAuthPolicy.forTenants(TENANT_A)); // GH-90000
            Principal wrongTenant = new Principal("bob", List.of("viewer [GH-90000]"), TENANT_B);

            assertThatThrownBy(() -> authService.requireAuthorization(wrongTenant, AGENT_FRAUD)) // GH-90000
                    .isInstanceOf(AgentAuthorizationException.class) // GH-90000
                    .satisfies(ex -> { // GH-90000
                        AgentAuthorizationException aex = (AgentAuthorizationException) ex; // GH-90000
                        assertThat(aex.getPrincipalName()).isEqualTo("bob [GH-90000]");
                        assertThat(aex.getTenantId()).isEqualTo(TENANT_B); // GH-90000
                        assertThat(aex.getAgentId()).isEqualTo(AGENT_FRAUD); // GH-90000
                    });
        }
    }

    // =========================================================================
    // 9. Policy Record Factories
    // =========================================================================

    @Nested
    @DisplayName("AgentAuthPolicy Factories [GH-90000]")
    class PolicyFactories {

        @Test
        @DisplayName("open() creates unrestricted policy [GH-90000]")
        void openPolicyUnrestricted() { // GH-90000
            AgentAuthPolicy policy = AgentAuthPolicy.open(); // GH-90000
            assertThat(policy.allowedTenants()).isEmpty(); // GH-90000
            assertThat(policy.requiredRoles()).isEmpty(); // GH-90000
            assertThat(policy.grantedPrincipals()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("forTenants() creates tenant-restricted policy [GH-90000]")
        void forTenantsPolicy() { // GH-90000
            AgentAuthPolicy policy = AgentAuthPolicy.forTenants(TENANT_A, TENANT_B); // GH-90000
            assertThat(policy.allowedTenants()).containsExactlyInAnyOrder(TENANT_A, TENANT_B); // GH-90000
            assertThat(policy.requiredRoles()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("forRoles() creates role-restricted policy [GH-90000]")
        void forRolesPolicy() { // GH-90000
            AgentAuthPolicy policy = AgentAuthPolicy.forRoles("processor", "admin"); // GH-90000
            assertThat(policy.requiredRoles()).containsExactlyInAnyOrder("processor", "admin"); // GH-90000
            assertThat(policy.allowedTenants()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("forPrincipals() creates principal-restricted policy [GH-90000]")
        void forPrincipalsPolicy() { // GH-90000
            AgentAuthPolicy policy = AgentAuthPolicy.forPrincipals("alice", "bob"); // GH-90000
            assertThat(policy.grantedPrincipals()).containsExactlyInAnyOrder("alice", "bob"); // GH-90000
        }

        @Test
        @DisplayName("Null collections in policy are converted to empty sets [GH-90000]")
        void nullsConvertedToEmpty() { // GH-90000
            AgentAuthPolicy policy = new AgentAuthPolicy(null, null, null); // GH-90000
            assertThat(policy.allowedTenants()).isEmpty(); // GH-90000
            assertThat(policy.requiredRoles()).isEmpty(); // GH-90000
            assertThat(policy.grantedPrincipals()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("Policy sets are immutable [GH-90000]")
        void policySetsImmutable() { // GH-90000
            AgentAuthPolicy policy = AgentAuthPolicy.forTenants(TENANT_A); // GH-90000
            assertThatThrownBy(() -> policy.allowedTenants().add(TENANT_B)) // GH-90000
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        }
    }
}
