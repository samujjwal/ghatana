package com.ghatana.datacloud.spi;

import com.ghatana.datacloud.DataRecord;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;

/**
 * Extension interface for plugins supporting transactions.
 *
 * <p>
 * <b>Purpose</b><br>
 * Optional capability for storage plugins that support ACID transactions.
 * Provides both simple transaction execution and explicit transaction control.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * if (plugin instanceof TransactionCapability tx) {
 *     // Simple transaction
 *     tx.executeInTransaction("tenantId", transaction -> {
 *         transaction.insert(record1);
 *         transaction.insert(record2);
 *         transaction.update(record3);
 *         return Promise.complete();
 *     });
 *
 *     // Explicit control
 *     Transaction transaction = tx.beginTransaction("tenantId", options).getResult();
 *     try {
 *         transaction.insert(record1).getResult();
 *         transaction.insert(record2).getResult();
 *         transaction.commit().getResult();
 *     } catch (Exception e) {
 *         transaction.rollback().getResult();
 *         throw e;
 *     }
 * }
 * }</pre>
 *
 * @param <R> Record type
 * @see StoragePlugin
 * @doc.type interface
 * @doc.purpose Transaction capability
 * @doc.layer core
 * @doc.pattern Capability Interface
 */
public interface TransactionCapability<R extends DataRecord> {

    /**
     * Begins a new transaction.
     *
     * @param tenantId Tenant ID
     * @param options Transaction options
     * @return Promise with transaction handle
     */
    Promise<Transaction<R>> beginTransaction(String tenantId, TransactionOptions options);

    /**
     * Begins a transaction with default options.
     *
     * @param tenantId Tenant ID
     * @return Promise with transaction handle
     */
    default Promise<Transaction<R>> beginTransaction(String tenantId) {
        return beginTransaction(tenantId, TransactionOptions.defaults());
    }

    /**
     * Executes a function within a transaction with auto-commit/rollback.
     *
     * @param tenantId Tenant ID
     * @param work Work to execute
     * @param <T> Result type
     * @return Promise with result
     */
    <T> Promise<T> executeInTransaction(String tenantId, TransactionWork<R, T> work);

    /**
     * Executes a function within a transaction with options.
     *
     * @param tenantId Tenant ID
     * @param options Transaction options
     * @param work Work to execute
     * @param <T> Result type
     * @return Promise with result
     */
    <T> Promise<T> executeInTransaction(String tenantId, TransactionOptions options, TransactionWork<R, T> work);

    /**
     * Transaction handle for explicit control.
     *
     * @param <R> Record type
     */
    interface Transaction<R extends DataRecord> {

        /**
         * Gets the transaction ID.
         */
        String getId();

        /**
         * Checks if transaction is active.
         */
        boolean isActive();

        /**
         * Inserts a record within this transaction.
         */
        Promise<R> insert(R record);

        /**
         * Inserts multiple records within this transaction.
         */
        Promise<StoragePlugin.BatchResult> insertBatch(List<R> records);

        /**
         * Updates a record within this transaction.
         */
        Promise<R> update(R record);

        /**
         * Updates multiple records within this transaction.
         */
        Promise<StoragePlugin.BatchResult> updateBatch(List<R> records);

        /**
         * Deletes a record within this transaction.
         */
        Promise<Void> delete(String collectionName, java.util.UUID id);

        /**
         * Commits the transaction.
         */
        Promise<Void> commit();

        /**
         * Rolls back the transaction.
         */
        Promise<Void> rollback();

        /**
         * Creates a savepoint.
         */
        Promise<String> savepoint(String name);

        /**
         * Rolls back to a savepoint.
         */
        Promise<Void> rollbackToSavepoint(String name);

        /**
         * Releases a savepoint.
         */
        Promise<Void> releaseSavepoint(String name);
    }

    /**
     * Functional interface for transaction work.
     *
     * @param <R> Record type
     * @param <T> Result type
     */
    @FunctionalInterface
    interface TransactionWork<R extends DataRecord, T> {

        Promise<T> execute(Transaction<R> transaction);
    }

    /**
     * Transaction options.
     */
    record TransactionOptions(
            IsolationLevel isolationLevel,
            boolean readOnly,
            java.time.Duration timeout,
            Map<String, Object> properties
            ) {

        public static TransactionOptions defaults() {
            return new TransactionOptions(
                    IsolationLevel.READ_COMMITTED,
                    false,
                    java.time.Duration.ofSeconds(30),
                    Map.of()
            );
        }

        public static TransactionOptionsBuilder builder() {
            return new TransactionOptionsBuilder();
        }

        public static class TransactionOptionsBuilder {

            private IsolationLevel isolationLevel = IsolationLevel.READ_COMMITTED;
            private boolean readOnly = false;
            private java.time.Duration timeout = java.time.Duration.ofSeconds(30);
            private Map<String, Object> properties = Map.of();

            public TransactionOptionsBuilder isolationLevel(IsolationLevel level) {
                this.isolationLevel = level;
                return this;
            }

            public TransactionOptionsBuilder readOnly() {
                this.readOnly = true;
                return this;
            }

            public TransactionOptionsBuilder readWrite() {
                this.readOnly = false;
                return this;
            }

            public TransactionOptionsBuilder timeout(java.time.Duration timeout) {
                this.timeout = timeout;
                return this;
            }

            public TransactionOptionsBuilder properties(Map<String, Object> properties) {
                this.properties = properties;
                return this;
            }

            public TransactionOptions build() {
                return new TransactionOptions(isolationLevel, readOnly, timeout, properties);
            }
        }
    }

    /**
     * Transaction isolation levels.
     */
    enum IsolationLevel {
        READ_UNCOMMITTED,
        READ_COMMITTED,
        REPEATABLE_READ,
        SERIALIZABLE
    }
}
