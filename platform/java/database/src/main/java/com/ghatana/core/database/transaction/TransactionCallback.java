package com.ghatana.core.database.transaction;

/**
 * Functional interface for transaction callback with context propagation and exception handling.
 *
 * <p><b>Purpose</b><br>
 * Defines contract for transactional operations with typed context and result.
 * Enables flexible transaction boundaries with automatic resource management
 * and exception propagation.
 *
 * <p><b>Architecture Role</b><br>
 * Port interface in core/database/transaction for callback-based transactions.
 * Used by:
 * - TransactionManager - Execute callbacks within transaction boundaries
 * - Service Layer - Implement transactional business logic
 * - Testing - Mock transactional behavior
 * - Batch Processing - Execute batches within transactions
 *
 * <p><b>Callback Pattern Benefits</b><br>
 * - <b>Resource Safety</b>: Automatic begin/commit/rollback/cleanup
 * - <b>Type Safety</b>: Generic types ensure compile-time safety
 * - <b>Exception Handling</b>: Checked exceptions wrapped, unchecked propagated
 * - <b>Composability</b>: Chain callbacks for complex workflows
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Simple callback with EntityManager context
 * TransactionCallback<EntityManager, User> createUser = em -> {
 *     User user = new User("john@example.com");
 *     em.persist(user);
 *     return user;
 * };
 * 
 * // Use with TransactionManager
 * User user = transactionManager.execute(createUser);
 * 
 * // Complex multi-step callback
 * TransactionCallback<EntityManager, Order> processOrder = em -> {
 *     // Step 1: Create order
 *     Order order = new Order(customerId);
 *     em.persist(order);
 *     
 *     // Step 2: Reserve inventory
 *     for (OrderLine line : order.getLines()) {
 *         Product product = em.find(Product.class, line.getProductId());
 *         if (product.getStock() < line.getQuantity()) {
 *             throw new InsufficientStockException(product.getId());
 *         }
 *         product.decrementStock(line.getQuantity());
 *         em.merge(product);
 *     }
 *     
 *     // Step 3: Update customer
 *     Customer customer = em.find(Customer.class, customerId);
 *     customer.addOrder(order);
 *     em.merge(customer);
 *     
 *     return order; // All-or-nothing: success=commit, exception=rollback
 * };
 * 
 * // With custom transaction context
 * public class TransactionContext {
 *     private final EntityManager em;
 *     private final AuditLogger auditLogger;
 *     
 *     public TransactionContext(EntityManager em, AuditLogger logger) {
 *         this.em = em;
 *         this.auditLogger = logger;
 *     }
 * }
 * 
 * TransactionCallback<TransactionContext, Void> auditedOperation = ctx -> {
 *     ctx.auditLogger.log("Starting operation");
 *     // Perform operation
 *     ctx.em.persist(entity);
 *     ctx.auditLogger.log("Operation completed");
 *     return null;
 * };
 * }</pre>
 *
 * <p><b>Exception Handling Contract</b><br>
 * - {@code throws Exception} - Broad exception signature for flexibility
 * - Checked exceptions wrapped in RuntimeException by caller
 * - Unchecked exceptions propagated as-is
 * - All exceptions trigger transaction rollback
 *
 * <p><b>Generic Type Parameters</b><br>
 * - {@code <C>} - Transaction context type (EntityManager, Connection, custom)
 * - {@code <T>} - Result type (entity, primitive, Void for no return)
 *
 * <p><b>Thread Safety</b><br>
 * Implementation thread safety depends on callback logic. Context (EntityManager)
 * is typically NOT thread-safe - use one context per thread.
 *
 * @param <C> transaction context type (EntityManager, Connection, etc.)
 * @param <T> result type returned from callback
 * @see com.ghatana.core.database.TransactionManager
 * @doc.type interface
 * @doc.purpose Functional interface for transactional callbacks
 * @doc.layer core
 * @doc.pattern Port
 */
@FunctionalInterface
public interface TransactionCallback<C, T> {
    T execute(C context) throws Exception;
}
