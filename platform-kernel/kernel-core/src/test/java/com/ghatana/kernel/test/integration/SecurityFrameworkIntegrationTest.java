package com.ghatana.kernel.test.integration;

import com.ghatana.kernel.security.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Security Framework.
 *
 * <p>Tests security manager, privacy manager, and policy enforcement
 * in integrated scenarios.</p>
 *
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
@DisplayName("Security Framework Integration Tests [GH-90000]")
class SecurityFrameworkIntegrationTest {

    private MockKernelSecurityManager securityManager;
    private MockPrivacyManager privacyManager;
    private PolicyEnforcementPoint policyEnforcementPoint;

    @BeforeEach
    void setUp() { // GH-90000
        securityManager = new MockKernelSecurityManager(); // GH-90000
        privacyManager = new MockPrivacyManager(); // GH-90000
        policyEnforcementPoint = new PolicyEnforcementPoint(securityManager, privacyManager); // GH-90000
    }

    @Test
    @DisplayName("Should create security context with tenant and user [GH-90000]")
    void testCreateSecurityContext() { // GH-90000
        SecurityContext context = securityManager.createSecurityContext("tenant-1", "user-1"); // GH-90000

        assertNotNull(context); // GH-90000
        assertEquals("tenant-1", context.getTenantId()); // GH-90000
        assertEquals("user-1", context.getUserId()); // GH-90000
        assertTrue(context.isAuthenticated()); // GH-90000
    }

    @Test
    @DisplayName("Should authorize action with valid security context [GH-90000]")
    void testAuthorizeAction() { // GH-90000
        SecurityContext context = securityManager.createSecurityContext("tenant-1", "user-1"); // GH-90000
        KernelSecurityManager.Action action = new KernelSecurityManager.Action( // GH-90000
            "patient-records", "read", "phr"
        );

        boolean authorized = securityManager.authorizeAction(action, context); // GH-90000

        assertTrue(authorized); // GH-90000
    }

    @Test
    @DisplayName("Should enforce security policy [GH-90000]")
    void testEnforceSecurityPolicy() { // GH-90000
        SecurityContext context = securityManager.createSecurityContext("tenant-1", "user-1"); // GH-90000
        Policy policy = new MockPolicy("test-policy", Policy.PolicyType.AUTHORIZATION); // GH-90000

        assertDoesNotThrow(() -> securityManager.enforceSecurityPolicy(context, policy)); // GH-90000
    }

    @Test
    @DisplayName("Should check consent status for data request [GH-90000]")
    void testCheckConsent() { // GH-90000
        PrivacyManager.DataRequest request = new PrivacyManager.DataRequest( // GH-90000
            "user-1", "patient-data", "treatment", Map.of() // GH-90000
        );

        PrivacyManager.ConsentStatus status = privacyManager.checkConsent(request, "tenant-1"); // GH-90000

        assertNotNull(status); // GH-90000
        assertEquals(PrivacyManager.ConsentStatus.GRANTED, status); // GH-90000
    }

    @Test
    @DisplayName("Should classify data according to privacy rules [GH-90000]")
    void testClassifyData() { // GH-90000
        Object sensitiveData = Map.of("ssn", "123-45-6789", "name", "John Doe"); // GH-90000

        PrivacyManager.DataClassification classification = privacyManager.classifyData(sensitiveData); // GH-90000

        assertNotNull(classification); // GH-90000
        assertEquals(PrivacyManager.DataClassification.PII, classification); // GH-90000
    }

    @Test
    @DisplayName("Should enforce data residency requirements [GH-90000]")
    void testEnforceResidency() { // GH-90000
        PrivacyManager.DataLocation location = new PrivacyManager.DataLocation( // GH-90000
            "us-east-1", "USA", "datacenter-1"
        );

        boolean compliant = privacyManager.enforceResidency(location, "tenant-1"); // GH-90000

        assertTrue(compliant); // GH-90000
    }

    @Test
    @DisplayName("Should enforce policy with authenticated context [GH-90000]")
    void testPolicyEnforcementWithAuthentication() { // GH-90000
        SecurityContext context = securityManager.createSecurityContext("tenant-1", "user-1"); // GH-90000
        PolicyEnforcementPoint.Request request = PolicyEnforcementPoint.Request.builder() // GH-90000
            .resource("patient-records [GH-90000]")
            .operation("read [GH-90000]")
            .scope("phr [GH-90000]")
            .build(); // GH-90000

        PolicyEnforcementPoint.EnforcementDecision decision =
            policyEnforcementPoint.enforce(request, context); // GH-90000

        assertTrue(decision.isAllowed()); // GH-90000
    }

    @Test
    @DisplayName("Should deny policy enforcement without authentication [GH-90000]")
    void testPolicyEnforcementWithoutAuthentication() { // GH-90000
        SecurityContext context = new MockSecurityContext("tenant-1", "user-1", false); // GH-90000
        PolicyEnforcementPoint.Request request = PolicyEnforcementPoint.Request.builder() // GH-90000
            .resource("patient-records [GH-90000]")
            .operation("read [GH-90000]")
            .scope("phr [GH-90000]")
            .build(); // GH-90000

        PolicyEnforcementPoint.EnforcementDecision decision =
            policyEnforcementPoint.enforce(request, context); // GH-90000

        assertFalse(decision.isAllowed()); // GH-90000
        assertEquals("Not authenticated", decision.getReason()); // GH-90000
    }

    @Test
    @DisplayName("Should enforce consent requirements [GH-90000]")
    void testPolicyEnforcementWithConsent() { // GH-90000
        SecurityContext context = securityManager.createSecurityContext("tenant-1", "user-1"); // GH-90000
        PolicyEnforcementPoint.Request request = PolicyEnforcementPoint.Request.builder() // GH-90000
            .resource("patient-records [GH-90000]")
            .operation("read [GH-90000]")
            .scope("phr [GH-90000]")
            .requiresConsent(true) // GH-90000
            .dataType("patient-data [GH-90000]")
            .purpose("treatment [GH-90000]")
            .build(); // GH-90000

        PolicyEnforcementPoint.EnforcementDecision decision =
            policyEnforcementPoint.enforce(request, context); // GH-90000

        assertTrue(decision.isAllowed()); // GH-90000
    }

    // Mock implementations for testing

    private static class MockKernelSecurityManager implements KernelSecurityManager {
        @Override
        public SecurityContext createSecurityContext(String tenantId, String userId) { // GH-90000
            return TenantSecurityContext.builder() // GH-90000
                .tenantId(tenantId) // GH-90000
                .userId(userId) // GH-90000
                .sessionId("session-" + System.currentTimeMillis()) // GH-90000
                .role("user [GH-90000]")
                .permission("read:patient-records [GH-90000]")
                .authenticated(true) // GH-90000
                .build(); // GH-90000
        }

        @Override
        public boolean authorizeAction(Action action, SecurityContext context) { // GH-90000
            return context.isAuthenticated(); // GH-90000
        }

        @Override
        public void enforceSecurityPolicy(SecurityContext context, Policy policy) { // GH-90000
            if (!context.isAuthenticated()) { // GH-90000
                throw new SecurityPolicyViolationException("Not authenticated [GH-90000]");
            }
        }

        @Override
        public ValidationResult validateCredentials(Credentials credentials) { // GH-90000
            return ValidationResult.success(); // GH-90000
        }

        @Override
        public SecurityContext getCurrentContext() { // GH-90000
            return null;
        }
    }

    private static class MockPrivacyManager implements PrivacyManager {
        @Override
        public ConsentStatus checkConsent(DataRequest request, String tenantId) { // GH-90000
            return ConsentStatus.GRANTED;
        }

        @Override
        public DataClassification classifyData(Object data) { // GH-90000
            return DataClassification.PII;
        }

        @Override
        public boolean enforceResidency(DataLocation location, String tenantId) { // GH-90000
            return true;
        }

        @Override
        public void recordConsent(String tenantId, String userId, String purpose, boolean granted) { // GH-90000
        }

        @Override
        public Policy getPrivacyPolicy(String tenantId) { // GH-90000
            return new MockPolicy("privacy-policy", Policy.PolicyType.DATA_ACCESS); // GH-90000
        }
    }

    private static class MockPolicy implements Policy {
        private final String policyId;
        private final PolicyType type;

        MockPolicy(String policyId, PolicyType type) { // GH-90000
            this.policyId = policyId;
            this.type = type;
        }

        @Override
        public String getPolicyId() { // GH-90000
            return policyId;
        }

        @Override
        public String getName() { // GH-90000
            return "Mock Policy";
        }

        @Override
        public PolicyType getType() { // GH-90000
            return type;
        }

        @Override
        public Set<PolicyRule> getRules() { // GH-90000
            return Set.of(); // GH-90000
        }

        @Override
        public Map<String, Object> getMetadata() { // GH-90000
            return Map.of(); // GH-90000
        }

        @Override
        public boolean appliesTo(SecurityContext context) { // GH-90000
            return true;
        }
    }

    private static class MockSecurityContext implements SecurityContext {
        private final String tenantId;
        private final String userId;
        private final boolean authenticated;

        MockSecurityContext(String tenantId, String userId, boolean authenticated) { // GH-90000
            this.tenantId = tenantId;
            this.userId = userId;
            this.authenticated = authenticated;
        }

        @Override
        public String getTenantId() { // GH-90000
            return tenantId;
        }

        @Override
        public String getUserId() { // GH-90000
            return userId;
        }

        @Override
        public Set<String> getRoles() { // GH-90000
            return Set.of(); // GH-90000
        }

        @Override
        public Map<String, Object> getAttributes() { // GH-90000
            return Map.of(); // GH-90000
        }

        @Override
        public boolean hasRole(String role) { // GH-90000
            return false;
        }

        @Override
        public boolean hasPermission(String permission) { // GH-90000
            return false;
        }

        @Override
        public Object getAttribute(String key) { // GH-90000
            return null;
        }

        @Override
        public String getSessionId() { // GH-90000
            return "mock-session";
        }

        @Override
        public boolean isAuthenticated() { // GH-90000
            return authenticated;
        }

        @Override
        public long getAuthenticationTime() { // GH-90000
            return System.currentTimeMillis(); // GH-90000
        }
    }
}
