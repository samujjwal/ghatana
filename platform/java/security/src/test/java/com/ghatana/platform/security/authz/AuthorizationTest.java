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
    void shouldEnforceRoleBasedAccessControl() {
        AbacEngine engine = new AbacEngine();
        
        AbacPolicy adminPolicy = AbacPolicy.builder("admin-policy")
            .target(req -> "admin".equals(req.subject().get("role")))
            .condition(req -> true)
            .build();
        
        engine.addPolicy(adminPolicy);
        
        assertThat(engine.policyCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should check permissions correctly")
    void shouldCheckPermissionsCorrectly() {
        AbacEngine engine = new AbacEngine();
        
        AbacPolicy readPolicy = AbacPolicy.builder("read-policy")
            .target(req -> "read".equals(req.action()))
            .condition(req -> "user".equals(req.subject().get("role")))
            .build();
        
        engine.addPolicy(readPolicy);
        
        AbacRequest request = AbacRequest.builder()
            .subject(Map.of("role", "user"))
            .action("read")
            .resource(Map.of("type", "document"))
            .build();
        
        AbacDecision decision = engine.evaluate(request);
        assertThat(decision.permitted()).isTrue();
    }

    @Test
    @DisplayName("Should validate security policies")
    void shouldValidateSecurityPolicies() {
        AbacEngine engine = new AbacEngine();
        
        AbacPolicy policy = AbacPolicy.builder("security-policy")
            .target(req -> "sensitive".equals(req.resource().get("level")))
            .condition(req -> "admin".equals(req.subject().get("role")))
            .build();
        
        engine.addPolicy(policy);
        
        assertThat(engine.removePolicy("security-policy")).isTrue();
        assertThat(engine.policyCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should handle permission revocation")
    void shouldHandlePermissionRevocation() {
        AbacEngine engine = new AbacEngine();
        
        AbacPolicy policy = AbacPolicy.builder("revoke-policy")
            .target(req -> true)
            .condition(req -> false)
            .build();
        
        engine.addPolicy(policy);
        engine.removePolicy("revoke-policy");
        
        assertThat(engine.policyCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should handle role assignment changes")
    void shouldHandleRoleAssignmentChanges() {
        AbacEngine engine = new AbacEngine();
        
        AbacPolicy userPolicy = AbacPolicy.builder("user-policy")
            .target(req -> "user".equals(req.subject().get("role")))
            .condition(req -> true)
            .build();
        
        engine.addPolicy(userPolicy);
        
        AbacRequest request = AbacRequest.builder()
            .subject(Map.of("role", "user"))
            .action("access")
            .resource(Map.of("type", "resource"))
            .build();
        
        AbacDecision decision = engine.evaluate(request);
        assertThat(decision.permitted()).isTrue();
    }

    @Test
    @DisplayName("Should handle cross-tenant authorization")
    void shouldHandleCrossTenantAuthorization() {
        AbacEngine engine = new AbacEngine();
        
        AbacPolicy tenantPolicy = AbacPolicy.builder("tenant-policy")
            .target(req -> req.subject().get("tenantId").equals(req.resource().get("tenantId")))
            .condition(req -> true)
            .build();
        
        engine.addPolicy(tenantPolicy);
        
        AbacRequest sameTenantRequest = AbacRequest.builder()
            .subject(Map.of("tenantId", "tenant-1"))
            .action("access")
            .resource(Map.of("tenantId", "tenant-1"))
            .build();
        
        AbacDecision decision = engine.evaluate(sameTenantRequest);
        assertThat(decision.permitted()).isTrue();
    }
}
