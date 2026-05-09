package com.ghatana.datacloud.spi;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Transaction context for tracking operations within a transaction (DC-BE-003).
 *
 * <p>Maintains transaction state, tracks rollback handlers for each operation, and manages
 * the lifecycle of a transaction (active, committed, rolled back). This enables atomic
 * multi-step writes by providing rollback capabilities when operations fail.
 *
 * <h2>DC-BE-003: Transaction Boundaries for Multi-Step Writes</h2>
 * This context is used by TransactionManager to:
 * - Track all operations performed within a transaction
 * - Register rollback handlers for each operation
 * - Manage transaction state transitions
 * - Provide visibility into transaction metadata
 *
 * @doc.type class
 * @doc.purpose Transaction context for tracking operations and rollback handlers
 * @doc.layer spi
 * @doc.pattern Context
 */
public final class TransactionContext {

    private final String transactionId;
    private final String tenantId;
    private final Instant createdAt;
    private State state;
    private final List<RollbackHandler> rollbackHandlers;
    private final ConcurrentHashMap<String, Object> attributes;

    private TransactionContext(String transactionId, String tenantId) {
        this.transactionId = Objects.requireNonNull(transactionId, "transactionId");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.createdAt = Instant.now();
        this.state = State.ACTIVE;
        this.rollbackHandlers = new ArrayList<>();
        this.attributes = new ConcurrentHashMap<>();
    }

    /**
     * Creates a new transaction context.
     *
     * @param tenantId the tenant ID for transaction scoping
     * @return new transaction context
     */
    public static TransactionContext create(String tenantId) {
        return new TransactionContext(UUID.randomUUID().toString(), tenantId);
    }

    /**
     * Registers a rollback handler for an operation.
     *
     * <p>The rollback handler will be executed if the transaction is rolled back.
     * Handlers are executed in reverse order of registration (LIFO).
     *
     * @param handler the rollback handler
     */
    public void registerRollbackHandler(RollbackHandler handler) {
        Objects.requireNonNull(handler, "handler");
        if (state != State.ACTIVE) {
            throw new IllegalStateException("Cannot register rollback handler in state: " + state);
        }
        rollbackHandlers.add(handler);
    }

    /**
     * Marks the transaction as committed.
     *
     * <p>Once committed, rollback handlers are cleared and no further operations are allowed.
     */
    public void commit() {
        if (state != State.ACTIVE) {
            throw new IllegalStateException("Cannot commit transaction in state: " + state);
        }
        state = State.COMMITTED;
        rollbackHandlers.clear(); // Clear handlers since transaction succeeded
    }

    /**
     * Marks the transaction as rolled back and executes all rollback handlers.
     *
     * <p>Rollback handlers are executed in reverse order of registration (LIFO).
     * Any exceptions during rollback are logged but don't prevent other handlers from executing.
     */
    public void rollback() {
        if (state != State.ACTIVE) {
            throw new IllegalStateException("Cannot rollback transaction in state: " + state);
        }
        state = State.ROLLED_BACK;

        // Execute rollback handlers in reverse order (LIFO)
        for (int i = rollbackHandlers.size() - 1; i >= 0; i--) {
            RollbackHandler handler = rollbackHandlers.get(i);
            try {
                handler.rollback();
            } catch (Exception e) {
                // Log but continue with other rollback handlers
                System.err.println("[DC-BE-003] Rollback handler failed: " + e.getMessage());
            }
        }
        rollbackHandlers.clear();
    }

    /**
     * Gets the transaction ID.
     *
     * @return transaction ID
     */
    public String transactionId() {
        return transactionId;
    }

    /**
     * Gets the tenant ID.
     *
     * @return tenant ID
     */
    public String tenantId() {
        return tenantId;
    }

    /**
     * Gets the transaction creation timestamp.
     *
     * @return creation timestamp
     */
    public Instant createdAt() {
        return createdAt;
    }

    /**
     * Gets the current transaction state.
     *
     * @return transaction state
     */
    public State state() {
        return state;
    }

    /**
     * Checks if the transaction is active.
     *
     * @return true if active
     */
    public boolean isActive() {
        return state == State.ACTIVE;
    }

    /**
     * Checks if the transaction is committed.
     *
     * @return true if committed
     */
    public boolean isCommitted() {
        return state == State.COMMITTED;
    }

    /**
     * Checks if the transaction is rolled back.
     *
     * @return true if rolled back
     */
    public boolean isRolledBack() {
        return state == State.ROLLED_BACK;
    }

    /**
     * Gets the number of registered rollback handlers.
     *
     * @return rollback handler count
     */
    public int rollbackHandlerCount() {
        return rollbackHandlers.size();
    }

    /**
     * Stores an attribute in the transaction context.
     *
     * @param key the attribute key
     * @param value the attribute value
     */
    public void setAttribute(String key, Object value) {
        Objects.requireNonNull(key, "key");
        attributes.put(key, value);
    }

    /**
     * Gets an attribute from the transaction context.
     *
     * @param key the attribute key
     * @return the attribute value, or null if not found
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        Objects.requireNonNull(key, "key");
        return (T) attributes.get(key);
    }

    /**
     * Removes an attribute from the transaction context.
     *
     * @param key the attribute key
     * @return the removed value, or null if not found
     */
    @SuppressWarnings("unchecked")
    public <T> T removeAttribute(String key) {
        Objects.requireNonNull(key, "key");
        return (T) attributes.remove(key);
    }

    /**
     * Transaction state.
     */
    public enum State {
        /** Transaction is active and accepting operations */
        ACTIVE,
        /** Transaction has been committed successfully */
        COMMITTED,
        /** Transaction has been rolled back */
        ROLLED_BACK
    }

    /**
     * Rollback handler interface.
     */
    @FunctionalInterface
    public interface RollbackHandler {
        /**
         * Executes rollback logic for an operation.
         *
         * @throws Exception if rollback fails
         */
        void rollback() throws Exception;
    }
}
