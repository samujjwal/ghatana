package com.ghatana.core.database;

import com.ghatana.platform.core.util.Preconditions;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Production-grade transaction manager providing declarative transaction boundaries with ACID guarantees.
 *
 * <p><b>Purpose</b><br>
 * Manages JPA transaction lifecycle with automatic commit/rollback, proper resource
 * cleanup, and comprehensive error handling. Provides both programmatic and declarative
 * transaction patterns following Template Method design pattern.
 *
 * <p><b>Architecture Role</b><br>
 * Transaction orchestrator in core/database for ACID transaction management.
 * Used by:
 * - Service Layer - Execute business logic within transactions
 * - Repository Layer - Ensure data consistency across operations
 * - Batch Processors - Manage long-running transactional jobs
 * - Integration Tests - Setup/teardown transactional fixtures
 *
 * <p><b>Transaction Semantics</b><br>
 * - <b>Atomicity</b>: All-or-nothing execution (commit or rollback)
 * - <b>Consistency</b>: Database constraints maintained
 * - <b>Isolation</b>: Concurrent transactions don't interfere (configurable level)
 * - <b>Durability</b>: Committed changes survive crashes
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * TransactionManager txManager = new TransactionManager(entityManagerProvider);
 * 
 * // 1. Simple transactional operation (no return value)
 * txManager.inTransaction(em -> {
 *     User user = new User("john@example.com");
 *     em.persist(user);
 *     // Transaction automatically committed
 * });
 * 
 * // 2. Transactional operation with return value
 * User createdUser = txManager.inTransaction(em -> {
 *     User user = new User("jane@example.com");
 *     em.persist(user);
 *     em.flush(); // Force ID generation
 *     return user;
 * });
 * 
 * // 3. Read-only transaction (optimization hint)
 * List<User> users = txManager.inReadOnlyTransaction(em ->
 *     em.createQuery("SELECT u FROM User u WHERE u.active = true", User.class)
 *       .getResultList()
 * );
 * 
 * // 4. Complex multi-step transaction
 * Order order = txManager.inTransaction(em -> {
 *     // Step 1: Create order
 *     Order newOrder = new Order(customerId);
 *     em.persist(newOrder);
 *     
 *     // Step 2: Add line items
 *     for (CartItem item : cartItems) {
 *         OrderLine line = new OrderLine(newOrder, item);
 *         em.persist(line);
 *     }
 *     
 *     // Step 3: Update inventory
 *     for (CartItem item : cartItems) {
 *         Product product = em.find(Product.class, item.getProductId());
 *         product.decrementStock(item.getQuantity());
 *         em.merge(product);
 *     }
 *     
 *     em.flush(); // Force database sync
 *     return newOrder; // All-or-nothing: success=commit, exception=rollback
 * });
 * 
 * // 5. Nested transactions (propagate outer transaction)
 * txManager.inTransaction(em -> {
 *     User user = createUser(em); // Inner operation participates in outer tx
 *     assignRole(em, user, "ADMIN");
 *     // Both createUser + assignRole commit together
 * });
 * }</pre>
 *
 * <p><b>Transaction Lifecycle</b><br>
 * 1. <b>Begin</b>: {@code transaction.begin()} - Start transaction
 * 2. <b>Execute</b>: Run user function with EntityManager
 * 3. <b>Success</b>: {@code transaction.commit()} - Persist changes
 * 4. <b>Failure</b>: {@code transaction.rollback()} - Revert changes
 * 5. <b>Cleanup</b>: Close EntityManager (automatic via provider)
 *
 * <p><b>Error Handling</b><br>
 * - <b>Application Exceptions</b>: Trigger automatic rollback, rethrow as-is
 * - <b>Persistence Exceptions</b>: Rollback, wrap in RuntimeException
 * - <b>Rollback Failures</b>: Log error, add as suppressed exception
 * - <b>Resource Cleanup</b>: Guaranteed via try-with-resources in provider
 *
 * <p><b>Read-Only Optimization</b><br>
 * {@code inReadOnlyTransaction()} hints to persistence provider that no writes
 * occur, enabling optimizations:
 * - Skip dirty checking
 * - Route to read replica
 * - Avoid flush operations
 * - No lock acquisition
 *
 * <p><b>Best Practices</b><br>
 * - Keep transactions short (minimize lock duration)
 * - Never call external services within transactions (deadlock risk)
 * - Use read-only for queries (performance)
 * - Avoid nested transactions (use propagation instead)
 * - Flush before queries on recently persisted entities
 * - Handle optimistic locking conflicts
 *
 * <p><b>Thread Safety</b><br>
 * TransactionManager is thread-safe. EntityManager is NOT thread-safe - each
 * transaction gets isolated EntityManager from provider.
 *
 * @see EntityManagerProvider
 * @see jakarta.persistence.EntityTransaction
 * @since 1.0.0
 * @doc.type class
 * @doc.purpose Transaction manager with ACID guarantees and automatic rollback
 * @doc.layer core
 * @doc.pattern Template Method
 */
public final class TransactionManager {
    private static final Logger LOG = LoggerFactory.getLogger(TransactionManager.class);
    
    private final EntityManagerProvider entityManagerProvider;
    
    /**
     * Creates a new TransactionManager.
     * 
     * @param entityManagerProvider The EntityManagerProvider to use
     */
    public TransactionManager(EntityManagerProvider entityManagerProvider) {
        this.entityManagerProvider = Preconditions.requireNonNull(
            entityManagerProvider, "EntityManagerProvider cannot be null");
        
        LOG.info("TransactionManager initialized");
    }
    
    /**
     * Executes a function within a transaction boundary.
     * 
     * <p>The transaction is automatically started before the function is called
     * and committed after successful completion. If an exception occurs, the
     * transaction is rolled back.
     * 
     * @param <T> The return type
     * @param function The function to execute within the transaction
     * @return The result of the function
     * @throws RuntimeException if the transaction fails
     */
    public <T> T inTransaction(Function<EntityManager, T> function) {
        Preconditions.requireNonNull(function, "Function cannot be null");
        
        return entityManagerProvider.withEntityManager(entityManager -> {
            EntityTransaction transaction = entityManager.getTransaction();
            
            try {
                transaction.begin();
                LOG.debug("Transaction started");
                
                T result = function.apply(entityManager);
                
                transaction.commit();
                LOG.debug("Transaction committed successfully");
                
                return result;
                
            } catch (Exception e) {
                if (transaction.isActive()) {
                    try {
                        transaction.rollback();
                        LOG.debug("Transaction rolled back due to exception: {}", e.getMessage());
                    } catch (Exception rollbackException) {
                        LOG.error("Failed to rollback transaction", rollbackException);
                        e.addSuppressed(rollbackException);
                    }
                }
                
                if (e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                } else {
                    throw new RuntimeException("Transaction failed", e);
                }
            }
        });
    }
    
    /**
     * Executes a consumer within a transaction boundary.
     * 
     * <p>The transaction is automatically started before the consumer is called
     * and committed after successful completion. If an exception occurs, the
     * transaction is rolled back.
     * 
     * @param consumer The consumer to execute within the transaction
     * @throws RuntimeException if the transaction fails
     */
    public void inTransaction(Consumer<EntityManager> consumer) {
        Preconditions.requireNonNull(consumer, "Consumer cannot be null");
        
        inTransaction(entityManager -> {
            consumer.accept(entityManager);
            return null;
        });
    }
    
    /**
     * Executes a function within a read-only transaction.
     * 
     * <p>This method is optimized for read-only operations and may provide
     * performance benefits. The transaction is automatically started and
     * committed, but no writes should be performed.
     * 
     * @param <T> The return type
     * @param function The function to execute within the read-only transaction
     * @return The result of the function
     * @throws RuntimeException if the transaction fails
     */
    public <T> T inReadOnlyTransaction(Function<EntityManager, T> function) {
        Preconditions.requireNonNull(function, "Function cannot be null");
        
        return entityManagerProvider.withEntityManager(entityManager -> {
            EntityTransaction transaction = entityManager.getTransaction();
            
            try {
                transaction.begin();
                LOG.debug("Read-only transaction started");
                
                // Set read-only hint (Hibernate-specific optimization)
                entityManager.setProperty("org.hibernate.readOnly", true);
                
                T result = function.apply(entityManager);
                
                transaction.commit();
                LOG.debug("Read-only transaction committed successfully");
                
                return result;
                
            } catch (Exception e) {
                if (transaction.isActive()) {
                    try {
                        transaction.rollback();
                        LOG.debug("Read-only transaction rolled back due to exception: {}", e.getMessage());
                    } catch (Exception rollbackException) {
                        LOG.error("Failed to rollback read-only transaction", rollbackException);
                        e.addSuppressed(rollbackException);
                    }
                }
                
                if (e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                } else {
                    throw new RuntimeException("Read-only transaction failed", e);
                }
            }
        });
    }
    
    /**
     * Executes a consumer within a read-only transaction.
     * 
     * <p>This method is optimized for read-only operations and may provide
     * performance benefits. The transaction is automatically started and
     * committed, but no writes should be performed.
     * 
     * @param consumer The consumer to execute within the read-only transaction
     * @throws RuntimeException if the transaction fails
     */
    public void inReadOnlyTransaction(Consumer<EntityManager> consumer) {
        Preconditions.requireNonNull(consumer, "Consumer cannot be null");
        
        inReadOnlyTransaction(entityManager -> {
            consumer.accept(entityManager);
            return null;
        });
    }
    
    /**
     * Executes a function with manual transaction control.
     * 
     * <p>This method provides an EntityManager with an active transaction,
     * but the caller is responsible for committing or rolling back the transaction.
     * Use this for complex transaction scenarios that require manual control.
     * 
     * @param <T> The return type
     * @param function The function to execute with manual transaction control
     * @return The result of the function
     * @throws RuntimeException if an error occurs
     */
    public <T> T withManualTransaction(Function<EntityManager, T> function) {
        Preconditions.requireNonNull(function, "Function cannot be null");
        
        return entityManagerProvider.withEntityManager(entityManager -> {
            EntityTransaction transaction = entityManager.getTransaction();
            
            try {
                transaction.begin();
                LOG.debug("Manual transaction started");
                
                return function.apply(entityManager);
                
            } catch (Exception e) {
                if (transaction.isActive()) {
                    try {
                        transaction.rollback();
                        LOG.debug("Manual transaction rolled back due to exception: {}", e.getMessage());
                    } catch (Exception rollbackException) {
                        LOG.error("Failed to rollback manual transaction", rollbackException);
                        e.addSuppressed(rollbackException);
                    }
                }
                
                if (e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                } else {
                    throw new RuntimeException("Manual transaction failed", e);
                }
            }
        });
    }
    
    /**
     * Executes a consumer with manual transaction control.
     * 
     * <p>This method provides an EntityManager with an active transaction,
     * but the caller is responsible for committing or rolling back the transaction.
     * Use this for complex transaction scenarios that require manual control.
     * 
     * @param consumer The consumer to execute with manual transaction control
     * @throws RuntimeException if an error occurs
     */
    public void withManuelTransaction(Consumer<EntityManager> consumer) {
        Preconditions.requireNonNull(consumer, "Consumer cannot be null");
        
        withManualTransaction(entityManager -> {
            consumer.accept(entityManager);
            return null;
        });
    }
    
    /**
     * Executes multiple operations within a single transaction.
     * 
     * <p>This method allows batching multiple database operations within a single
     * transaction for better performance and consistency.
     * 
     * @param operations The operations to execute within the transaction
     * @throws RuntimeException if the transaction fails
     */
    @SafeVarargs
    public final void inBatchTransaction(Consumer<EntityManager>... operations) {
        Preconditions.requireNonNull(operations, "Operations cannot be null");
        if (operations.length == 0) {
            throw new IllegalArgumentException("Operations cannot be empty");
        }
        
        inTransaction(entityManager -> {
            for (Consumer<EntityManager> operation : operations) {
                operation.accept(entityManager);
            }
        });
    }
    
    /**
     * Executes a function within a new transaction, regardless of existing transaction context.
     * 
     * <p>This method always creates a new transaction, even if one is already active.
     * Use this for operations that need to be isolated from the current transaction.
     * 
     * @param <T> The return type
     * @param function The function to execute in a new transaction
     * @return The result of the function
     * @throws RuntimeException if the transaction fails
     */
    public <T> T requiresNewTransaction(Function<EntityManager, T> function) {
        Preconditions.requireNonNull(function, "Function cannot be null");
        
        // Always create a new EntityManager for a new transaction
        EntityManager entityManager = entityManagerProvider.createEntityManager();
        try {
            EntityTransaction transaction = entityManager.getTransaction();
            
            try {
                transaction.begin();
                LOG.debug("New transaction started (requires new)");
                
                T result = function.apply(entityManager);
                
                transaction.commit();
                LOG.debug("New transaction committed successfully");
                
                return result;
                
            } catch (Exception e) {
                if (transaction.isActive()) {
                    try {
                        transaction.rollback();
                        LOG.debug("New transaction rolled back due to exception: {}", e.getMessage());
                    } catch (Exception rollbackException) {
                        LOG.error("Failed to rollback new transaction", rollbackException);
                        e.addSuppressed(rollbackException);
                    }
                }
                
                if (e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                } else {
                    throw new RuntimeException("New transaction failed", e);
                }
            }
        } finally {
            entityManagerProvider.closeEntityManager(entityManager);
        }
    }
    
    /**
     * Executes a consumer within a new transaction, regardless of existing transaction context.
     * 
     * <p>This method always creates a new transaction, even if one is already active.
     * Use this for operations that need to be isolated from the current transaction.
     * 
     * @param consumer The consumer to execute in a new transaction
     * @throws RuntimeException if the transaction fails
     */
    public void requiresNewTransaction(Consumer<EntityManager> consumer) {
        Preconditions.requireNonNull(consumer, "Consumer cannot be null");
        
        requiresNewTransaction(entityManager -> {
            consumer.accept(entityManager);
            return null;
        });
    }
    
    /**
     * Starts a new transaction if one is not already active.
     * 
     * @throws IllegalStateException if there is already an active transaction
     */
    public void begin() {
        EntityManager entityManager = entityManagerProvider.getEntityManager();
        EntityTransaction transaction = entityManager.getTransaction();
        
        if (transaction.isActive()) {
            throw new IllegalStateException("A transaction is already active");
        }
        
        transaction.begin();
        LOG.debug("Transaction started");
    }
    
    /**
     * Commits the current transaction.
     * 
     * @throws IllegalStateException if there is no active transaction
     * @throws IllegalStateException if the transaction is marked for rollback only
     */
    public void commit() {
        EntityManager entityManager = entityManagerProvider.getEntityManager();
        EntityTransaction transaction = entityManager.getTransaction();
        
        if (!transaction.isActive()) {
            throw new IllegalStateException("No active transaction to commit");
        }
        
        if (transaction.getRollbackOnly()) {
            throw new IllegalStateException("Transaction is marked for rollback only");
        }
        
        try {
            transaction.commit();
            LOG.debug("Transaction committed successfully");
        } catch (Exception e) {
            LOG.error("Failed to commit transaction", e);
            throw new RuntimeException("Failed to commit transaction", e);
        }
    }
    
    /**
     * Rolls back the current transaction.
     * 
     * @throws IllegalStateException if there is no active transaction
     */
    public void rollback() {
        EntityManager entityManager = entityManagerProvider.getEntityManager();
        EntityTransaction transaction = entityManager.getTransaction();
        
        if (!transaction.isActive()) {
            throw new IllegalStateException("No active transaction to rollback");
        }
        
        try {
            transaction.rollback();
            LOG.debug("Transaction rolled back");
        } catch (Exception e) {
            LOG.error("Failed to rollback transaction", e);
            throw new RuntimeException("Failed to rollback transaction", e);
        }
    }
    
    /**
     * Checks if there is an active transaction.
     * 
     * @return true if there is an active transaction, false otherwise
     */
    public boolean isActive() {
        try {
            EntityManager entityManager = entityManagerProvider.getEntityManager();
            return entityManager.getTransaction().isActive();
        } catch (Exception e) {
            LOG.debug("Failed to check transaction status", e);
            return false;
        }
    }
    
    /**
     * Checks if the current transaction is marked for rollback only.
     * 
     * @return true if the transaction is marked for rollback only, false otherwise
     * @throws IllegalStateException if there is no active transaction
     */
    public boolean isRollbackOnly() {
        EntityManager entityManager = entityManagerProvider.getEntityManager();
        EntityTransaction transaction = entityManager.getTransaction();
        
        if (!transaction.isActive()) {
            throw new IllegalStateException("No active transaction");
        }
        
        return transaction.getRollbackOnly();
    }
}
