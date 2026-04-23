package com.ghatana.core.database;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for EntityManagerProvider.
 */
@ExtendWith(MockitoExtension.class) // GH-90000
class EntityManagerProviderTest {

    @Mock
    private EntityManagerFactory entityManagerFactory;

    @Mock
    private EntityManager entityManager;

    @Mock
    private EntityTransaction transaction;

    private EntityManagerProvider provider;
    private Thread testThread;

    @BeforeEach
    void setUp() { // GH-90000
        // Use lenient stubbing for default single-threaded tests
        lenient().when(entityManager.getTransaction()).thenReturn(transaction); // GH-90000
        lenient().when(entityManager.isOpen()).thenReturn(true); // GH-90000
        lenient().when(transaction.isActive()).thenReturn(false); // GH-90000

        // By default return the same mock; some tests override to return distinct Ems.
        lenient().when(entityManagerFactory.createEntityManager()).thenReturn(entityManager); // GH-90000

        provider = new EntityManagerProvider(entityManagerFactory); // GH-90000
    }

    @Test
    void testConstructorValidation() { // GH-90000
        assertThatThrownBy(() -> new EntityManagerProvider(null)) // GH-90000
            .isInstanceOf(IllegalArgumentException.class) // GH-90000
            .hasMessageContaining("EntityManagerFactory cannot be null");
    }

    @Test
    void testCreateEntityManager() { // GH-90000
        EntityManager em = provider.createEntityManager(); // GH-90000

        assertThat(em).isNotNull(); // GH-90000
        verify(entityManagerFactory).createEntityManager(); // GH-90000
    }

    @Test
    void testCreateEntityManagerWhenClosed() { // GH-90000
        provider.close(); // GH-90000

        assertThatThrownBy(() -> provider.createEntityManager()) // GH-90000
            .isInstanceOf(IllegalStateException.class) // GH-90000
            .hasMessageContaining("EntityManagerProvider is closed");
    }

    @Test
    void testGetThreadLocalEntityManager() { // GH-90000
        EntityManager em1 = provider.getThreadLocalEntityManager(); // GH-90000
        EntityManager em2 = provider.getThreadLocalEntityManager(); // GH-90000

        assertThat(em1).isNotNull(); // GH-90000
        assertThat(em2).isSameAs(em1); // Same instance for same thread // GH-90000

        verify(entityManagerFactory, times(1)).createEntityManager(); // Only created once // GH-90000
    }

    @Test
    void testGetThreadLocalEntityManagerDifferentThreads() throws Exception { // GH-90000
        AtomicReference<EntityManager> em1Ref = new AtomicReference<>(); // GH-90000
        AtomicReference<EntityManager> em2Ref = new AtomicReference<>(); // GH-90000
        CountDownLatch latch = new CountDownLatch(2); // GH-90000

        // Make the factory create a new mock EntityManager per call so different threads get different instances
        when(entityManagerFactory.createEntityManager()).thenAnswer(invocation -> { // GH-90000
            EntityManager em = mock(EntityManager.class); // GH-90000
            EntityTransaction tx = mock(EntityTransaction.class); // GH-90000
            when(em.getTransaction()).thenReturn(tx); // GH-90000
            when(em.isOpen()).thenReturn(true); // GH-90000
            when(tx.isActive()).thenReturn(false); // GH-90000
            return em;
        });

        // Create EntityManagers in different threads
        ExecutorService executor = Executors.newFixedThreadPool(2); // GH-90000

        executor.submit(() -> { // GH-90000
            try {
                em1Ref.set(provider.getThreadLocalEntityManager()); // GH-90000
            } finally {
                latch.countDown(); // GH-90000
            }
        });

        executor.submit(() -> { // GH-90000
            try {
                em2Ref.set(provider.getThreadLocalEntityManager()); // GH-90000
            } finally {
                latch.countDown(); // GH-90000
            }
        });

        latch.await(); // GH-90000
        executor.shutdown(); // GH-90000

        assertThat(em1Ref.get()).isNotNull(); // GH-90000
        assertThat(em2Ref.get()).isNotNull(); // GH-90000
        assertThat(em1Ref.get()).isNotSameAs(em2Ref.get()); // Different instances for different threads // GH-90000

        verify(entityManagerFactory, times(2)).createEntityManager(); // Created for each thread // GH-90000
    }

    @Test
    void testCloseThreadLocalEntityManager() { // GH-90000
        EntityManager em = provider.getThreadLocalEntityManager(); // GH-90000

        boolean closed = provider.closeThreadLocalEntityManager(); // GH-90000

        assertThat(closed).isTrue(); // GH-90000
        verify(entityManager).close(); // GH-90000
    }

    @Test
    void testCloseThreadLocalEntityManagerWhenNone() { // GH-90000
        boolean closed = provider.closeThreadLocalEntityManager(); // GH-90000

        assertThat(closed).isFalse(); // GH-90000
        verify(entityManager, never()).close(); // GH-90000
    }

    @Test
    void testCloseEntityManager() { // GH-90000
        provider.closeEntityManager(entityManager); // GH-90000

        verify(entityManager).close(); // GH-90000
    }

    @Test
    void testCloseEntityManagerWithActiveTransaction() { // GH-90000
        when(transaction.isActive()).thenReturn(true); // GH-90000

        provider.closeEntityManager(entityManager); // GH-90000

        verify(transaction).rollback(); // GH-90000
        verify(entityManager).close(); // GH-90000
    }

    @Test
    void testCloseEntityManagerWithNull() { // GH-90000
        assertThatCode(() -> provider.closeEntityManager(null)) // GH-90000
            .doesNotThrowAnyException(); // GH-90000
    }

    @Test
    void testCloseEntityManagerWhenAlreadyClosed() { // GH-90000
        when(entityManager.isOpen()).thenReturn(false); // GH-90000

        provider.closeEntityManager(entityManager); // GH-90000

        verify(entityManager, never()).close(); // GH-90000
    }

    @Test
    void testWithEntityManager() { // GH-90000
        String result = provider.withEntityManager(em -> { // GH-90000
            assertThat(em).isNotNull(); // GH-90000
            return "test-result";
        });

        assertThat(result).isEqualTo("test-result");
        verify(entityManager).close(); // GH-90000
    }

    @Test
    void testWithEntityManagerException() { // GH-90000
        RuntimeException testException = new RuntimeException("Test exception");

        assertThatThrownBy(() -> // GH-90000
            provider.withEntityManager((java.util.function.Function<jakarta.persistence.EntityManager, Object>) em -> { // GH-90000
                throw testException;
            })
        ).isSameAs(testException); // GH-90000

        verify(entityManager).close(); // Should still close on exception // GH-90000
    }

    @Test
    void testWithEntityManagerConsumer() { // GH-90000
        java.util.concurrent.atomic.AtomicReference<jakarta.persistence.EntityManager> emRef = new java.util.concurrent.atomic.AtomicReference<>(); // GH-90000

        provider.withEntityManager((java.util.function.Consumer<jakarta.persistence.EntityManager>) em -> { // GH-90000
            emRef.set(em); // GH-90000
        });

        assertThat(emRef.get()).isNotNull(); // GH-90000
        verify(entityManager).close(); // GH-90000
    }

    @Test
    void testWithThreadLocalEntityManager() { // GH-90000
        String result = provider.withThreadLocalEntityManager(em -> { // GH-90000
            assertThat(em).isNotNull(); // GH-90000
            return "test-result";
        });

        assertThat(result).isEqualTo("test-result");
        verify(entityManager, never()).close(); // Should not close thread-local EM // GH-90000
    }

    @Test
    void testWithThreadLocalEntityManagerConsumer() { // GH-90000
        AtomicReference<EntityManager> emRef = new AtomicReference<>(); // GH-90000

        provider.withThreadLocalEntityManager(em -> { // GH-90000
            emRef.set(em); // GH-90000
        });

        assertThat(emRef.get()).isNotNull(); // GH-90000
        verify(entityManager, never()).close(); // Should not close thread-local EM // GH-90000
    }

    @Test
    void testFunctionValidation() { // GH-90000
        assertThatThrownBy(() -> provider.withEntityManager((java.util.function.Function<EntityManager, String>) null)) // GH-90000
            .isInstanceOf(IllegalArgumentException.class) // GH-90000
            .hasMessageContaining("Function cannot be null");

        assertThatThrownBy(() -> provider.withEntityManager((java.util.function.Consumer<EntityManager>) null)) // GH-90000
            .isInstanceOf(IllegalArgumentException.class) // GH-90000
            .hasMessageContaining("Consumer cannot be null");

        assertThatThrownBy(() -> provider.withThreadLocalEntityManager((java.util.function.Function<EntityManager, String>) null)) // GH-90000
            .isInstanceOf(IllegalArgumentException.class) // GH-90000
            .hasMessageContaining("Function cannot be null");

        assertThatThrownBy(() -> provider.withThreadLocalEntityManager((java.util.function.Consumer<EntityManager>) null)) // GH-90000
            .isInstanceOf(IllegalArgumentException.class) // GH-90000
            .hasMessageContaining("Consumer cannot be null");
    }

    @Test
    void testIsClosed() { // GH-90000
        assertThat(provider.isClosed()).isFalse(); // GH-90000

        provider.close(); // GH-90000

        assertThat(provider.isClosed()).isTrue(); // GH-90000
    }

    @Test
    void testGetEntityManagerFactory() { // GH-90000
        assertThat(provider.getEntityManagerFactory()).isSameAs(entityManagerFactory); // GH-90000
    }

    @Test
    void testGetActiveThreadLocalEntityManagerCount() { // GH-90000
        assertThat(provider.getActiveThreadLocalEntityManagerCount()).isEqualTo(0); // GH-90000

        provider.getThreadLocalEntityManager(); // GH-90000

        assertThat(provider.getActiveThreadLocalEntityManagerCount()).isEqualTo(1); // GH-90000

        provider.closeThreadLocalEntityManager(); // GH-90000

        assertThat(provider.getActiveThreadLocalEntityManagerCount()).isEqualTo(0); // GH-90000
    }

    @Test
    void testClose() { // GH-90000
        // Create thread-local EntityManager
        provider.getThreadLocalEntityManager(); // GH-90000

        when(entityManagerFactory.isOpen()).thenReturn(true); // GH-90000

        provider.close(); // GH-90000

        assertThat(provider.isClosed()).isTrue(); // GH-90000
        verify(entityManager).close(); // Thread-local EM should be closed // GH-90000
        verify(entityManagerFactory).close(); // EMF should be closed // GH-90000
    }

    @Test
    void testCloseIdempotent() { // GH-90000
        provider.close(); // GH-90000
        provider.close(); // Should not throw exception // GH-90000

        assertThat(provider.isClosed()).isTrue(); // GH-90000
    }

    @Test
    void testCloseWithClosedEntityManagerFactory() { // GH-90000
        when(entityManagerFactory.isOpen()).thenReturn(false); // GH-90000

        provider.close(); // GH-90000

        verify(entityManagerFactory, never()).close(); // Should not try to close already closed EMF // GH-90000
    }
}
