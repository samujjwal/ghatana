package com.ghatana.core.database;

import com.ghatana.platform.core.util.Preconditions;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Production-grade EntityManager provider with lifecycle management, thread-local storage, and auto-cleanup.
 *
 * <p><b>Purpose</b><br>
 * Manages EntityManager instances providing safe creation, thread-local caching,
 * and automatic cleanup. Supports both programmatic and declarative usage patterns
 * with resource safety guarantees via AutoCloseable.
 *
 * <p><b>Architecture Role</b><br>
 * Resource manager in core/database for EntityManager lifecycle.
 * Used by:
 * - TransactionManager - Create EntityManager for transactions
 * - Repository Layer - Obtain EntityManager for queries
 * - Service Layer - Execute operations with auto-cleanup
 * - Request Scope - Thread-local EntityManager per request
 *
 * <p><b>Lifecycle Patterns</b><br>
 * - <b>Auto-Cleanup</b>: {@code withEntityManager(Consumer/Function)} - Automatic close
 * - <b>Thread-Local</b>: {@code getThreadLocalEntityManager()} - Shared per thread
 * - <b>Manual</b>: {@code createEntityManager()} - Caller closes
 * - <b>Resource Safety</b>: Implements AutoCloseable for try-with-resources
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * EntityManagerProvider provider = new EntityManagerProvider(entityManagerFactory);
 *
 * // 1. Auto-cleanup with consumer (void)
 * provider.withEntityManager(em -> {
 *     User user = em.find(User.class, userId);
 *     user.setLastLogin(Instant.now());
 *     em.merge(user);
 *     // EntityManager automatically closed
 * });
 * 
 * // 2. Auto-cleanup with function (return value)
 * User user = provider.withEntityManager(em -> 
 *     em.find(User.class, userId)
 * );
 * 
 * // 3. Thread-local EntityManager (shared across method calls)
 * public class UserService {
 *     private final EntityManagerProvider provider;
 *     
 *     public void processUser(UserId id) {
 *         EntityManager em = provider.getThreadLocalEntityManager();
 *         
 *         User user = findUser(em, id);      // Same EM
 *         updateUser(em, user);              // Same EM
 *         auditUser(em, user);               // Same EM
 *         
 *         provider.closeThreadLocalEntityManager(); // Explicit cleanup
 *     }
 * }
 *
 * // 4. Manual management
 * EntityManager em = provider.createEntityManager();
 * try {
 *     em.getTransaction().begin();
 *     // Use EntityManager
 *     em.getTransaction().commit();
 * } finally {
 *     provider.closeEntityManager(em);
 * }
 *
 * // 5. Try-with-resources (provider cleanup)
 * try (EntityManagerProvider provider = new EntityManagerProvider(emf)) {
 *     provider.withEntityManager(em -> {
 *         // Use EntityManager
 *     });
 * } // Auto-close all EntityManagers and EntityManagerFactory
 * }</pre>
 *
 * <p><b>Thread-Local EntityManager Pattern</b><br>
 * - Same EntityManager instance returned for current thread
 * - Shared across multiple method calls in request
 * - MUST call {@code closeThreadLocalEntityManager()} to cleanup
 * - Use for "Open Session in View" or request-scoped patterns
 * - WARNING: Risk of connection leak if not closed
 *
 * <p><b>withEntityManager Patterns</b><br>
 * Preferred for most use cases - automatic resource management:
 * <pre>{@code
 * // Consumer (void return)
 * provider.withEntityManager(em -> {
 *     em.persist(entity);
 * });
 *
 * // Function (typed return)
 * List<User> users = provider.withEntityManager(em ->
 *     em.createQuery("SELECT u FROM User u", User.class).getResultList()
 * );
 * }</pre>
 *
 * <p><b>Lifecycle States</b><br>
 * - <b>Active</b>: Provider open, can create EntityManagers
 * - <b>Closed</b>: Provider closed, throws IllegalStateException
 * - Call {@code close()} to shutdown provider and cleanup resources
 *
 * <p><b>Resource Cleanup</b><br>
 * {@code close()} method:
 * 1. Closes all thread-local EntityManagers
 * 2. Closes EntityManagerFactory
 * 3. Marks provider as closed
 * 4. Subsequent operations throw IllegalStateException
 *
 * <p><b>Error Handling</b><br>
 * - {@code checkNotClosed()} validates provider state before operations
 * - Throws IllegalStateException if closed
 * - EntityManager exceptions propagated as-is
 *
 * <p><b>Thread Safety</b><br>
 * Provider is thread-safe. EntityManager is NOT thread-safe - each thread
 * gets isolated EntityManager via thread-local storage.
 *
 * @see TransactionManager
 * @see jakarta.persistence.EntityManager
 * @see jakarta.persistence.EntityManagerFactory
 * @since 1.0.0
 * @doc.type class
 * @doc.purpose EntityManager lifecycle manager with thread-local storage
 * @doc.layer core
 * @doc.pattern Factory
 */
public final class EntityManagerProvider implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(EntityManagerProvider.class);
    
    private final EntityManagerFactory entityManagerFactory;
    private final ConcurrentMap<Thread, EntityManager> threadLocalEntityManagers;
    private volatile boolean closed = false;
    
    /**
     * Creates a new EntityManagerProvider.
     * 
     * @param entityManagerFactory The EntityManagerFactory to use
     */
    public EntityManagerProvider(EntityManagerFactory entityManagerFactory) {
        if (entityManagerFactory == null) {
            throw new IllegalArgumentException("EntityManagerFactory cannot be null");
        }
        this.entityManagerFactory = entityManagerFactory;
        this.threadLocalEntityManagers = new ConcurrentHashMap<>();
        
        LOG.info("EntityManagerProvider initialized");
    }
    
    /**
     * Creates a new EntityManager instance.
     * 
     * <p>The caller is responsible for closing the EntityManager when done.
     * Consider using {@link #withEntityManager(Consumer)} or 
     * {@link #withEntityManager(Function)} for automatic cleanup.
     * 
     * @return A new EntityManager instance
     * @throws IllegalStateException if the provider is closed
     */
    public EntityManager createEntityManager() {
        checkNotClosed();
        
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        LOG.debug("Created new EntityManager: {}", entityManager);
        
        return entityManager;
    }
    
    /**
     * Gets or creates a thread-local EntityManager.
     * 
     * <p>This method returns the same EntityManager instance for the current thread
     * until it's explicitly closed or the thread ends. Use this for scenarios where
     * you need to share an EntityManager across multiple method calls in the same thread.
     * 
     * @return Thread-local EntityManager instance
     * @throws IllegalStateException if the provider is closed
     */
    public EntityManager getThreadLocalEntityManager() {
        checkNotClosed();
        
        Thread currentThread = Thread.currentThread();
        return threadLocalEntityManagers.computeIfAbsent(currentThread, thread -> {
            EntityManager em = createEntityManager();
            LOG.debug("Created thread-local EntityManager for thread: {}", thread.getName());
            return em;
        });
    }
    
    /**
     * Closes the thread-local EntityManager for the current thread.
     * 
     * @return true if an EntityManager was closed, false if none existed
     */
    public boolean closeThreadLocalEntityManager() {
        Thread currentThread = Thread.currentThread();
        EntityManager em = threadLocalEntityManagers.remove(currentThread);
        
        if (em != null && em.isOpen()) {
            closeEntityManager(em);
            LOG.debug("Closed thread-local EntityManager for thread: {}", currentThread.getName());
            return true;
        }
        
        return false;
    }
    
    /**
     * Safely closes an EntityManager.
     * 
     * @param entityManager The EntityManager to close
     */
    public void closeEntityManager(EntityManager entityManager) {
        if (entityManager != null && entityManager.isOpen()) {
            try {
                if (entityManager.getTransaction().isActive()) {
                    entityManager.getTransaction().rollback();
                    LOG.warn("Rolled back active transaction during EntityManager close");
                }
                entityManager.close();
                LOG.debug("Closed EntityManager: {}", entityManager);
            } catch (Exception e) {
                LOG.error("Error closing EntityManager: {}", entityManager, e);
            }
        }
    }
    
    /**
     * Executes a function with an EntityManager, automatically managing its lifecycle.
     * 
     * <p>The EntityManager is created before the function is called and closed
     * after the function completes, regardless of whether an exception occurs.
     * 
     * @param <T> The return type
     * @param function The function to execute
     * @return The result of the function
     * @throws IllegalStateException if the provider is closed
     */
    public <T> T withEntityManager(Function<EntityManager, T> function) {
        if (function == null) {
            throw new IllegalArgumentException("Function cannot be null");
        }
        checkNotClosed();
        
        EntityManager entityManager = createEntityManager();
        try {
            return function.apply(entityManager);
        } finally {
            closeEntityManager(entityManager);
        }
    }
    
    /**
     * Executes a consumer with an EntityManager, automatically managing its lifecycle.
     * 
     * <p>The EntityManager is created before the consumer is called and closed
     * after the consumer completes, regardless of whether an exception occurs.
     * 
     * @param consumer The consumer to execute
     * @throws IllegalStateException if the provider is closed
     */
    public void withEntityManager(Consumer<EntityManager> consumer) {
        if (consumer == null) {
            throw new IllegalArgumentException("Consumer cannot be null");
        }
        
        withEntityManager(entityManager -> {
            consumer.accept(entityManager);
            return null;
        });
    }
    
    /**
     * Executes a function with a thread-local EntityManager.
     * 
     * <p>This method uses the thread-local EntityManager, which persists across
     * multiple calls within the same thread. The EntityManager is not automatically
     * closed after the function completes - use {@link #closeThreadLocalEntityManager()}
     * when done with the thread-local EntityManager.
     * 
     * @param <T> The return type
     * @param function The function to execute
     * @return The result of the function
     * @throws IllegalStateException if the provider is closed
     */
    public <T> T withThreadLocalEntityManager(Function<EntityManager, T> function) {
        if (function == null) {
            throw new IllegalArgumentException("Function cannot be null");
        }

        EntityManager entityManager = getThreadLocalEntityManager();
        return function.apply(entityManager);
    }
    
    /**
     * Executes a consumer with a thread-local EntityManager.
     * 
     * <p>This method uses the thread-local EntityManager, which persists across
     * multiple calls within the same thread. The EntityManager is not automatically
     * closed after the consumer completes - use {@link #closeThreadLocalEntityManager()}
     * when done with the thread-local EntityManager.
     * 
     * @param consumer The consumer to execute
     * @throws IllegalStateException if the provider is closed
     */
    public void withThreadLocalEntityManager(Consumer<EntityManager> consumer) {
        if (consumer == null) {
            throw new IllegalArgumentException("Consumer cannot be null");
        }

        withThreadLocalEntityManager(entityManager -> {
            consumer.accept(entityManager);
            return null;
        });
    }
    
    /**
     * Checks if the provider is closed.
     * 
     * @return true if closed, false otherwise
     */
    public boolean isClosed() {
        return closed;
    }
    
    /**
     * Gets the underlying EntityManagerFactory.
     * 
     * @return The EntityManagerFactory
     */
    public EntityManagerFactory getEntityManagerFactory() {
        return entityManagerFactory;
    }
    
    /**
     * Gets the number of active thread-local EntityManagers.
     * 
     * @return The count of active thread-local EntityManagers
     */
    public int getActiveThreadLocalEntityManagerCount() {
        return threadLocalEntityManagers.size();
    }
    
    /**
     * Closes the provider and all associated resources.
     * 
     * <p>This method closes all active thread-local EntityManagers and marks
     * the provider as closed. After calling this method, no new EntityManagers
     * can be created.
     */
    /**
     * Gets the current thread's EntityManager, creating one if it doesn't exist.
     * 
     * @return The current thread's EntityManager
     * @throws IllegalStateException if the provider has been closed
     */
    public EntityManager getEntityManager() {
        if (closed) {
            throw new IllegalStateException("EntityManagerProvider is closed");
        }
        
        return threadLocalEntityManagers.computeIfAbsent(Thread.currentThread(), 
            t -> {
                EntityManager em = entityManagerFactory.createEntityManager();
                LOG.debug("Created new EntityManager for thread: {}", t.getName());
                return em;
            });
    }
    
    @Override
    public void close() {
        if (closed) {
            return;
        }
        
        closed = true;
        
        // Close all managed EntityManagers
        threadLocalEntityManagers.values().forEach(this::closeEntityManager);
        threadLocalEntityManagers.clear();
        
        // Close the factory
        if (entityManagerFactory != null && entityManagerFactory.isOpen()) {
            try {
                entityManagerFactory.close();
                LOG.debug("EntityManagerFactory closed");
            } catch (Exception e) {
                LOG.error("Error closing EntityManagerFactory", e);
            }
        }
    }
    
    /**
     * Checks if the provider is not closed and throws an exception if it is.
     * 
     * @throws IllegalStateException if the provider is closed
     */
    private void checkNotClosed() {
        if (closed) {
            throw new IllegalStateException("EntityManagerProvider is closed");
        }
    }
}
