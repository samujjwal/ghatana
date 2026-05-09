package com.ghatana.datacloud.spi;

import java.util.Objects;

/**
 * Exception thrown when a transaction fails and must be rolled back (DC-BE-003).
 *
 * <p>This exception indicates that a transactional operation failed and rollback was
 * initiated. It contains details about the failure, including the transaction ID,
 * the operation that failed, and the original cause.
 *
 * <h2>DC-BE-003: Transaction Boundaries for Multi-Step Writes</h2>
 * This exception is used by TransactionManager to signal transaction failures:
 * - Entity write + event append + audit log failures
 * - Event append + metadata update + audit log failures
 * - Governance operation + event + audit log failures
 *
 * @doc.type class
 * @doc.purpose Exception for transaction failures requiring rollback
 * @doc.layer spi
 * @doc.pattern Exception
 */
public final class TransactionException extends RuntimeException {

    private final String transactionId;
    private final String failedOperation;
    private final RollbackStatus rollbackStatus;

    /**
     * Rollback status after transaction failure.
     */
    public enum RollbackStatus {
        /** Rollback completed successfully */
        COMPLETED,
        /** Rollback partially completed (some operations rolled back, others failed) */
        PARTIAL,
        /** Rollback failed completely */
        FAILED,
        /** Rollback not attempted */
        NOT_ATTEMPTED
    }

    /**
     * Creates a transaction exception.
     *
     * @param transactionId the transaction ID
     * @param failedOperation the operation that failed
     * @param cause the original cause of the failure
     */
    public TransactionException(String transactionId, String failedOperation, Throwable cause) {
        this(transactionId, failedOperation, cause, RollbackStatus.NOT_ATTEMPTED);
    }

    /**
     * Creates a transaction exception with rollback status.
     *
     * @param transactionId the transaction ID
     * @param failedOperation the operation that failed
     * @param cause the original cause of the failure
     * @param rollbackStatus the rollback status
     */
    public TransactionException(String transactionId, String failedOperation, Throwable cause, RollbackStatus rollbackStatus) {
        super(String.format("Transaction %s failed during operation '%s': %s",
                Objects.requireNonNull(transactionId, "transactionId"),
                Objects.requireNonNull(failedOperation, "failedOperation"),
                cause != null ? cause.getMessage() : "unknown"),
                cause);
        this.transactionId = transactionId;
        this.failedOperation = failedOperation;
        this.rollbackStatus = rollbackStatus;
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
     * Gets the failed operation name.
     *
     * @return failed operation
     */
    public String failedOperation() {
        return failedOperation;
    }

    /**
     * Gets the rollback status.
     *
     * @return rollback status
     */
    public RollbackStatus rollbackStatus() {
        return rollbackStatus;
    }

    /**
     * Checks if rollback completed successfully.
     *
     * @return true if rollback completed
     */
    public boolean isRollbackCompleted() {
        return rollbackStatus == RollbackStatus.COMPLETED;
    }

    /**
     * Checks if rollback failed.
     *
     * @return true if rollback failed
     */
    public boolean isRollbackFailed() {
        return rollbackStatus == RollbackStatus.FAILED || rollbackStatus == RollbackStatus.PARTIAL;
    }

    @Override
    public String toString() {
        return "TransactionException{" +
                "transactionId='" + transactionId + '\'' +
                ", failedOperation='" + failedOperation + '\'' +
                ", rollbackStatus=" + rollbackStatus +
                ", cause=" + getCause() +
                '}';
    }
}
