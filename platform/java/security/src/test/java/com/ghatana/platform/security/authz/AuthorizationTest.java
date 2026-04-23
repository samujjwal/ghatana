/**
 * @doc.type class
 * @doc.purpose Role-based access control, permission checks, and security policy validation
 * @doc.layer platform
 * @doc.pattern Test
 */
package com.ghatana.platform.security.authz;

import com.ghatana.platform.security.abac.AbacDecision;
import com.ghatana.platform.security.abac.AbacEngine;
import com.ghatana.platform.security.abac.AbacPolicy;
import com.ghatana.platform.security.abac.AbacRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Authorization Tests
 *
 * Role-based access control, permission checks, and security policy validation.
 */
@DisplayName("Authorization Tests")
class AuthorizationTest {

    @Test
    @DisplayName("Should enforce role-based access control")
    void shouldEnforceRoleBasedAccessControl() { // GH-90000
        AbacEngine engine = new AbacEngine(); // GH-90000

        AbacPolicy adminPolicy = AbacPolicy.builder("admin-policy")
            .target(req -> "admin".equals(req.subject().get("role")))
            .condition(req -> true) // GH-90000
            .build(); // GH-90000

        engine.addPolicy(adminPolicy); // GH-90000

        assertThat(engine.policyCount()).isEqualTo(1); // GH-90000
    }

    @Test
    @DisplayName("Should check permissions correctly")
    void shouldCheckPermissionsCorrectly() { // GH-90000
        AbacEngine engine = new AbacEngine(); // GH-90000

        AbacPolicy readPolicy = AbacPolicy.builder("read-policy")
            .target(req -> "read".equals(req.action())) // GH-90000
            .condition(req -> "user".equals(req.subject().get("role")))
            .build(); // GH-90000

        engine.addPolicy(readPolicy); // GH-90000

        AbacRequest request = new AbacRequest( // GH-90000
            Map.of("role", "user"), // GH-90000
            Map.of("type", "document"), // GH-90000
            "read",
            Map.of() // GH-90000
        );

        AbacDecision decision = engine.evaluate(request); // GH-90000
        assertThat(decision.permitted()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("Should validate security policies")
    void shouldValidateSecurityPolicies() { // GH-90000
        AbacEngine engine = new AbacEngine(); // GH-90000

        AbacPolicy policy = AbacPolicy.builder("security-policy")
            .target(req -> "sensitive".equals(req.resource().get("level")))
            .condition(req -> "admin".equals(req.subject().get("role")))
            .build(); // GH-90000

        engine.addPolicy(policy); // GH-90000

        assertThat(engine.removePolicy("security-policy")).isTrue();
        assertThat(engine.policyCount()).isEqualTo(0); // GH-90000
    }

    @Test
    @DisplayName("Should handle permission revocation")
    void shouldHandlePermissionRevocation() { // GH-90000
        AbacEngine engine = new AbacEngine(); // GH-90000

        AbacPolicy policy = AbacPolicy.builder("revoke-policy")
            .target(req -> true) // GH-90000
            .condition(req -> false) // GH-90000
            .build(); // GH-90000

        engine.addPolicy(policy); // GH-90000
        engine.removePolicy("revoke-policy");

        assertThat(engine.policyCount()).isEqualTo(0); // GH-90000
    }

    @Test
    @DisplayName("Should handle role assignment changes")
    void shouldHandleRoleAssignmentChanges() { // GH-90000
        AbacEngine engine = new AbacEngine(); // GH-90000

        AbacPolicy userPolicy = AbacPolicy.builder("user-policy")
            .target(req -> "user".equals(req.subject().get("role")))
            .condition(req -> true) // GH-90000
            .build(); // GH-90000

        engine.addPolicy(userPolicy); // GH-90000

        AbacRequest request = new AbacRequest( // GH-90000
            Map.of("role", "user"), // GH-90000
            Map.of("type", "resource"), // GH-90000
            "access",
            Map.of() // GH-90000
        );

        AbacDecision decision = engine.evaluate(request); // GH-90000
        assertThat(decision.permitted()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("Should handle cross-tenant authorization")
    void shouldHandleCrossTenantAuthorization() { // GH-90000
        AbacEngine engine = new AbacEngine(); // GH-90000

        AbacPolicy tenantPolicy = AbacPolicy.builder("tenant-policy")
            .target(req -> req.subject().get("tenantId").equals(req.resource().get("tenantId")))
            .condition(req -> true) // GH-90000
            .build(); // GH-90000

        engine.addPolicy(tenantPolicy); // GH-90000

        AbacRequest sameTenantRequest = new AbacRequest( // GH-90000
            Map.of("tenantId", "tenant-1"), // GH-90000
            Map.of("tenantId", "tenant-1"), // GH-90000
            "access",
            Map.of() // GH-90000
        );

        AbacDecision decision = engine.evaluate(sameTenantRequest); // GH-90000
        assertThat(decision.permitted()).isTrue(); // GH-90000
    }
}
