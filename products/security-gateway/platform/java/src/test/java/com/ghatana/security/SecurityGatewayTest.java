package com.ghatana.security;

import com.ghatana.platform.audit.AuditEvent;
import com.ghatana.security.audit.AuditLogger;
import com.ghatana.platform.security.model.User;
import com.ghatana.platform.security.oauth2.TokenIntrospector;
import com.ghatana.platform.security.rbac.InMemoryPolicyRepository;
import com.ghatana.platform.security.rbac.Policy;
import com.ghatana.platform.security.rbac.PolicyRepository;
import com.ghatana.platform.security.rbac.PolicyService;
import com.ghatana.platform.security.SecurityGatewayConfig;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for SecurityGateway facade.
 *
 * @doc.type class
 * @doc.purpose Unit tests for SecurityGateway
 * @doc.layer core
 * @doc.pattern Test
 */
@DisplayName("SecurityGateway Tests")
class SecurityGatewayTest extends EventloopTestBase {

    private TokenIntrospector tokenIntrospector;
    private PolicyService policyService;
    private AuditLogger auditLogger;
    private SecurityGateway gateway;

    @BeforeEach
    void setUp() {
        tokenIntrospector = mock(TokenIntrospector.class);
        auditLogger = mock(AuditLogger.class);

        // Use real PolicyService with in-memory repository
        PolicyRepository policyRepository = new InMemoryPolicyRepository();
        policyService = new PolicyService(policyRepository);

        gateway = SecurityGateway.builder()
                .tokenIntrospector(tokenIntrospector)
                .policyService(policyService)
                .auditLogger(auditLogger)
                .build();
    }

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("should build gateway with all components")
        void shouldBuildGatewayWithAllComponents() {
            // GIVEN
            SecurityGatewayConfig config = SecurityGatewayConfig.builder()
                    .rateLimitRequestsPerMinute(50)
                    .build();

            // WHEN
            SecurityGateway built = SecurityGateway.builder()
                    .tokenIntrospector(tokenIntrospector)
                    .policyService(policyService)
                    .auditLogger(auditLogger)
                    .config(config)
                    .build();

            // THEN
            assertThat(built).isNotNull();
            assertThat(built.getConfig().getRateLimitRequestsPerMinute()).isEqualTo(50);
        }

        @Test
        @DisplayName("should fail without tokenIntrospector")
        void shouldFailWithoutTokenIntrospector() {
            // GIVEN / WHEN / THEN
            assertThatThrownBy(() -> SecurityGateway.builder()
                    .policyService(policyService)
                    .build())
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("tokenIntrospector");
        }

        @Test
        @DisplayName("should fail without policyService")
        void shouldFailWithoutPolicyService() {
            // GIVEN / WHEN / THEN
            assertThatThrownBy(() -> SecurityGateway.builder()
                    .tokenIntrospector(tokenIntrospector)
                    .build())
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("policyService");
        }

        @Test
        @DisplayName("should build without auditLogger (optional)")
        void shouldBuildWithoutAuditLogger() {
            // GIVEN / WHEN
            SecurityGateway built = SecurityGateway.builder()
                    .tokenIntrospector(tokenIntrospector)
                    .policyService(policyService)
                    .build();

            // THEN
            assertThat(built).isNotNull();
        }
    }

    @Nested
    @DisplayName("Token Validation Tests")
    class TokenValidationTests {

        @Test
        @DisplayName("should validate token successfully")
        void shouldValidateTokenSuccessfully() {
            // GIVEN
            User expectedUser = User.builder()
                    .userId("user-123")
                    .username("testuser")
                    .email("test@example.com")
                    .build();
            when(tokenIntrospector.introspect("valid-token"))
                    .thenReturn(Promise.of(expectedUser));

            // WHEN
            User result = runPromise(() -> gateway.validateToken("valid-token"));

            // THEN
            assertThat(result.getUserId()).isEqualTo("user-123");
            assertThat(result.getUsername()).isEqualTo("testuser");
            verify(auditLogger).log(any(AuditEvent.class));
        }

        @Test
        @DisplayName("should fail on invalid token")
        void shouldFailOnInvalidToken() {
            // GIVEN
            when(tokenIntrospector.introspect("invalid-token"))
                    .thenReturn(Promise.ofException(new RuntimeException("Token expired")));

            // WHEN / THEN
            assertThatThrownBy(() -> runPromise(() -> gateway.validateToken("invalid-token")))
                    .hasMessageContaining("Token expired");
            verify(auditLogger).log(any(AuditEvent.class));
        }

        @Test
        @DisplayName("should reject null token")
        void shouldRejectNullToken() {
            // GIVEN / WHEN / THEN
            assertThatThrownBy(() -> gateway.validateToken(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("token must not be null");
        }

        @Test
        @DisplayName("should reject blank token")
        void shouldRejectBlankToken() {
            // WHEN / THEN
            assertThatThrownBy(() -> runPromise(() -> gateway.validateToken("   ")))
                    .hasMessageContaining("token must not be blank");
        }
    }

    @Nested
    @DisplayName("Policy Evaluation Tests")
    class PolicyEvaluationTests {

        @BeforeEach
        void setUpPolicies() {
            // Create test policies
            policyService.createPolicy(
                    "admin-all",
                    "Admin can do everything",
                    "admin",
                    "*",
                    Set.of("read", "write", "delete")
            );

            policyService.createPolicy(
                    "user-orders-read",
                    "Users can read orders",
                    "user",
                    "orders/*",
                    Set.of("read")
            );

            policyService.createPolicy(
                    "user-profile",
                    "Users can manage their profile",
                    "user",
                    "profile",
                    Set.of("read", "write")
            );
        }

        @Test
        @DisplayName("should allow admin to access any resource")
        void shouldAllowAdminToAccessAnyResource() {
            // GIVEN - use principalId and roles instead of Principal object
            String principalId = "admin-1";
            Set<String> roles = Set.of("admin");

            // WHEN
            Boolean allowed = runPromise(() -> gateway.evaluatePolicy(principalId, roles, "anything", "delete"));

            // THEN
            assertThat(allowed).isTrue();
        }

        @Test
        @DisplayName("should allow user to read orders")
        void shouldAllowUserToReadOrders() {
            // GIVEN
            String principalId = "user-1";
            Set<String> roles = Set.of("user");

            // WHEN
            Boolean allowed = runPromise(() -> gateway.evaluatePolicy(principalId, roles, "orders/123", "read"));

            // THEN
            assertThat(allowed).isTrue();
        }

        @Test
        @DisplayName("should deny user to delete orders")
        void shouldDenyUserToDeleteOrders() {
            // GIVEN
            String principalId = "user-1";
            Set<String> roles = Set.of("user");

            // WHEN
            Boolean allowed = runPromise(() -> gateway.evaluatePolicy(principalId, roles, "orders/123", "delete"));

            // THEN
            assertThat(allowed).isFalse();
        }

        @Test
        @DisplayName("should allow user to write profile")
        void shouldAllowUserToWriteProfile() {
            // GIVEN
            String principalId = "user-1";
            Set<String> roles = Set.of("user");

            // WHEN
            Boolean allowed = runPromise(() -> gateway.evaluatePolicy(principalId, roles, "profile", "write"));

            // THEN
            assertThat(allowed).isTrue();
        }

        @Test
        @DisplayName("should audit policy evaluation")
        void shouldAuditPolicyEvaluation() {
            // GIVEN
            String principalId = "user-1";
            Set<String> roles = Set.of("user");

            ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);

            // WHEN
            runPromise(() -> gateway.evaluatePolicy(principalId, roles, "orders/123", "read"));

            // THEN
            verify(auditLogger).log(eventCaptor.capture());
            AuditEvent event = eventCaptor.getValue();
            assertThat(event.getEventType()).isEqualTo("POLICY_EVALUATION");
            assertThat(event.getResourceId()).isEqualTo("orders/123");
            assertThat(event.getPrincipal()).isEqualTo("user-1");
            assertThat(event.getSuccess()).isTrue();
        }
    }

    @Nested
    @DisplayName("Token and Permission Combined Tests")
    class TokenAndPermissionTests {

        @Test
        @DisplayName("should validate token and check permission")
        void shouldValidateTokenAndCheckPermission() {
            // GIVEN
            User user = User.builder()
                    .userId("user-1")
                    .username("testuser")
                    .roles(Set.of("user"))
                    .build();
            when(tokenIntrospector.introspect("valid-token"))
                    .thenReturn(Promise.of(user));

            // Create policy for user role
            policyService.createPolicy(
                    "user-data-read",
                    "Users can read data",
                    "user",
                    "data/*",
                    Set.of("read")
            );

            // WHEN
            Boolean allowed = runPromise(() -> gateway.validateTokenAndPermission(
                    "valid-token", "data/123", "read"));

            // THEN
            assertThat(allowed).isTrue();
        }

        @Test
        @DisplayName("should deny if token valid but no permission")
        void shouldDenyIfTokenValidButNoPermission() {
            // GIVEN
            User user = User.builder()
                    .userId("user-1")
                    .username("testuser")
                    .roles(Set.of("viewer"))
                    .build();
            when(tokenIntrospector.introspect("valid-token"))
                    .thenReturn(Promise.of(user));

            // WHEN
            Boolean allowed = runPromise(() -> gateway.validateTokenAndPermission(
                    "valid-token", "data/123", "write"));

            // THEN
            assertThat(allowed).isFalse();
        }
    }

    @Nested
    @DisplayName("Policy Management Tests")
    class PolicyManagementTests {

        @Test
        @DisplayName("should create and retrieve policy")
        void shouldCreateAndRetrievePolicy() {
            // GIVEN / WHEN
            Policy created = gateway.createPolicy(
                    "test-policy",
                    "Test description",
                    "tester",
                    "tests/*",
                    Set.of("run", "read")
            );

            // THEN
            assertThat(created.getName()).isEqualTo("test-policy");
            assertThat(gateway.hasPermission("tester", "tests/123", "run")).isTrue();
            assertThat(gateway.hasPermission("tester", "tests/123", "delete")).isFalse();
        }

        @Test
        @DisplayName("should get policies for role")
        void shouldGetPoliciesForRole() {
            // GIVEN
            gateway.createPolicy("policy1", "desc", "manager", "reports/*", Set.of("read"));
            gateway.createPolicy("policy2", "desc", "manager", "budgets/*", Set.of("read", "write"));

            // WHEN
            var policies = gateway.getPoliciesForRole("manager");

            // THEN
            assertThat(policies).hasSize(2);
        }
    }
}
