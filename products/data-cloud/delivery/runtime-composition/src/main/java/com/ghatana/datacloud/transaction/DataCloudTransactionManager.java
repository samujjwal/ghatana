package com.ghatana.datacloud.transaction;

import com.ghatana.datacloud.spi.TransactionContext;
import com.ghatana.datacloud.spi.TransactionException;
import com.ghatana.datacloud.spi.TransactionManager;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Data Cloud transaction manager for atomic multi-step writes (DC-BE-003).
 *
 * <p>Implements transaction boundaries for multi-step write operations to ensure atomicity
 * and prevent partial writes from leaving invalid state. Uses TransactionContext to track
 * operations and rollback handlers.
 *
 * <h2>DC-BE-003: Transaction Boundaries for Multi-Step Writes</h2>
 * This transaction manager ensures atomicity across:
 * - Entity CRUD: entity write + CDC event + audit log
 * - Event append: event write + metadata update + audit log
 * - Governance operations: policy write + event + audit log
 *
 * <h2>Transaction Semantics</h2>
 * <ul>
 *   <li>If all operations succeed, the transaction commits and rollback handlers are cleared.</li>
 *   <li>If any operation fails, the transaction rolls back all operations via registered handlers.</li>
 *   <li>Rollback handlers are executed in reverse order (LIFO) to undo operations in correct sequence.</li>
 *   <li>Transactions are scoped by tenant to prevent cross-tenant interference.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Transaction manager implementation for atomic multi-step writes
 * @doc.layer product
 * @doc.pattern Manager
 */
public final class DataCloudTransactionManager implements TransactionManager {

    private static final Logger log = LoggerFactory.getLogger(DataCloudTransactionManager.class);

    private static final long DEFAULT_TRANSACTION_TIMEOUT_MS = 30_000; // 30 seconds

    private final long transactionTimeoutMs;

    /**
     * Creates a transaction manager with default timeout.
     */
    public DataCloudTransactionManager() {
        this(DEFAULT_TRANSACTION_TIMEOUT_MS);
    }

    /**
     * Creates a transaction manager with custom timeout.
     *
     * @param transactionTimeoutMs transaction timeout in milliseconds
     */
    public DataCloudTransactionManager(long transactionTimeoutMs) {
        if (transactionTimeoutMs <= 0) {
            throw new IllegalArgumentException("Transaction timeout must be positive");
        }
        this.transactionTimeoutMs = transactionTimeoutMs;
    }

    @Override
    public <T> Promise<T> executeInTransaction(Supplier<Promise<T>> operation) {
        return executeInTransaction("default", operation);
    }

    @Override
    public <T> Promise<T> executeInTransaction(String tenantId, Supplier<Promise<T>> operation) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(operation, "operation");

        TransactionContext context = TransactionContext.create(tenantId);
        log.debug("[DC-BE-003] Starting transaction {} for tenant {}", context.transactionId(), tenantId);

        try {
            // Execute the operation with context
            Promise<T> resultPromise = operation.get();

            // Handle completion
            return resultPromise
                    .map(result -> {
                        context.commit();
                        log.debug("[DC-BE-003] Transaction {} committed successfully", context.transactionId());
                        return result;
                    })
                    .whenException(throwable -> {
                        context.rollback();
                        TransactionException.RollbackStatus rollbackStatus = context.isRolledBack()
                                ? TransactionException.RollbackStatus.COMPLETED
                                : TransactionException.RollbackStatus.FAILED;
                        throw new TransactionException(
                                context.transactionId(),
                                "transactional operation",
                                throwable,
                                rollbackStatus);
                    });

        } catch (Exception e) {
            // Synchronous exception before Promise creation
            context.rollback();
            TransactionException.RollbackStatus rollbackStatus = context.isRolledBack()
                    ? TransactionException.RollbackStatus.COMPLETED
                    : TransactionException.RollbackStatus.FAILED;
            throw new TransactionException(
                    context.transactionId(),
                    "transactional operation",
                    e,
                    rollbackStatus);
        }
    }

    /**
     * Executes an operation within a transaction boundary with explicit context access.
     *
     * <p>This variant allows the operation to access the TransactionContext for registering
     * rollback handlers and storing attributes.
     *
     * @param tenantId the tenant ID for transaction scoping
     * @param operation the operation to execute with context access
     * @param <T> the return type of the operation
     * @return promise that completes with the operation result on success, or fails on rollback
     */
    @Override
    public <T> Promise<T> executeInTransactionWithContext(String tenantId,
                                                               TransactionManager.TransactionalOperation<T> operation) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(operation, "operation");

        TransactionContext context = TransactionContext.create(tenantId);
        log.debug("[DC-BE-003] Starting transaction {} for tenant {} with context access",
                context.transactionId(), tenantId);

        try {
            // Execute the operation with context
            Promise<T> resultPromise = operation.execute(context);

            // Handle completion
            return resultPromise
                    .map(result -> {
                        context.commit();
                        log.debug("[DC-BE-003] Transaction {} committed successfully", context.transactionId());
                        return result;
                    })
                    .whenException(throwable -> {
                        context.rollback();
                        TransactionException.RollbackStatus rollbackStatus = context.isRolledBack()
                                ? TransactionException.RollbackStatus.COMPLETED
                                : TransactionException.RollbackStatus.FAILED;
                        throw new TransactionException(
                                context.transactionId(),
                                "transactional operation with context",
                                throwable,
                                rollbackStatus);
                    });

        } catch (Exception e) {
            // Synchronous exception before Promise creation
            context.rollback();
            TransactionException.RollbackStatus rollbackStatus = context.isRolledBack()
                    ? TransactionException.RollbackStatus.COMPLETED
                    : TransactionException.RollbackStatus.FAILED;
            throw new TransactionException(
                    context.transactionId(),
                    "transactional operation with context",
                    e,
                    rollbackStatus);
        }
    }
}
