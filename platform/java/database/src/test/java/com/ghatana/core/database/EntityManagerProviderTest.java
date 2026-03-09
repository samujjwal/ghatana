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
@ExtendWith(MockitoExtension.class)
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
    void setUp() {
        // Use lenient stubbing for default single-threaded tests
        lenient().when(entityManager.getTransaction()).thenReturn(transaction);
        lenient().when(entityManager.isOpen()).thenReturn(true);
        lenient().when(transaction.isActive()).thenReturn(false);

        // By default return the same mock; some tests override to return distinct Ems.
        lenient().when(entityManagerFactory.createEntityManager()).thenReturn(entityManager);
        
        provider = new EntityManagerProvider(entityManagerFactory);
    }
    
    @Test
    void testConstructorValidation() {
        assertThatThrownBy(() -> new EntityManagerProvider(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("EntityManagerFactory cannot be null");
    }
    
    @Test
    void testCreateEntityManager() {
        EntityManager em = provider.createEntityManager();
        
        assertThat(em).isNotNull();
        verify(entityManagerFactory).createEntityManager();
    }
    
    @Test
    void testCreateEntityManagerWhenClosed() {
        provider.close();
        
        assertThatThrownBy(() -> provider.createEntityManager())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("EntityManagerProvider is closed");
    }
    
    @Test
    void testGetThreadLocalEntityManager() {
        EntityManager em1 = provider.getThreadLocalEntityManager();
        EntityManager em2 = provider.getThreadLocalEntityManager();
        
        assertThat(em1).isNotNull();
        assertThat(em2).isSameAs(em1); // Same instance for same thread
        
        verify(entityManagerFactory, times(1)).createEntityManager(); // Only created once
    }
    
    @Test
    void testGetThreadLocalEntityManagerDifferentThreads() throws Exception {
        AtomicReference<EntityManager> em1Ref = new AtomicReference<>();
        AtomicReference<EntityManager> em2Ref = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(2);
        
        // Make the factory create a new mock EntityManager per call so different threads get different instances
        when(entityManagerFactory.createEntityManager()).thenAnswer(invocation -> {
            EntityManager em = mock(EntityManager.class);
            EntityTransaction tx = mock(EntityTransaction.class);
            when(em.getTransaction()).thenReturn(tx);
            when(em.isOpen()).thenReturn(true);
            when(tx.isActive()).thenReturn(false);
            return em;
        });

        // Create EntityManagers in different threads
        ExecutorService executor = Executors.newFixedThreadPool(2);
        
        executor.submit(() -> {
            try {
                em1Ref.set(provider.getThreadLocalEntityManager());
            } finally {
                latch.countDown();
            }
        });
        
        executor.submit(() -> {
            try {
                em2Ref.set(provider.getThreadLocalEntityManager());
            } finally {
                latch.countDown();
            }
        });
        
        latch.await();
        executor.shutdown();
        
        assertThat(em1Ref.get()).isNotNull();
        assertThat(em2Ref.get()).isNotNull();
        assertThat(em1Ref.get()).isNotSameAs(em2Ref.get()); // Different instances for different threads
        
        verify(entityManagerFactory, times(2)).createEntityManager(); // Created for each thread
    }
    
    @Test
    void testCloseThreadLocalEntityManager() {
        EntityManager em = provider.getThreadLocalEntityManager();
        
        boolean closed = provider.closeThreadLocalEntityManager();
        
        assertThat(closed).isTrue();
        verify(entityManager).close();
    }
    
    @Test
    void testCloseThreadLocalEntityManagerWhenNone() {
        boolean closed = provider.closeThreadLocalEntityManager();
        
        assertThat(closed).isFalse();
        verify(entityManager, never()).close();
    }
    
    @Test
    void testCloseEntityManager() {
        provider.closeEntityManager(entityManager);
        
        verify(entityManager).close();
    }
    
    @Test
    void testCloseEntityManagerWithActiveTransaction() {
        when(transaction.isActive()).thenReturn(true);
        
        provider.closeEntityManager(entityManager);
        
        verify(transaction).rollback();
        verify(entityManager).close();
    }
    
    @Test
    void testCloseEntityManagerWithNull() {
        assertThatCode(() -> provider.closeEntityManager(null))
            .doesNotThrowAnyException();
    }
    
    @Test
    void testCloseEntityManagerWhenAlreadyClosed() {
        when(entityManager.isOpen()).thenReturn(false);
        
        provider.closeEntityManager(entityManager);
        
        verify(entityManager, never()).close();
    }
    
    @Test
    void testWithEntityManager() {
        String result = provider.withEntityManager(em -> {
            assertThat(em).isNotNull();
            return "test-result";
        });
        
        assertThat(result).isEqualTo("test-result");
        verify(entityManager).close();
    }
    
    @Test
    void testWithEntityManagerException() {
        RuntimeException testException = new RuntimeException("Test exception");
        
        assertThatThrownBy(() -> 
            provider.withEntityManager((java.util.function.Function<jakarta.persistence.EntityManager, Object>) em -> {
                throw testException;
            })
        ).isSameAs(testException);
        
        verify(entityManager).close(); // Should still close on exception
    }
    
    @Test
    void testWithEntityManagerConsumer() {
        java.util.concurrent.atomic.AtomicReference<jakarta.persistence.EntityManager> emRef = new java.util.concurrent.atomic.AtomicReference<>();

        provider.withEntityManager((java.util.function.Consumer<jakarta.persistence.EntityManager>) em -> {
            emRef.set(em);
        });
        
        assertThat(emRef.get()).isNotNull();
        verify(entityManager).close();
    }
    
    @Test
    void testWithThreadLocalEntityManager() {
        String result = provider.withThreadLocalEntityManager(em -> {
            assertThat(em).isNotNull();
            return "test-result";
        });
        
        assertThat(result).isEqualTo("test-result");
        verify(entityManager, never()).close(); // Should not close thread-local EM
    }
    
    @Test
    void testWithThreadLocalEntityManagerConsumer() {
        AtomicReference<EntityManager> emRef = new AtomicReference<>();
        
        provider.withThreadLocalEntityManager(em -> {
            emRef.set(em);
        });
        
        assertThat(emRef.get()).isNotNull();
        verify(entityManager, never()).close(); // Should not close thread-local EM
    }
    
    @Test
    void testFunctionValidation() {
        assertThatThrownBy(() -> provider.withEntityManager((java.util.function.Function<EntityManager, String>) null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Function cannot be null");
        
        assertThatThrownBy(() -> provider.withEntityManager((java.util.function.Consumer<EntityManager>) null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Consumer cannot be null");
        
        assertThatThrownBy(() -> provider.withThreadLocalEntityManager((java.util.function.Function<EntityManager, String>) null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Function cannot be null");
        
        assertThatThrownBy(() -> provider.withThreadLocalEntityManager((java.util.function.Consumer<EntityManager>) null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Consumer cannot be null");
    }
    
    @Test
    void testIsClosed() {
        assertThat(provider.isClosed()).isFalse();
        
        provider.close();
        
        assertThat(provider.isClosed()).isTrue();
    }
    
    @Test
    void testGetEntityManagerFactory() {
        assertThat(provider.getEntityManagerFactory()).isSameAs(entityManagerFactory);
    }
    
    @Test
    void testGetActiveThreadLocalEntityManagerCount() {
        assertThat(provider.getActiveThreadLocalEntityManagerCount()).isEqualTo(0);
        
        provider.getThreadLocalEntityManager();
        
        assertThat(provider.getActiveThreadLocalEntityManagerCount()).isEqualTo(1);
        
        provider.closeThreadLocalEntityManager();
        
        assertThat(provider.getActiveThreadLocalEntityManagerCount()).isEqualTo(0);
    }
    
    @Test
    void testClose() {
        // Create thread-local EntityManager
        provider.getThreadLocalEntityManager();
        
        when(entityManagerFactory.isOpen()).thenReturn(true);
        
        provider.close();
        
        assertThat(provider.isClosed()).isTrue();
        verify(entityManager).close(); // Thread-local EM should be closed
        verify(entityManagerFactory).close(); // EMF should be closed
    }
    
    @Test
    void testCloseIdempotent() {
        provider.close();
        provider.close(); // Should not throw exception
        
        assertThat(provider.isClosed()).isTrue();
    }
    
    @Test
    void testCloseWithClosedEntityManagerFactory() {
        when(entityManagerFactory.isOpen()).thenReturn(false);
        
        provider.close();
        
        verify(entityManagerFactory, never()).close(); // Should not try to close already closed EMF
    }
}
