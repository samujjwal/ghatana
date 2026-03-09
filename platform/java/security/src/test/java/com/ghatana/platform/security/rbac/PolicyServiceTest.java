package com.ghatana.platform.security.rbac;

import com.ghatana.platform.core.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link PolicyService} using {@link InMemoryPolicyRepository}.
 *
 * @doc.type class
 * @doc.purpose RBAC policy creation, querying, and enforcement tests
 * @doc.layer core
 * @doc.pattern Unit Test
 */
@DisplayName("PolicyService")
class PolicyServiceTest {

    private InMemoryPolicyRepository repository;
    private PolicyService policyService;

    @BeforeEach
    void setUp() {
        repository = new InMemoryPolicyRepository();
        policyService = new PolicyService(repository);
    }

    @Nested
    @DisplayName("createPolicy")
    class CreatePolicy {

        @Test
        @DisplayName("should create and persist a policy")
        void shouldCreateAndPersist() {
            Policy policy = policyService.createPolicy(
                    "admin-access", "Admin access to users",
                    "ADMIN", "users", Set.of("read", "write", "delete"));

            assertThat(policy).isNotNull();
            assertThat(policy.getId()).isNotNull();
            assertThat(policy.getName()).isEqualTo("admin-access");
            assertThat(policy.getRole()).isEqualTo("ADMIN");
            assertThat(policy.getResource()).isEqualTo("users");
            assertThat(policy.getPermissions()).containsExactlyInAnyOrder("read", "write", "delete");
        }

        @Test
        @DisplayName("should allow retrieving created policy by ID")
        void shouldRetrieveById() {
            Policy created = policyService.createPolicy(
                    "viewer", "Viewer policy", "VIEWER", "reports", Set.of("read"));

            Policy retrieved = policyService.getPolicyById(created.getId());
            assertThat(retrieved.getName()).isEqualTo("viewer");
        }
    }

    @Nested
    @DisplayName("getPolicyById")
    class GetPolicyById {

        @Test
        @DisplayName("should throw ResourceNotFoundException for unknown ID")
        void shouldThrowForUnknownId() {
            assertThatThrownBy(() -> policyService.getPolicyById("non-existent"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("hasPermission")
    class HasPermission {

        @Test
        @DisplayName("should return true when policy grants permission")
        void shouldReturnTrueWhenGranted() {
            policyService.createPolicy("p1", "desc", "ADMIN", "users", Set.of("read", "write"));

            assertThat(policyService.hasPermission("ADMIN", "users", "read")).isTrue();
            assertThat(policyService.hasPermission("ADMIN", "users", "write")).isTrue();
        }

        @Test
        @DisplayName("should return false when permission not granted")
        void shouldReturnFalseWhenNotGranted() {
            policyService.createPolicy("p1", "desc", "VIEWER", "users", Set.of("read"));

            assertThat(policyService.hasPermission("VIEWER", "users", "delete")).isFalse();
        }

        @Test
        @DisplayName("should return false when no matching role")
        void shouldReturnFalseForUnknownRole() {
            assertThat(policyService.hasPermission("UNKNOWN", "users", "read")).isFalse();
        }

        @Test
        @DisplayName("should grant all permissions with wildcard")
        void shouldHandleWildcardPermission() {
            policyService.createPolicy("p1", "desc", "SUPERADMIN", "users", Set.of("*"));

            assertThat(policyService.hasPermission("SUPERADMIN", "users", "anything")).isTrue();
        }
    }

    @Nested
    @DisplayName("enforcePermission")
    class EnforcePermission {

        @Test
        @DisplayName("should not throw when permission is granted")
        void shouldPassWhenGranted() {
            policyService.createPolicy("p1", "desc", "ADMIN", "users", Set.of("read"));

            // Should not throw
            policyService.enforcePermission("ADMIN", "users", "read");
        }

        @Test
        @DisplayName("should throw AccessDeniedException when permission denied")
        void shouldThrowWhenDenied() {
            assertThatThrownBy(() -> policyService.enforcePermission("VIEWER", "users", "delete"))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Nested
    @DisplayName("getPermissions")
    class GetPermissions {

        @Test
        @DisplayName("should return all permissions for role and resource")
        void shouldReturnAllPermissions() {
            policyService.createPolicy("p1", "desc", "ADMIN", "users", Set.of("read", "write"));
            policyService.createPolicy("p2", "desc", "ADMIN", "users", Set.of("delete"));

            Set<String> perms = policyService.getPermissions("ADMIN", "users");
            assertThat(perms).containsExactlyInAnyOrder("read", "write", "delete");
        }

        @Test
        @DisplayName("should return empty set for no matching policies")
        void shouldReturnEmptyForNoMatch() {
            Set<String> perms = policyService.getPermissions("UNKNOWN", "resources");
            assertThat(perms).isEmpty();
        }
    }

    @Nested
    @DisplayName("policy lifecycle")
    class PolicyLifecycle {

        @Test
        @DisplayName("should add permission to existing policy")
        void shouldAddPermission() {
            Policy policy = policyService.createPolicy(
                    "p1", "desc", "ADMIN", "users", Set.of("read"));

            Policy updated = policyService.addPermission(policy.getId(), "write");
            assertThat(updated.getPermissions()).contains("read", "write");
        }

        @Test
        @DisplayName("should remove permission from existing policy")
        void shouldRemovePermission() {
            Policy policy = policyService.createPolicy(
                    "p1", "desc", "ADMIN", "users", Set.of("read", "write"));

            Policy updated = policyService.removePermission(policy.getId(), "write");
            assertThat(updated.getPermissions()).containsExactly("read");
        }

        @Test
        @DisplayName("should disable policy so it no longer grants permissions")
        void shouldDisablePolicy() {
            Policy policy = policyService.createPolicy(
                    "p1", "desc", "ADMIN", "users", Set.of("read"));

            policyService.updatePolicy(policy.getId(), null, null, false);
            assertThat(policyService.hasPermission("ADMIN", "users", "read")).isFalse();
        }
    }
}
