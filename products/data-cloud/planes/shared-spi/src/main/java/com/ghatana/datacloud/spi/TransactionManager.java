package com.ghatana.datacloud.spi;

import io.activej.promise.Promise;

/**
 * Transaction manager for atomic multi-step writes (DC-BE-003).
 *
 * <p>Provides transaction boundaries for multi-step write operations to ensure atomicity
 * and prevent partial writes from leaving invalid state. This is critical for operations
 * that involve multiple data planes (entity + event + audit).
 *
 * <h2>DC-BE-003: Transaction Boundaries for Multi-Step Writes</h2>
 * This transaction manager ensures atomicity across:
 * - Entity CRUD: entity write + CDC event + audit log
 * - Event append: event write + metadata update + audit log
 * - Governance operations: policy write + event + audit log
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * transactionManager.executeInTransaction(() -> {
 *     Promise<Entity> entityWrite = entityStore.save(entity);
 *     Promise<Event> eventWrite = eventLogStore.append(cdcEvent);
 *     Promise<Void> auditWrite = auditService.log(auditEvent);
 *     return Promises.all(entityWrite, eventWrite, auditWrite)
 *         .map(results -> results.get(0)); // Return entity
 * });
 * }</pre>
 *
 * <h2>Transaction Semantics</h2>
 * <ul>
 *   <li>If all operations succeed, the transaction commits.</li>
 *   <li>If any operation fails, the transaction rolls back all operations.</li>
 *   <li>Rollback attempts to undo all operations that succeeded before the failure.</li>
 *   <li>Transactions are scoped by tenant to prevent cross-tenant interference.</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose Transaction manager for atomic multi-step writes
 * @doc.layer spi
 * @doc.pattern Port
 */
public interface TransactionManager {

    /**
     * Executes an operation within a transaction boundary.
     *
     * <p>If the operation completes successfully, all writes are committed.
     * If the operation fails, all writes are rolled back.
     *
     * @param operation the operation to execute transactionally
     * @param <T> the return type of the operation
     * @return promise that completes with the operation result on success, or fails on rollback
     */
    <T> Promise<T> executeInTransaction(java.util.function.Supplier<Promise<T>> operation);

    /**
     * Executes an operation within a transaction boundary with explicit tenant scoping.
     *
     * <p>Tenant scoping ensures transactions don't interfere across tenants.
     *
     * @param tenantId the tenant ID for transaction scoping
     * @param operation the operation to execute transactionally
     * @param <T> the return type of the operation
     * @return promise that completes with the operation result on success, or fails on rollback
     */
    <T> Promise<T> executeInTransaction(String tenantId, java.util.function.Supplier<Promise<T>> operation);

    /**
     * Executes an operation within a transaction boundary with context access.
     *
     * <p>This variant allows the operation to access the TransactionContext for registering
     * rollback handlers and storing attributes.
     *
     * @param tenantId the tenant ID for transaction scoping
     * @param operation the operation to execute with context access
     * @param <T> the return type of the operation
     * @return promise that completes with the operation result on success, or fails on rollback
     */
    <T> Promise<T> executeInTransactionWithContext(String tenantId, TransactionalOperation<T> operation);

    /**
     * Transactional operation that receives the TransactionContext.
     *
     * @param <T> the return type
     */
    @FunctionalInterface
    interface TransactionalOperation<T> {
        /**
         * Executes the operation with access to the transaction context.
         *
         * @param context the transaction context
         * @return promise that completes with the operation result
         */
        Promise<T> execute(TransactionContext context);
    }
}
