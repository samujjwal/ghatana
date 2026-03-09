package com.ghatana.auth.adapter.memory;

import com.ghatana.auth.core.port.UserRepository;
import com.ghatana.platform.domain.auth.User;
import com.ghatana.platform.domain.auth.UserId;
import com.ghatana.platform.domain.auth.UserStatus;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for InMemoryUserRepository.
 *
 * Tests validate:
 * - CRUD operations (create, read, update, delete)
 * - Tenant isolation (queries scoped to tenant)
 * - Email uniqueness per tenant
 * - Promise-based async operations
 * - Error handling (null parameters, duplicates)
 */
@DisplayName("InMemory User Repository Tests")
class InMemoryUserRepositoryTest extends EventloopTestBase {

    private UserRepository repository;
    private TenantId tenant1;
    private TenantId tenant2;

    @BeforeEach
    void setUp() {
        repository = new InMemoryUserRepository();
        tenant1 = TenantId.of("tenant-1");
        tenant2 = TenantId.of("tenant-2");
    }

    /**
     * Should save and retrieve user in same tenant.
     *
     * GIVEN: InMemory repository with tenant-1
     * WHEN: save user alice, then findByEmail
     * THEN: User retrieved with same email
     */
    @Test
    @DisplayName("Should save and retrieve user by email")
    void shouldSaveAndRetrieveUserByEmail() {
        // GIVEN
        User alice = createUser(tenant1, "alice@example.com", "Alice");

        // WHEN
        User saved = runPromise(() -> repository.save(alice));
        Optional<User> retrieved = runPromise(() -> repository.findByEmail(tenant1, "alice@example.com"));

        // THEN
        assertThat(saved).isNotNull();
        assertThat(saved.getEmail()).isEqualTo("alice@example.com");
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getEmail()).isEqualTo("alice@example.com");
    }

    /**
     * Should enforce tenant isolation - different tenants have separate user spaces.
     *
     * GIVEN: Two tenants with same email
     * WHEN: save alice to tenant-1 and tenant-2
     * THEN: Each tenant has separate user instance
     */
    @Test
    @DisplayName("Should enforce tenant isolation - same email allowed in different tenants")
    void shouldEnforceTenantIsolation() {
        // GIVEN
        User alice1 = createUser(tenant1, "alice@example.com", "Alice Tenant1");
        User alice2 = createUser(tenant2, "alice@example.com", "Alice Tenant2");

        // WHEN
        runPromise(() -> repository.save(alice1));
        runPromise(() -> repository.save(alice2));

        Optional<User> tenant1User = runPromise(() -> repository.findByEmail(tenant1, "alice@example.com"));
        Optional<User> tenant2User = runPromise(() -> repository.findByEmail(tenant2, "alice@example.com"));

        // THEN
        assertThat(tenant1User).isPresent();
        assertThat(tenant1User.get().getDisplayName()).isEqualTo("Alice Tenant1");

        assertThat(tenant2User).isPresent();
        assertThat(tenant2User.get().getDisplayName()).isEqualTo("Alice Tenant2");
    }

    /**
     * Should reject duplicate email within same tenant.
     *
     * GIVEN: User alice in tenant-1
     * WHEN: Try to save another user with same email to tenant-1
     * THEN: Exception thrown
     */
    @Test
    @DisplayName("Should reject duplicate email within tenant")
    void shouldRejectDuplicateEmailWithinTenant() {
        // GIVEN
        User alice1 = createUser(tenant1, "alice@example.com", "Alice");
        User alice2 = createUser(tenant1, "alice@example.com", "Alice 2");

        runPromise(() -> repository.save(alice1));

        // WHEN & THEN
        assertThatThrownBy(() -> runPromise(() -> repository.save(alice2)))
            .as("Should reject duplicate email in same tenant")
            .hasMessageContaining("Email already in use");
    }

    /**
     * Should retrieve user by ID.
     *
     * GIVEN: Saved user alice with ID
     * WHEN: findById called with same ID and tenant
     * THEN: User retrieved
     */
    @Test
    @DisplayName("Should retrieve user by ID and tenant")
    void shouldRetrieveUserById() {
        // GIVEN
        User alice = createUser(tenant1, "alice@example.com", "Alice");
        User saved = runPromise(() -> repository.save(alice));
        UserId userId = saved.getUserId();

        // WHEN
        Optional<User> retrieved = runPromise(() -> repository.findByUserId(tenant1, userId));

        // THEN
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getEmail()).isEqualTo("alice@example.com");
    }

    /**
     * Should return empty for non-existent user.
     *
     * GIVEN: No user with email nonexistent@example.com
     * WHEN: findByEmail called
     * THEN: Optional.empty() returned
     */
    @Test
    @DisplayName("Should return empty for non-existent user")
    void shouldReturnEmptyForNonExistent() {
        // GIVEN
        // No setup needed

        // WHEN
        Optional<User> result = runPromise(() -> repository.findByEmail(tenant1, "nonexistent@example.com"));

        // THEN
        assertThat(result).isEmpty();
    }

    /**
     * Should delete user and make unavailable.
     *
     * GIVEN: User alice saved
     * WHEN: delete called, then findByEmail
     * THEN: User no longer found
     */
    @Test
    @DisplayName("Should delete user")
    void shouldDeleteUser() {
        // GIVEN
        User alice = createUser(tenant1, "alice@example.com", "Alice");
        User saved = runPromise(() -> repository.save(alice));

        // WHEN
        runPromise(() -> repository.delete(tenant1, saved.getUserId()));
        Optional<User> result = runPromise(() -> repository.findByEmail(tenant1, "alice@example.com"));

        // THEN
        assertThat(result).isEmpty();
    }

    /**
     * Should update user.
     *
     * GIVEN: User alice saved
     * WHEN: update called with new display name
     * THEN: Changes persisted
     */
    @Test
    @DisplayName("Should update user")
    void shouldUpdateUser() {
        // GIVEN
        User alice = createUser(tenant1, "alice@example.com", "Alice");
        User saved = runPromise(() -> repository.save(alice));

        User updated = User.forInternalAuth()
                .tenantId(tenant1)
                .userId(saved.getUserId())
                .username(saved.getUsername())
                .email(saved.getEmail())
                .status(UserStatus.ACTIVE)
                .passwordHash(saved.getPasswordHash().orElse("hash"))
                .displayName("Alice Updated")
                .build();

        // WHEN
        runPromise(() -> repository.save(updated));
        Optional<User> retrieved = runPromise(() -> repository.findByUserId(tenant1, saved.getUserId()));

        // THEN
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getDisplayName()).isEqualTo("Alice Updated");
    }

    /**
     * Should reject null parameters.
     *
     * GIVEN: Repository with invalid inputs
     * WHEN: save called with null tenantId
     * THEN: Exception thrown
     */
    @Test
    @DisplayName("Should reject null tenantId in queries")
    void shouldRejectNullTenant() {
        // GIVEN
        User alice = createUser(tenant1, "alice@example.com", "Alice");
        runPromise(() -> repository.save(alice));

        // WHEN & THEN
        assertThatThrownBy(() -> runPromise(() -> repository.findByEmail(null, "alice@example.com")))
            .as("Should reject null tenantId")
            .hasMessageContaining("tenantId must not be null");
    }

    /**
     * Should reject null user in save.
     *
     * GIVEN: Repository with null user
     * WHEN: save called with null user
     * THEN: Exception thrown
     */
    @Test
    @DisplayName("Should reject null user in save")
    void shouldRejectNullUser() {
        // WHEN & THEN
        assertThatThrownBy(() -> runPromise(() -> repository.save(null)))
            .as("Should reject null user")
            .hasMessageContaining("user must not be null");
    }

    private User createUser(TenantId tenant, String email, String displayName) {
        return User.forInternalAuth()
                .tenantId(tenant)
                .userId(UserId.random())
                .username(displayName.toLowerCase().replace(" ", ""))
                .email(email)
                .status(UserStatus.ACTIVE)
                .passwordHash("hash")
                .displayName(displayName)
                .build();
    }
}
