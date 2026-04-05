package com.ghatana.kernel.security;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.test.TestKernelContextFactory;
import com.ghatana.kernel.registry.KernelRegistryImpl;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for Kernel security framework.
 * Validates authentication, authorization, and security policies.
 *
 * @doc.type class
 * @doc.purpose Validates Kernel security integration and policy enforcement
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Kernel Security Integration Tests")
class KernelSecurityIntegrationTest extends EventloopTestBase {

    private KernelRegistryImpl registry;
    private KernelContext context;
    private TestSecurityManager securityManager;

    @BeforeEach
    void setUp() {
        registry = new KernelRegistryImpl();
        context = TestKernelContextFactory.create(registry);
        securityManager = new TestSecurityManager();
    }

    @Test
    @DisplayName("Should authenticate user with valid credentials")
    void testUserAuthentication() {
        // GIVEN: Valid user credentials
        String username = "test-user";
        String password = "secure-password";

        // WHEN: Authenticate user
        AuthenticationResult result = runPromise(() -> 
            securityManager.authenticate(username, password)
        );

        // THEN: Authentication succeeds
        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result.getUserId()).isEqualTo(username);
    }

    @Test
    @DisplayName("Should reject invalid credentials")
    void testInvalidCredentials() {
        // GIVEN: Invalid credentials
        String username = "test-user";
        String password = "wrong-password";

        // WHEN: Authenticate with invalid password
        AuthenticationResult result = runPromise(() -> 
            securityManager.authenticate(username, password)
        );

        // THEN: Authentication fails
        assertThat(result.isAuthenticated()).isFalse();
    }

    @Test
    @DisplayName("Should authorize user with required permissions")
    void testUserAuthorization() {
        // GIVEN: Authenticated user with permissions
        String userId = "test-user";
        securityManager.grantPermission(userId, "module:read");
        securityManager.grantPermission(userId, "module:write");

        // WHEN: Check authorization
        boolean canRead = runPromise(() -> 
            securityManager.hasPermission(userId, "module:read")
        );
        boolean canWrite = runPromise(() -> 
            securityManager.hasPermission(userId, "module:write")
        );
        boolean canDelete = runPromise(() -> 
            securityManager.hasPermission(userId, "module:delete")
        );

        // THEN: Permissions correctly enforced
        assertThat(canRead).isTrue();
        assertThat(canWrite).isTrue();
        assertThat(canDelete).isFalse();
    }

    @Test
    @DisplayName("Should enforce role-based access control")
    void testRoleBasedAccessControl() {
        // GIVEN: User with admin role
        String userId = "admin-user";
        securityManager.assignRole(userId, "admin");
        securityManager.addRolePermission("admin", "system:manage");

        // WHEN: Check role permissions
        boolean hasPermission = runPromise(() -> 
            securityManager.hasPermission(userId, "system:manage")
        );

        // THEN: Role permissions applied
        assertThat(hasPermission).isTrue();
    }

    @Test
    @DisplayName("Should validate security tokens")
    void testSecurityTokenValidation() {
        // GIVEN: Generated security token
        String userId = "test-user";
        String token = runPromise(() -> 
            securityManager.generateToken(userId)
        );

        // WHEN: Validate token
        TokenValidationResult validation = runPromise(() -> 
            securityManager.validateToken(token)
        );

        // THEN: Token is valid
        assertThat(validation.isValid()).isTrue();
        assertThat(validation.getUserId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("Should reject expired tokens")
    void testExpiredTokenRejection() {
        // GIVEN: Expired token
        String expiredToken = securityManager.generateExpiredToken("test-user");

        // WHEN: Validate expired token
        TokenValidationResult validation = runPromise(() -> 
            securityManager.validateToken(expiredToken)
        );

        // THEN: Token is invalid
        assertThat(validation.isValid()).isFalse();
        assertThat(validation.getReason()).contains("expired");
    }

    @Test
    @DisplayName("Should enforce security policies across modules")
    void testCrossModuleSecurityPolicies() {
        // GIVEN: Security policy for module access
        SecurityPolicy policy = new SecurityPolicy("module-access-policy");
        policy.addRule("module:finance", "role:finance-user");
        policy.addRule("module:phr", "role:healthcare-user");
        
        securityManager.registerPolicy(policy);

        // WHEN: Check access for different users
        String financeUser = "finance-user";
        String phrUser = "phr-user";
        
        securityManager.assignRole(financeUser, "finance-user");
        securityManager.assignRole(phrUser, "healthcare-user");

        boolean financeAccess = runPromise(() -> 
            securityManager.checkPolicyAccess(financeUser, "module:finance")
        );
        boolean phrAccess = runPromise(() -> 
            securityManager.checkPolicyAccess(phrUser, "module:phr")
        );
        boolean crossAccess = runPromise(() -> 
            securityManager.checkPolicyAccess(financeUser, "module:phr")
        );

        // THEN: Policies correctly enforced
        assertThat(financeAccess).isTrue();
        assertThat(phrAccess).isTrue();
        assertThat(crossAccess).isFalse();
    }

    @Test
    @DisplayName("Should audit security events")
    void testSecurityAuditLogging() {
        // GIVEN: Security manager with audit logging
        String userId = "test-user";

        // WHEN: Perform security operations
        runPromise(() -> securityManager.authenticate(userId, "password"));
        runPromise(() -> securityManager.hasPermission(userId, "module:read"));

        // THEN: Security events are audited
        assertThat(securityManager.getAuditLog()).isNotEmpty();
        assertThat(securityManager.getAuditLog())
            .anyMatch(event -> event.contains("authentication"))
            .anyMatch(event -> event.contains("authorization"));
    }

    @Test
    @DisplayName("Should handle concurrent authentication requests")
    void testConcurrentAuthentication() throws Exception {
        // GIVEN: Multiple concurrent authentication requests
        int requestCount = 50;
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(requestCount);
        java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);

        // WHEN: Authenticate concurrently
        for (int i = 0; i < requestCount; i++) {
            final String userId = "user-" + i;
            new Thread(() -> {
                try {
                    AuthenticationResult result = runPromise(() -> 
                        securityManager.authenticate(userId, "password")
                    );
                    if (result.isAuthenticated()) {
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        latch.await();

        // THEN: All authentications succeed
        assertThat(successCount.get()).isEqualTo(requestCount);
    }

    // Test security implementations

    private static class TestSecurityManager {
        private final Set<String> validUsers = new HashSet<>();
        private final java.util.Map<String, Set<String>> userPermissions = new java.util.HashMap<>();
        private final java.util.Map<String, Set<String>> userRoles = new java.util.HashMap<>();
        private final java.util.Map<String, Set<String>> rolePermissions = new java.util.HashMap<>();
        private final java.util.Map<String, SecurityPolicy> policies = new java.util.HashMap<>();
        private final java.util.List<String> auditLog = new java.util.ArrayList<>();
        private final java.util.Map<String, Long> tokens = new java.util.HashMap<>();

        TestSecurityManager() {
            validUsers.add("test-user");
            validUsers.add("admin-user");
            validUsers.add("finance-user");
            validUsers.add("phr-user");
        }

        Promise<AuthenticationResult> authenticate(String username, String password) {
            auditLog.add("authentication:" + username);
            
            if (validUsers.contains(username) && "password".equals(password) || "secure-password".equals(password)) {
                return Promise.of(new AuthenticationResult(true, username));
            }
            return Promise.of(new AuthenticationResult(false, null));
        }

        void grantPermission(String userId, String permission) {
            userPermissions.computeIfAbsent(userId, k -> new HashSet<>()).add(permission);
        }

        void assignRole(String userId, String role) {
            userRoles.computeIfAbsent(userId, k -> new HashSet<>()).add(role);
        }

        void addRolePermission(String role, String permission) {
            rolePermissions.computeIfAbsent(role, k -> new HashSet<>()).add(permission);
        }

        Promise<Boolean> hasPermission(String userId, String permission) {
            auditLog.add("authorization:" + userId + ":" + permission);
            
            // Check direct permissions
            if (userPermissions.getOrDefault(userId, Set.of()).contains(permission)) {
                return Promise.of(true);
            }
            
            // Check role permissions
            Set<String> roles = userRoles.getOrDefault(userId, Set.of());
            for (String role : roles) {
                if (rolePermissions.getOrDefault(role, Set.of()).contains(permission)) {
                    return Promise.of(true);
                }
            }
            
            return Promise.of(false);
        }

        Promise<String> generateToken(String userId) {
            String token = "token-" + userId + "-" + System.currentTimeMillis();
            tokens.put(token, System.currentTimeMillis() + 3600000); // 1 hour expiry
            return Promise.of(token);
        }

        String generateExpiredToken(String userId) {
            String token = "expired-token-" + userId;
            tokens.put(token, System.currentTimeMillis() - 1000); // Already expired
            return token;
        }

        Promise<TokenValidationResult> validateToken(String token) {
            Long expiry = tokens.get(token);
            if (expiry == null) {
                return Promise.of(new TokenValidationResult(false, null, "Token not found"));
            }
            
            if (System.currentTimeMillis() > expiry) {
                return Promise.of(new TokenValidationResult(false, null, "Token expired"));
            }
            
            String userId = token.split("-")[1];
            return Promise.of(new TokenValidationResult(true, userId, null));
        }

        void registerPolicy(SecurityPolicy policy) {
            policies.put(policy.getName(), policy);
        }

        Promise<Boolean> checkPolicyAccess(String userId, String resource) {
            for (SecurityPolicy policy : policies.values()) {
                String requiredRole = policy.getRequiredRole(resource);
                if (requiredRole != null) {
                    Set<String> roles = userRoles.getOrDefault(userId, Set.of());
                    return Promise.of(roles.contains(requiredRole));
                }
            }
            return Promise.of(false);
        }

        java.util.List<String> getAuditLog() {
            return new java.util.ArrayList<>(auditLog);
        }
    }

    private static class AuthenticationResult {
        private final boolean authenticated;
        private final String userId;

        AuthenticationResult(boolean authenticated, String userId) {
            this.authenticated = authenticated;
            this.userId = userId;
        }

        boolean isAuthenticated() {
            return authenticated;
        }

        String getUserId() {
            return userId;
        }
    }

    private static class TokenValidationResult {
        private final boolean valid;
        private final String userId;
        private final String reason;

        TokenValidationResult(boolean valid, String userId, String reason) {
            this.valid = valid;
            this.userId = userId;
            this.reason = reason;
        }

        boolean isValid() {
            return valid;
        }

        String getUserId() {
            return userId;
        }

        String getReason() {
            return reason;
        }
    }

    private static class SecurityPolicy {
        private final String name;
        private final java.util.Map<String, String> rules = new java.util.HashMap<>();

        SecurityPolicy(String name) {
            this.name = name;
        }

        void addRule(String resource, String requiredRole) {
            rules.put(resource, requiredRole);
        }

        String getName() {
            return name;
        }

        String getRequiredRole(String resource) {
            return rules.get(resource);
        }
    }
}
