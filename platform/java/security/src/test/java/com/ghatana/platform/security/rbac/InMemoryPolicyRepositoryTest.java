package com.ghatana.platform.security.rbac;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type class
 * @doc.purpose Unit tests for InMemoryPolicyRepository CRUD behavior
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("InMemoryPolicyRepository — in-memory RBAC policy store")
class InMemoryPolicyRepositoryTest {

    private InMemoryPolicyRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryPolicyRepository();
    }

    private Policy buildPolicy(String role, String resource, String... permissions) {
        Policy policy = Policy.builder()
                .name("test-policy-" + role)
                .role(role)
                .resource(resource)
                .permissions(Set.of(permissions))
                .build();
        return policy;
    }

    @Test
    @DisplayName("findById returns empty Optional when policy not found")
    void findByIdReturnsEmptyWhenNotFound() {
        Optional<Policy> result = repository.findById("non-existent-id");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("save and findById round-trip works")
    void saveAndFindByIdRoundTrip() {
        Policy policy = buildPolicy("admin", "users", "read", "write");
        Policy saved = repository.save(policy);

        Optional<Policy> found = repository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getRole()).isEqualTo("admin");
        assertThat(found.get().getResource()).isEqualTo("users");
    }

    @Test
    @DisplayName("save returns the same policy")
    void saveReturnsSavedPolicy() {
        Policy policy = buildPolicy("viewer", "reports", "read");

        Policy returned = repository.save(policy);

        assertThat(returned).isSameAs(policy);
    }

    @Test
    @DisplayName("save null policy throws IllegalArgumentException")
    void saveNullThrows() {
        assertThatThrownBy(() -> repository.save(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("findByRole returns policies for the given role")
    void findByRoleReturnsPoliciesForRole() {
        Policy admin1 = repository.save(buildPolicy("admin", "users", "read"));
        Policy admin2 = repository.save(buildPolicy("admin", "reports", "read"));
        repository.save(buildPolicy("viewer", "users", "read"));

        List<Policy> adminPolicies = repository.findByRole("admin");

        assertThat(adminPolicies).hasSize(2)
                .extracting(Policy::getRole)
                .containsOnly("admin");
    }

    @Test
    @DisplayName("findByRole returns empty list when no policies for role")
    void findByRoleReturnsEmptyListWhenNoneFound() {
        repository.save(buildPolicy("admin", "users", "read"));

        List<Policy> result = repository.findByRole("nonexistent-role");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByResource returns policies matching resource")
    void findByResourceReturnsMatchingPolicies() {
        repository.save(buildPolicy("admin", "users", "read"));
        repository.save(buildPolicy("viewer", "users", "read"));
        repository.save(buildPolicy("admin", "reports", "read"));

        List<Policy> result = repository.findByResource("users");

        assertThat(result).hasSize(2)
                .allMatch(p -> p.appliesTo("users"));
    }

    @Test
    @DisplayName("findAll returns all stored policies")
    void findAllReturnsAllPolicies() {
        repository.save(buildPolicy("admin", "users", "read"));
        repository.save(buildPolicy("viewer", "reports", "read"));
        repository.save(buildPolicy("editor", "documents", "write"));

        List<Policy> all = repository.findAll();

        assertThat(all).hasSize(3);
    }

    @Test
    @DisplayName("findAll returns empty list when no policies stored")
    void findAllReturnsEmptyWhenNoPolocies() {
        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("deleteById removes policy and returns true")
    void deleteByIdRemovesPolicyAndReturnsTrue() {
        Policy policy = repository.save(buildPolicy("admin", "users", "read"));

        boolean deleted = repository.deleteById(policy.getId());

        assertThat(deleted).isTrue();
        assertThat(repository.findById(policy.getId())).isEmpty();
    }

    @Test
    @DisplayName("deleteById returns false when policy not found")
    void deleteByIdReturnsFalseWhenNotFound() {
        boolean deleted = repository.deleteById("nonexistent-id");

        assertThat(deleted).isFalse();
    }

    @Test
    @DisplayName("clear removes all policies")
    void clearRemovesAllPolicies() {
        repository.save(buildPolicy("admin", "users", "read"));
        repository.save(buildPolicy("viewer", "reports", "read"));

        repository.clear();

        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("overwriting policy with same id replaces it")
    void overwritingPolicyWithSameIdReplacesIt() {
        Policy original = buildPolicy("admin", "users", "read");
        original.setName("original");
        repository.save(original);

        Policy updated = Policy.builder()
                .id(original.getId())
                .name("updated")
                .role("admin")
                .resource("users")
                .permissions(Set.of("read", "write"))
                .build();
        repository.save(updated);

        assertThat(repository.findAll()).hasSize(1);
        assertThat(repository.findById(original.getId()).get().getName()).isEqualTo("updated");
    }
}
