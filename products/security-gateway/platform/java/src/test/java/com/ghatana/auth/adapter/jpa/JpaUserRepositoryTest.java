package com.ghatana.auth.adapter.jpa;

import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.domain.auth.User;
import com.ghatana.platform.domain.auth.UserId;
import com.ghatana.platform.domain.auth.UserStatus;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("JPA User Repository Tests")
@ExtendWith(MockitoExtension.class)
class JpaUserRepositoryTest extends EventloopTestBase {

    @Mock
    private EntityManager entityManager;

    @Mock
    private EntityTransaction transaction;

    @Mock
    private TypedQuery<UserEntity> userQuery;

    private JpaUserRepository repository;

    @BeforeEach
    void setUp() {
        repository = new JpaUserRepository(entityManager);
    }

    @Test
    @DisplayName("findByEmail returns empty when the user does not exist")
    void findByEmailReturnsEmptyWhenMissing() {
        TenantId tenantId = TenantId.of("tenant-1");

        when(entityManager.createQuery(any(String.class), eq(UserEntity.class))).thenReturn(userQuery);
        when(userQuery.setParameter(eq("tenantId"), eq("tenant-1"))).thenReturn(userQuery);
        when(userQuery.setParameter(eq("email"), eq("missing@example.com"))).thenReturn(userQuery);
        when(userQuery.getSingleResult()).thenThrow(new NoResultException());

        Optional<User> user = runPromise(() -> repository.findByEmail(tenantId, "missing@example.com"));

        assertThat(user).isEmpty();
    }

    @Test
    @DisplayName("authenticate returns empty when password does not match")
    void authenticateReturnsEmptyWhenPasswordDoesNotMatch() {
        TenantId tenantId = TenantId.of("tenant-1");
        UserEntity entity = new UserEntity("tenant-1", sampleUser("stored-password"));

        when(entityManager.createQuery(any(String.class), eq(UserEntity.class))).thenReturn(userQuery);
        when(userQuery.setParameter(eq("tenantId"), eq("tenant-1"))).thenReturn(userQuery);
        when(userQuery.setParameter(eq("username"), eq("alice@example.com"))).thenReturn(userQuery);
        when(userQuery.getSingleResult()).thenReturn(entity);

        Optional<User> user = runPromise(() -> repository.authenticate(tenantId, "alice@example.com", "wrong-password"));

        assertThat(user).isEmpty();
    }

    @Test
    @DisplayName("save rolls back the transaction when persist fails")
    void saveRollsBackTransactionWhenPersistFails() {
        when(entityManager.getTransaction()).thenReturn(transaction);
        when(transaction.isActive()).thenReturn(true);

        RuntimeException failure = new RuntimeException("db write failed");
        org.mockito.Mockito.doThrow(failure).when(entityManager).persist(any(UserEntity.class));

        assertThatThrownBy(() -> runPromise(() -> repository.save(sampleUser("stored-password"))))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("db write failed");

        verify(transaction).begin();
        verify(transaction).rollback();
        verify(transaction, never()).commit();
    }

    @Test
    @DisplayName("save commits the transaction on success")
    void saveCommitsTransactionOnSuccess() {
        when(entityManager.getTransaction()).thenReturn(transaction);

        User saved = runPromise(() -> repository.save(sampleUser("stored-password")));

        assertThat(saved.getEmail()).isEqualTo("alice@example.com");
        verify(transaction).begin();
        verify(entityManager).persist(any(UserEntity.class));
        verify(transaction).commit();
        verify(transaction, never()).rollback();
    }

    private User sampleUser(String passwordHash) {
        return User.forInternalAuth()
                .tenantId(TenantId.of("tenant-1"))
                .userId(UserId.of(UUID.fromString("11111111-1111-1111-1111-111111111111").toString()))
                .email("alice@example.com")
                .username("alice@example.com")
                .displayName("Alice")
                .passwordHash(passwordHash)
                .status(UserStatus.ACTIVE)
                .createdAt(Instant.parse("2026-04-16T10:15:30Z"))
                .updatedAt(Instant.parse("2026-04-16T10:16:30Z"))
                .build();
    }
}