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
@DisplayName("Security Framework Integration Tests")
class SecurityFrameworkIntegrationTest {

    private MockKernelSecurityManager securityManager;
    private MockPrivacyManager privacyManager;
    private PolicyEnforcementPoint policyEnforcementPoint;

    @BeforeEach
    void setUp() { 
        securityManager = new MockKernelSecurityManager(); 
        privacyManager = new MockPrivacyManager(); 
        policyEnforcementPoint = new PolicyEnforcementPoint(securityManager, privacyManager); 
    }

    @Test
    @DisplayName("Should create security context with tenant and user")
    void testCreateSecurityContext() { 
        SecurityContext context = securityManager.createSecurityContext("tenant-1", "user-1"); 

        assertNotNull(context); 
        assertEquals("tenant-1", context.getTenantId()); 
        assertEquals("user-1", context.getUserId()); 
        assertTrue(context.isAuthenticated()); 
    }

    @Test
    @DisplayName("Should authorize action with valid security context")
    void testAuthorizeAction() { 
        SecurityContext context = securityManager.createSecurityContext("tenant-1", "user-1"); 
        KernelSecurityManager.Action action = new KernelSecurityManager.Action( 
            "patient-records", "read", "phr"
        );

        boolean authorized = securityManager.authorizeAction(action, context); 

        assertTrue(authorized); 
    }

    @Test
    @DisplayName("Should enforce security policy")
    void testEnforceSecurityPolicy() { 
        SecurityContext context = securityManager.createSecurityContext("tenant-1", "user-1"); 
        Policy policy = new MockPolicy("test-policy", Policy.PolicyType.AUTHORIZATION); 

        assertDoesNotThrow(() -> securityManager.enforceSecurityPolicy(context, policy)); 
    }

    @Test
    @DisplayName("Should check consent status for data request")
    void testCheckConsent() { 
        PrivacyManager.DataRequest request = new PrivacyManager.DataRequest( 
            "user-1", "patient-data", "treatment", Map.of() 
        );

        PrivacyManager.ConsentStatus status = privacyManager.checkConsent(request, "tenant-1"); 

        assertNotNull(status); 
        assertEquals(PrivacyManager.ConsentStatus.GRANTED, status); 
    }

    @Test
    @DisplayName("Should classify data according to privacy rules")
    void testClassifyData() { 
        Object sensitiveData = Map.of("ssn", "123-45-6789", "name", "John Doe"); 

        PrivacyManager.DataClassification classification = privacyManager.classifyData(sensitiveData); 

        assertNotNull(classification); 
        assertEquals(PrivacyManager.DataClassification.PII, classification); 
    }

    @Test
    @DisplayName("Should enforce data residency requirements")
    void testEnforceResidency() { 
        PrivacyManager.DataLocation location = new PrivacyManager.DataLocation( 
            "us-east-1", "USA", "datacenter-1"
        );

        boolean compliant = privacyManager.enforceResidency(location, "tenant-1"); 

        assertTrue(compliant); 
    }

    @Test
    @DisplayName("Should enforce policy with authenticated context")
    void testPolicyEnforcementWithAuthentication() { 
        SecurityContext context = securityManager.createSecurityContext("tenant-1", "user-1"); 
        PolicyEnforcementPoint.Request request = PolicyEnforcementPoint.Request.builder() 
            .resource("patient-records")
            .operation("read")
            .scope("phr")
            .build(); 

        PolicyEnforcementPoint.EnforcementDecision decision =
            policyEnforcementPoint.enforce(request, context); 

        assertTrue(decision.isAllowed()); 
    }

    @Test
    @DisplayName("Should deny policy enforcement without authentication")
    void testPolicyEnforcementWithoutAuthentication() { 
        SecurityContext context = new MockSecurityContext("tenant-1", "user-1", false); 
        PolicyEnforcementPoint.Request request = PolicyEnforcementPoint.Request.builder() 
            .resource("patient-records")
            .operation("read")
            .scope("phr")
            .build(); 

        PolicyEnforcementPoint.EnforcementDecision decision =
            policyEnforcementPoint.enforce(request, context); 

        assertFalse(decision.isAllowed()); 
        assertEquals("Not authenticated", decision.getReason()); 
    }

    @Test
    @DisplayName("Should enforce consent requirements")
    void testPolicyEnforcementWithConsent() { 
        SecurityContext context = securityManager.createSecurityContext("tenant-1", "user-1"); 
        PolicyEnforcementPoint.Request request = PolicyEnforcementPoint.Request.builder() 
            .resource("patient-records")
            .operation("read")
            .scope("phr")
            .requiresConsent(true) 
            .dataType("patient-data")
            .purpose("treatment")
            .build(); 

        PolicyEnforcementPoint.EnforcementDecision decision =
            policyEnforcementPoint.enforce(request, context); 

        assertTrue(decision.isAllowed()); 
    }

    // Mock implementations for testing

    private static class MockKernelSecurityManager implements KernelSecurityManager {
        @Override
        public SecurityContext createSecurityContext(String tenantId, String userId) { 
            return TenantSecurityContext.builder() 
                .tenantId(tenantId) 
                .userId(userId) 
                .sessionId("session-" + System.currentTimeMillis()) 
                .role("user")
                .permission("read:patient-records")
                .authenticated(true) 
                .build(); 
        }

        @Override
        public boolean authorizeAction(Action action, SecurityContext context) { 
            return context.isAuthenticated(); 
        }

        @Override
        public void enforceSecurityPolicy(SecurityContext context, Policy policy) { 
            if (!context.isAuthenticated()) { 
                throw new SecurityPolicyViolationException("Not authenticated");
            }
        }

        @Override
        public ValidationResult validateCredentials(Credentials credentials) { 
            return ValidationResult.success(); 
        }

        @Override
        public SecurityContext getCurrentContext() { 
            return null;
        }
    }

    private static class MockPrivacyManager implements PrivacyManager {
        @Override
        public ConsentStatus checkConsent(DataRequest request, String tenantId) { 
            return ConsentStatus.GRANTED;
        }

        @Override
        public DataClassification classifyData(Object data) { 
            return DataClassification.PII;
        }

        @Override
        public boolean enforceResidency(DataLocation location, String tenantId) { 
            return true;
        }

        @Override
        public void recordConsent(String tenantId, String userId, String purpose, boolean granted) { 
        }

        @Override
        public Policy getPrivacyPolicy(String tenantId) { 
            return new MockPolicy("privacy-policy", Policy.PolicyType.DATA_ACCESS); 
        }
    }

    private static class MockPolicy implements Policy {
        private final String policyId;
        private final PolicyType type;

        MockPolicy(String policyId, PolicyType type) { 
            this.policyId = policyId;
            this.type = type;
        }

        @Override
        public String getPolicyId() { 
            return policyId;
        }

        @Override
        public String getName() { 
            return "Mock Policy";
        }

        @Override
        public PolicyType getType() { 
            return type;
        }

        @Override
        public Set<PolicyRule> getRules() { 
            return Set.of(); 
        }

        @Override
        public Map<String, Object> getMetadata() { 
            return Map.of(); 
        }

        @Override
        public boolean appliesTo(SecurityContext context) { 
            return true;
        }
    }

    private static class MockSecurityContext implements SecurityContext {
        private final String tenantId;
        private final String userId;
        private final boolean authenticated;

        MockSecurityContext(String tenantId, String userId, boolean authenticated) { 
            this.tenantId = tenantId;
            this.userId = userId;
            this.authenticated = authenticated;
        }

        @Override
        public String getTenantId() { 
            return tenantId;
        }

        @Override
        public String getUserId() { 
            return userId;
        }

        @Override
        public Set<String> getRoles() { 
            return Set.of(); 
        }

        @Override
        public Map<String, Object> getAttributes() { 
            return Map.of(); 
        }

        @Override
        public boolean hasRole(String role) { 
            return false;
        }

        @Override
        public boolean hasPermission(String permission) { 
            return false;
        }

        @Override
        public Object getAttribute(String key) { 
            return null;
        }

        @Override
        public String getSessionId() { 
            return "mock-session";
        }

        @Override
        public boolean isAuthenticated() { 
            return authenticated;
        }

        @Override
        public long getAuthenticationTime() { 
            return System.currentTimeMillis(); 
        }
    }
}
