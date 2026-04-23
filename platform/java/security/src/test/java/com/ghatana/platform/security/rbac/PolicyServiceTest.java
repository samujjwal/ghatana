package com.ghatana.platform.security.rbac;

import com.ghatana.platform.core.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
    void setUp() { // GH-90000
        repository = new InMemoryPolicyRepository(); // GH-90000
        policyService = new PolicyService(repository); // GH-90000
    }

    @Nested
    @DisplayName("createPolicy")
    class CreatePolicy {

        @Test
        @DisplayName("should create and persist a policy")
        void shouldCreateAndPersist() { // GH-90000
            Policy policy = policyService.createPolicy( // GH-90000
                    "admin-access", "Admin access to users",
                    "ADMIN", "users", Set.of("read", "write", "delete")); // GH-90000

            assertThat(policy).isNotNull(); // GH-90000
            assertThat(policy.getId()).isNotNull(); // GH-90000
            assertThat(policy.getName()).isEqualTo("admin-access");
            assertThat(policy.getRole()).isEqualTo("ADMIN");
            assertThat(policy.getResource()).isEqualTo("users");
            assertThat(policy.getPermissions()).containsExactlyInAnyOrder("read", "write", "delete"); // GH-90000
        }

        @Test
        @DisplayName("should allow retrieving created policy by ID")
        void shouldRetrieveById() { // GH-90000
            Policy created = policyService.createPolicy( // GH-90000
                    "viewer", "Viewer policy", "VIEWER", "reports", Set.of("read"));

            Policy retrieved = policyService.getPolicyById(created.getId()); // GH-90000
            assertThat(retrieved.getName()).isEqualTo("viewer");
        }
    }

    @Nested
    @DisplayName("getPolicyById")
    class GetPolicyById {

        @Test
        @DisplayName("should throw ResourceNotFoundException for unknown ID")
        void shouldThrowForUnknownId() { // GH-90000
            assertThatThrownBy(() -> policyService.getPolicyById("non-existent"))
                    .isInstanceOf(ResourceNotFoundException.class); // GH-90000
        }
    }

    @Nested
    @DisplayName("hasPermission")
    class HasPermission {

        @Test
        @DisplayName("should return true when policy grants permission")
        void shouldReturnTrueWhenGranted() { // GH-90000
            policyService.createPolicy("p1", "desc", "ADMIN", "users", Set.of("read", "write")); // GH-90000

            assertThat(policyService.hasPermission("ADMIN", "users", "read")).isTrue(); // GH-90000
            assertThat(policyService.hasPermission("ADMIN", "users", "write")).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should return false when permission not granted")
        void shouldReturnFalseWhenNotGranted() { // GH-90000
            policyService.createPolicy("p1", "desc", "VIEWER", "users", Set.of("read"));

            assertThat(policyService.hasPermission("VIEWER", "users", "delete")).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("should return false when no matching role")
        void shouldReturnFalseForUnknownRole() { // GH-90000
            assertThat(policyService.hasPermission("UNKNOWN", "users", "read")).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("should grant all permissions with wildcard")
        void shouldHandleWildcardPermission() { // GH-90000
            policyService.createPolicy("p1", "desc", "SUPERADMIN", "users", Set.of("*"));

            assertThat(policyService.hasPermission("SUPERADMIN", "users", "anything")).isTrue(); // GH-90000
        }
    }

    @Nested
    @DisplayName("enforcePermission")
    class EnforcePermission {

        @Test
        @DisplayName("should not throw when permission is granted")
        void shouldPassWhenGranted() { // GH-90000
            policyService.createPolicy("p1", "desc", "ADMIN", "users", Set.of("read"));

            // Should not throw
            policyService.enforcePermission("ADMIN", "users", "read"); // GH-90000
        }

        @Test
        @DisplayName("should throw AccessDeniedException when permission denied")
        void shouldThrowWhenDenied() { // GH-90000
            assertThatThrownBy(() -> policyService.enforcePermission("VIEWER", "users", "delete")) // GH-90000
                    .isInstanceOf(AccessDeniedException.class); // GH-90000
        }
    }

    @Nested
    @DisplayName("getPermissions")
    class GetPermissions {

        @Test
        @DisplayName("should return all permissions for role and resource")
        void shouldReturnAllPermissions() { // GH-90000
            policyService.createPolicy("p1", "desc", "ADMIN", "users", Set.of("read", "write")); // GH-90000
            policyService.createPolicy("p2", "desc", "ADMIN", "users", Set.of("delete"));

            Set<String> perms = policyService.getPermissions("ADMIN", "users"); // GH-90000
            assertThat(perms).containsExactlyInAnyOrder("read", "write", "delete"); // GH-90000
        }

        @Test
        @DisplayName("should return empty set for no matching policies")
        void shouldReturnEmptyForNoMatch() { // GH-90000
            Set<String> perms = policyService.getPermissions("UNKNOWN", "resources"); // GH-90000
            assertThat(perms).isEmpty(); // GH-90000
        }
    }

    @Nested
    @DisplayName("policy lifecycle")
    class PolicyLifecycle {

        @Test
        @DisplayName("should add permission to existing policy")
        void shouldAddPermission() { // GH-90000
            Policy policy = policyService.createPolicy( // GH-90000
                    "p1", "desc", "ADMIN", "users", Set.of("read"));

            Policy updated = policyService.addPermission(policy.getId(), "write"); // GH-90000
            assertThat(updated.getPermissions()).contains("read", "write"); // GH-90000
        }

        @Test
        @DisplayName("should remove permission from existing policy")
        void shouldRemovePermission() { // GH-90000
            Policy policy = policyService.createPolicy( // GH-90000
                    "p1", "desc", "ADMIN", "users", Set.of("read", "write")); // GH-90000

            Policy updated = policyService.removePermission(policy.getId(), "write"); // GH-90000
            assertThat(updated.getPermissions()).containsExactly("read");
        }

        @Test
        @DisplayName("should disable policy so it no longer grants permissions")
        void shouldDisablePolicy() { // GH-90000
            Policy policy = policyService.createPolicy( // GH-90000
                    "p1", "desc", "ADMIN", "users", Set.of("read"));

            policyService.updatePolicy(policy.getId(), null, null, false); // GH-90000
            assertThat(policyService.hasPermission("ADMIN", "users", "read")).isFalse(); // GH-90000
        }
    }
}
