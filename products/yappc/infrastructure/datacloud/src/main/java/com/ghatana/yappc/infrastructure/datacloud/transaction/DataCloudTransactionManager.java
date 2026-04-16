/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Infrastructure — Data Cloud Transaction Manager
 */
package com.ghatana.yappc.infrastructure.datacloud.transaction;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Transaction manager for Data Cloud operations using compensating transaction pattern.
 *
 * <p>Data Cloud doesn't support native ACID transactions across multiple entities.
 * This manager implements a compensating transaction pattern where:
 * <ul>
 *   <li>Operations are executed in sequence</li>
 *   <li>Compensation actions are registered for each operation</li>
 *   <li>On failure, registered compensations are executed in reverse order</li>
 *   <li>Compensation failures are logged but don't prevent rollback</li>
 * </ul>
 *
 * <p><b>Usage</b></p>
 * <pre>{@code
 * DataCloudTransactionManager txManager = new DataCloudTransactionManager(repository);
 *
 * txManager.inTransaction(tx -> {
 *     // Operation 1: Create entity
 *     tx.execute(
 *         () -> repository.save(entity1),
 *         () -> repository.deleteById(entity1.getId())
 *     );
 *
 *     // Operation 2: Create related entity
 *     tx.execute(
 *         () -> repository.save(entity2),
 *         () -> repository.deleteById(entity2.getId())
 *     );
 *
 *     // Operation 3: Update third entity
 *     Entity original = repository.findById(entity3Id).get();
 *     tx.execute(
 *         () -> repository.save(updated),
 *         () -> repository.save(original)
 *     );
 * });
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Compensating transaction manager for Data Cloud operations
 * @doc.layer infrastructure
 * @doc.pattern Transaction Manager, Compensating Transaction
 */
public class DataCloudTransactionManager {

    private static final Logger LOG = LoggerFactory.getLogger(DataCloudTransactionManager.class);

    /**
     * Transaction context holding operations and compensations.
     */
    public static class TransactionContext {
        private final String transactionId;
        private final List<Operation> operations = new ArrayList<>();
        private final List<Compensation> compensations = new ArrayList<>();
        private boolean committed = false;
        private boolean rolledBack = false;

        public TransactionContext(String transactionId) {
            this.transactionId = transactionId;
        }

        public String transactionId() {
            return transactionId;
        }

        public boolean isCommitted() {
            return committed;
        }

        public boolean isRolledBack() {
            return rolledBack;
        }
    }

    /**
     * Represents a single operation in a transaction.
     */
    @FunctionalInterface
    public interface Operation {
        Promise<Void> execute();
    }

    /**
     * Represents a compensation action to undo an operation.
     */
    @FunctionalInterface
    public interface Compensation {
        Promise<Void> compensate();
    }

    /**
     * Executes a function within a transaction boundary.
     *
     * <p>The transaction is automatically committed after successful completion.
     * If an exception occurs, the transaction is rolled back using compensating actions.
     *
     * @param <T> The return type
     * @param function The function to execute within the transaction
     * @return Promise resolving to the result of the function
     */
    public <T> Promise<T> inTransaction(Function<TransactionContext, Promise<T>> function) {
        String transactionId = UUID.randomUUID().toString();
        TransactionContext context = new TransactionContext(transactionId);

        LOG.debug("Starting transaction: {}", transactionId);

        return function.apply(context)
                .whenResult(result -> {
                    context.committed = true;
                    LOG.debug("Transaction committed successfully: {}", transactionId);
                })
                .whenException(ex -> {
                    if (!context.committed && !context.rolledBack) {
                        rollback(context).whenResult(ignored -> {
                            context.rolledBack = true;
                            LOG.debug("Transaction rolled back: {}", transactionId);
                        });
                    }
                });
    }

    /**
     * Executes an operation within the transaction context and registers its compensation.
     *
     * @param context The transaction context
     * @param operation The operation to execute
     * @param compensation The compensation action to undo the operation
     * @return Promise resolving when the operation completes
     */
    public Promise<Void> execute(
            @NotNull TransactionContext context,
            @NotNull Operation operation,
            @NotNull Compensation compensation) {
        LOG.debug("Executing operation in transaction: {}", context.transactionId());

        return operation.execute()
                .then(ignored -> {
                    // Register compensation after successful operation
                    context.compensations.add(0, compensation); // Add at front for reverse order
                    LOG.debug("Operation completed, compensation registered: {}", context.transactionId());
                    return Promise.complete();
                })
                .whenException(ex -> {
                    LOG.error("Operation failed in transaction {}: {}", context.transactionId(), ex.getMessage());
                    // Exception will be propagated automatically
                });
    }

    /**
     * Rolls back a transaction by executing compensations in reverse order.
     *
     * @param context The transaction context to rollback
     * @return Promise resolving when rollback completes
     */
    private Promise<Void> rollback(TransactionContext context) {
        LOG.warn("Rolling back transaction: {}", context.transactionId());

        if (context.compensations.isEmpty()) {
            LOG.debug("No compensations to execute for transaction: {}", context.transactionId());
            return Promise.complete();
        }

        List<Promise<Void>> compensationPromises = new ArrayList<>();
        for (Compensation compensation : context.compensations) {
            compensationPromises.add(compensation.compensate()
                    .whenException(ex -> {
                        LOG.error("Compensation failed for transaction {}: {}",
                                context.transactionId(), ex.getMessage());
                        // Continue with other compensations
                    }));
        }

        return io.activej.promise.Promises.all(compensationPromises)
                .map(ignored -> null);
    }
}
