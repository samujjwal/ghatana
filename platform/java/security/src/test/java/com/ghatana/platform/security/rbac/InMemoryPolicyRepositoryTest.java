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
@DisplayName("InMemoryPolicyRepository — in-memory RBAC policy store [GH-90000]")
class InMemoryPolicyRepositoryTest {

    private InMemoryPolicyRepository repository;

    @BeforeEach
    void setUp() { // GH-90000
        repository = new InMemoryPolicyRepository(); // GH-90000
    }

    private Policy buildPolicy(String role, String resource, String... permissions) { // GH-90000
        Policy policy = Policy.builder() // GH-90000
                .name("test-policy-" + role) // GH-90000
                .role(role) // GH-90000
                .resource(resource) // GH-90000
                .permissions(Set.of(permissions)) // GH-90000
                .build(); // GH-90000
        return policy;
    }

    @Test
    @DisplayName("findById returns empty Optional when policy not found [GH-90000]")
    void findByIdReturnsEmptyWhenNotFound() { // GH-90000
        Optional<Policy> result = repository.findById("non-existent-id [GH-90000]");

        assertThat(result).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("save and findById round-trip works [GH-90000]")
    void saveAndFindByIdRoundTrip() { // GH-90000
        Policy policy = buildPolicy("admin", "users", "read", "write"); // GH-90000
        Policy saved = repository.save(policy); // GH-90000

        Optional<Policy> found = repository.findById(saved.getId()); // GH-90000

        assertThat(found).isPresent(); // GH-90000
        assertThat(found.get().getRole()).isEqualTo("admin [GH-90000]");
        assertThat(found.get().getResource()).isEqualTo("users [GH-90000]");
    }

    @Test
    @DisplayName("save returns the same policy [GH-90000]")
    void saveReturnsSavedPolicy() { // GH-90000
        Policy policy = buildPolicy("viewer", "reports", "read"); // GH-90000

        Policy returned = repository.save(policy); // GH-90000

        assertThat(returned).isSameAs(policy); // GH-90000
    }

    @Test
    @DisplayName("save null policy throws IllegalArgumentException [GH-90000]")
    void saveNullThrows() { // GH-90000
        assertThatThrownBy(() -> repository.save(null)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }

    @Test
    @DisplayName("findByRole returns policies for the given role [GH-90000]")
    void findByRoleReturnsPoliciesForRole() { // GH-90000
        Policy admin1 = repository.save(buildPolicy("admin", "users", "read")); // GH-90000
        Policy admin2 = repository.save(buildPolicy("admin", "reports", "read")); // GH-90000
        repository.save(buildPolicy("viewer", "users", "read")); // GH-90000

        List<Policy> adminPolicies = repository.findByRole("admin [GH-90000]");

        assertThat(adminPolicies).hasSize(2) // GH-90000
                .extracting(Policy::getRole) // GH-90000
                .containsOnly("admin [GH-90000]");
    }

    @Test
    @DisplayName("findByRole returns empty list when no policies for role [GH-90000]")
    void findByRoleReturnsEmptyListWhenNoneFound() { // GH-90000
        repository.save(buildPolicy("admin", "users", "read")); // GH-90000

        List<Policy> result = repository.findByRole("nonexistent-role [GH-90000]");

        assertThat(result).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("findByResource returns policies matching resource [GH-90000]")
    void findByResourceReturnsMatchingPolicies() { // GH-90000
        repository.save(buildPolicy("admin", "users", "read")); // GH-90000
        repository.save(buildPolicy("viewer", "users", "read")); // GH-90000
        repository.save(buildPolicy("admin", "reports", "read")); // GH-90000

        List<Policy> result = repository.findByResource("users [GH-90000]");

        assertThat(result).hasSize(2) // GH-90000
                .allMatch(p -> p.appliesTo("users [GH-90000]"));
    }

    @Test
    @DisplayName("findAll returns all stored policies [GH-90000]")
    void findAllReturnsAllPolicies() { // GH-90000
        repository.save(buildPolicy("admin", "users", "read")); // GH-90000
        repository.save(buildPolicy("viewer", "reports", "read")); // GH-90000
        repository.save(buildPolicy("editor", "documents", "write")); // GH-90000

        List<Policy> all = repository.findAll(); // GH-90000

        assertThat(all).hasSize(3); // GH-90000
    }

    @Test
    @DisplayName("findAll returns empty list when no policies stored [GH-90000]")
    void findAllReturnsEmptyWhenNoPolocies() { // GH-90000
        assertThat(repository.findAll()).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("deleteById removes policy [GH-90000]")
    void deleteByIdRemovesPolicy() { // GH-90000
        Policy policy = repository.save(buildPolicy("admin", "users", "read")); // GH-90000
        assertThat(repository.existsById(policy.getId())).isTrue(); // GH-90000

        repository.deleteById(policy.getId()); // GH-90000

        assertThat(repository.findById(policy.getId())).isEmpty(); // GH-90000
        assertThat(repository.existsById(policy.getId())).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("deleteById does nothing when policy not found [GH-90000]")
    void deleteByIdDoesNothingWhenNotFound() { // GH-90000
        assertThat(repository.existsById("nonexistent-id [GH-90000]")).isFalse();
        repository.deleteById("nonexistent-id [GH-90000]");
        assertThat(repository.existsById("nonexistent-id [GH-90000]")).isFalse();
    }

    @Test
    @DisplayName("clear removes all policies [GH-90000]")
    void clearRemovesAllPolicies() { // GH-90000
        repository.save(buildPolicy("admin", "users", "read")); // GH-90000
        repository.save(buildPolicy("viewer", "reports", "read")); // GH-90000

        repository.clear(); // GH-90000

        assertThat(repository.findAll()).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("overwriting policy with same id replaces it [GH-90000]")
    void overwritingPolicyWithSameIdReplacesIt() { // GH-90000
        Policy original = buildPolicy("admin", "users", "read"); // GH-90000
        original.setName("original [GH-90000]");
        repository.save(original); // GH-90000

        Policy updated = Policy.builder() // GH-90000
                .id(original.getId()) // GH-90000
                .name("updated [GH-90000]")
                .role("admin [GH-90000]")
                .resource("users [GH-90000]")
                .permissions(Set.of("read", "write")) // GH-90000
                .build(); // GH-90000
        repository.save(updated); // GH-90000

        assertThat(repository.findAll()).hasSize(1); // GH-90000
        assertThat(repository.findById(original.getId()).get().getName()).isEqualTo("updated [GH-90000]");
    }
}
